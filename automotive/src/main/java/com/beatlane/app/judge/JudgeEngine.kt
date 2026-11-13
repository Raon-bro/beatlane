package com.beatlane.app.judge

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * 감속 판정 엔진. 순수 Kotlin (Android 의존성 없음).
 *
 * 샘플을 [process]로 순서대로 넣으면 노트를 열고 닫으며 판정한다.
 * 주행을 마칠 때 [finish]로 진행 중인 노트를 마무리하고 [report]로 리포트를 만든다.
 * 스레드 안전하지 않다. 한 코루틴/스레드에서만 호출할 것.
 */
class JudgeEngine(
    val powertrain: Powertrain,
    val passenger: Passenger,
    val config: JudgeConfig = JudgeConfig(),
) {
    private class Recording(val type: NoteType, val startMs: Long) {
        val points = mutableListOf<Kinematics>()
        /** 브레이크 노트에서 END 구간(저속)에 처음 들어간 시각. */
        var endPhaseMs: Long? = null
    }

    private val filter = KinematicsFilter(config.smoothingWindow)
    /** 회생 노트 lookback 용 최근 샘플. */
    private val recent = ArrayDeque<Kinematics>()
    private var active: Recording? = null
    private var prevBrake = false
    private var hardDecelSinceMs: Long? = null
    private var hardDecelCounted = false
    private val hardDecelTimes = mutableListOf<Long>()

    private val _notes = mutableListOf<NoteResult>()
    val notes: List<NoteResult> get() = _notes

    var combo = 0
        private set
    var maxCombo = 0
        private set
    var hardDecelCount = 0
        private set

    /** 최근 노트 등급의 가중 평균 (최근일수록 가중치 큼). 노트가 없으면 5.0. */
    val rideScore: Double
        get() {
            val recentNotes = _notes.takeLast(config.scoreWindow)
            if (recentNotes.isEmpty()) return 5.0
            var sum = 0.0
            var weights = 0.0
            recentNotes.forEachIndexed { i, note ->
                val w = (i + 1).toDouble()
                sum += note.grade.points * w
                weights += w
            }
            return sum / weights
        }

    fun process(sample: VehicleSample): JudgeUpdate {
        val k = filter.update(sample)
        val judged = mutableListOf<NoteResult>()

        val brakeEdge = k.brakePressed && !prevBrake
        prevBrake = k.brakePressed
        val stopped = k.rawSpeed <= config.stopSpeed

        // 1) 진행 중인 노트 갱신 / 종료
        active?.let { rec ->
            rec.points += k
            when (rec.type) {
                NoteType.BRAKE -> {
                    if (rec.endPhaseMs == null && k.rawSpeed < config.endPhaseSpeed) {
                        rec.endPhaseMs = k.timeMs
                    }
                    if (stopped) {
                        judged += close(rec, k.timeMs, byStop = true)
                    } else if (!k.brakePressed) {
                        judged += close(rec, k.timeMs, byStop = false)
                    }
                }
                NoteType.REGEN -> {
                    // 브레이크가 눌리면 회생 노트를 닫고 아래에서 브레이크 노트를 연다.
                    if (stopped) {
                        judged += close(rec, k.timeMs, byStop = true)
                    } else if (k.brakePressed || k.accel > config.regenEndAccel) {
                        judged += close(rec, k.timeMs, byStop = false)
                    }
                }
            }
        }

        // 2) 새 노트 시작
        if (active == null && k.rawSpeed > config.minNoteSpeed) {
            if (brakeEdge) {
                active = Recording(NoteType.BRAKE, k.timeMs).also { it.points += k }
            } else if (
                powertrain == Powertrain.ELECTRIFIED &&
                !k.brakePressed &&
                k.accel < config.regenStartAccel
            ) {
                active = Recording(NoteType.REGEN, k.timeMs).also { rec ->
                    val from = k.timeMs - config.regenLookbackSec.toMs()
                    rec.points += recent.filter { it.timeMs >= from && !it.brakePressed }
                    rec.points += k
                }
            }
        }

        recent.addLast(k)
        val keepFrom = k.timeMs - config.regenLookbackSec.toMs()
        while (recent.isNotEmpty() && recent.first().timeMs < keepFrom) recent.removeFirst()

        // 3) 급감속
        val hardDecelDetected = trackHardDecel(k)

        return JudgeUpdate(
            kinematics = k,
            activeNote = active?.let { ActiveNote(it.type, it.startMs, phaseOf(it, k.timeMs)) },
            judged = judged,
            hardDecelDetected = hardDecelDetected,
            combo = combo,
            rideScore = rideScore,
        )
    }

    /** 주행 종료: 진행 중인 노트가 있으면 판정하고 돌려준다. */
    fun finish(): List<NoteResult> {
        val rec = active ?: return emptyList()
        val endMs = rec.points.last().timeMs
        return listOf(close(rec, endMs, byStop = false))
    }

    fun report(): DriveReport {
        val counts = Grade.entries.associateWith { g -> _notes.count { it.grade == g } }
        val score = rideScore
        return DriveReport(
            powertrain = powertrain,
            passenger = passenger,
            rideScore = score,
            stars = (score * 2).roundToLong() / 2.0,
            gradeCounts = counts,
            noteCount = _notes.size,
            hardDecelCount = hardDecelCount,
            maxCombo = maxCombo,
            coaching = Coach.message(powertrain, _notes, hardDecelCount),
            notes = _notes.toList(),
            hardDecelTimesMs = hardDecelTimes.toList(),
        )
    }

    private fun phaseOf(rec: Recording, nowMs: Long): NotePhase {
        val inStart = nowMs - rec.startMs <= config.startWindowSec.toMs()
        return when (rec.type) {
            NoteType.BRAKE -> when {
                rec.endPhaseMs != null -> NotePhase.END
                inStart -> NotePhase.START
                else -> NotePhase.HOLD
            }
            NoteType.REGEN -> if (inStart) NotePhase.LIFT else NotePhase.REGEN
        }
    }

    private fun close(rec: Recording, endMs: Long, byStop: Boolean): NoteResult {
        active = null
        val result = evaluate(rec, endMs, byStop)
        _notes += result
        if (result.grade.breaksCombo) {
            combo = 0
        } else {
            combo++
            if (combo > maxCombo) maxCombo = combo
        }
        return result
    }

    private fun evaluate(rec: Recording, endMs: Long, byStop: Boolean): NoteResult {
        val f = passenger.factor
        val startWindowEnd = rec.startMs + config.startWindowSec.toMs()

        val startJerk = rec.points
            .filter { it.timeMs <= startWindowEnd }
            .maxOfOrNull { abs(it.jerk) } ?: 0.0
        val startGrade = config.jerk.grade(startJerk, f)

        if (rec.type == NoteType.REGEN) {
            return NoteResult(
                type = rec.type, startMs = rec.startMs, endMs = endMs,
                grade = startGrade,
                startJerk = startJerk, startGrade = startGrade,
                holdStd = null, holdGrade = null,
                endDecel = null, endGrade = null,
                endedByStop = byStop,
            )
        }

        // HOLD: START 구간 이후 ~ (정지: END 구간 전 / 떼기: 푸는 동작 전)
        val holdEnd = if (byStop) {
            min(endMs - config.endWindowSec.toMs(), rec.endPhaseMs ?: Long.MAX_VALUE)
        } else {
            endMs - config.releaseTrimSec.toMs()
        }
        var holdStd: Double? = null
        if (holdEnd - startWindowEnd >= config.minHoldSec.toMs()) {
            val holdAccels = rec.points
                .filter { it.timeMs > startWindowEnd && it.timeMs <= holdEnd }
                .map { it.accel }
            if (holdAccels.size >= 3) holdStd = stdDev(holdAccels)
        }
        val holdGrade = holdStd?.let { config.holdStd.grade(it, f) }

        // END: 정지 직전 평균 감속도
        val endDecel = if (byStop) {
            val from = endMs - config.endDecelSampleSec.toMs()
            rec.points.filter { it.timeMs >= from }.map { abs(it.accel) }.average()
        } else null
        val endGrade = endDecel?.let { config.endDecel.grade(it, f) }

        val grade = Grade.worstOf(listOfNotNull(startGrade, holdGrade, endGrade))
        return NoteResult(
            type = rec.type, startMs = rec.startMs, endMs = endMs,
            grade = grade,
            startJerk = startJerk, startGrade = startGrade,
            holdStd = holdStd, holdGrade = holdGrade,
            endDecel = endDecel, endGrade = endGrade,
            endedByStop = byStop,
        )
    }

    private fun trackHardDecel(k: Kinematics): Boolean {
        if (-k.accel > config.hardDecel) {
            val since = hardDecelSinceMs ?: k.timeMs.also { hardDecelSinceMs = it }
            if (!hardDecelCounted && k.timeMs - since >= config.hardDecelMinSec.toMs()) {
                hardDecelCounted = true
                hardDecelCount++
                hardDecelTimes += since
                return true
            }
        } else {
            hardDecelSinceMs = null
            hardDecelCounted = false
        }
        return false
    }

    private fun Double.toMs(): Long = (this * 1000).roundToLong()

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
