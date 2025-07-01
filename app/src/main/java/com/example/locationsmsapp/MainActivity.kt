package com.example.locationsmsapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build 
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var serviceStatusTextView: TextView

    private lateinit var recurringServiceStatusTextView: TextView
    private lateinit var stopRecurringServiceButton: Button


    private val recurringServiceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecurringSmsService.ACTION_RECURRING_SERVICE_STARTED,
                RecurringSmsService.ACTION_RECURRING_SERVICE_STOPPED -> {
                    updateRecurringServiceStatusUI()
                }
            }
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            
            val essentialPermissionsGranted = permissions.entries
                .filter { it.key == Manifest.permission.ACCESS_FINE_LOCATION || it.key == Manifest.permission.SEND_SMS }
                .all { it.value }

            if (essentialPermissionsGranted) {
                Toast.makeText(this, "Essential permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location and SMS permissions are critical for core functionality.", Toast.LENGTH_LONG).show()
            }

            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
                    Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification permission denied. Service notifications might be affected.", Toast.LENGTH_LONG).show()
                }
            }
        }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        serviceStatusTextView = findViewById(R.id.serviceStatusTextView)

        recurringServiceStatusTextView = findViewById(R.id.recurringServiceStatusTextView)
        stopRecurringServiceButton = findViewById(R.id.stopRecurringServiceButton)

        checkAndRequestInitialPermissions()

        val sendLocationButton: Button = findViewById(R.id.sendLocationButton)
        val manageContactsButton: Button = findViewById(R.id.manageContactsButton)

        val enableServiceButton: Button = findViewById(R.id.enableServiceButton)
        enableServiceButton.setOnClickListener {

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Please allow the app to run in the background for reliability.", Toast.LENGTH_LONG).show()
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }


            Handler(Looper.getMainLooper()).postDelayed({
                val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(accessibilityIntent)
                Toast.makeText(this, "Find 'LocationSmsApp', turn it on, then triple-press a volume button to send an alert.", Toast.LENGTH_LONG).show()
            }, 1000)

        }

        sendLocationButton.setOnClickListener {


            if (RecurringSmsService.Companion.isServiceCurrentlyRunning) {
                Toast.makeText(this, "Recurring SMS service is already active.", Toast.LENGTH_SHORT).show()
            }else{
                checkPermissionsAndSendSms()
            }
        }

        manageContactsButton.setOnClickListener {
            Log.d("AlertSender", "Manage Contacts Button Clicked!")
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        stopRecurringServiceButton.setOnClickListener {
            val stopIntent = Intent(this, RecurringSmsService::class.java).apply {
                action = RecurringSmsService.ACTION_STOP_RECURRING_SMS
            }
            startService(stopIntent)
            RecurringSmsService.Companion.isServiceCurrentlyRunning = false
            updateRecurringServiceStatusUI()
            Toast.makeText(this, "Recurring SMS service stopping...", Toast.LENGTH_SHORT).show()
        }

        updateRecurringServiceStatusUI()
    }
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter().apply {
            addAction(RecurringSmsService.ACTION_RECURRING_SERVICE_STARTED)
            addAction(RecurringSmsService.ACTION_RECURRING_SERVICE_STOPPED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(recurringServiceStateReceiver, intentFilter)
        updateRecurringServiceStatusUI() // Also good to update here in case state changed while paused
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recurringServiceStateReceiver)
    }

    override fun onResume() {
        super.onResume()
        requestDefaultSmsApp()
        updateServiceStatusUI()
        updateRecurringServiceStatusUI()
    }

    @SuppressLint("SetTextI18n")
    private fun updateServiceStatusUI() {
        if (isAccessibilityServiceEnabled()) {
            serviceStatusTextView.text = "Accessibility Service Status: ENABLED"
            serviceStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            serviceStatusTextView.text = "Accessibility Service Status: DISABLED"
            serviceStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateRecurringServiceStatusUI() {
        if (RecurringSmsService.Companion.isServiceCurrentlyRunning) {
            recurringServiceStatusTextView.text = "Recurring SMS: Active"
            recurringServiceStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark)) // Or your R.color.green
            stopRecurringServiceButton.visibility = android.view.View.VISIBLE
        } else {
            recurringServiceStatusTextView.text = "Recurring SMS: Inactive"
            recurringServiceStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // Or your R.color.red
            stopRecurringServiceButton.visibility = android.view.View.GONE
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${KeypressService::class.java.canonicalName}"
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(serviceId, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking accessibility service: ${e.message}")
            return false
        }
        return false
    }

    private fun requestDefaultSmsApp() {
        val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this)
        if (packageName != defaultSmsApp) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            startActivity(intent)
        }
    }

    private fun checkAndRequestInitialPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val filteredPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (filteredPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(filteredPermissions.toTypedArray())
        }
    }

    private fun checkPermissionsAndSendSms() {
        
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {

            startRecurringSmsService()
//            fetchLocationAndSendSms()
        } else {
            Toast.makeText(this, "Please grant Location and SMS permissions first.", Toast.LENGTH_LONG).show()
            
            checkAndRequestInitialPermissions() 
        }
    }

    private fun startRecurringSmsService() {
        val serviceIntent = Intent(this, RecurringSmsService::class.java).apply {
            action = RecurringSmsService.ACTION_START_RECURRING_SMS
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d("MainActivity", "Attempting to start RecurringSmsService.")
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSendSms() {
        val contacts = StorageManager.loadContacts(this)
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts found. Please add contacts in settings.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val customMessage = StorageManager.loadCustomMessage(this)
                    val locationUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    val finalMessage = "$customMessage\nHere is my current location: $locationUrl"

                    sendSmsToAllContacts(contacts, finalMessage)
                } else {
                    Toast.makeText(this, "Failed to get location. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendSmsToAllContacts(contacts: List<Contact>, message: String) {
        val smsManager = this.getSystemService(SmsManager::class.java)
        var messagesSent = 0
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.number, null, message, null, null)
                Log.d("MainActivity", "SMS sent to ${contact.name} at ${contact.number}")
                messagesSent++
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send SMS to ${contact.name}", e)
            }
        }
        if (messagesSent > 0) {
            Toast.makeText(this, "$messagesSent location SMS(s) sent successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS failed to send. Check logs for details.", Toast.LENGTH_LONG).show()
        }
    }
}