package com.blacksingularities.open3dmaze.game

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/** Skala świata: szerokość korytarza = 2,5 cm. */
const val UNIT_M = 0.025f

/** Przyspieszenie ziemskie w jednostkach świata. */
const val G = 9.81f / UNIT_M

const val HOLE_R = 0.42f

/** Odstęp próbek „ducha” w sekundach. */
const val GHOST_DT = 0.1f
private const val MU_ROLL = 0.010f
private const val MU_ICE = 0.0015f
private const val AIR = 1.2f
private const val RESTITUTION = 0.45f
private const val MAX_SPEED = 60f

enum class Phase { RUNNING, FALLING, WON }

enum class Ev { GEM, FALL, WIN, PORTAL, CHECK, BOOST, RESPAWN }

/**
 * Fizyka kulki na pochylonej planszy. Przyspieszenie przychodzi z czujnika jako
 * składowe grawitacji w jednostkach g (−1…1).
 */
class Game(val level: Level) {
    var x = level.cx(level.start)
    var y = level.cy(level.start)
    var vx = 0f
    var vy = 0f
    var phase = Phase.RUNNING
    var time = 0f
    var falls = 0
    var distance = 0f
    /** Obrót kulki (do rysowania tekstury), w radianach na osiach X/Y. */
    var rollX = 0f
    var rollY = 0f

    /**
     * Orientacja kulki (macierz obrotu 3×3, wierszami). Osie: x w prawo, y w dół ekranu,
     * z w głąb ekranu – punkt z = −1 patrzy na gracza.
     */
    val rot = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val tmp = FloatArray(9)
    private var renorm = 0

    /** Zapis przejazdu do „ducha”: pozycje co [GHOST_DT] sekundy. */
    val ghostX = ArrayList<Float>()
    val ghostY = ArrayList<Float>()
    private var ghostAcc = 0f

    /** Najlepszy dotychczasowy przejazd (x0, y0, x1, y1, …) do wyświetlenia jako „duch”. */
    var ghost: FloatArray? = null
    val collected = BooleanArray(level.gems.size)
    val gemsTaken get() = collected.count { it }
    val speed get() = hypot(vx, vy)
    val onIce get() = level.at(Geo.index(x), Geo.index(y)) == T.ICE

    private var checkX = x
    private var checkY = y
    var checkpoint = -1
        private set
    var fallT = 0f
        private set
    private var fallFromX = 0f
    private var fallFromY = 0f
    var holeX = 0f
        private set
    var holeY = 0f
        private set
    private var portalLock = -1
    /** Najsilniejsze uderzenie w ostatnim kroku (prędkość normalna). */
    var impact = 0f
        private set
    var winT = 0f
        private set

    val events = ArrayList<Ev>()

    /** Ślad kulki – pierścień ostatnich pozycji. */
    val trailX = FloatArray(48)
    val trailY = FloatArray(48)
    var trailHead = 0
        private set
    var trailCount = 0
        private set
    private var trailAcc = 0f

    var sensitivity = 1f

