package com.blacksingularities.open3dmaze.game

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Grubość ściany względem szerokości korytarza (korytarz = 1). */
const val WALL_T = 0.34f

/** Promień kulki w jednostkach świata. */
const val BALL_R = 0.30f

/** Rodzaje pól siatki. */
object T {
    const val FLOOR: Byte = 0
    const val WALL: Byte = 1
    const val HOLE: Byte = 2
    const val ICE: Byte = 3
    const val CHECK: Byte = 4
    const val PORTAL: Byte = 5
    const val BOOST: Byte = 6
}

/**
 * Siatka labiryntu ma nierówne kolumny: indeksy parzyste to cienkie ściany ([WALL_T]),
 * nieparzyste to korytarze o szerokości 1. Dzięki temu ściany są smukłe, a fizyka dalej
 * działa na prostokątach.
 */
object Geo {
    private const val STEP = 1f + WALL_T
    fun start(i: Int): Float = (i / 2) * STEP + if (i % 2 == 1) WALL_T else 0f
    fun size(i: Int): Float = if (i % 2 == 1) 1f else WALL_T
    fun center(i: Int): Float = start(i) + size(i) / 2f
    fun index(v: Float): Int {
        val k = floor(v / STEP).toInt()
        val r = v - k * STEP
        return 2 * k + if (r < WALL_T) 0 else 1
    }
}

/** Parametry, z których powstaje poziom. Ten sam zestaw zawsze daje ten sam labirynt. */
data class LevelSpec(
    val seed: Long,
    val cw: Int,
    val ch: Int,
    val braid: Float = 0f,
    val holes: Int = 0,
    val gems: Int = 3,
    val ice: Float = 0f,
    val portals: Int = 0,
    val checkpoints: Int = 0,
    val boosts: Int = 0,
    val dark: Boolean = false,
)

class Level(
    val spec: LevelSpec,
    val gw: Int,
    val gh: Int,
    val tiles: ByteArray,
    val start: Int,
    val goal: Int,
    val gems: IntArray,
    /** Pary portali: [a0, b0, a1, b1, …]. */
    val portals: IntArray,
    /** Kierunki pól przyspieszenia: 0 →, 1 ↓, 2 ←, 3 ↑ (indeks pola → kierunek). */
    val boostDir: Map<Int, Int>,
    /** Długość najkrótszej drogi start–meta w jednostkach świata. */
    val pathLength: Float,
) {
    val worldW: Float = Geo.start(gw - 1) + Geo.size(gw - 1)
    val worldH: Float = Geo.start(gh - 1) + Geo.size(gh - 1)

    /** Czas „par” w sekundach – poniżej niego trzecia gwiazdka. */
    val parSeconds: Int = (pathLength / 4.5f + 4f + spec.holes * 0.3f).roundToInt()

    /** Ściany do rysowania – policzone raz. */
    val walls: IntArray = (0 until gw * gh).filter { tiles[it] == T.WALL }.toIntArray()

    fun at(i: Int, j: Int): Byte = if (i < 0 || j < 0 || i >= gw || j >= gh) T.WALL else tiles[j * gw + i]
    fun cx(idx: Int) = Geo.center(idx % gw)
    fun cy(idx: Int) = Geo.center(idx / gw)

    fun partnerOf(idx: Int): Int {
        for (k in portals.indices) if (portals[k] == idx) return portals[k xor 1]
        return -1
    }
}

object MazeGen {

