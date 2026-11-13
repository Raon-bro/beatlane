package com.beatlane.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.beatlane.app.data.sim.SimForecast
import com.beatlane.app.judge.ActiveNote
import com.beatlane.app.judge.Grade
import com.beatlane.app.judge.NotePhase
import com.beatlane.app.judge.NoteResult
import com.beatlane.app.judge.NoteType
import com.beatlane.app.ui.theme.LocalBeatColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 롱노트 위의 표식: 노트 머리(START) · 구간 경계 · 꼬리. 생길 때의 주행 거리로 도로에 고정된다. */
private enum class MarkKind { HEAD, PHASE, TAIL }

private class LaneMark(val travel: Float, val kind: MarkKind, val color: Color)

/**
 * 원근 차선 + 판정선 + 롱노트 + 내 차.
 *
 * 롱노트 (예고 없음, 1단계 범위):
 * - 브레이크를 밟는 순간 판정선에 노트 머리가 생기고, 밟는 동안 노트 몸통이 지평선에서 내려와
 *   판정선을 지나 차 쪽으로 흘러간다 (도로 속도에 맞춰). 판정선 아래로 지나간 부분은 밝게 "채워진다".
 * - START→HOLD→END 구간이 바뀌는 지점과 노트 꼬리도 도로에 고정되어 함께 흘러간다.
 */
