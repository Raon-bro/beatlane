package com.beatlane.app.judge

import com.beatlane.app.data.sim.Scenario
import com.beatlane.app.data.sim.ScenarioBuilder
import com.beatlane.app.data.sim.SimScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeEngineTest {

    /** 시나리오들을 이어서(속도·시각 연속) 엔진에 넣고, 판정된 노트를 돌려준다. */
    private class Drive(val engine: JudgeEngine, val hz: Int = SimScenario.DEFAULT_HZ) {
        private var timeMs = 0L
        private var speed = 0.0
        val notes = mutableListOf<NoteResult>()
        var hardDecelEvents = 0

        fun play(scenario: SimScenario) = feed(scenario.build(speed, hz))

        fun feed(scenario: Scenario) = apply {
            // 첫 점은 직전 상태와 같으므로 처음 한 번만 넣는다.
            val points = if (timeMs == 0L) scenario.points else scenario.points.drop(1)
            for (p in points) {
                val update = engine.process(VehicleSample(timeMs, p.speed, p.brakePressed))
                notes += update.judged
                if (update.hardDecelDetected) hardDecelEvents++
                timeMs += scenario.periodMs
                speed = p.speed
            }
        }

        fun finish() = apply { notes += engine.finish() }
    }

    private fun gasoline(passenger: Passenger = Passenger.NONE) =
        Drive(JudgeEngine(Powertrain.GASOLINE, passenger))

    private fun electrified(passenger: Passenger = Passenger.NONE) =
        Drive(JudgeEngine(Powertrain.ELECTRIFIED, passenger))

    private fun NoteResult.describe() =
        "grade=$grade startJerk=%.2f(%s) holdStd=%s(%s) endDecel=%s(%s)".format(
            startJerk, startGrade, holdStd?.let { "%.2f".format(it) }, holdGrade,
            endDecel?.let { "%.2f".format(it) }, endGrade,
        )

    // ---- 기본 4 시나리오 ----

    @Test
    fun smoothStop_isPerfect() {
        val drive = gasoline().play(SimScenario.SMOOTH_STOP).finish()

        assertEquals(1, drive.notes.size)
        val note = drive.notes.single()
        assertEquals(NoteType.BRAKE, note.type)
        assertTrue(note.endedByStop)
        assertEquals(note.describe(), Grade.PERFECT, note.grade)
        assertEquals(note.describe(), Grade.PERFECT, note.startGrade)
        assertEquals(note.describe(), Grade.PERFECT, note.holdGrade)
        assertEquals(note.describe(), Grade.PERFECT, note.endGrade)
        assertEquals(0, drive.engine.hardDecelCount)
    }

    @Test
    fun hardStop_isMiss_andCountsHardDecelOnce() {
        val drive = gasoline().play(SimScenario.HARD_STOP).finish()

        val note = drive.notes.single()
        assertEquals(NoteType.BRAKE, note.type)
        assertEquals(note.describe(), Grade.MISS, note.grade)
        assertEquals(note.describe(), Grade.MISS, note.startGrade)
        assertEquals(note.describe(), Grade.MISS, note.endGrade)
        assertEquals(1, drive.engine.hardDecelCount)
        assertEquals(1, drive.hardDecelEvents)
        assertEquals(0, drive.engine.combo)
    }

    @Test
    fun regenSlow_ev_isPerfect() {
        val drive = electrified().play(SimScenario.REGEN_SLOW).finish()

        val note = drive.notes.single()
        assertEquals(NoteType.REGEN, note.type)
        assertEquals(note.describe(), Grade.PERFECT, note.grade)
        assertNull(note.holdGrade)
        assertNull(note.endGrade)
    }

    @Test
    fun regenFast_ev_isMiss() {
        val drive = electrified().play(SimScenario.REGEN_FAST).finish()

        val note = drive.notes.single()
        assertEquals(NoteType.REGEN, note.type)
        assertEquals(note.describe(), Grade.MISS, note.grade)
        assertEquals(0, drive.engine.hardDecelCount)
    }

    // ---- 차종 모드 ----

    @Test
    fun regenScenarios_inGasolineMode_produceNoNotes() {
        val drive = gasoline()
            .play(SimScenario.REGEN_SLOW)
            .play(SimScenario.REGEN_FAST)
            .finish()
        assertTrue(drive.notes.isEmpty())
    }

    @Test
    fun brakeStops_inEvMode_areBrakeNotes() {
        val drive = electrified().play(SimScenario.SMOOTH_STOP).finish()
        val note = drive.notes.single()
        assertEquals(NoteType.BRAKE, note.type)
        assertEquals(note.describe(), Grade.PERFECT, note.grade)
    }

    // ---- 동승자 보정 ----

    @Test
    fun elderlyPassenger_isStricter() {
        val none = gasoline(Passenger.NONE).play(SimScenario.SMOOTH_STOP).finish().notes.single()
        val elderly = gasoline(Passenger.ELDERLY).play(SimScenario.SMOOTH_STOP).finish().notes.single()

        assertEquals(none.startJerk, elderly.startJerk, 1e-9) // 측정값은 같고
        assertEquals(Grade.PERFECT, none.grade)
        assertEquals(elderly.describe(), Grade.GREAT, elderly.grade) // 임계값만 엄격해진다
    }

    // ---- 가솔린 핵심: 멈추기 직전 살짝 풀기 ----

    @Test
    fun stopWithoutRelease_losesOnEnd_andCoachesRelease() {
        val scenario = ScenarioBuilder(SimScenario.CRUISE_SPEED)
            .hold(1.0, brake = false)
            .ramp(-2.5, 1.5, brake = true)
            .holdUntilStop(brake = true) // 끝까지 같은 힘으로 → 정지 순간 울컥
            .hold(1.0, brake = true)
            .build()
        val drive = gasoline().feed(scenario).finish()

        val note = drive.notes.single()
        assertEquals(note.describe(), Grade.MISS, note.endGrade)
        assertEquals(NotePhase.END, note.weakestPhase)
        assertEquals(0, drive.engine.hardDecelCount)
        assertTrue(drive.engine.report().coaching.contains("살짝 풀어"))
    }

    // ---- 노트 경계 ----

    @Test
    fun brakeReleasedBeforeStop_endsNoteWithoutEndGrade() {
        val scenario = ScenarioBuilder(SimScenario.CRUISE_SPEED)
            .hold(1.0, brake = false)
            .ramp(-1.5, 1.5, brake = true)
            .hold(2.0, brake = true)
            .ramp(0.0, 1.5, brake = true)
            .hold(1.0, brake = false)
            .build()
        val drive = gasoline().feed(scenario).finish()

        val note = drive.notes.single()
        assertFalse(note.endedByStop)
        assertNull(note.endDecel)
        assertEquals(note.describe(), Grade.PERFECT, note.grade)
    }

    @Test
    fun brakeWhileStationary_isNotANote() {
        val scenario = ScenarioBuilder(0.0)
            .hold(1.0, brake = false)
            .hold(2.0, brake = true)
            .hold(1.0, brake = false)
            .build()
        val drive = gasoline().feed(scenario).finish()
        assertTrue(drive.notes.isEmpty())
    }

    @Test
    fun activeNotePhase_goesStartHoldEnd() {
        val engine = JudgeEngine(Powertrain.GASOLINE, Passenger.NONE)
        val scenario = SimScenario.SMOOTH_STOP.build(0.0)
        val phases = mutableListOf<NotePhase>()
        scenario.points.forEachIndexed { i, p ->
            val u = engine.process(VehicleSample(i * scenario.periodMs, p.speed, p.brakePressed))
            u.activeNote?.phase?.let { if (phases.lastOrNull() != it) phases += it }
        }
        assertEquals(listOf(NotePhase.START, NotePhase.HOLD, NotePhase.END), phases)
    }

    // ---- 샘플링 주기 ----

    @Test
    fun at10Hz_scenariosKeepTheirGrades() {
        fun gradeAt10Hz(powertrain: Powertrain, s: SimScenario): Grade {
            val drive = Drive(JudgeEngine(powertrain, Passenger.NONE), hz = 10).play(s).finish()
            return drive.notes.single().grade
        }
        assertEquals(Grade.PERFECT, gradeAt10Hz(Powertrain.GASOLINE, SimScenario.SMOOTH_STOP))
        assertEquals(Grade.MISS, gradeAt10Hz(Powertrain.GASOLINE, SimScenario.HARD_STOP))
        assertEquals(Grade.PERFECT, gradeAt10Hz(Powertrain.ELECTRIFIED, SimScenario.REGEN_SLOW))
    }

    // ---- 콤보·점수·리포트 ----

    @Test
    fun comboAndReport() {
        val drive = electrified()
            .play(SimScenario.SMOOTH_STOP)
            .play(SimScenario.REGEN_SLOW)
            .play(SimScenario.HARD_STOP)
            .play(SimScenario.SMOOTH_STOP)
            .finish()
        val engine = drive.engine

        // REGEN_SLOW 뒤 HARD_STOP 시작 전 가속 구간에는 노트가 없어야 한다.
        assertEquals(
            drive.notes.joinToString { "${it.type}:${it.grade}" },
            listOf(Grade.PERFECT, Grade.PERFECT, Grade.MISS, Grade.PERFECT),
            drive.notes.map { it.grade },
        )
        assertEquals(1, engine.combo)
        assertEquals(2, engine.maxCombo)

        val report = engine.report()
        assertEquals(4, report.noteCount)
        assertEquals(3, report.gradeCounts[Grade.PERFECT])
        assertEquals(1, report.gradeCounts[Grade.MISS])
        assertEquals(0, report.gradeCounts[Grade.GREAT])
        assertEquals(1, report.hardDecelCount)
        // 가중 평균: (5·1 + 5·2 + 1·3 + 5·4) / 10 = 3.8 → ★4.0
        assertEquals(3.8, report.rideScore, 1e-9)
        assertEquals(4.0, report.stars, 1e-9)
        assertTrue(report.coaching.contains("급감속"))

        // 리포트 그래프용 데이터: 급감속 시각은 MISS 노트 구간 안에 있어야 한다
        assertEquals(drive.notes, report.notes)
        val miss = report.notes.single { it.grade == Grade.MISS }
        assertTrue(report.hardDecelTimesMs.single() in miss.startMs..miss.endMs)
    }

    @Test
    fun emptyDrive_hasFullScoreAndFriendlyMessage() {
        val report = JudgeEngine(Powertrain.GASOLINE, Passenger.NONE).report()
        assertEquals(5.0, report.rideScore, 0.0)
        assertEquals(0, report.noteCount)
        assertTrue(report.coaching.isNotBlank())
    }
}
