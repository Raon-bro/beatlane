package com.beatlane.app.ui.drive

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 내 차 그림. PNG 에셋이 있으면 그것을, 없으면 코드로 그린 SUV를 쓴다.
 *
 * 에셋 넣는 법 (docs/assets.md):
 * - `res/drawable-nodpi/car_rear.png` : 뒤에서 본 차, 투명 배경, 가로 800px 이상 권장
 * - `res/drawable-nodpi/car_rear_brake.png` (선택) : 브레이크등이 켜진 같은 그림
 *   없으면 [CarAssetSpec.lampY] 위치에 붉은 빛을 코드로 덧그린다.
 */
object CarAssetSpec {
    /** 이미지 높이 대비 리어램프 세로 위치 (0=위, 1=아래). 에셋에 맞게 조정. (현재 car_rear.png 기준) */
    const val lampY = 0.575f
    /** 이미지 폭 대비 리어램프 가로 길이. */
    const val lampWidth = 0.88f
}

class CarImages(val normal: ImageBitmap, val brake: ImageBitmap?)

@SuppressLint("DiscouragedApi") // 선택 에셋이라 이름으로 찾는다 (없으면 0)
private fun drawableId(context: Context, name: String): Int =
    context.resources.getIdentifier(name, "drawable", context.packageName)

/** 에셋이 있으면 불러 둔다. 없으면 null → 코드 그림 사용. */
@Composable
fun rememberCarImages(): CarImages? {
    val context = LocalContext.current
    val ids = remember { drawableId(context, "car_rear") to drawableId(context, "car_rear_brake") }
    if (ids.first == 0) return null
    val normal = ImageBitmap.imageResource(ids.first)
    val brake = if (ids.second != 0) ImageBitmap.imageResource(ids.second) else null
    return remember(normal, brake) { CarImages(normal, brake) }
}

/**
 * 앞차 그림 (`res/drawable-nodpi/lead_car.png`: 뒤에서 본 차, 브레이크등 켜짐, 투명 배경).
 * 없으면 null → 코드로 그린 SUV를 쓴다.
 */
@Composable
fun rememberLeadCarImage(): ImageBitmap? {
    val context = LocalContext.current
    val id = remember { drawableId(context, "lead_car") }
    if (id == 0) return null
    return ImageBitmap.imageResource(id)
}

/**
 * 도로 위 앞차를 그린다. [centerX], [groundY]는 차 바닥 가운데, [width]는 화면에서의 차 폭(px).
 * 멀리 있을 때는 [alpha]로 흐리게.
 */