@Composable
fun LaneView(
    speedMps: Float,
    brakePressed: Boolean,
    activeNote: ActiveNote?,
    judgeLineColor: Color,
    lastResult: NoteResult?,
    resultSeq: Int,
    modifier: Modifier = Modifier,
    /** 시뮬레이션 시나리오의 예고 노트·앞차 (없으면 기존처럼 감속하는 순간부터 노트가 생긴다). */
    forecast: SimForecast? = null,
) {
    val c = LocalBeatColors.current
    val carImages = rememberCarImages()
    val leadImage = rememberLeadCarImage()

    // 주행 거리(차선 흐름)와 펄스용 시각을 프레임마다 갱신. 멈추면 흐름도 멈춘다.
    val speed by rememberUpdatedState(speedMps)
    var travel by remember { mutableFloatStateOf(0f) }
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) travel += speed * (t - last) / 1e9f
                last = t
                frameMs = t / 1_000_000
            }
        }
    }

    // 예고 노트: 거리 값은 20Hz로 오므로, 받은 순간의 주행 거리를 기억해 두고
    // 그 사이 프레임에서는 그동안 달린 만큼 당겨 그린다 (끊기지 않고 부드럽게 다가온다).
    var fcRef by remember { mutableStateOf<SimForecast?>(null) }
    var fcRefTravel by remember { mutableFloatStateOf(0f) }
    var fcRefMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(forecast?.tick) {
        fcRef = forecast
        fcRefTravel = travel
        fcRefMs = frameMs
    }
    val forecastColor = if (forecast?.byBrake == false) c.regenNote else c.brakeNote

    // 예고 노트의 시작선이 판정선과 겹치는 순간(= 감속을 시작하는 순간)의 섬광 (0 → 1)
    val hit = remember { Animatable(1f) }
    LaunchedEffect(activeNote?.startMs) {
        if (activeNote != null && forecast?.noteVisible == true) {
            hit.snapTo(0f)
            hit.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    // 롱노트 표식: 노트 시작 · 구간 변경 · 노트 끝에서 하나씩
    val marks = remember { mutableStateListOf<LaneMark>() }
    val noteColor = when (activeNote?.type) {
        NoteType.REGEN -> c.regenNote
        else -> c.brakeNote
    }
    var lastNoteStart by remember { mutableLongStateOf(-1L) }
    var lastNoteColor by remember { mutableStateOf(c.brakeNote) }
    LaunchedEffect(activeNote?.startMs, activeNote?.phase) {
        val note = activeNote
        if (note == null) {
            if (lastNoteStart >= 0) marks += LaneMark(travel, MarkKind.TAIL, lastNoteColor)
            lastNoteStart = -1
            // 정지로 끝나면 꼬리가 판정선에 멈춰 있게 되므로 잠시 뒤 정리한다 (다음 노트가 오면 취소됨)
            delay(NOTE_CLEAR_DELAY_MS)
            marks.clear()
            return@LaunchedEffect
        }
        if (note.startMs != lastNoteStart) {
            if (lastNoteStart >= 0) marks += LaneMark(travel, MarkKind.TAIL, lastNoteColor)
            lastNoteStart = note.startMs
            lastNoteColor = noteColor
            marks += LaneMark(travel, MarkKind.HEAD, noteColor)
        } else {
            marks += LaneMark(travel, MarkKind.PHASE, noteColor)
        }
        while (marks.size > 16) marks.removeAt(0)
    }

    // 판정 순간 터지는 효과 (0 → 1)
    val burst = remember { Animatable(1f) }
    LaunchedEffect(resultSeq) {
        if (resultSeq > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier) {
        val g = LaneGeometry(size.width, size.height)
        drawRoad(g, travel, c.lane, c.textSub)

        val judgeHalf = g.halfWidth(g.judgeY) * 0.82f
        val barH = g.h * 0.04f

        val fc = fcRef
        val fcOff = travel - fcRefTravel
        // 앞차가 움직이는 동안에는 값 사이 프레임에서도 앞차 속도만큼 밀어 그린다 (떨림 방지)
        val leadD = if (fc != null) {
            fc.leadAheadM - fcOff + fc.leadSpeedMps * ((frameMs - fcRefMs).coerceIn(0L, 200L) / 1000f)
        } else 0f
        val showForecast = fc != null && fc.noteVisible
        if (fc != null && showForecast) {
            // 예고 노트: 멀리서 다가와 시작선이 판정선에 닿고(감속 시작), 끝선이 닿으면(감속 끝) 끝난다
            drawSafeGap(g, fc.endAheadM - fcOff, leadD, c.accent)
            drawForecastNote(g, fc.startAheadM - fcOff, fc.endAheadM - fcOff, forecastColor)
        } else {
            drawLongNote(g, marks, travel, activeNote != null, noteColor)
        }

        // 판정선
        drawRoundRect(
            color = judgeLineColor.copy(alpha = 0.9f),
            topLeft = Offset(g.cx - judgeHalf, g.judgeY - barH / 2),
            size = Size(judgeHalf * 2, barH),
            cornerRadius = CornerRadius(barH / 2),
            style = Stroke(width = g.h * 0.005f),
        )

        // 밟고 있는 동안 판정선 위 노트 머리가 빛난다. END/LIFT는 깜빡여서 "지금 풀기"를 알린다.
        if (activeNote != null) {
            val pulse = if (activeNote.phase == NotePhase.END || activeNote.phase == NotePhase.LIFT) {
                0.65f + 0.35f * sin(frameMs / 1000.0 * 2 * PI * 2.5).toFloat()
            } else 1f
            drawGlowBar(g.cx, g.judgeY, judgeHalf, barH * 0.55f, noteColor, pulse)
        }

        if (hit.value < 1f) drawHitFlash(g, judgeHalf, barH, hit.value, forecastColor)

        if (lastResult != null && burst.value < 1f) {
            drawJudgeBurst(g, judgeHalf, barH, burst.value, c.gradeColor(lastResult.grade), lastResult.grade, resultSeq)
        }

        val carW = minOf(g.w * 0.17f, g.h * 0.30f)
        if (fc != null) drawLeadOnRoad(g, leadImage, leadD, carW, c.danger)
        drawCar(carImages, g.cx, g.h * 0.965f, carW, brakePressed, c.danger)
    }

    // 화면 밖으로 나간 표식 제거
    LaunchedEffect(travel.toInt()) {
        // 진행 중인 노트의 머리는 몸통 기준점이라 남겨 둔다 (화면에서는 아래 끝에 걸려 보임)
        val currentHead = if (activeNote != null) marks.lastOrNull { it.kind == MarkKind.HEAD } else null
        marks.removeAll { it !== currentHead && travel - it.travel > MARK_LIFETIME_M }
    }
}

private const val MARK_LIFETIME_M = 6f
private const val NOTE_CLEAR_DELAY_MS = 1200L
/** 예고 노트·앞차가 보이기 시작하는 거리 (m). 이보다 멀면 그리지 않는다. */
private const val FORECAST_VISIBLE_M = 150f
/** 보이기 시작한 뒤 이 거리만큼 다가오는 동안 서서히 진해진다. */
private const val FORECAST_FADE_M = 30f
/** 내 차 바닥의 세로 위치 (화면 높이 대비). 앞차 크기를 이 위치의 차선 폭 기준으로 줄인다. */
private const val OWN_CAR_GROUND = 0.965f
/** 앞차를 원근 크기보다 조금 크게 그려 멀리서도 알아보게 한다. */
private const val LEAD_CAR_SCALE = 1.4f

/** 원근 좌표계. d = 판정선에서 앞으로 몇 m (음수 = 판정선을 지나 차 쪽). */
private class LaneGeometry(val w: Float, val h: Float) {
    val cx = w / 2
    val horizonY = h * 0.03f
    val judgeY = h * 0.70f
    private val topHalf = w * 0.012f
    private val bottomHalf = w * 0.36f
    private val k = 14f

    fun halfWidth(y: Float) = topHalf + (bottomHalf - topHalf) * ((y - horizonY) / (h - horizonY))

    /** d가 -k에 가까우면 발산하므로 화면 아래(-3m 부근)에서 자른다. */
    fun yAt(dMeters: Float) = horizonY + (judgeY - horizonY) * k / (k + dMeters.coerceAtLeast(MIN_D))

    companion object {
        const val MIN_D = -3.2f
    }
}

private fun DrawScope.drawRoad(g: LaneGeometry, travel: Float, lane: Color, sub: Color) {
    val h = g.h
    val road = Path().apply {
        moveTo(g.cx - g.halfWidth(g.horizonY), g.horizonY)
        lineTo(g.cx + g.halfWidth(g.horizonY), g.horizonY)
        lineTo(g.cx + g.halfWidth(h), h)
        lineTo(g.cx - g.halfWidth(h), h)
        close()
    }
    drawPath(road, Brush.verticalGradient(0f to lane.copy(alpha = 0f), 1f to lane.copy(alpha = 0.85f), startY = g.horizonY, endY = h))
    drawPath(road, sub.copy(alpha = 0.6f), style = Stroke(width = h * 0.003f))

    // 흐르는 가로 줄 (속도감)
    val spacing = 10f
    var d = spacing - travel % spacing - spacing
    while (d < 160f) {
        if (d > LaneGeometry.MIN_D) {
            val y = g.yAt(d)
            val hw = g.halfWidth(y)
            drawLine(sub.copy(alpha = 0.10f), Offset(g.cx - hw, y), Offset(g.cx + hw, y), strokeWidth = h * 0.0025f)
        }
        d += spacing
    }

    // 점선 (옆 차선 + 차선 안쪽 가장자리) : 현재 속도로 아래로 흐른다
    val dashSpacing = 7f
    val dashLen = 3.5f
    var dd = dashSpacing - travel % dashSpacing - dashSpacing
    while (dd < 160f) {
        val near = (dd).coerceAtLeast(LaneGeometry.MIN_D)
        val far = (dd + dashLen).coerceAtLeast(LaneGeometry.MIN_D)
        if (far > near) {
            val y1 = g.yAt(far)
            val y2 = g.yAt(near)
            for (side in listOf(-1f, 1f)) {
                for ((spread, alpha, width) in listOf(Triple(1.55f, 0.55f, 0.005f), Triple(0.97f, 0.35f, 0.004f))) {
                    drawLine(
                        sub.copy(alpha = alpha),
                        Offset(g.cx + side * g.halfWidth(y1) * spread, y1),
                        Offset(g.cx + side * g.halfWidth(y2) * spread, y2),
                        strokeWidth = h * width,
                    )
                }
            }
        }
        dd += dashSpacing
    }
}

/** 도로 위 d1(가까운 쪽) ~ d2(먼 쪽) 구간을 차선 폭으로 칠한다. */
private fun DrawScope.fillSegment(g: LaneGeometry, d1: Float, d2: Float, widthRatio: Float, brush: Brush) {
    val yNear = g.yAt(d1)
    val yFar = g.yAt(d2)
    if (yNear - yFar < 0.5f) return
    val p = Path().apply {
        moveTo(g.cx - g.halfWidth(yNear) * widthRatio, yNear)
        lineTo(g.cx - g.halfWidth(yFar) * widthRatio, yFar)
        lineTo(g.cx + g.halfWidth(yFar) * widthRatio, yFar)
        lineTo(g.cx + g.halfWidth(yNear) * widthRatio, yNear)
        close()
    }
    drawPath(p, brush)
}

private fun DrawScope.drawLongNote(
    g: LaneGeometry,
    marks: List<LaneMark>,
    travel: Float,
    active: Boolean,
    activeColor: Color,
) {
    val ratio = 0.82f
    // 1) 밟는 중이면 지평선 쪽에서 판정선으로 들어오는 몸통 (흐르는 줄무늬)
    if (active) {
        fillSegment(
            g, 0f, 120f, ratio,
            Brush.verticalGradient(
                0f to activeColor.copy(alpha = 0f),
                1f to activeColor.copy(alpha = 0.28f),
                startY = g.yAt(120f), endY = g.judgeY,
            ),
        )
    }

    // 2) 판정선을 지나간(채워진) 몸통: 각 노트의 머리 ~ (꼬리 또는 판정선)
    var i = 0
    while (i < marks.size) {
        val head = marks[i]
        if (head.kind != MarkKind.HEAD) { i++; continue }
        val tail = marks.drop(i + 1).firstOrNull { it.kind == MarkKind.TAIL || it.kind == MarkKind.HEAD }
        val dHead = head.travel - travel // 머리 위치 (음수: 판정선을 지나감)
        val dEnd = when {
            tail != null -> tail.travel - travel
            active -> 0f
            else -> dHead
        }
        if (dEnd > dHead) {
            fillSegment(
                g, dHead, dEnd, ratio,
                Brush.verticalGradient(
                    0f to head.color.copy(alpha = 0.75f),
                    1f to head.color.copy(alpha = 0.45f),
                    startY = g.yAt(dEnd), endY = g.yAt(dHead),
                ),
            )
        }
        i++
    }

    // 3) 흐르는 줄무늬: 밟는 동안 몸통 위로 3m마다 (판정선을 지나며 내려간다)
    if (active) {
        val stripe = 3f
        var d = stripe - travel % stripe - stripe
        val head = marks.lastOrNull { it.kind == MarkKind.HEAD }
        val minD = head?.let { it.travel - travel } ?: 0f
        while (d < 60f) {
            if (d > minD && d > LaneGeometry.MIN_D) {
                val y = g.yAt(d)
                val hw = g.halfWidth(y) * ratio
                drawLine(Color.White.copy(alpha = if (d < 0) 0.35f else 0.18f), Offset(g.cx - hw, y), Offset(g.cx + hw, y), strokeWidth = g.h * 0.003f)
            }
            d += stripe
        }
    }

    // 4) 표식 (머리·구간 경계·꼬리) : 도로에 고정되어 흘러간다
    for (m in marks) {
        val d = m.travel - travel
        if (d <= LaneGeometry.MIN_D) continue
        val y = g.yAt(d)
        val hw = g.halfWidth(y) * ratio
        val thick = g.h * when (m.kind) {
            MarkKind.HEAD, MarkKind.TAIL -> 0.014f
            MarkKind.PHASE -> 0.006f
        }
        drawRoundRect(
            if (m.kind == MarkKind.PHASE) Color.White.copy(alpha = 0.7f) else m.color,
            topLeft = Offset(g.cx - hw, y - thick / 2),
            size = Size(hw * 2, thick),
            cornerRadius = CornerRadius(thick / 2),
        )
    }
}

/**
 * 예고 롱노트. d = 판정선에서 앞으로 몇 m.
 * - 시작선(START)·끝선(END): 얇은 캡슐. 멀수록 원근에 맞게 좁고 얇다.
 * - 몸통: 아직 오지 않은 부분은 옅게, 판정선을 지나간(밟고 있는) 부분은 진하게.
 */
private fun DrawScope.drawForecastNote(g: LaneGeometry, dStart: Float, dEnd: Float, color: Color) {
    if (dStart > FORECAST_VISIBLE_M) return
    val appear = ((FORECAST_VISIBLE_M - dStart) / FORECAST_FADE_M).coerceIn(0f, 1f)
    val far = dEnd.coerceAtMost(FORECAST_VISIBLE_M)
    val near = dStart.coerceAtLeast(LaneGeometry.MIN_D)
    val bodyRatio = 0.60f

    val aheadFrom = near.coerceAtLeast(0f)
    if (far > aheadFrom) {
        fillSegment(
            g, aheadFrom, far, bodyRatio,
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.20f * appear),
                1f to color.copy(alpha = 0.42f * appear),
                startY = g.yAt(far), endY = g.yAt(aheadFrom),
            ),
        )
    }
    val passedTo = far.coerceAtMost(0f)
    if (passedTo > near) {
        fillSegment(
            g, near, passedTo, bodyRatio,
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.50f),
                1f to color.copy(alpha = 0.80f),
                startY = g.yAt(passedTo), endY = g.yAt(near),
            ),
        )
    }
    drawNoteCap(g, dEnd, color, appear)
    drawNoteCap(g, dStart, color, appear)
}

