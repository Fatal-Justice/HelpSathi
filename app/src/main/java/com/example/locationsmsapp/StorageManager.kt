package com.example.locationsmsapp

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StorageManager {

    private const val PREFS_NAME = "LocationSmsPrefs"
    private const val KEY_CONTACTS = "contacts_list"
    private const val KEY_CUSTOM_MESSAGE = "custom_message"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun saveContacts(context: Context, contacts: List<Contact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = json.encodeToString(contacts)
        prefs.edit().putString(KEY_CONTACTS, jsonString).apply()
    }

    fun loadContacts(context: Context): MutableList<Contact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CONTACTS, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<MutableList<Contact>>(jsonString)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveCustomMessage(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_MESSAGE, message).apply()
    }

    fun loadCustomMessage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_MESSAGE, "Emergency, please check on me.") ?: "Emergency, please check on me."
    }
}