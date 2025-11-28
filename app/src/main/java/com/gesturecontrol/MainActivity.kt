package com.gesturecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var enableAccessibilityButton: Button
    private lateinit var instructionsText: TextView

    private var isServiceRunning = false
    private val CAMERA_PERMISSION_CODE = 100
    private val OVERLAY_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        enableAccessibilityButton = findViewById(R.id.enableAccessibilityButton)
        instructionsText = findViewById(R.id.instructionsText)

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
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
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
                    "• Cámara: Para detectar cuando mueves la cabeza\n" +
                    "• Superposición: Para mostrarse sobre TikTok\n\n" +
                    "Sin estos permisos la app no puede funcionar.")
            .setPositiveButton("Conceder") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Activar Accesibilidad")
            .setMessage("Para hacer swipes automáticos en TikTok, necesitas activar el servicio de accesibilidad:\n\n" +
                    "1. Se abrirá Configuración\n" +
                    "2. Busca 'GestureControl'\n" +
                    "3. Activa el interruptor\n" +
                    "4. Confirma con 'Permitir'")
            .setPositiveButton("Ir a Configuración") { _, _ ->
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
        val intent = Intent(this, GestureService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, "Control por gestos activado", Toast.LENGTH_SHORT).show()
    }

    private fun stopGestureService() {
        val intent = Intent(this, GestureService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Control por gestos desactivado", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasPermissions = checkPermissions()
        val hasAccessibility = AccessibilityGestureService.isEnabled()

        // Estado de permisos básicos
        if (hasPermissions) {
            statusText.text = "✓ Permisos básicos: OK"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "✗ Faltan permisos básicos"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }

        // Estado de accesibilidad
        if (hasAccessibility) {
            accessibilityStatusText.text = "✓ Accesibilidad: Activada"
            accessibilityStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
            enableAccessibilityButton.isEnabled = false
        } else {
            accessibilityStatusText.text = "✗ Accesibilidad: Desactivada"
            accessibilityStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
            enableAccessibilityButton.isEnabled = true
        }

        // Botones de control
        if (hasPermissions && hasAccessibility) {
            if (isServiceRunning) {
                startButton.text = "Servicio activo ✓"
                startButton.isEnabled = false
                stopButton.isEnabled = true
            } else {
                startButton.text = "INICIAR CONTROL"
                startButton.isEnabled = true
                stopButton.isEnabled = false
            }
        } else {
            if (!hasPermissions) {
                startButton.text = "CONCEDER PERMISOS"
            } else {
                startButton.text = "ACTIVAR ACCESIBILIDAD"
            }
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}