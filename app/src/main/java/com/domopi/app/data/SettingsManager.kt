package com.domopi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.domopi.app.network.HomeNetworkSettings

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Settings in DataStore; secrets (MQTT and TinyCam passwords, admin PIN, EmonCMS key) only in the
 * encrypted [CredentialStore], never as defaults in the code: the repository is public.
 */
class SettingsManager(private val context: Context, private val credentials: CredentialStore? = null) {
    // null until loadSecrets() has run, so consumers wait instead of using an empty value.
    private val secrets = mapOf(
        Credential.MQTT_PASSWORD to MutableStateFlow<String?>(null),
        Credential.TINYCAM_PASSWORD to MutableStateFlow<String?>(null),
        Credential.ADMIN_PIN to MutableStateFlow<String?>(null),
        Credential.EMONCMS_API_KEY to MutableStateFlow<String?>(null),
    )
    private fun secret(credential: Credential): Flow<String> = secrets.getValue(credential).filterNotNull()

    /**
     * Moves secrets saved in plain DataStore by older versions into the encrypted store, then loads
     * them. Call once at start-up.
     */
    suspend fun loadSecrets() {
        val legacy = mapOf(Credential.MQTT_PASSWORD to DOMOPI_BROKER_PASS,
            Credential.TINYCAM_PASSWORD to TINYCAM_PASS, Credential.ADMIN_PIN to ADMIN_PIN)
        val stored = context.dataStore.data.first()
        for ((credential, key) in legacy) {
            val old = stored[key]
            if (credentials != null && !old.isNullOrEmpty() && credentials.get(credential) is CredentialResult.Absent &&
                credentials.set(credential, old)) {
                context.dataStore.edit { it.remove(key) }
            }
        }
        for ((credential, flow) in secrets) {
            flow.value = (credentials?.get(credential) as? CredentialResult.Present)?.value ?: ""
        }
    }

    private suspend fun saveSecret(credential: Credential, value: String) {
        val store = credentials ?: return
        if (value.isBlank()) store.clear(credential) else store.set(credential, value)
        secrets.getValue(credential).value = value
    }

    companion object {
        val TINYCAM_LOCAL_IP = stringPreferencesKey("tinycam_local_ip")
        val TINYCAM_REMOTE_IP = stringPreferencesKey("tinycam_remote_ip")
        val TINYCAM_PORT = stringPreferencesKey("tinycam_port")
        val TINYCAM_USER = stringPreferencesKey("tinycam_user")
        val TINYCAM_PASS = stringPreferencesKey("tinycam_pass")
        val TINYCAM_CAMERAS = stringPreferencesKey("tinycam_cameras")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val IS_ADMIN_MODE = booleanPreferencesKey("is_admin_mode")
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
        val HOME_NETWORK_DOMAIN = stringPreferencesKey("home_network_domain")
        val HOME_NETWORK_SUBNET = stringPreferencesKey("home_network_subnet")
        val HOUSE_AI_URL = stringPreferencesKey("house_ai_url")
        val DIGITAL_TWIN_PREFIX = stringPreferencesKey("digital_twin_prefix")

        val DOMOPI_BROKER_IP = stringPreferencesKey("domopi_broker_ip")
        val DOMOPI_REMOTE_IP = stringPreferencesKey("domopi_remote_ip")
        val DOMOPI_BROKER_PORT = stringPreferencesKey("domopi_broker_port")
        val DOMOPI_BROKER_USER = stringPreferencesKey("domopi_broker_user")
        val DOMOPI_BROKER_PASS = stringPreferencesKey("domopi_broker_pass")

        val EMONCMS_LOCAL_IP = stringPreferencesKey("emonpi_broker_ip")
        val EMONCMS_REMOTE_IP = stringPreferencesKey("emonpi_remote_ip")
    }

    suspend fun saveDomoPiBroker(localIp: String, remoteIp: String, port: String, user: String, pass: String) {
        context.dataStore.edit {
            it[DOMOPI_BROKER_IP] = localIp
            it[DOMOPI_REMOTE_IP] = remoteIp
            it[DOMOPI_BROKER_PORT] = port
            it[DOMOPI_BROKER_USER] = user
            it.remove(DOMOPI_BROKER_PASS)
        }
        saveSecret(Credential.MQTT_PASSWORD, pass)
    }

    suspend fun saveEmonCms(localIp: String, remoteIp: String, apiKey: String) {
        context.dataStore.edit {
            it[EMONCMS_LOCAL_IP] = localIp
            it[EMONCMS_REMOTE_IP] = remoteIp
        }
        saveSecret(Credential.EMONCMS_API_KEY, apiKey.trim())
    }
    
