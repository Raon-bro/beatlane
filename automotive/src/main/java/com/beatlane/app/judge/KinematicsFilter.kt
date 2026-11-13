package com.beatlane.app.judge

/**
 * 원시 속도 → (이동평균 보정 속도, 가속도, 저크).
 * 시각 간격은 샘플의 timeMs 차이를 그대로 쓴다.
 */
class KinematicsFilter(private val window: Int) {
    init {
        require(window >= 1)
    }

    private val speeds = ArrayDeque<Double>()
    private var prevTimeMs: Long? = null
    private var prevSpeed = 0.0
    private var prevAccel: Double? = null

    fun reset() {
        speeds.clear()
        prevTimeMs = null
        prevAccel = null
    }

    fun update(sample: VehicleSample): Kinematics {
        speeds.addLast(sample.speed)
        if (speeds.size > window) speeds.removeFirst()
        val smoothed = speeds.average()

        val lastTime = prevTimeMs
        var accel = 0.0
        var jerk = 0.0
        if (lastTime != null && sample.timeMs > lastTime) {
            val dt = (sample.timeMs - lastTime) / 1000.0
            accel = (smoothed - prevSpeed) / dt
            prevAccel?.let { jerk = (accel - it) / dt }
            prevAccel = accel
        } else if (lastTime != null) {
            // 같은/역행 시각: 이전 값 유지
            accel = prevAccel ?: 0.0
        }
        if (lastTime == null || sample.timeMs > lastTime) {
            prevTimeMs = sample.timeMs
            prevSpeed = smoothed
        }

        return Kinematics(
            timeMs = sample.timeMs,
            rawSpeed = sample.speed,
            speed = smoothed,
            accel = accel,
            jerk = jerk,
            brakePressed = sample.brakePressed,
        )
    }
}