    fun step(dtIn: Float, gx: Float, gy: Float) {
        val dt = dtIn.coerceIn(0f, 0.034f)
        impact = 0f
        when (phase) {
            Phase.FALLING -> {
                // Czas przejazdu biegnie dalej, więc „duch” też dostaje próbki.
                time += dt
                ghostAcc += dt
                while (ghostAcc >= GHOST_DT) { ghostAcc -= GHOST_DT; ghostX += x; ghostY += y }
                fallT += dt
                val k = (fallT / 0.25f).coerceAtMost(1f)
                x = fallFromX + (holeX - fallFromX) * k
                y = fallFromY + (holeY - fallFromY) * k
                if (fallT > 0.75f) respawn()
                return
            }
            Phase.WON -> {
                winT += dt
                x += (level.cx(level.goal) - x) * (1f - exp(-8f * dt))
                y += (level.cy(level.goal) - y) * (1f - exp(-8f * dt))
                return
            }
            Phase.RUNNING -> Unit
        }
        time += dt

        val ti = Geo.index(x)
        val tj = Geo.index(y)
        val tile = level.at(ti, tj)
        val ice = tile == T.ICE
        // Toczenie bez poślizgu: a = 5/7·g·sinθ (pełna kula). Na lodzie kulka ślizga się: a = g·sinθ.
        val k = (if (ice) 1f else 5f / 7f) * G * sensitivity
        var ax = gx * k
        var ay = gy * k

        if (tile == T.HOLE) {
            val hx = Geo.center(ti); val hy = Geo.center(tj)
            val ddx = hx - x; val ddy = hy - y
            val d = hypot(ddx, ddy)
            if (d < HOLE_R - BALL_R * 0.45f) {
                startFall(hx, hy); return
            }
            // Środek kulki nad krawędzią – kulka przechyla się i zsuwa do środka.
            if (d < HOLE_R && d > 1e-4f) {
                val pull = G * 0.55f * (1f - d / HOLE_R + 0.3f)
                ax += ddx / d * pull; ay += ddy / d * pull
            }
        }
        if (tile == T.BOOST) {
            val b = G * 0.5f
            when (level.boostDir[tj * level.gw + ti]) {
                0 -> ax += b; 1 -> ay += b; 2 -> ax -= b; 3 -> ay -= b
            }
        }

        vx += ax * dt
        vy += ay * dt
        // Opór toczenia: stała siła przeciwna ruchowi. Przy małym pochyleniu kulka stoi.
        val rr = (if (ice) MU_ICE else MU_ROLL) * G * dt + if (ice) 0f else AIR * dt
        val sp0 = hypot(vx, vy)
        if (sp0 <= rr) { vx = 0f; vy = 0f } else { val f = (sp0 - rr) / sp0; vx *= f; vy *= f }
        val sp = hypot(vx, vy)
        if (sp > MAX_SPEED) { vx *= MAX_SPEED / sp; vy *= MAX_SPEED / sp }

        val n = ceil(sp * dt / 0.06f).toInt().coerceIn(1, 40)
        val h = dt / n
        val ox = x; val oy = y
        repeat(n) {
            x += vx * h
            y += vy * h
            collide()
        }
        val moved = hypot(x - ox, y - oy)
        distance += moved
        rollX += (x - ox) / BALL_R
        rollY += (y - oy) / BALL_R
        roll(x - ox, y - oy)

        ghostAcc += dt
        while (ghostAcc >= GHOST_DT) {
            ghostAcc -= GHOST_DT
            ghostX += x; ghostY += y
        }

        trailAcc += moved
        if (trailAcc > 0.12f) {
            trailAcc = 0f
            trailHead = (trailHead + 1) % trailX.size
            trailX[trailHead] = x; trailY[trailHead] = y
            if (trailCount < trailX.size) trailCount++
        }

        interact()
    }

    private fun collide() {
        val i0 = Geo.index(x - BALL_R); val i1 = Geo.index(x + BALL_R)
        val j0 = Geo.index(y - BALL_R); val j1 = Geo.index(y + BALL_R)
        for (j in j0..j1) for (i in i0..i1) {
            if (level.at(i, j) != T.WALL) continue
            val rx0 = Geo.start(i); val rx1 = rx0 + Geo.size(i)
            val ry0 = Geo.start(j); val ry1 = ry0 + Geo.size(j)
            val cx = x.coerceIn(rx0, rx1)
            val cy = y.coerceIn(ry0, ry1)
            var nx = x - cx
            var ny = y - cy
            val d2 = nx * nx + ny * ny
            if (d2 >= BALL_R * BALL_R) continue
            val pen: Float
            if (d2 < 1e-10f) {
                // Środek w ścianie (bardzo szybki ruch) – wypychamy najkrótszą drogą.
                val l = x - rx0; val r = rx1 - x; val u = y - ry0; val b = ry1 - y
                val m = minOf(minOf(l, r), minOf(u, b))
                nx = 0f; ny = 0f
                when (m) { l -> nx = -1f; r -> nx = 1f; u -> ny = -1f; else -> ny = 1f }
                pen = m + BALL_R
            } else {
                val d = sqrt(d2)
                nx /= d; ny /= d
                pen = BALL_R - d
            }
            x += nx * pen
            y += ny * pen
            val vn = vx * nx + vy * ny
            if (vn < 0f) {
                // Stal o drewno: część energii ginie, mocne uderzenia odbijają słabiej.
                val e = (RESTITUTION - (-vn) * 0.004f).coerceIn(0.2f, RESTITUTION)
                vx -= (1f + e) * vn * nx
                vy -= (1f + e) * vn * ny
                // Tarcie styczne – kulka uderzona pod kątem traci trochę prędkości wzdłuż ściany.
                val tx = -ny; val ty = nx
                val vt = vx * tx + vy * ty
                val loss = (-vn * 0.12f).coerceAtMost(kotlin.math.abs(vt) * 0.3f)
                val sgn = if (vt > 0f) 1f else -1f
                vx -= sgn * loss * tx; vy -= sgn * loss * ty
                if (-vn > impact) impact = -vn
            }
        }
    }

