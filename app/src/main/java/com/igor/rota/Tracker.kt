package com.igor.rota

import android.location.Location
import android.os.SystemClock

enum class St { IDLE, RUNNING, PAUSED }
data class Pt(val lat: Double, val lon: Double, val t: Long = 0L)

/** Estado da gravação, compartilhado entre o serviço (GPS) e a tela. Sem limite de velocidade. */
object Tracker {
    @Volatile var st = St.IDLE
    @Volatile var dist = 0.0
    @Volatile var maxKmh = 0.0
    private val pts = ArrayList<Pt>()
    private var movingMs = 0L
    private var lastTick = 0L
    private var last: Location? = null

    @Synchronized fun reset() { pts.clear(); dist = 0.0; maxKmh = 0.0; movingMs = 0; last = null; st = St.IDLE }
    @Synchronized fun resume() { st = St.RUNNING; lastTick = SystemClock.elapsedRealtime(); last = null }
    @Synchronized fun pause() {
        if (st == St.RUNNING) movingMs += SystemClock.elapsedRealtime() - lastTick
        st = St.PAUSED
    }
    @Synchronized fun finish() { pause(); st = St.IDLE }
    @Synchronized fun elapsed(): Long =
        movingMs + if (st == St.RUNNING) SystemClock.elapsedRealtime() - lastTick else 0L
    @Synchronized fun points(): List<Pt> = ArrayList(pts)

    @Synchronized fun onLocation(l: Location) {
        if (st != St.RUNNING || l.accuracy > 40f) return
        val p = last
        if (p == null) { last = l; pts.add(Pt(l.latitude, l.longitude, l.time)); return }
        val d = p.distanceTo(l).toDouble()
        val dt = (l.time - p.time) / 1000.0
        if (d < 3 || dt <= 0) return
        dist += d
        val v = (if (l.hasSpeed()) l.speed.toDouble() else d / dt) * 3.6
        if (l.accuracy <= 25f && v > maxKmh) maxKmh = v
        pts.add(Pt(l.latitude, l.longitude, l.time))
        last = l
    }
}