/** 노트 시작선·끝선 캡슐 하나. */
private fun DrawScope.drawNoteCap(g: LaneGeometry, d: Float, color: Color, intensity: Float) {
    if (d <= LaneGeometry.MIN_D || d > FORECAST_VISIBLE_M || intensity <= 0f) return
    val y = g.yAt(d)
    val half = g.halfWidth(y) * 0.82f
    val thick = maxOf(g.h * 0.006f, half * 0.045f)
    drawGlowBar(g.cx, y, half, thick, color, intensity)
}

/** 노트 끝선과 앞차 사이: 멈춘 뒤에도 남겨 두는 안전거리 구간 (청록). */
private fun DrawScope.drawSafeGap(g: LaneGeometry, dEnd: Float, dLead: Float, color: Color) {
    val near = dEnd.coerceAtLeast(LaneGeometry.MIN_D)
    val far = dLead.coerceAtMost(FORECAST_VISIBLE_M)
    if (far <= near || dEnd > FORECAST_VISIBLE_M) return
    val appear = ((FORECAST_VISIBLE_M - dEnd) / FORECAST_FADE_M).coerceIn(0f, 1f)
    fillSegment(
        g, near, far, 0.60f,
        Brush.verticalGradient(
            0f to color.copy(alpha = 0.10f * appear),
            1f to color.copy(alpha = 0.24f * appear),
            startY = g.yAt(far), endY = g.yAt(near),
        ),
    )
}

