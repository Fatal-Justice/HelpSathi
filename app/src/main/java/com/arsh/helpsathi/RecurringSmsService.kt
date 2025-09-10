package com.arsh.helpsathi

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority


class RecurringSmsService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var handler: Handler
    private lateinit var smsRunnable: Runnable

    private var isServiceRunning = false

    companion object {
        @Volatile @JvmStatic var isServiceCurrentlyRunning = false
        const val ACTION_START_RECURRING_SMS = "com.example.locationsmsapp.action.START_RECURRING_SMS"
        const val ACTION_STOP_RECURRING_SMS = "com.example.locationsmsapp.action.STOP_RECURRING_SMS"
        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "RecurringSmsChannel"
        private const val SMS_INTERVAL_MS = 5 * 60 * 1000L 
        private const val TAG = "RecurringSmsService"

        const val ACTION_RECURRING_SERVICE_STARTED = "com.example.locationsmsapp.action.RECURRING_SERVICE_STARTED"
        const val ACTION_RECURRING_SERVICE_STOPPED = "com.example.locationsmsapp.action.RECURRING_SERVICE_STOPPED"

    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_RECURRING_SMS -> {
                if (!isServiceRunning) {
                    startForegroundServiceWithNotification()
                    setupSmsRunnable()
                    handler.post(smsRunnable) 
                    isServiceRunning = true
                    Companion.isServiceCurrentlyRunning = true
                    val startedIntent = Intent(ACTION_RECURRING_SERVICE_STARTED)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(startedIntent)
                    Log.d(TAG, "Recurring SMS started and service is running")
                }
            }
            ACTION_STOP_RECURRING_SMS -> {
                stopRecurringSms()
            }
        }
        return START_STICKY 
    }

    private fun setupSmsRunnable() {
        smsRunnable = Runnable {
            if (!isServiceRunning) return@Runnable 

            Log.d(TAG, "Attempting to send recurring SMS.")
            fetchLocationAndSendRecurringSms()
            
            handler.postDelayed(smsRunnable, SMS_INTERVAL_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSendRecurringSms() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions (Location/SMS) not granted. Cannot send SMS.")
            return
        }

        val contacts = StorageManager.loadContacts(this)
        if (contacts.isEmpty()) {
            Log.e(TAG, "No contacts to send SMS to. Skipping this interval.")
            return
        }

        val customMessage = StorageManager.loadCustomMessage(this)

        Log.d(TAG, "Fetching location...")
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val finalMessage: String
                if (location != null) {
                    val locationUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    finalMessage = "$customMessage\nMy current location: $locationUrl"
                    Log.d(TAG, "Location fetched successfully. Sending SMS with location.")
                } else {
                    finalMessage = customMessage
                    Log.e(TAG, "Failed to get location (location is null). Sending SMS without location.")
                }
                sendSmsToAllContacts(contacts, finalMessage)
            }
            .addOnFailureListener { e ->
                val finalMessage = customMessage
                Log.e(TAG, "Error getting location: ${e.message}. Sending SMS without location.")
                sendSmsToAllContacts(contacts, finalMessage)
            }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendSmsToAllContacts(contacts: List<Contact>, message: String) {
        
        
        val smsManager = this.getSystemService(SmsManager::class.java)
        contacts.forEach { contact ->
            try {
                
                smsManager.sendTextMessage(contact.number, null, message, null, null)
                Log.d(TAG, "Recurring SMS sent to ${contact.name} (${contact.number})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send recurring SMS to ${contact.name}", e)
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val stopSelfIntent = Intent(this, RecurringSmsService::class.java).apply {
            action = ACTION_STOP_RECURRING_SMS
        }
        val pendingIntentStop = PendingIntent.getService(
            this, 0, stopSelfIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location SMS Active")
            .setContentText("Sending location updates every 5 minutes.")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingIntentStop) 
            .setOngoing(true)
            
            .build()

        
        notification.flags = notification.flags or NotificationCompat.FLAG_ONGOING_EVENT

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service started in foreground.")
    }

    private fun stopRecurringSms() {

        Log.d(TAG, "Stopping recurring SMS")
        Companion.isServiceCurrentlyRunning = false
        val stoppedIntent = Intent(ACTION_RECURRING_SERVICE_STOPPED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(stoppedIntent)
        isServiceRunning = false
        handler.removeCallbacks(smsRunnable)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf() 
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recurring SMS Service"
            val descriptionText = "Channel for recurring location SMS updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT 
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created/updated with importance: $importance")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null 
    }

    override fun onDestroy() {
        super.onDestroy()
        Companion.isServiceCurrentlyRunning = false
        val stoppedIntent = Intent(ACTION_RECURRING_SERVICE_STOPPED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(stoppedIntent)
        if (isServiceRunning) {
             handler.removeCallbacks(smsRunnable)
        }
        Log.d(TAG, "Service Destroyed")
    }
}
