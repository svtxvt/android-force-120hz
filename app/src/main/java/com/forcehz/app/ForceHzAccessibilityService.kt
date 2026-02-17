package com.forcehz.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.core.content.edit
import java.lang.ref.WeakReference

/**
 * Service to force 120Hz refresh rate using invisible overlay animations.
 *
 * It creates two minimal, invisible views (bottom and center) that request rerendering
 * on every VSync frame using Choreographer. This signals the system that screen content
 * is changing, preventing LTPO from dropping the refresh rate to 60Hz.
 */
class ForceHzAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: WeakReference<ForceHzAccessibilityService>? = null
        val instance: ForceHzAccessibilityService?
            get() = instanceRef?.get()
        var isRunning = false

        const val PREFS_NAME = "force_hz_prefs"
        const val PREF_ANIMATION_ENABLED = "animation_enabled"
        const val PREF_SHOW_FPS = "show_fps"
        const val PREF_RESTORE_ON_CONNECT = "restore_on_connect"
        
        const val ACTION_FPS_UPDATE = "com.forcehz.app.FPS_UPDATE"
        const val EXTRA_FPS = "fps"
    }

    private var windowManager: WindowManager? = null
    private var bottomInvisibleView: View? = null // The only active view
    private var fpsView: TextView? = null
    private var mainHandler: Handler? = null
    private var showFps = false
    private var animationActive = false
    private var isScreenOn = true
    private var displayRefreshRate = 120f
    private var screenWidth = 1080
    private var screenHeight = 2400
    
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        isRunning = true
        
        mainHandler = Handler(Looper.getMainLooper())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        detectDisplayInfo()
        registerScreenReceiver()
        
        // Optimize service info to not monitor events
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        showFps = prefs.getBoolean(PREF_SHOW_FPS, false)

        val restoreOnConnect = prefs.getBoolean(PREF_RESTORE_ON_CONNECT, false)
        if (restoreOnConnect) {
            prefs.edit { putBoolean(PREF_RESTORE_ON_CONNECT, false) }
        }

        if (prefs.getBoolean(PREF_ANIMATION_ENABLED, false) || restoreOnConnect) {
            startForceRefresh()
        }
    }
    
    private fun detectDisplayInfo() {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }
            displayRefreshRate = display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 120f
            
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        } catch (e: Exception) {
            displayRefreshRate = 120f
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        stopForceRefresh()
        removeFpsOverlay()
        mainHandler?.removeCallbacksAndMessages(null)
        mainHandler = null
        instanceRef = null
        isRunning = false
    }
    
    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        if (animationActive) {
                            (bottomInvisibleView as? InvisibleLineView)?.resume()
                            if (showFps) createFpsOverlay()
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        (bottomInvisibleView as? InvisibleLineView)?.pause()
                        removeFpsOverlay()
                    }
                }
            }
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }
    
    private fun unregisterScreenReceiver() {
        screenReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        screenReceiver = null
    }
    
    fun getCurrentMode() = 0 // Single mode now
    fun getDisplayRefreshRate() = displayRefreshRate.toInt()
    fun changeMode(mode: Int) { }
    
    fun startForceRefresh() {
        if (animationActive) return
        animationActive = true
        
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREF_ANIMATION_ENABLED, true)
            putBoolean(PREF_RESTORE_ON_CONNECT, false)
        }
        
        createOverlay()
        if (showFps && isScreenOn) createFpsOverlay()
    }
    
    fun stopForceRefresh() {
        animationActive = false
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREF_ANIMATION_ENABLED, false)
            putBoolean(PREF_RESTORE_ON_CONNECT, false)
        }
        removeOverlay()
        removeFpsOverlay()
    }
    
    fun toggleFpsOverlay() {
        showFps = !showFps
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREF_SHOW_FPS, showFps)
        }
        
        if (showFps && animationActive && isScreenOn) createFpsOverlay()
        else removeFpsOverlay()
    }
    
    fun isAnimationEnabled() = animationActive
    
    private fun createOverlay() {
        // View: Invisible bottom line (1px) - at the very bottom
        // Provides a trigger point for the display system without visual clutter
        bottomInvisibleView = InvisibleLineView(this, screenWidth)
        val params = WindowManager.LayoutParams().apply {
            width = screenWidth
            height = 1
            x = 0
            y = screenHeight - 1 // Bottom pixel
            gravity = Gravity.TOP or Gravity.START
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            preferredRefreshRate = displayRefreshRate
        }
        try { windowManager?.addView(bottomInvisibleView, params) } catch (e: Exception) { }
    }
    
    private fun removeOverlay() {
        (bottomInvisibleView as? InvisibleLineView)?.stop()
        try { windowManager?.removeView(bottomInvisibleView) } catch (e: Exception) { }
        bottomInvisibleView = null
    }
    
    private fun createFpsOverlay() {
        if (fpsView != null || !isScreenOn) return
        
        fpsView = TextView(this).apply {
            text = getString(R.string.fps_placeholder)
            setTextColor(Color.GREEN)
            textSize = 11f
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(10, 5, 10, 5)
        }
        
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 12
            y = 100  // Below status bar
            gravity = Gravity.TOP or Gravity.START
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            preferredRefreshRate = displayRefreshRate
        }
        
        try { windowManager?.addView(fpsView, params) } catch (e: Exception) { }
    }
    
    private fun removeFpsOverlay() {
        try { fpsView?.let { windowManager?.removeView(it) } } catch (e: Exception) { }
        fpsView = null
    }
    
    fun updateFps(fps: Float) {
        if (!isScreenOn) return
        
        mainHandler?.post {
            fpsView?.text = getString(R.string.fps_value, fps)
            fpsView?.setTextColor(when {
                fps >= 115 -> Color.GREEN
                fps >= 85 -> Color.YELLOW
                else -> Color.RED
            })
        }
        
        sendBroadcast(Intent(ACTION_FPS_UPDATE).apply {
            putExtra(EXTRA_FPS, fps)
            setPackage(packageName)
        })
    }

    /**
     * Invisible 1px line animation for the bottom
     */
    private inner class InvisibleLineView(
        context: Context,
        private val viewWidth: Int
    ) : View(context), Choreographer.FrameCallback {
        
        private val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }
        
        // Pre-calculated colors to avoid object allocation/calc in onDraw
        private val colors = IntArray(5) { i ->
            Color.argb(i + 1, 0, 0, 0)
        }
        
        private var isRunning = false
        private var frameIndex = 0
        
        // FPS logic
        private var frameCount = 0
        private var lastFpsTime = 0L

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            start()
        }
        
        fun start() {
            if (isRunning) return
            isRunning = true
            lastFpsTime = System.nanoTime()
            frameCount = 0
            Choreographer.getInstance().postFrameCallback(this)
        }
        
        fun stop() {
            isRunning = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
        
        fun pause() {
            stop()
        }
        
        fun resume() {
            start()
        }
        
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning || !isScreenOn) return
            
            // Just request redraw, no heavy logic
            frameIndex++
            
            postInvalidateOnAnimation()
            
            // FPS Logic
            // FPS Logic - only if enabled to save CPU
            if (showFps) {
                frameCount++
                val now = System.nanoTime()
                if (now - lastFpsTime >= 500_000_000L) { // Update every 0.5s
                    updateFps(frameCount * 1_000_000_000f / (now - lastFpsTime))
                    frameCount = 0
                    lastFpsTime = now
                }
            }
            
            Choreographer.getInstance().postFrameCallback(this)
        }
        
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT)
            
            // Changing alpha on a 1px line
            // Invisible to eye (alpha 1-5), but visible to system
            // Changing alpha on a 1px line
            // Invisible to eye (alpha 1-5), but visible to system
            paint.color = colors[frameIndex % 5]
            canvas.drawRect(0f, 0f, viewWidth.toFloat(), 1f, paint)
        }
    }
}
