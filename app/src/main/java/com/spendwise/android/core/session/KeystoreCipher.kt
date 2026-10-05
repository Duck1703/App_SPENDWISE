package com.spendwise.android.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES/GCM cipher backed by an Android Keystore key (AD-010).
 *
 * The key never leaves the Android Keystore. The stored blob layout is
 * `IV (12 bytes) || ciphertext || GCM tag`; GCM authentication also detects
 * tampering on decrypt.
 */
@Singleton
class KeystoreCipher @Inject constructor() {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey =
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plain)
        return cipher.iv + ciphertext
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE_BYTES) { "KeystoreCipher blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_SIZE_BYTES),
        )
        return cipher.doFinal(blob, IV_SIZE_BYTES, blob.size - IV_SIZE_BYTES)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "spendwise_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
