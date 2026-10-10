package com.domopi.app.data

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialStoreTest {
    private class MemoryValues : EncryptedValueStore {
        val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String): Boolean { map[key] = value; return true }
        override fun remove(key: String): Boolean { map.remove(key); return true }
    }

    /** Software AES key standing in for the Android Keystore on the JVM. */
    private class SoftwareKey(var key: SecretKey? = null) : MasterKeyProvider {
        override fun getOrCreateEncryptionKey(): SecretKey =
            key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun getExistingDecryptionKey(): SecretKey? = key
    }

    @Test fun tokenRoundTripsEncryptedAndNeverStoredInClear() = runBlocking {
        val values = MemoryValues()
        val store = CredentialStore(values, SoftwareKey())
        assertEquals(CredentialResult.Absent, store.get(Credential.HOUSE_AI_TOKEN))
        assertTrue(store.set(Credential.HOUSE_AI_TOKEN, "segreto-123"))
        assertEquals(CredentialResult.Present("segreto-123"), store.get(Credential.HOUSE_AI_TOKEN))
        val stored = values.map.getValue("house_ai_token")
        assertFalse(stored.contains("segreto"))
        assertEquals("Present([REDACTED])", store.get(Credential.HOUSE_AI_TOKEN).toString())
    }

    @Test fun clearRemovesTheToken() = runBlocking {
        val store = CredentialStore(MemoryValues(), SoftwareKey())
        store.set(Credential.HOUSE_AI_TOKEN, "x")
        assertTrue(store.clear(Credential.HOUSE_AI_TOKEN))
        assertEquals(CredentialResult.Absent, store.get(Credential.HOUSE_AI_TOKEN))
    }

    @Test fun lostKeyOrTamperedDataNeverYieldsAToken() = runBlocking {
        val values = MemoryValues()
        val key = SoftwareKey()
        CredentialStore(values, key).set(Credential.HOUSE_AI_TOKEN, "x")
        // Key gone (e.g. app data restored on a new phone): token must be asked again.
        assertEquals(CredentialResult.KeyInvalidatedOrMissing, CredentialStore(values, SoftwareKey(null)).get(Credential.HOUSE_AI_TOKEN))
        // Different key: authentication tag fails.
        assertEquals(CredentialResult.CorruptOrUndecryptable,
            CredentialStore(values, SoftwareKey().apply { getOrCreateEncryptionKey() }).get(Credential.HOUSE_AI_TOKEN))
        values.map["house_ai_token"] = "not base64!"
        assertEquals(CredentialResult.CorruptOrUndecryptable, CredentialStore(values, key).get(Credential.HOUSE_AI_TOKEN))
    }
}
