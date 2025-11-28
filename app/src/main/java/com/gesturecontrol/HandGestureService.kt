package com.gesturecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class HandGestureService : Service(), LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private lateinit var statusTextView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }

    private var handLandmarker: HandLandmarker? = null

    private var lastGestureTime = 0L
    private val GESTURE_DELAY = 800L // 800ms entre gestos

    // Para detectar pinch (pellizco)
    private var wasPinched = false
    private val PINCH_THRESHOLD = 0.05f // Dedos juntos si están a menos de 5% de distancia
    private val RELEASE_THRESHOLD = 0.12f // Dedos separados si están a más de 12%

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

        initializeHandLandmarker()
        createOverlay()
        startCamera()
    }

    private fun initializeHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)

        } catch (e: Exception) {
            e.printStackTrace()
            handler.post {
                updateStatus("❌ Error: falta modelo")
            }
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "gesture_channel")
            .setContentTitle("GestureControl activo 👌")
            .setContentText("Junta pulgar e índice, luego sepáralos")
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

        updateStatus("👌 Listo")
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
                processImageProxy(imageProxy)
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

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)

        if (bitmap != null && handLandmarker != null) {
            val mpImage = BitmapImageBuilder(bitmap).build()

            try {
                val result = handLandmarker!!.detect(mpImage)
                detectHandGesture(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        imageProxy.close()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null

            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun detectHandGesture(result: HandLandmarkerResult) {
        val currentTime = System.currentTimeMillis()

        // No detectar si acabamos de hacer un gesto
        if (currentTime - lastGestureTime < GESTURE_DELAY) {
            return
        }

        if (result.landmarks().isEmpty()) {
            // No hay mano detectada
            wasPinched = false
            return
        }

        val handLandmarks = result.landmarks()[0]

        // Obtener posiciones de pulgar e índice
        val thumbTip = handLandmarks[4]  // Punta del pulgar
        val indexTip = handLandmarks[8]  // Punta del índice

        // Calcular distancia entre pulgar e índice
        val distance = sqrt(
            (thumbTip.x() - indexTip.x()) * (thumbTip.x() - indexTip.x()) +
                    (thumbTip.y() - indexTip.y()) * (thumbTip.y() - indexTip.y())
        )

        // Detectar si los dedos están juntos (pinch)
        val isPinching = distance < PINCH_THRESHOLD

        // Detectar si los dedos se separaron después de estar juntos
        val isReleased = distance > RELEASE_THRESHOLD

        // Lógica del gesto
        if (isPinching && !wasPinched) {
            // Dedos se juntaron
            wasPinched = true
            handler.post { updateStatus("👌 Juntos!") }
        } else if (isReleased && wasPinched) {
            // Dedos se separaron después de estar juntos = GESTO COMPLETO
            performSwipe()
            wasPinched = false
        } else if (wasPinched && distance > PINCH_THRESHOLD && distance < RELEASE_THRESHOLD) {
            // Dedos están juntos pero no completamente separados
            handler.post { updateStatus("✌️ Sepáralos") }
        } else if (!wasPinched && !isPinching) {
            // Estado normal
            handler.post { updateStatus("👌 Junta dedos") }
        }
    }

    private fun performSwipe() {
        val currentTime = System.currentTimeMillis()
        lastGestureTime = currentTime

        handler.post {
            updateStatus("🎬 ¡SCROLL!")

            val accessibilityService = AccessibilityGestureService.getInstance()
            if (accessibilityService != null) {
                accessibilityService.performSwipeUp()
            } else {
                updateStatus("✗ Activar Accesibilidad")
            }

            handler.postDelayed({
                updateStatus("👌 Listo")
            }, 1000)
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

        handLandmarker?.close()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}