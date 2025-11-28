package com.gesturecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var startButton: CardView
    private lateinit var stopButton: CardView
    private lateinit var enableAccessibilityButton: CardView

    private var isServiceRunning = false
    private val CAMERA_PERMISSION_CODE = 100
    private val OVERLAY_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hacer que la app use pantalla completa con barra de estado transparente
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        statusText = findViewById(R.id.statusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        enableAccessibilityButton = findViewById(R.id.enableAccessibilityButton)

        startButton.setOnClickListener {
            if (checkPermissions()) {
                if (AccessibilityGestureService.isEnabled()) {
                    startGestureService()
                } else {
                    showAccessibilityDialog()
                }
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            stopGestureService()
        }

        enableAccessibilityButton.setOnClickListener {
            openAccessibilitySettings()
        }

        updateUI()
    }

    private fun checkPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val overlayPermission = Settings.canDrawOverlays(this)

        return cameraPermission && overlayPermission
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!Settings.canDrawOverlays(this)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
                    } else {
                        updateUI()
                    }
                } else {
                    showPermissionDialog()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "✓ Permisos concedidos", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios")
            .setMessage("GestureControl necesita:\n\n" +
                    "• Cámara: Para detectar gestos\n" +
                    "• Superposición: Para funcionar sobre otras apps")
            .setPositiveButton("Conceder") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Activar Accesibilidad")
            .setMessage("Para hacer gestos automáticos:\n\n" +
                    "1. Se abrirá Configuración\n" +
                    "2. Busca 'GestureControl'\n" +
                    "3. Activa el interruptor\n" +
                    "4. Confirma con 'Permitir'")
            .setPositiveButton("Ir") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun startGestureService() {
        val intent = Intent(this, HandGestureService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, "✓ Control activado", Toast.LENGTH_SHORT).show()
    }

    private fun stopGestureService() {
        val intent = Intent(this, HandGestureService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Control desactivado", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasPermissions = checkPermissions()
        val hasAccessibility = AccessibilityGestureService.isEnabled()

        // Actualizar iconos de status
        if (hasPermissions) {
            statusText.text = "✓"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            statusText.text = "✗"
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }

        if (hasAccessibility) {
            accessibilityStatusText.text = "✓"
            accessibilityStatusText.setTextColor(getColor(android.R.color.holo_green_light))
            enableAccessibilityButton.alpha = 0.5f
            enableAccessibilityButton.isEnabled = false
        } else {
            accessibilityStatusText.text = "✗"
            accessibilityStatusText.setTextColor(getColor(android.R.color.holo_red_light))
            enableAccessibilityButton.alpha = 1.0f
            enableAccessibilityButton.isEnabled = true
        }

        // Actualizar botones
        if (hasPermissions && hasAccessibility) {
            if (isServiceRunning) {
                startButton.alpha = 0.5f
                startButton.isEnabled = false
                stopButton.alpha = 1.0f
                stopButton.isEnabled = true
            } else {
                startButton.alpha = 1.0f
                startButton.isEnabled = true
                stopButton.alpha = 0.5f
                stopButton.isEnabled = false
            }
        } else {
            startButton.alpha = 1.0f
            startButton.isEnabled = true
            stopButton.alpha = 0.5f
            stopButton.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}