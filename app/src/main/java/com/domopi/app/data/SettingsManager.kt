package com.domopi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val NODE_RED_LOCAL_IP = stringPreferencesKey("node_red_local_ip")
        val NODE_RED_REMOTE_IP = stringPreferencesKey("node_red_remote_ip")
        val NODE_RED_PORT = stringPreferencesKey("node_red_port")
        
        val MQTT_LOCAL_IP = stringPreferencesKey("mqtt_local_ip")
        val MQTT_REMOTE_IP = stringPreferencesKey("mqtt_remote_ip")
        val MQTT_PORT = stringPreferencesKey("mqtt_port")
        
        val TINYCAM_LOCAL_IP = stringPreferencesKey("tinycam_local_ip")
        val TINYCAM_REMOTE_IP = stringPreferencesKey("tinycam_remote_ip")
        val TINYCAM_PORT = stringPreferencesKey("tinycam_port")
        val DARK_MODE = booleanPreferencesKey("dark_mode")

        val DOMOPI_BROKER_IP = stringPreferencesKey("domopi_broker_ip")
        val DOMOPI_BROKER_PORT = stringPreferencesKey("domopi_broker_port")
        val DOMOPI_BROKER_USER = stringPreferencesKey("domopi_broker_user")
        val DOMOPI_BROKER_PASS = stringPreferencesKey("domopi_broker_pass")

        val EMONPI_BROKER_IP = stringPreferencesKey("emonpi_broker_ip")
        val EMONPI_BROKER_PORT = stringPreferencesKey("emonpi_broker_port")
        val EMONPI_BROKER_USER = stringPreferencesKey("emonpi_broker_user")
        val EMONPI_BROKER_PASS = stringPreferencesKey("emonpi_broker_pass")
    }

    val nodeRedLocalIp: Flow<String> = context.dataStore.data.map { it[NODE_RED_LOCAL_IP] ?: "192.168.1.20" }.distinctUntilChanged()
    val nodeRedRemoteIp: Flow<String> = context.dataStore.data.map { it[NODE_RED_REMOTE_IP] ?: "100.x.x.x" }.distinctUntilChanged()
    val nodeRedPort: Flow<String> = context.dataStore.data.map { it[NODE_RED_PORT] ?: "1880" }.distinctUntilChanged()

    suspend fun saveNodeRed(localIp: String, remoteIp: String, port: String) {
        context.dataStore.edit {
            it[NODE_RED_LOCAL_IP] = localIp
            it[NODE_RED_REMOTE_IP] = remoteIp
            it[NODE_RED_PORT] = port
        }
    }

    val mqttLocalIp: Flow<String> = context.dataStore.data.map { it[MQTT_LOCAL_IP] ?: "192.168.1.20" }.distinctUntilChanged()
    val mqttRemoteIp: Flow<String> = context.dataStore.data.map { it[MQTT_REMOTE_IP] ?: "100.x.x.x" }.distinctUntilChanged()
    val mqttPort: Flow<String> = context.dataStore.data.map { it[MQTT_PORT] ?: "1883" }.distinctUntilChanged()

    suspend fun saveMqtt(localIp: String, remoteIp: String, port: String) {
        context.dataStore.edit {
            it[MQTT_LOCAL_IP] = localIp
            it[MQTT_REMOTE_IP] = remoteIp
            it[MQTT_PORT] = port
        }
    }

    suspend fun saveDomoPiBroker(ip: String, port: String, user: String, pass: String) {
        context.dataStore.edit {
            it[DOMOPI_BROKER_IP] = ip
            it[DOMOPI_BROKER_PORT] = port
            it[DOMOPI_BROKER_USER] = user
            it[DOMOPI_BROKER_PASS] = pass
        }
    }

    suspend fun saveEmonPiBroker(ip: String, port: String, user: String, pass: String) {
        context.dataStore.edit {
            it[EMONPI_BROKER_IP] = ip
            it[EMONPI_BROKER_PORT] = port
            it[EMONPI_BROKER_USER] = user
            it[EMONPI_BROKER_PASS] = pass
        }
    }
    
    val tinycamLocalIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_LOCAL_IP] ?: "192.168.1.20" }.distinctUntilChanged()
    val tinycamRemoteIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_REMOTE_IP] ?: "100.x.x.x" }.distinctUntilChanged()
    val tinycamPort: Flow<String> = context.dataStore.data.map { it[TINYCAM_PORT] ?: "8083" }.distinctUntilChanged()

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[DARK_MODE] }.distinctUntilChanged()

    val domopiIp: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_IP] ?: "192.168.1.20" }.distinctUntilChanged()
    val domopiPort: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_PORT] ?: "1883" }.distinctUntilChanged()
    val domopiUser: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_USER] ?: "domopi" }.distinctUntilChanged()
    val domopiPass: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_PASS] ?: "domopimqtt" }.distinctUntilChanged()

    val emonpiIp: Flow<String> = context.dataStore.data.map { it[EMONPI_BROKER_IP] ?: "192.168.1.15" }.distinctUntilChanged()
    val emonpiPort: Flow<String> = context.dataStore.data.map { it[EMONPI_BROKER_PORT] ?: "1883" }.distinctUntilChanged()
    val emonpiUser: Flow<String> = context.dataStore.data.map { it[EMONPI_BROKER_USER] ?: "emonpi" }.distinctUntilChanged()
    val emonpiPass: Flow<String> = context.dataStore.data.map { it[EMONPI_BROKER_PASS] ?: "emonpimqtt2016" }.distinctUntilChanged()

    suspend fun saveTinycam(localIp: String, remoteIp: String, port: String) {
        context.dataStore.edit {
            it[TINYCAM_LOCAL_IP] = localIp
            it[TINYCAM_REMOTE_IP] = remoteIp
            it[TINYCAM_PORT] = port
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit {
            it[DARK_MODE] = enabled
        }
    }
}
