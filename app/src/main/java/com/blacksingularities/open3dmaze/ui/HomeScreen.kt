package com.blacksingularities.open3dmaze.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.data.Campaign
import com.blacksingularities.open3dmaze.data.Mode
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.LevelSpec
import com.blacksingularities.open3dmaze.game.MazeGen
import com.blacksingularities.open3dmaze.game.Phase
import java.time.LocalDate

@Composable
fun HomeScreen(nav: (Route) -> Unit, play: (Mode) -> Unit) {
    val progress by Store.progress.collectAsState()
    val settings by Store.settings.collectAsState()
    val accent = accentColor()
    val tilt = rememberTilt()
    // Żywe tło: prawdziwy labirynt, w którym kulka toczy się razem z pochyleniem telefonu.
    fun newDemo() = Game(MazeGen.generate(LevelSpec(System.nanoTime(), 9, 14, braid = 0.5f, gems = 8, portals = 1, boosts = 2)))
    var demo by remember { mutableStateOf(newDemo()) }
    val update = rememberUpdateState()
    LaunchedEffect(settings.autoUpdate) { if (settings.autoUpdate) update.check() }

    val fx = rememberTiltFx(tilt)
    // Wejście: elementy wyjeżdżają z głębi jeden po drugim.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    fun Modifier.rise(order: Int): Modifier = graphicsLayer {
        val t = (enter.value * 1.75f - order * 0.12f).coerceIn(0f, 1f)
        alpha = t
        translationY = (1f - t) * 60f * density
        rotationX = (1f - t) * 35f
        cameraDistance = 16f * density
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MazeView(demo, tilt, paused = false, modifier = Modifier.fillMaxSize(), dim = 0.55f, hud = false) {
            demo.events.clear()
            if (demo.phase == Phase.WON && demo.winT > 2.5f) demo = newDemo()
        }
        // Przyciemnienie u góry i u dołu – środek zostaje jasny, żeby żywy labirynt było widać.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0xF2000000), 0.32f to Color(0x80000000), 0.55f to Color(0x66000000), 1f to Color(0xF2000000)),
            ),
        )
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Column(Modifier.rise(0).tilt3d(fx, 10f)) {
                ExtrudedText("Open3Dmaze", accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("★ ${progress.totalStars}/${Campaign.COUNT * 3}")
                        if (progress.dailyStreak > 0) append("   🔥 ${progress.dailyStreak}")
                    },
                    color = Palette.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(28.dp))

            update.Banner()

            val nextLevel = (0 until Campaign.COUNT).firstOrNull { progress.stars[it] == 0 } ?: (Campaign.COUNT - 1)
            SlabButton(
                "Graj",
                Modifier.fillMaxWidth().rise(1).tilt3d(fx, 6f),
                subtitle = "Poziom ${Campaign.name(nextLevel)} · ${Campaign.worlds[nextLevel / Campaign.PER_WORLD].name}",
                big = true,
            ) { play(Mode.CAMPAIGN) }
            Spacer(Modifier.height(18.dp))

            val dailyDone = progress.dailyDone == LocalDate.now().toString()
            val tiles = listOf(
                Tile(Icons.Rounded.Map, "Poziomy", "${Campaign.COUNT} w ${Campaign.WORLDS} światach") { nav(Route.CampaignMap) },
                Tile(Icons.Rounded.Today, "Dnia", if (dailyDone) "Ukończony ✓" else "Nowy dziś") { play(Mode.DAILY) },
                Tile(Icons.Rounded.AllInclusive, "Bez końca", "Rekord ${progress.endlessBest}") { play(Mode.ENDLESS) },
                Tile(Icons.Rounded.Timer, "Na czas", "Rekord ${progress.timeAttackBest}") { play(Mode.TIME_ATTACK) },
                Tile(Icons.Rounded.DarkMode, "Mrok", "Rekord ${progress.darkBest}") { play(Mode.DARK) },
                Tile(Icons.Rounded.SelfImprovement, "Zen", "Bez pułapek") { play(Mode.ZEN) },
            )
            tiles.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).rise(2 + r).tilt3d(fx, 5f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { ModeTile(it, Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().rise(6),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundButton(Icons.Rounded.Tune, "Własny") { nav(Route.Custom) }
                RoundButton(Icons.Rounded.BarChart, "Statystyki") { nav(Route.Stats) }
                RoundButton(Icons.Rounded.Settings, "Ustawienia") { nav(Route.Settings) }
                RoundButton(Icons.Rounded.Info, "O grze") { nav(Route.About) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private class Tile(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
private fun ModeTile(t: Tile, modifier: Modifier) {
    val accent = accentColor()
    Column(
        modifier.press3d(onClick = t.onClick).glass(RoundedCornerShape(22.dp)).padding(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.08f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, null, tint = accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(t.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(t.subtitle, color = Palette.TextDim, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.press3d(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Palette.TextDim, fontSize = 12.sp)
    }
}
