package com.beatlane.app.data

import com.beatlane.app.data.sim.SimScenario
import com.beatlane.app.judge.Grade
import com.beatlane.app.judge.JudgeEngine
import com.beatlane.app.judge.NoteType
import com.beatlane.app.judge.Passenger
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.judge.VehicleSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedVehicleDataSourceTest {

    @Test
    fun idle_emitsSteadySamplesAt20Hz() = runTest {
        val source = SimulatedVehicleDataSource(Powertrain.GASOLINE, backgroundScope, initialSpeed = 5.0, clockMs = { testScheduler.currentTime })
        val samples = mutableListOf<VehicleSample>()
        backgroundScope.launch { source.samples.collect { samples += it } }

        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("got ${samples.size}", samples.size in 19..21)
        samples.zipWithNext().forEach { (a, b) -> assertEquals(50L, b.timeMs - a.timeMs) }
        assertTrue(samples.all { it.speed == 5.0 && !it.brakePressed })
        assertEquals(5.0f, source.speedFlow.first(), 0f)
        assertFalse(source.brakePressedFlow.first())
    }

    @Test
    fun playSmoothStop_endsStopped_andJudgesPerfect() = runTest {
        val source = SimulatedVehicleDataSource(Powertrain.GASOLINE, backgroundScope, clockMs = { testScheduler.currentTime })
        val samples = mutableListOf<VehicleSample>()
        backgroundScope.launch { source.samples.collect { samples += it } }
        runCurrent()

        source.play(SimScenario.SMOOTH_STOP)
        assertEquals(SimScenario.SMOOTH_STOP, source.playing.value)

        advanceTimeBy(60_000)
        runCurrent()

        assertNull("scenario should have finished", source.playing.value)
        assertTrue(samples.maxOf { it.speed } > 13.0)
        assertEquals(0.0, samples.last().speed, 0.0)
        assertFalse(samples.last().brakePressed)
        samples.zipWithNext().forEach { (a, b) -> assertEquals(50L, b.timeMs - a.timeMs) }

        val engine = JudgeEngine(Powertrain.GASOLINE, Passenger.NONE)
        val notes = samples.flatMap { engine.process(it).judged } + engine.finish()
        assertEquals(1, notes.size)
        assertEquals(NoteType.BRAKE, notes.single().type)
        assertEquals(Grade.PERFECT, notes.single().grade)
    }

    @Test
    fun playWhilePlaying_switchesScenario() = runTest {
        val source = SimulatedVehicleDataSource(Powertrain.ELECTRIFIED, backgroundScope, clockMs = { testScheduler.currentTime })
        val samples = mutableListOf<VehicleSample>()
        backgroundScope.launch { source.samples.collect { samples += it } }
        runCurrent()

        source.play(SimScenario.SMOOTH_STOP)
        advanceTimeBy(3_000)
        source.play(SimScenario.REGEN_FAST)
        runCurrent()
        assertEquals(SimScenario.REGEN_FAST, source.playing.value)

        advanceTimeBy(60_000)
        runCurrent()

        assertNull(source.playing.value)
        // REGEN_FAST는 브레이크를 쓰지 않는다 → 갈아탄 뒤에는 브레이크가 한 번도 눌리지 않음
        assertTrue(samples.none { it.brakePressed })
        val engine = JudgeEngine(Powertrain.ELECTRIFIED, Passenger.NONE)
        val notes = samples.flatMap { engine.process(it).judged } + engine.finish()
        assertEquals(listOf(Grade.MISS), notes.map { it.grade })
        assertEquals(NoteType.REGEN, notes.single().type)
    }
}
