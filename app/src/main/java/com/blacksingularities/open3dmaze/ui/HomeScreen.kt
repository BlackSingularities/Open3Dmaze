package com.blacksingularities.open3dmaze.ui

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
import androidx.compose.ui.graphics.Color
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MazeView(demo, tilt, paused = false, modifier = Modifier.fillMaxSize(), dim = 0.5f) {
            demo.events.clear()
            if (demo.phase == Phase.WON && demo.winT > 2.5f) demo = newDemo()
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(36.dp))
            Text("Open3Dmaze", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("Pochyl telefon. Ściany pójdą za Tobą.", color = Palette.TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("★ ${progress.totalStars}/${Campaign.COUNT * 3}")
                Pill("Ukończone: ${progress.levelsDone}")
                if (progress.dailyStreak > 0) Pill("🔥 ${progress.dailyStreak}")
            }
            Spacer(Modifier.height(22.dp))

            update.Banner()

            val nextLevel = (0 until Campaign.COUNT).firstOrNull { progress.stars[it] == 0 } ?: (Campaign.COUNT - 1)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(accent).clickable { play(Mode.CAMPAIGN) }.padding(20.dp),
            ) {
                Column {
                    Text("Graj", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("Kampania · poziom ${Campaign.name(nextLevel)} · ${Campaign.worlds[nextLevel / Campaign.PER_WORLD].name}", color = Color(0xB3000000), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            ModeCard(Icons.Rounded.Map, "Wybór poziomu", "${Campaign.WORLDS} światów, ${Campaign.COUNT} poziomów") { nav(Route.CampaignMap) }
            val dailyDone = progress.dailyDone == LocalDate.now().toString()
            ModeCard(Icons.Rounded.Today, Mode.DAILY.title, if (dailyDone) "Ukończony dziś – popraw czas" else Mode.DAILY.subtitle) { play(Mode.DAILY) }
            ModeCard(Icons.Rounded.AllInclusive, Mode.ENDLESS.title, "Rekord: etap ${progress.endlessBest}") { play(Mode.ENDLESS) }
            ModeCard(Icons.Rounded.Timer, Mode.TIME_ATTACK.title, "Rekord: ${progress.timeAttackBest} labiryntów") { play(Mode.TIME_ATTACK) }
            ModeCard(Icons.Rounded.DarkMode, Mode.DARK.title, "Rekord: etap ${progress.darkBest}") { play(Mode.DARK) }
            ModeCard(Icons.Rounded.SelfImprovement, Mode.ZEN.title, Mode.ZEN.subtitle) { play(Mode.ZEN) }
            ModeCard(Icons.Rounded.Tune, Mode.CUSTOM.title, Mode.CUSTOM.subtitle) { nav(Route.Custom) }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTile(Icons.Rounded.BarChart, "Statystyki", Modifier.weight(1f)) { nav(Route.Stats) }
                SmallTile(Icons.Rounded.Settings, "Ustawienia", Modifier.weight(1f)) { nav(Route.Settings) }
                SmallTile(Icons.Rounded.Info, "O aplikacji", Modifier.weight(1f)) { nav(Route.About) }
            }
            Spacer(Modifier.height(24.dp))
            Credits()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000)).border(1.dp, Palette.Line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xCC0A0A0A))
            .border(1.dp, Palette.Line, RoundedCornerShape(22.dp)).clickable(onClick = onClick).heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.PanelHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accentColor())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SmallTile(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xCC0A0A0A)).border(1.dp, Palette.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp)
    }
}
