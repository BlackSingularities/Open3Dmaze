package com.blacksingularities.open3dmaze.render

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.blacksingularities.open3dmaze.data.Settings
import com.blacksingularities.open3dmaze.game.BALL_R
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.Geo
import com.blacksingularities.open3dmaze.game.HOLE_R
import com.blacksingularities.open3dmaze.game.Level
import com.blacksingularities.open3dmaze.game.Phase
import com.blacksingularities.open3dmaze.game.T
import com.blacksingularities.open3dmaze.game.WALL_T
import com.blacksingularities.open3dmaze.ui.Skin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rysuje płaski labirynt jak bryłę 3D: każda ściana jest graniastosłupem, którego górna
 * ściana odsuwa się od punktu zbiegu. Punkt zbiegu wędruje razem z pochyleniem telefonu,
 * więc ściany „wyrastają” w stronę gracza i przechylają się na boki.
 *
 * Światło pada z lewej góry: ściany rzucają cień na podłogę, u podstaw ścian jest
 * zaciemnienie (okluzja), a krawędzie wierzchów są fazowane.
 */
class MazeRenderer {
    private val path = Path()
    private val shadowPath = Path()
    private val topPath = Path()
    private val bevelLight = Path()
    private val bevelDark = Path()
    private var sortBuf = LongArray(256)
    private var camX = Float.NaN
    private var camY = Float.NaN
    private var vxS = Float.NaN
    private var vyS = Float.NaN
    private var curSc = Float.NaN
    private var mapFor: Level? = null
    private var map: ImageBitmap? = null

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
        hud: Boolean = true,
        overview: Boolean = false,
    ) = with(ds) {
        val lv = g.level
        val w = size.width
        val h = size.height
        // Podgląd całości: skala dopasowana do labiryntu; przejście jest płynne w obie strony.
        val playSc = w / (7.2f * (1f + WALL_T) / st.zoom.coerceIn(0.5f, 2f))
        val fitSc = min(w / (lv.worldW + 1.2f), h / (lv.worldH + 1.2f)).coerceAtMost(playSc)
        val targetSc = if (overview) fitSc else playSc
        curSc = if (curSc.isNaN()) targetSc else curSc + (targetSc - curSc) * (1f - exp(-3.5f * dt))
        val sc = curSc
        val viewW = w / sc
        val viewH = h / sc

        // Kamera podąża za kulką z lekkim wyprzedzeniem w kierunku ruchu, nie wyjeżdża poza labirynt.
        val lookX = (g.vx * 0.18f).coerceIn(-viewW * 0.25f, viewW * 0.25f)
        val lookY = (g.vy * 0.18f).coerceIn(-viewH * 0.25f, viewH * 0.25f)
        val pad = 0.6f
        val tcx = if (overview || lv.worldW + pad * 2 <= viewW) lv.worldW / 2f else (g.x + lookX).coerceIn(viewW / 2f - pad, lv.worldW - viewW / 2f + pad)
        val tcy = if (overview || lv.worldH + pad * 2 <= viewH) lv.worldH / 2f else (g.y + lookY).coerceIn(viewH / 2f - pad, lv.worldH - viewH / 2f + pad)
        if (camX.isNaN()) { camX = tcx; camY = tcy } else {
            val a = 1f - exp((if (overview) -3.5f else -4.5f) * dt)
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

        val unit = sc
        // Kierunek cienia (światło z lewej góry) – w pikselach na jednostkę wysokości.
        val shX = unit * 0.30f * st.depth.coerceAtLeast(0.3f)
        val shY = unit * 0.40f * st.depth.coerceAtLeast(0.3f)

        // Tło i deska z fakturą, przesuwana razem z labiryntem.
        drawRect(Color.Black)
        val ox = sx(0f); val oy = sy(0f)
        translate(ox, oy) {
            drawRect(Color(0xFF0B0B0C), Offset.Zero, Size(lv.worldW * sc, lv.worldH * sc))
            drawRect(floorBrush, Offset.Zero, Size(lv.worldW * sc, lv.worldH * sc))
        }

        val margin = (max(w, h) * 0.35f) / sc
        val i0 = Geo.index(camX - viewW / 2f - margin).coerceIn(0, lv.gw - 1)
        val i1 = Geo.index(camX + viewW / 2f + margin).coerceIn(0, lv.gw - 1)
        val j0 = Geo.index(camY - viewH / 2f - margin).coerceIn(0, lv.gh - 1)
        val j1 = Geo.index(camY + viewH / 2f + margin).coerceIn(0, lv.gh - 1)

        // Cienie ścian – jedna ścieżka, więc nakładające się cienie nie ciemnieją podwójnie.
        shadowPath.reset()
        for (j in j0..j1) for (i in i0..i1) {
            if (lv.at(i, j) != T.WALL) continue
            val x0 = sx(Geo.start(i)); val y0 = sy(Geo.start(j))
            shadowPath.addRect(Rect(x0 + shX * 0.15f, y0 + shY * 0.15f, x0 + Geo.size(i) * unit + shX, y0 + Geo.size(j) * unit + shY))
        }
        drawPath(shadowPath, Color(0x99000000))

        // Pola specjalne.
        for (j in j0..j1) for (i in i0..i1) {
            val tile = lv.at(i, j)
            if (tile == T.WALL || tile == T.FLOOR) continue
            val px = sx(Geo.center(i)); val py = sy(Geo.center(j))
            val corridor = i % 2 == 1 && j % 2 == 1
            when (tile) {
                T.ICE -> {
                    val tl = Offset(sx(Geo.start(i)), sy(Geo.start(j)))
                    val sz = Size(Geo.size(i) * unit + 1f, Geo.size(j) * unit + 1f)
                    drawRect(Brush.linearGradient(listOf(Color(0x3390E4FF), Color(0x14B0F0FF), Color(0x3390E4FF)), tl, tl + Offset(sz.width, sz.height)), tl, sz)
                    if (corridor) {
                        drawLine(Color(0x40E0FAFF), Offset(px - unit * 0.32f, py + unit * 0.18f), Offset(px - unit * 0.02f, py - unit * 0.3f), 1.5f)
                        drawLine(Color(0x26E0FAFF), Offset(px - unit * 0.1f, py + unit * 0.3f), Offset(px + unit * 0.2f, py - unit * 0.12f), 1.2f)
                    }
                }
                T.HOLE -> {
                    val r = unit * HOLE_R
                    val c = Offset(px, py)
                    // Metalowy pierścień z połyskiem od lewej góry.
                    drawCircle(Brush.linearGradient(listOf(Color(0xFF8A8A8A), Color(0xFF2A2A2A), Color(0xFF151515)), c - Offset(r, r), c + Offset(r, r)), r * 1.12f, c)
                    drawCircle(Color.Black, r, c)
                    // Głąb: wnętrze przesunięte do punktu zbiegu, z ciemniejącą ścianką.
                    val inner = Offset(lx(px, -0.9f), ly(py, -0.9f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF202020), Color.Black), c, r), r * 0.98f, c)
                    drawCircle(Color.Black, r * (1f - k * 0.9f), inner)
                }
                T.CHECK -> {
                    val on = g.checkpoint == j * lv.gw + i
                    val col = if (on) accent else Color(0x88FFFFFF)
                    drawCircle(col.copy(alpha = if (on) 0.22f else 0.06f), unit * 0.42f, Offset(px, py))
                    drawCircle(col, unit * 0.42f, Offset(px, py), style = Stroke(2f))
                    val top = Offset(lx(px, 1.2f), ly(py, 1.2f))
                    drawLine(Color(0x88000000), Offset(px, py), Offset(px + shX * 1.2f, py + shY * 1.2f), 3f, cap = StrokeCap.Round)
                    drawLine(Color(0xFFBDBDBD), Offset(px, py), top, 3f, cap = StrokeCap.Round)
                    path.reset()
                    path.moveTo(top.x, top.y)
                    path.lineTo(top.x + unit * 0.3f, top.y + unit * 0.09f)
                    path.lineTo(top.x, top.y + unit * 0.2f)
                    path.close()
                    drawPath(path, col)
                }
                T.BOOST -> {
                    val dir = lv.boostDir[j * lv.gw + i] ?: 0
                    val ang = dir * (PI / 2).toFloat()
                    val ca = cos(ang); val sa = sin(ang)
                    drawRoundRect(accent.copy(alpha = 0.08f), Offset(px - unit * 0.42f, py - unit * 0.42f), Size(unit * 0.84f, unit * 0.84f), CornerRadius(unit * 0.12f))
                    for (n in 0..2) {
                        val ph = ((time * 1.6f + n / 3f) % 1f)
                        val o = (ph - 0.5f) * unit * 0.7f
                        val a = (1f - abs(ph - 0.5f) * 2f).coerceIn(0f, 1f)
                        val bx = px + ca * o; val by = py + sa * o
                        val s = unit * 0.18f
                        val c = accent.copy(alpha = 0.8f * a)
                        drawLine(c, Offset(bx - ca * s - sa * s, by - sa * s + ca * s), Offset(bx, by), 4f, StrokeCap.Round)
                        drawLine(c, Offset(bx - ca * s + sa * s, by - sa * s - ca * s), Offset(bx, by), 4f, StrokeCap.Round)
                    }
                }
                T.PORTAL -> {
                    val idx = j * lv.gw + i
                    val pair = lv.portals.indexOf(idx) / 2
                    val pc = PortalColors[pair % PortalColors.size]
                    drawCircle(Brush.radialGradient(listOf(pc.copy(alpha = 0.6f), Color.Transparent), Offset(px, py), unit * 0.5f), unit * 0.5f, Offset(px, py))
                    drawCircle(Color.Black, unit * 0.2f, Offset(px, py))
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
                else -> Unit
            }
        }

        // Meta: wgłębiony dołek z poświatą i słup światła.
        val gx = sx(lv.cx(lv.goal)); val gy = sy(lv.cy(lv.goal))
        val gc = Offset(gx, gy)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), gc, unit * 0.9f), unit * 0.9f, gc)
        drawCircle(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.6f)), gc - Offset(unit * 0.4f, unit * 0.4f), gc + Offset(unit * 0.4f, unit * 0.4f)), unit * 0.38f, gc)
        drawCircle(Color(0xFF050505), unit * 0.31f, gc)
        drawCircle(accent.copy(alpha = 0.5f), unit * 0.31f * (1f - k * 0.5f), Offset(lx(gx, -0.5f), ly(gy, -0.5f)), style = Stroke(2f))
        for (n in 0..1) {
            val ph = (time * 0.6f + n / 2f) % 1f
            drawCircle(accent.copy(alpha = (1f - ph) * 0.5f), unit * (0.4f + ph * 0.5f), gc, style = Stroke(2.5f))
        }
        val beamTop = Offset(lx(gx, 3.2f), ly(gy, 3.2f))
        drawLine(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), gc, beamTop), gc, beamTop, unit * 0.3f, StrokeCap.Round)

        // Ślad kulki.
        if (st.trail && g.trailCount > 1) {
            val n = g.trailCount
            for (q in 0 until n) {
                val idx = (g.trailHead - q + g.trailX.size) % g.trailX.size
                val a = (1f - q / n.toFloat()) * 0.16f
                drawCircle((skin.glow ?: accent).copy(alpha = a), unit * BALL_R * (0.7f - 0.45f * q / n), Offset(sx(g.trailX[idx]), sy(g.trailY[idx])))
            }
        }

        // Klejnoty unoszą się nad podłogą i rzucają cień.
        for (q in lv.gems.indices) {
            if (g.collected[q]) continue
            val c = lv.gems[q]
            val px = sx(lv.cx(c)); val py = sy(lv.cy(c))
            if (px < -unit || px > w + unit || py < -unit || py > h + unit) continue
            val hh = 0.55f + 0.12f * sin(time * 2.4f + q)
            drawOval(Color(0x88000000), Offset(px + shX * hh - unit * 0.16f, py + shY * hh - unit * 0.09f), Size(unit * 0.32f, unit * 0.18f))
            val gpx = lx(px, hh); val gpy = ly(py, hh)
            val s = unit * 0.2f * (1f + k * hh)
            val spin = cos(time * 2f + q * 1.3f)
            drawCircle(Brush.radialGradient(listOf(Color(0x5560F0FF), Color.Transparent), Offset(gpx, gpy), s * 2.2f), s * 2.2f, Offset(gpx, gpy))
            path.reset()
            path.moveTo(gpx, gpy - s * 1.25f); path.lineTo(gpx + s * spin, gpy); path.lineTo(gpx, gpy + s * 1.25f); path.lineTo(gpx - s * spin, gpy); path.close()
            drawPath(path, Color(0xFF3CC8E0))
            path.reset()
            path.moveTo(gpx, gpy - s * 1.25f); path.lineTo(gpx + s * spin, gpy); path.lineTo(gpx, gpy); path.close()
            drawPath(path, Color(0xFFBFFBFF))
            path.reset()
            path.moveTo(gpx, gpy + s * 1.25f); path.lineTo(gpx - s * spin, gpy); path.lineTo(gpx, gpy); path.close()
            drawPath(path, Color(0xFF1E7F96))
        }

        g.ghost?.let { gh ->
            val n = gh.size / 2
            if (n > 1 && g.phase != Phase.WON) {
                val f = (g.time / com.blacksingularities.open3dmaze.game.GHOST_DT).coerceIn(0f, (n - 1).toFloat())
                val i = f.toInt().coerceAtMost(n - 2)
                val u = f - i
                val gxw = gh[2 * i] + (gh[2 * i + 2] - gh[2 * i]) * u
                val gyw = gh[2 * i + 1] + (gh[2 * i + 3] - gh[2 * i + 1]) * u
                val p = Offset(lx(sx(gxw), BALL_R), ly(sy(gyw), BALL_R))
                val rr = unit * BALL_R * (1f + k * BALL_R)
                drawCircle(Color.White.copy(alpha = 0.10f), rr, p)
                drawCircle(Color.White.copy(alpha = 0.45f), rr, p, style = Stroke(2f))
            }
        }

        drawBall(g, skin, accent, ::sx, ::sy, ::lx, ::ly, unit, k, time, shX, shY)

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

        val base = Color(0xFF101010)
        val cLeft = lerp(base, accent, 0.46f)
        val cTop = lerp(base, accent, 0.60f)
        val cRight = lerp(base, accent, 0.22f)
        val cBottom = lerp(base, accent, 0.13f)
        val ao = Color(0x73000000)
        val aoH = 0.32f
        for (q in cnt - 1 downTo 0) {
            val idx = (sortBuf[q] and 0xFFFFFFFFL).toInt()
            val i = idx % lv.gw; val j = idx / lv.gw
            val x0 = sx(Geo.start(i)); val x1 = x0 + Geo.size(i) * unit
            val y0 = sy(Geo.start(j)); val y1 = y0 + Geo.size(j) * unit
            val tx0 = lx(x0, 1f); val tx1 = lx(x1, 1f); val ty0 = ly(y0, 1f); val ty1 = ly(y1, 1f)
            val ax0 = lx(x0, aoH); val ax1 = lx(x1, aoH); val ay0 = ly(y0, aoH); val ay1 = ly(y1, aoH)
            if (vX < x0 && lv.at(i - 1, j) != T.WALL) {
                quad(x0, y0, x0, y1, tx0, ty1, tx0, ty0, cLeft); quad(x0, y0, x0, y1, ax0, ay1, ax0, ay0, ao)
            }
            if (vX > x1 && lv.at(i + 1, j) != T.WALL) {
                quad(x1, y0, x1, y1, tx1, ty1, tx1, ty0, cRight); quad(x1, y0, x1, y1, ax1, ay1, ax1, ay0, ao)
            }
            if (vY < y0 && lv.at(i, j - 1) != T.WALL) {
                quad(x0, y0, x1, y0, tx1, ty0, tx0, ty0, cTop); quad(x0, y0, x1, y0, ax1, ay0, ax0, ay0, ao)
            }
            if (vY > y1 && lv.at(i, j + 1) != T.WALL) {
                quad(x0, y1, x1, y1, tx1, ty1, tx0, ty1, cBottom); quad(x0, y1, x1, y1, ax1, ay1, ax0, ay1, ao)
            }
        }

        // Wierzchy jedną ścieżką, potem fazowanie odsłoniętych krawędzi.
        topPath.reset(); bevelLight.reset(); bevelDark.reset()
        for (q in 0 until cnt) {
            val idx = (sortBuf[q] and 0xFFFFFFFFL).toInt()
            val i = idx % lv.gw; val j = idx / lv.gw
            val x0 = lx(sx(Geo.start(i)), 1f); val x1 = lx(sx(Geo.start(i) + Geo.size(i)), 1f)
            val y0 = ly(sy(Geo.start(j)), 1f); val y1 = ly(sy(Geo.start(j) + Geo.size(j)), 1f)
            topPath.addRect(Rect(x0 - 0.5f, y0 - 0.5f, x1 + 0.5f, y1 + 0.5f))
            if (lv.at(i, j - 1) != T.WALL) { bevelLight.moveTo(x0, y0 + 1f); bevelLight.lineTo(x1, y0 + 1f) }
            if (lv.at(i - 1, j) != T.WALL) { bevelLight.moveTo(x0 + 1f, y0); bevelLight.lineTo(x0 + 1f, y1) }
            if (lv.at(i, j + 1) != T.WALL) { bevelDark.moveTo(x0, y1 - 1f); bevelDark.lineTo(x1, y1 - 1f) }
            if (lv.at(i + 1, j) != T.WALL) { bevelDark.moveTo(x1 - 1f, y0); bevelDark.lineTo(x1 - 1f, y1) }
        }
        drawPath(topPath, lerp(base, accent, 0.86f))
        drawPath(bevelLight, lerp(accent, Color.White, 0.6f), style = Stroke(2.5f))
        drawPath(bevelDark, lerp(base, accent, 0.55f), style = Stroke(2.5f))

        if (lv.spec.dark) {
            val bc = Offset(lx(sx(g.x), BALL_R), ly(sy(g.y), BALL_R))
            val r = unit * 3.4f
            drawRect(Brush.radialGradient(0f to Color.Transparent, 0.45f to Color(0x99000000), 1f to Color.Black, center = bc, radius = r))
            drawLine(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent), gc, beamTop), gc, beamTop, unit * 0.12f, StrokeCap.Round)
        }

        // Winieta – światło skupione na środku planszy.
        drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0xA0000000), center = Offset(cx, cy), radius = max(w, h) * 0.75f))

        if (hud && !overview && st.minimap && !lv.spec.dark && (lv.worldW > viewW || lv.worldH > viewH)) drawMinimap(g, accent, viewW, viewH)
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }

    private fun DrawScope.drawMinimap(g: Game, accent: Color, viewW: Float, viewH: Float) {
        val lv = g.level
        if (mapFor !== lv) { map = buildMap(lv); mapFor = lv }
        val img = map ?: return
        val maxW = size.width * 0.30f
        val maxH = size.height * 0.24f
        val s = min(maxW / lv.gw, maxH / lv.gh)
        val mw = lv.gw * s; val mh = lv.gh * s
        val left = 16f * density / 2.6f + 8f
        val top = size.height - mh - 24f * density
        drawRoundRect(Color(0xCC000000), Offset(left - 6f, top - 6f), Size(mw + 12f, mh + 12f), CornerRadius(10f))
        drawImage(img, IntOffset.Zero, IntSize(lv.gw, lv.gh), IntOffset(left.toInt(), top.toInt()), IntSize(mw.toInt(), mh.toInt()), alpha = 0.8f, filterQuality = FilterQuality.None)
        val fx = lv.worldW
        val fy = lv.worldH
        fun mx(v: Float) = left + v / fx * mw
        fun my(v: Float) = top + v / fy * mh
        drawRect(Color(0x55FFFFFF), Offset(mx(camX - viewW / 2f), my(camY - viewH / 2f)), Size(viewW / fx * mw, viewH / fy * mh), style = Stroke(1.5f))
        drawCircle(accent, max(3f, s * 1.2f), Offset(mx(lv.cx(lv.goal)), my(lv.cy(lv.goal))))
        drawCircle(Color.White, max(3f, s * 1.1f), Offset(mx(g.x), my(g.y)))
    }

    private fun buildMap(lv: Level): ImageBitmap {
        val bmp = Bitmap.createBitmap(lv.gw, lv.gh, Bitmap.Config.ARGB_8888)
        val px = IntArray(lv.gw * lv.gh) { i ->
            when (lv.tiles[i]) {
                T.WALL -> 0xFFD0D0D0.toInt()
                T.HOLE -> 0xFF000000.toInt()
                T.ICE -> 0xFF1A3A48.toInt()
                else -> 0xFF161616.toInt()
            }
        }
        bmp.setPixels(px, 0, lv.gw, 0, 0, lv.gw, lv.gh)
        return bmp.asImageBitmap()
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
        unit: Float, k: Float, time: Float, shX: Float, shY: Float,
    ) {
        var hh = BALL_R
        var scale = 1f
        if (g.phase == Phase.FALLING) {
            val f = g.fallProgress
            hh = BALL_R - f * 1.6f
            scale = 1f - f * 0.5f
        } else if (g.phase == Phase.WON) {
            // Kulka wpada do dołka mety.
            hh = BALL_R - min(g.winT, 0.4f) * 1.2f
        }
        val fx = sx(g.x); val fy = sy(g.y)
        val br = unit * BALL_R
        val alpha = if (g.phase == Phase.FALLING) (1f - g.fallProgress).coerceIn(0f, 1f) else 1f
        if (g.phase == Phase.RUNNING) {
            // Miękki cień przesunięty od światła i ciemny punkt styku.
            drawOval(
                Brush.radialGradient(listOf(Color(0xB0000000), Color.Transparent), Offset(fx + shX * 0.45f, fy + shY * 0.45f), br * 1.25f),
                Offset(fx + shX * 0.45f - br * 1.25f, fy + shY * 0.45f - br * 1.05f), Size(br * 2.5f, br * 2.1f),
            )
            drawCircle(Color(0xCC000000), br * 0.55f, Offset(fx + br * 0.08f, fy + br * 0.1f))
            skin.glow?.let { drawCircle(Brush.radialGradient(listOf(it.copy(alpha = 0.4f), Color.Transparent), Offset(fx, fy), unit * 0.75f), unit * 0.75f, Offset(fx, fy)) }
        }
        val bx = lx(fx, hh); val by = ly(fy, hh)
        val r = br * (1f + k * hh) * scale
        if (r <= 0.5f) return
        val c = Offset(bx, by)
        // Korpus: odbicie jasnego otoczenia u góry, ciemnej podłogi u dołu.
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha), 0.22f to skin.base.copy(alpha = alpha), 0.75f to skin.shade.copy(alpha = alpha),
                1f to lerp(skin.shade, Color.Black, 0.6f).copy(alpha = alpha),
                center = Offset(bx - r * 0.38f, by - r * 0.42f), radius = r * 1.7f,
            ),
            r, c,
        )
        drawCircle(
            Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f * alpha), startY = by - r, endY = by + r),
            r, c,
        )
        // Plamki przyklejone do powierzchni kuli, obracane prawdziwą macierzą obrotu.
        val m = g.rot
        val spotC = if (skin.stripes) accent else skin.shade
        val spotA = if (skin.stripes) 0.95f else 0.28f
        val spotR = if (skin.stripes) 0.42f else 0.17f
        for (p in 0 until SPOTS.size / 3) {
            val bxs = SPOTS[3 * p]; val bys = SPOTS[3 * p + 1]; val bzs = SPOTS[3 * p + 2]
            val wx = m[0] * bxs + m[1] * bys + m[2] * bzs
            val wy = m[3] * bxs + m[4] * bys + m[5] * bzs
            val wz = m[6] * bxs + m[7] * bys + m[8] * bzs
            if (wz >= -0.05f) continue
            val face = -wz
            // Plamka przy krawędzi jest mniejsza i bledsza – skrót perspektywiczny kuli.
            drawCircle(spotC.copy(alpha = alpha * spotA * face.coerceIn(0f, 1f)), r * spotR * (0.35f + 0.65f * face), Offset(bx + wx * r * 0.92f, by + wy * r * 0.92f))
        }
        // Odbity akcent na krawędzi i ostry blik.
        drawArc(accent.copy(alpha = 0.35f * alpha), 20f, 70f, false, Offset(bx - r * 0.92f, by - r * 0.92f), Size(r * 1.84f, r * 1.84f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * alpha), Color.Transparent), Offset(bx - r * 0.4f, by - r * 0.45f), r * 0.32f), r * 0.32f, Offset(bx - r * 0.4f, by - r * 0.45f))
        drawCircle(Color.White.copy(alpha = alpha), r * 0.08f, Offset(bx - r * 0.42f, by - r * 0.47f))
        if (g.phase == Phase.WON) {
            val ph = (time * 1.5f) % 1f
            drawCircle(accent.copy(alpha = 1f - ph), unit * (0.4f + ph * 1.2f), Offset(fx, fy), style = Stroke(3f))
        }
    }

    companion object {
        /** Rozmieszczenie plamek na kuli (wektory jednostkowe, ściany sześcianu). */
        private val SPOTS = floatArrayOf(1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f)

        val PortalColors = listOf(Color(0xFFFF2D95), Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFFB6FF3B))

        /** Delikatna faktura podłogi: ziarno i plamy jak na matowej, ciemnej płycie. */
        private val floorBrush: ShaderBrush by lazy {
            val n = 192
            val rnd = Random(42)
            val blot = FloatArray(n * n)
            repeat(24) {
                val bx = rnd.nextInt(n); val by = rnd.nextInt(n); val br = 10 + rnd.nextInt(30); val str = rnd.nextFloat() * 6f
                for (y in -br..br) for (x in -br..br) {
                    val d = (x * x + y * y).toFloat() / (br * br)
                    if (d < 1f) {
                        val ix = (bx + x + n) % n; val iy = (by + y + n) % n
                        blot[iy * n + ix] += str * (1f - d)
                    }
                }
            }
            val px = IntArray(n * n) { i ->
                val v = (rnd.nextFloat() * 10f + blot[i]).toInt().coerceIn(0, 40)
                // Kanał alfa niesie fakturę, kolor jest szarobiały.
                (v shl 24) or 0x00D8D8DC
            }
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, n, 0, 0, n, n)
            ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
        }
    }
}
