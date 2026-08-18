package com.domopi.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val NODE_RED_LOCAL_IP = stringPreferencesKey("node_red_local_ip")
        val NODE_RED_REMOTE_IP = stringPreferencesKey("node_red_remote_ip")
        val MQTT_LOCAL_IP = stringPreferencesKey("mqtt_local_ip")
        val MQTT_REMOTE_IP = stringPreferencesKey("mqtt_remote_ip")
        val TINYCAM_LOCAL_IP = stringPreferencesKey("tinycam_local_ip")
        val TINYCAM_REMOTE_IP = stringPreferencesKey("tinycam_remote_ip")
    }

    val nodeRedLocalIp: Flow<String> = context.dataStore.data.map { it[NODE_RED_LOCAL_IP] ?: "192.168.1.20" }
    val nodeRedRemoteIp: Flow<String> = context.dataStore.data.map { it[NODE_RED_REMOTE_IP] ?: "100.x.x.x" }

    suspend fun saveNodeRedLocalIp(ip: String) {
        context.dataStore.edit { it[NODE_RED_LOCAL_IP] = ip }
    }
    
    suspend fun saveNodeRedRemoteIp(ip: String) {
        context.dataStore.edit { it[NODE_RED_REMOTE_IP] = ip }
    }

    // Altri metodi per MQTT e Tinycam...
    val mqttLocalIp: Flow<String> = context.dataStore.data.map { it[MQTT_LOCAL_IP] ?: "192.168.1.20" }
    val mqttRemoteIp: Flow<String> = context.dataStore.data.map { it[MQTT_REMOTE_IP] ?: "100.x.x.x" }

    suspend fun saveMqttLocalIp(ip: String) {
        context.dataStore.edit { it[MQTT_LOCAL_IP] = ip }
    }

    suspend fun saveMqttRemoteIp(ip: String) {
        context.dataStore.edit { it[MQTT_REMOTE_IP] = ip }
    }
    
    val tinycamLocalIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_LOCAL_IP] ?: "192.168.1.20" }
    val tinycamRemoteIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_REMOTE_IP] ?: "100.x.x.x" }
}
