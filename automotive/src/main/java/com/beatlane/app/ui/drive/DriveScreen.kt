package com.beatlane.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.beatlane.app.R
import com.beatlane.app.data.sim.SimScenario
import com.beatlane.app.judge.Grade
import com.beatlane.app.judge.NotePhase
import com.beatlane.app.judge.NoteType
import com.beatlane.app.judge.Passenger
import com.beatlane.app.judge.Powertrain
import com.beatlane.app.ui.theme.BeatColors
import com.beatlane.app.ui.theme.KoreanFont
import com.beatlane.app.ui.theme.LocalBeatColors
import com.beatlane.app.ui.theme.Outfit
import java.util.Locale

/** 최소 터치 영역 (CLAUDE.md: 76dp). */
private val MinTouch = 76.dp

/** 예고 노트가 이 시간 안으로 다가오면 "브레이크 밟기 n초 전"을 띄운다. */
private const val FORECAST_COUNTDOWN_SEC = 6f

@Composable
fun DriveScreen(
    state: DriveUiState,
    onPlay: (SimScenario) -> Unit,
    onPowertrain: (Powertrain) -> Unit,
    onPassenger: (Passenger) -> Unit,
    onFinish: () -> Unit,
    onSound: (Boolean) -> Unit,
) {
    val c = LocalBeatColors.current
    var showSim by rememberSaveable { mutableStateOf(true) }
    val ev = state.powertrain == Powertrain.ELECTRIFIED
    val modeColor = if (ev) c.regenNote else c.brakeNote

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(c.bgInner, c.bgOuter))),
    ) {
        val u = maxHeight / 100 // 화면 높이 1% 단위
        val gaugeSize = minOf(maxHeight * 0.48f, maxWidth * 0.22f)

        Column(Modifier.fillMaxSize().padding(horizontal = u * 3, vertical = u * 2)) {
            Header(state, modeColor, u)

            Row(
                Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedGauge(state.speedKmh, c.accent, gaugeSize)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val laneH = maxHeight
                    LaneView(
                        speedMps = state.speedKmh / 3.6f,
                        brakePressed = state.brakePressed,
                        activeNote = state.activeNote,
                        judgeLineColor = modeColor,
                        lastResult = state.lastResult,
                        resultSeq = state.resultSeq,
                        modifier = Modifier.fillMaxSize(),
                        forecast = state.forecast,
                    )
                    // 판정선(높이 70%) 왼쪽: HOLD 게이지
                    HoldGauge(
                        state, u,
                        Modifier.align(Alignment.TopStart).padding(start = u, top = laneH * 0.70f - u * 26f),
                    )
                    // 오른쪽: 이상적인 감속 곡선 따라 그리기 (판정 글자 아래, 도로 바깥)
                    GuideGraph(state, u, Modifier.align(Alignment.TopEnd).padding(top = laneH * 0.40f))
                    JudgementText(state, u, Modifier.align(Alignment.TopCenter).padding(top = u * 14))
                    ComboMilestoneBanner(
                        state.comboMilestoneSeq, state.combo, u,
                        Modifier.align(Alignment.TopCenter).padding(top = u * 3),
                    )
                }
                ScoreGauge(state.rideScore, state.combo, if (ev) c.regenNote else c.accent, gaugeSize, state.comboMilestoneSeq)
            }

            BottomBar(state, u, onLogoClick = { showSim = !showSim }, onSound = onSound)

            if (showSim) {
                Spacer(Modifier.height(u * 1.5f))
                SimPanel(state, u, onPlay, onPowertrain, onPassenger, onFinish)
            }
        }
    }
}

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

