package com.example.voicecommander

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var serviceRunning = false

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)

        toggleButton.setOnClickListener {
            if (!serviceRunning) {
                if (hasAllPermissions()) {
                    startVoiceService()
                } else {
                    ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
                }
            } else {
                stopVoiceService()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (hasAllPermissions()) {
                startVoiceService()
            } else {
                Toast.makeText(this, "Sabhi permissions zaroori hain", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceListenerService::class.java)
        ContextCompat.startForegroundService(this, intent)
        serviceRunning = true
        statusText.text = "Service: Running (bolo \"command on\")"
        toggleButton.text = "Stop Listening"
    }

    private fun stopVoiceService() {
        val intent = Intent(this, VoiceListenerService::class.java)
        stopService(intent)
        serviceRunning = false
        statusText.text = "Service: Stopped"
        toggleButton.text = "Start Listening"
    }
}