/** 앞차를 도로 위 d(m) 지점에 원근에 맞는 크기로 그린다. [ownCarW]는 내 차의 화면 폭. */
private fun DrawScope.drawLeadOnRoad(g: LaneGeometry, image: ImageBitmap?, d: Float, ownCarW: Float, red: Color) {
    if (d > FORECAST_VISIBLE_M || d < 1f) return
    val y = g.yAt(d)
    val w = ownCarW * LEAD_CAR_SCALE * g.halfWidth(y) / g.halfWidth(g.h * OWN_CAR_GROUND)
    if (w < 4f) return
    val alpha = ((FORECAST_VISIBLE_M - d) / FORECAST_FADE_M).coerceIn(0f, 1f)
    drawLeadCar(image, g.cx, y, w, alpha, red)
}

/**
 * 예고 노트의 시작선이 판정선과 겹치는 순간: 겹친 선이 밝게 빛나고,
 * 판정선을 따라 빛줄기가 뻗고, 얇은 테두리 두 겹이 퍼진다. (과하지 않게 0.7초)
 */
private fun DrawScope.drawHitFlash(g: LaneGeometry, judgeHalf: Float, barH: Float, p: Float, color: Color) {
    val fade = 1f - p
    val streakH = barH * 0.22f * fade + 1f
    drawRect(
        Brush.horizontalGradient(
            0f to color.copy(alpha = 0f),
            0.5f to Color.White.copy(alpha = 0.9f * fade),
            1f to color.copy(alpha = 0f),
        ),
        topLeft = Offset(0f, g.judgeY - streakH / 2),
        size = Size(g.w, streakH),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.85f * fade),
        topLeft = Offset(g.cx - judgeHalf, g.judgeY - barH * 0.3f),
        size = Size(judgeHalf * 2, barH * 0.6f),
        cornerRadius = CornerRadius(barH * 0.3f),
    )
    for (ring in 0..1) {
        val q = (p - ring * 0.18f).coerceIn(0f, 1f)
        if (q > 0f) {
            val half = judgeHalf * (1f + 0.22f * q)
            val bh = barH * (1f + 1.8f * q)
            drawRoundRect(
                color = color.copy(alpha = (1f - q) * 0.8f),
                topLeft = Offset(g.cx - half, g.judgeY - bh / 2),
                size = Size(half * 2, bh),
                cornerRadius = CornerRadius(bh / 2),
                style = Stroke(width = g.h * 0.004f),
            )
        }
    }
}

