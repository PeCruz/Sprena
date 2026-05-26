package br.com.sprena.shared.auth.session

/**
 * Persistência local cifrada da sessão.
 *
 * Implementação Android: [EncryptedSessionStore] (Tink AEAD + DataStore Preferences).
 * Tests injetam fakes em memória.
 */
interface SessionStore {
    suspend fun save(user: SessionUser)

    suspend fun load(): SessionUser?

    suspend fun clear()
}
