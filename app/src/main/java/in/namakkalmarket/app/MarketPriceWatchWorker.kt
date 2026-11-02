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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MarketPriceWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val repository = SheetRepository(applicationContext)
        val data = kotlinx.coroutines.runBlocking { repository.load() }
        val normalized = data.rows
            .map { it.tamil + "|" + it.english + "|" + it.low.orEmpty() + "|" + it.high.orEmpty() }
            .sorted()
            .joinToString("\n")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val prefs = applicationContext.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
        val old = prefs.getString("notification_fingerprint", null)
        if (old != null && old != fingerprint) sendNotification(data.date.toString())
        prefs.edit()
            .putString("notification_fingerprint", fingerprint)
            .putString("notification_date", data.date.toString())
            .apply()
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
        val open = PendingIntent.getActivity(context, 4101, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val shownDate = date.replace("-", ".")
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("நாமக்கல் உழவர் சந்தை")
            .setContentText(shownDate + " விலைத் தகவல் புதுப்பிக்கப்பட்டுள்ளது. திறந்து பார்க்கவும்.")
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
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
