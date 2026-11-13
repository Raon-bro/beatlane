package com.beatlane.app.data.sim

import kotlin.math.max
import kotlin.math.roundToInt

/** 시나리오의 한 시점 상태. 시각은 인덱스 × (1000 / hz) ms. */
data class ScenarioPoint(val speed: Double, val brakePressed: Boolean)

/**
 * 일정 주기(hz)로 재생할 차량 상태 목록. 첫 점은 시작 상태.
 *
 * @param eventStart 감속 이벤트가 시작되는 점 번호 (모르면 -1)
 * @param eventEnd 감속 이벤트가 끝나는 점 번호: 정지한 점, 회생제동이면 감속이 풀린 점 (모르면 -1)
 */
class Scenario(
    val hz: Int,
    val points: List<ScenarioPoint>,
    val eventStart: Int = -1,
    val eventEnd: Int = -1,
) {
    val periodMs: Long get() = 1000L / hz
}

/**
 * 시연용 시나리오. 모두 현재 속도에서 시작해 50km/h까지 가속 → 4초 정속 → 감속 이벤트.
 * 도로 위 예고 노트·앞차 배치는 [LeadCarPlan]이 시나리오에서 계산한다.
 */
enum class SimScenario {
    SMOOTH_STOP,
    HARD_STOP,
    REGEN_SLOW,
    REGEN_FAST;

    fun build(startSpeed: Double, hz: Int = DEFAULT_HZ): Scenario {
        val b = ScenarioBuilder(startSpeed, hz)
        b.accelerateTo(CRUISE_SPEED)
        // 예고 노트가 다가오는 모습을 볼 시간
        b.hold(CRUISE_HOLD_SEC, brake = false)
        val eventStart = b.size
        val eventEnd: Int
        when (this) {
            SMOOTH_STOP -> {
                // 지그시 밟고(저크 ≈1.3) → 일정하게 유지 → 정지 직전 살짝 풀기
                b.ramp(-2.0, 1.5, brake = true)
                b.holdUntilSpeedBelow(2.0, brake = true)
                b.ramp(-0.5, 1.0, brake = true)
                b.holdUntilStop(brake = true)
                eventEnd = b.size - 1
                b.hold(1.5, brake = true)
                b.hold(0.5, brake = false)
            }
            HARD_STOP -> {
                // 0.3초 만에 6m/s² 까지 콱 → 끝까지 유지
                b.ramp(-6.0, 0.3, brake = true)
                b.holdUntilStop(brake = true)
                eventEnd = b.size - 1
                b.hold(1.5, brake = true)
                b.hold(0.5, brake = false)
            }
            REGEN_SLOW -> {
                // 가속 페달에서 천천히 발 떼기 (저크 ≈0.8)
                b.ramp(-1.2, 1.5, brake = false)
                b.holdUntilSpeedBelow(5.0, brake = false)
                b.ramp(0.0, 1.0, brake = false)
                eventEnd = b.size - 1
                b.hold(2.0, brake = false)
            }
            REGEN_FAST -> {
                // 가속 페달에서 발을 확 뗌
                b.ramp(-2.5, 0.15, brake = false)
                b.holdUntilSpeedBelow(5.0, brake = false)
                b.ramp(0.0, 0.3, brake = false)
                eventEnd = b.size - 1
                b.hold(2.0, brake = false)
            }
        }
        return Scenario(hz, b.build().points, eventStart, eventEnd)
    }

    companion object {
        const val DEFAULT_HZ = 20
        /** 50 km/h */
        const val CRUISE_SPEED = 50.0 / 3.6
        /** 감속 이벤트 전에 정속으로 달리는 시간. */
        const val CRUISE_HOLD_SEC = 4.0
    }
}

/**
 * 가속도 프로파일로 속도를 적분해 시나리오를 만든다.
 * 속도가 0에 닿으면 0에 고정하고 가속도도 0으로 둔다.
 */
class ScenarioBuilder(startSpeed: Double, val hz: Int = SimScenario.DEFAULT_HZ) {
    private val dt = 1.0 / hz
    private var speed = max(0.0, startSpeed)
    private var accel = 0.0
    private val points = mutableListOf(ScenarioPoint(speed, false))

    val currentSpeed: Double get() = speed

    /** 지금까지 만든 점 개수 (= 다음에 추가될 점의 번호). */
    val size: Int get() = points.size

    private fun step(newAccel: Double, brake: Boolean) {
        accel = newAccel
        speed = max(0.0, speed + accel * dt)
        if (speed == 0.0) accel = 0.0
        points += ScenarioPoint(speed, brake)
    }

    private fun stepsFor(seconds: Double) = max(1, (seconds * hz).roundToInt())

    /** 현재 가속도를 유지한다. */
    fun hold(seconds: Double, brake: Boolean) = apply {
        repeat(stepsFor(seconds)) { step(accel, brake) }
    }

    /** 가속도를 [seconds] 동안 [target]까지 선형으로 바꾼다 (저크 일정). */
    fun ramp(target: Double, seconds: Double, brake: Boolean) = apply {
        val from = accel
        val n = stepsFor(seconds)
        for (i in 1..n) step(from + (target - from) * i / n, brake)
    }

    fun holdUntilSpeedBelow(target: Double, brake: Boolean, maxSeconds: Double = 60.0) = apply {
        var n = stepsFor(maxSeconds)
        while (speed > target && n-- > 0) step(accel, brake)
    }

    fun holdUntilStop(brake: Boolean, maxSeconds: Double = 60.0) =
        holdUntilSpeedBelow(0.0, brake, maxSeconds)

    /** 브레이크 off 상태로 [target]까지 가속 후 가속도를 0으로. 이미 빠르면 아무것도 안 함. */
    fun accelerateTo(target: Double, accelRate: Double = 1.5) = apply {
        if (speed >= target - 0.5) return@apply
        ramp(accelRate, 0.5, brake = false)
        var n = stepsFor(60.0)
        // 가속도를 0으로 줄이는 0.5초 동안 오르는 속도(≈ accelRate × 0.25)를 남겨 둔다.
        while (speed < target - accelRate * 0.25 && n-- > 0) step(accel, brake = false)
        ramp(0.0, 0.5, brake = false)
    }

    fun build(): Scenario = Scenario(hz, points.toList())
}
