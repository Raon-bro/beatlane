package com.beatlane.app.judge

/** 값이 [perfect] 이하면 PERFECT, [great] 이하면 GREAT, [good] 이하면 GOOD, 그 이상은 MISS. */
data class GradeThresholds(val perfect: Double, val great: Double, val good: Double) {
    /** [factor]는 동승자 보정 계수. 임계값에 곱한다. */
    fun grade(value: Double, factor: Double): Grade = when {
        value <= perfect * factor -> Grade.PERFECT
        value <= great * factor -> Grade.GREAT
        value <= good * factor -> Grade.GOOD
        else -> Grade.MISS
    }
}

/**
 * 판정 상수. 실차 보정 전 임시값이므로 여기 한 곳에서만 바꾼다.
 * 단위: 속도 m/s, 가속도 m/s², 저크 m/s³, 시간 초.
 */
data class JudgeConfig(
    /** 속도 이동평균 샘플 수. */
    val smoothingWindow: Int = 5,

    /** START / LIFT: 최대 |j| (CLAUDE.md 기준값). */
    val jerk: GradeThresholds = GradeThresholds(1.5, 2.5, 4.0),
    /** HOLD: 감속도 표준편차. */
    val holdStd: GradeThresholds = GradeThresholds(0.4, 0.7, 1.0),
    /** END: 정지 직전 평균 |a|. 작을수록 "멈추기 직전 살짝 풀기"가 잘 된 것. */
    val endDecel: GradeThresholds = GradeThresholds(0.8, 1.3, 2.0),

    /** 노트 시작 후 START(LIFT) 판정 구간 길이. */
    val startWindowSec: Double = 1.0,
    /** 정지 직전 END 구간 길이 (HOLD 구간에서 제외). */
    val endWindowSec: Double = 1.0,
    /** END 판정에 쓰는 정지 직전 평균 구간. */
    val endDecelSampleSec: Double = 0.5,
    /** 이 속도 아래로 내려가면 END 구간으로 본다 (화면 표시 + HOLD 제외). */
    val endPhaseSpeed: Double = 2.5,
    /** 브레이크를 떼서 끝난 노트에서 HOLD 구간 끝부분을 잘라낼 길이 (페달을 푸는 동작 제외). */
    val releaseTrimSec: Double = 0.5,
    /** HOLD 구간이 이보다 짧으면 HOLD는 판정하지 않는다. */
    val minHoldSec: Double = 0.5,

    /** 이 속도 이하면 정지로 본다. */
    val stopSpeed: Double = 0.3,
    /** 이 속도 이하에서 시작한 감속은 노트로 만들지 않는다 (정차 중 브레이크 등). */
    val minNoteSpeed: Double = 1.0,

    /** 회생 노트 시작: 브레이크 off + a < 이 값. */
    val regenStartAccel: Double = -0.5,
    /** 회생 노트 끝: a > 이 값 (시작값과 차이를 둬서 깜빡임 방지). */
    val regenEndAccel: Double = -0.3,
    /** 회생 시작을 감지하기 전 저크도 놓치지 않도록 앞쪽으로 더 보는 구간. */
    val regenLookbackSec: Double = 0.3,

    /** 급감속: 감속도가 이 값을 넘는 상태가 */
    val hardDecel: Double = 3.0,
    /** 이 시간 이상 이어지면 1회. */
    val hardDecelMinSec: Double = 0.5,

    /** 승차감 점수에 반영하는 최근 노트 수. */
    val scoreWindow: Int = 10,
)