fun DrawScope.drawLeadCar(image: ImageBitmap?, centerX: Float, groundY: Float, width: Float, alpha: Float, red: Color) {
    drawOval(
        Color.Black.copy(alpha = 0.45f * alpha),
        topLeft = Offset(centerX - width * 0.60f, groundY - width * 0.05f),
        size = Size(width * 1.20f, width * 0.10f),
    )
    if (image == null) {
        drawSuv(centerX, groundY, width, brake = true, red = red)
        return
    }
    val h = width * image.height / image.width
    drawImage(
        image,
        dstOffset = IntOffset((centerX - width / 2).roundToInt(), (groundY - h).roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
    )
}

/** 차를 그린다. [centerX], [groundY]는 차 바닥 가운데. [width]는 차 폭(px). */
fun DrawScope.drawCar(images: CarImages?, centerX: Float, groundY: Float, width: Float, brake: Boolean, red: Color) {
    // 그림자
    drawOval(
        Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(centerX - width * 0.62f, groundY - width * 0.06f),
        size = Size(width * 1.24f, width * 0.12f),
    )
    if (images != null) {
        drawCarImage(images, centerX, groundY, width, brake, red)
    } else {
        drawSuv(centerX, groundY, width, brake, red)
    }
}

private fun DrawScope.drawCarImage(images: CarImages, cx: Float, ground: Float, w: Float, brake: Boolean, red: Color) {
    val img = if (brake && images.brake != null) images.brake else images.normal
    val h = w * img.height / img.width
    val left = cx - w / 2
    val top = ground - h
    drawImage(
        img,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
    )
    if (brake && images.brake == null) {
        // 브레이크등 에셋이 없으면 램프 위치에 빛 번짐을 덧그린다
        val ly = top + h * CarAssetSpec.lampY
        val lw = w * CarAssetSpec.lampWidth
        val lh = w * 0.03f
        for (i in 4 downTo 1) {
            val s = lh * i
            drawRoundRect(
                red.copy(alpha = 0.10f),
                topLeft = Offset(cx - lw / 2 - s, ly - lh / 2 - s),
                size = Size(lw + s * 2, lh + s * 2),
                cornerRadius = CornerRadius(lh / 2 + s),
            )
        }
    }
}

/**
 * 코드로 그린 SUV 뒷모습 (시안 G3~G7의 흰색 SUV 느낌).
 * 위에서 살짝 내려다본 각도: 검은 파노라마 지붕 · 루프레일 · 뒷유리 · 얇은 가로형 램프 · 검은 하단 클래딩.
 * 모든 치수는 차 폭 W 기준 비율.
 */
private fun DrawScope.drawSuv(cx: Float, ground: Float, w: Float, brake: Boolean, red: Color) {
    fun y(k: Float) = ground - w * k
    fun x(k: Float) = cx + w * k
    fun rr(color: Color, l: Float, r: Float, top: Float, bottom: Float, radius: Float) = drawRoundRect(
        color, topLeft = Offset(x(l), y(top)), size = Size(w * (r - l), w * (top - bottom)), cornerRadius = CornerRadius(w * radius),
    )

    val white = Color(0xFFF2F6F8)
    val whiteShade = Color(0xFFC9D3D8)
    val cladding = Color(0xFF2E373C)
    val glassTop = Color(0xFF3B5560)
    val glassBottom = Color(0xFF111A1F)

    // 타이어
    rr(Color(0xFF0A0F12), -0.47f, -0.31f, 0.15f, 0.0f, 0.03f)
    rr(Color(0xFF0A0F12), 0.31f, 0.47f, 0.15f, 0.0f, 0.03f)

    // 지붕 (위에서 보이는 검은 파노라마 루프) + 루프레일
    val roof = Path().apply {
        moveTo(x(-0.33f), y(0.615f))
        lineTo(x(-0.27f), y(0.71f))
        quadraticTo(x(-0.25f), y(0.725f), x(-0.21f), y(0.725f))
        lineTo(x(0.21f), y(0.725f))
        quadraticTo(x(0.25f), y(0.725f), x(0.27f), y(0.71f))
        lineTo(x(0.33f), y(0.615f))
        close()
    }
    drawPath(roof, Brush.verticalGradient(listOf(Color(0xFF26343A), Color(0xFF0B1115)), startY = y(0.725f), endY = y(0.615f)))
    drawLine(Color.White.copy(alpha = 0.22f), Offset(x(-0.12f), y(0.70f)), Offset(x(0.10f), y(0.70f)), strokeWidth = w * 0.006f)
    for (side in listOf(-1f, 1f)) {
        drawLine(
            Color(0xFFAEB9BE),
            Offset(x(side * 0.335f), y(0.62f)), Offset(x(side * 0.275f), y(0.715f)),
            strokeWidth = w * 0.014f,
        )
    }

    // 캐빈 (D필러) : 흰색 사다리꼴
    val cabin = Path().apply {
        moveTo(x(-0.44f), y(0.40f))
        lineTo(x(-0.35f), y(0.62f))
        lineTo(x(0.35f), y(0.62f))
        lineTo(x(0.44f), y(0.40f))
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(white, whiteShade), startY = y(0.62f), endY = y(0.40f)))

    // 스포일러 립 + 보조 제동등
    rr(white, -0.34f, 0.34f, 0.63f, 0.595f, 0.015f)
    rr(if (brake) red else red.copy(alpha = 0.25f), -0.07f, 0.07f, 0.613f, 0.603f, 0.005f)

    // 뒷유리
    val glass = Path().apply {
        moveTo(x(-0.37f), y(0.44f))
        lineTo(x(-0.30f), y(0.585f))
        lineTo(x(0.30f), y(0.585f))
        lineTo(x(0.37f), y(0.44f))
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(glassTop, glassBottom), startY = y(0.585f), endY = y(0.44f)))
    drawLine(Color.White.copy(alpha = 0.16f), Offset(x(-0.22f), y(0.565f)), Offset(x(-0.04f), y(0.565f)), strokeWidth = w * 0.006f)

    // 몸통 (테일게이트)
    drawRoundRect(
        Brush.verticalGradient(listOf(white, whiteShade), startY = y(0.43f), endY = y(0.15f)),
        topLeft = Offset(x(-0.5f), y(0.43f)),
        size = Size(w, w * 0.28f),
        cornerRadius = CornerRadius(w * 0.07f),
    )

    // 하단 클래딩 + 스키드 플레이트
    rr(cladding, -0.49f, 0.49f, 0.19f, 0.065f, 0.035f)
    rr(Color(0xFFBFC9CE), -0.17f, 0.17f, 0.105f, 0.08f, 0.01f)

    // 번호판
    rr(Color(0xFFDDE5E8), -0.11f, 0.11f, 0.29f, 0.235f, 0.01f)

    // 가로형 리어램프 (얇은 바 + 양 끝 블록). 브레이크 시 밝아지고 번진다.
    val lampColor = if (brake) Color(0xFFFF4D57) else Color(0xFF7A2A31)
    val lampTopK = 0.375f
    val lampH = w * 0.022f
    if (brake) {
        for (i in 4 downTo 1) {
            val s = lampH * i * 1.1f
            drawRoundRect(
                red.copy(alpha = 0.09f),
                topLeft = Offset(x(-0.48f) - s, y(lampTopK) - s),
                size = Size(w * 0.96f + s * 2, lampH + s * 2),
                cornerRadius = CornerRadius(lampH / 2 + s),
            )
        }
    }
    drawRoundRect(lampColor, Offset(x(-0.47f), y(lampTopK)), Size(w * 0.94f, lampH), CornerRadius(lampH / 2))
    rr(lampColor, -0.495f, -0.39f, lampTopK + 0.012f, lampTopK - 0.045f, 0.012f)
    rr(lampColor, 0.39f, 0.495f, lampTopK + 0.012f, lampTopK - 0.045f, 0.012f)
    if (brake) {
        drawRoundRect(
            Color.White.copy(alpha = 0.55f),
            Offset(x(-0.44f), y(lampTopK) + lampH * 0.38f),
            Size(w * 0.88f, lampH * 0.24f),
            CornerRadius(lampH * 0.12f),
        )
    }
}
