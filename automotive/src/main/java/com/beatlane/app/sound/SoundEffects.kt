package com.beatlane.app.sound

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.beatlane.app.judge.Grade
import java.io.File

/**
 * 판정 효과음 재생.
 *
 * 기본은 [ToneSynth]로 만든 소리. `res/raw/sfx_perfect`, `sfx_great`, `sfx_good`, `sfx_miss`
 * (ogg/wav 등)를 넣으면 그 파일을 우선 쓴다. 자세한 내용은 docs/assets.md.
 */
class SoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // 짧은 알림음: 미디어 재생을 끊지 않는 용도
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mutableMapOf<Grade, Int>()
    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
        Grade.entries.forEach { g -> ids[g] = load(context, g) }
    }

    @SuppressLint("DiscouragedApi") // 에셋이 있을 때만 쓰는 선택 리소스라 이름으로 찾는다
    private fun load(context: Context, grade: Grade): Int {
        val name = "sfx_" + grade.name.lowercase()
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId != 0) {
            Log.d(TAG, "using asset $name")
            return pool.load(context, resId, 1)
        }
        val file = File(context.cacheDir, "$name.wav")
        if (!file.exists()) file.writeBytes(ToneSynth.wav(ToneSynth.pcm(grade)))
        return pool.load(file.path, 1)
    }

    fun play(grade: Grade) {
        val id = ids[grade] ?: return
        if (synchronized(loaded) { id !in loaded }) return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()

    private companion object {
        const val TAG = "BeatLane/Sound"
    }
}
