package com.beatlane.app.data

import android.content.Context

/** 앱 설정 (기기 안에만 저장). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 판정 효과음. 기본 켜짐. */
    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    private companion object {
        const val KEY_SOUND = "sound_on"
    }
}
