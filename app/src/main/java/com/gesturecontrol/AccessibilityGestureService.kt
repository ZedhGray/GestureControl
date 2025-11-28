package com.gesturecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AccessibilityGestureService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        private var instance: AccessibilityGestureService? = null

        fun getInstance(): AccessibilityGestureService? = instance

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // Ya existe - Scroll arriba
    fun performSwipeUp() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.75f
        val endX = screenWidth / 2f
        val endY = screenHeight * 0.25f

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // NUEVO - Scroll abajo (video anterior)
    fun performSwipeDown() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.25f
        val endX = screenWidth / 2f
        val endY = screenHeight * 0.75f

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // NUEVO - Tap simple (pausa/play)
    fun performTap() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = screenWidth / 2f
        val y = screenHeight / 2f

        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // NUEVO - Doble tap (like)
    fun performDoubleTap() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = screenWidth / 2f
        val y = screenHeight / 2f

        val path = Path()
        path.moveTo(x, y)

        // Primer tap
        val gesture1 = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture1, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // Segundo tap después de 100ms
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val gesture2 = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
                        .build()
                    dispatchGesture(gesture2, null, null)
                }, 100)
            }
        }, null)
    }

    // NUEVO - Tap en posición específica
    fun performTapAtPosition(xPercent: Float, yPercent: Float) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = screenWidth * xPercent
        val y = screenHeight * yPercent

        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // NUEVO - Long press (guardar)
    fun performLongPress() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = screenWidth / 2f
        val y = screenHeight / 2f

        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 800)) // 800ms = long press
            .build()

        dispatchGesture(gesture, null, null)
    }

    // NUEVO - Bajar volumen
    fun performVolumeDown() {
        // Usar AudioManager para bajar volumen
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_LOWER,
            0
        )
    }
}