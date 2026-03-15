package com.xiaoke.locationreporter

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "location_reporter_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_RUNNING = "is_running"
        private const val REQUEST_LOCATION_PERMISSION = 1001
        private const val REQUEST_BACKGROUND_LOCATION = 1002
        private const val REQUEST_NOTIFICATION = 1003
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var etServerUrl: EditText
    private lateinit var etInterval: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnToggle: Button

    private lateinit var locationManager: LocationManager
    private var previewListener: LocationListener? = null
    private var isRunning = false

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationService.ACTION_LOCATION_UPDATE) {
                val lat = intent.getDoubleExtra("lat", 0.0)
                val lng = intent.getDoubleExtra("lng", 0.0)
                val accuracy = intent.getFloatExtra("accuracy", 0f)
                val time = intent.getLongExtra("timestamp", 0L)
                updateLocationDisplay(lat, lng, accuracy, time)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        etServerUrl = findViewById(R.id.et_server_url)
        etInterval = findViewById(R.id.et_interval)
        tvStatus = findViewById(R.id.tv_status)
        tvLocation = findViewById(R.id.tv_location)
        btnToggle = findViewById(R.id.btn_toggle)

        etServerUrl.setText(prefs.getString(KEY_SERVER_URL, ""))
        etInterval.setText(prefs.getInt(KEY_INTERVAL, 5).toString())
        isRunning = prefs.getBoolean(KEY_RUNNING, false)
        updateUI()

        btnToggle.setOnClickListener {
            if (isRunning) {
                stopReporting()
            } else {
                startReporting()
            }
        }

        requestLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LocationService.ACTION_LOCATION_UPDATE)
        ContextCompat.registerReceiver(this, locationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreviewLocation()
    }

    private fun updateLocationDisplay(lat: Double, lng: Double, accuracy: Float, time: Long) {
        tvLocation.text = "纬度: %.6f\n经度: %.6f\n精度: %.1f米\n时间: %s".format(
            lat, lng, accuracy,
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(time))
        )
    }

    private fun updateUI() {
        if (isRunning) {
            btnToggle.text = "停止上报"
            tvStatus.text = "状态：上报中..."
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_running))
        } else {
            btnToggle.text = "开始上报"
            tvStatus.text = "状态：已停止（定位预览中）"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_stopped))
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            startPreviewLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPreviewLocation() {
        tvLocation.text = "正在定位..."

        // 先尝试获取上次已知位置
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val lastLocation = when {
            lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            lastNet != null -> lastNet
            else -> null
        }
        if (lastLocation != null) {
            updateLocationDisplay(
                lastLocation.latitude, lastLocation.longitude,
                lastLocation.accuracy, lastLocation.time
            )
        }

        // 持续定位预览
        previewListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLocationDisplay(
                    location.latitude, location.longitude,
                    location.accuracy, location.time
                )
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    tvLocation.text = "❌ GPS 已关闭，请打开定位服务"
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // 同时请求 GPS 和网络定位
        val providers = mutableListOf<String>()
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }

        if (providers.isEmpty()) {
            tvLocation.text = "❌ 没有可用的定位服务\n请打开 GPS 或网络定位"
            return
        }

        for (provider in providers) {
            locationManager.requestLocationUpdates(
                provider, 3000L, 0f, previewListener!!
            )
        }

        // 显示正在使用的定位方式
        tvStatus.text = "状态：定位中（${providers.joinToString("+")}）"
    }

    private fun stopPreviewLocation() {
        previewListener?.let {
            locationManager.removeUpdates(it)
            previewListener = null
        }
    }

    private fun startReporting() {
        val serverUrl = etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        val intervalStr = etInterval.text.toString().trim()
        val interval = intervalStr.toIntOrNull() ?: 5
        if (interval < 1) {
            Toast.makeText(this, "上报间隔至少1分钟", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putInt(KEY_INTERVAL, interval)
            .apply()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_BACKGROUND_LOCATION
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION
                )
                return
            }
        }

        doStartService()
    }

    private fun doStartService() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
        val interval = prefs.getInt(KEY_INTERVAL, 5)

        isRunning = true
        prefs.edit().putBoolean(KEY_RUNNING, true).apply()
        updateUI()
        stopPreviewLocation()

        val intent = Intent(this, LocationService::class.java).apply {
            putExtra("server_url", serverUrl)
            putExtra("interval_minutes", interval)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopReporting() {
        isRunning = false
        prefs.edit().putBoolean(KEY_RUNNING, false).apply()
        updateUI()
        stopService(Intent(this, LocationService::class.java))
        startPreviewLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startPreviewLocation()
                } else {
                    tvLocation.text = "❌ 需要位置权限才能定位"
                    Toast.makeText(this, "需要位置权限", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_BACKGROUND_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startReporting()
                } else {
                    Toast.makeText(this, "需要后台位置权限才能持续上报", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_NOTIFICATION -> {
                doStartService()
            }
        }
    }
}
