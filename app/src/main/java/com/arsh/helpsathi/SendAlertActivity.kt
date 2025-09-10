package com.arsh.helpsathi

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class SendAlertActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("KeypressReceiver", "Worker activity started.")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (hasRequiredPermissions()) {
            fetchLocationAndSendSms()
        } else {
            Log.e("KeypressReceiver", "Required permissions not granted. Cannot send alert.")
            finish() 
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSendSms() {
        
        Toast.makeText(this, "Sending location alert...", Toast.LENGTH_SHORT).show()

        val contacts = StorageManager.loadContacts(this)
        if (contacts.isEmpty()) {
            Log.e("KeypressReceiver", "No contacts to send SMS to.")
            finish()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val customMessage = StorageManager.loadCustomMessage(this)
                    val locationUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    val finalMessage = "$customMessage\nMy current location: $locationUrl"
                    sendSmsToAllContacts(contacts, finalMessage)
                } else {
                    Log.e("KeypressReceiver", "Failed to get location.")
                }
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("KeypressReceiver", "Error getting location: ${e.message}")
                finish()
            }
    }

    private fun sendSmsToAllContacts(contacts: List<Contact>, message: String) {
        val smsManager = this.getSystemService(SmsManager::class.java)
        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.number, null, message, null, null)
                Log.d("KeypressReceiver", "SMS sent to ${contact.name}")
            } catch (e: Exception) {
                Log.e("KeypressReceiver", "Failed to send SMS to ${contact.name}", e)
            }
        }
    }
}