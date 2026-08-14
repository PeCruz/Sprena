package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.ProfileSaveResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * TDD — gravação dos campos autodeclarados (F1.6a).
 *
 * O patch só carrega o que vai para `user_profiles/{uid}`. Campo em branco é estado
 * legítimo ("não informado") e vira `null`; campo preenchido e malformado é recusado
 * antes de tocar a rede.
 */
class SaveMyProfileUseCaseTest {
    private val session =
        SessionUser(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            lastLoginEpochMillis = 1_000L,
        )

    @Test
    fun `sem sessao nao grava nada`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(null), NoOpLogger())

            assertIs<ProfileSaveResult.Failed>(useCase(patch()))
            assertNull(repo.lastSavedUid)
        }

    @Test
    fun `CPF com digitos a menos e recusado antes de tocar a rede`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase(patch(cpf = "123"))

            assertIs<ProfileSaveResult.Invalid>(result)
            assertNull(repo.lastSavedUid)
        }

    @Test
    fun `telefone malformado e recusado antes de tocar a rede`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            assertIs<ProfileSaveResult.Invalid>(useCase(patch(phone = "119")))
            assertNull(repo.lastSavedUid)
        }

    @Test
    fun `campos em branco viram null — nao informado e estado legitimo`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase(patch(apelido = "   ", cpf = "", phone = ""))

            assertIs<ProfileSaveResult.Saved>(result)
            val saved = repo.lastSavedPatch!!
            assertNull(saved.apelido)
            assertNull(saved.cpf)
            assertNull(saved.phone)
        }

    @Test
    fun `grava normalizando CPF e telefone para so digitos`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase(patch(cpf = "123.456.789-00", phone = "(11) 98765-4321"))

            assertIs<ProfileSaveResult.Saved>(result)
            assertEquals("uid-1", repo.lastSavedUid)
            assertEquals("12345678900", repo.lastSavedPatch?.cpf)
            assertEquals("11987654321", repo.lastSavedPatch?.phone)
        }

    @Test
    fun `falha do repositorio vira Failed com mensagem`() =
        runTest {
            val repo =
                FakeProfileRepo(
                    result = Result.success(null),
                    saveResult = Result.failure(IllegalStateException("offline")),
                )
            val useCase = SaveMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            assertIs<ProfileSaveResult.Failed>(useCase(patch()))
        }

    private fun patch(
        apelido: String? = "Pe",
        cpf: String? = "12345678900",
        phone: String? = "11987654321",
        modalities: List<SportModality> = listOf(SportModality.VOLEI),
    ) = ProfilePatch(apelido = apelido, cpf = cpf, phone = phone, modalities = modalities)
}
