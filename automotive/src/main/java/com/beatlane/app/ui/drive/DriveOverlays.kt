package com.beatlane.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.beatlane.app.R
import com.beatlane.app.judge.NotePhase
import com.beatlane.app.judge.NoteType
import com.beatlane.app.ui.theme.KoreanFont
import com.beatlane.app.ui.theme.LocalBeatColors
import com.beatlane.app.ui.theme.Outfit
import kotlin.math.max

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

/** 노트가 끝난 뒤에도 잠깐 보였다가 사라지게 하는 투명도. */
@Composable
private fun rememberLingerAlpha(visible: Boolean, lingerMs: Long = 1500): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            alpha.animateTo(1f, tween(150))
        } else {
            kotlinx.coroutines.delay(lingerMs)
            alpha.animateTo(0f, tween(400))
        }
    }
    return alpha.value
}

/** 세그먼트 하나: 이름, 채움(0..1), 지금 구간인지. */
private data class Seg(val label: String, val fill: Float, val current: Boolean)

private class LastGauge {
    var segs: List<Seg> = emptyList()
    var color: Color? = null
}

/**
 * HOLD 게이지: 밟는 동안 아래에서 위로 채워진다.
 * 브레이크: START(1초) → HOLD(유지 시간) → RELEASE(정지 직전 얼마나 풀었는지).
 * 회생: LIFT(1초) → REGEN(유지 시간).
 */
