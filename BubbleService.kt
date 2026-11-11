package it.flatech.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startFg()
            if (bubble == null) addBubble()
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bub", "Bollina FLA-Tech", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "bub")
            .setSmallIcon(android.R.drawable.ic_menu_revert)
            .setContentTitle("FLA-Tech")
            .setContentText("Tocca la bollina per tornare")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun addBubble() {
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val d = resources.displayMetrics.density
        val size = (58 * d).toInt()
        val tv = TextView(this).apply {
            text = "▲"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5AA9DC"))
                setStroke((3 * d).toInt(), Color.WHITE)
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = (220 * d).toInt() }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp.x = sx + dx.toInt(); lp.y = sy + dy.toInt()
                    w.updateViewLayout(tv, lp); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) goBack(); true }
                else -> false
            }
        }
        w.addView(tv, lp)
        bubble = tv
    }

    private fun goBack() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        stopSelf()
    }

    override fun onDestroy() {
        try { bubble?.let { wm?.removeView(it) } } catch (_: Exception) {}
        bubble = null
        super.onDestroy()
    }
}
