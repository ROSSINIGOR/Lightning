package com.igor.rota

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

/** Serviço em primeiro plano: mantém o GPS gravando com a tela apagada. */
class TrackingService : Service() {
    private var active = false
    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) { Tracker.onLocation(l) }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            "START" -> begin()
            else -> end()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (active) return
        val n = notif()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            active = true
        } catch (e: SecurityException) { end() }
    }

    private fun end() {
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(listener) } catch (_: Exception) {}
        active = false
        stopForeground(true)
        stopSelf()
    }

    private fun notif(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("rota", "Gravação", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "rota")
            .setContentTitle("Rota")
            .setContentText("Gravando sua atividade…")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { if (active) end(); super.onDestroy() }
}
