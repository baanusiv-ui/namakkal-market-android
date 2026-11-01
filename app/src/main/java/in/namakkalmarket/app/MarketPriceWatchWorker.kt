package com.namakkalmarket.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MarketPriceWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val client = OkHttpClient()

    override fun doWork(): Result = try {
        val base = BuildConfig.SUPABASE_URL.trimEnd("/") + "/rest/v1/market_prices"
        val headers = mapOf("apikey" to BuildConfig.SUPABASE_PUBLISHABLE_KEY, "Cache-Control" to "no-cache")
        fun get(path: String): String {
            val request = Request.Builder().url(base + path).apply { headers.forEach { (k,v) -> header(k,v) } }.build()
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string() ?: "[]"
            }
        }
        val latestArray = JSONArray(get("?select=market_date&order=market_date.desc&limit=1"))
        if (latestArray.length() == 0) return Result.success()
        val date = latestArray.getJSONObject(0).getString("market_date")
        val data = get("?select=market_date,tamil_name,english_name,low_price,high_price&market_date=eq.$date&order=tamil_name.asc,english_name.asc")
        val rows = JSONArray(data)
        val normalized = (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            listOf(o.optString("tamil_name"),o.optString("english_name"),o.optString("low_price"),o.optString("high_price")).joinToString("|")
        }.sorted().joinToString("\n")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        val prefs = applicationContext.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
        val old = prefs.getString("notification_fingerprint", null)
        if (old != null && old != fingerprint) sendNotification(date)
        prefs.edit().putString("notification_fingerprint", fingerprint).putString("notification_date", date).apply()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    private fun sendNotification(date: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channelId = "market_price_updates"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Market price updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val shownDate = date.replace("-", ".")
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("நாமக்கல் உழவர் சந்தை")
            .setContentText("$shownDate விலைத் தகவல் புதுப்பிக்கப்பட்டுள்ளது. திறந்து பார்க்கவும்.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$shownDate விலைத் தகவல் புதுப்பிக்கப்பட்டுள்ளது. புதிய விலைகளைப் பார்க்க செயலியைத் திறக்கவும்."))
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(4101, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MarketPriceWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "namakkal-market-price-watch",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
