package com.namakkalmarket.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AppUpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val client = OkHttpClient()

    override fun doWork(): Result = try {
        val manifestUrl = "https://drive.google.com/uc?export=download&id=1b55CeRlXw6J31Vg_pWI0pEpCD8NVrQ5A"
        val request = Request.Builder().url(manifestUrl).header("Cache-Control", "no-cache").build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Manifest HTTP ${response.code}")
            response.body?.string() ?: error("Empty update manifest")
        }
        val manifest = JSONObject(body)
        val latestCode = manifest.optLong("versionCode", 0L)
        val latestName = manifest.optString("versionName", "")
        val apkUrl = manifest.optString("apkUrl", "")
        val currentCode = if (Build.VERSION.SDK_INT >= 28) {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionCode.toLong()
        }
        if (latestCode > currentCode && apkUrl.startsWith("https://")) {
            notifyUpdate(latestName, apkUrl)
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    private fun notifyUpdate(version: String, apkUrl: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channelId = "app_updates"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openDownload = PendingIntent.getActivity(
            context, 4201, Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("நாமக்கல் செயலிக்கு புதிய புதுப்பிப்பு")
            .setContentText("புதிய பதிப்பு $version கிடைக்கிறது. பதிவிறக்கத் தொடத் தட்டவும்.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("நாமக்கல் உழவர் சந்தை செயலியின் புதிய பதிப்பு $version கிடைக்கிறது. Google Drive-ல் இருந்து APK-ஐ பதிவிறக்கி Android நிறுவல் அனுமதியுடன் புதுப்பிக்கவும்."))
            .setContentIntent(openDownload)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(4201, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "namakkal-app-update-check",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
