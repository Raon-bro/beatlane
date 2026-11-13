package com.beatlane.app.data.sim

import kotlin.math.max

/**
 * 화면 표시용 예고 정보 (시뮬레이션 전용).
 *
 * 거리는 모두 "판정선에서 앞으로 몇 m" (음수 = 이미 판정선을 지나감).
 * 출처는 센서가 아니라 재생 중인 시나리오 자체다: 시나리오는 미리 정해져 있으므로
 * 언제 감속을 시작하고 어디서 끝낼지 알 수 있다. 실제 차량 데이터에서는 만들지 않는다.
 */
data class SimForecast(
    /** 값이 갱신될 때마다 바뀐다 (샘플 틱). */
    val tick: Long,
    /** 감속 노트 시작선(감속 시작 지점)까지. */
    val startAheadM: Float,
    /** 감속 노트 끝선(감속이 끝나는 지점)까지. */
    val endAheadM: Float,
    /** 앞차 뒤 범퍼까지. */
    val leadAheadM: Float,
    /** 앞차의 지금 속도 (m/s). 값 사이 프레임에서 앞차 위치를 이어 그리는 데 쓴다. */
    val leadSpeedMps: Float,
    /** 감속을 시작하기까지 남은 시간 (시작한 뒤에는 0). */
    val secondsToStart: Float,
    /** true = 브레이크로 감속, false = 회생제동(가속 페달에서 발 떼기). */
    val byBrake: Boolean,
    /** 노트를 그릴지 (감속이 끝나고 잠시 뒤 숨긴다. 앞차는 계속 보인다). */
    val noteVisible: Boolean,
)

/**
 * 시나리오의 도로 위 배치 (모든 시뮬레이션 시나리오 공통).
 *
 * 시나리오 점들로 우리 차의 누적 주행 거리를 구해
 * - 노트 시작선 = 감속을 시작하는 지점 (브레이크를 밟거나, 회생제동이면 발을 떼는 지점)
 * - 노트 끝선 = 감속이 끝나는 지점 (정지하거나, 회생제동이면 감속이 풀리는 지점)
 * - 앞차 = 끝선보다 [SAFE_GAP_M] 앞
 * 으로 정한다. 그래서 시작선이 판정선에 닿는 순간 감속이 시작되고,
 * 끝선이 판정선에 닿는 순간 감속이 끝난다.
 *
 * 앞차 움직임: 거의 멈춘 상태로 조금 굴러가다 멈춘다. 우리 차가 멈추지 않고 끝나는
 * 시나리오(회생제동)에서는, 우리가 도착하기 직전에 다시 출발해 우리 차와 같은 속도로 달린다.
 */
class LeadCarPlan(scenario: Scenario) {
    private val hz = scenario.hz
    private val dt = 1.0 / hz

    /** 감속을 시작하는 점. */
    val startIndex: Int

    /** 감속이 끝나는 점. */
    val endIndex: Int

    /** 시나리오 시작 지점에서 노트 시작선까지 거리 (m). */
    val startM: Double

    /** 시나리오 시작 지점에서 노트 끝선까지 거리 (m). */
    val endM: Double

    /** 브레이크로 감속하는 시나리오인지. */
    val byBrake: Boolean

    /** 감속이 끝났을 때 우리 차 속도 (정지면 0). 앞차도 결국 이 속도로 달린다. */
    val endSpeed: Double

    private val leadStopM: Double
    private val leadStopSec: Double
    private val restartSec: Double
    private val endSec: Double

