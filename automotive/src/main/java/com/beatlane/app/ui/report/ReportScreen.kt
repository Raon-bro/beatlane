package com.beatlane.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.beatlane.app.R
import com.beatlane.app.judge.Grade
import com.beatlane.app.judge.Passenger
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.ui.drive.ReportUi
import com.beatlane.app.ui.theme.BeatColors
import com.beatlane.app.ui.theme.KoreanFont
import com.beatlane.app.ui.theme.LocalBeatColors
import com.beatlane.app.ui.theme.Outfit
import java.util.Locale
import kotlin.math.ceil

@Composable
fun ReportScreen(data: ReportUi, onNewDrive: () -> Unit) {
    val c = LocalBeatColors.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(c.bgInner, c.bgOuter))),
    ) {
        val u = maxHeight / 100
        Column(Modifier.fillMaxSize().padding(horizontal = u * 4, vertical = u * 3)) {
            Header(data, u, onNewDrive)
            Spacer(Modifier.height(u * 1.5f))
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.6f), c.accent.copy(alpha = 0.05f))),
                ),
            )
            Spacer(Modifier.height(u * 2.5f))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 2)) {
                Card(Modifier.weight(1.3f).fillMaxHeight(), u) {
                    TimelineCard(data, u)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2)) {
                    CoachCard(data, u)
                    StatRow(data, u)
                    DistributionCard(data, u)
                    Card(Modifier.fillMaxWidth().weight(1f), u) { Last5(data, u) }
                }
            }
        }
    }
}

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

// ---------- 머리말 ----------

@Composable
private fun Header(data: ReportUi, u: Dp, onNewDrive: () -> Unit) {
    val c = LocalBeatColors.current
    val r = data.report
    val totalSec = (data.durationMs / 1000).toInt()
    val meta = listOf(
        stringResource(R.string.report_duration, totalSec / 60, totalSec % 60),
        stringResource(passengerRes(r.passenger)),
        stringResource(if (r.powertrain == Powertrain.ELECTRIFIED) R.string.mode_electrified else R.string.mode_gasoline),
    ).joinToString("  ·  ")

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("DRIVE REPORT", u)
                Text(
                    "  ·  $meta",
                    color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.9f).sp(),
                )
            }
            Spacer(Modifier.height(u * 0.8f))
            Text(
                stringResource(
                    when (r.passenger) {
                        Passenger.NONE -> R.string.report_title_none
                        Passenger.GENERAL -> R.string.report_title_general
                        Passenger.CHILD, Passenger.ELDERLY -> R.string.report_title_family
                    },
                ),
                color = c.text, fontFamily = KoreanFont, fontWeight = FontWeight.Light, fontSize = (u * 5f).sp(),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // 판정이 하나도 없으면 점수를 매기지 않는다
                    if (r.noteCount == 0) "–" else fmt1(r.rideScore),
                    color = c.accent, fontFamily = Outfit, fontWeight = FontWeight(200), fontSize = (u * 10f).sp(),
                )
                Text(
                    " / 5",
                    color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(300), fontSize = (u * 3f).sp(),
                    modifier = Modifier.padding(bottom = u * 1.6f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stars(if (r.noteCount == 0) 0.0 else r.stars, c, u)
                data.previousScore?.takeIf { r.noteCount > 0 }?.let { prev ->
                    val d = r.rideScore - prev
                    val sign = if (d >= 0) "▲ +" else "▼ "
                    Spacer(Modifier.width(u * 1.5f))
                    Text(
                        stringResource(R.string.report_delta, sign + fmt1(d).removePrefix("-")),
                        color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.9f).sp(),
                    )
                }
            }
        }
        Spacer(Modifier.width(u * 3))
        val shape = RoundedCornerShape(u * 1.5f)
        Box(
            Modifier
                .heightIn(min = 76.dp)
                .widthIn(min = 120.dp)
                .height(u * 8)
                .clip(shape)
                .background(c.bgInner.copy(alpha = 0.6f))
                .border(2.dp, c.accent.copy(alpha = 0.7f), shape)
                .clickable(onClick = onNewDrive)
                .padding(horizontal = u * 3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.new_drive),
                color = c.text, fontFamily = KoreanFont, fontWeight = FontWeight.Medium, fontSize = (u * 2.6f).sp(),
            )
        }
    }
}

