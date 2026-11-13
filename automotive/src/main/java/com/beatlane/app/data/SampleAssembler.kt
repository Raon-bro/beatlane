package com.beatlane.app.data

import com.beatlane.app.judge.VehicleSample
import kotlin.math.abs

/**
 * 따로 들어오는 속도 이벤트와 브레이크 이벤트를 판정용 샘플 하나로 묶는다. (순수 Kotlin)
 *
 * - 속도 이벤트가 올 때 샘플을 만든다. 단 [minIntervalMs]보다 촘촘하면 건너뛴다 (판정은 10~20Hz 기준).
 * - 속도가 변하지 않아 이벤트가 끊기면 [onTick]이 [staleMs]마다 마지막 값으로 샘플을 채운다.
 *   (브레이크만 바뀐 경우도 이 경로로 늦어도 [staleMs] 안에 전달된다.)
 * - 샘플 시각은 항상 증가하게 보장한다.
 *
 * 여러 스레드(차량 콜백, 메인)에서 불리므로 동기화한다.
 */
class SampleAssembler(
    private val minIntervalMs: Long = 45,
    private val staleMs: Long = 150,
) {
    private var speed: Double? = null
    private var brake = false
    private var lastEmitMs: Long? = null

    @Synchronized
    fun setBrake(pressed: Boolean) {
        brake = pressed
    }

    /** 속도 이벤트. [speedMps]는 m/s (후진 등 음수는 크기만 쓴다). */
    @Synchronized
    fun onSpeed(timeMs: Long, speedMps: Double): VehicleSample? {
        speed = abs(speedMps)
        val last = lastEmitMs
        return if (last == null || timeMs - last >= minIntervalMs) emit(timeMs) else null
    }

    /** 주기적으로 호출. 샘플이 한동안 없었으면 마지막 값으로 하나 만든다. */
    @Synchronized
    fun onTick(nowMs: Long): VehicleSample? {
        if (speed == null) return null
        val last = lastEmitMs
        return if (last == null || nowMs - last >= staleMs) emit(nowMs) else null
    }

    private fun emit(timeMs: Long): VehicleSample? {
        val s = speed ?: return null
        val last = lastEmitMs
        if (last != null && timeMs <= last) return null
        lastEmitMs = timeMs
        return VehicleSample(timeMs, s, brake)
    }
}