    private fun interact() {
        val ti = Geo.index(x); val tj = Geo.index(y)
        val idx = tj * level.gw + ti
        val tile = level.at(ti, tj)

        if (portalLock >= 0 && idx != portalLock) portalLock = -1
        if (tile == T.PORTAL && portalLock < 0) {
            val p = level.partnerOf(idx)
            if (p >= 0) {
                x = level.cx(p); y = level.cy(p)
                portalLock = p
                events += Ev.PORTAL
            }
        }
        if (tile == T.CHECK && checkpoint != idx) {
            checkpoint = idx
            checkX = level.cx(idx); checkY = level.cy(idx)
            events += Ev.CHECK
        }
        for (k in level.gems.indices) {
            if (collected[k]) continue
            val g = level.gems[k]
            if (hypot(level.cx(g) - x, level.cy(g) - y) < 0.48f) {
                collected[k] = true
                events += Ev.GEM
            }
        }
        if (hypot(level.cx(level.goal) - x, level.cy(level.goal) - y) < 0.30f) {
            phase = Phase.WON
            vx = 0f; vy = 0f
            events += Ev.WIN
        }
    }

    private fun startFall(hx: Float, hy: Float) {
        phase = Phase.FALLING
        fallT = 0f
        fallFromX = x; fallFromY = y
        holeX = hx; holeY = hy
        vx = 0f; vy = 0f
        falls++
        events += Ev.FALL
    }

    /** Toczenie bez poślizgu: oś obrotu ω = n × v (n – normalna podłogi, w stronę gracza). */
    private fun roll(dx: Float, dy: Float) {
        val d = hypot(dx, dy)
        if (d < 1e-6f) return
        val ax = dy / d; val ay = -dx / d
        val th = d / BALL_R
        val c = kotlin.math.cos(th); val s = kotlin.math.sin(th); val t = 1f - c
        // Rodrigues dla osi (ax, ay, 0).
        val q0 = t * ax * ax + c; val q1 = t * ax * ay; val q2 = s * ay
        val q3 = t * ax * ay; val q4 = t * ay * ay + c; val q5 = -s * ax
        val q6 = -s * ay; val q7 = s * ax; val q8 = c
        for (col in 0..2) {
            val a = rot[col]; val b = rot[3 + col]; val e = rot[6 + col]
            tmp[col] = q0 * a + q1 * b + q2 * e
            tmp[3 + col] = q3 * a + q4 * b + q5 * e
            tmp[6 + col] = q6 * a + q7 * b + q8 * e
        }
        tmp.copyInto(rot)
        if (++renorm > 120) { renorm = 0; orthonormalize() }
    }

    private fun orthonormalize() {
        for (c in 0..2) {
            for (p in 0 until c) {
                val dot = rot[p] * rot[c] + rot[3 + p] * rot[3 + c] + rot[6 + p] * rot[6 + c]
                rot[c] -= dot * rot[p]; rot[3 + c] -= dot * rot[3 + p]; rot[6 + c] -= dot * rot[6 + p]
            }
            val l = sqrt(rot[c] * rot[c] + rot[3 + c] * rot[3 + c] + rot[6 + c] * rot[6 + c])
            rot[c] /= l; rot[3 + c] /= l; rot[6 + c] /= l
        }
    }

    private fun respawn() {
        x = checkX; y = checkY
        vx = 0f; vy = 0f
        phase = Phase.RUNNING
        trailCount = 0
        events += Ev.RESPAWN
    }

    /** Ułamek 0–1 animacji wpadania (1 = kulka zniknęła). */
    val fallProgress get() = if (phase == Phase.FALLING) (fallT / 0.5f).coerceAtMost(1f) else 0f
}
