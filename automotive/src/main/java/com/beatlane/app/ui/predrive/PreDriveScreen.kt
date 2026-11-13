package com.beatlane.app.ui.predrive

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.runtime.Composable
import com.beatlane.app.R
import com.beatlane.app.judge.Passenger
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.data.SignalStatus
import com.beatlane.app.ui.drive.DriveUiState
import com.beatlane.app.ui.drive.SourceKind
import com.beatlane.app.ui.theme.KoreanFont
import com.beatlane.app.ui.theme.LocalBeatColors
import com.beatlane.app.ui.theme.Outfit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val MinTouch = 76.dp

/** 출발 전 화면 (시안 A). 동승자 + 차종 모드 선택 후 주행 시작. */
@Composable
fun PreDriveScreen(
    state: DriveUiState,
    onPassenger: (Passenger) -> Unit,
    onPowertrain: (Powertrain) -> Unit,
    onSource: (SourceKind) -> Unit,
    onRequestSpeedPermission: () -> Unit,
    onSound: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val c = LocalBeatColors.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(c.bgInner, c.bgOuter))),
    ) {
        val u = maxHeight / 100
        Column(Modifier.fillMaxSize().padding(horizontal = u * 4, vertical = u * 3)) {
            TopBar(state, u, onSource)
            Spacer(Modifier.height(u * 1.5f))
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.05f), c.accent.copy(alpha = 0.6f), c.accent.copy(alpha = 0.05f))),
                ),
            )
            Spacer(Modifier.height(u * 2.5f))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 2.5f)) {
                CoachPanel(state, u, onRequestSpeedPermission, Modifier.weight(0.72f).fillMaxHeight())
                Column(Modifier.weight(1.28f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 2.5f)) {
                    PassengerCard(state.passenger, u, onPassenger)
                    PowertrainCard(state.powertrain, u, onPowertrain)
                    SoundCard(state.soundOn, u, onSound)
                    Spacer(Modifier.weight(1f))
                    StartButton(u, onStart)
                }
            }
        }
    }
}

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

@Composable
private fun TopBar(state: DriveUiState, u: Dp, onSource: (SourceKind) -> Unit) {
    val c = LocalBeatColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 막대 4개 로고
        Canvas(Modifier.size(u * 4)) {
            val heights = listOf(0.45f, 0.8f, 1f, 0.6f)
            val w = size.width / 7
            heights.forEachIndexed { i, h ->
                drawRoundRect(
                    c.accent,
                    topLeft = Offset(i * w * 1.9f, size.height * (1 - h)),
                    size = Size(w, size.height * h),
                    cornerRadius = CornerRadius(w / 3),
                )
            }
        }
        Spacer(Modifier.width(u * 1.5f))
        Text(
            "BEATLANE", color = c.text, fontFamily = Outfit, fontWeight = FontWeight(300),
            fontSize = (u * 3.4f).sp(), letterSpacing = 0.25.em,
        )
        Spacer(Modifier.width(u * 1.5f))
        Text(stringResource(R.string.pre_brand_ko), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(), letterSpacing = 0.1.em)
        Spacer(Modifier.weight(1f))
        SourceSegment(
            stringResource(R.string.source_vehicle),
            selected = !state.simulated,
            enabled = state.vehicleAvailable,
            u = u,
        ) { onSource(SourceKind.VEHICLE) }
        Spacer(Modifier.width(u))
        SourceSegment(
            stringResource(R.string.source_sim),
            selected = state.simulated,
            enabled = true,
            u = u,
        ) { onSource(SourceKind.SIMULATION) }
    }
}

@Composable
private fun SourceSegment(text: String, selected: Boolean, enabled: Boolean, u: Dp, onClick: () -> Unit) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.textSub.copy(alpha = 0.3f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = u * 2.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                selected -> c.text
                enabled -> c.textSub
                else -> c.textSub.copy(alpha = 0.4f)
            },
            fontFamily = KoreanFont, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = (u * 2f).sp(),
        )
    }
}

@Composable
private fun SignalRow(label: String, status: SignalStatus, u: Dp, onClick: (() -> Unit)? = null) {
    val c = LocalBeatColors.current
    val (color, text) = when (status) {
        SignalStatus.CHECKING -> c.textSub to R.string.signal_checking
        SignalStatus.READY -> c.regenNote to R.string.signal_ready
        SignalStatus.UNSUPPORTED -> c.brakeNote to R.string.signal_unsupported
        SignalStatus.NO_PERMISSION -> c.brakeNote to R.string.signal_no_permission
        SignalStatus.FAILED -> c.danger to R.string.signal_failed
    }
    // 권한 요청이 필요하면 누를 수 있게 (터치 영역 76dp)
    val rowModifier = if (onClick != null) {
        Modifier.heightIn(min = MinTouch).clip(RoundedCornerShape(u)).clickable(onClick = onClick).padding(end = u)
    } else Modifier
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(u * 1.1f).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(u * 0.8f))
        Text("$label  ", color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp())
        Text(
            stringResource(text) + if (onClick != null) "  ›" else "",
            color = if (onClick != null) c.accent else c.text,
            fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(),
        )
    }
}

