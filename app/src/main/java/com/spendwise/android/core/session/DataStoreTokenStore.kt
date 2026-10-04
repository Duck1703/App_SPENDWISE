package com.spendwise.android.core.session

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences-DataStore [TokenStore] with the token encrypted via
 * [KeystoreCipher] (AD-010). Only ciphertext touches disk; the store
 * directory is excluded from backup (res/xml backup rules, ARCHITECTURE §10).
 *
 * A corrupt/undecryptable blob (e.g. keystore key invalidated) is treated as
 * "no session" rather than crashing.
 */
@Singleton
class DataStoreTokenStore @Inject constructor(
    @param:SessionDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
) : TokenStore {

    override suspend fun load(): StoredSession? {
        val prefs = dataStore.data.first()
        val ciphertext = prefs[KEY_TOKEN] ?: return null
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: return null
        val token = runCatching {
            cipher.decrypt(Base64.decode(ciphertext, Base64.NO_WRAP))
        }.getOrNull()?.decodeToString() ?: run {
            clearToken()
            return null
        }
        return StoredSession(
            token = token,
            expiresAt = Instant.ofEpochMilli(expiresAt),
            username = prefs[KEY_USERNAME],
        )
    }

    override suspend fun save(token: String, expiresAt: Instant, username: String?) {
        val encoded = Base64.encodeToString(cipher.encrypt(token.encodeToByteArray()), Base64.NO_WRAP)
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = encoded
            prefs[KEY_EXPIRES_AT] = expiresAt.toEpochMilli()
            if (username != null) prefs[KEY_USERNAME] = username else prefs.remove(KEY_USERNAME)
        }
    }

    override suspend fun clearToken() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_EXPIRES_AT)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_EXPIRES_AT)
            prefs.remove(KEY_USERNAME)
        }
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("token_ciphertext")
        val KEY_EXPIRES_AT = longPreferencesKey("token_expires_at_epoch_ms")
        val KEY_USERNAME = stringPreferencesKey("last_username")
    }
}
