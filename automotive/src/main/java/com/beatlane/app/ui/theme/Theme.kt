package com.beatlane.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.beatlane.app.R
import com.beatlane.app.judge.Grade

/** 팀 시안 색. 다크가 기본. */
data class BeatColors(
    val bgOuter: Color,
    val bgInner: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val brakeNote: Color,
    val regenNote: Color,
    val lane: Color,
    val danger: Color,
) {
    fun gradeColor(grade: Grade): Color = when (grade) {
        Grade.PERFECT -> regenNote
        Grade.GREAT -> accent
        Grade.GOOD -> brakeNote
        Grade.MISS -> danger
    }
}

val DarkBeatColors = BeatColors(
    bgOuter = Color(0xFF03080B),
    bgInner = Color(0xFF0E2E3A),
    text = Color(0xFFEAF4F6),
    textSub = Color(0xFF8AA6B0),
    accent = Color(0xFF56C6D6),
    brakeNote = Color(0xFFFF9A3D),
    regenNote = Color(0xFF3FE0B0),
    lane = Color(0xFF1B4A58),
    danger = Color(0xFFFF5A5A),
)

val LightBeatColors = BeatColors(
    bgOuter = Color(0xFFDCE7EB),
    bgInner = Color(0xFFEEF4F6),
    text = Color(0xFF08171D),
    textSub = Color(0xFF4A6670),
    accent = Color(0xFF138CA0),
    brakeNote = Color(0xFFE06F00),
    regenNote = Color(0xFF0E8F6E),
    lane = Color(0xFFB9D0D8),
    danger = Color(0xFFD03030),
)

val LocalBeatColors = staticCompositionLocalOf { DarkBeatColors }

/** 숫자·영문용. 가변 폰트라 굵기별로 축 값을 지정한다. */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    listOf(200, 300, 400, 500, 600).map { w ->
        Font(
            resId = R.font.outfit,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

/** 한글: 시스템 Noto Sans CJK. */
val KoreanFont: FontFamily = FontFamily.SansSerif

@Composable
fun BeatLaneTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBeatColors provides if (dark) DarkBeatColors else LightBeatColors,
        content = content,
    )
}
