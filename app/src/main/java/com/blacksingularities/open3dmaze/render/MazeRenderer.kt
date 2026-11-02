package com.blacksingularities.open3dmaze.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.blacksingularities.open3dmaze.data.Settings
import com.blacksingularities.open3dmaze.game.BALL_R
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.Geo
import com.blacksingularities.open3dmaze.game.Phase
import com.blacksingularities.open3dmaze.game.T
import com.blacksingularities.open3dmaze.game.WALL_T
import com.blacksingularities.open3dmaze.ui.Skin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rysuje płaski labirynt jak bryłę 3D: każda ściana jest graniastosłupem, którego górna
 * ściana odsuwa się od punktu zbiegu. Punkt zbiegu wędruje razem z pochyleniem telefonu,
 * więc ściany „wyrastają” w stronę gracza i przechylają się na boki.
 */
class MazeRenderer {
    private val path = Path()
    private var sortBuf = LongArray(256)
    private var camX = Float.NaN
    private var camY = Float.NaN
    private var vxS = Float.NaN
    private var vyS = Float.NaN

    fun resetCamera() {
        camX = Float.NaN; camY = Float.NaN
    }

    fun draw(
        ds: DrawScope,
        g: Game,
        st: Settings,
        tiltX: Float,
        tiltY: Float,
        time: Float,
        dt: Float,
        accent: Color,
        skin: Skin,
        dim: Float = 0f,
    ) = with(ds) {
        val lv = g.level
        val w = size.width
        val h = size.height
        val sc = w / (7.2f * (1f + WALL_T) / st.zoom.coerceIn(0.5f, 2f))
        val viewW = w / sc
        val viewH = h / sc

        // Kamera podąża za kulką, ale nie wyjeżdża poza labirynt.
        val tcx = if (lv.worldW <= viewW) lv.worldW / 2f else g.x.coerceIn(viewW / 2f, lv.worldW - viewW / 2f)
        val tcy = if (lv.worldH <= viewH) lv.worldH / 2f else g.y.coerceIn(viewH / 2f, lv.worldH - viewH / 2f)
        if (camX.isNaN()) { camX = tcx; camY = tcy } else {
            val a = 1f - exp(-5f * dt)
            camX += (tcx - camX) * a; camY += (tcy - camY) * a
        }
        val cx = w / 2f
        val cy = h / 2f
        fun sx(v: Float) = (v - camX) * sc + cx
        fun sy(v: Float) = (v - camY) * sc + cy

        // Punkt zbiegu – wygładzony, żeby ściany płynęły, a nie drgały.
        val par = if (st.invertParallax) -1f else 1f
        val tvx = cx - tiltX * w * 0.75f * st.parallax * par
        val tvy = cy - tiltY * h * 0.5f * st.parallax * par
        if (vxS.isNaN()) { vxS = tvx; vyS = tvy } else {
            val a = 1f - exp(-12f * dt)
            vxS += (tvx - vxS) * a; vyS += (tvy - vyS) * a
        }
        val vX = vxS
        val vY = vyS
        val k = 0.11f * st.depth
        fun lx(px: Float, hh: Float) = vX + (px - vX) * (1f + k * hh)
        fun ly(py: Float, hh: Float) = vY + (py - vY) * (1f + k * hh)

        drawRect(Color.Black)
        drawRect(Color(0xFF060606), Offset(sx(0f), sy(0f)), Size(lv.worldW * sc, lv.worldH * sc))

        val margin = (max(w, h) * 0.35f) / sc
        val i0 = Geo.index(camX - viewW / 2f - margin).coerceIn(0, lv.gw - 1)
        val i1 = Geo.index(camX + viewW / 2f + margin).coerceIn(0, lv.gw - 1)
        val j0 = Geo.index(camY - viewH / 2f - margin).coerceIn(0, lv.gh - 1)
        val j1 = Geo.index(camY + viewH / 2f + margin).coerceIn(0, lv.gh - 1)

        val unit = sc
        // Podłoga i pola specjalne.
        for (j in j0..j1) for (i in i0..i1) {
            val tile = lv.at(i, j)
            if (tile == T.WALL) continue
            val px = sx(Geo.center(i)); val py = sy(Geo.center(j))
            val corridor = i % 2 == 1 && j % 2 == 1
            when (tile) {
                T.ICE -> {
                    drawRect(Color(0x2A6FD8FF), Offset(sx(Geo.start(i)), sy(Geo.start(j))), Size(Geo.size(i) * unit + 1f, Geo.size(j) * unit + 1f))
                    if (corridor) drawLine(Color(0x336FD8FF), Offset(px - unit * 0.3f, py + unit * 0.2f), Offset(px + unit * 0.1f, py - unit * 0.3f), 2f)
                }
                T.HOLE -> {
                    val r = unit * 0.4f
                    drawCircle(Color(0xFF262626), r * 1.06f, Offset(px, py))
                    drawCircle(Color(0x55FFFFFF), r * 1.06f, Offset(px, py), style = Stroke(1.5f))
                    // Wnętrze dziury cofnięte „w głąb” – przesunięte w stronę punktu zbiegu.
                    drawCircle(Color.Black, r * (1f - k * 0.6f), Offset(lx(px, -0.6f), ly(py, -0.6f)))
                }
                T.CHECK -> {
                    val on = g.checkpoint == j * lv.gw + i
                    val c = if (on) accent else Color(0x66FFFFFF)
                    drawCircle(c.copy(alpha = if (on) 0.25f else 0.08f), unit * 0.42f, Offset(px, py))
                    drawCircle(c, unit * 0.42f, Offset(px, py), style = Stroke(2f))
                    val top = Offset(lx(px, 1.1f), ly(py, 1.1f))
                    drawLine(c, Offset(px, py), top, 3f, cap = StrokeCap.Round)
                    drawCircle(c, unit * 0.08f, top)
                }
                T.BOOST -> {
                    val dir = lv.boostDir[j * lv.gw + i] ?: 0
                    val ang = dir * (PI / 2).toFloat()
                    val ca = cos(ang); val sa = sin(ang)
                    for (n in 0..2) {
                        val ph = ((time * 1.6f + n / 3f) % 1f)
                        val o = (ph - 0.5f) * unit * 0.7f
                        val a = (1f - kotlin.math.abs(ph - 0.5f) * 2f).coerceIn(0f, 1f)
                        val bx = px + ca * o; val by = py + sa * o
                        val s = unit * 0.18f
                        val c = accent.copy(alpha = 0.75f * a)
                        drawLine(c, Offset(bx - ca * s - sa * s, by - sa * s + ca * s), Offset(bx, by), 4f, StrokeCap.Round)
                        drawLine(c, Offset(bx - ca * s + sa * s, by - sa * s - ca * s), Offset(bx, by), 4f, StrokeCap.Round)
                    }
                }
                T.PORTAL -> {
                    val idx = j * lv.gw + i
                    val pair = lv.portals.indexOf(idx) / 2
                    val pc = PortalColors[pair % PortalColors.size]
                    drawCircle(Brush.radialGradient(listOf(pc.copy(alpha = 0.55f), Color.Transparent), Offset(px, py), unit * 0.5f), unit * 0.5f, Offset(px, py))
                    for (n in 0..2) {
                        val r = unit * (0.18f + n * 0.1f)
                        drawArc(
                            pc.copy(alpha = 0.9f - n * 0.25f),
                            startAngle = time * (160f + n * 70f) * (if (n % 2 == 0) 1f else -1f),
                            sweepAngle = 220f, useCenter = false,
                            topLeft = Offset(px - r, py - r), size = Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round),
                        )
                    }
                }
                else -> if (st.floorGrid && corridor) drawCircle(Color(0x14FFFFFF), 2.2f, Offset(px, py))
            }
        }

