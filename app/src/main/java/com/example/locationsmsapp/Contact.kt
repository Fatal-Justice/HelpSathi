package com.example.locationsmsapp

import kotlinx.serialization.Serializable

@Serializable
data class Contact(val name: String, val number: String)