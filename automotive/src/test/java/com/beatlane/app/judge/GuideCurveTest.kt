package com.beatlane.app.judge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCurveTest {
    private val config = JudgeConfig()
    private val none = GuideCurve(config, Passenger.NONE)

    @Test
    fun rampNeverExceedsPerfectJerk() {
        val target = 2.0
        val dt = 0.05
        var prev = none.idealDecel(NoteType.BRAKE, 0.0, 14.0, target)
        var t = dt
        while (t < 5.0) {
            val v = none.idealDecel(NoteType.BRAKE, t, 14.0, target)
            assertTrue("slope at t=$t", (v - prev) / dt <= config.jerk.perfect + 1e-9)
            prev = v
            t += dt
        }
        assertEquals(target, prev, 1e-9) // 결국 목표에서 평평하게
    }

    @Test
    fun brakeTapersToEndTargetAtStop() {
        val target = 2.0
        assertEquals(target, none.idealDecel(NoteType.BRAKE, 5.0, config.endPhaseSpeed, target), 1e-9)
        assertEquals(none.endTarget, none.idealDecel(NoteType.BRAKE, 5.0, 0.0, target), 1e-9)
        // 그 사이는 단조 감소
        val mid = none.idealDecel(NoteType.BRAKE, 5.0, config.endPhaseSpeed / 2, target)
        assertTrue(mid < target && mid > none.endTarget)
    }

    @Test
    fun regenHasNoTaper() {
        assertEquals(1.0, none.idealDecel(NoteType.REGEN, 5.0, 0.5, 1.0), 1e-9)
    }

    @Test
    fun elderlyPassengerMeansGentlerGuide() {
        val elderly = GuideCurve(config, Passenger.ELDERLY)
        assertTrue(elderly.jerkLimit < none.jerkLimit)
        assertTrue(elderly.endTarget < none.endTarget)
        assertTrue(
            elderly.idealDecel(NoteType.BRAKE, 0.5, 14.0, 2.0) < none.idealDecel(NoteType.BRAKE, 0.5, 14.0, 2.0),
        )
    }

    @Test
    fun targetIsClampedToRealisticRange() {
        assertEquals(GuideCurve.BRAKE_MAX, none.target(NoteType.BRAKE, 6.0), 0.0)
        assertEquals(GuideCurve.BRAKE_MIN, none.target(NoteType.BRAKE, 0.2), 0.0)
        assertEquals(GuideCurve.REGEN_MAX, none.target(NoteType.REGEN, 2.5), 0.0)
    }

    @Test
    fun negativeTimeIsZero() {
        assertEquals(0.0, none.idealDecel(NoteType.REGEN, -0.3, 10.0, 1.0), 0.0)
    }
}
