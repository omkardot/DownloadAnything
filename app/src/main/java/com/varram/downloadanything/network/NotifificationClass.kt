package com.varram.downloadanything.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.varram.downloadanything.R
import com.varram.downloadanything.viewmodel.DownloadState

class DownloadNotificationHelper(private val context: Context) {

    private val channelId = "download_channel"
    private val notificationId = 1001

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Download Notifications",
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid noisy updates on progress ticks
            ).apply {
                description = "Shows progress for ongoing file downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(state: DownloadState) {
        // Check permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true) // Keeps sound/vibration silent when updating progress
            .setOngoing(state is DownloadState.Downloading) // Prevents user from swiping away while downloading

        when (state) {
            is DownloadState.Idle -> return

            is DownloadState.Downloading -> {
                builder.setContentTitle("Downloading file...")
                if (state.indeterminate) {
                    builder.setContentText("Downloading...")
                        .setProgress(0, 0, true)
                } else {
                    builder.setContentText("${state.progress}% (${state.bytesDownloaded / (1024 * 1024)} MB)")
                        .setProgress(100, state.progress, false)
                }
            }

            is DownloadState.Success -> {
                builder.setContentTitle("Download Complete")
                    .setContentText(state.fileName)
                    .setProgress(0, 0, false) // Clear progress bar
                    .setOngoing(false)
            }

            is DownloadState.Failed -> {
                builder.setContentTitle("Download Failed")
                    .setContentText(state.reason)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}