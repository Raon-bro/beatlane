package com.beatlane.app.ui.drive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beatlane.app.data.AppSettings
import com.beatlane.app.data.DriveHistory
import com.beatlane.app.data.DriveHistoryStore
import com.beatlane.app.judge.GuideCurve
import com.beatlane.app.judge.NoteType
import com.beatlane.app.sound.SoundEffects
import com.beatlane.app.data.PleosVehicleDataSource
import com.beatlane.app.data.SignalStatus
import com.beatlane.app.data.SimulatedVehicleDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.beatlane.app.data.sim.SimForecast
import com.beatlane.app.data.sim.SimScenario
import com.beatlane.app.judge.ActiveNote
import com.beatlane.app.judge.DriveReport
import com.beatlane.app.judge.Grade
import com.beatlane.app.judge.JudgeEngine
import com.beatlane.app.judge.NoteResult
import com.beatlane.app.judge.Passenger
import com.beatlane.app.judge.Powertrain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Stage { PRE_DRIVE, DRIVING, REPORT }

/** 직전 주행 요약 (출발 전 화면용). */
data class LastDrive(
    val score: Double,
    val coaching: String,
    /** GOOD·MISS·급감속이 있어서 [coaching]이 "고칠 점"인지 (아니면 칭찬 문구). */
    val hasImprovement: Boolean,
)

/** 화면 상태. 주행 중에는 20Hz로 갱신된다. */
data class DriveUiState(
    val stage: Stage = Stage.PRE_DRIVE,
    val lastDrive: LastDrive? = null,
    /** 실제 차량 데이터가 아니라 시뮬레이션으로 도는 중인지. */
    val simulated: Boolean = true,
    /** 차량 데이터(속도+브레이크)를 받을 수 있는지. */
    val vehicleAvailable: Boolean = false,
    val speedStatus: SignalStatus = SignalStatus.CHECKING,
    val brakeStatus: SignalStatus = SignalStatus.CHECKING,
    val powertrain: Powertrain = Powertrain.GASOLINE,
    val passenger: Passenger = Passenger.NONE,
    val speedKmh: Float = 0f,
    val brakePressed: Boolean = false,
    val activeNote: ActiveNote? = null,
    /** 진행 중인 노트의 경과 시간. */
    val noteElapsedMs: Long = 0,
    /** 마지막 판정 결과. [resultSeq]가 바뀔 때마다 판정 연출을 다시 띄운다. */
    val lastResult: NoteResult? = null,
    val resultSeq: Int = 0,
    val combo: Int = 0,
    val rideScore: Double = 5.0,
    /** 급감속이 확정될 때마다 1 증가. */
    val hardDecelSeq: Int = 0,
    val simPlaying: SimScenario? = null,
    /** null이 아니면 주행이 끝나고 리포트를 보여 주는 중. */
    val report: ReportUi? = null,
    /** 지금 감속도 (m/s², 양수 = 감속). HOLD 게이지의 "풀기" 표시용. */
    val decel: Float = 0f,
    /** 지금(또는 방금 끝난) 노트의 가이드 곡선과 실제 감속. */
    val guide: GuideTrace? = null,
    /** 10콤보 단위에 도달할 때마다 1 증가 (강조 연출). */
    val comboMilestoneSeq: Int = 0,
    /** 판정 효과음. */
    val soundOn: Boolean = true,
    /** 시뮬레이션 "앞차 감속"의 예고 노트·앞차 위치 (그 외에는 null). */
    val forecast: SimForecast? = null,
)

/** 그래프 한 점: 노트 시작 후 초, 감속도(m/s²). */
data class GuidePt(val t: Float, val decel: Float)

/** "따라 그리기" 그래프 데이터. */
data class GuideTrace(
    val type: NoteType,
    /** 실제 감속 (지금까지). */
    val actual: List<GuidePt>,
    /** 이상적인 감속 (지금까지 + 앞으로 잠깐). */
    val ideal: List<GuidePt>,
    /** 노트가 진행 중인지 (끝났으면 화면에서 서서히 사라진다). */
    val active: Boolean,
    /** 지금까지 최대 감속 (HOLD 게이지 "풀기" 기준). */
    val peakDecel: Float,
    /** 정지 직전 목표 감속도. */
    val endTarget: Float,
)

data class SpeedPoint(val timeMs: Long, val kmh: Float)

