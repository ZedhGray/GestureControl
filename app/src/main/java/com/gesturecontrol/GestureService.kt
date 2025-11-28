package com.gesturecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureService : Service(), LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private lateinit var statusTextView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private var lastGestureTime = 0L
    private val GESTURE_DELAY = 1500L // 1.5 segundos entre gestos

    // Para detectar parpadeo doble
    private var lastBlinkTime = 0L
    private var blinkCount = 0
    private val DOUBLE_BLINK_WINDOW = 800L // Ventana de 800ms para detectar 2 parpadeos

    private val handler = Handler(Looper.getMainLooper())

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cameraExecutor = Executors.newSingleThreadExecutor()

        createNotificationChannel()
        startForeground(1, createNotification())

        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        createOverlay()
        startCamera()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "gesture_channel")
            .setContentTitle("GestureControl activo 👁️")
            .setContentText("Abre la boca o parpadea 2 veces")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gesture_channel",
                "Gesture Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = 20
        layoutParams.y = 100

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        statusTextView = overlayView!!.findViewById(R.id.overlayStatus)

        windowManager.addView(overlayView, layoutParams)

        updateStatus("👁️ Listo")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    faceDetector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                detectGestures(faces[0])
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectGestures(face: com.google.mlkit.vision.face.Face) {
        val currentTime = System.currentTimeMillis()

        // No detectar si acabamos de hacer un gesto
        if (currentTime - lastGestureTime < GESTURE_DELAY) {
            return
        }

        // GESTO 1: Boca abierta (más fácil de detectar)
        val mouthBottom = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM)
        val mouthTop = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)

        if (mouthBottom != null && mouthTop != null) {
            val mouthOpenDistance = kotlin.math.abs(mouthBottom.position.y - mouthTop.position.y)

            // Si la boca está bien abierta (más de 25 píxeles de distancia)
            if (mouthOpenDistance > 25) {
                performSwipe("😮 Boca!")
                return
            }
        }

        // GESTO 2: Parpadeo doble
        val leftEyeOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 1.0f

        // Detectar si ambos ojos están cerrados (parpadeo)
        val isBothEyesClosed = leftEyeOpen < 0.3f && rightEyeOpen < 0.3f

        if (isBothEyesClosed) {
            // Si es el primer parpadeo o pasó mucho tiempo desde el último
            if (currentTime - lastBlinkTime > DOUBLE_BLINK_WINDOW) {
                blinkCount = 1
                lastBlinkTime = currentTime
            }
            // Si es un segundo parpadeo dentro de la ventana de tiempo
            else if (blinkCount == 1) {
                blinkCount = 0
                performSwipe("😉😉 Doble!")
                return
            }
        }
    }

    private fun performSwipe(gestureText: String) {
        val currentTime = System.currentTimeMillis()
        lastGestureTime = currentTime

        handler.post {
            updateStatus("✓ $gestureText")

            val accessibilityService = AccessibilityGestureService.getInstance()
            if (accessibilityService != null) {
                accessibilityService.performSwipeUp()
            } else {
                updateStatus("✗ Activar Accesibilidad")
            }

            handler.postDelayed({
                updateStatus("👁️ Listo")
            }, 1200)
        }
    }

    private fun updateStatus(text: String) {
        handler.post {
            statusTextView.text = text
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
        cameraExecutor.shutdown()
        faceDetector.close()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}