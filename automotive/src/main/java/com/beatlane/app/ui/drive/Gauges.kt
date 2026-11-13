package com.beatlane.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.beatlane.app.R
import com.beatlane.app.ui.theme.KoreanFont
import com.beatlane.app.ui.theme.LocalBeatColors
import com.beatlane.app.ui.theme.Outfit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/** 눈금 링 + 진행 호. [fraction]은 0..1. */
@Composable
private fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, flash: Float = 0f) {
    val c = LocalBeatColors.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(250), label = "ring")
    Canvas(modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        if (flash > 0f) {
            // 10콤보 강조: 링 전체가 번쩍
            drawCircle(color.copy(alpha = 0.25f * flash), r * 0.80f, center, style = Stroke(r * 0.18f * flash + 1f))
            drawCircle(color.copy(alpha = 0.9f * flash), r * 0.80f, center, style = Stroke(r * 0.03f))
        }
        val tickOuter = r
        val tickInner = r * 0.86f
        val ticks = 120
        for (i in 0 until ticks) {
            val deg = Math.toRadians(i * 360.0 / ticks)
            val major = i % 10 == 0
            val inner = if (major) tickInner * 0.97f else tickInner
            drawLine(
                color = c.textSub.copy(alpha = if (major) 0.35f else 0.18f),
                start = center + Offset((cos(deg) * inner).toFloat(), (sin(deg) * inner).toFloat()),
                end = center + Offset((cos(deg) * tickOuter).toFloat(), (sin(deg) * tickOuter).toFloat()),
                strokeWidth = r * 0.008f,
            )
        }
        val arcR = r * 0.80f
        val stroke = r * 0.03f
        val topLeft = Offset(center.x - arcR, center.y - arcR)
        val arcSize = Size(arcR * 2, arcR * 2)
        drawArc(
            color = c.textSub.copy(alpha = 0.12f),
            startAngle = START_ANGLE, sweepAngle = SWEEP, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (animated > 0.001f) {
            // 발광: 굵고 옅은 호를 먼저 깔고 위에 선명한 호
            drawArc(
                color = color.copy(alpha = 0.18f),
                startAngle = START_ANGLE, sweepAngle = SWEEP * animated, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke * 4, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = START_ANGLE, sweepAngle = SWEEP * animated, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Dp.sp(fraction: Float): TextUnit = with(LocalDensity.current) { (this@sp * fraction).toSp() }

@Composable
fun SpeedGauge(speedKmh: Float, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val c = LocalBeatColors.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Ring(speedKmh / 160f, color, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speedKmh.toInt().coerceAtLeast(0).toString(),
                color = c.text,
                fontFamily = Outfit,
                fontWeight = FontWeight(200),
                fontSize = size.sp(0.30f),
            )
            Text(
                text = "km/h",
                color = c.textSub,
                fontFamily = Outfit,
                fontWeight = FontWeight(300),
                fontSize = size.sp(0.065f),
            )
        }
    }
}

@Composable
fun ScoreGauge(
    score: Double,
    combo: Int,
    color: Color,
    size: Dp,
    milestoneSeq: Int = 0,
    modifier: Modifier = Modifier,
) {
    val c = LocalBeatColors.current

    // 콤보가 오를 때마다 숫자가 튄다
    val bump = remember { Animatable(1f) }
    val prevCombo = remember { intArrayOf(combo) }
    LaunchedEffect(combo) {
        val up = combo > prevCombo[0]
        prevCombo[0] = combo
        if (up) {
            bump.snapTo(1.7f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    // 10콤보마다 링이 번쩍
    val flash = remember { Animatable(0f) }
    LaunchedEffect(milestoneSeq) {
        if (milestoneSeq > 0) {
            repeat(2) {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(450))
            }
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Ring((score / 5.0).toFloat(), color, Modifier.fillMaxSize(), flash.value)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.ride_comfort),
                color = c.textSub,
                fontFamily = KoreanFont,
                fontSize = size.sp(0.055f),
                letterSpacing = 0.2.em,
            )
            Text(
                text = String.format(Locale.US, "%.1f", score),
                color = c.text,
                fontFamily = Outfit,
                fontWeight = FontWeight(200),
                fontSize = size.sp(0.26f),
            )
            Text(
                text = stringResource(R.string.combo_format, combo),
                color = if (combo > 0) color else c.textSub,
                fontFamily = KoreanFont,
                fontWeight = if (bump.value > 1.05f) FontWeight.Bold else FontWeight.Normal,
                fontSize = size.sp(0.075f),
                modifier = Modifier.graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                },
            )
        }
    }
}