/** 리포트 화면에 필요한 것 전부. */
data class ReportUi(
    val report: DriveReport,
    val startMs: Long,
    val endMs: Long,
    /** 2Hz로 줄인 속도 기록. */
    val speedTrace: List<SpeedPoint>,
    /** 최근 주행 승차감 (오래된 것 → 이번 주행, 최대 5개). 판정이 없던 주행은 빠진다. */
    val recentScores: List<Double>,
    /** 직전 주행 점수 (없으면 null). */
    val previousScore: Double?,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

private const val TAG = "BeatLane"
private const val TRACE_INTERVAL_MS = 500L
private const val TRACE_MAX = 4000
private const val GUIDE_MAX_SAMPLES = 1200

enum class SourceKind { VEHICLE, SIMULATION }

@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModel(app: Application) : AndroidViewModel(app) {

    /** 시연용 가짜 데이터. 항상 준비되어 있다. */
    private val sim = SimulatedVehicleDataSource(Powertrain.GASOLINE, viewModelScope)

    /** 실제 차량 데이터 (Pleos 브레이크 + AAOS 속도). 지원 여부는 비동기로 확인된다. */
    private val vehicle = PleosVehicleDataSource(app, Powertrain.GASOLINE, viewModelScope)

    /** 지금 판정에 쓰는 소스. */
    private val activeSource = MutableStateFlow(SourceKind.SIMULATION)

    /** 사용자가 직접 시뮬레이션을 고르면 차량 연결이 돼도 자동 전환하지 않는다. */
    private var userPickedSource = false

    private var engine = JudgeEngine(Powertrain.GASOLINE, Passenger.NONE)

    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    // 이번 주행 기록 (리포트용)
    private var driveStartMs: Long? = null
    private var lastSampleMs = 0L
    private val speedTrace = ArrayList<SpeedPoint>()
    /** 이 앱 실행 동안 끝낸 주행들의 승차감. */
    private val scoreHistory = mutableListOf<Double>()
    private var lastDrive: LastDrive? = null

    /** 최근 기록은 기기에 저장해서 앱을 다시 켜도 유지한다. */
    private val historyStore = DriveHistoryStore(app)

    private val settings = AppSettings(app)
    private val sfx = SoundEffects(app)

    // "따라 그리기" 가이드: 지금 노트의 샘플
    private class GuideSample(val t: Double, val decel: Double, val speed: Double)
    private var guideCurve = GuideCurve(engine.config, Passenger.NONE)
    private var guideStartMs: Long? = null
    private var guideType: NoteType? = null
    private val guideSamples = ArrayList<GuideSample>()
    private var guidePeak = 0.0

    init {
        _state.update { it.copy(soundOn = settings.soundOn) }
        // 차량 연결(Pleos SDK 초기화·지원 확인, Car 연결)은 수 초 걸릴 수 있어 메인 스레드 밖에서.
        // 그동안 화면은 시뮬레이션 상태로 바로 뜬다.
        viewModelScope.launch(Dispatchers.Default) { vehicle.start() }
        historyStore.load().let { h ->
            scoreHistory += h.scores
            if (h.lastScore != null && h.lastCoaching != null) {
                lastDrive = LastDrive(h.lastScore, h.lastCoaching, h.lastHasImprovement)
            }
            _state.update { it.copy(lastDrive = lastDrive) }
            Log.d(TAG, "history loaded scores=${h.scores.size} last=${h.lastScore}")
        }
        viewModelScope.launch {
            activeSource.flatMapLatest { kind ->
                val flow = if (kind == SourceKind.VEHICLE) vehicle.samples else sim.samples
                flow.map { kind to it }
            }.collect { (kind, sample) ->
                // 소스를 막 바꾼 순간 이전 소스 샘플이 섞이지 않게
                if (kind != activeSource.value) return@collect
                if (_state.value.stage != Stage.DRIVING) return@collect
                val u = engine.process(sample)
                val k = u.kinematics
                recordTrace(k.timeMs, (k.rawSpeed * 3.6).toFloat())
                if (u.activeNote != null && _state.value.activeNote == null) {
                    Log.d(TAG, "note start ${u.activeNote.type} t=${k.timeMs} v=%.1f".format(k.rawSpeed))
                }
                u.judged.forEach {
                    Log.d(
                        TAG,
                        "judged ${it.type} ${it.grade} t=${k.timeMs} v=%.1f stop=${it.endedByStop} j=%.2f hold=%s end=%s"
                            .format(k.rawSpeed, it.startJerk, it.holdStd, it.endDecel),
                    )
                }
                u.judged.lastOrNull()?.let { if (_state.value.soundOn) sfx.play(it.grade) }
                val guide = updateGuide(u.activeNote, k.timeMs, -k.accel, k.rawSpeed)
                _state.update { s ->
                    val milestone = u.combo > s.combo && u.combo % 10 == 0
                    s.copy(
                        speedKmh = (k.rawSpeed * 3.6).toFloat(),
                        brakePressed = k.brakePressed,
                        activeNote = u.activeNote,
                        noteElapsedMs = u.activeNote?.let { k.timeMs - it.startMs } ?: 0,
                        lastResult = u.judged.lastOrNull() ?: s.lastResult,
                        resultSeq = s.resultSeq + u.judged.size,
                        combo = u.combo,
                        rideScore = u.rideScore,
                        hardDecelSeq = s.hardDecelSeq + if (u.hardDecelDetected) 1 else 0,
                        decel = (-k.accel).toFloat(),
                        guide = guide ?: s.guide,
                        comboMilestoneSeq = s.comboMilestoneSeq + if (milestone) 1 else 0,
                        forecast = if (kind == SourceKind.SIMULATION) sim.forecast.value else null,
                    )
                }
            }
        }
        viewModelScope.launch {
            sim.playing.collect { p -> _state.update { it.copy(simPlaying = p) } }
        }
        viewModelScope.launch {
            combine(vehicle.speedStatus, vehicle.brakeStatus, vehicle.available) { s, b, ok -> Triple(s, b, ok) }
                .collect { (s, b, ok) ->
                    Log.d(TAG, "vehicle speed=$s brake=$b available=$ok")
                    _state.update { it.copy(speedStatus = s, brakeStatus = b, vehicleAvailable = ok) }
                    // 연결되면 자동으로 차량 데이터 사용 (주행 중이 아닐 때만). 끊기면 시뮬레이션으로.
                    if (ok && !userPickedSource && _state.value.stage != Stage.DRIVING) {
                        switchSource(SourceKind.VEHICLE)
                    } else if (!ok && activeSource.value == SourceKind.VEHICLE) {
                        switchSource(SourceKind.SIMULATION)
                    }
                }
        }
    }

    override fun onCleared() {
        vehicle.stop()
        sfx.release()
    }

    fun setSoundOn(on: Boolean) {
        settings.soundOn = on
        _state.update { it.copy(soundOn = on) }
    }

    /**
     * 노트가 진행 중이면 샘플을 쌓고 가이드를 새로 만든다.
     * 노트가 막 끝났으면 마지막 모습을 active=false로 한 번 돌려준다. 그 외에는 null (변경 없음).
     */
    private fun updateGuide(note: ActiveNote?, timeMs: Long, decel: Double, speed: Double): GuideTrace? {
        if (note == null) {
            if (guideStartMs == null) return null
            guideStartMs = null
            return buildGuide(active = false)
        }
        if (note.startMs != guideStartMs) {
            guideStartMs = note.startMs
            guideType = note.type
            guideSamples.clear()
            guidePeak = 0.0
        }
        val d = decel.coerceAtLeast(0.0)
        guideSamples += GuideSample((timeMs - note.startMs) / 1000.0, d, speed)
        if (guideSamples.size > GUIDE_MAX_SAMPLES) guideSamples.removeAt(0)
        guidePeak = maxOf(guidePeak, d)
        return buildGuide(active = true)
    }

    private fun buildGuide(active: Boolean): GuideTrace? {
        val type = guideType ?: return null
        if (guideSamples.isEmpty()) return null
        val curve = guideCurve
        val target = curve.target(type, guidePeak)
        val actual = guideSamples.map { GuidePt(it.t.toFloat(), it.decel.toFloat()) }
        val ideal = ArrayList<GuidePt>(guideSamples.size + 16)
        guideSamples.forEach { ideal += GuidePt(it.t.toFloat(), curve.idealDecel(type, it.t, it.speed, target).toFloat()) }
        if (active) {
            // 앞으로 1.5초는 지금 속도가 유지된다고 보고 가이드를 미리 그려 둔다
            val last = guideSamples.last()
            var t = last.t + 0.1
            while (t <= last.t + 1.5) {
                ideal += GuidePt(t.toFloat(), curve.idealDecel(type, t, last.speed, target).toFloat())
                t += 0.1
            }
        }
        return GuideTrace(type, actual, ideal, active, guidePeak.toFloat(), curve.endTarget.toFloat())
    }

    /** 속도 런타임 권한 결과. 허용되면 속도 구독을 다시 시도한다. */
    fun onSpeedPermissionResult(granted: Boolean) {
        Log.d(TAG, "speed permission granted=$granted")
        if (granted) vehicle.startSpeed()
    }

    /** 앱으로 돌아올 때: 설정 등에서 권한을 허용했을 수 있으니 다시 확인. */
    fun recheckVehicle() {
        if (_state.value.speedStatus == SignalStatus.NO_PERMISSION) vehicle.startSpeed()
    }

    /** 출발 전 화면에서 데이터 소스 선택. */
    fun selectSource(kind: SourceKind) {
        if (kind == SourceKind.VEHICLE && !_state.value.vehicleAvailable) return
        userPickedSource = true
        switchSource(kind)
    }

    private fun switchSource(kind: SourceKind) {
        if (activeSource.value == kind) return
        Log.d(TAG, "source $kind")
        activeSource.value = kind
        _state.update { it.copy(simulated = kind == SourceKind.SIMULATION) }
        // 시각 기준이 다른 소스로 바뀌므로 판정을 새로 시작한다
        if (_state.value.stage == Stage.DRIVING) restart(_state.value.powertrain, _state.value.passenger)
    }

    fun play(scenario: SimScenario) {
        Log.d(TAG, "play $scenario")
        // 시뮬레이션 버튼은 언제든 동작해야 한다 (시연 안정성)
        if (activeSource.value != SourceKind.SIMULATION) {
            userPickedSource = true
            switchSource(SourceKind.SIMULATION)
        }
        sim.play(scenario)
    }

    fun setPowertrain(p: Powertrain) {
        if (p == _state.value.powertrain) return
        Log.d(TAG, "powertrain $p")
        sim.powertrain = p
        vehicle.powertrain = p
        restart(p, _state.value.passenger)
    }

    fun setPassenger(p: Passenger) {
        if (p == _state.value.passenger) return
        Log.d(TAG, "passenger $p")
        restart(_state.value.powertrain, p)
    }

    /** 출발 전 화면 → 판정 화면. */
    fun startDrive() {
        Log.d(TAG, "start ${_state.value.powertrain} ${_state.value.passenger}")
        restart(_state.value.powertrain, _state.value.passenger, Stage.DRIVING)
    }

    fun finishDrive() {
        if (_state.value.stage != Stage.DRIVING) return
        val judged = engine.finish()
        val report = engine.report()
        val previous = scoreHistory.lastOrNull()
        if (report.noteCount > 0) {
            scoreHistory += report.rideScore
            val needsWork = report.hardDecelCount > 0 ||
                report.notes.any { it.grade == Grade.GOOD || it.grade == Grade.MISS }
            lastDrive = LastDrive(report.rideScore, report.coaching, needsWork)
            historyStore.save(
                DriveHistory(
                    scores = scoreHistory.takeLast(DriveHistory.MAX_SCORES),
                    lastScore = report.rideScore,
                    lastCoaching = report.coaching,
                    lastHasImprovement = needsWork,
                ),
            )
        }
        val ui = ReportUi(
            report = report,
            startMs = driveStartMs ?: lastSampleMs,
            endMs = lastSampleMs,
            speedTrace = speedTrace.toList(),
            recentScores = scoreHistory.takeLast(5),
            previousScore = previous,
        )
        Log.d(TAG, "finish notes=${report.noteCount} score=%.2f".format(report.rideScore))
        _state.update {
            it.copy(
                stage = Stage.REPORT,
                lastDrive = lastDrive,
                activeNote = null,
                lastResult = judged.lastOrNull() ?: it.lastResult,
                report = ui,
            )
        }
    }

    /** 리포트 → 출발 전 화면. */
    fun newDrive() = restart(_state.value.powertrain, _state.value.passenger, Stage.PRE_DRIVE)

    private fun recordTrace(timeMs: Long, kmh: Float) {
        if (driveStartMs == null) driveStartMs = timeMs
        lastSampleMs = timeMs
        if (speedTrace.isEmpty() || timeMs - speedTrace.last().timeMs >= TRACE_INTERVAL_MS) {
            speedTrace += SpeedPoint(timeMs, kmh)
            if (speedTrace.size > TRACE_MAX) {
                // 오래 달리면 절반으로 솎아 낸다
                val kept = speedTrace.filterIndexed { i, _ -> i % 2 == 0 }
                speedTrace.clear()
                speedTrace.addAll(kept)
            }
        }
    }

    private fun restart(powertrain: Powertrain, passenger: Passenger, stage: Stage = _state.value.stage) {
        engine = JudgeEngine(powertrain, passenger)
        guideCurve = GuideCurve(engine.config, passenger)
        guideStartMs = null
        guideType = null
        guideSamples.clear()
        guidePeak = 0.0
        driveStartMs = null
        speedTrace.clear()
        _state.update {
            DriveUiState(
                stage = stage,
                soundOn = it.soundOn,
                lastDrive = lastDrive,
                simulated = it.simulated,
                vehicleAvailable = it.vehicleAvailable,
                speedStatus = it.speedStatus,
                brakeStatus = it.brakeStatus,
                powertrain = powertrain,
                passenger = passenger,
                speedKmh = it.speedKmh,
                brakePressed = it.brakePressed,
                simPlaying = it.simPlaying,
            )
        }
    }
}
