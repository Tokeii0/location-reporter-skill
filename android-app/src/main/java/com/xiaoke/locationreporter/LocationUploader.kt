package com.xiaoke.locationreporter

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LocationUploader {

    companion object {
        private const val TAG = "LocationUploader"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun upload(serverUrl: String, lat: Double, lng: Double, timestamp: Long, accuracy: Float) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("timestamp", timestamp)
                    put("accuracy", accuracy.toDouble())
                    put("device", android.os.Build.MODEL)
                }

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Upload success: ${response.code}")
                    } else {
                        Log.w(TAG, "Upload failed: ${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
            }
        }.start()
    }
}
