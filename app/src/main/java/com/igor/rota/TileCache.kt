package com.igor.rota

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Baixa e guarda em memória os tiles do mapa (OpenStreetMap). */
object TileCache {
    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pending = HashSet<String>()
    private val failed = HashMap<String, Long>()
    private val pool = Executors.newFixedThreadPool(4)

    fun get(z: Int, x: Int, y: Int, onLoaded: () -> Unit): Bitmap? {
        val k = "$z/$x/$y"
        synchronized(this) {
            mem.get(k)?.let { return it }
            val f = failed[k]
            if (f != null && System.currentTimeMillis() - f < 30_000) return null
            if (!pending.add(k)) return null
        }
        pool.execute {
            try {
                val cn = URL("https://tile.openstreetmap.org/$k.png").openConnection() as HttpURLConnection
                cn.setRequestProperty("User-Agent", "RotaApp/1.0 (Android)")
                cn.connectTimeout = 8000; cn.readTimeout = 8000
                val bmp = cn.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) { synchronized(this) { mem.put(k, bmp) }; onLoaded() }
                else synchronized(this) { failed[k] = System.currentTimeMillis() }
            } catch (e: Exception) {
                synchronized(this) { failed[k] = System.currentTimeMillis() }
            } finally {
                synchronized(this) { pending.remove(k) }
            }
        }
        return null
    }
}
