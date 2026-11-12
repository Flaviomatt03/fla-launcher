package it.flatech.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val text = i.getStringExtra("text") ?: return
        if (id == 0 || MainActivity.visible) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("fla_rem") == null) {
            val ch = NotificationChannel("fla_rem", "Promemoria", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, "fla_rem")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FLA-Tech")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        nm.notify(id, n)
        val sp = c.getSharedPreferences("fla_alarm", Context.MODE_PRIVATE)
        val set = (sp.getStringSet("fired", emptySet()) ?: emptySet()).toMutableSet()
        set.add(id.toString())
        sp.edit().putStringSet("fired", set).apply()
    }
}
