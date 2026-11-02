package com.blacksingularities.open3dmaze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.open3dmaze.data.Campaign
import com.blacksingularities.open3dmaze.data.Mode
import com.blacksingularities.open3dmaze.data.Session
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.game.Ev
import com.blacksingularities.open3dmaze.game.Fx
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.MazeGen
import com.blacksingularities.open3dmaze.game.Phase
import kotlinx.coroutines.delay
import java.time.LocalDate

private data class Outcome(
    val win: Boolean,
    val time: Float,
    val stars: Int,
    val gems: Int,
    val gemsTotal: Int,
    val best: Float?,
    val newBest: Boolean,
    val par: Int,
    val note: String,
)

@Composable
fun GameScreen(session: Session, onExit: () -> Unit, onNextCampaign: (Int) -> Unit) {
    val ctx = LocalContext.current
    val settings by Store.settings.collectAsState()
    val accent = accentColor()
    val tilt = rememberTilt()
    val fx = remember { Fx(ctx.applicationContext) }
    DisposableEffect(Unit) {
        fx.start()
        onDispose { fx.release() }
    }
    fx.sound = settings.sound
    fx.haptics = settings.haptics

    var gen by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf(Game(MazeGen.generate(session.spec()))) }
    var paused by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var gemsHud by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(session.lives) }
    var timeLeft by remember { mutableFloatStateOf(session.timeLeft) }
    var clock by remember { mutableFloatStateOf(0f) }
    var toast by remember { mutableStateOf<String?>(null) }
    var intro by remember { mutableStateOf(true) }
    var overview by remember { mutableStateOf(settings.flyover && !session.countdown) }
    fun attachGhost(g: Game) {
        val key = session.bestKey()
        g.ghost = if (key != null && Store.settings.value.ghost) Store.loadGhost(key) else null
    }
    remember(game) { attachGhost(game); 0 }
    val flushed = remember { booleanArrayOf(false) }

    fun flush(g: Game, done: Boolean) {
        if (flushed[0]) return
        flushed[0] = true
        val fresh = Store.record {
            it.copy(
                playMs = it.playMs + (g.time * 1000).toLong(),
                levelsDone = it.levelsDone + if (done) 1 else 0,
                gems = it.gems + g.gemsTaken,
                falls = it.falls + g.falls,
                distance = it.distance + g.distance,
            )
        }
        if (fresh.isNotEmpty()) toast = "Osiągnięcie: " + fresh.joinToString { it.title }
    }

    fun newLevel() {
        flushed[0] = false
        game = Game(MazeGen.generate(session.spec()))
        gemsHud = 0
        outcome = null
        paused = false
        intro = true
        overview = Store.settings.value.flyover && !session.countdown
        gen++
    }

    fun endRun(note: String) {
        flush(game, false)
        val reached = session.stage
        val fresh = Store.record {
            when (session.mode) {
                Mode.ENDLESS -> it.copy(endlessBest = maxOf(it.endlessBest, reached))
                Mode.DARK -> it.copy(darkBest = maxOf(it.darkBest, reached))
                Mode.TIME_ATTACK -> it.copy(timeAttackBest = maxOf(it.timeAttackBest, reached))
                else -> it
            }
        }
        if (fresh.isNotEmpty()) toast = "Osiągnięcie: " + fresh.joinToString { it.title }
        outcome = Outcome(false, game.time, 0, game.gemsTaken, game.level.gems.size, null, false, 0, note)
    }

    fun onWin() {
        val g = game
        flush(g, true)
        val all = g.gemsTaken == g.level.gems.size
        val underPar = g.time <= g.level.parSeconds
        val stars = 1 + (if (all) 1 else 0) + (if (underPar) 1 else 0)
        val key = session.bestKey()
        val prev = key?.let { Store.progress.value.best[it] }
        val ms = (g.time * 1000).toLong()
        val newBest = key != null && (prev == null || ms < prev)
        val today = LocalDate.now().toString()
        val fresh = Store.record { p ->
            var n = p
            if (session.mode == Mode.CAMPAIGN) {
                n = n.copy(stars = n.stars.toMutableList().also { s -> s[session.index] = maxOf(s[session.index], stars) })
            }
            if (newBest && key != null) n = n.copy(best = n.best + (key to ms))
            if (session.mode == Mode.DAILY && n.dailyDone != today) {
                val streak = if (n.dailyDone == LocalDate.now().minusDays(1).toString()) n.dailyStreak + 1 else 1
                n = n.copy(dailyDone = today, dailyStreak = streak)
            }
            n
        }
        if (fresh.isNotEmpty()) toast = "Osiągnięcie: " + fresh.joinToString { it.title }
        if (newBest && key != null) Store.saveGhost(key, g.ghostX, g.ghostY)

        when (session.mode) {
            Mode.TIME_ATTACK -> {
                session.stage++
                timeLeft += 8f
                Store.record { it.copy(timeAttackBest = maxOf(it.timeAttackBest, session.stage)) }
            }
            Mode.ENDLESS, Mode.DARK -> {
                session.stage++
                Store.record {
                    if (session.mode == Mode.ENDLESS) it.copy(endlessBest = maxOf(it.endlessBest, session.stage))
                    else it.copy(darkBest = maxOf(it.darkBest, session.stage))
                }
            }
            else -> Unit
        }
        val note = when (session.mode) {
            Mode.CAMPAIGN -> if (stars < 3) buildString {
                if (!all) append("Zbierz wszystkie klejnoty. ")
                if (!underPar) append("Zmieść się w ${g.level.parSeconds} s.")
            }.trim() else "Komplet gwiazdek!"
            Mode.ENDLESS, Mode.DARK -> "Etap ${session.stage} za Tobą · życia: $lives"
            else -> ""
        }
        outcome = Outcome(true, g.time, stars, g.gemsTaken, g.level.gems.size, prev?.let { it / 1000f }, newBest, g.level.parSeconds, note)
    }

    // Wyścig z czasem przechodzi do następnego labiryntu od razu.
    LaunchedEffect(outcome) {
        val o = outcome
        if (o != null && o.win && session.mode == Mode.TIME_ATTACK) {
            delay(900)
            newLevel()
        }
    }
    LaunchedEffect(gen) {
        delay(1600)
        intro = false
        // Przelot: po pokazaniu całości kamera zjeżdża do kulki.
        delay(400)
        overview = false
    }
    LaunchedEffect(paused, outcome, overview) {
        if (paused || outcome != null || overview) fx.rolling(0f, false)
    }
    // W tle (wygaszony ekran, inna aplikacja) gra pauzuje i milknie.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                if (outcome == null) paused = true
                fx.rolling(0f, false)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(3200); toast = null }
    }
    DisposableEffect(Unit) { onDispose { flush(game, false) } }

    BackHandler {
        when {
            overview -> overview = false
            outcome != null || paused -> { flush(game, false); onExit() }
            else -> paused = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MazeView(
            game = game,
            tilt = tilt,
            paused = paused || overview || outcome != null && game.phase != Phase.WON,
            modifier = Modifier.fillMaxSize(),
            overview = overview,
        ) { dt ->
            val g = game
            clock = g.time
            if (g.impact > 0f) fx.bump(g.impact)
            fx.rolling(if (g.phase == Phase.RUNNING) g.speed else 0f, g.onIce)
            if (session.countdown && g.phase == Phase.RUNNING && outcome == null) {
                timeLeft -= dt
                if (timeLeft <= 0f) {
                    timeLeft = 0f
                    fx.fall()
                    endRun("Koniec czasu. Labiryntów: ${session.stage}")
                }
            }
            if (g.events.isEmpty()) return@MazeView
            for (e in g.events) when (e) {
                Ev.GEM -> {
                    fx.gem(); gemsHud = g.gemsTaken
                    if (session.countdown) timeLeft += 3f
                }
                Ev.FALL -> {
                    fx.fall()
                    if (session.usesLives) {
                        lives--
                        session.lives = lives
                        if (lives <= 0) endRun("Koniec żyć. Etap: ${session.stage + 1}")
                    }
                }
                Ev.WIN -> { fx.win(); if (outcome == null) onWin() }
                Ev.PORTAL -> fx.portal()
                Ev.CHECK -> fx.check()
                Ev.BOOST -> fx.boost()
                Ev.RESPAWN -> Unit
            }
            g.events.clear()
        }

        // Pasek u góry.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip {
                Text(session.title(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (game.level.gems.isNotEmpty()) {
                Chip {
                    Icon(Icons.Rounded.Diamond, null, tint = Color(0xFF7CF7FF), modifier = Modifier.size(16.dp))
                    Text(" $gemsHud/${game.level.gems.size}", color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.size(6.dp))
            }
            if (session.usesLives) {
                Chip {
                    Icon(Icons.Rounded.Favorite, null, tint = Palette.Danger, modifier = Modifier.size(16.dp))
                    Text(" $lives", color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.size(6.dp))
            }
            if (session.countdown) {
                Chip { TimeText({ timeLeft }) { if (it < 10f) Palette.Danger else accent } }
            } else if (settings.showTimer) {
                Chip { TimeText({ clock }) { Color.White } }
            }
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (overview) accent else Color(0xB3000000)).border(1.dp, Palette.Line, CircleShape)
                    .clickable { overview = !overview },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Map, "Podgląd labiryntu", tint = if (overview) Color.Black else Color.White) }
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xB3000000)).border(1.dp, Palette.Line, CircleShape)
                    .clickable { paused = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Pause, "Pauza", tint = Color.White) }
        }

        AnimatedVisibility(intro && outcome == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000)).padding(24.dp)) {
                Text(session.title(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val sub = when (session.mode) {
                    Mode.CAMPAIGN -> Campaign.worlds[session.index / Campaign.PER_WORLD].name + " · par ${game.level.parSeconds} s"
                    else -> session.mode.subtitle
                }
                Text(sub, color = Palette.TextDim, fontSize = 15.sp)
            }
        }

        AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("🏆 " + (toast ?: ""), color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }

        if (paused && outcome == null) {
            Overlay {
                Text("Pauza", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(session.title() + " · " + formatTime(game.time), color = Palette.TextDim)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Wznów", Modifier.fillMaxWidth()) { paused = false }
                Spacer(Modifier.height(10.dp))
                GhostButton("Od nowa", Modifier.fillMaxWidth()) {
                    flush(game, false)
                    if (session.usesLives || session.countdown) {
                        session.stage = 0; session.lives = 3; lives = 3; timeLeft = 45f
                    }
                    newLevel()
                }
                Spacer(Modifier.height(10.dp))
                if (tilt.available && settings.control == 0) {
                    GhostButton("Kalibruj: obecne pochylenie = poziom", Modifier.fillMaxWidth()) {
                        Store.update { it.copy(calX = tilt.rawX, calY = tilt.rawY) }
                        paused = false
                    }
                    Spacer(Modifier.height(10.dp))
                }
                SliderRow("Czułość", settings.sensitivity, 0.3f..1.6f, "×%.1f".format(settings.sensitivity)) { v -> Store.update { it.copy(sensitivity = v) } }
                Spacer(Modifier.height(6.dp))
                GhostButton("Wyjdź do menu", Modifier.fillMaxWidth()) { flush(game, false); onExit() }
            }
        }

        val o = outcome
        if (o != null && !(o.win && session.mode == Mode.TIME_ATTACK)) {
            Overlay {
                Text(if (o.win) "Meta!" else "Koniec gry", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                if (o.win && session.mode == Mode.CAMPAIGN) {
                    Row(Modifier.padding(vertical = 8.dp)) {
                        repeat(3) { i ->
                            Text("★", fontSize = 44.sp, color = if (i < o.stars) Palette.Gold else Color(0x33FFFFFF))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                StatLine("Czas", formatTime(o.time) + if (o.win && session.mode == Mode.CAMPAIGN) "  (par ${o.par} s)" else "")
                if (o.gemsTotal > 0) StatLine("Klejnoty", "${o.gems}/${o.gemsTotal}")
                if (o.best != null) StatLine("Rekord", formatTime(o.best))
                if (o.newBest) Text("Nowy rekord!", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                if (o.note.isNotEmpty()) Text(o.note, color = Palette.TextDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(20.dp))
                when {
                    !o.win -> PrimaryButton("Jeszcze raz", Modifier.fillMaxWidth()) {
                        session.stage = 0; session.lives = 3; lives = 3; timeLeft = 45f
                        newLevel()
                    }
                    session.mode == Mode.CAMPAIGN && session.index + 1 < Campaign.COUNT ->
                        PrimaryButton("Następny poziom", Modifier.fillMaxWidth()) { onNextCampaign(session.index + 1) }
                    session.mode == Mode.ENDLESS || session.mode == Mode.DARK || session.mode == Mode.ZEN || session.mode == Mode.CUSTOM ->
                        PrimaryButton("Dalej", Modifier.fillMaxWidth()) {
                            if (session.mode == Mode.ZEN || session.mode == Mode.CUSTOM) session.stage++
                            newLevel()
                        }
                    else -> Unit
                }
                if (o.win) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Powtórz", Modifier.fillMaxWidth()) {
                        if (session.usesLives) session.stage = (session.stage - 1).coerceAtLeast(0)
                        newLevel()
                    }
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("Menu", Modifier.fillMaxWidth()) { onExit() }
            }
        }
    }
}

/** Czyta stan dopiero tutaj, więc co klatkę odświeża się tylko ten napis. */
@Composable
private fun TimeText(read: () -> Float, color: (Float) -> Color) {
    val v = read()
    Text(formatTime(v), color = color(v), fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000)).border(1.dp, Palette.Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xD9000000)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(28.dp))
                .background(Palette.Panel).border(1.dp, Palette.Line, RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}
