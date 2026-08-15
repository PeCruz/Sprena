package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.DataExport
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.time.Clock
import br.com.sprena.shared.core.time.toIso8601Utc
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DATE_LENGTH = 10

/**
 * Monta o arquivo de exportação dos dados do titular (LGPD art. 18, V — portabilidade).
 *
 * **O que entra:** identidade da conta, perfil autodeclarado com CPF e telefone
 * **completos** (mascarar aqui não seria portabilidade — o destinatário é o dono do
 * dado) e a trilha de consentimento.
 *
 * **O que nunca entra:** qualquer documento de `sport_clients`. São dados de terceiros
 * sob responsabilidade do operador, e exportá-los pela porta de "meus dados" seria um
 * vazamento com aparência de direito. Por isso este use case não recebe
 * `SportClientRepository` — a omissão é a garantia, não um esquecimento.
 *
 * Também ficam de fora token do Firebase, keyset do Tink, conteúdo do `session_prefs` e
 * token do App Check: são credenciais, não dados pessoais do titular.
 */
class ExportMyDataUseCase(
    private val profileRepository: UserProfileRepository,
    private val consentRepository: ConsentRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(): Result<DataExport> {
        val session =
            sessionStore.load()
                ?: return Result.failure(IllegalStateException("sem sessao"))

        return profileRepository
            .current(session.uid)
            .mapCatching { profile ->
                requireNotNull(profile) { "conta sem perfil" }
                // Falha no histórico degrada, não derruba: o essencial do direito de acesso
                // é o perfil, e um arquivo sem a trilha ainda é entregável.
                val history =
                    consentRepository.history(session.uid).getOrElse { error ->
                        logger.warn(TAG, "consent history unavailable in export", error)
                        emptyList()
                    }
                build(profile, history)
            }.onFailure { logger.warn(TAG, "export failed", it) }
    }

    private fun build(
        profile: UserProfile,
        history: List<ConsentRecord>,
    ): DataExport {
        val nowIso = toIso8601Utc(clock.nowEpochMillis())

        val payload =
            JsonObject(
                mapOf(
                    "formatoVersao" to JsonPrimitive(FORMAT_VERSION),
                    "exportadoEm" to JsonPrimitive(nowIso),
                    "conta" to
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive(profile.uid),
                                "email" to JsonPrimitive(profile.email),
                                "papel" to JsonPrimitive(profile.role.name),
                                "papelDescricao" to JsonPrimitive(profile.role.displayName),
                            ),
                        ),
                    "perfil" to
                        JsonObject(
                            mapOf(
                                "nome" to nullable(profile.name),
                                "apelido" to nullable(profile.apelido),
                                "cpf" to nullable(profile.cpf),
                                "telefone" to nullable(profile.phone),
                                "modalidades" to
                                    JsonArray(profile.modalities.map { JsonPrimitive(it.name) }),
                            ),
                        ),
                    "consentimento" to
                        JsonObject(
                            mapOf(
                                "historico" to
                                    JsonArray(
                                        history.map { record ->
                                            JsonObject(
                                                mapOf(
                                                    "versao" to JsonPrimitive(record.policyVersion),
                                                    "aceitoEm" to
                                                        JsonPrimitive(
                                                            toIso8601Utc(record.acceptedAtEpochMillis),
                                                        ),
                                                ),
                                            )
                                        },
                                    ),
                            ),
                        ),
                    "observacoes" to JsonPrimitive(NOTES),
                ),
            )

        return DataExport(
            fileName = "sprena-meus-dados-${nowIso.take(DATE_LENGTH)}.json",
            json = json.encodeToString(JsonObject.serializer(), payload),
        )
    }

    private fun nullable(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull

    private companion object {
        const val TAG = "ExportMyData"
        const val FORMAT_VERSION = "1"

        val json = Json { prettyPrint = true }

        const val NOTES =
            "Dados financeiros, comandas e tarefas não constam desta exportação porque, nesta " +
                "versão do aplicativo, não são armazenados no servidor. Os clientes cadastrados " +
                "por você também não constam: são dados de terceiros, sob responsabilidade do " +
                "operador da conta."
    }
}
