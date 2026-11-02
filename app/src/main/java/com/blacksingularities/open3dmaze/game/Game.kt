package com.blacksingularities.open3dmaze.game

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

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
    val collected = BooleanArray(level.gems.size)
    val gemsTaken get() = collected.count { it }

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
        val accel = 30f * sensitivity
        var ax = gx * accel
        var ay = gy * accel

        // Krawędź dziury lekko wciąga kulkę.
        if (tile == T.HOLE) {
            val hx = Geo.center(ti); val hy = Geo.center(tj)
            val ddx = hx - x; val ddy = hy - y
            val d = hypot(ddx, ddy)
            if (d < 0.36f) {
                startFall(hx, hy); return
            }
            if (d > 1e-4f) { ax += ddx / d * 14f; ay += ddy / d * 14f }
        }
        if (tile == T.BOOST) {
            when (level.boostDir[tj * level.gw + ti]) {
                0 -> ax += 60f; 1 -> ay += 60f; 2 -> ax -= 60f; 3 -> ay -= 60f
            }
        }

        vx += ax * dt
        vy += ay * dt
        val damp = if (tile == T.ICE) 0.12f else 1.5f
        val f = exp(-damp * dt)
        vx *= f; vy *= f
        val sp = hypot(vx, vy)
        if (sp > 16f) { vx *= 16f / sp; vy *= 16f / sp }

        val n = ceil(sp * dt / 0.07f).toInt().coerceIn(1, 12)
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
                val e = 0.38f
                vx -= (1f + e) * vn * nx
                vy -= (1f + e) * vn * ny
                // Tarcie styczne przy uderzeniu.
                val tx = -ny; val ty = nx
                val vt = vx * tx + vy * ty
                vx -= vt * 0.04f * tx; vy -= vt * 0.04f * ty
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
        if (hypot(level.cx(level.goal) - x, level.cy(level.goal) - y) < 0.38f) {
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
