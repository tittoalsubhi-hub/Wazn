package com.budgetsms.app.notify

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
import com.budgetsms.app.MainActivity
import com.budgetsms.app.R

/** Notifications never include amounts or message text unless the user allowed it. */
class Notifier(private val context: Context) {
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TX, context.getString(R.string.channel_transactions), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    fun newTransactions(count: Int, needReview: Int) {
        if (!canNotify()) return
        val text = if (needReview > 0) context.getString(R.string.notif_review, needReview) else context.getString(R.string.notif_new, count)
        val n = NotificationCompat.Builder(context, CH_TX).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name)).setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open()).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(1001, n) } catch (_: SecurityException) {}
    }

    fun alert(key: String, title: String, text: String) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(context, CH_ALERT).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open()).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(key.hashCode(), n) } catch (_: SecurityException) {}
    }

    companion object { const val CH_TX = "transactions"; const val CH_ALERT = "alerts" }
}