private fun DrawScope.drawGlowBar(cx: Float, cy: Float, half: Float, barH: Float, color: Color, intensity: Float) {
    for (i in 4 downTo 1) {
        val spread = barH * i * 0.9f
        drawRoundRect(
            color = color.copy(alpha = 0.07f * intensity),
            topLeft = Offset(cx - half - spread, cy - barH / 2 - spread),
            size = Size(half * 2 + spread * 2, barH + spread * 2),
            cornerRadius = CornerRadius(barH / 2 + spread),
        )
    }
    drawRoundRect(
        color = color.copy(alpha = intensity),
        topLeft = Offset(cx - half, cy - barH / 2),
        size = Size(half * 2, barH),
        cornerRadius = CornerRadius(barH / 2),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f * intensity),
        topLeft = Offset(cx - half * 0.96f, cy - barH * 0.12f),
        size = Size(half * 1.92f, barH * 0.24f),
        cornerRadius = CornerRadius(barH * 0.12f),
    )
}

/**
 * 판정 순간 효과: 판정선을 가로지르는 빛줄기 + 판정선 섬광 + 사방으로 튀는 불꽃 + 퍼지는 테두리.
 * PERFECT일수록 크고 많게, MISS는 짧고 작게.
 */
private fun DrawScope.drawJudgeBurst(
    g: LaneGeometry,
    judgeHalf: Float,
    barH: Float,
    p: Float,
    color: Color,
    grade: Grade,
    seq: Int,
) {
    val fade = 1f - p
    val power = when (grade) {
        Grade.PERFECT -> 1f
        Grade.GREAT -> 0.8f
        Grade.GOOD -> 0.6f
        Grade.MISS -> 0.4f
    }

    // 빛줄기 (G5 시안처럼 판정선을 따라 화면 끝까지)
    val streakH = barH * 0.35f * fade * power + 1f
    drawRect(
        Brush.horizontalGradient(
            0f to color.copy(alpha = 0f),
            0.5f to color.copy(alpha = 0.9f * fade),
            1f to color.copy(alpha = 0f),
        ),
        topLeft = Offset(0f, g.judgeY - streakH / 2),
        size = Size(g.w, streakH),
    )

    // 판정선 섬광 (처음 0.25 동안 강하게)
    val flash = (1f - p / 0.25f).coerceIn(0f, 1f) * power
    if (flash > 0f) {
        drawRoundRect(
            Color.White.copy(alpha = 0.8f * flash),
            topLeft = Offset(g.cx - judgeHalf, g.judgeY - barH * 0.3f),
            size = Size(judgeHalf * 2, barH * 0.6f),
            cornerRadius = CornerRadius(barH * 0.3f),
        )
    }

    // 불꽃 (원근에 맞게 납작한 타원으로 퍼짐)
    val count = (12 + 12 * power).toInt()
    for (i in 0 until count) {
        val a = i * 2 * PI / count + seq * 0.37
        val along = (i % 3 + 1) / 3f // 판정선 위 출발점 분포
        val startX = g.cx + (along * 2 - 1.33f) * judgeHalf * 0.75f
        val r = p * judgeHalf * 0.45f * power * (0.7f + 0.3f * ((i * 7) % 5) / 4f)
        val x = startX + (cos(a) * r).toFloat()
        val y = g.judgeY + (sin(a) * r * 0.45f).toFloat()
        val s = barH * 0.22f * fade
        drawCircle(color.copy(alpha = fade), s, Offset(x, y))
        drawCircle(Color.White.copy(alpha = fade * 0.7f), s * 0.45f, Offset(x, y))
    }

    // 퍼지는 테두리
    val half = judgeHalf * (1f + 0.35f * p)
    val bh = barH * (1f + 2.5f * p)
    drawRoundRect(
        color = color.copy(alpha = fade * 0.9f),
        topLeft = Offset(g.cx - half, g.judgeY - bh / 2),
        size = Size(half * 2, bh),
        cornerRadius = CornerRadius(bh / 2),
        style = Stroke(width = g.h * 0.008f * fade + 1f),
    )
}

/**
 * 판정 텍스트 등장 연출: 크게 튀어나왔다가 스프링으로 제자리 → 유지 → 사라짐.
 * @return (alpha, scale)
 */
@Composable
fun rememberJudgementAlpha(resultSeq: Int, holdMs: Int = 1400): Pair<Float, Float> {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(resultSeq) {
        if (resultSeq == 0) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(1f)
        scale.snapTo(1.9f)
        scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
        kotlinx.coroutines.delay(holdMs.toLong())
        alpha.animateTo(0f, tween(300, easing = LinearEasing))
    }
    return alpha.value to scale.value
}
