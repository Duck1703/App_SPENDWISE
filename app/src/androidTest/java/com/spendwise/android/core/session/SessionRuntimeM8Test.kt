package com.spendwise.android.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant

/**
 * M8 runtime validation (§18–23): real Android Keystore + encrypted
 * DataStore session persistence on an actual device runtime.
 *
 * Each store gets a unique preferences file (DataStore enforces one
 * active instance per canonical path), and the Keystore alias is
 * deleted in [tearDown] so an invalidation test cannot leak state.
 */
@RunWith(AndroidJUnit4::class)
class SessionRuntimeM8Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val createdFiles = mutableListOf<File>()
    private val createdScopes = mutableListOf<CoroutineScope>()

    private fun newStore(): Pair<DataStoreTokenStore, File> {
        val file = File(context.filesDir, "m8_session_${System.nanoTime()}.preferences_pb")
        createdFiles += file
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        createdScopes += scope
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreTokenStore(dataStore, KeystoreCipher()) to file
    }

    @After
    fun tearDown() {
        createdScopes.forEach { it.cancel() }
        runCatching { keyStore.deleteEntry("spendwise_session_key") }
        createdFiles.forEach { it.delete() }
    }

    // ── §19 Keystore ────────────────────────────────────────────────────

    @Test
    fun keystore_encryptDecryptRoundTrip_onRealKey() {
        val cipher = KeystoreCipher()
        val plain = "eyJhbGciOiJIUzI1NiIs-m8-session".encodeToByteArray()

        val blob = cipher.encrypt(plain)

        assertFalse("ciphertext must not equal plaintext", blob.contentEquals(plain))
        assertTrue("blob must carry the 12-byte GCM IV prefix", blob.size > 12 + plain.size)
        assertArrayEquals(plain, cipher.decrypt(blob))
    }

    @Test
    fun keystore_gcmTagRejectsTampering() {
        val cipher = KeystoreCipher()
        val blob = cipher.encrypt("m8-token".encodeToByteArray())
        blob[blob.lastIndex] = (blob.last().toInt() xor 0x01).toByte()

        val result = runCatching { cipher.decrypt(blob) }

        assertTrue("tampered blob must fail the GCM tag check", result.isFailure)
    }

    @Test
    fun keystore_newCipherInstanceDecryptsSameKeyMaterial() {
        // Same alias → same TEE-held key → a fresh cipher instance (cold
        // start equivalent) decrypts blobs written earlier.
        val blob = KeystoreCipher().encrypt("cold-start".encodeToByteArray())
        assertArrayEquals("cold-start".encodeToByteArray(), KeystoreCipher().decrypt(blob))
    }

    // ── §20 DataStore session persistence ───────────────────────────────

    @Test
    fun tokenStore_saveLoadRoundTrip() = runBlocking {
        val (store, _) = newStore()
        val expiry = Instant.now().plusSeconds(3600).toEpochMilli().let(Instant::ofEpochMilli)

        store.save("token-abc", expiry, username = "ana", userId = "u1")
        val loaded = store.load()

        assertEquals("token-abc", loaded?.token)
        assertEquals(expiry, loaded?.expiresAt)
        assertEquals("ana", loaded?.username)
        assertEquals("u1", loaded?.userId)
    }

    @Test
    fun tokenStore_plaintextTokenNeverTouchesDisk() = runBlocking {
        val (store, file) = newStore()
        val token = "s3cret-m8-token-0123456789"

        store.save(token, Instant.now().plusSeconds(3600), username = "ana", userId = "u1")
        assertTrue(file.exists())
        val disk = file.readBytes()

        assertFalse(
            "plaintext token must never be written to disk",
            String(disk).contains(token),
        )
    }

    @Test
    fun tokenStore_coldStartReadsPersistedSession() = runBlocking {
        val (first, file) = newStore()
        first.save("persisted", Instant.now().plusSeconds(3600), "ana", "u1")
        // Close the first store: process death / cold start equivalent —
        // DataStore requires the old scope cancelled before re-opening.
        createdScopes.last().cancel()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        createdScopes += scope
        val second = DataStoreTokenStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
            KeystoreCipher(),
        )
        val loaded = second.load()

        assertEquals("persisted", loaded?.token)
        assertEquals("u1", loaded?.userId)
    }

    @Test
    fun tokenStore_keyInvalidationReadsAsNoSession_notCrash() = runBlocking {
        val (store, _) = newStore()
        store.save("will-be-orphaned", Instant.now().plusSeconds(3600), "ana", "u1")

        // Keystore key invalidated (e.g. lockscreen change / factory state).
        keyStore.deleteEntry("spendwise_session_key")

        val loaded = store.load()

        assertNull("undecryptable blob must read as signed-out, not crash", loaded)
    }

    @Test
    fun tokenStore_clearTokenDropsCredential() = runBlocking {
        val (store, _) = newStore()
        store.save("gone", Instant.now().plusSeconds(3600), "ana", "u1")

        store.clearToken()

        assertNull(store.load())
    }

    // ── §21 cross-user privacy reset ────────────────────────────────────

    @Test
    fun tokenStore_clearWipesIdentityForNextUser() = runBlocking {
        val (store, _) = newStore()
        store.save("user-a-token", Instant.now().plusSeconds(3600), "ana", "u1")

        // Sign-out for user B signing into the same device.
        store.clear()

        val loaded = store.load()
        assertNull(loaded)
    }
}
