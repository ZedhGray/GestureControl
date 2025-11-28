package com.gesturecontrol

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import java.util.Locale

class VoiceCommandService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var restartListeningRunnable: Runnable? = null

    companion object {
        private var instance: VoiceCommandService? = null
        private var isEnabled = true

        fun getInstance(): VoiceCommandService? = instance

        fun setEnabled(enabled: Boolean) {
            isEnabled = enabled
        }

        fun isVoiceEnabled(): Boolean = isEnabled
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeSpeechRecognizer()
        startListening()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    Log.d("VoiceCommand", "Listo para escuchar")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("VoiceCommand", "Comenzó a hablar")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    Log.d("VoiceCommand", "Terminó de hablar")
                }

                override fun onError(error: Int) {
                    isListening = false
                    Log.d("VoiceCommand", "Error: $error")
                    // Reintentar después de 500ms
                    scheduleRestart()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val command = matches[0].lowercase(Locale.getDefault())
                        Log.d("VoiceCommand", "Comando detectado: $command")
                        processCommand(command)
                    }
                    // Reiniciar escucha
                    scheduleRestart()
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        if (!isEnabled) return

        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e("VoiceCommand", "Error al iniciar: ${e.message}")
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        restartListeningRunnable?.let { handler.removeCallbacks(it) }
        restartListeningRunnable = Runnable {
            if (isEnabled) {
                startListening()
            }
        }
        handler.postDelayed(restartListeningRunnable!!, 500)
    }

    private fun processCommand(command: String) {
        val accessibilityService = AccessibilityGestureService.getInstance()

        if (accessibilityService == null) {
            showToast("Activa el servicio de accesibilidad")
            return
        }

        when {
            // NAVEGACIÓN
            command.contains("siguiente") || command.contains("scroll") -> {
                Log.d("VoiceCommand", "Ejecutando: Siguiente")
                accessibilityService.performSwipeUp()
                showToast("⬆️ Siguiente")
            }

            command.contains("atrás") || command.contains("anterior") -> {
                Log.d("VoiceCommand", "Ejecutando: Atrás")
                accessibilityService.performSwipeDown()
                showToast("⬇️ Atrás")
            }

            // INTERACCIÓN
            command.contains("like") || command.contains("me gusta") -> {
                Log.d("VoiceCommand", "Ejecutando: Like")
                accessibilityService.performDoubleTap()
                showToast("❤️ Like")
            }

            command.contains("pausa") -> {
                Log.d("VoiceCommand", "Ejecutando: Pausa")
                accessibilityService.performTap()
                showToast("⏸️ Pausa")
            }

            command.contains("play") || command.contains("reproduce") -> {
                Log.d("VoiceCommand", "Ejecutando: Play")
                accessibilityService.performTap()
                showToast("▶️ Play")
            }

            command.contains("silencio") || command.contains("mutear") -> {
                Log.d("VoiceCommand", "Ejecutando: Silencio")
                accessibilityService.performVolumeDown()
                showToast("🔇 Silencio")
            }

            // OPCIONALES
            command.contains("compartir") -> {
                Log.d("VoiceCommand", "Ejecutando: Compartir")
                accessibilityService.performTapAtPosition(0.9f, 0.5f) // Botón compartir
                showToast("📤 Compartir")
            }

            command.contains("guardar") || command.contains("favorito") -> {
                Log.d("VoiceCommand", "Ejecutando: Guardar")
                accessibilityService.performLongPress()
                showToast("⭐ Guardar")
            }

            command.contains("comentario") -> {
                Log.d("VoiceCommand", "Ejecutando: Comentarios")
                accessibilityService.performTapAtPosition(0.9f, 0.7f) // Botón comentarios
                showToast("💬 Comentarios")
            }

            else -> {
                Log.d("VoiceCommand", "Comando no reconocido: $command")
            }
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        restartListeningRunnable?.let { handler.removeCallbacks(it) }
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}