@Composable
private fun Stars(stars: Double, c: BeatColors, u: Dp) {
    // 별 5개: 채움 / 반 / 빈 별. 반 별은 왼쪽만 칠한다.
    Row(horizontalArrangement = Arrangement.spacedBy(u * 0.3f)) {
        for (i in 0 until 5) {
            val fill = (stars - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.size(u * 3f)) {
                Canvas(Modifier.fillMaxSize()) {
                    val path = starPath(size.minDimension)
                    drawPath(path, c.textSub.copy(alpha = 0.3f))
                    if (fill > 0f) {
                        val w = size.width * if (fill >= 1f) 1f else 0.5f
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, w, size.height)
                        drawPath(path, c.accent)
                        drawContext.canvas.restore()
                    }
                }
            }
        }
    }
}

private fun starPath(s: Float): Path = Path().apply {
    val cx = s / 2
    val cy = s / 2
    val outer = s / 2
    val inner = outer * 0.45f
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = Math.toRadians(-90.0 + k * 36.0)
        val x = cx + (r * kotlin.math.cos(a)).toFloat()
        val y = cy + (r * kotlin.math.sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ---------- 공통 ----------

@Composable
private fun Card(modifier: Modifier, u: Dp, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 2)
    Column(
        modifier
            .clip(shape)
            .background(c.bgInner.copy(alpha = 0.35f))
            .border(1.dp, c.textSub.copy(alpha = 0.18f), shape)
            .padding(horizontal = u * 2.2f, vertical = u * 1.8f),
        content = content,
    )
}

/** 영문 대문자 라벨 (넓은 자간). */
@Composable
private fun Label(text: String, u: Dp, color: Color = LocalBeatColors.current.textSub) {
    Text(
        text, color = color, fontFamily = Outfit, fontWeight = FontWeight(500),
        fontSize = (u * 1.7f).sp(), letterSpacing = 0.25.em,
    )
}

@Composable
private fun CardTitle(en: String, ko: String, u: Dp) {
    val c = LocalBeatColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(en, u)
        Text("  ·  $ko", color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.7f).sp())
    }
}

private fun passengerRes(p: Passenger) = when (p) {
    Passenger.NONE -> R.string.passenger_none
    Passenger.GENERAL -> R.string.passenger_general
    Passenger.CHILD -> R.string.passenger_child
    Passenger.ELDERLY -> R.string.passenger_elderly
}

private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

// ---------- 울렁임 기록 (속도 + 판정 지점) ----------

