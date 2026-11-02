package com.blacksingularities.open3dmaze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.BuildConfig
import com.blacksingularities.open3dmaze.data.Achievements
import com.blacksingularities.open3dmaze.data.Campaign
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.game.LevelSpec
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
private fun Screen(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), content = content)
    }
}

@Composable
fun CampaignScreen(onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val progress by Store.progress.collectAsState()
    val accent = accentColor()
    Screen("Kampania", onBack) {
        items(Campaign.WORLDS) { w ->
            val world = Campaign.worlds[w]
            val got = (0 until Campaign.PER_WORLD).sumOf { progress.stars[w * Campaign.PER_WORLD + it] }
            Panel(title = "Świat ${w + 1} · ${world.name}  ★ $got/${Campaign.PER_WORLD * 3}") {
                Text(world.about, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                for (row in 0 until Campaign.PER_WORLD / 4) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 4) {
                            val i = w * Campaign.PER_WORLD + row * 4 + c
                            val open = progress.unlocked(i)
                            val st = progress.stars[i]
                            Column(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (st == 3) accent.copy(alpha = 0.18f) else Palette.PanelHigh)
                                    .border(1.dp, if (st > 0) accent.copy(alpha = 0.6f) else Palette.Line, RoundedCornerShape(16.dp))
                                    .clickable(enabled = open) { onPlay(i) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (open) {
                                    Text("${row * 4 + c + 1}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("★".repeat(st) + "☆".repeat(3 - st), color = if (st > 0) Palette.Gold else Palette.TextFaint, fontSize = 12.sp)
                                    val best = progress.best["c$i"]
                                    if (best != null) Text(formatTime(best / 1000f), color = Palette.TextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                } else {
                                    Icon(Icons.Rounded.Lock, "Zablokowany", tint = Palette.TextFaint)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun CustomScreen(onBack: () -> Unit, onPlay: (LevelSpec) -> Unit) {
    var cw by remember { mutableFloatStateOf(9f) }
    var ch by remember { mutableFloatStateOf(14f) }
    var holes by remember { mutableFloatStateOf(6f) }
    var gems by remember { mutableFloatStateOf(5f) }
    var ice by remember { mutableFloatStateOf(0.1f) }
    var portals by remember { mutableFloatStateOf(1f) }
    var boosts by remember { mutableFloatStateOf(2f) }
    var checks by remember { mutableFloatStateOf(1f) }
    var braid by remember { mutableFloatStateOf(0.25f) }
    var dark by remember { mutableStateOf(false) }
    var seed by remember { mutableLongStateOf(Random.nextLong(1, 999_999)) }

    Screen("Własny labirynt", onBack) {
        item {
            Panel(title = "Rozmiar") {
                SliderRow("Szerokość", cw, 3f..20f, "${cw.roundToInt()} pól", steps = 16) { cw = it }
                SliderRow("Wysokość", ch, 4f..30f, "${ch.roundToInt()} pól", steps = 25) { ch = it }
                SliderRow("Pętle i skróty", braid, 0f..0.8f, "${(braid * 100).roundToInt()}%") { braid = it }
            }
            Panel(title = "Pułapki i pomoce") {
                SliderRow("Dziury", holes, 0f..30f, "${holes.roundToInt()}", steps = 29) { holes = it }
                SliderRow("Lód", ice, 0f..0.6f, "${(ice * 100).roundToInt()}%") { ice = it }
                SliderRow("Pary portali", portals, 0f..4f, "${portals.roundToInt()}", steps = 3) { portals = it }
                SliderRow("Przyspieszacze", boosts, 0f..8f, "${boosts.roundToInt()}", steps = 7) { boosts = it }
                SliderRow("Punkty kontrolne", checks, 0f..3f, "${checks.roundToInt()}", steps = 2) { checks = it }
                SliderRow("Klejnoty", gems, 0f..12f, "${gems.roundToInt()}", steps = 11) { gems = it }
                SwitchRow("Mrok", "Widać tylko okolicę kulki", dark) { dark = it }
            }
            Panel(title = "Ziarno") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#$seed", color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                        Text("To samo ziarno i ustawienia = ten sam labirynt", color = Palette.TextDim, fontSize = 13.sp)
                    }
                    GhostButton("Losuj") { seed = Random.nextLong(1, 999_999) }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Graj", Modifier.fillMaxWidth()) {
                onPlay(
                    LevelSpec(
                        seed, cw.roundToInt(), ch.roundToInt(), braid = braid, holes = holes.roundToInt(), gems = gems.roundToInt(),
                        ice = ice, portals = portals.roundToInt(), checkpoints = checks.roundToInt(), boosts = boosts.roundToInt(), dark = dark,
                    ),
                )
            }
        }
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val p by Store.progress.collectAsState()
    val accent = accentColor()
    var confirm by remember { mutableStateOf(false) }
    Screen("Statystyki", onBack) {
        item {
            Panel(title = "Podsumowanie") {
                val h = p.playMs / 3_600_000
                val m = (p.playMs / 60_000) % 60
                Grid(
                    listOf(
                        "Gwiazdki" to "${p.totalStars}/${Campaign.COUNT * 3}",
                        "Ukończone" to "${p.levelsDone}",
                        "Klejnoty" to "${p.gems}",
                        "Upadki" to "${p.falls}",
                        "Czas gry" to "${h} h ${m} min",
                        "Dystans" to "${p.distance.roundToInt()}",
                        "Bez końca" to "etap ${p.endlessBest}",
                        "Mrok" to "etap ${p.darkBest}",
                        "Wyścig" to "${p.timeAttackBest}",
                        "Seria dnia" to "${p.dailyStreak} dni",
                    ),
                )
            }
            Panel(title = "Osiągnięcia · ${p.achievements.size}/${Achievements.all.size}") {
                Achievements.all.forEach { a ->
                    val on = a.id in p.achievements
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (on) accent else Palette.PanelHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (on) "🏆" else "·", color = if (on) Color.Black else Palette.TextFaint) }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(a.title, color = if (on) Color.White else Palette.TextDim, fontWeight = FontWeight.SemiBold)
                            Text(a.desc, color = Palette.TextFaint, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton("Wyzeruj postęp", Modifier.fillMaxWidth()) { confirm = true }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.PanelHigh,
            title = { Text("Wyzerować postęp?") },
            text = { Text("Gwiazdki, rekordy, statystyki i osiągnięcia zostaną usunięte. Ustawienia zostają.") },
            confirmButton = { TextButton({ Store.resetProgress(); confirm = false }) { Text("Wyzeruj", color = Palette.Danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Anuluj", color = Color.White) } },
        )
    }
}

@Composable
private fun Grid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (k, v) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Palette.PanelHigh).padding(14.dp)) {
                    Text(v, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(k, color = Palette.TextDim, fontSize = 13.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by Store.settings.collectAsState()
    val tilt = rememberTilt()
    Screen("Ustawienia", onBack) {
        item {
            Panel(title = "Sterowanie") {
                ChoiceRow("Sposób", listOf("Pochylanie", "Palec"), s.control, { it }) { v -> Store.update { it.copy(control = v) } }
                SliderRow("Czułość", s.sensitivity, 0.4f..2.2f, "×%.1f".format(s.sensitivity)) { v -> Store.update { it.copy(sensitivity = v) } }
                if (tilt.available) {
                    ActionRow(null, "Kalibruj", "Połóż telefon tak, jak chcesz grać, i dotknij") {
                        Store.update { it.copy(calX = tilt.rawX, calY = tilt.rawY) }
                    }
                    if (s.calX != 0f || s.calY != 0f) ActionRow(null, "Przywróć poziom fabryczny", "Płasko = poziom") {
                        Store.update { it.copy(calX = 0f, calY = 0f) }
                    }
                } else {
                    Text("Telefon nie ma czujnika ruchu – gra używa sterowania palcem.", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }
            Panel(title = "Efekt 3D") {
                SliderRow("Wysokość ścian", s.depth, 0f..2.5f, if (s.depth < 0.05f) "płasko" else "×%.1f".format(s.depth)) { v -> Store.update { it.copy(depth = v) } }
                SliderRow("Ruch ścian przy pochylaniu", s.parallax, 0f..2f, if (s.parallax < 0.05f) "wyłączony" else "×%.1f".format(s.parallax)) { v -> Store.update { it.copy(parallax = v) } }
                SwitchRow("Odwróć kierunek przechyłu ścian", "Ściany pochylają się w drugą stronę", s.invertParallax) { v -> Store.update { it.copy(invertParallax = v) } }
                SliderRow("Przybliżenie", s.zoom, 0.6f..1.6f, "×%.1f".format(s.zoom)) { v -> Store.update { it.copy(zoom = v) } }
            }
            Panel(title = "Wygląd") {
                Text("Kolor akcentu", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accents.forEachIndexed { i, a ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(a.color)
                                .border(if (i == s.accent) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { Store.update { it.copy(accent = i) } },
                            contentAlignment = Alignment.Center,
                        ) { if (i == s.accent) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Black)) }
                    }
                }
                Text("Kulka · ${Skins.getOrElse(s.skin) { Skins[0] }.name}", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skins.forEachIndexed { i, sk ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .border(if (i == s.skin) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { Store.update { it.copy(skin = i) } }.padding(4.dp),
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val r = size.minDimension / 2f
                                sk.glow?.let { drawCircle(it.copy(alpha = 0.35f), r) }
                                drawCircle(
                                    Brush.radialGradient(
                                        0f to Color.White, 0.25f to sk.base, 1f to sk.shade,
                                        center = Offset(center.x - r * 0.35f, center.y - r * 0.4f), radius = r * 1.5f,
                                    ),
                                    r * 0.82f,
                                )
                            }
                        }
                    }
                }
                SwitchRow("Ślad kulki", null, s.trail) { v -> Store.update { it.copy(trail = v) } }
                SwitchRow("Kropki na podłodze", null, s.floorGrid) { v -> Store.update { it.copy(floorGrid = v) } }
                SwitchRow("Stoper w trakcie gry", null, s.showTimer) { v -> Store.update { it.copy(showTimer = v) } }
            }
            Panel(title = "Dźwięk i wibracje") {
                SwitchRow("Dźwięki", null, s.sound) { v -> Store.update { it.copy(sound = v) } }
                SwitchRow("Wibracje", "Uderzenia, klejnoty, upadki", s.haptics) { v -> Store.update { it.copy(haptics = v) } }
            }
            Panel(title = "Aktualizacje") {
                SwitchRow("Sprawdzaj automatycznie", "Przy starcie aplikacji", s.autoUpdate) { v -> Store.update { it.copy(autoUpdate = v) } }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val update = rememberUpdateState()
    Screen("O aplikacji", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Open3Dmaze", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("Wersja ${BuildConfig.VERSION_NAME}", color = Palette.TextDim)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Labirynt jest płaski, ale ściany żyją: pochylasz telefon, kulka się toczy, a ściany przechylają się i wyrastają w Twoją stronę.",
                    color = Palette.TextDim, textAlign = TextAlign.Center, fontSize = 14.sp,
                )
            }
            Panel(title = "Jak grać") {
                listOf(
                    "Pochylaj telefon – kulka toczy się w dół.",
                    "Dotrzyj do pulsującej mety ze słupem światła.",
                    "Klejnoty dają drugą gwiazdkę, czas poniżej „par” – trzecią.",
                    "Czarne dziury cofają do punktu kontrolnego.",
                    "Lód nie hamuje, strzałki przyspieszają, portale przenoszą.",
                    "Jeśli „poziom” nie jest poziomy – skalibruj w pauzie albo ustawieniach.",
                ).forEach { Text("•  $it", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) }
            }
            Panel(title = "Aktualizacje") {
                update.Banner()
                ActionRow(
                    null,
                    if (update.checking) "Sprawdzanie…" else "Sprawdź aktualizacje",
                    if (update.checked && update.update == null) "Masz najnowszą wersję" else "Pobierane prosto z redzikowski.com",
                ) { update.checkNow() }
            }
            Spacer(Modifier.height(20.dp))
            Credits()
        }
    }
}
