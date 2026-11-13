package com.beatlane.app.judge

/** 차종 모드. 전기·하이브리드만 회생 노트를 판정한다. */
enum class Powertrain { GASOLINE, ELECTRIFIED }

/** 동승자. [factor]를 판정 임계값에 곱한다 (작을수록 엄격). */
enum class Passenger(val factor: Double) {
    NONE(1.0),
    GENERAL(0.9),
    CHILD(0.75),
    ELDERLY(0.6),
}

/** 판정 등급. [points]는 승차감 점수(5점 만점) 계산용. */
enum class Grade(val points: Double) {
    PERFECT(5.0),
    GREAT(4.0),
    GOOD(3.0),
    MISS(1.0);

    val breaksCombo: Boolean get() = this == MISS

    companion object {
        fun worstOf(grades: List<Grade>): Grade = grades.maxBy { it.ordinal }
    }
}

enum class NoteType { BRAKE, REGEN }

/**
 * 노트 안의 구간.
 * 브레이크 노트: START → HOLD → END. 회생 노트: LIFT(발 떼기) → REGEN(회생 유지).
 */
enum class NotePhase { START, HOLD, END, LIFT, REGEN }

/** 판정 엔진 입력 한 개. [speed]는 m/s, [timeMs]는 단조 증가하는 시각. */
data class VehicleSample(
    val timeMs: Long,
    val speed: Double,
    val brakePressed: Boolean,
)

/** 보정된 속도와 그로부터 계산한 가속도·저크. */
data class Kinematics(
    val timeMs: Long,
    val rawSpeed: Double,
    val speed: Double,
    /** m/s². 음수 = 감속. */
    val accel: Double,
    /** m/s³. */
    val jerk: Double,
    val brakePressed: Boolean,
)

/** 판정이 끝난 노트 한 개. */
data class NoteResult(
    val type: NoteType,
    val startMs: Long,
    val endMs: Long,
    val grade: Grade,
    /** START(브레이크) 또는 LIFT(회생) 구간의 최대 |j|. */
    val startJerk: Double,
    val startGrade: Grade,
    /** HOLD 구간 감속도 표준편차. 구간이 너무 짧으면 null. */
    val holdStd: Double?,
    val holdGrade: Grade?,
    /** 정지 직전 평균 |a|. 정지로 끝나지 않았으면 null. */
    val endDecel: Double?,
    val endGrade: Grade?,
    val endedByStop: Boolean,
) {
    /** 가장 점수를 깎은 구간 (코칭 문구용). 모두 PERFECT면 null. */
    val weakestPhase: NotePhase?
        get() {
            val parts = buildList {
                add((if (type == NoteType.BRAKE) NotePhase.START else NotePhase.LIFT) to startGrade)
                holdGrade?.let { add(NotePhase.HOLD to it) }
                endGrade?.let { add(NotePhase.END to it) }
            }
            val worst = parts.maxBy { it.second.ordinal }
            return if (worst.second == Grade.PERFECT) null else worst.first
        }
}

/** 지금 진행 중인 노트 (화면의 노트 레인 표시용). */
data class ActiveNote(
    val type: NoteType,
    val startMs: Long,
    val phase: NotePhase,
)

/** 샘플 한 개를 처리한 결과. */
data class JudgeUpdate(
    val kinematics: Kinematics,
    val activeNote: ActiveNote?,
    /** 이번 샘플에서 판정이 끝난 노트들 (보통 0~1개). */
    val judged: List<NoteResult>,
    /** 이번 샘플에서 급감속이 새로 확정됐는지. */
    val hardDecelDetected: Boolean,
    val combo: Int,
    val rideScore: Double,
)

/** 주행 후 리포트. */
data class DriveReport(
    val powertrain: Powertrain,
    val passenger: Passenger,
    /** 0.0~5.0 */
    val rideScore: Double,
    /** 별점 (0.5 단위 반올림). */
    val stars: Double,
    val gradeCounts: Map<Grade, Int>,
    val noteCount: Int,
    val hardDecelCount: Int,
    val maxCombo: Int,
    val coaching: String,
    /** 판정된 노트 전체 (시간순). */
    val notes: List<NoteResult>,
    /** 급감속이 시작된 시각들. */
    val hardDecelTimesMs: List<Long>,
)
