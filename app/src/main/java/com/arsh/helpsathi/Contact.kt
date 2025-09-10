package com.arsh.helpsathi

import kotlinx.serialization.Serializable

@Serializable
data class Contact(val name: String, val number: String)