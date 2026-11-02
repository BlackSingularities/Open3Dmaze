package com.blacksingularities.open3dmaze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.blacksingularities.open3dmaze.data.Campaign
import com.blacksingularities.open3dmaze.data.Mode
import com.blacksingularities.open3dmaze.data.Session
import com.blacksingularities.open3dmaze.data.Store

sealed interface Route {
    data object Home : Route
    data object CampaignMap : Route
    data object Custom : Route
    data object Stats : Route
    data object Settings : Route
    data object About : Route
    /** [id] odróżnia kolejne rozgrywki, żeby ekran gry zaczynał od zera. */
    data class Play(val session: Session, val id: Long = System.nanoTime()) : Route
}

@Composable
fun App() {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(r: Route) { stack[stack.lastIndex] = r }

    BackHandler(enabled = stack.size > 1 && stack.last() !is Route.Play) { pop() }

    AnimatedContent(stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { route ->
        when (route) {
            Route.Home -> HomeScreen(nav = { push(it) }) { mode ->
                val session = if (mode == Mode.CAMPAIGN) {
                    val p = Store.progress.value
                    Session(mode, (0 until Campaign.COUNT).firstOrNull { p.stars[it] == 0 } ?: (Campaign.COUNT - 1))
                } else Session(mode)
                push(Route.Play(session))
            }
            Route.CampaignMap -> CampaignScreen(onBack = { pop() }) { push(Route.Play(Session(Mode.CAMPAIGN, it))) }
            Route.Custom -> CustomScreen(onBack = { pop() }) { push(Route.Play(Session(Mode.CUSTOM, custom = it))) }
            Route.Stats -> StatsScreen { pop() }
            Route.Settings -> SettingsScreen { pop() }
            Route.About -> AboutScreen { pop() }
            is Route.Play -> GameScreen(
                session = route.session,
                onExit = { pop() },
                onNextCampaign = { replaceTop(Route.Play(Session(Mode.CAMPAIGN, it))) },
            )
        }
    }
}
