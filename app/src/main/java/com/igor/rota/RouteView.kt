package com.igor.rota

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Rota sobre mapa do OpenStreetMap (início em verde, posição atual em azul). */
class RouteView(c: Context) : View(c) {
    var pts: List<Pt> = emptyList()
    var hlFrom = -1
    var hlTo = -1
    private val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FC4C02"); style = Paint.Style.STROKE; strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 42f; textAlign = Paint.Align.CENTER }
    private val attr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 24f; textAlign = Paint.Align.RIGHT }
    private val dst = RectF()

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#E8E8E8"))
        val w = width.toFloat(); val h = height.toFloat()
        if (pts.isEmpty()) { c.drawText("A rota aparece aqui", w / 2, h / 2, txt); return }

        val T = 256f * resources.displayMetrics.density
        val mnA = pts.minOf { it.lat }; val mxA = pts.maxOf { it.lat }
        val mnO = pts.minOf { it.lon }; val mxO = pts.maxOf { it.lon }
        fun wx(lon: Double, z: Int): Double = (lon + 180) / 360 * (1 shl z) * T
        fun wy(lat: Double, z: Int): Double {
            val r = Math.toRadians(lat)
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z) * T
        }
        val pad = 40f
        var z = 17
        while (z > 3 && (wx(mxO, z) - wx(mnO, z) > w - 2 * pad || wy(mnA, z) - wy(mxA, z) > h - 2 * pad)) z--
        val ox = (wx(mnO, z) + wx(mxO, z)) / 2 - w / 2
        val oy = (wy(mnA, z) + wy(mxA, z)) / 2 - h / 2
        val n = 1 shl z

        for (ty in floor(oy / T).toInt()..floor((oy + h) / T).toInt()) {
            if (ty < 0 || ty >= n) continue
            for (tx in floor(ox / T).toInt()..floor((ox + w) / T).toInt()) {
                val bmp = TileCache.get(z, ((tx % n) + n) % n, ty) { postInvalidate() } ?: continue
                val l = (tx * T - ox).toFloat(); val t = (ty * T - oy).toFloat()
                dst.set(l, t, l + T, t + T)
                c.drawBitmap(bmp, null, dst, null)
            }
        }

        fun px(p: Pt) = (wx(p.lon, z) - ox).toFloat()
        fun py(p: Pt) = (wy(p.lat, z) - oy).toFloat()
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p), py(p)) else path.lineTo(px(p), py(p)) }
        c.drawPath(path, outline); c.drawPath(path, line)
        if (hlFrom >= 0 && hlTo > hlFrom && hlTo < pts.size) {
            val hp = Path()
            for (i in hlFrom..hlTo) { if (i == hlFrom) hp.moveTo(px(pts[i]), py(pts[i])) else hp.lineTo(px(pts[i]), py(pts[i])) }
            c.drawPath(hp, hl)
        }
        dot.color = Color.parseColor("#2E7D32"); c.drawCircle(px(pts.first()), py(pts.first()), 16f, dot)
        if (pts.size > 1) { dot.color = Color.parseColor("#1565C0"); c.drawCircle(px(pts.last()), py(pts.last()), 16f, dot) }
        c.drawText("© OpenStreetMap", w - 8, h - 8, attr)
    }
}