        // Meta: pulsujące pierścienie i słup światła.
        val gx = sx(lv.cx(lv.goal)); val gy = sy(lv.cy(lv.goal))
        for (n in 0..2) {
            val ph = (time * 0.7f + n / 3f) % 1f
            drawCircle(accent.copy(alpha = (1f - ph) * 0.6f), unit * (0.15f + ph * 0.45f), Offset(gx, gy), style = Stroke(3f))
        }
        drawCircle(accent.copy(alpha = 0.35f), unit * 0.3f, Offset(gx, gy))
        val beamTop = Offset(lx(gx, 3.2f), ly(gy, 3.2f))
        drawLine(Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent), Offset(gx, gy), beamTop), Offset(gx, gy), beamTop, unit * 0.22f, StrokeCap.Round)

        // Ślad kulki.
        if (st.trail && g.trailCount > 1) {
            val n = g.trailCount
            for (q in 0 until n) {
                val idx = (g.trailHead - q + g.trailX.size) % g.trailX.size
                val a = (1f - q / n.toFloat()) * 0.22f
                drawCircle((skin.glow ?: accent).copy(alpha = a), unit * BALL_R * (0.8f - 0.5f * q / n), Offset(sx(g.trailX[idx]), sy(g.trailY[idx])))
            }
        }

        // Klejnoty unoszą się nad podłogą – rzut zależy od wysokości, więc też „pływają”.
        for (q in lv.gems.indices) {
            if (g.collected[q]) continue
            val c = lv.gems[q]
            val px = sx(lv.cx(c)); val py = sy(lv.cy(c))
            if (px < -unit || px > w + unit || py < -unit || py > h + unit) continue
            val hh = 0.55f + 0.12f * sin(time * 2.4f + q)
            drawCircle(Brush.radialGradient(listOf(Color(0x5560F0FF), Color.Transparent), Offset(px, py), unit * 0.3f), unit * 0.3f, Offset(px, py))
            val gpx = lx(px, hh); val gpy = ly(py, hh)
            val s = unit * 0.2f * (1f + k * hh)
            val spin = cos(time * 2f + q * 1.3f)
            path.reset()
            path.moveTo(gpx, gpy - s * 1.25f)
            path.lineTo(gpx + s * spin, gpy)
            path.lineTo(gpx, gpy + s * 1.25f)
            path.lineTo(gpx - s * spin, gpy)
            path.close()
            drawPath(path, Color(0xFF7CF7FF))
            path.reset()
            path.moveTo(gpx, gpy - s * 1.25f)
            path.lineTo(gpx + s * spin, gpy)
            path.lineTo(gpx, gpy)
            path.close()
            drawPath(path, Color(0xFFE6FFFF))
        }

        drawBall(g, skin, accent, ::sx, ::sy, ::lx, ::ly, unit, k, time)

        // Ściany – najpierw boki od najdalszych od punktu zbiegu, potem wszystkie wierzchy.
        var cnt = 0
        if (sortBuf.size < lv.walls.size) sortBuf = LongArray(lv.walls.size)
        for (idx in lv.walls) {
            val i = idx % lv.gw; val j = idx / lv.gw
            if (i < i0 || i > i1 || j < j0 || j > j1) continue
            val dx = sx(Geo.center(i)) - vX; val dy = sy(Geo.center(j)) - vY
            val key = java.lang.Float.floatToIntBits(dx * dx + dy * dy).toLong()
            sortBuf[cnt++] = (key shl 32) or idx.toLong()
        }
        java.util.Arrays.sort(sortBuf, 0, cnt)

        val cLeft = lerp(Color(0xFF0C0C0C), accent, 0.50f)
        val cTop = lerp(Color(0xFF0C0C0C), accent, 0.66f)
        val cRight = lerp(Color(0xFF0C0C0C), accent, 0.26f)
        val cBottom = lerp(Color(0xFF0C0C0C), accent, 0.18f)
        val edge = lerp(Color.Black, accent, 0.9f)
        for (q in cnt - 1 downTo 0) {
            val idx = (sortBuf[q] and 0xFFFFFFFFL).toInt()
            val i = idx % lv.gw; val j = idx / lv.gw
            val x0 = sx(Geo.start(i)); val x1 = x0 + Geo.size(i) * unit
            val y0 = sy(Geo.start(j)); val y1 = y0 + Geo.size(j) * unit
            val tx0 = lx(x0, 1f); val tx1 = lx(x1, 1f); val ty0 = ly(y0, 1f); val ty1 = ly(y1, 1f)
            if (vX < x0 && lv.at(i - 1, j) != T.WALL) quad(x0, y0, x0, y1, tx0, ty1, tx0, ty0, cLeft)
            if (vX > x1 && lv.at(i + 1, j) != T.WALL) quad(x1, y0, x1, y1, tx1, ty1, tx1, ty0, cRight)
            if (vY < y0 && lv.at(i, j - 1) != T.WALL) quad(x0, y0, x1, y0, tx1, ty0, tx0, ty0, cTop)
            if (vY > y1 && lv.at(i, j + 1) != T.WALL) quad(x0, y1, x1, y1, tx1, ty1, tx0, ty1, cBottom)
        }
        for (q in 0 until cnt) {
            val idx = (sortBuf[q] and 0xFFFFFFFFL).toInt()
            val i = idx % lv.gw; val j = idx / lv.gw
            val x0 = lx(sx(Geo.start(i)), 1f); val x1 = lx(sx(Geo.start(i) + Geo.size(i)), 1f)
            val y0 = ly(sy(Geo.start(j)), 1f); val y1 = ly(sy(Geo.start(j) + Geo.size(j)), 1f)
            drawRect(edge, Offset(x0 - 0.5f, y0 - 0.5f), Size(x1 - x0 + 1f, y1 - y0 + 1f))
        }

        if (lv.spec.dark) {
            val bc = Offset(lx(sx(g.x), BALL_R), ly(sy(g.y), BALL_R))
            val r = unit * 3.4f
            drawRect(Brush.radialGradient(0f to Color.Transparent, 0.45f to Color(0x99000000), 1f to Color.Black, center = bc, radius = r))
            // Słaba poświata mety pozwala wiedzieć, dokąd zmierzać.
            drawLine(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent), Offset(gx, gy), beamTop), Offset(gx, gy), beamTop, unit * 0.12f, StrokeCap.Round)
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }

    private fun DrawScope.quad(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, c: Color) {
        path.reset()
        path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.lineTo(dx, dy); path.close()
        drawPath(path, c)
    }

    private fun DrawScope.drawBall(
        g: Game, skin: Skin, accent: Color,
        sx: (Float) -> Float, sy: (Float) -> Float,
        lx: (Float, Float) -> Float, ly: (Float, Float) -> Float,
        unit: Float, k: Float, time: Float,
    ) {
        var hh = BALL_R
        var scale = 1f
        if (g.phase == Phase.FALLING) {
            val f = g.fallProgress
            hh = BALL_R - f * 1.4f
            scale = 1f - f * 0.55f
        } else if (g.phase == Phase.WON) {
            hh = BALL_R + min(g.winT, 1.5f) * 1.6f
        }
        val fx = sx(g.x); val fy = sy(g.y)
        val glowC = skin.glow ?: accent
        if (g.phase != Phase.FALLING) {
            drawCircle(Brush.radialGradient(listOf(glowC.copy(alpha = if (skin.glow != null) 0.45f else 0.18f), Color.Transparent), Offset(fx, fy), unit * 0.7f), unit * 0.7f, Offset(fx, fy))
        }
        val bx = lx(fx, hh); val by = ly(fy, hh)
        val r = unit * BALL_R * (1f + k * hh) * scale
        if (r <= 0.5f) return
        val alpha = if (g.phase == Phase.FALLING) (1f - g.fallProgress).coerceIn(0f, 1f) else 1f
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha), 0.25f to skin.base.copy(alpha = alpha), 1f to skin.shade.copy(alpha = alpha),
                center = Offset(bx - r * 0.35f, by - r * 0.4f), radius = r * 1.5f,
            ),
            r, Offset(bx, by),
        )
        // Znacznik obrotu – widać, że kulka się toczy, a nie ślizga.
        val mx = sin(g.rollX); val my = sin(g.rollY)
        val vis = cos(g.rollX) * cos(g.rollY)
        if (vis > 0f) {
            val mc = if (skin.stripes) accent else skin.shade
            drawCircle(mc.copy(alpha = alpha * (if (skin.stripes) 0.95f else 0.35f) * vis), r * (if (skin.stripes) 0.45f else 0.18f) * (0.6f + 0.4f * vis), Offset(bx + mx * r * 0.6f, by + my * r * 0.6f))
        }
        drawCircle(Color.White.copy(alpha = 0.85f * alpha), r * 0.16f, Offset(bx - r * 0.38f, by - r * 0.42f))
        if (g.phase == Phase.WON) {
            val ph = (time * 2f) % 1f
            drawCircle(accent.copy(alpha = 1f - ph), r * (1.2f + ph * 2f), Offset(bx, by), style = Stroke(3f))
        }
    }

    companion object {
        val PortalColors = listOf(Color(0xFFFF2D95), Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFFB6FF3B))
    }
}