@Composable
private fun Header(state: DriveUiState, modeColor: Color, u: Dp) {
    val c = LocalBeatColors.current
    val ev = state.powertrain == Powertrain.ELECTRIFIED
    // 예고 중(시뮬레이션): 아직 감속 전이면 남은 시간을 알려 준다
    val fc = state.forecast
    val forecastShown = fc != null && fc.noteVisible
    val secondsToStart = fc?.secondsToStart ?: 0f
    val forecastByBrake = fc?.byBrake != false
    val forecasting = forecastShown && state.activeNote == null &&
        secondsToStart > 0f && secondsToStart <= FORECAST_COUNTDOWN_SEC
    val phaseHeadline = stringResource(
        when (state.activeNote?.phase) {
            null -> if (ev) R.string.headline_idle_electrified else R.string.headline_idle_gasoline
            NotePhase.START -> R.string.headline_start
            NotePhase.HOLD -> R.string.headline_hold
            NotePhase.END -> if (ev) R.string.headline_end_electrified else R.string.headline_end_gasoline
            NotePhase.LIFT -> R.string.headline_lift
            NotePhase.REGEN -> R.string.headline_regen
        },
    )
    val headline = if (forecasting) {
        stringResource(
            if (forecastByBrake) R.string.headline_forecast else R.string.headline_forecast_regen,
            String.format(Locale.US, "%.1f", secondsToStart),
        )
    } else {
        phaseHeadline
    }
    val noteColor = when (state.activeNote?.type) {
        NoteType.BRAKE -> c.brakeNote
        NoteType.REGEN -> c.regenNote
        null -> if (forecasting) (if (forecastByBrake) c.brakeNote else c.regenNote) else c.text
    }

    // 급감속 경고 깜빡임
    val hardAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.hardDecelSeq) {
        if (state.hardDecelSeq > 0) {
            hardAlpha.snapTo(1f)
            hardAlpha.animateTo(0f, tween(2000))
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(
                text = stringResource(if (ev) R.string.mode_electrified else R.string.mode_gasoline),
                color = modeColor,
                filled = true,
                u = u,
            )
            if (forecastShown) {
                Spacer(Modifier.width(u))
                Chip(
                    stringResource(R.string.chip_lead_car),
                    if (forecastByBrake) c.brakeNote else c.regenNote,
                    filled = false,
                    u = u,
                )
                // 예고는 센서가 아니라 시뮬레이션 시나리오에서 계산한 것: 확장 단계 기능임을 밝힌다
                Spacer(Modifier.width(u))
                Chip(stringResource(R.string.chip_preview), c.textSub, filled = false, u = u)
            }
            if (hardAlpha.value > 0f) {
                Spacer(Modifier.width(u))
                Box(Modifier.graphicsLayer { alpha = hardAlpha.value }) {
                    Chip(stringResource(R.string.hard_decel), c.danger, filled = true, u = u)
                }
            }
            Spacer(Modifier.width(u * 2))
            Text(
                text = headline,
                color = noteColor,
                fontFamily = KoreanFont,
                fontWeight = FontWeight.Medium,
                fontSize = (u * 4.6f).sp(),
            )
        }
        Spacer(Modifier.height(u * 0.6f))
        Text(
            text = stringResource(if (ev) R.string.sub_electrified else R.string.sub_gasoline),
            color = c.textSub,
            fontFamily = KoreanFont,
            fontSize = (u * 2.1f).sp(),
        )
    }
}

@Composable
private fun Chip(text: String, color: Color, filled: Boolean, u: Dp) {
    val c = LocalBeatColors.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color, shape))
            .padding(horizontal = u * 1.8f, vertical = u * 0.7f),
    ) {
        Text(
            text = text,
            color = if (filled) c.bgOuter else color,
            fontFamily = KoreanFont,
            fontWeight = FontWeight.Bold,
            fontSize = (u * 2.2f).sp(),
        )
    }
}

@Composable
private fun JudgementText(state: DriveUiState, u: Dp, modifier: Modifier) {
    val c = LocalBeatColors.current
    val result = state.lastResult ?: return
    val (alpha, scale) = rememberJudgementAlpha(state.resultSeq)
    if (alpha <= 0f) return
    Column(
        modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = result.grade.name,
            color = c.gradeColor(result.grade),
            fontFamily = Outfit,
            fontWeight = FontWeight(300),
            fontSize = (u * 9f).sp(),
            letterSpacing = 0.12.em,
        )
        if (state.combo >= 2 && result.grade != Grade.MISS) {
            Text(
                text = "${state.combo} COMBO",
                color = c.text,
                fontFamily = Outfit,
                fontWeight = FontWeight(300),
                fontSize = (u * 3f).sp(),
                letterSpacing = 0.2.em,
            )
        }
    }
}

