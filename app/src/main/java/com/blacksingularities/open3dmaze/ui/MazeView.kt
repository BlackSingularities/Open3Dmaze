package com.blacksingularities.open3dmaze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.TiltSensor
import com.blacksingularities.open3dmaze.render.MazeRenderer
import kotlinx.coroutines.isActive

/** Czujnik pochylenia związany z cyklem życia ekranu (wyłącza się w tle). */
@Composable
fun rememberTilt(): TiltSensor {
    val ctx = LocalContext.current
    val tilt = remember { TiltSensor(ctx.applicationContext) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> tilt.start()
                Lifecycle.Event.ON_PAUSE -> tilt.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tilt.start()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            tilt.stop()
        }
    }
    return tilt
}

private class Clock {
    var time = 0f
    var dt = 0f
    var tx = 0f
    var ty = 0f
    // Sterowanie palcem: wektor od miejsca dotknięcia.
    var touchX = 0f
    var touchY = 0f
}

/**
 * Plansza z pętlą gry. [paused] zatrzymuje fizykę, ale ściany dalej reagują na pochylenie.
 * [onStep] dostaje czas kroku po każdej klatce fizyki.
 */
@Composable
fun MazeView(
    game: Game,
    tilt: TiltSensor,
    paused: Boolean,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
    hud: Boolean = true,
    overview: Boolean = false,
    onStep: (Float) -> Unit = {},
) {
    val settings by Store.settings.collectAsState()
    val accent = Accents.getOrElse(settings.accent) { Accents[0] }.color
    val skin = Skins.getOrElse(settings.skin) { Skins[0] }
    val renderer = remember(game.level) { MazeRenderer() }
    val clock = remember { Clock() }
    var frame by remember { mutableLongStateOf(0L) }
    val step by rememberUpdatedState(onStep)
    val s by rememberUpdatedState(settings)
    val isPaused by rememberUpdatedState(paused)

    LaunchedEffect(game) {
        var last = 0L
        while (isActive) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                last = t
                val touch = s.control == 1 || !tilt.available
                val ix: Float
                val iy: Float
                if (touch) {
                    ix = clock.touchX; iy = clock.touchY
                } else {
                    ix = (tilt.rawX - s.calX).coerceIn(-1f, 1f)
                    iy = (tilt.rawY - s.calY).coerceIn(-1f, 1f)
                }
                clock.tx = ix; clock.ty = iy
                game.sensitivity = s.sensitivity
                if (!isPaused) {
                    game.step(dt, ix, iy)
                    step(dt)
                }
                clock.time += dt
                clock.dt = dt
                frame = t
            }
        }
    }

    val touchMod = Modifier.pointerInput(settings.control) {
        val span = size.width * 0.28f
        var ox = 0f
        var oy = 0f
        detectDragGestures(
            onDragStart = { ox = it.x; oy = it.y },
            onDragEnd = { clock.touchX = 0f; clock.touchY = 0f },
            onDragCancel = { clock.touchX = 0f; clock.touchY = 0f },
        ) { change, _ ->
            clock.touchX = ((change.position.x - ox) / span).coerceIn(-1f, 1f)
            clock.touchY = ((change.position.y - oy) / span).coerceIn(-1f, 1f)
        }
    }

    Canvas(modifier.then(if (settings.control == 1 || !tilt.available) touchMod else Modifier)) {
        frame
        renderer.draw(this, game, settings, clock.tx, clock.ty, clock.time, clock.dt, accent, skin, dim, hud, overview)
    }
}
