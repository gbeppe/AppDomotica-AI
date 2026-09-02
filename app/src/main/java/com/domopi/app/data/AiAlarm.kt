@file:OptIn(InternalSerializationApi::class)
package com.domopi.app.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiAlarm(
    val stato: String = "",
    val motivo: String = "",
    @SerialName("elementi_mancanti")
    val elementiMancanti: List<String> = emptyList(),
    val timestamp: Long = 0,
)