    fun generate(spec: LevelSpec): Level {
        val rnd = Random(spec.seed)
        val cw = spec.cw.coerceIn(2, 48)
        val ch = spec.ch.coerceIn(2, 72)
        val gw = 2 * cw + 1
        val gh = 2 * ch + 1
        val t = ByteArray(gw * gh) { T.WALL }
        fun id(i: Int, j: Int) = j * gw + i

        // „Growing tree”: zwykle rozwija najnowszą gałąź (długie, kręte korytarze), czasem
        // losową (więcej rozwidleń i zwodniczych odnóg niż w czystym backtrackerze).
        val visited = BooleanArray(cw * ch)
        val stack = ArrayList<Int>()
        stack.add(0)
        visited[0] = true
        t[id(1, 1)] = T.FLOOR
        val dx = intArrayOf(1, 0, -1, 0)
        val dy = intArrayOf(0, 1, 0, -1)
        while (stack.isNotEmpty()) {
            val pick = if (rnd.nextFloat() < 0.72f) stack.lastIndex else rnd.nextInt(stack.size)
            val c = stack[pick]
            val x = c % cw
            val y = c / cw
            val dirs = (0..3).filter {
                val nx = x + dx[it]; val ny = y + dy[it]
                nx in 0 until cw && ny in 0 until ch && !visited[ny * cw + nx]
            }
            if (dirs.isEmpty()) {
                stack.removeAt(pick); continue
            }
            val d = dirs[rnd.nextInt(dirs.size)]
            val nx = x + dx[d]; val ny = y + dy[d]
            visited[ny * cw + nx] = true
            t[id(2 * x + 1 + dx[d], 2 * y + 1 + dy[d])] = T.FLOOR
            t[id(2 * nx + 1, 2 * ny + 1)] = T.FLOOR
            stack.add(ny * cw + nx)
        }

        // Pętle: część ślepych zaułków dostaje drugie wyjście.
        if (spec.braid > 0f) {
            for (y in 0 until ch) for (x in 0 until cw) {
                val i = 2 * x + 1; val j = 2 * y + 1
                val open = (0..3).count { t[id(i + dx[it], j + dy[it])] != T.WALL }
                if (open == 1 && rnd.nextFloat() < spec.braid) {
                    val cand = (0..3).filter {
                        val wi = i + dx[it]; val wj = j + dy[it]
                        wi in 1 until gw - 1 && wj in 1 until gh - 1 && t[id(wi, wj)] == T.WALL
                    }
                    if (cand.isNotEmpty()) {
                        val d = cand[rnd.nextInt(cand.size)]
                        t[id(i + dx[d], j + dy[d])] = T.FLOOR
                    }
                }
            }
        }

        val start = id(1, 1)
        // Meta: najdalsza komórka od startu.
        val dist0 = bfs(t, gw, gh, start)
        var goal = start
        var best = -1
        for (y in 0 until ch) for (x in 0 until cw) {
            val c = id(2 * x + 1, 2 * y + 1)
            if (dist0[c] > best) { best = dist0[c]; goal = c }
        }

        val cells = ArrayList<Int>()
        for (y in 0 until ch) for (x in 0 until cw) cells += id(2 * x + 1, 2 * y + 1)
        val reserved = HashSet<Int>().apply { add(start); add(goal) }
        // Okolica startu zostaje spokojna.
        for (c in cells) if (dist0[c] in 0..2) reserved += c

        // Dziury – tylko tak, by meta nadal była osiągalna.
        var holes = 0
        for (c in cells.shuffled(rnd)) {
            if (holes >= spec.holes) break
            if (c in reserved) continue
            t[c] = T.HOLE
            if (bfs(t, gw, gh, start)[goal] < 0) t[c] = T.FLOOR else holes++
        }

        val reach = bfs(t, gw, gh, start)
        val path = pathCells(t, gw, start, goal, reach)

        // Lód – placki rozlewające się po korytarzach.
        if (spec.ice > 0f) {
            val target = (cells.size * spec.ice).roundToInt()
            var placed = 0
            var guard = 0
            while (placed < target && guard++ < 400) {
                var c = cells[rnd.nextInt(cells.size)]
                repeat(3 + rnd.nextInt(5)) {
                    if (t[c] == T.FLOOR && c !in reserved) { t[c] = T.ICE; placed++ }
                    val opts = (0..3).filter { d ->
                        val n = c + dx[d] + dy[d] * gw
                        t[n] != T.WALL && t[n] != T.HOLE
                    }
                    if (opts.isEmpty()) return@repeat
                    val d = opts[rnd.nextInt(opts.size)]
                    val pass = c + dx[d] + dy[d] * gw
                    if (t[pass] == T.FLOOR) t[pass] = T.ICE
                    c = pass + dx[d] + dy[d] * gw
                }
            }
        }

        val free = cells.filter { (t[it] == T.FLOOR || t[it] == T.ICE) && it !in reserved && reach[it] >= 0 }.toMutableList()
        free.shuffle(rnd)

        // Punkty kontrolne na trasie.
        for (k in 1..spec.checkpoints) {
            val c = path.getOrNull(path.size * k / (spec.checkpoints + 1)) ?: continue
            if (c != start && c != goal && t[c] != T.HOLE) {
                t[c] = T.CHECK; free.remove(c); reserved += c
            }
        }

        // Portale – jeden koniec bliżej startu, drugi dalej (skróty).
        val portals = ArrayList<Int>()
        val byDist = free.sortedBy { reach[it] }
        for (k in 0 until spec.portals) {
            if (byDist.size < 8) break
            val a = byDist.filter { it !in portals && t[it] != T.CHECK }.let { l -> l.getOrNull(rnd.nextInt(maxOf(1, l.size / 3))) } ?: break
            val b = byDist.filter { it !in portals && it != a && t[it] != T.CHECK }
                .let { l -> l.getOrNull(l.size - 1 - rnd.nextInt(maxOf(1, l.size / 3))) } ?: break
            t[a] = T.PORTAL; t[b] = T.PORTAL
            portals += a; portals += b
            free.remove(a); free.remove(b)
        }

        // Pola przyspieszenia w prostych odcinkach korytarza.
        val boostDir = HashMap<Int, Int>()
        for (c in free.toList()) {
            if (boostDir.size >= spec.boosts) break
            val i = c % gw; val j = c / gw
            val horiz = t[id(i - 1, j)] != T.WALL && t[id(i + 1, j)] != T.WALL && t[id(i, j - 1)] == T.WALL && t[id(i, j + 1)] == T.WALL
            val vert = t[id(i, j - 1)] != T.WALL && t[id(i, j + 1)] != T.WALL && t[id(i - 1, j)] == T.WALL && t[id(i + 1, j)] == T.WALL
            if (!horiz && !vert || t[c] != T.FLOOR) continue
            // Kierunek zgodny z drogą do mety, jeśli pole leży na trasie.
            val pi = path.indexOf(c)
            val dir = if (pi >= 0 && pi + 1 < path.size) {
                val n = path[pi + 1]
                when {
                    n % gw > i -> 0; n / gw > j -> 1; n % gw < i -> 2; else -> 3
                }
            } else if (horiz) (if (rnd.nextBoolean()) 0 else 2) else (if (rnd.nextBoolean()) 1 else 3)
            t[c] = T.BOOST
            boostDir[c] = dir
            free.remove(c)
        }

        val gems = free.filter { t[it] == T.FLOOR || t[it] == T.ICE }.take(spec.gems).toIntArray()

        // Długość trasy w świecie: krok korytarz→korytarz to 1 + WALL_T.
        val pathLen = max(1, reach[goal]) / 2f * (1f + WALL_T)

        return Level(spec, gw, gh, t, start, goal, gems, portals.toIntArray(), boostDir, pathLen)
    }

