package com.beatlane.app.data

import com.beatlane.app.data.sim.ScenarioPoint
import com.beatlane.app.data.sim.SimScenario
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.judge.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicReference

/**
 * 시연·테스트용 가짜 차량 데이터.
 *
 * [scope]가 살아 있는 동안 [hz] 주기로 샘플을 계속 내보낸다.
 * 시나리오가 없을 때는 현재 상태(속도·브레이크)를 그대로 유지한다.
 * 샘플 시각은 실제 시계가 아니라 틱 번호 × 주기로 붙여서, 재생 지연이 있어도 판정이 흔들리지 않는다.
 */
class SimulatedVehicleDataSource(
    /** 시연 중 차종 모드를 바꿀 수 있게 var. */
    override var powertrain: Powertrain,
    scope: CoroutineScope,
    private val hz: Int = SimScenario.DEFAULT_HZ,
    initialSpeed: Double = 0.0,
    /** 재생 속도 맞춤용 단조 시계 (ms). 테스트에서는 가상 시계를 넣는다. */
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : VehicleDataSource {

    private val periodMs = 1000L / hz
    private val pending = AtomicReference<SimScenario?>(null)

    private val _samples = MutableSharedFlow<VehicleSample>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples: SharedFlow<VehicleSample> = _samples.asSharedFlow()
    override val speedFlow: Flow<Float> = samples.map { it.speed.toFloat() }
    override val brakePressedFlow: Flow<Boolean> = samples.map { it.brakePressed }.distinctUntilChanged()

    private val _playing = MutableStateFlow<SimScenario?>(null)
    /** 재생 중인 시나리오 (없으면 null). 버튼 상태 표시용. */
    val playing: StateFlow<SimScenario?> = _playing.asStateFlow()

    init {
        scope.launch { run(initialSpeed) }
    }

    /** 시나리오 재생. 재생 중이면 현재 상태에서 새 시나리오로 바로 갈아탄다. */
    fun play(scenario: SimScenario) {
        pending.set(scenario)
        _playing.value = scenario
    }

    private suspend fun run(initialSpeed: Double) {
        var tick = 0L
        var speed = initialSpeed
        var brake = false
        var queue: Iterator<ScenarioPoint>? = null
        // 고정 주기 스케줄: delay 오차가 쌓이지 않게 "다음 틱 예정 시각"까지 기다린다.
        var nextAt = clockMs()

        while (true) {
            pending.getAndSet(null)?.let { scenario ->
                // 첫 점은 현재 상태와 같으므로 건너뛴다.
                queue = scenario.build(speed, hz).points.drop(1).iterator()
                _playing.value = scenario
            }
            val q = queue
            if (q != null) {
                if (q.hasNext()) {
                    val p = q.next()
                    speed = p.speed
                    brake = p.brakePressed
                } else {
                    queue = null
                    if (pending.get() == null) _playing.value = null
                }
            }
            _samples.emit(VehicleSample(tick * periodMs, speed, brake))
            tick++
            nextAt += periodMs
            val now = clockMs()
            // 앱이 멈췄다 돌아오는 등 크게 밀리면 몰아서 따라잡지 않고 다시 맞춘다.
            if (now - nextAt > 500) nextAt = now
            val wait = nextAt - now
            if (wait > 0) delay(wait) else yield()
        }
    }
}
