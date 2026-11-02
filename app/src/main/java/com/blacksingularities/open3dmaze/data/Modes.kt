package com.blacksingularities.open3dmaze.data

import com.blacksingularities.open3dmaze.game.LevelSpec
import java.time.LocalDate
import kotlin.math.roundToInt

object Campaign {
    const val WORLDS = 5
    const val PER_WORLD = 12
    const val COUNT = WORLDS * PER_WORLD

    data class World(val name: String, val about: String)

    val worlds = listOf(
        World("Obsydian", "Pierwsze kroki: korytarze, klejnoty i meta."),
        World("Otchłań", "Dziury w podłodze. Jeden fałszywy ruch i wracasz."),
        World("Szron", "Lód nie hamuje. Planuj zakręty z wyprzedzeniem."),
        World("Pryzmat", "Portale, przyspieszacze i punkty kontrolne."),
        World("Zaćmienie", "Wszystko naraz – i prawie po ciemku."),
    )

    fun spec(i: Int): LevelSpec {
        val w = i / PER_WORLD
        val l = i % PER_WORLD
        val f = l / (PER_WORLD - 1f)
        val seed = 0x3D3D_0000L + i * 7919L
        fun lerp(a: Int, b: Int) = (a + (b - a) * f).roundToInt()
        return when (w) {
            0 -> LevelSpec(seed, lerp(4, 8), lerp(6, 12), braid = 0.05f, holes = lerp(0, 2), gems = 3)
            1 -> LevelSpec(seed, lerp(6, 9), lerp(9, 14), braid = 0.25f, holes = lerp(4, 14), gems = 4, checkpoints = if (l > 5) 1 else 0)
            2 -> LevelSpec(seed, lerp(6, 10), lerp(9, 15), braid = 0.2f, holes = lerp(2, 8), gems = 4, ice = 0.25f + 0.3f * f, checkpoints = 1)
            3 -> LevelSpec(seed, lerp(8, 11), lerp(12, 17), braid = 0.3f, holes = lerp(4, 12), gems = 5, ice = 0.12f, portals = lerp(1, 3), checkpoints = 2, boosts = lerp(2, 6))
            else -> LevelSpec(seed, lerp(8, 12), lerp(12, 18), braid = 0.3f, holes = lerp(6, 16), gems = 5, ice = 0.15f, portals = lerp(1, 3), checkpoints = 2, boosts = lerp(2, 5), dark = true)
        }
    }

    fun name(i: Int) = "${i / PER_WORLD + 1}-${i % PER_WORLD + 1}"
}

enum class Mode(val title: String, val subtitle: String) {
    CAMPAIGN("Kampania", "60 poziomów w pięciu światach"),
    DAILY("Labirynt dnia", "Codziennie nowy, ten sam dla wszystkich"),
    ENDLESS("Bez końca", "Coraz większe labirynty, trzy życia"),
    TIME_ATTACK("Wyścig z czasem", "Klejnoty i meta dokładają sekundy"),
    DARK("Mrok", "Widzisz tylko to, co oświetla kulka"),
    CUSTOM("Własny labirynt", "Sam ustawiasz rozmiar i pułapki"),
    ZEN("Zen", "Bez pułapek, bez czasu, bez końca"),
}

/** Stan rozgrywki ponad pojedynczym poziomem. */
class Session(val mode: Mode, var index: Int = 0, val custom: LevelSpec? = null) {
    var stage = 0
    var lives = 3
    var timeLeft = 45f
    private val runSeed = System.nanoTime()

    val usesLives get() = mode == Mode.ENDLESS || mode == Mode.DARK
    val countdown get() = mode == Mode.TIME_ATTACK

