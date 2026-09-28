package com.tobfd.tsuzuki.core.datastore

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** Keyset file and Keystore alias; both are excluded from backups (res/xml of `app`). */
internal const val KEYSET_PREFS_FILE = "tsuzuki_keyset_prefs"
private const val KEYSET_NAME = "tsuzuki_token_keyset"
private const val MASTER_KEY_URI = "android-keystore://tsuzuki_master_key"
private val ASSOCIATED_DATA = "tsuzuki-anilist-token".encodeToByteArray()

/** Encrypts the access token at rest; [TokenCipher] in the app, a fake in tests. */
interface TokenEncryption {
    fun encrypt(plaintext: String): String

    /** Returns null if [ciphertext] can't be decrypted; the session then counts as logged out. */
    fun decrypt(ciphertext: String): String?
}

/**
 * Encrypts the AniList access token with Tink AES-256-GCM. The Tink keyset is itself encrypted with a
 * master key in the Android Keystore, so the token never touches disk in clear text.
 * (`EncryptedSharedPreferences` is deprecated and not used.)
 */
@Singleton
class TokenCipher @Inject constructor(@ApplicationContext private val context: Context) : TokenEncryption {
    // Created on first use, off the main thread (DataStore and login run on the IO dispatcher).
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plaintext: String): String =
        Base64.encodeToString(aead.encrypt(plaintext.encodeToByteArray(), ASSOCIATED_DATA), Base64.NO_WRAP)

    /** Null e.g. after a device restore, where the Keystore key is gone. */
    override fun decrypt(ciphertext: String): String? = try {
        aead.decrypt(Base64.decode(ciphertext, Base64.NO_WRAP), ASSOCIATED_DATA).decodeToString()
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
