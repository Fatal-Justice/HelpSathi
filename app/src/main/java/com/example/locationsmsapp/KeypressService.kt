package com.example.locationsmsapp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

@SuppressLint("AccessibilityPolicy")
class KeypressService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pressCount = 0
    private val pressTimeout = 500L
    private val TAG = "KeypressService"


    private val LOG_INTERVAL = 15000L
    private val loggingRunnable = object : Runnable {
        override fun run() {
            Log.i(TAG, "SERVICE IS ALIVE AND RUNNING in the background.")
            handler.postDelayed(this, LOG_INTERVAL)
        }
    }


    private val resetCounterAndSendSms = Runnable {
        Log.d(TAG, "Resetting press count.")
        if (pressCount == 3) {
            startRecurringSmsService()
        }
        pressCount = 0
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service has been connected and is running.")

        handler.post(loggingRunnable)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                Log.d(TAG, "Volume button pressed. Count: ${pressCount + 1}")
                handler.removeCallbacks(resetCounterAndSendSms)
                pressCount++

                handler.postDelayed(resetCounterAndSendSms, pressTimeout)

                return super.onKeyEvent(event)
            }
        }
        return super.onKeyEvent(event)
    }


    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "Accessibility Service has been disconnected/disabled.")
        handler.removeCallbacks(loggingRunnable)
        return super.onUnbind(intent)
    }

    private fun startRecurringSmsService() {
        val serviceIntent = Intent(this, RecurringSmsService::class.java).apply {
            action = RecurringSmsService.ACTION_START_RECURRING_SMS
            
            
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d(TAG, "Attempting to start RecurringSmsService.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
}