    init {
        val points = scenario.points
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + points[i].speed * dt

        val firstBrake = points.indexOfFirst { it.brakePressed }
        startIndex = when {
            scenario.eventStart in points.indices -> scenario.eventStart
            firstBrake >= 0 -> firstBrake
            else -> points.lastIndex
        }
        var stop = points.lastIndex
        for (i in startIndex..points.lastIndex) {
            if (points[i].speed <= 0.0) {
                stop = i
                break
            }
        }
        endIndex = if (scenario.eventEnd in startIndex..points.lastIndex) scenario.eventEnd else stop
        byBrake = points[startIndex].brakePressed
        startM = cum[startIndex]
        endM = cum[endIndex]
        endSpeed = points[endIndex].speed
        endSec = endIndex * dt

        // 굴러가는 거리를 안전거리보다 짧게 잡아서, 앞차는 언제나 노트 끝선보다 앞에 있다.
        leadStopM = endM + SAFE_GAP_M
        leadStopSec = startIndex * dt + LEAD_STOP_AFTER_START_SEC
        restartSec = max(leadStopSec, endSec - LEAD_RESTART_SEC)
    }

    /** 다시 출발하는 데 걸리는 시간 (우리 차가 멈추는 시나리오에서는 쓰이지 않는다). */
    private val restartSpan: Double get() = endSec - restartSec

    /** 시나리오 시작 후 [tSec]초일 때 앞차 뒤 범퍼 위치 (시작 지점 기준 m). */
    fun leadPositionM(tSec: Double): Double {
        val t = max(0.0, tSec)
        val remain = if (t >= leadStopSec) 0.0 else 1.0 - t / leadStopSec
        val base = leadStopM - LEAD_ROLL_M * remain * remain
        if (endSpeed <= 0.0 || t <= restartSec) return base
        val span = restartSpan
        if (span <= 0.0) return base + endSpeed * (t - endSec)
        // 0 → endSpeed 로 일정하게 가속한 뒤 그 속도 유지
        return if (t < endSec) {
            base + endSpeed * (t - restartSec) * (t - restartSec) / (2 * span)
        } else {
            base + endSpeed * span / 2 + endSpeed * (t - endSec)
        }
    }

    /** 시나리오 시작 후 [tSec]초일 때 앞차 속도 (m/s). */
    fun leadSpeedMps(tSec: Double): Double {
        val t = max(0.0, tSec)
        val roll = if (t >= leadStopSec) 0.0 else 2 * LEAD_ROLL_M * (1.0 - t / leadStopSec) / leadStopSec
        if (endSpeed <= 0.0 || t <= restartSec) return roll
        val span = restartSpan
        return if (span > 0.0 && t < endSec) roll + endSpeed * (t - restartSec) / span else roll + endSpeed
    }

    /**
     * @param index 지금까지 재생한 점 번호 (0 = 시작 상태). 시나리오가 끝난 뒤에도 계속 늘려서 넘긴다.
     * @param travelledM 시나리오 시작 후 우리 차가 달린 거리
     */
    fun forecastAt(index: Int, travelledM: Double, tick: Long): SimForecast = SimForecast(
        tick = tick,
        startAheadM = (startM - travelledM).toFloat(),
        endAheadM = (endM - travelledM).toFloat(),
        leadAheadM = (leadPositionM(index * dt) - travelledM).toFloat(),
        leadSpeedMps = leadSpeedMps(index * dt).toFloat(),
        secondsToStart = (max(0, startIndex - index) * dt).toFloat(),
        byBrake = byBrake,
        noteVisible = index <= endIndex + (NOTE_LINGER_SEC * hz).toInt(),
    )

    companion object {
        /** 감속이 끝났을 때 판정선에서 앞차 뒤 범퍼까지 남기는 최소 거리. */
        const val SAFE_GAP_M = 7.0

        /** 앞차가 멈추기 전까지 굴러가는 거리. [SAFE_GAP_M]보다 짧아야 한다. */
        const val LEAD_ROLL_M = 5.0

        /** 우리가 감속을 시작한 뒤 앞차가 완전히 멈추기까지. */
        const val LEAD_STOP_AFTER_START_SEC = 1.0

        /** (우리 차가 멈추지 않는 시나리오) 우리가 도착하기 이만큼 전에 앞차가 다시 출발한다. */
        const val LEAD_RESTART_SEC = 2.0

        /** 감속이 끝난 뒤 노트를 화면에 남겨 두는 시간. */
        const val NOTE_LINGER_SEC = 1.2
    }
}
