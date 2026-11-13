package com.beatlane.app.data

import android.content.Context
import java.util.Locale

/** 저장되는 주행 기록: 최근 승차감 점수들 + 직전 주행 요약. */
data class DriveHistory(
    /** 오래된 것 → 최근 순. */
    val scores: List<Double> = emptyList(),
    val lastScore: Double? = null,
    val lastCoaching: String? = null,
    val lastHasImprovement: Boolean = false,
) {
    companion object {
        /** 보관 개수 (리포트는 최근 5개만 쓰지만 여유 있게). */
        const val MAX_SCORES = 20

        fun encodeScores(scores: List<Double>): String =
            scores.takeLast(MAX_SCORES).joinToString(",") { String.format(Locale.US, "%.3f", it) }

        /** 깨진 값은 건너뛰고, 0~5 범위만 받는다. */
        fun decodeScores(raw: String?): List<Double> =
            raw.orEmpty()
                .split(',')
                .mapNotNull { it.trim().toDoubleOrNull() }
                .filter { it in 0.0..5.0 }
                .takeLast(MAX_SCORES)
    }
}

/**
 * 주행 기록을 기기 안(SharedPreferences)에만 저장한다. 외부로 보내지 않는다.
 * 개인정보는 담지 않는다 (점수와 코칭 문구뿐).
 */
class DriveHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DriveHistory {
        val hasLast = prefs.contains(KEY_LAST_SCORE)
        return DriveHistory(
            scores = DriveHistory.decodeScores(prefs.getString(KEY_SCORES, null)),
            lastScore = if (hasLast) prefs.getFloat(KEY_LAST_SCORE, 0f).toDouble() else null,
            lastCoaching = prefs.getString(KEY_LAST_COACHING, null),
            lastHasImprovement = prefs.getBoolean(KEY_LAST_IMPROVE, false),
        )
    }

    fun save(history: DriveHistory) {
        prefs.edit().apply {
            putString(KEY_SCORES, DriveHistory.encodeScores(history.scores))
            if (history.lastScore != null) {
                putFloat(KEY_LAST_SCORE, history.lastScore.toFloat())
                putString(KEY_LAST_COACHING, history.lastCoaching)
                putBoolean(KEY_LAST_IMPROVE, history.lastHasImprovement)
            }
        }.apply()
    }

    private companion object {
        const val PREFS = "drive_history"
        const val KEY_SCORES = "scores"
        const val KEY_LAST_SCORE = "last_score"
        const val KEY_LAST_COACHING = "last_coaching"
        const val KEY_LAST_IMPROVE = "last_has_improvement"
    }
}
