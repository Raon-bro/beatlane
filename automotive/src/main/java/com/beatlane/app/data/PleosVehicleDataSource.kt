package com.beatlane.app.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.judge.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 신호 하나(속도 또는 브레이크)의 연결 상태. */
enum class SignalStatus {
    CHECKING,
    READY,
    /** 이 차량(에뮬레이터)에서 지원하지 않음. */
    UNSUPPORTED,
    /** 권한이 없음 (속도: 런타임 권한 거부). */
    NO_PERMISSION,
    /** 연결·조회 실패. */
    FAILED,
}

/**
 * 실제 차량 데이터.
 * - 브레이크 페달: Pleos Vehicle SDK `Brake` (권한 pleos.car.permission.CAR_DRIVE)
 * - 차량 속도: AAOS CarPropertyManager `PERF_VEHICLE_SPEED` (권한 android.car.permission.CAR_SPEED)
 *
 * 쓰기 전에 각각 지원 여부를 확인하고, 둘 다 [SignalStatus.READY]일 때만 [available]이 true가 된다.
 * 하나라도 안 되면 앱은 시뮬레이션으로 시연을 계속한다.
 */
class PleosVehicleDataSource(
    private val context: Context,
    override var powertrain: Powertrain,
    private val scope: CoroutineScope,
) : VehicleDataSource {

    private val assembler = SampleAssembler()
    private val _samples = MutableSharedFlow<VehicleSample>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples: SharedFlow<VehicleSample> = _samples.asSharedFlow()
    override val speedFlow: Flow<Float> = samples.map { it.speed.toFloat() }
    override val brakePressedFlow: Flow<Boolean> = samples.map { it.brakePressed }.distinctUntilChanged()

    private val _speedStatus = MutableStateFlow(SignalStatus.CHECKING)
    val speedStatus: StateFlow<SignalStatus> = _speedStatus.asStateFlow()
    private val _brakeStatus = MutableStateFlow(SignalStatus.CHECKING)
    val brakeStatus: StateFlow<SignalStatus> = _brakeStatus.asStateFlow()

    /** 속도·브레이크 둘 다 받을 수 있는지. */
    val available: Flow<Boolean> = combine(_speedStatus, _brakeStatus) { s, b ->
        s == SignalStatus.READY && b == SignalStatus.READY
    }

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var tickJob: Job? = null

    // ---------- 브레이크 (Pleos SDK) ----------

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            if (status == null) return
            Log.d(TAG, "input brake=${if (status) "ON" else "OFF"}")
            assembler.setBrake(status)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "brake listener failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission") // CAR_DRIVE는 normal 권한, Manifest에 선언됨
    private fun startBrake() {
        val v = Vehicle(context)
        vehicle = v
        try {
            v.initialize()
            // 연결 전 호출은 SDK가 큐에 넣었다가 연결되면 보낸다.
            v.getBrake().checkBrakePedalStatusCapabilities(
                onComplete = { config ->
                    Log.d(TAG, "brake capability ${config.accessType}")
                    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                        try {
                            v.getBrake().registerBrakePedalStatus(brakeListener)
                            v.getBrake().getCurrentBrakePedalStatus(
                                onComplete = { it?.let(assembler::setBrake) },
                                onFailed = { e -> Log.w(TAG, "brake get failed: ${e.message}") },
                            )
                            _brakeStatus.value = SignalStatus.READY
                        } catch (e: Exception) {
                            Log.w(TAG, "brake register failed", e)
                            _brakeStatus.value = SignalStatus.FAILED
                        }
                    } else {
                        _brakeStatus.value = SignalStatus.UNSUPPORTED
                    }
                },
                onFailed = { e ->
                    Log.w(TAG, "brake capability failed: ${e.message}")
                    _brakeStatus.value = SignalStatus.FAILED
                },
            )
        } catch (e: Exception) {
            // SDK가 없는 환경(일반 에뮬레이터 등)에서도 앱이 죽지 않게
            Log.w(TAG, "vehicle sdk init failed", e)
            _brakeStatus.value = SignalStatus.FAILED
        }
    }

    // ---------- 속도 (AAOS) ----------

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            @Suppress("DEPRECATION") // 대체 API(propertyStatus)는 API 35부터라 대상 34에서는 status 사용
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
            val mps = (value.value as? Float)?.toDouble() ?: return
            logSpeedInput(value.timestamp / 1_000_000, mps)
            assembler.onSpeed(value.timestamp / 1_000_000, mps)?.let { _samples.tryEmit(it) }
        }

        // 실차/Virtual Console 입력 확인용: 속도가 바뀌었을 때만 0.5초에 한 번 정도
        private var lastLogMs: Long? = null
        private var lastLoggedKmh = Double.NaN

        private fun logSpeedInput(timeMs: Long, mps: Double) {
            val kmh = mps * 3.6
            val last = lastLogMs
            val first = last == null
            val changed = first || kotlin.math.abs(kmh - lastLoggedKmh) >= SPEED_LOG_STEP_KMH
            if (changed && (first || timeMs - last!! >= SPEED_LOG_INTERVAL_MS)) {
                Log.d(TAG, "input speed=%.1f km/h t=%d".format(kmh, timeMs))
                lastLogMs = timeMs
                lastLoggedKmh = kmh
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "speed error event prop=$propertyId area=$areaId")
        }
    }

    /** 속도 권한이 허용된 뒤 다시 부를 수 있다. 백그라운드·메인 어느 스레드에서 불러도 된다. */
    @Synchronized
    fun startSpeed() {
        if (_speedStatus.value == SignalStatus.READY) return
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            _speedStatus.value = SignalStatus.NO_PERMISSION
            return
        }
        try {
            val c = car ?: Car.createCar(context).also { car = it }
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            if (pm.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null) {
                _speedStatus.value = SignalStatus.UNSUPPORTED
                return
            }
            @Suppress("DEPRECATION") // 대상 API 34에는 subscribePropertyEvents가 없다
            val ok = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ)
            _speedStatus.value = if (ok) SignalStatus.READY else SignalStatus.FAILED
        } catch (e: Exception) {
            Log.w(TAG, "speed subscribe failed", e)
            _speedStatus.value = SignalStatus.FAILED
        }
    }

    // ---------- 수명 ----------

    /** 수 초 걸릴 수 있으므로 백그라운드 스레드에서 부른다. */
    @Synchronized
    fun start() {
        startBrake()
        startSpeed()
        tickJob = scope.launch {
            while (isActive) {
                assembler.onTick(SystemClock.elapsedRealtime())?.let { _samples.emit(it) }
                delay(TICK_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop() {
        tickJob?.cancel()
        try {
            @Suppress("DEPRECATION") // registerCallback 짝 (API 34 대상)
            propertyManager?.unregisterCallback(speedCallback)
        } catch (e: Exception) {
            Log.w(TAG, "speed unregister failed", e)
        }
        car?.disconnect()
        car = null
        vehicle?.let { v ->
            try {
                if (_brakeStatus.value == SignalStatus.READY) v.getBrake().unregisterBrakePedalStatus(brakeListener)
            } catch (e: Exception) {
                Log.w(TAG, "brake unregister failed", e)
            }
            v.release()
        }
        vehicle = null
    }

    private companion object {
        const val TAG = "BeatLane/Vehicle"
        const val SPEED_RATE_HZ = 20f
        const val TICK_MS = 50L
        const val SPEED_LOG_INTERVAL_MS = 500L
        const val SPEED_LOG_STEP_KMH = 0.5
    }
}
