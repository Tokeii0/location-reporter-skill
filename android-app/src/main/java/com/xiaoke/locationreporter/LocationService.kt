package com.xiaoke.locationreporter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LocationService : Service() {

    companion object {
        const val ACTION_LOCATION_UPDATE = "com.xiaoke.locationreporter.LOCATION_UPDATE"
        private const val CHANNEL_ID = "location_reporter_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "LocationService"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var uploader: LocationUploader
    private var locationListener: LocationListener? = null
    private var serverUrl = ""
    private var intervalMinutes = 5

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        uploader = LocationUploader()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverUrl = intent?.getStringExtra("server_url") ?: ""
        intervalMinutes = intent?.getIntExtra("interval_minutes", 5) ?: 5

        startForeground(NOTIFICATION_ID, createNotification())

        // 立刻上报一次上次已知位置
        uploadLastKnownLocation()

        // 然后按间隔持续上报
        startLocationUpdates()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.let {
            locationManager.removeUpdates(it)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "位置上报服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "位置上报后台服务通知"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("位置上报")
            .setContentText("正在上报位置，间隔 ${intervalMinutes} 分钟")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun uploadLastKnownLocation() {
        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                lastNet != null -> lastNet
                else -> null
            }
            if (best != null && serverUrl.isNotEmpty()) {
                Log.d(TAG, "Uploading last known: ${best.latitude}, ${best.longitude}")
                broadcastAndUpload(best)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
        }
    }

    private fun broadcastAndUpload(location: Location) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            setPackage(packageName)
            putExtra("lat", location.latitude)
            putExtra("lng", location.longitude)
            putExtra("accuracy", location.accuracy)
            putExtra("timestamp", location.time)
        }
        sendBroadcast(intent)

        if (serverUrl.isNotEmpty()) {
            uploader.upload(
                serverUrl,
                location.latitude,
                location.longitude,
                location.time / 1000,
                location.accuracy
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
                broadcastAndUpload(location)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        val intervalMs = intervalMinutes * 60 * 1000L

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, intervalMs, 0f, locationListener!!
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, intervalMs, 0f, locationListener!!
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission", e)
            stopSelf()
        }
    }
}
