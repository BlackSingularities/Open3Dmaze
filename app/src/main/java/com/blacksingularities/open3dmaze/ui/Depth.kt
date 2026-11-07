package com.blacksingularities.open3dmaze.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.game.TiltSensor
import kotlinx.coroutines.isActive

/**
 * Wygładzone pochylenie telefonu dla efektów 3D interfejsu (−1…1). Wartości czyta się tylko
 * w `graphicsLayer`, więc ruch kart nie przebudowuje ekranu – odświeża jedynie warstwę.
 */
@Stable
class TiltFx {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

@Composable
fun rememberTiltFx(tilt: TiltSensor): TiltFx {
    val fx = remember { TiltFx() }
    LaunchedEffect(tilt) {
        while (isActive) {
            withFrameNanos {
                val s = Store.settings.value
                val tx = if (tilt.available) (tilt.rawX - s.calX).coerceIn(-1f, 1f) else 0f
                val ty = if (tilt.available) (tilt.rawY - s.calY).coerceIn(-1f, 1f) else 0f
                fx.x += (tx - fx.x) * 0.10f
                fx.y += (ty - fx.y) * 0.10f
            }
        }
    }
    return fx
}

/** Karta leży na „desce” razem z labiryntem: przechyla się w 3D zgodnie z telefonem. */
fun Modifier.tilt3d(fx: TiltFx, degrees: Float = 7f): Modifier = graphicsLayer {
    rotationY = -fx.x * degrees
    rotationX = fx.y * degrees
    cameraDistance = 16f * density
}

/** Delikatne wciśnięcie w głąb przy dotyku. */
@Composable
fun Modifier.press3d(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s; rotationX = (1f - s) * 40f; cameraDistance = 16f * density }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Szkło: ciemny gradient i jaśniejsza krawędź u góry, jak światło padające z góry. */
val GlassFill = Brush.verticalGradient(listOf(Color(0xEE1A1B21), Color(0xEE0B0C0F)))
val GlassEdge = Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x0DFFFFFF)))

fun Modifier.glass(shape: Shape): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(GlassFill)
    .border(1.dp, GlassEdge, shape)

/**
 * Przycisk-płytka: lico w kolorze akcentu stoi na ciemniejszym boku. Przy dotyku lico
 * zjeżdża w dół, jakby przycisk naprawdę się wciskał.
 */
@Composable
fun SlabButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: Color = accentColor(),
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 1.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 1200f), label = "slab")
    val shape = RoundedCornerShape(if (big) 26.dp else 20.dp)
    Box(modifier) {
        // Bok płytki i cień pod nią.
        Box(
            Modifier.matchParentSize().padding(top = 6.dp)
                .shadow(18.dp, shape, ambientColor = color, spotColor = color)
                .clip(shape).background(lerp(color, Color.Black, 0.55f)),
        )
        Column(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).offset(y = 6.dp - lift)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.28f), color, lerp(color, Color.Black, 0.12f))))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color.Transparent)), shape)
                .clickable(interactionSource = src, indication = null, onClick = onClick)
                .heightIn(min = if (big) 76.dp else 54.dp)
                .padding(horizontal = 22.dp, vertical = if (big) 14.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(text, color = Color.Black, fontWeight = FontWeight.Black, fontSize = if (big) 26.sp else 17.sp, letterSpacing = if (big) (-0.5).sp else 0.sp)
            if (subtitle != null) Text(subtitle, color = Color(0xA6000000), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Napis z wytłoczeniem: kilka warstw przesuniętych w głąb daje bryłę liter. */
@Composable
fun ExtrudedText(text: String, accent: Color, fontSize: Int = 46, modifier: Modifier = Modifier) {
    Box(modifier) {
        for (d in 6 downTo 1) {
            Text(
                text, fontSize = fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
                color = lerp(lerp(accent, Color.Black, 0.45f), Color.Black, d / 8f),
                modifier = Modifier.offset(x = (d * 0.6f).dp, y = (d * 1.1f).dp),
            )
        }
        Text(
            text, fontSize = fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
            style = androidx.compose.ui.text.TextStyle(brush = Brush.verticalGradient(listOf(Color.White, lerp(Color.White, accent, 0.35f)))),
        )
    }
}
