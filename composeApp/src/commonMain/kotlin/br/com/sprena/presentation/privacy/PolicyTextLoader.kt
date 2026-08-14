package br.com.sprena.presentation.privacy

import br.com.sprena.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Carrega o texto da política de privacidade.
 *
 * É interface para que o ViewModel seja testável em `commonTest` sem o runtime de
 * Compose Resources.
 */
fun interface PolicyTextLoader {
    suspend fun load(): String
}

/** Impl real: lê o arquivo embarcado em `composeResources/files`. */
class ComposeResourcePolicyTextLoader : PolicyTextLoader {
    @OptIn(ExperimentalResourceApi::class)
    override suspend fun load(): String = Res.readBytes(POLICY_PATH).decodeToString()

    private companion object {
        const val POLICY_PATH = "files/privacy-policy.md"
    }
}
