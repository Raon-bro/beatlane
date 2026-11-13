package com.beatlane.app.sound

import com.beatlane.app.judge.Grade
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 판정 효과음을 코드로 합성한다 (외부 음원·라이선스 없음). 순수 Kotlin.
 * 운전 중이므로 짧고 부드럽게: 0.15~0.3초, 부드러운 시작, 지수 감쇠.
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100

    /** 한 음: 주파수(Hz), 시작 시각(초), 길이(초). */
    private data class Note(val freq: Double, val start: Double, val length: Double)

    private fun notesFor(grade: Grade): List<Note> = when (grade) {
        // 밝게 올라가는 3음 (C6-E6-G6)
        Grade.PERFECT -> listOf(Note(1046.5, 0.0, 0.16), Note(1318.5, 0.06, 0.16), Note(1568.0, 0.12, 0.22))
        // 2음 (E5-A5)
        Grade.GREAT -> listOf(Note(659.3, 0.0, 0.14), Note(880.0, 0.07, 0.18))
        // 1음 (A4)
        Grade.GOOD -> listOf(Note(440.0, 0.0, 0.2))
        // 낮게 내려가는 2음 (G3-D3), 경고지만 거슬리지 않게
        Grade.MISS -> listOf(Note(196.0, 0.0, 0.14), Note(146.8, 0.1, 0.2))
    }

    /** 16bit mono PCM 샘플. 최대 진폭은 [peak] (0..1). */
    fun pcm(grade: Grade, peak: Double = 0.5): ShortArray {
        val notes = notesFor(grade)
        val total = notes.maxOf { it.start + it.length }
        val n = (total * SAMPLE_RATE).roundToInt()
        val mix = DoubleArray(n)
        for (note in notes) {
            val s0 = (note.start * SAMPLE_RATE).roundToInt()
            val len = (note.length * SAMPLE_RATE).roundToInt()
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toDouble() / SAMPLE_RATE
                val attack = min(1.0, t / 0.008) // 8ms 페이드인 (딸깍 소리 방지)
                val decay = exp(-t / (note.length * 0.35))
                val tail = min(1.0, (len - i).toDouble() / (0.01 * SAMPLE_RATE)) // 끝 10ms 페이드아웃
                // 기음 + 약한 2배음으로 종소리 느낌
                val wave = sin(2 * PI * note.freq * t) + 0.25 * sin(2 * PI * note.freq * 2 * t)
                mix[idx] += wave * attack * decay * tail
            }
        }
        val maxAbs = mix.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0
        return ShortArray(n) { i -> (mix[i] / maxAbs * peak * Short.MAX_VALUE).roundToInt().toShort() }
    }

    /** WAV 파일 바이트 (SoundPool이 읽을 수 있게). */
    fun wav(samples: ShortArray): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1) // PCM
            putShort(1) // mono
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        out.write(header.array())
        val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { body.putShort(it) }
        out.write(body.array())
        return out.toByteArray()
    }
}
