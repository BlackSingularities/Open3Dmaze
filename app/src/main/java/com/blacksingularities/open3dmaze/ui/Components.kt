package com.blacksingularities.open3dmaze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.data.Store

const val AUTHOR_URL = "https://redzikowski.com"
const val COFFEE_URL = "https://buycoffee.to/adamredzikowski"

@Composable
fun accentColor(): Color {
    val s by Store.settings.collectAsState()
    return Accents.getOrElse(s.accent) { Accents[0] }.color
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", tint = Color.White) }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), fontSize = 12.sp, letterSpacing = 1.5.sp, color = Palette.TextFaint,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel)
                .border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).padding(vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
fun ActionRow(icon: ImageVector?, title: String, subtitle: String? = null, tint: Color = Color.White, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.PanelHigh), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 13.sp)
        }
        if (trailing != null) Text(trailing, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.TextFaint)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = accentColor()
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 13.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = accent,
                uncheckedThumbColor = Palette.TextDim, uncheckedTrackColor = Palette.PanelHigh, uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, steps: Int = 0, onChange: (Float) -> Unit) {
    val accent = accentColor()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(label, color = Palette.TextDim, fontSize = 14.sp)
        }
        Slider(
            value, onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Palette.PanelHigh,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: Int, label: (T) -> String, onSelect: (Int) -> Unit) {
    val accent = accentColor()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) accent else Palette.PanelHigh)
                        .clickable { onSelect(i) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = accentColor()
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(accent).clickable(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(50)).border(1.dp, Palette.Line, RoundedCornerShape(50))
            .background(Palette.Panel).clickable(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

/** Jednolita stopka autorska – ta sama treść w aplikacji i na stronie. */
@Composable
fun Credits(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Pomysł i wykonanie: Adam Rędzikowski · redzikowski.com",
            color = Palette.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { runCatching { uri.openUri(AUTHOR_URL) } }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50)).background(Palette.Coffee)
                .clickable { runCatching { uri.openUri(COFFEE_URL) } }.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("☕", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text("Postaw kawę na buycoffee.to", color = Palette.OnCoffee, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

fun formatTime(sec: Float): String {
    val ms = (sec * 1000).toLong().coerceAtLeast(0)
    return "%d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)
}