    val tinycamLocalIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_LOCAL_IP] ?: "192.168.1.50" }.distinctUntilChanged()
    val tinycamRemoteIp: Flow<String> = context.dataStore.data.map { it[TINYCAM_REMOTE_IP] ?: "" }.distinctUntilChanged()
    val tinycamPort: Flow<String> = context.dataStore.data.map { it[TINYCAM_PORT] ?: "8083" }.distinctUntilChanged()
    val tinycamUser: Flow<String> = context.dataStore.data.map { it[TINYCAM_USER] ?: "admin" }.distinctUntilChanged()
    val tinycamPass: Flow<String> = secret(Credential.TINYCAM_PASSWORD)
    /** "Nome=ID, Nome=ID"; the first camera is shown on the dashboard card. */
    val tinycamCameras: Flow<String> = context.dataStore.data.map { it[TINYCAM_CAMERAS] ?: TinyCamCameras.DEFAULT }.distinctUntilChanged()

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[DARK_MODE] }.distinctUntilChanged()
    val isAdminMode: Flow<Boolean> = context.dataStore.data.map { it[IS_ADMIN_MODE] ?: false }.distinctUntilChanged()
    /** Empty when no PIN has been chosen yet: the unlock dialog then asks to set one. */
    val adminPin: Flow<String> = secret(Credential.ADMIN_PIN)
    val houseAiUrl: Flow<String> = context.dataStore.data.map { it[HOUSE_AI_URL] ?: HouseAiRepository.DEFAULT_BASE_URL }.distinctUntilChanged()

    val homeNetwork: Flow<HomeNetworkSettings> = context.dataStore.data.map {
        HomeNetworkSettings(
            domain = it[HOME_NETWORK_DOMAIN] ?: HomeNetworkSettings.DEFAULT_DOMAIN,
            subnet = it[HOME_NETWORK_SUBNET] ?: HomeNetworkSettings.DEFAULT_SUBNET,
        )
    }.distinctUntilChanged()
    val digitalTwinPrefix: Flow<String> = context.dataStore.data.map { it[DIGITAL_TWIN_PREFIX] ?: "zara/interface" }.distinctUntilChanged()

    val domopiIp: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_IP] ?: "192.168.1.20" }.distinctUntilChanged()
    val domopiRemoteIp: Flow<String> = context.dataStore.data.map { it[DOMOPI_REMOTE_IP] ?: "" }.distinctUntilChanged()
    val domopiPort: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_PORT] ?: "1883" }.distinctUntilChanged()
    val domopiUser: Flow<String> = context.dataStore.data.map { it[DOMOPI_BROKER_USER] ?: "domopi" }.distinctUntilChanged()
    /** Empty means anonymous MQTT login. */
    val domopiPass: Flow<String> = secret(Credential.MQTT_PASSWORD)

    val emoncmsIp: Flow<String> = context.dataStore.data.map { it[EMONCMS_LOCAL_IP] ?: "192.168.1.15" }.distinctUntilChanged()
    val emoncmsRemoteIp: Flow<String> = context.dataStore.data.map { it[EMONCMS_REMOTE_IP] ?: "" }.distinctUntilChanged()
    /** EmonCMS read-only API key (My Account → Read API Key). */
    val emoncmsApiKey: Flow<String> = secret(Credential.EMONCMS_API_KEY)

    suspend fun saveTinycam(localIp: String, remoteIp: String, port: String, user: String, pass: String, cameras: String) {
        context.dataStore.edit {
            it[TINYCAM_CAMERAS] = cameras.trim()
            it[TINYCAM_LOCAL_IP] = localIp
            it[TINYCAM_REMOTE_IP] = remoteIp
            it[TINYCAM_PORT] = port
            it[TINYCAM_USER] = user
            it.remove(TINYCAM_PASS)
        }
        saveSecret(Credential.TINYCAM_PASSWORD, pass)
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit {
            it[DARK_MODE] = enabled
        }
    }

    suspend fun saveAdminMode(enabled: Boolean) {
        context.dataStore.edit {
            it[IS_ADMIN_MODE] = enabled
        }
    }

    suspend fun saveAdminPin(pin: String) {
        context.dataStore.edit { it.remove(ADMIN_PIN) }
        saveSecret(Credential.ADMIN_PIN, pin)
    }

    /** Saves only a fully valid pair; returns false and keeps the previous values otherwise. */
    suspend fun saveHomeNetwork(domain: String, subnet: String): Boolean {
        if (!HomeNetworkSettings.isValidDomain(domain) || !HomeNetworkSettings.isValidSubnet(subnet)) return false
        context.dataStore.edit {
            it[HOME_NETWORK_DOMAIN] = domain
            it[HOME_NETWORK_SUBNET] = subnet
        }
        return true
    }

    suspend fun saveHouseAiUrl(url: String) {
        context.dataStore.edit {
            it[HOUSE_AI_URL] = url.trim()
        }
    }

    suspend fun saveDigitalTwinPrefix(prefix: String) {
        context.dataStore.edit {
            it[DIGITAL_TWIN_PREFIX] = prefix
        }
    }
}