@Composable
private fun Panel(modifier: Modifier, u: Dp, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 2.2f)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgInner.copy(alpha = 0.35f))
                .border(1.dp, c.textSub.copy(alpha = 0.18f), shape)
                .padding(horizontal = u * 3, vertical = u * 2.5f),
            content = content,
        )
        // 시안의 왼쪽 위 짧은 강조선
        Box(
            Modifier
                .padding(start = u * 2.2f)
                .width(u * 4)
                .height(2.dp)
                .background(c.accent),
        )
    }
}

@Composable
private fun SectionTitle(en: String, ko: String, hint: String, u: Dp) {
    val c = LocalBeatColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(en, color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(500), fontSize = (u * 1.7f).sp(), letterSpacing = 0.25.em)
        Text("  ·  $ko", color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.7f).sp())
        Spacer(Modifier.weight(1f))
        Text(hint, color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 1.8f).sp())
    }
}

// ---------- 왼쪽: 코치 패널 ----------

@Composable
private fun CoachPanel(state: DriveUiState, u: Dp, onRequestSpeedPermission: () -> Unit, modifier: Modifier) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 2.2f)
    Column(
        modifier
            .clip(shape)
            .background(c.bgInner.copy(alpha = 0.35f))
            .border(1.dp, c.textSub.copy(alpha = 0.18f), shape)
            .padding(horizontal = u * 3.5f, vertical = u * 3.5f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HexBadge(u * 13)
            Spacer(Modifier.width(u * 2.5f))
            Column {
                Text("COACH", color = c.accent, fontFamily = Outfit, fontWeight = FontWeight(600), fontSize = (u * 1.7f).sp(), letterSpacing = 0.25.em)
                Spacer(Modifier.height(u * 0.5f))
                Text(
                    stringResource(if (state.simulated) R.string.pre_coach_status_sim else R.string.pre_coach_status_vehicle),
                    color = c.text, fontFamily = KoreanFont, fontSize = (u * 2.1f).sp(),
                )
                Spacer(Modifier.height(u * 0.8f))
                SignalRow(
                    stringResource(R.string.signal_speed), state.speedStatus, u,
                    onClick = if (state.speedStatus == SignalStatus.NO_PERMISSION) onRequestSpeedPermission else null,
                )
                Spacer(Modifier.height(u * 0.3f))
                SignalRow(stringResource(R.string.signal_brake), state.brakeStatus, u)
            }
        }
        Spacer(Modifier.height(u * 6))
        Text(
            stringResource(R.string.pre_ready),
            color = c.text, fontFamily = KoreanFont, fontWeight = FontWeight.Light,
            fontSize = (u * 4.6f).sp(), lineHeight = (u * 6.4f).sp(),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.textSub.copy(alpha = 0.2f)))
        Spacer(Modifier.height(u * 2.2f))
        Text("LAST DRIVE", color = c.textSub, fontFamily = Outfit, fontWeight = FontWeight(500), fontSize = (u * 1.7f).sp(), letterSpacing = 0.25.em)
        Spacer(Modifier.height(u * 1.2f))
        val last = state.lastDrive
        if (last == null) {
            Text(stringResource(R.string.pre_first_drive), color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 2.1f).sp(), lineHeight = (u * 3.2f).sp())
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%.1f", last.score),
                    color = c.text, fontFamily = Outfit, fontWeight = FontWeight(300), fontSize = (u * 4.6f).sp(),
                )
                Text(
                    "  " + stringResource(R.string.pre_last_score_unit),
                    color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 2f).sp(),
                    modifier = Modifier.padding(bottom = u * 0.9f),
                )
            }
            Spacer(Modifier.height(u * 0.8f))
            Text(
                if (last.hasImprovement) stringResource(R.string.pre_last_point, last.coaching) else last.coaching,
                color = c.textSub, fontFamily = KoreanFont, fontSize = (u * 2f).sp(), lineHeight = (u * 3f).sp(),
            )
        }
    }
}

/** 육각형 + 음표 배지 (시안의 코파일럿 아이콘). */
@Composable
private fun HexBadge(size: Dp) {
    val c = LocalBeatColors.current
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val r = this.size.minDimension / 2
        drawCircle(
            c.accent.copy(alpha = 0.5f), r * 0.98f,
            style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))),
        )
        val hex = Path().apply {
            for (k in 0 until 6) {
                val a = Math.toRadians(-90.0 + k * 60.0)
                val x = cx + (r * 0.72f * cos(a)).toFloat()
                val y = cy + (r * 0.72f * sin(a)).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(hex, Color(0xFF1C3FA0).copy(alpha = 0.55f))
        drawPath(hex, Color(0xFF5B8CFF), style = Stroke(r * 0.04f))
        // 음표
        val stroke = r * 0.07f
        val noteColor = Color(0xFF8FB2FF)
        drawOval(noteColor, topLeft = Offset(cx - r * 0.26f, cy + r * 0.08f), size = Size(r * 0.3f, r * 0.22f), style = Stroke(stroke))
        drawLine(noteColor, Offset(cx + r * 0.03f, cy + r * 0.18f), Offset(cx + r * 0.03f, cy - r * 0.36f), strokeWidth = stroke)
        drawLine(noteColor, Offset(cx + r * 0.03f, cy - r * 0.36f), Offset(cx + r * 0.22f, cy - r * 0.2f), strokeWidth = stroke)
    }
}

