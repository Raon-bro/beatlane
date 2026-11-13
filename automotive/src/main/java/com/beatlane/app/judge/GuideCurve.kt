package com.beatlane.app.judge

import kotlin.math.max
import kotlin.math.min

/**
 * "따라 그리기" 가이드: 지금 노트에서 이상적인 감속도(양수, m/s²)가 시간에 따라 어떻게 가야 하는지.
 *
 * - 올라갈 때: PERFECT 저크 한계(동승자 보정 포함)를 넘지 않는 기울기로 목표 감속도까지.
 * - 브레이크 노트는 속도가 [JudgeConfig.endPhaseSpeed] 아래로 내려가면
 *   정지 직전 목표(END PERFECT 기준)까지 속도에 비례해 풀어 준다. ("멈추기 직전 살짝 풀기")
 * - 회생 노트는 풀기 구간이 없다.
 *
 * 예고가 아니라 "지금 밟고 있는 노트"를 기준으로만 그린다 (1단계 범위).
 */
class GuideCurve(config: JudgeConfig, passenger: Passenger) {

    /** 올라가는 기울기 한계 (m/s³). */
    val jerkLimit: Double = config.jerk.perfect * passenger.factor

    /** 정지 순간 목표 감속도 (m/s²). */
    val endTarget: Double = config.endDecel.perfect * passenger.factor

    private val endPhaseSpeed = config.endPhaseSpeed

    /**
     * 가이드가 향하는 감속도 수준. 운전자가 실제로 쓴 최대 감속을 기준으로 하되
     * 너무 약하거나 강하면 현실적인 범위로 자른다.
     */
    fun target(type: NoteType, peakDecelSoFar: Double): Double = when (type) {
        NoteType.BRAKE -> peakDecelSoFar.coerceIn(BRAKE_MIN, BRAKE_MAX)
        NoteType.REGEN -> peakDecelSoFar.coerceIn(REGEN_MIN, REGEN_MAX)
    }

    /**
     * @param tSec 노트 시작 후 경과 시간 (초). 음수면 0으로 본다.
     * @param speedMps 그 시점의 속도 (m/s).
     * @param target [target]으로 구한 목표 감속도.
     */
    fun idealDecel(type: NoteType, tSec: Double, speedMps: Double, target: Double): Double {
        val ramp = min(target, jerkLimit * max(0.0, tSec))
        if (type == NoteType.REGEN || speedMps >= endPhaseSpeed) return ramp
        val ratio = (speedMps / endPhaseSpeed).coerceIn(0.0, 1.0)
        val taper = endTarget + (target - endTarget) * ratio
        return min(ramp, taper)
    }

    companion object {
        const val BRAKE_MIN = 1.0
        const val BRAKE_MAX = 3.5
        const val REGEN_MIN = 0.6
        const val REGEN_MAX = 2.0
    }
}
