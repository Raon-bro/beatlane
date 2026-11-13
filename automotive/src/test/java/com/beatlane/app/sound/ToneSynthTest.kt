package com.beatlane.app.sound

import com.beatlane.app.judge.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ToneSynthTest {

    @Test
    fun everyGradeIsShortAndBounded() {
        for (g in Grade.entries) {
            val pcm = ToneSynth.pcm(g, peak = 0.5)
            val seconds = pcm.size.toDouble() / ToneSynth.SAMPLE_RATE
            assertTrue("$g length $seconds", seconds in 0.1..0.4)
            val maxAbs = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$g peak $maxAbs", maxAbs <= Short.MAX_VALUE / 2 + 1)
            assertTrue("$g not silent", maxAbs > 1000)
        }
    }

    @Test
    fun startsAndEndsQuietly() {
        // 딸깍 소리가 나지 않게 시작과 끝이 거의 0
        for (g in Grade.entries) {
            val pcm = ToneSynth.pcm(g)
            assertTrue("$g start", abs(pcm.first().toInt()) < 200)
            assertTrue("$g end", abs(pcm.last().toInt()) < 200)
        }
    }

    @Test
    fun wavHeaderIsValid() {
        val pcm = ToneSynth.pcm(Grade.PERFECT)
        val wav = ToneSynth.wav(pcm)
        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ToneSynth.SAMPLE_RATE, bb.getInt(24))
        assertEquals(pcm.size * 2, bb.getInt(40))
    }
}
