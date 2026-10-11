package com.domopi.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Secrets kept encrypted on the phone (ported from WallBox KeystoreCredentialStore). */
enum class Credential(val prefKey: String) {
    HOUSE_AI_TOKEN("house_ai_token"),
    MQTT_PASSWORD("mqtt_password"),
    TINYCAM_PASSWORD("tinycam_password"),
    ADMIN_PIN("admin_pin"),
    EMONCMS_API_KEY("emoncms_api_key"),
}

sealed interface CredentialResult {
    data object Absent : CredentialResult
    data object KeyInvalidatedOrMissing : CredentialResult
    data object CorruptOrUndecryptable : CredentialResult
    data object TemporaryOrProviderError : CredentialResult
    data class Present(val value: String) : CredentialResult {
        override fun toString(): String = "Present([REDACTED])"
    }
}

interface MasterKeyProvider {
    fun getOrCreateEncryptionKey(): SecretKey
    fun getExistingDecryptionKey(): SecretKey?
}

/** Minimal persistence seam so encryption can be unit-tested on the JVM. */
interface EncryptedValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
    fun remove(key: String): Boolean
}

class AndroidKeyStoreMasterKeyProvider : MasterKeyProvider {
    override fun getOrCreateEncryptionKey(): SecretKey {
        getExistingDecryptionKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun getExistingDecryptionKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "domopi_master_key"
    }
}

class SharedPreferencesValueStore(context: Context) : EncryptedValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    // commit() to observe persistence success synchronously.
    override fun write(key: String, value: String): Boolean = prefs.edit().putString(key, value).commit()
    override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()

    private companion object {
        const val PREFS_NAME = "domopi_encrypted_credentials"
    }
}

class CredentialStore(
    private val values: EncryptedValueStore,
    private val masterKeyProvider: MasterKeyProvider = AndroidKeyStoreMasterKeyProvider(),
) {
    constructor(context: Context) : this(SharedPreferencesValueStore(context))

    suspend fun get(credential: Credential): CredentialResult = withContext(Dispatchers.IO) { read(credential) }

    suspend fun set(credential: Credential, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKeyProvider.getOrCreateEncryptionKey())
            val combined = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            values.write(credential.prefKey, Base64.getEncoder().encodeToString(combined))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clear(credential: Credential): Boolean = withContext(Dispatchers.IO) {
        try { values.remove(credential.prefKey) } catch (_: Exception) { false }
    }

    private fun read(credential: Credential): CredentialResult {
        val encoded = values.read(credential.prefKey) ?: return CredentialResult.Absent
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size < GCM_IV_LENGTH_BYTES + 1) return CredentialResult.CorruptOrUndecryptable
            val key = masterKeyProvider.getExistingDecryptionKey() ?: return CredentialResult.KeyInvalidatedOrMissing
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)))
            val plain = cipher.doFinal(combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size))
            CredentialResult.Present(String(plain, Charsets.UTF_8))
        } catch (_: InvalidKeyException) {
            CredentialResult.KeyInvalidatedOrMissing
        } catch (_: AEADBadTagException) {
            CredentialResult.CorruptOrUndecryptable
        } catch (_: GeneralSecurityException) {
            CredentialResult.CorruptOrUndecryptable
        } catch (_: IllegalArgumentException) {
            CredentialResult.CorruptOrUndecryptable
        } catch (_: Exception) {
            CredentialResult.TemporaryOrProviderError
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
