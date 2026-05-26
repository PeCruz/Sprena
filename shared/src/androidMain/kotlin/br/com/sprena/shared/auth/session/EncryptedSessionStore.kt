package br.com.sprena.shared.auth.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.core.logger.Logger
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Persistência cifrada da sessão. Tink AEAD (AES-256-GCM) com chave no Android Keystore.
 *
 * Em caso de falha de decifragem (corrupção, rotação de chave), [load] retorna null
 * e dispara [clear] defensivamente.
 */
class EncryptedSessionStore(
    private val context: Context,
    private val logger: Logger,
) : SessionStore {
    private val aead: Aead by lazy { buildAead() }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun save(user: SessionUser) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_UID] = encrypt(user.uid)
            prefs[KEY_EMAIL] = encrypt(user.email)
            prefs[KEY_ROLE] = encrypt(user.role.name)
            prefs[KEY_LAST_LOGIN] = encrypt(user.lastLoginEpochMillis.toString())
        }
    }

    // ReturnCount: progressive null-check of 4 cipher fields is clearer as guards than via
    // listOfNotNull tricks. TooGenericExceptionCaught: Tink can throw GeneralSecurityException,
    // base64 decode errors, NumberFormatException, IllegalArgumentException — all handled
    // identically (clear + return null defensively).
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun load(): SessionUser? {
        val prefs = context.sessionDataStore.data.first()
        val uidEnc = prefs[KEY_UID] ?: return null
        val emailEnc = prefs[KEY_EMAIL] ?: return null
        val roleEnc = prefs[KEY_ROLE] ?: return null
        val lastEnc = prefs[KEY_LAST_LOGIN] ?: return null

        return try {
            SessionUser(
                uid = decrypt(uidEnc),
                email = decrypt(emailEnc),
                role = UserRole.valueOf(decrypt(roleEnc)),
                lastLoginEpochMillis = decrypt(lastEnc).toLong(),
            )
        } catch (e: Exception) {
            logger.warn(TAG, "session decrypt failed — clearing", e)
            clear()
            null
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encrypt(plaintext: String): String {
        val cipherBytes = aead.encrypt(plaintext.encodeToByteArray(), null)
        return Base64.encode(cipherBytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decrypt(base64: String): String {
        val cipherBytes = Base64.decode(base64)
        return aead.decrypt(cipherBytes, null).decodeToString()
    }

    private fun buildAead(): Aead {
        AeadConfig.register()
        val handle =
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
        return handle.getPrimitive(Aead::class.java)
    }

    private companion object {
        const val TAG = "EncryptedSessionStore"
        const val DATASTORE_NAME = "session_prefs"
        const val KEYSET_NAME = "sprena_session_keyset"
        const val KEYSET_PREF_FILE = "sprena_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://sprena_session_key"
        val KEY_UID = stringPreferencesKey("uid_enc")
        val KEY_EMAIL = stringPreferencesKey("email_enc")
        val KEY_ROLE = stringPreferencesKey("role_enc")
        val KEY_LAST_LOGIN = stringPreferencesKey("last_login_enc")
    }
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")