// ---------- 오른쪽: 선택 카드 ----------

@Composable
private fun PassengerCard(selected: Passenger, u: Dp, onSelect: (Passenger) -> Unit) {
    Panel(Modifier.fillMaxWidth(), u) {
        SectionTitle("PASSENGER", stringResource(R.string.pre_passenger_title), stringResource(R.string.pre_passenger_hint), u)
        Spacer(Modifier.height(u * 2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
            // 시안 순서: 없음 · 아이 · 어르신 · 일반
            listOf(
                Triple(Passenger.NONE, R.string.pre_opt_none, R.string.pre_strict_none),
                Triple(Passenger.CHILD, R.string.pre_opt_child, R.string.pre_strict_child),
                Triple(Passenger.ELDERLY, R.string.pre_opt_elderly, R.string.pre_strict_elderly),
                Triple(Passenger.GENERAL, R.string.pre_opt_general, R.string.pre_strict_general),
            ).forEach { (p, title, sub) ->
                OptionCard(stringResource(title), stringResource(sub), p == selected, u) { onSelect(p) }
            }
        }
    }
}

@Composable
private fun PowertrainCard(selected: Powertrain, u: Dp, onSelect: (Powertrain) -> Unit) {
    val c = LocalBeatColors.current
    Panel(Modifier.fillMaxWidth(), u) {
        SectionTitle("POWERTRAIN", stringResource(R.string.pre_mode_title), stringResource(R.string.pre_mode_hint), u)
        Spacer(Modifier.height(u * 2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
            OptionCard(
                stringResource(R.string.pre_mode_gasoline), stringResource(R.string.pre_mode_gasoline_desc),
                selected == Powertrain.GASOLINE, u, swatch = c.brakeNote,
            ) { onSelect(Powertrain.GASOLINE) }
            OptionCard(
                stringResource(R.string.pre_mode_electrified), stringResource(R.string.pre_mode_electrified_desc),
                selected == Powertrain.ELECTRIFIED, u, swatch = c.regenNote,
            ) { onSelect(Powertrain.ELECTRIFIED) }
        }
    }
}

@Composable
private fun SoundCard(on: Boolean, u: Dp, onSelect: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth(), u) {
        SectionTitle("SOUND", stringResource(R.string.pre_sound_title), stringResource(R.string.pre_sound_hint), u)
        Spacer(Modifier.height(u * 2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 1.5f)) {
            OptionCard(stringResource(R.string.pre_sound_on), stringResource(R.string.pre_sound_on_desc), on, u) { onSelect(true) }
            OptionCard(stringResource(R.string.pre_sound_off), stringResource(R.string.pre_sound_off_desc), !on, u) { onSelect(false) }
        }
    }
}

@Composable
private fun RowScope.OptionCard(
    title: String,
    sub: String,
    selected: Boolean,
    u: Dp,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 1.6f)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = MinTouch)
            .height(u * 10)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.16f) else c.textSub.copy(alpha = 0.08f))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.textSub.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (swatch != null) {
                    Box(Modifier.size(u * 1.4f).clip(RoundedCornerShape(3.dp)).background(swatch))
                    Spacer(Modifier.width(u))
                }
                Text(
                    title,
                    color = if (selected) c.text else c.text.copy(alpha = 0.75f),
                    fontFamily = KoreanFont, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = (u * 2.8f).sp(),
                )
            }
            Spacer(Modifier.height(u * 0.4f))
            Text(
                sub,
                color = if (selected) c.text.copy(alpha = 0.85f) else c.textSub,
                fontFamily = KoreanFont, fontSize = (u * 1.8f).sp(), maxLines = 1,
            )
        }
    }
}

@Composable
private fun StartButton(u: Dp, onClick: () -> Unit) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 3)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .height(u * 13)
            .clip(shape)
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = u * 5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.pre_start), color = c.bgOuter, fontFamily = KoreanFont, fontSize = (u * 4f).sp())
        Spacer(Modifier.weight(1f))
        Text("ENGAGE", color = c.bgOuter, fontFamily = Outfit, fontWeight = FontWeight(600), fontSize = (u * 2f).sp(), letterSpacing = 0.25.em)
        Spacer(Modifier.width(u * 2))
        Text("→", color = c.bgOuter, fontFamily = Outfit, fontSize = (u * 3.4f).sp())
    }
}
