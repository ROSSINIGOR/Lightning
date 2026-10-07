package com.igor.rota

import kotlin.math.*

data class Seg(val id: Long, val name: String, val pts: List<Pt>, val len: Double)

object Geo {
    fun dist(a: Pt, b: Pt): Double {
        val r = Math.PI / 180
        val dl = (b.lat - a.lat) * r; val dn = (b.lon - a.lon) * r
        val x = sin(dl / 2).pow(2) + cos(a.lat * r) * cos(b.lat * r) * sin(dn / 2).pow(2)
        return 2 * 6371000.0 * asin(sqrt(x))
    }

    fun length(p: List<Pt>): Double { var s = 0.0; for (i in 1 until p.size) s += dist(p[i - 1], p[i]); return s }

    /** Insere pontos interpolados (inclusive no tempo) para que o espaçamento fique em torno de [step] metros. */
    fun densify(p: List<Pt>, step: Double): List<Pt> {
        val o = ArrayList<Pt>()
        for (i in p.indices) {
            if (i > 0) {
                val a = p[i - 1]; val b = p[i]
                val n = min((dist(a, b) / step).toInt(), 200)
                for (k in 1..n) {
                    val f = k.toDouble() / (n + 1)
                    o.add(Pt(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, (a.t + (b.t - a.t) * f).toLong()))
                }
            }
            o.add(p[i])
        }
        return o
    }

    /** Mantém um ponto a cada [step] metros (sempre o primeiro e o último). */
    fun sample(p: List<Pt>, step: Double): List<Pt> {
        if (p.size < 2) return p
        val o = arrayListOf(p.first()); var acc = 0.0
        for (i in 1 until p.size) {
            acc += dist(p[i - 1], p[i])
            if (acc >= step) { o.add(p[i]); acc = 0.0 }
        }
        if (o.last() !== p.last()) o.add(p.last())
        return o
    }
}

/** Procura passagens de uma atividade por um segmento. Retorna pares (horário de início, duração em ms). */
object Matcher {
    private const val R = 25.0    // raio de início/fim
    private const val TOL = 30.0  // distância máxima até a linha do segmento

    fun find(act: List<Pt>, seg: Seg): List<Pair<Long, Long>> {
        if (act.size < 2 || seg.pts.size < 2 || act.any { it.t <= 0 }) return emptyList()
        val a = Geo.densify(act, 5.0)
        val s = Geo.sample(Geo.densify(seg.pts, 5.0), 20.0)
        val s0 = s.first()
        val out = ArrayList<Pair<Long, Long>>()
        var i = 0
        while (i < a.size) {
            if (Geo.dist(a[i], s0) > R) { i++; continue }
            var best = i; var bd = Geo.dist(a[i], s0); var k = i
            while (k < a.size) {
                val d = Geo.dist(a[k], s0)
                if (d > R) break
                if (d < bd) { bd = d; best = k }
                k++
            }
            val j = end(a, best, s, seg.len)
            if (j > 0) { out.add(a[best].t to (a[j].t - a[best].t)); i = j + 1 } else i = k
        }
        return out.filter { it.second > 0 }
    }

    private fun end(a: List<Pt>, from: Int, s: List<Pt>, len: Double): Int {
        val sm = s.last(); var trav = 0.0; var idx = from + 1; var bestJ = -1; var bd = Double.MAX_VALUE
        while (idx < a.size) {
            trav += Geo.dist(a[idx - 1], a[idx])
            if (trav > len * 1.5 + 100) break
            val d = Geo.dist(a[idx], sm)
            if (d <= R && trav >= len * 0.7) { if (d < bd) { bd = d; bestJ = idx } }
            else if (bestJ > 0) break
            idx++
        }
        return if (bestJ > 0 && covers(a, from, bestJ, s)) bestJ else -1
    }

    /** O trecho da atividade precisa seguir todo o segmento, na mesma ordem. */
    private fun covers(a: List<Pt>, from: Int, to: Int, s: List<Pt>): Boolean {
        var p = from
        for (sp in s) {
            var q = p; var found = false
            while (q <= to) { if (Geo.dist(a[q], sp) <= TOL) { found = true; break }; q++ }
            if (!found) return false
            p = q
        }
        return true
    }
}