@Composable
private fun ColumnScope.TimelineCard(data: ReportUi, u: Dp) {
    val c = LocalBeatColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardTitle("WOOZY TIMELINE", stringResource(R.string.report_timeline), u)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(u * 1.8f)) { drawDiamond(center, size.minDimension / 2, c.danger) }
        Text(
            "  " + stringResource(R.string.report_legend_miss),
            color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(),
        )
        Spacer(Modifier.width(u * 2))
        Canvas(Modifier.size(u * 1.4f)) { drawCircle(c.accent, size.minDimension / 2) }
        Text(
            "  " + stringResource(R.string.report_legend_note),
            color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(),
        )
    }
    Spacer(Modifier.height(u * 1.5f))
    if (data.speedTrace.size < 2) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.report_empty_trace), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 2.2f).sp())
        }
        return
    }
    val startLabel = stringResource(R.string.report_start)
    val endLabel = stringResource(R.string.report_end)
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(300), fontSize = (u * 1.6f).sp())
    val markStyle = TextStyle(color = c.text, fontFamily = KoreanFont, fontWeight = FontWeight.Bold, fontSize = (u * 2.0f).sp())
    val endpointStyle = TextStyle(color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp())

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val trace = data.speedTrace
        val t0 = data.startMs
        val t1 = maxOf(data.endMs, t0 + 1)
        val maxKmh = trace.maxOf { it.kmh }
        val yMax = maxOf(60f, ceil(maxKmh / 30f) * 30f)

        val left = 4.2f * u.toPx()
        val right = size.width - 1.2f * u.toPx()
        val top = 3.5f * u.toPx()
        val bottom = size.height - 3.6f * u.toPx()
        fun x(t: Long) = left + (right - left) * ((t - t0).toFloat() / (t1 - t0))
        fun y(kmh: Float) = bottom - (bottom - top) * (kmh / yMax)
        fun speedAt(t: Long): Float {
            val i = trace.indexOfFirst { it.timeMs >= t }
            return if (i <= 0) trace.first().kmh else trace[i].kmh
        }

        // 격자 + y축 (km/h)
        var g = 0f
        while (g <= yMax + 0.1f) {
            val yy = y(g)
            drawLine(c.textSub.copy(alpha = 0.10f), Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            val tl = measurer.measure(g.toInt().toString(), axisStyle)
            drawText(tl, topLeft = Offset(left - tl.size.width - 1.6f * u.toPx(), yy - tl.size.height / 2))
            g += 30f
        }
        drawText(measurer.measure("km/h", axisStyle), topLeft = Offset(left, 0f))

        // 노트 구간을 옅게 칠한다
        data.report.notes.forEach { n ->
            val col = c.gradeColor(n.grade)
            drawRect(
                col.copy(alpha = 0.08f),
                topLeft = Offset(x(n.startMs), top),
                size = Size((x(n.endMs) - x(n.startMs)).coerceAtLeast(2f), bottom - top),
            )
        }

        // 속도 선 (발광 + 본선)
        val path = Path().apply {
            trace.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.timeMs), y(p.kmh)) else lineTo(x(p.timeMs), y(p.kmh)) }
        }
        drawPath(path, c.accent.copy(alpha = 0.15f), style = Stroke(width = 1.4f * u.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, c.accent, style = Stroke(width = 0.35f * u.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 출발 / 도착
        val sq = 1.4f * u.toPx()
        listOf(Triple(trace.first(), startLabel, true), Triple(trace.last(), endLabel, false)).forEach { (p, label, isStart) ->
            val o = Offset(x(p.timeMs), y(p.kmh))
            drawRect(c.bgOuter, topLeft = o - Offset(sq / 2, sq / 2), size = Size(sq, sq))
            drawRect(c.accent, topLeft = o - Offset(sq / 2, sq / 2), size = Size(sq, sq), style = Stroke(0.3f * u.toPx()))
            // 축 라벨과 겹치지 않게 표시 위쪽, 그래프 안쪽으로
            val tl = measurer.measure(label, endpointStyle)
            val tx = if (isStart) o.x + sq else o.x - sq - tl.size.width
            drawText(tl, topLeft = Offset(tx, o.y - sq - tl.size.height))
        }

        // 판정 지점: 무난한 노트는 점, MISS·급감속은 번호 붙은 다이아몬드
        val dotR = 0.8f * u.toPx()
        val missTimes = data.report.notes.filter { it.grade == Grade.MISS }.map { it.startMs to it.endMs }
        val diamonds = (
            missTimes.map { it.first } +
                data.report.hardDecelTimesMs.filter { t -> missTimes.none { (s, e) -> t in s..e } }
            ).sorted()

        data.report.notes.filter { it.grade != Grade.MISS }.forEach { n ->
            val o = Offset(x(n.startMs), y(speedAt(n.startMs)))
            drawCircle(c.bgOuter, dotR + 2.dp.toPx(), o) // 표면 링
            drawCircle(c.gradeColor(n.grade), dotR, o)
        }
        diamonds.forEachIndexed { i, t ->
            val o = Offset(x(t), y(speedAt(t)))
            val r = 1.3f * u.toPx()
            drawCircle(
                c.danger.copy(alpha = 0.7f), r * 2.2f, o,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
            )
            drawDiamond(o, r, c.danger)
            val tl = measurer.measure(circled(i + 1), markStyle)
            val tx = (o.x + r * 2.6f).coerceAtMost(size.width - tl.size.width)
            drawText(tl, topLeft = Offset(tx, o.y - r * 2.6f - tl.size.height / 2))
        }

        // x축: 0:00 ~ 주행 시간
        val endSec = ((t1 - t0) / 1000).toInt()
        val l0 = measurer.measure("0:00", axisStyle)
        drawText(l0, topLeft = Offset(left, size.height - l0.size.height))
        val l1 = measurer.measure(String.format(Locale.US, "%d:%02d", endSec / 60, endSec % 60), axisStyle)
        drawText(l1, topLeft = Offset(right - l1.size.width, size.height - l1.size.height))
    }
}

private fun circled(n: Int): String = if (n in 1..20) ('①' + (n - 1)).toString() else "($n)"

private fun DrawScope.drawDiamond(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    drawPath(p, color)
}

// ---------- 오른쪽 카드들 ----------

@Composable
private fun CoachCard(data: ReportUi, u: Dp) {
    val c = LocalBeatColors.current
    Card(Modifier.fillMaxWidth(), u) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(u * 7)
                    .clip(CircleShape)
                    .border(1.5.dp, c.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("♪", color = c.accent, fontSize = (u * 3.4f).sp())
            }
            Spacer(Modifier.width(u * 2))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("COACH", u, c.accent)
                    Text("  ·  " + stringResource(R.string.report_coach), color = c.accent, fontFamily = KoreanFont, fontSize = (u * 1.7f).sp())
                }
                Spacer(Modifier.height(u * 0.6f))
                Text(
                    data.report.coaching,
                    color = c.text, fontFamily = KoreanFont, fontWeight = FontWeight.Medium,
                    fontSize = (u * 2.4f).sp(), lineHeight = (u * 3.4f).sp(),
                )
            }
        }
    }
}

