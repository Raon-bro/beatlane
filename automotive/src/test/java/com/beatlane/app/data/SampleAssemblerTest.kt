package com.beatlane.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleAssemblerTest {

    @Test
    fun noSampleUntilFirstSpeed() {
        val a = SampleAssembler()
        a.setBrake(true)
        assertNull(a.onTick(1_000))
    }

    @Test
    fun throttlesFastSpeedEventsTo20Hz() {
        val a = SampleAssembler(minIntervalMs = 45)
        // 100Hz 이벤트 1초
        val emitted = (0 until 100).mapNotNull { i -> a.onSpeed(i * 10L, 10.0) }
        assertTrue("got ${emitted.size}", emitted.size in 19..21)
        emitted.zipWithNext().forEach { (x, y) -> assertTrue(y.timeMs - x.timeMs >= 45) }
    }

    @Test
    fun tickFillsGapWhenSpeedStopsChanging() {
        val a = SampleAssembler(staleMs = 150)
        assertNotNull(a.onSpeed(0, 5.0))
        assertNull(a.onTick(100))
        a.setBrake(true)
        val s = a.onTick(160)
        assertNotNull(s)
        assertEquals(5.0, s!!.speed, 0.0)
        assertTrue(s.brakePressed)
    }

    @Test
    fun timeNeverGoesBackwards() {
        val a = SampleAssembler()
        assertNotNull(a.onSpeed(1_000, 3.0))
        // 이미 1000ms 샘플이 나간 뒤 더 이른 시각의 이벤트는 버린다
        assertNull(a.onSpeed(900, 3.0))
        assertNull(a.onTick(1_050))
    }

    @Test
    fun negativeSpeedUsesMagnitude() {
        val a = SampleAssembler()
        assertEquals(2.0, a.onSpeed(0, -2.0)!!.speed, 0.0)
    }
}