@Composable
private fun BottomBar(state: DriveUiState, u: Dp, onLogoClick: () -> Unit, onSound: (Boolean) -> Unit) {
    val c = LocalBeatColors.current
    val ev = state.powertrain == Powertrain.ELECTRIFIED
    val phase = state.activeNote?.phase

    data class Item(val label: String, val desc: String, val color: Color, val active: Boolean)

    val items = if (ev) {
        listOf(
            Item("LIFT", stringResource(R.string.legend_lift), c.regenNote, phase == NotePhase.LIFT),
            Item("REGEN", stringResource(R.string.legend_regen), c.regenNote, phase == NotePhase.REGEN),
            Item(
                "BRAKE", stringResource(R.string.legend_brake), c.brakeNote,
                state.activeNote?.type == NoteType.BRAKE,
            ),
        )
    } else {
        listOf(
            Item("START", stringResource(R.string.legend_start), c.brakeNote, phase == NotePhase.START),
            Item("HOLD", stringResource(R.string.legend_hold), c.brakeNote, phase == NotePhase.HOLD),
            Item("RELEASE", stringResource(R.string.legend_release), c.brakeNote, phase == NotePhase.END),
        )
    }

    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.heightIn(min = MinTouch).widthIn(min = MinTouch).clickable(onClick = onLogoClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "BEATLANE",
                color = c.accent,
                fontFamily = Outfit,
                fontWeight = FontWeight(500),
                fontSize = (u * 1.9f).sp(),
                letterSpacing = 0.3.em,
            )
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(u * 4, Alignment.CenterHorizontally),
        ) {
            items.forEach { item ->
                Text(
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = Outfit,
                                fontWeight = FontWeight(600),
                                letterSpacing = 0.15.em,
                                color = item.color,
                            ),
                        ) { append(item.label) }
                        append("  ")
                        append(item.desc)
                    },
                    color = if (item.active) c.text else c.textSub,
                    fontFamily = KoreanFont,
                    fontSize = (u * (if (item.active) 2.6f else 2.1f)).sp(),
                    modifier = Modifier.graphicsLayer { alpha = if (phase == null || item.active) 1f else 0.55f },
                )
            }
        }
        Text(
            stringResource(passengerLabel(state.passenger)) + "  ·  " +
                stringResource(if (state.simulated) R.string.source_label_sim else R.string.source_label_vehicle),
            color = c.textSub,
            fontFamily = KoreanFont,
            fontSize = (u * 2.1f).sp(),
        )
        Spacer(Modifier.width(u))
        // 효과음 켜기/끄기 (터치 영역 76dp)
        Box(
            Modifier
                .size(MinTouch)
                .clip(RoundedCornerShape(50))
                .clickable { onSound(!state.soundOn) },
            contentAlignment = Alignment.Center,
        ) {
            SpeakerIcon(state.soundOn, if (state.soundOn) c.accent else c.textSub, MinTouch * 0.5f)
        }
    }
}

private fun passengerLabel(p: Passenger) = when (p) {
    Passenger.NONE -> R.string.passenger_none
    Passenger.GENERAL -> R.string.passenger_general
    Passenger.CHILD -> R.string.passenger_child
    Passenger.ELDERLY -> R.string.passenger_elderly
}

@Composable
private fun SimPanel(
    state: DriveUiState,
    u: Dp,
    onPlay: (SimScenario) -> Unit,
    onPowertrain: (Powertrain) -> Unit,
    onPassenger: (Passenger) -> Unit,
    onFinish: () -> Unit,
) {
    val c = LocalBeatColors.current
    val ev = state.powertrain == Powertrain.ELECTRIFIED
    val scenarios = buildList {
        add(SimScenario.SMOOTH_STOP to R.string.sim_smooth_stop)
        add(SimScenario.HARD_STOP to R.string.sim_hard_stop)
        if (ev) {
            add(SimScenario.REGEN_SLOW to R.string.sim_regen_slow)
            add(SimScenario.REGEN_FAST to R.string.sim_regen_fast)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(u * 2))
            .background(c.bgOuter.copy(alpha = 0.6f))
            .padding(u),
        horizontalArrangement = Arrangement.spacedBy(u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.sim_title),
            color = c.textSub,
            fontFamily = Outfit,
            fontWeight = FontWeight(500),
            fontSize = (u * 1.6f).sp(),
            letterSpacing = 0.25.em,
            modifier = Modifier.padding(horizontal = u),
        )
        scenarios.forEach { (scenario, label) ->
            PanelButton(
                text = stringResource(label),
                color = if (scenario == SimScenario.REGEN_SLOW || scenario == SimScenario.REGEN_FAST) c.regenNote else c.brakeNote,
                selected = state.simPlaying == scenario,
                u = u,
                modifier = Modifier.weight(1f),
            ) { onPlay(scenario) }
        }
        PanelButton(
            text = stringResource(if (ev) R.string.mode_electrified else R.string.mode_gasoline),
            color = c.accent,
            selected = false,
            u = u,
            modifier = Modifier.weight(0.8f),
        ) { onPowertrain(if (ev) Powertrain.GASOLINE else Powertrain.ELECTRIFIED) }
        PanelButton(
            text = stringResource(passengerLabel(state.passenger)),
            color = c.accent,
            selected = false,
            u = u,
            modifier = Modifier.weight(0.8f),
        ) { onPassenger(Passenger.entries[(state.passenger.ordinal + 1) % Passenger.entries.size]) }
        PanelButton(
            text = stringResource(R.string.finish_drive),
            color = c.text,
            selected = false,
            u = u,
            modifier = Modifier.weight(0.8f),
        ) { onFinish() }
    }
}

@Composable
private fun PanelButton(
    text: String,
    color: Color,
    selected: Boolean,
    u: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c: BeatColors = LocalBeatColors.current
    val shape = RoundedCornerShape(u * 1.5f)
    Box(
        modifier
            .heightIn(min = MinTouch)
            .height(u * 8)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.25f) else c.bgInner.copy(alpha = 0.5f))
            .border(2.dp, color.copy(alpha = if (selected) 1f else 0.5f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.text else color,
            fontFamily = KoreanFont,
            fontWeight = FontWeight.Medium,
            fontSize = (u * 2.3f).sp(),
            maxLines = 1,
        )
    }
}
