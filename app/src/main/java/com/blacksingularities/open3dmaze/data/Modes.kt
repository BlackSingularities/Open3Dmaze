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
        // Pierwsze poziomy mieszczą się na ekranie, dalsze są wielokrotnie większe – wtedy prowadzi kamera.
        return when (w) {
            0 -> LevelSpec(seed, lerp(4, 9), lerp(6, 14), braid = 0.05f, holes = lerp(0, 3), gems = lerp(3, 4))
            1 -> LevelSpec(seed, lerp(8, 14), lerp(12, 21), braid = 0.2f, holes = lerp(6, 22), gems = lerp(4, 6), checkpoints = lerp(1, 2))
            2 -> LevelSpec(seed, lerp(11, 18), lerp(16, 27), braid = 0.2f, holes = lerp(8, 24), gems = 6, ice = 0.2f + 0.25f * f, checkpoints = lerp(1, 3))
            3 -> LevelSpec(seed, lerp(14, 24), lerp(21, 36), braid = 0.25f, holes = lerp(12, 34), gems = lerp(6, 8), ice = 0.12f, portals = lerp(1, 4), checkpoints = lerp(2, 4), boosts = lerp(3, 10))
            else -> LevelSpec(seed, lerp(18, 30), lerp(27, 46), braid = 0.25f, holes = lerp(16, 44), gems = 8, ice = 0.15f, portals = lerp(2, 4), checkpoints = lerp(3, 5), boosts = lerp(4, 10), dark = true)
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
            LevelSpec(seed * 31L, 16, 24, braid = 0.25f, holes = 18, gems = 6, ice = 0.15f, portals = 2, checkpoints = 2, boosts = 5)
        }
        Mode.ENDLESS -> {
            val s = stage
            LevelSpec(runSeed + s * 104729L, 5 + minOf(s * 2, 26), 7 + minOf(s * 3, 40),
                braid = 0.2f, holes = minOf(s * 3, 40), gems = 3 + minOf(s / 2, 5), ice = if (s > 3) 0.15f else 0f,
                portals = minOf(s / 4, 3), checkpoints = minOf(s / 3, 4), boosts = minOf(s / 2, 8))
        }
        Mode.DARK -> {
            val s = stage
            LevelSpec(runSeed + s * 15485863L, 6 + minOf(s * 2, 18), 9 + minOf(s * 3, 27),
                braid = 0.25f, holes = minOf(2 + s * 3, 30), gems = 4, checkpoints = 1 + minOf(s / 3, 3), dark = true)
        }
        Mode.TIME_ATTACK -> LevelSpec(runSeed + stage * 7727L, 5 + minOf(stage / 2, 4), 7 + minOf(stage / 2, 6),
            braid = 0.35f, holes = minOf(stage, 8), gems = 6, boosts = 2)
        Mode.ZEN -> LevelSpec(runSeed + stage * 31337L, 10 + minOf(stage * 3, 20), 15 + minOf(stage * 4, 30), braid = 0.4f, gems = 8)
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