    fun spec(): LevelSpec = when (mode) {
        Mode.CAMPAIGN -> Campaign.spec(index)
        Mode.DAILY -> {
            val d = LocalDate.now()
            val seed = d.year * 10000L + d.monthValue * 100L + d.dayOfMonth
            LevelSpec(seed * 31L, 10, 15, braid = 0.25f, holes = 9, gems = 5, ice = 0.15f, portals = 1, checkpoints = 1, boosts = 3)
        }
        Mode.ENDLESS -> {
            val s = stage
            LevelSpec(runSeed + s * 104729L, 4 + minOf(s, 10), 6 + minOf((s * 1.5f).roundToInt(), 14),
                braid = 0.2f, holes = minOf(s * 2, 18), gems = 3, ice = if (s > 3) 0.15f else 0f,
                portals = if (s > 5) 1 else 0, checkpoints = if (s > 4) 1 else 0, boosts = minOf(s / 2, 4))
        }
        Mode.DARK -> {
            val s = stage
            LevelSpec(runSeed + s * 15485863L, 5 + minOf(s, 7), 8 + minOf(s, 9),
                braid = 0.25f, holes = minOf(2 + s * 2, 14), gems = 3, checkpoints = 1, dark = true)
        }
        Mode.TIME_ATTACK -> LevelSpec(runSeed + stage * 7727L, 5 + minOf(stage / 2, 4), 7 + minOf(stage / 2, 6),
            braid = 0.35f, holes = minOf(stage, 8), gems = 6, boosts = 2)
        Mode.ZEN -> LevelSpec(runSeed + stage * 31337L, 7 + minOf(stage, 5), 10 + minOf(stage, 8), braid = 0.4f, gems = 6)
        Mode.CUSTOM -> (custom ?: LevelSpec(1, 8, 12)).let { if (stage == 0) it else it.copy(seed = it.seed + stage) }
    }

    fun title(): String = when (mode) {
        Mode.CAMPAIGN -> "Poziom ${Campaign.name(index)}"
        Mode.DAILY -> "Labirynt dnia"
        Mode.CUSTOM -> "Własny #${stage + 1}"
        else -> "${mode.title} · ${stage + 1}"
    }

    /** Klucz najlepszego czasu (tylko tryby z powtarzalnym labiryntem). */
    fun bestKey(): String? = when (mode) {
        Mode.CAMPAIGN -> "c$index"
        Mode.DAILY -> "d" + LocalDate.now().toString()
        else -> null
    }
}

data class Achievement(val id: String, val title: String, val desc: String, val check: (Progress) -> Boolean)

object Achievements {
    val all = listOf(
        Achievement("first", "Pierwszy krok", "Ukończ dowolny labirynt") { it.levelsDone >= 1 },
        Achievement("ten", "Wprawna ręka", "Ukończ 10 labiryntów") { it.levelsDone >= 10 },
        Achievement("hundred", "Mistrz labiryntu", "Ukończ 100 labiryntów") { it.levelsDone >= 100 },
        Achievement("w1", "Obsydian", "Ukończ pierwszy świat") { p -> (0 until 12).all { p.stars[it] > 0 } },
        Achievement("w5", "Po zaćmieniu", "Ukończ całą kampanię") { p -> p.stars.all { it > 0 } },
        Achievement("stars90", "Zbieracz gwiazd", "Zdobądź 90 gwiazdek") { it.totalStars >= 90 },
        Achievement("stars180", "Perfekcjonista", "Wszystkie 180 gwiazdek") { it.totalStars >= 180 },
        Achievement("gems50", "Jubiler", "Zbierz 50 klejnotów") { it.gems >= 50 },
        Achievement("gems500", "Skarbiec", "Zbierz 500 klejnotów") { it.gems >= 500 },
        Achievement("km1", "Kilometr", "Przetocz kulkę o 1000 jednostek") { it.distance >= 1000f },
        Achievement("falls25", "Grawitacja wygrywa", "Wpadnij do dziury 25 razy") { it.falls >= 25 },
        Achievement("endless10", "Maratończyk", "Dotrzyj do etapu 10 w trybie Bez końca") { it.endlessBest >= 10 },
        Achievement("dark5", "Nocny marek", "Przejdź 5 labiryntów w trybie Mrok") { it.darkBest >= 5 },
        Achievement("ta10", "Sprinter", "Przejdź 10 labiryntów w Wyścigu z czasem") { it.timeAttackBest >= 10 },
        Achievement("streak7", "Tydzień z rzędu", "Labirynt dnia przez 7 dni z rzędu") { it.dailyStreak >= 7 },
        Achievement("hour", "Godzina w labiryncie", "Graj łącznie godzinę") { it.playMs >= 3_600_000L },
    )
}