@Composable
fun HoldGauge(state: DriveUiState, u: Dp, modifier: Modifier = Modifier) {
    val c = LocalBeatColors.current
    val note = state.activeNote
    val guide = state.guide
    val visible = note != null
    val alpha = rememberLingerAlpha(visible)

    // 노트가 끝난 뒤에는 마지막 모습을 유지한다 (상태가 아닌 보관함: 컴포지션 중 쓰기 루프 방지)
    val last = remember { LastGauge() }
    var segs = last.segs
    var color = last.color ?: c.brakeNote
    if (note != null) {
        val e = state.noteElapsedMs.toFloat()
        val startFill = (e / 1000f).coerceIn(0f, 1f)
        val holdFill = if (note.phase == NotePhase.START || note.phase == NotePhase.LIFT) 0f
        else ((e - 1000f) / 3000f).coerceIn(0f, 1f)
        color = if (note.type == NoteType.BRAKE) c.brakeNote else c.regenNote
        segs = if (note.type == NoteType.BRAKE) {
            val endFill = if (note.phase == NotePhase.END && guide != null && guide.peakDecel > guide.endTarget) {
                ((guide.peakDecel - state.decel) / (guide.peakDecel - guide.endTarget)).coerceIn(0f, 1f)
            } else 0f
            listOf(
                Seg("START", startFill, note.phase == NotePhase.START),
                Seg("HOLD", if (note.phase == NotePhase.END) 1f else holdFill, note.phase == NotePhase.HOLD),
                Seg("RELEASE", endFill, note.phase == NotePhase.END),
            )
        } else {
            listOf(
                Seg("LIFT", startFill, note.phase == NotePhase.LIFT),
                Seg("REGEN", holdFill, note.phase == NotePhase.REGEN),
            )
        }
        last.segs = segs
        last.color = color
    }
    if (alpha <= 0f || segs.isEmpty()) return

    Row(modifier.graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically) {
        // 세그먼트 이름 (위가 마지막 구간)
        Column(horizontalAlignment = Alignment.End) {
            segs.reversed().forEach { s ->
                Text(
                    s.label,
                    color = if (s.current) c.text else c.textSub.copy(alpha = 0.7f),
                    fontFamily = Outfit, fontWeight = FontWeight(if (s.current) 600 else 400),
                    fontSize = (u * 1.7f).sp(), letterSpacing = 0.15.em,
                    modifier = Modifier.height(u * 26f / segs.size),
                )
            }
        }
        Spacer(Modifier.width(u))
        Canvas(Modifier.width(u * 2.6f).height(u * 26f)) {
            val n = segs.size
            val gap = 4.dp.toPx()
            val segH = (size.height - gap * (n - 1)) / n
            val r = CornerRadius(size.width / 2)
            segs.forEachIndexed { i, s ->
                // 아래가 첫 구간
                val top = size.height - (i + 1) * segH - i * gap
                drawRoundRect(c.textSub.copy(alpha = 0.18f), Offset(0f, top), Size(size.width, segH), r)
                if (s.fill > 0f) {
                    val fh = segH * s.fill
                    drawRoundRect(color.copy(alpha = if (s.current) 1f else 0.75f), Offset(0f, top + segH - fh), Size(size.width, fh), r)
                }
                if (s.current) {
                    drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(0f, top), Size(size.width, segH), r, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

/**
 * "따라 그리기" 그래프: 이상적인 감속 곡선(점선) 위에 실제 감속(실선)을 실시간으로 그린다.
 * 노트 진행 중에만 보이고, 끝나면 잠깐 남았다가 사라진다.
 */
@Composable
fun GuideGraph(state: DriveUiState, u: Dp, modifier: Modifier = Modifier) {
    val c = LocalBeatColors.current
    val guide = state.guide ?: return
    val alpha = rememberLingerAlpha(guide.active && state.activeNote != null)
    if (alpha <= 0f) return
    val noteColor = if (guide.type == NoteType.BRAKE) c.brakeNote else c.regenNote

    val shape = RoundedCornerShape(u * 1.5f)
    Column(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(c.bgOuter.copy(alpha = 0.72f))
            .border(1.dp, c.textSub.copy(alpha = 0.2f), shape)
            .padding(horizontal = u * 1.2f, vertical = u * 1f),
    ) {
        Text(
            stringResource(R.string.guide_title),
            color = c.text, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(),
        )
        Spacer(Modifier.height(u * 0.3f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(u * 2.4f, u * 1f)) {
                drawLine(
                    c.text.copy(alpha = 0.8f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                )
            }
            Text(" " + stringResource(R.string.guide_ideal) + "   ", color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.6f).sp())
            Canvas(Modifier.size(u * 2.4f, u * 1f)) {
                drawLine(noteColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx())
            }
            Text(" " + stringResource(R.string.guide_actual), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.6f).sp())
        }
        Spacer(Modifier.height(u * 0.6f))
        Canvas(Modifier.width(u * 19f).height(u * 10f)) {
            val tMax = max(4f, (guide.actual.lastOrNull()?.t ?: 0f) + 1.6f)
            val tMin = minOf(0f, guide.actual.firstOrNull()?.t ?: 0f)
            val yMax = max(3f, max(guide.peakDecel, guide.ideal.maxOfOrNull { it.decel } ?: 0f) * 1.15f)
            fun x(t: Float) = (t - tMin) / (tMax - tMin) * size.width
            fun y(d: Float) = size.height - d.coerceIn(0f, yMax) / yMax * size.height

            // 바닥선 + 정지 목표선
            drawLine(c.textSub.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (guide.type == NoteType.BRAKE) {
                drawLine(
                    c.textSub.copy(alpha = 0.18f), Offset(0f, y(guide.endTarget)), Offset(size.width, y(guide.endTarget)),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                )
            }

            fun path(points: List<GuidePt>) = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.t), y(p.decel)) else lineTo(x(p.t), y(p.decel)) }
            }

            // 가이드 (점선)
            drawPath(
                path(guide.ideal), c.text.copy(alpha = 0.75f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
            // 실제 (발광 + 실선)
            val actual = path(guide.actual)
            drawPath(actual, noteColor.copy(alpha = 0.25f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(actual, noteColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            guide.actual.lastOrNull()?.let { p ->
                drawCircle(Color.White, 4.dp.toPx(), Offset(x(p.t), y(p.decel)))
                drawCircle(noteColor, 2.5.dp.toPx(), Offset(x(p.t), y(p.decel)))
            }
        }
    }
}

/** 10콤보마다 크게 튀어나오는 배너. */
@Composable
fun ComboMilestoneBanner(seq: Int, combo: Int, u: Dp, modifier: Modifier = Modifier) {
    val c = LocalBeatColors.current
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(seq) {
        if (seq == 0) return@LaunchedEffect
        shown = combo
        alpha.snapTo(1f)
        scale.snapTo(0.4f)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        kotlinx.coroutines.delay(900)
        alpha.animateTo(0f, tween(400))
    }
    if (alpha.value <= 0f) return
    Text(
        "$shown COMBO!",
        color = c.accent,
        fontFamily = Outfit, fontWeight = FontWeight(500),
        fontSize = (u * 6f).sp(), letterSpacing = 0.12.em,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

/** 스피커 아이콘 (켜짐: 음파, 꺼짐: 사선). */
@Composable
fun SpeakerIcon(on: Boolean, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.30f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.82f)
            lineTo(w * 0.30f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.62f)
            close()
        }
        drawPath(body, color)
        val sw = w * 0.07f
        if (on) {
            drawArc(color, -45f, 90f, false, Offset(w * 0.40f, h * 0.30f), Size(w * 0.30f, h * 0.40f), style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, -45f, 90f, false, Offset(w * 0.36f, h * 0.16f), Size(w * 0.50f, h * 0.68f), style = Stroke(sw, cap = StrokeCap.Round))
        } else {
            drawLine(color, Offset(w * 0.64f, h * 0.36f), Offset(w * 0.90f, h * 0.64f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.90f, h * 0.36f), Offset(w * 0.64f, h * 0.64f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