@Composable
private fun StatRow(data: ReportUi, u: Dp) {
    val c = LocalBeatColors.current
    val r = data.report
    val perfect = r.gradeCounts[Grade.PERFECT] ?: 0
    val perfectPct = if (r.noteCount == 0) "–" else "${perfect * 100 / r.noteCount}%"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
        StatCard("PERFECT", null, perfectPct, c.text, u, Modifier.weight(1f))
        StatCard(
            null, stringResource(R.string.report_hard_decel_label),
            stringResource(R.string.report_count, r.hardDecelCount),
            if (r.hardDecelCount > 0) c.danger else c.text, u, Modifier.weight(1f),
        )
        StatCard(null, stringResource(R.string.report_max_combo_label), r.maxCombo.toString(), c.text, u, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(en: String?, ko: String?, value: String, valueColor: Color, u: Dp, modifier: Modifier) {
    val c = LocalBeatColors.current
    Card(modifier, u) {
        if (en != null) Label(en, u) else Text(ko.orEmpty(), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp())
        Spacer(Modifier.height(u * 0.5f))
        Text(value, color = valueColor, fontFamily = KoreanFont, fontWeight = FontWeight.SemiBold, fontSize = (u * 4f).sp())
    }
}

@Composable
private fun DistributionCard(data: ReportUi, u: Dp) {
    val c = LocalBeatColors.current
    val r = data.report
    Card(Modifier.fillMaxWidth(), u) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("JUDGE", stringResource(R.string.report_distribution), u)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.report_notes_total, r.noteCount), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp())
        }
        Spacer(Modifier.height(u * 1.2f))
        // 가로 누적 막대 (조각 사이 2dp 간격)
        Canvas(Modifier.fillMaxWidth().height(u * 2.4f)) {
            val gap = 2.dp.toPx()
            val radius = CornerRadius(4.dp.toPx())
            if (r.noteCount == 0) {
                drawRoundRect(c.textSub.copy(alpha = 0.15f), cornerRadius = radius)
                return@Canvas
            }
            val present = Grade.entries.filter { (r.gradeCounts[it] ?: 0) > 0 }
            val usable = size.width - gap * (present.size - 1)
            var x = 0f
            present.forEach { g ->
                val w = usable * (r.gradeCounts[g] ?: 0) / r.noteCount
                drawRoundRect(c.gradeColor(g), topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = radius)
                x += w + gap
            }
        }
        Spacer(Modifier.height(u * 1.2f))
        // 범례 겸 값 라벨: 색만으로 구분하지 않도록 이름과 개수를 함께
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Grade.entries.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(u * 1.4f).clip(RoundedCornerShape(3.dp)).background(c.gradeColor(g)))
                    Text(
                        "  ${g.name}  ",
                        color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(500),
                        fontSize = (u * 1.7f).sp(), letterSpacing = 0.1.em,
                    )
                    Text(
                        (r.gradeCounts[g] ?: 0).toString(),
                        color = c.text, fontFamily = Outfit, fontWeight = FontWeight(500), fontSize = (u * 2.2f).sp(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Last5(data: ReportUi, u: Dp) {
    val c = LocalBeatColors.current
    CardTitle("LAST 5", stringResource(R.string.report_last5), u)
    Spacer(Modifier.height(u * 1.2f))
    val scores = data.recentScores
    if (scores.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.report_last5_empty), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.9f).sp())
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(400), fontSize = (u * 1.9f).sp())
    val currentStyle = valueStyle.copy(color = c.text, fontWeight = FontWeight(600))
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val slots = 5
        val gap = 1.5f * u.toPx()
        val barW = (size.width - gap * (slots - 1)) / slots
        val labelH = measurer.measure("0.0", valueStyle).size.height + 0.6f * u.toPx()
        val maxH = size.height - labelH
        scores.forEachIndexed { i, s ->
            val current = i == scores.lastIndex
            val h = (maxH * (s / 5.0)).toFloat().coerceAtLeast(4.dp.toPx())
            val x = i * (barW + gap)
            drawRoundRect(
                if (current) c.accent else c.textSub.copy(alpha = 0.22f),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            val tl = measurer.measure(fmt1(s), if (current) currentStyle else valueStyle)
            drawText(tl, topLeft = Offset(x + (barW - tl.size.width) / 2, size.height - h - tl.size.height - 0.4f * u.toPx()))
        }
    }
}