    /** BFS po polach przejezdnych (bez ścian i dziur). -1 = nieosiągalne. */
    fun bfs(t: ByteArray, gw: Int, gh: Int, from: Int): IntArray {
        val d = IntArray(t.size) { -1 }
        val q = IntArray(t.size)
        var h = 0; var tl = 0
        d[from] = 0; q[tl++] = from
        while (h < tl) {
            val c = q[h++]
            val i = c % gw; val j = c / gw
            val ns = intArrayOf(if (i + 1 < gw) c + 1 else -1, if (i > 0) c - 1 else -1, if (j + 1 < gh) c + gw else -1, if (j > 0) c - gw else -1)
            for (n in ns) if (n >= 0 && d[n] < 0 && t[n] != T.WALL && t[n] != T.HOLE) {
                d[n] = d[c] + 1; q[tl++] = n
            }
        }
        return d
    }

    /** Komórki korytarzy (nieparzyste) leżące na najkrótszej drodze, od startu do mety. */
    private fun pathCells(t: ByteArray, gw: Int, start: Int, goal: Int, d: IntArray): List<Int> {
        if (d[goal] < 0) return emptyList()
        val out = ArrayList<Int>()
        var c = goal
        while (c != start) {
            if ((c % gw) % 2 == 1 && (c / gw) % 2 == 1) out += c
            val ns = intArrayOf(c + 1, c - 1, c + gw, c - gw)
            c = ns.firstOrNull { it in t.indices && d[it] == d[c] - 1 } ?: break
        }
        out += start
        return out.reversed()
    }
}
