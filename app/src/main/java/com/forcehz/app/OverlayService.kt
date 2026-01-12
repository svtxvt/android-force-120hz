package com.forcehz.app

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.sin

/**
 * Foreground Service для 120Hz используя Android Animation Framework.
 * 
 * Техники:
 * 1. ValueAnimator - как Telegram и другие приложения
 * 2. View.invalidate() в цикле анимации
 * 3. Choreographer как backup
 * 4. Hardware Layer для GPU ускорения
 */
class OverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "force_hz_channel"
        
        const val PREF_NAME = "force_hz_prefs"
        const val PREF_SERVICE_ENABLED = "service_enabled"
        const val PREF_SHOW_FPS_OVERLAY = "show_fps_overlay"
        
        const val ACTION_FPS_UPDATE = "com.forcehz.app.FPS_UPDATE"
        const val ACTION_TOGGLE_FPS_OVERLAY = "com.forcehz.app.TOGGLE_FPS_OVERLAY"
        const val EXTRA_CURRENT_FPS = "current_fps"
        
        private const val TARGET_FPS = 120f
    }

    private var windowManager: WindowManager? = null
    private var overlayView: AnimatedOverlayView? = null
    private var fpsOverlayView: TextView? = null
    
    private var mainHandler: Handler? = null
    private var showFpsOverlay = false
    
    private var screenReceiver: BroadcastReceiver? = null
    private var commandReceiver: BroadcastReceiver? = null
    private var isScreenOn = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        loadSettings()
        mainHandler = Handler(Looper.getMainLooper())
        
        registerScreenReceiver()
        registerCommandReceiver()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlay()
        
        if (showFpsOverlay) createFpsOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SERVICE_ENABLED, true)
            .apply()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        unregisterCommandReceiver()
        removeOverlay()
        removeFpsOverlay()
        mainHandler = null

        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SERVICE_ENABLED, false)
            .apply()
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, OverlayService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        showFpsOverlay = prefs.getBoolean(PREF_SHOW_FPS_OVERLAY, false)
    }
    
    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        overlayView?.startAnimation()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        overlayView?.stopAnimation()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive
    }
    
    private fun unregisterScreenReceiver() {
        screenReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        screenReceiver = null
    }
    
    private fun registerCommandReceiver() {
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_TOGGLE_FPS_OVERLAY) {
                    showFpsOverlay = !showFpsOverlay
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_SHOW_FPS_OVERLAY, showFpsOverlay)
                        .apply()
                    
                    if (showFpsOverlay) createFpsOverlay() else removeFpsOverlay()
                }
            }
        }
        
        val filter = IntentFilter(ACTION_TOGGLE_FPS_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }
    
    private fun unregisterCommandReceiver() {
        commandReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        commandReceiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Force 120Hz", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Force 120Hz")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = AnimatedOverlayView(this)

        val layoutParams = WindowManager.LayoutParams().apply {
            width = 1
            height = 1
            x = 0
            y = 0

            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

            format = PixelFormat.TRANSLUCENT
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.stopAnimation()
        try { windowManager?.removeView(overlayView) } catch (e: Exception) { }
        overlayView = null
    }
    
    private fun createFpsOverlay() {
        if (fpsOverlayView != null) return
        
        fpsOverlayView = TextView(this).apply {
            text = "-- FPS"
            setTextColor(Color.GREEN)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(16, 8, 16, 8)
        }
        
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 20
            y = 100
            gravity = Gravity.TOP or Gravity.START
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
        }

        try { windowManager?.addView(fpsOverlayView, layoutParams) } catch (e: Exception) { }
    }
    
    private fun removeFpsOverlay() {
        try { fpsOverlayView?.let { windowManager?.removeView(it) } } catch (e: Exception) { }
        fpsOverlayView = null
    }
    
    private fun updateFpsOverlay(fps: Float) {
        mainHandler?.post {
            fpsOverlayView?.text = "%.0f FPS".format(fps)
            fpsOverlayView?.setTextColor(when {
                fps >= 115 -> Color.GREEN
                fps >= 85 -> Color.YELLOW
                else -> Color.RED
            })
        }
    }
    
    private fun broadcastFps(fps: Float) {
        updateFpsOverlay(fps)
        sendBroadcast(Intent(ACTION_FPS_UPDATE).apply {
            putExtra(EXTRA_CURRENT_FPS, fps)
            setPackage(packageName)
        })
    }

    /**
     * View с бесконечной анимацией через ValueAnimator.
     * Это то, что используют приложения типа Telegram.
     */
    private inner class AnimatedOverlayView(context: Context) : View(context), Choreographer.FrameCallback {
        
        private val paint = Paint()
        private var animationValue = 0f
        private var animator: ValueAnimator? = null
        
        // FPS tracking
        private var frameCount = 0
        private var lastFpsTime = System.currentTimeMillis()
        private var isAnimating = false
        
        // Choreographer для дополнительной гарантии
        private var useChoreographer = true
        
        init {
            // Hardware acceleration
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startAnimation()
        }
        
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }
        
        fun startAnimation() {
            if (isAnimating) return
            isAnimating = true
            
            // Метод 1: ValueAnimator (как в Telegram)
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000 // 3 секунды на полный цикл
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                
                addUpdateListener { animation ->
                    animationValue = animation.animatedValue as Float
                    invalidate() // Триггерит перерисовку каждый кадр
                    
                    // Считаем FPS
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTime >= 500) {
                        val fps = frameCount * 1000f / (now - lastFpsTime)
                        broadcastFps(fps)
                        frameCount = 0
                        lastFpsTime = now
                    }
                }
                
                start()
            }
            
            // Метод 2: Choreographer как backup
            if (useChoreographer) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        fun stopAnimation() {
            isAnimating = false
            animator?.cancel()
            animator = null
            Choreographer.getInstance().removeFrameCallback(this)
        }
        
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            
            // Дополнительный invalidate от Choreographer
            invalidate()
            
            Choreographer.getInstance().postFrameCallback(this)
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            // Комплексное изменение пикселя каждый кадр
            val phase = animationValue
            
            // Синусоидальные компоненты для непредсказуемости
            val r = ((sin(Math.toRadians(phase.toDouble())) + 1) * 5).toInt()
            val g = ((sin(Math.toRadians((phase + 120).toDouble())) + 1) * 5).toInt()
            val b = ((sin(Math.toRadians((phase + 240).toDouble())) + 1) * 5).toInt()
            val a = ((sin(Math.toRadians((phase * 2).toDouble())) + 1) * 5).toInt() + 1
            
            paint.color = Color.argb(a, r, g, b)
            canvas.drawRect(0f, 0f, 1f, 1f, paint)
            
            // Дополнительные свойства View для изменения
            alpha = 0.01f + (sin(Math.toRadians(phase.toDouble())).toFloat() + 1) * 0.005f
            translationX = (sin(Math.toRadians((phase * 3).toDouble())).toFloat()) * 0.1f
            rotation = phase * 0.01f
        }
    }
}
