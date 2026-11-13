package com.hzyazilim.betalink.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores Supabase tokens encrypted with a non-exportable Android Keystore AES key. */
class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("betalink_secure_session", Context.MODE_PRIVATE)

    @Volatile private var memorySession: Pair<String, String>? = null

    fun save(accessToken: String, refreshToken: String, persist: Boolean = true) {
        memorySession = accessToken to refreshToken
        if (!persist) {
            preferences.edit().remove(SESSION_KEY).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = JSONObject()
            .put("access", accessToken)
            .put("refresh", refreshToken)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(payload)
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        preferences.edit().putString(SESSION_KEY, encoded).apply()
    }

    fun read(): Pair<String, String>? {
        memorySession?.let { return it }
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            val access = json.getString("access")
            val refresh = json.getString("refresh")
            access to refresh
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        memorySession = null
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "betalink.session.aes"
        private const val SESSION_KEY = "encrypted_tokens"
    }
}
