package com.beatlane.app.data.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadCarPlanTest {

    private val hz = SimScenario.DEFAULT_HZ
    private val dt = 1.0 / hz

    private fun plan(s: SimScenario, startSpeed: Double): Pair<Scenario, LeadCarPlan> {
        val scenario = s.build(startSpeed, hz)
        return scenario to LeadCarPlan(scenario)
    }

    /** 점 번호별 누적 주행 거리 (시뮬레이터와 같은 방식). */
    private fun travelled(scenario: Scenario): DoubleArray {
        val cum = DoubleArray(scenario.points.size)
        for (i in 1 until scenario.points.size) cum[i] = cum[i - 1] + scenario.points[i].speed * dt
        return cum
    }

    @Test
    fun `브레이크 시나리오의 시작선은 브레이크를 밟는 순간 판정선에 닿는다`() {
        for (s in listOf(SimScenario.SMOOTH_STOP, SimScenario.HARD_STOP)) {
            val (scenario, plan) = plan(s, 0.0)
            val cum = travelled(scenario)
            assertTrue(plan.byBrake)
            assertTrue(scenario.points[plan.startIndex].brakePressed)
            assertFalse(scenario.points[plan.startIndex - 1].brakePressed)

            val f = plan.forecastAt(plan.startIndex, cum[plan.startIndex], tick = 0)
            assertEquals(0f, f.startAheadM, 1e-3f)
            assertEquals(0f, f.secondsToStart, 1e-6f)
            assertTrue(f.endAheadM > 10f)
        }
    }

    @Test
    fun `브레이크 시나리오의 끝선은 멈추는 순간 판정선에 닿고 앞차와 안전거리가 남는다`() {
        for (s in listOf(SimScenario.SMOOTH_STOP, SimScenario.HARD_STOP)) {
            val (scenario, plan) = plan(s, 0.0)
            val cum = travelled(scenario)
            assertEquals(0.0, scenario.points[plan.endIndex].speed, 0.0)

            val f = plan.forecastAt(plan.endIndex, cum[plan.endIndex], tick = 0)
            assertEquals(0f, f.endAheadM, 1e-3f)
            assertEquals(LeadCarPlan.SAFE_GAP_M.toFloat(), f.leadAheadM, 1e-3f)
            assertEquals(0f, f.leadSpeedMps, 0f)
        }
    }

    @Test
    fun `회생제동 시나리오는 발을 떼는 순간 시작하고 끝난 뒤 앞차와 같은 속도로 달린다`() {
        for (s in listOf(SimScenario.REGEN_SLOW, SimScenario.REGEN_FAST)) {
            val (scenario, plan) = plan(s, 0.0)
            val cum = travelled(scenario)
            assertFalse(plan.byBrake)
            assertTrue(scenario.points[plan.startIndex].speed < scenario.points[plan.startIndex - 1].speed)
            assertTrue(plan.endSpeed > 0.0)

            val f = plan.forecastAt(plan.endIndex, cum[plan.endIndex], tick = 0)
            assertEquals(0f, f.endAheadM, 1e-3f)
            assertEquals(plan.endSpeed.toFloat(), f.leadSpeedMps, 1e-3f)
            assertTrue(f.leadAheadM >= LeadCarPlan.SAFE_GAP_M.toFloat())

            // 시나리오가 끝난 뒤 같은 속도로 5초 더 달려도 간격이 그대로다
            val later = plan.forecastAt(plan.endIndex + 5 * hz, cum[plan.endIndex] + plan.endSpeed * 5, tick = 0)
            assertEquals(f.leadAheadM, later.leadAheadM, 1e-2f)
        }
    }

    @Test
    fun `앞차는 언제나 노트 끝선과 우리 차보다 앞에 있다`() {
        for (s in SimScenario.entries) {
            for (startSpeed in listOf(0.0, 20.0 / 3.6, SimScenario.CRUISE_SPEED)) {
                val (scenario, plan) = plan(s, startSpeed)
                val cum = travelled(scenario)
                for (i in scenario.points.indices) {
                    val f = plan.forecastAt(i, cum[i], tick = i.toLong())
                    assertTrue(f.leadAheadM > f.endAheadM)
                    assertTrue(f.leadAheadM > 1f)
                    assertTrue(f.endAheadM > f.startAheadM)
                }
            }
        }
    }

    @Test
    fun `예고 시간은 감속 전에 남아 있고 노트는 감속이 끝난 뒤 잠시 후 숨는다`() {
        for (s in SimScenario.entries) {
            val (scenario, plan) = plan(s, SimScenario.CRUISE_SPEED)
            val cum = travelled(scenario)
            val early = plan.forecastAt(0, 0.0, tick = 0)
            assertEquals(SimScenario.CRUISE_HOLD_SEC.toFloat(), early.secondsToStart, 0.11f)
            assertTrue(early.noteVisible)

            val last = scenario.points.lastIndex
            assertFalse(plan.forecastAt(last, cum[last], tick = 0).noteVisible)
        }
    }
}
