package com.example.voicecommander

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat

class VoiceListenerService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isActive = false
    private var isDestroyed = false

    private val channelId = "voice_commander_channel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, buildNotification("Sun raha hoon..."))
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isDestroyed = true
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun startListening() {
        if (isDestroyed) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.lowercase()?.trim()
                if (!spoken.isNullOrEmpty()) {
                    handleSpokenText(spoken)
                }
                restartListening()
            }

            override fun onError(error: Int) {
                restartListening()
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e("VoiceListenerService", "startListening failed", e)
            restartListening()
        }
    }

    private fun restartListening() {
        if (isDestroyed) return
        handler.postDelayed({ startListening() }, 500)
    }

    private fun handleSpokenText(spoken: String) {
        Log.d("VoiceListenerService", "Heard: $spoken")

        if (spoken.contains("command on")) {
            isActive = true
            updateNotification("Active - bolo: open <app> ya call <naam>")
            return
        }
        if (spoken.contains("command off")) {
            isActive = false
            updateNotification("Suspend - bolo \"command on\" activate karne ke liye")
            return
        }

        if (!isActive) return

        when {
            spoken.startsWith("open ") -> {
                val appName = spoken.removePrefix("open ").trim()
                openApp(appName)
            }
            spoken.startsWith("call ") -> {
                val contactName = spoken.removePrefix("call ").trim()
                callContact(contactName)
            }
        }
    }

    private fun openApp(spokenName: String) {
        val pm = packageManager
        val launchableApps = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }

        val match = launchableApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(spokenName) || spokenName.contains(label)
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                updateNotification("Opened: ${pm.getApplicationLabel(match)}")
                return
            }
        }
        updateNotification("App nahi mila: $spokenName")
    }

    private fun callContact(spokenName: String) {
        val resolver = contentResolver
        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )

        var phoneNumber: String? = null
        cursor?.use {
            while (it.moveToNext()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val name = it.getString(nameIndex)?.lowercase() ?: continue
                if (name.contains(spokenName) || spokenName.contains(name)) {
                    val contactId = it.getString(idIndex)
                    val phoneCursor = resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId), null
                    )
                    phoneCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            phoneNumber = pc.getString(
                                pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            )
                        }
                    }
                    if (phoneNumber != null) break
                }
            }
        }

        if (phoneNumber != null) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(callIntent)
                updateNotification("Calling: $spokenName")
            } catch (e: SecurityException) {
                updateNotification("Call permission nahi hai")
            }
        } else {
            updateNotification("Contact nahi mila: $spokenName")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Voice Commander", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Voice Commander")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(text))
    }
}
