package com.blacksingularities.open3dmaze.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

data class Settings(
    val sensitivity: Float = 1f,
    /** 0 = pochylanie telefonu, 1 = przeciąganie palcem. */
    val control: Int = 0,
    val depth: Float = 1f,
    val parallax: Float = 1f,
    val zoom: Float = 1f,
    val accent: Int = 0,
    val skin: Int = 0,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val trail: Boolean = true,
    val invertParallax: Boolean = false,
    val showTimer: Boolean = true,
    val calX: Float = 0f,
    val calY: Float = 0f,
    val autoUpdate: Boolean = true,
    val floorGrid: Boolean = true,
)

data class Progress(
    val stars: List<Int> = List(Campaign.COUNT) { 0 },
    val best: Map<String, Long> = emptyMap(),
    val endlessBest: Int = 0,
    val darkBest: Int = 0,
    val timeAttackBest: Int = 0,
    val playMs: Long = 0,
    val levelsDone: Int = 0,
    val gems: Int = 0,
    val falls: Int = 0,
    val distance: Float = 0f,
    val dailyDone: String = "",
    val dailyStreak: Int = 0,
    val achievements: Set<String> = emptySet(),
) {
    val totalStars get() = stars.sum()
    /** Odblokowany jest pierwszy poziom i każdy po ukończonym. */
    fun unlocked(i: Int) = i == 0 || stars.getOrElse(i - 1) { 0 } > 0
}

object Store {
    private lateinit var p: SharedPreferences
    val settings = MutableStateFlow(Settings())
    val progress = MutableStateFlow(Progress())

    fun init(context: Context) {
        if (::p.isInitialized) return
        p = context.getSharedPreferences("open3dmaze", Context.MODE_PRIVATE)
        settings.value = Settings(
            sensitivity = p.getFloat("sens", 1f),
            control = p.getInt("control", 0),
            depth = p.getFloat("depth", 1f),
            parallax = p.getFloat("parallax", 1f),
            zoom = p.getFloat("zoom", 1f),
            accent = p.getInt("accent", 0),
            skin = p.getInt("skin", 0),
            haptics = p.getBoolean("haptics", true),
            sound = p.getBoolean("sound", true),
            trail = p.getBoolean("trail", true),
            invertParallax = p.getBoolean("invPar", false),
            showTimer = p.getBoolean("timer", true),
            calX = p.getFloat("calX", 0f),
            calY = p.getFloat("calY", 0f),
            autoUpdate = p.getBoolean("autoUpd", true),
            floorGrid = p.getBoolean("grid", true),
        )
        val starStr = p.getString("stars", "") ?: ""
        val stars = starStr.split(',').mapNotNull { it.toIntOrNull() }
        val best = HashMap<String, Long>()
        runCatching {
            val j = JSONObject(p.getString("best", "{}") ?: "{}")
            for (k in j.keys()) best[k] = j.getLong(k)
        }
        progress.value = Progress(
            stars = List(Campaign.COUNT) { stars.getOrElse(it) { 0 } },
            best = best,
            endlessBest = p.getInt("endless", 0),
            darkBest = p.getInt("dark", 0),
            timeAttackBest = p.getInt("ta", 0),
            playMs = p.getLong("playMs", 0),
            levelsDone = p.getInt("done", 0),
            gems = p.getInt("gems", 0),
            falls = p.getInt("falls", 0),
            distance = p.getFloat("dist", 0f),
            dailyDone = p.getString("daily", "") ?: "",
            dailyStreak = p.getInt("streak", 0),
            achievements = p.getStringSet("ach", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun update(f: (Settings) -> Settings) {
        val s = f(settings.value)
        settings.value = s
        p.edit()
            .putFloat("sens", s.sensitivity).putInt("control", s.control)
            .putFloat("depth", s.depth).putFloat("parallax", s.parallax).putFloat("zoom", s.zoom)
            .putInt("accent", s.accent).putInt("skin", s.skin)
            .putBoolean("haptics", s.haptics).putBoolean("sound", s.sound).putBoolean("trail", s.trail)
            .putBoolean("invPar", s.invertParallax).putBoolean("timer", s.showTimer)
            .putFloat("calX", s.calX).putFloat("calY", s.calY)
            .putBoolean("autoUpd", s.autoUpdate).putBoolean("grid", s.floorGrid)
            .apply()
    }

    /** Zapisuje postęp i zwraca nowo zdobyte osiągnięcia. */
    fun record(f: (Progress) -> Progress): List<Achievement> {
        val old = progress.value
        var n = f(old)
        val fresh = Achievements.all.filter { it.id !in n.achievements && it.check(n) }
        if (fresh.isNotEmpty()) n = n.copy(achievements = n.achievements + fresh.map { it.id })
        progress.value = n
        val best = JSONObject()
        n.best.forEach { (k, v) -> best.put(k, v) }
        p.edit()
            .putString("stars", n.stars.joinToString(","))
            .putString("best", best.toString())
            .putInt("endless", n.endlessBest).putInt("dark", n.darkBest).putInt("ta", n.timeAttackBest)
            .putLong("playMs", n.playMs).putInt("done", n.levelsDone).putInt("gems", n.gems)
            .putInt("falls", n.falls).putFloat("dist", n.distance)
            .putString("daily", n.dailyDone).putInt("streak", n.dailyStreak)
            .putStringSet("ach", n.achievements)
            .apply()
        return fresh
    }

    fun resetProgress() {
        record { Progress() }
        p.edit().putStringSet("ach", emptySet()).apply()
        progress.value = Progress()
    }
}
