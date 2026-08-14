# F1.5 — Baseline LGPD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o baseline LGPD do Sprena — gate de consentimento no cold start, política de privacidade versionada e embarcada, e masking de CPF na exibição.

**Architecture:** Um módulo novo `shared/privacy` (domínio puro em commonMain + implementação Firestore em androidMain) guarda o aceite em `user_consents/{uid}` com subcoleção `history` append-only. O NavGraph ganha uma rota `consent` decidida junto com o start destination — gate fail-closed, a Home não monta sem aceite confirmado. O masking é uma função pura em `shared/core/privacy` consumida pelo `ClientDetailViewModel`, com revelação restrita a ADM/MOD decidida no ViewModel.

**Tech Stack:** Kotlin Multiplatform 2.1.10, Compose Multiplatform 1.7.3, Koin 4.0.2, Firebase Firestore (BOM 33.7.0), Compose Resources, kotlin-test + Turbine, `@firebase/rules-unit-testing` no emulador.

**Spec:** [`docs/superpowers/specs/2026-08-12-f1-5-lgpd-baseline-design.md`](../specs/2026-08-12-f1-5-lgpd-baseline-design.md)

## Global Constraints

- **Branch:** `feature/f1-5-lgpd-baseline` (já criada a partir do master atualizado). Nunca commitar em `master`.
- **TDD obrigatório:** o teste vem antes do código funcional, e é executado para falhar antes de existir implementação.
- **Nada de Firebase em `shared/commonMain`** — imports do Firebase só em `shared/androidMain` ou `composeApp/androidMain` (restrição 13 do CLAUDE.md).
- **Nada de lógica de negócio em Composable** (restrição 1); decisão de role vive no ViewModel.
- **Estado imutável:** sempre `copy()` num novo state, nunca mutação (restrição 9).
- **Versões só via `gradle/libs.versions.toml`** — nunca inline nos `build.gradle.kts`.
- **Nomenclatura MVI:** `{Feature}Screen`, `{Feature}ViewModel`, `{Feature}State`, `{Feature}Intent`, `{Feature}Effect`.
- **`PrivacyPolicy.VERSION` = `"2026-08-12"`** — mesmo valor em todos os pontos do plano.
- **Coleção Firestore:** `user_consents` (doc id = uid); subcoleção `history` (doc id = policyVersion).
- **Idioma:** código e comentários seguem o padrão do repo (comentários e strings de UI em PT-BR).
- **JDK:** build Gradle em 17; testes de rules no emulador exigem JDK 21+ (`java -version` deve mostrar 21+ ao rodar a Task 3).
- **Commits:** formato `tipo(escopo): descrição`, um commit ao fim de cada task.

### Comandos de verificação

```bash
./gradlew :shared:testDebugUnitTest --no-daemon
./gradlew :composeApp:testDebugUnitTest --no-daemon
./gradlew ktlintCheck --no-daemon
./gradlew detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug --no-daemon
./gradlew :composeApp:assembleDebug --no-daemon
cd tools/firestore-rules-tests && npm run test:emulator
```

---

### Task 1: `CpfMasker` — masking e formatação de CPF

Função pura, sem dependências. É a base do que a Task 9 consome.

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/privacy/CpfMasker.kt`
- Test: `shared/src/commonTest/kotlin/br/com/sprena/shared/core/privacy/CpfMaskerTest.kt`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `fun maskCpf(raw: String): String` — em `br.com.sprena.shared.core.privacy`
  - `fun formatCpf(raw: String): String` — mesmo pacote

- [ ] **Step 1: Escrever o teste que falha**

Criar `shared/src/commonTest/kotlin/br/com/sprena/shared/core/privacy/CpfMaskerTest.kt`:

```kotlin
package br.com.sprena.shared.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — masking de CPF para exibição (F1.5).
 *
 * Regra: entrada válida mostra só os 3 últimos dígitos do corpo + os 2 do DV.
 * Entrada inválida NUNCA vaza dígito — mascara tudo.
 */
class CpfMaskerTest {
    @Test
    fun `mascara CPF valido preservando os tres ultimos digitos e o DV`() {
        assertEquals("***.***.789-00", maskCpf("12345678900"))
    }

    @Test
    fun `mascara CPF ja formatado — ignora pontuacao na entrada`() {
        assertEquals("***.***.789-00", maskCpf("123.456.789-00"))
    }

    @Test
    fun `entrada vazia vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf(""))
    }

    @Test
    fun `entrada curta vira mascara completa — nao vaza digito parcial`() {
        assertEquals("***.***.***-**", maskCpf("123"))
    }

    @Test
    fun `entrada longa demais vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf("123456789012"))
    }

    @Test
    fun `entrada sem digitos vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf("abcdefghijk"))
    }

    @Test
    fun `formata CPF valido para exibicao`() {
        assertEquals("123.456.789-00", formatCpf("12345678900"))
    }

    @Test
    fun `formata devolve a entrada crua quando nao ha 11 digitos`() {
        assertEquals("123", formatCpf("123"))
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
./gradlew :shared:testDebugUnitTest --tests "br.com.sprena.shared.core.privacy.CpfMaskerTest" --no-daemon
```

Esperado: FAIL na compilação — `Unresolved reference: maskCpf`.

- [ ] **Step 3: Implementar o mínimo**

Criar `shared/src/commonMain/kotlin/br/com/sprena/shared/core/privacy/CpfMasker.kt`:

```kotlin
package br.com.sprena.shared.core.privacy

private const val CPF_DIGITS = 11
private const val FULLY_MASKED = "***.***.***-**"

/**
 * Mascara um CPF para exibição: `12345678900` → `***.***.789-00`.
 *
 * Aceita entrada crua ou já pontuada — só os dígitos importam. Qualquer entrada
 * que não normalize para exatamente 11 dígitos vira máscara completa: entrada
 * malformada não pode vazar dígito parcial.
 */
fun maskCpf(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != CPF_DIGITS) return FULLY_MASKED
    return "***.***.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
}

/**
 * Formata um CPF completo para exibição: `12345678900` → `123.456.789-00`.
 *
 * Usado só quando a revelação foi autorizada (ADM/MOD). Entrada que não tenha
 * 11 dígitos volta como veio — formatar lixo esconderia o problema.
 */
fun formatCpf(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != CPF_DIGITS) return raw
    return "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

```bash
./gradlew :shared:testDebugUnitTest --tests "br.com.sprena.shared.core.privacy.CpfMaskerTest" --no-daemon
```

Esperado: PASS, 8 testes.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/privacy/CpfMasker.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/core/privacy/CpfMaskerTest.kt
git commit -m "feat(privacy): mascara e formatacao de CPF para exibicao"
```

---

### Task 2: Domínio de consentimento em `shared/privacy`

Models, contrato de repositório, dois use cases e o módulo Koin. Tudo em commonMain, testado com fakes.

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/ConsentRecord.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/ConsentStatus.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/PrivacyPolicy.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/repository/ConsentRepository.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/usecase/CheckConsentUseCase.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/usecase/AcceptConsentUseCase.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/di/PrivacyModule.kt`
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/di/SharedModules.kt`
- Test: `shared/src/commonTest/kotlin/br/com/sprena/shared/privacy/domain/usecase/CheckConsentUseCaseTest.kt`
- Test: `shared/src/commonTest/kotlin/br/com/sprena/shared/privacy/domain/usecase/AcceptConsentUseCaseTest.kt`

**Interfaces:**
- Consumes: `br.com.sprena.shared.core.logger.Logger` (existente; métodos `info/warn/error(tag, message, throwable = null)`), `NoOpLogger` (existente em commonTest).
- Produces:
  - `data class ConsentRecord(uid: String, policyVersion: String, acceptedAtEpochMillis: Long)`
  - `sealed interface ConsentStatus { data object Granted; data class Required(reason: Reason); data class Unavailable(message: String); enum class Reason { MISSING, OUTDATED } }`
  - `object PrivacyPolicy { const val VERSION: String }`
  - `interface ConsentRepository { suspend fun current(uid: String): Result<ConsentRecord?>; suspend fun accept(uid: String, policyVersion: String): Result<Unit> }`
  - `class CheckConsentUseCase(repository, logger)` com `suspend operator fun invoke(uid: String): ConsentStatus`
  - `class AcceptConsentUseCase(repository, logger)` com `suspend operator fun invoke(uid: String): Result<Unit>`
  - `fun privacyModule(): Module`

- [ ] **Step 1: Escrever os testes que falham**

Criar `shared/src/commonTest/kotlin/br/com/sprena/shared/privacy/domain/usecase/CheckConsentUseCaseTest.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD — decisão do gate de consentimento (F1.5).
 *
 * O gate é fail-closed: falha de leitura vira `Unavailable`, nunca `Granted`.
 */
class CheckConsentUseCaseTest {
    private class FakeRepo(
        var currentResult: Result<ConsentRecord?> = Result.success(null),
    ) : ConsentRepository {
        override suspend fun current(uid: String): Result<ConsentRecord?> = currentResult

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> = Result.success(Unit)
    }

    private fun useCase(repo: FakeRepo) = CheckConsentUseCase(repository = repo, logger = NoOpLogger())

    @Test
    fun `sem registro de aceite exige consentimento por ausencia`() =
        runTest {
            val repo = FakeRepo(currentResult = Result.success(null))

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Required(ConsentStatus.Reason.MISSING), status)
        }

    @Test
    fun `aceite de versao antiga exige novo consentimento`() =
        runTest {
            val repo =
                FakeRepo(
                    currentResult =
                        Result.success(
                            ConsentRecord(uid = "uid_1", policyVersion = "2020-01-01", acceptedAtEpochMillis = 1L),
                        ),
                )

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Required(ConsentStatus.Reason.OUTDATED), status)
        }

    @Test
    fun `aceite da versao atual libera o acesso`() =
        runTest {
            val repo =
                FakeRepo(
                    currentResult =
                        Result.success(
                            ConsentRecord(
                                uid = "uid_1",
                                policyVersion = PrivacyPolicy.VERSION,
                                acceptedAtEpochMillis = 1L,
                            ),
                        ),
                )

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Granted, status)
        }

    @Test
    fun `falha de leitura nao libera acesso — vira Unavailable`() =
        runTest {
            val repo = FakeRepo(currentResult = Result.failure(RuntimeException("offline")))

            val status = useCase(repo)("uid_1")

            assertTrue(status is ConsentStatus.Unavailable)
            assertTrue(status.message.isNotBlank())
        }
}
```

Criar `shared/src/commonTest/kotlin/br/com/sprena/shared/privacy/domain/usecase/AcceptConsentUseCaseTest.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TDD — gravação do aceite (F1.5). */
class AcceptConsentUseCaseTest {
    private class FakeRepo(
        var acceptResult: Result<Unit> = Result.success(Unit),
    ) : ConsentRepository {
        var lastUid: String? = null
        var lastVersion: String? = null

        override suspend fun current(uid: String): Result<ConsentRecord?> = Result.success(null)

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> {
            lastUid = uid
            lastVersion = policyVersion
            return acceptResult
        }
    }

    @Test
    fun `grava o aceite com a versao atual da politica`() =
        runTest {
            val repo = FakeRepo()

            val result = AcceptConsentUseCase(repository = repo, logger = NoOpLogger())("uid_1")

            assertTrue(result.isSuccess)
            assertEquals("uid_1", repo.lastUid)
            assertEquals(PrivacyPolicy.VERSION, repo.lastVersion)
        }

    @Test
    fun `propaga falha de gravacao`() =
        runTest {
            val repo = FakeRepo(acceptResult = Result.failure(RuntimeException("offline")))

            val result = AcceptConsentUseCase(repository = repo, logger = NoOpLogger())("uid_1")

            assertTrue(result.isFailure)
        }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```bash
./gradlew :shared:testDebugUnitTest --tests "br.com.sprena.shared.privacy.*" --no-daemon
```

Esperado: FAIL na compilação — `Unresolved reference: privacy`.

- [ ] **Step 3: Implementar os models e o contrato**

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/ConsentRecord.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.model

/**
 * Aceite de política registrado para um usuário.
 *
 * @property policyVersion versão do texto aceito — comparada com [PrivacyPolicy.VERSION]
 */
data class ConsentRecord(
    val uid: String,
    val policyVersion: String,
    val acceptedAtEpochMillis: Long,
)
```

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/ConsentStatus.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.model

/**
 * Decisão do gate de consentimento.
 *
 * [Unavailable] existe porque "não deu para saber" não é "pode entrar": o gate é
 * fail-closed e trata falha de leitura como bloqueio com retry, não como aceite.
 */
sealed interface ConsentStatus {
    data object Granted : ConsentStatus

    data class Required(
        val reason: Reason,
    ) : ConsentStatus

    data class Unavailable(
        val message: String,
    ) : ConsentStatus

    enum class Reason { MISSING, OUTDATED }
}
```

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/PrivacyPolicy.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.model

/**
 * Versão vigente da política de privacidade.
 *
 * É a data de publicação do texto em
 * `composeApp/src/commonMain/composeResources/files/privacy-policy.md`.
 * Mudou o texto → muda esta constante → todos os usuários reaceitam.
 */
object PrivacyPolicy {
    const val VERSION = "2026-08-12"
}
```

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/repository/ConsentRepository.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.repository

import br.com.sprena.shared.privacy.domain.model.ConsentRecord

/**
 * Persistência do aceite de política.
 *
 * `Result<ConsentRecord?>` separa as três situações que o gate precisa distinguir:
 * aceitou (`success(record)`), nunca aceitou (`success(null)`) e não deu para
 * saber (`failure`).
 */
interface ConsentRepository {
    suspend fun current(uid: String): Result<ConsentRecord?>

    suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit>
}
```

- [ ] **Step 4: Implementar os use cases**

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/usecase/CheckConsentUseCase.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository

/**
 * Decide se o usuário pode entrar no app ou precisa aceitar a política.
 *
 * Fail-closed: qualquer falha de leitura vira [ConsentStatus.Unavailable], que a
 * UI trata como bloqueio com retry.
 */
class CheckConsentUseCase(
    private val repository: ConsentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(uid: String): ConsentStatus {
        val record =
            repository.current(uid).getOrElse { error ->
                logger.warn(TAG, "consent read failed uid=$uid", error)
                return ConsentStatus.Unavailable(READ_FAILED_MESSAGE)
            }

        return when {
            record == null -> ConsentStatus.Required(ConsentStatus.Reason.MISSING)
            record.policyVersion != PrivacyPolicy.VERSION -> ConsentStatus.Required(ConsentStatus.Reason.OUTDATED)
            else -> ConsentStatus.Granted
        }
    }

    private companion object {
        const val TAG = "CheckConsent"
        const val READ_FAILED_MESSAGE = "Não foi possível verificar seu consentimento. Verifique a conexão."
    }
}
```

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/usecase/AcceptConsentUseCase.kt`:

```kotlin
package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository

/** Registra o aceite da versão vigente da política. */
class AcceptConsentUseCase(
    private val repository: ConsentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(uid: String): Result<Unit> =
        repository
            .accept(uid, PrivacyPolicy.VERSION)
            .onSuccess { logger.info(TAG, "consent accepted uid=$uid version=${PrivacyPolicy.VERSION}") }
            .onFailure { logger.warn(TAG, "consent write failed uid=$uid", it) }

    private companion object {
        const val TAG = "AcceptConsent"
    }
}
```

- [ ] **Step 5: Registrar o módulo Koin**

`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/di/PrivacyModule.kt`:

```kotlin
package br.com.sprena.shared.privacy.di

import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import org.koin.dsl.module

/**
 * Módulo Koin de privacidade (commonMain).
 *
 * NÃO declara `ConsentRepository` — a impl é Android-only
 * (`FirestoreConsentRepository`), declarada em `composeApp/PlatformModule.android.kt`.
 * Mesma estratégia do [br.com.sprena.shared.auth.di.authModule].
 */
fun privacyModule() =
    module {
        factory { CheckConsentUseCase(repository = get(), logger = get()) }
        factory { AcceptConsentUseCase(repository = get(), logger = get()) }
    }
```

Em `shared/src/commonMain/kotlin/br/com/sprena/shared/core/di/SharedModules.kt`, adicionar o import
`br.com.sprena.shared.privacy.di.privacyModule`, acrescentar `privacyModule(),` ao fim da lista de
`sharedModules()` e incluir `5. [privacyModule] → domínio Privacidade (consentimento LGPD)` na lista
do KDoc de ordem de carregamento.

- [ ] **Step 6: Rodar os testes e confirmar que passam**

```bash
./gradlew :shared:testDebugUnitTest --tests "br.com.sprena.shared.privacy.*" --no-daemon
```

Esperado: PASS, 6 testes.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/privacy shared/src/commonTest/kotlin/br/com/sprena/shared/privacy \
        shared/src/commonMain/kotlin/br/com/sprena/shared/core/di/SharedModules.kt
git commit -m "feat(privacy): dominio de consentimento LGPD com gate fail-closed"
```

---

### Task 3: Firestore Rules de `user_consents`

Regras server-side + testes no emulador. Independente das tasks de Kotlin — pode ser revisada sozinha.

**Files:**
- Modify: `firestore.rules` (inserir o bloco antes do `match /{document=**}` de default deny)
- Test: `tools/firestore-rules-tests/rules.test.mjs`

**Interfaces:**
- Consumes: helper `isSignedIn()` já existente em `firestore.rules`.
- Produces: paths `user_consents/{uid}` e `user_consents/{uid}/history/{policyVersion}` com escrita restrita ao dono.

> **Pré-requisito:** JDK 21+ no PATH (`java -version`). O `firebase-tools` recusa runtime anterior.

- [ ] **Step 1: Escrever os testes que falham**

Em `tools/firestore-rules-tests/rules.test.mjs`, adicionar `serverTimestamp` ao import do
`firebase/firestore` (a linha vira `import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';`)
e inserir este bloco depois do `describe('sport_clients/{id}', ...)`:

```javascript
describe('user_consents/{uid}', () => {
  const VERSION = '2026-08-12';
  const payload = () => ({
    uid: CLIENT_UID,
    policyVersion: VERSION,
    acceptedAt: serverTimestamp(),
    appVersion: '0.1.0',
  });

  it('11. le o proprio registro de consentimento — inclusive quando nao existe', async () => {
    await assertSucceeds(getDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID)));
  });

  it('12. nega ler o consentimento de outro usuario', async () => {
    await assertFails(getDoc(doc(as(CLIENT_UID), 'user_consents', ADM_UID)));
  });

  it('13. cria o proprio consentimento com payload valido', async () => {
    await assertSucceeds(setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), payload()));
  });

  it('14. nega criar consentimento em nome de outro uid', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', ADM_UID), { ...payload(), uid: ADM_UID }),
    );
  });

  it('15. nega delete do proprio consentimento', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'user_consents', CLIENT_UID), {
        uid: CLIENT_UID,
        policyVersion: VERSION,
      });
    });
    await assertFails(deleteDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID)));
  });

  it('16. nega update no historico — a trilha e append-only', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'user_consents', CLIENT_UID, 'history', VERSION), {
        policyVersion: VERSION,
      });
    });
    await assertFails(
      updateDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history', VERSION), {
        policyVersion: 'adulterado',
      }),
    );
  });
});
```

Atualizar também o comentário de cabeçalho do arquivo, acrescentando a linha
` *  - user_consents/{uid}  → cada um le e grava so o proprio aceite; history e append-only`
à lista "Modelo de acesso sob teste".

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```bash
cd tools/firestore-rules-tests && npm run test:emulator
```

Esperado: FAIL nos casos 11 e 13 (o default deny bloqueia tudo em `user_consents`). Os casos 12, 14,
15 e 16 já passam por acidente — só viram prova real depois do Step 3.

- [ ] **Step 3: Escrever as regras**

Em `firestore.rules`, inserir antes do bloco `match /{document=**}`:

```
    // Aceite da politica de privacidade (F1.5).
    //
    // O id do doc e o uid DE PROPOSITO: a regra de leitura e baseada no path, nao
    // em resource.data. Uma regra que lesse resource.data.uid daria evaluation
    // error num doc inexistente, e o app nao conseguiria distinguir "nunca aceitou"
    // de "sem permissao" — o gate precisa dessa diferenca.
    //
    // A subcolecao history e append-only: e ela que sustenta o onus da prova do
    // consentimento (LGPD art. 8 §1) quando a politica ganha versao nova e o doc
    // corrente e sobrescrito.
    match /user_consents/{uid} {
      allow read: if isSignedIn() && request.auth.uid == uid;
      allow create, update: if isSignedIn()
        && request.auth.uid == uid
        && request.resource.data.uid == uid
        && request.resource.data.policyVersion is string
        && request.resource.data.policyVersion.size() > 0
        && request.resource.data.acceptedAt == request.time;
      allow delete: if false;

      match /history/{policyVersion} {
        allow read: if isSignedIn() && request.auth.uid == uid;
        allow create: if isSignedIn()
          && request.auth.uid == uid
          && request.resource.data.policyVersion == policyVersion
          && request.resource.data.acceptedAt == request.time;
        allow update, delete: if false;
      }
    }

```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

```bash
cd tools/firestore-rules-tests && npm run test:emulator
```

Esperado: PASS, 18 testes (12 anteriores + 6 novos).

- [ ] **Step 5: Commit**

```bash
git add firestore.rules tools/firestore-rules-tests/rules.test.mjs
git commit -m "feat(rules): user_consents com escrita restrita ao dono e historico append-only"
```

> **Nota de deploy (não é passo do plano):** as regras só valem em produção depois de
> `firebase deploy --only firestore:rules --project <projeto>`. Isso entra no checklist do PR, não
> num commit.

---

### Task 4: `FirestoreConsentRepository` + wiring Koin

Implementação Android do contrato da Task 2. Sem teste unitário — repositórios Firestore não são
testados por unidade neste projeto (o `SportClientRepositoryImpl` segue o mesmo padrão); a cobertura
real vem das rules (Task 3) e do build.

**Files:**
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/data/dto/ConsentDto.kt`
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/data/repository/FirestoreConsentRepository.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt`

**Interfaces:**
- Consumes: `ConsentRepository`, `ConsentRecord` (Task 2); `FirebaseFirestore` e `Logger` já disponíveis no Koin.
- Produces: `class FirestoreConsentRepository(firestore: FirebaseFirestore, appVersion: String, logger: Logger) : ConsentRepository`, ligado no `platformModule()`.

- [ ] **Step 1: Escrever o DTO**

`shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/data/dto/ConsentDto.kt`:

```kotlin
package br.com.sprena.shared.privacy.data.dto

import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mapeia o doc `user_consents/{uid}` para o domínio.
 *
 * `acceptedAt` é gravado com `FieldValue.serverTimestamp()`; na leitura imediata
 * após a escrita ele pode vir null (o servidor ainda não resolveu) — nesse caso
 * cai para 0, o que é irrelevante: o gate só compara `policyVersion`.
 */
object ConsentDto {
    fun fromSnapshot(snapshot: DocumentSnapshot): ConsentRecord? {
        val uid = snapshot.getString("uid") ?: return null
        val version = snapshot.getString("policyVersion") ?: return null
        val acceptedAt = snapshot.get("acceptedAt") as? Timestamp
        return ConsentRecord(
            uid = uid,
            policyVersion = version,
            acceptedAtEpochMillis = acceptedAt?.toDate()?.time ?: 0L,
        )
    }
}
```

- [ ] **Step 2: Escrever o repositório**

`shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/data/repository/FirestoreConsentRepository.kt`:

```kotlin
package br.com.sprena.shared.privacy.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.data.dto.ConsentDto
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Persistência do aceite em `user_consents/{uid}`.
 *
 * A gravação é um batch: o doc corrente (sobrescrito a cada nova versão) e um doc
 * em `history/{policyVersion}` que nunca é alterado — as rules de F1.5 negam
 * update e delete nele.
 */
class FirestoreConsentRepository(
    private val firestore: FirebaseFirestore,
    private val appVersion: String,
    private val logger: Logger,
) : ConsentRepository {
    override suspend fun current(uid: String): Result<ConsentRecord?> =
        runCatching {
            val snapshot = firestore.collection(COLLECTION).document(uid).get().await()
            if (snapshot.exists()) ConsentDto.fromSnapshot(snapshot) else null
        }.onFailure { logger.warn(TAG, "read failed uid=$uid", it) }

    override suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit> =
        runCatching {
            val root = firestore.collection(COLLECTION).document(uid)
            val history = root.collection(HISTORY).document(policyVersion)
            firestore
                .runBatch { batch ->
                    batch.set(
                        root,
                        mapOf(
                            "uid" to uid,
                            "policyVersion" to policyVersion,
                            "acceptedAt" to FieldValue.serverTimestamp(),
                            "appVersion" to appVersion,
                        ),
                    )
                    batch.set(
                        history,
                        mapOf(
                            "policyVersion" to policyVersion,
                            "acceptedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }.await()
            Unit
        }.onFailure { logger.warn(TAG, "write failed uid=$uid", it) }

    private companion object {
        const val COLLECTION = "user_consents"
        const val HISTORY = "history"
        const val TAG = "ConsentRepo"
    }
}
```

- [ ] **Step 3: Ligar no Koin**

Em `composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt`, adicionar os imports

```kotlin
import br.com.sprena.BuildConfig
import br.com.sprena.shared.privacy.data.repository.FirestoreConsentRepository
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
```

e, dentro de `platformModule()`, junto aos outros bindings:

```kotlin
        // F1.5: aceite da política de privacidade. `appVersion` vem do BuildConfig do
        // composeApp — o módulo shared não tem BuildConfig próprio.
        single<ConsentRepository> {
            FirestoreConsentRepository(
                firestore = get(),
                appVersion = BuildConfig.VERSION_NAME,
                logger = get(),
            )
        }
```

- [ ] **Step 4: Verificar que compila**

```bash
./gradlew :composeApp:assembleDebug --no-daemon
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/androidMain/kotlin/br/com/sprena/shared/privacy \
        composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt
git commit -m "feat(privacy): repositorio Firestore do aceite com batch e historico"
```

---

### Task 5: Texto da política + carregador

O texto é a fonte única, embarcado via Compose Resources. O carregador é uma interface para que o
ViewModel da Task 6 seja testável sem o runtime de resources.

**Files:**
- Create: `composeApp/src/commonMain/composeResources/files/privacy-policy.md`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/privacy/PolicyTextLoader.kt`
- Create: `docs/legal/privacy-policy.md`
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt`

**Interfaces:**
- Consumes: nada das tasks anteriores.
- Produces:
  - `fun interface PolicyTextLoader { suspend fun load(): String }`
  - `class ComposeResourcePolicyTextLoader : PolicyTextLoader`
  - Classe gerada `br.com.sprena.resources.Res`

- [ ] **Step 1: Configurar o pacote da classe `Res`**

Em `composeApp/build.gradle.kts`, depois do bloco `kotlin { ... }` e antes de `android { ... }`:

```kotlin
// F1.5: a política de privacidade é um arquivo em composeResources/files.
// Pacote fixado explicitamente para o import não depender de heurística do plugin.
compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.sprena.resources"
    generateResClass = always
}
```

- [ ] **Step 2: Escrever o texto da política**

Criar `composeApp/src/commonMain/composeResources/files/privacy-policy.md`.

O texto é deliberadamente **sem sintaxe de markdown** (nada de `#`, `*` ou `-` no começo de linha):
o app renderiza como texto puro num `Text` rolável — sem parser de markdown, que seria dependência
nova para nada. Publicado como página, os parágrafos separados por linha em branco continuam válidos.

```
Política de Privacidade do Sprena

Versão 2026-08-12

1. Quem trata seus dados

O Sprena é um aplicativo de gestão de clientes, comandas e finanças de operações
esportivas. Os dados tratados no aplicativo são de responsabilidade do operador que
mantém a conta — a pessoa ou empresa que cadastrou os usuários e os clientes.

2. Quais dados são tratados

Do usuário do aplicativo: endereço de e-mail, perfil de acesso (administrador,
moderador ou cliente) e data do último acesso.

Dos clientes cadastrados pelo operador: nome, apelido, CPF, telefone, e-mail
quando informado, modalidades praticadas, presenças, forma de pagamento e
histórico de pagamentos e consumo.

3. Para que os dados são usados

Os dados são usados exclusivamente para operar o aplicativo: autenticar o acesso,
identificar clientes, registrar consumo e pagamentos e apresentar relatórios ao
operador. Não há uso para publicidade, não há venda de dados e não há
compartilhamento com terceiros além dos provedores de infraestrutura descritos
no item 5.

4. Base legal

O tratamento dos dados do usuário do aplicativo se apoia no consentimento
registrado neste aceite e na execução do contrato de uso.

O tratamento dos dados de clientes cadastrados é feito pelo operador, a quem cabe
obter a autorização do titular antes de inserir os dados no aplicativo. Ao
cadastrar um cliente, o operador declara ter essa autorização.

5. Onde os dados ficam

Os dados ficam armazenados no Google Cloud Firestore, provedor de infraestrutura
do aplicativo. O acesso é restrito por autenticação e por regras de segurança
aplicadas no servidor. A sessão local do usuário é gravada de forma criptografada
no próprio aparelho.

6. Por quanto tempo

Os dados são mantidos enquanto a conta do operador estiver ativa. O registro deste
aceite é mantido enquanto a conta existir, como comprovação do consentimento.

7. Exibição de CPF

O CPF dos clientes cadastrados é exibido mascarado no aplicativo. A visualização do
número completo é restrita a usuários com perfil de administrador ou moderador.

8. Seus direitos

A Lei Geral de Proteção de Dados garante ao titular os direitos de confirmação,
acesso, correção, portabilidade, eliminação e revogação do consentimento. Para
exercer qualquer um deles, entre em contato com o operador responsável pela conta.

9. Alterações desta política

Quando o texto for alterado, a versão indicada no topo muda e o aceite é solicitado
novamente no próximo acesso. O histórico de aceites é preservado.
```

> **Atenção do implementador:** este texto é um baseline técnico, não aconselhamento jurídico. Ele
> descreve fielmente o que o app faz hoje; o mantenedor deve revisar antes de publicar, e preencher o
> contato do item 8 conforme a operação real.

- [ ] **Step 3: Escrever o carregador**

`composeApp/src/commonMain/kotlin/br/com/sprena/presentation/privacy/PolicyTextLoader.kt`:

```kotlin
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
```

- [ ] **Step 4: Registrar no Koin**

Em `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt`, adicionar os imports

```kotlin
import br.com.sprena.presentation.privacy.ComposeResourcePolicyTextLoader
import br.com.sprena.presentation.privacy.PolicyTextLoader
```

e, dentro de `appModule()`, antes das declarações de `viewModel`:

```kotlin
        single<PolicyTextLoader> { ComposeResourcePolicyTextLoader() }
```

- [ ] **Step 5: Criar o ponteiro em `docs/legal`**

`docs/legal/privacy-policy.md`:

```markdown
# Política de Privacidade — onde vive o texto

O texto vigente **não** está neste arquivo. Ele é a fonte única embarcada no app:

`composeApp/src/commonMain/composeResources/files/privacy-policy.md`

Motivo: o aceite grava a versão exata do texto que o usuário leu. Manter uma cópia aqui criaria
duas verdades e a chance de divergirem.

## Como alterar a política

1. Editar `composeApp/src/commonMain/composeResources/files/privacy-policy.md`
2. Atualizar a linha `Versão AAAA-MM-DD` no topo do texto
3. Atualizar `PrivacyPolicy.VERSION` em
   `shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/PrivacyPolicy.kt`
   com o mesmo valor
4. Publicar o app — todos os usuários reaceitam no próximo acesso, e o aceite anterior fica
   preservado em `user_consents/{uid}/history/{policyVersion}`

## Publicação como URL pública

A Play Store exige uma URL pública de política de privacidade no listing. No release, publicar o
mesmo arquivo (por exemplo via GitHub Pages) e apontar o listing para ele. Esse passo é de release,
não de build — por isso não há automação no repositório.
```

- [ ] **Step 6: Verificar que compila e que a classe `Res` foi gerada**

```bash
./gradlew :composeApp:assembleDebug --no-daemon
```

Esperado: BUILD SUCCESSFUL. Se falhar com `Unresolved reference: resources`, confirmar que o arquivo
está exatamente em `composeApp/src/commonMain/composeResources/files/privacy-policy.md`.

- [ ] **Step 7: Commit**

```bash
git add composeApp/build.gradle.kts \
        composeApp/src/commonMain/composeResources/files/privacy-policy.md \
        composeApp/src/commonMain/kotlin/br/com/sprena/presentation/privacy/PolicyTextLoader.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt \
        docs/legal/privacy-policy.md
git commit -m "feat(privacy): politica de privacidade versionada embarcada no app"
```

---

### Task 6: Tela de consentimento (MVI)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent/ConsentState.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent/ConsentIntent.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent/ConsentEffect.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent/ConsentViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent/ConsentScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt`
- Test: `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/consent/ConsentViewModelTest.kt`

**Interfaces:**
- Consumes: `PolicyTextLoader` (Task 5), `AcceptConsentUseCase` (Task 2), `SessionStore`/`SessionUser` (existentes em `br.com.sprena.shared.auth.session`), `MviViewModel`/`UiState`/`UiIntent`/`UiEffect` (existentes em `br.com.sprena.shared.core.mvi`), `MainDispatcherEnv` (existente em `composeApp/src/commonTest/.../test`).
- Produces:
  - `data class ConsentState(policyText, isLoading, isAccepting, hasRead, error)` com `val canAccept: Boolean`
  - `sealed interface ConsentIntent { ToggleRead; Accept; Retry }`
  - `sealed interface ConsentEffect { data class NavigateHome(session: SessionUser); data object NavigateLogin }`
  - `class ConsentViewModel(policyLoader, acceptConsent, sessionStore)`

- [ ] **Step 1: Escrever o teste que falha**

`composeApp/src/commonTest/kotlin/br/com/sprena/presentation/consent/ConsentViewModelTest.kt`:

```kotlin
package br.com.sprena.presentation.consent

import app.cash.turbine.test
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD — ConsentViewModel (gate LGPD, F1.5).
 *
 * Cenários: carga do texto, falha de carga com retry, habilitação do aceite,
 * gravação com sucesso e com falha, sessão ausente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private val session =
        SessionUser(
            uid = "uid_1",
            email = "adm@sprena.com",
            role = UserRole.ADM,
            lastLoginEpochMillis = 1L,
        )

    private class FakeLoader(
        var text: String = "Política de Privacidade do Sprena",
        var failure: Throwable? = null,
    ) : PolicyTextLoader {
        override suspend fun load(): String = failure?.let { throw it } ?: text
    }

    private class FakeConsentRepo(
        var acceptResult: Result<Unit> = Result.success(Unit),
    ) : ConsentRepository {
        override suspend fun current(uid: String): Result<ConsentRecord?> = Result.success(null)

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> = acceptResult
    }

    private class FakeStore(
        var current: SessionUser?,
    ) : SessionStore {
        override suspend fun save(user: SessionUser) {
            current = user
        }

        override suspend fun load(): SessionUser? = current

        override suspend fun clear() {
            current = null
        }
    }

    private fun viewModel(
        loader: FakeLoader = FakeLoader(),
        repo: FakeConsentRepo = FakeConsentRepo(),
        store: FakeStore = FakeStore(session),
    ) = ConsentViewModel(
        policyLoader = loader,
        acceptConsent = AcceptConsentUseCase(repository = repo, logger = NoOpLogger()),
        sessionStore = store,
    )

    @Test
    fun `carrega o texto da politica na inicializacao`() =
        runTest {
            val vm = viewModel(loader = FakeLoader(text = "Texto da politica"))

            advanceUntilIdle()

            val state = vm.state.first()
            assertEquals("Texto da politica", state.policyText)
            assertFalse(state.isLoading)
            assertEquals(null, state.error)
        }

    @Test
    fun `falha ao carregar o texto vira erro e o aceite fica bloqueado`() =
        runTest {
            val vm = viewModel(loader = FakeLoader(failure = RuntimeException("io")))

            advanceUntilIdle()

            val state = vm.state.first()
            assertNotNull(state.error)
            assertFalse(state.canAccept)
        }

    @Test
    fun `retry recarrega o texto depois de uma falha`() =
        runTest {
            val loader = FakeLoader(failure = RuntimeException("io"))
            val vm = viewModel(loader = loader)
            advanceUntilIdle()

            loader.failure = null
            loader.text = "Carregou na segunda"
            vm.handleIntent(ConsentIntent.Retry)
            advanceUntilIdle()

            val state = vm.state.first()
            assertEquals("Carregou na segunda", state.policyText)
            assertEquals(null, state.error)
        }

    @Test
    fun `aceite so habilita depois de marcar a leitura`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            assertFalse(vm.state.first().canAccept)

            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            assertTrue(vm.state.first().canAccept)
        }

    @Test
    fun `aceite gravado com sucesso navega para a home com a sessao`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Accept)
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateHome(session), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `falha na gravacao mantem a tela com erro e nao navega`() =
        runTest {
            val repo = FakeConsentRepo(acceptResult = Result.failure(RuntimeException("offline")))
            val vm = viewModel(repo = repo)
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.handleIntent(ConsentIntent.Accept)
            advanceUntilIdle()

            val state = vm.state.first()
            assertNotNull(state.error)
            assertFalse(state.isAccepting)
        }

    @Test
    fun `sessao ausente manda de volta para o login`() =
        runTest {
            val vm = viewModel(store = FakeStore(null))
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Accept)
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateLogin, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "br.com.sprena.presentation.consent.*" --no-daemon
```

Esperado: FAIL na compilação — `Unresolved reference: ConsentViewModel`.

- [ ] **Step 3: Escrever State, Intent e Effect**

`ConsentState.kt`:

```kotlin
package br.com.sprena.presentation.consent

import br.com.sprena.shared.core.mvi.UiState

/**
 * State do gate de consentimento LGPD.
 *
 * [canAccept] é derivado: não se aceita política que não carregou nem se aceita
 * duas vezes em paralelo.
 */
data class ConsentState(
    val policyText: String = "",
    val isLoading: Boolean = true,
    val isAccepting: Boolean = false,
    val hasRead: Boolean = false,
    val error: String? = null,
) : UiState {
    val canAccept: Boolean
        get() = hasRead && !isLoading && !isAccepting && policyText.isNotBlank()
}
```

`ConsentIntent.kt`:

```kotlin
package br.com.sprena.presentation.consent

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface ConsentIntent : UiIntent {
    /** Marca/desmarca "li e concordo". */
    data object ToggleRead : ConsentIntent

    /** Grava o aceite da versão vigente. */
    data object Accept : ConsentIntent

    /** Recarrega o texto após falha. */
    data object Retry : ConsentIntent
}
```

`ConsentEffect.kt`:

```kotlin
package br.com.sprena.presentation.consent

import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.mvi.UiEffect

sealed interface ConsentEffect : UiEffect {
    /** Carrega a sessão junto porque a rota da Home precisa dos dados do usuário. */
    data class NavigateHome(
        val session: SessionUser,
    ) : ConsentEffect

    /** Sessão sumiu no meio do fluxo — estado inconsistente, volta para o login. */
    data object NavigateLogin : ConsentEffect
}
```

- [ ] **Step 4: Escrever o ViewModel**

`ConsentViewModel.kt`:

```kotlin
package br.com.sprena.presentation.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gate de consentimento LGPD (F1.5).
 *
 * Fail-closed: enquanto o aceite não for gravado com sucesso, nenhum efeito de
 * navegação para a Home é emitido.
 */
class ConsentViewModel(
    private val policyLoader: PolicyTextLoader,
    private val acceptConsent: AcceptConsentUseCase,
    private val sessionStore: SessionStore,
) : ViewModel(),
    MviViewModel<ConsentState, ConsentIntent, ConsentEffect> {
    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ConsentEffect>()
    override val effects: SharedFlow<ConsentEffect> = _effects.asSharedFlow()

    init {
        loadPolicy()
    }

    override fun handleIntent(intent: ConsentIntent) {
        when (intent) {
            is ConsentIntent.ToggleRead ->
                _state.value = _state.value.copy(hasRead = !_state.value.hasRead)

            is ConsentIntent.Retry -> loadPolicy()

            is ConsentIntent.Accept -> accept()
        }
    }

    private fun loadPolicy() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { policyLoader.load() }
                .onSuccess { text ->
                    _state.value = _state.value.copy(policyText = text, isLoading = false, error = null)
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = LOAD_ERROR)
                }
        }
    }

    private fun accept() {
        if (!_state.value.canAccept) return
        _state.value = _state.value.copy(isAccepting = true, error = null)
        viewModelScope.launch {
            val session = sessionStore.load()
            if (session == null) {
                _state.value = _state.value.copy(isAccepting = false)
                _effects.emit(ConsentEffect.NavigateLogin)
                return@launch
            }

            acceptConsent(session.uid)
                .onSuccess {
                    _state.value = _state.value.copy(isAccepting = false)
                    _effects.emit(ConsentEffect.NavigateHome(session))
                }.onFailure {
                    _state.value = _state.value.copy(isAccepting = false, error = SAVE_ERROR)
                }
        }
    }

    private companion object {
        const val LOAD_ERROR = "Não foi possível carregar a política. Tente novamente."
        const val SAVE_ERROR = "Não foi possível registrar seu aceite. Verifique a conexão."
    }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "br.com.sprena.presentation.consent.*" --no-daemon
```

Esperado: PASS, 7 testes.

- [ ] **Step 6: Escrever a tela**

`ConsentScreen.kt`:

```kotlin
package br.com.sprena.presentation.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.sprena.shared.auth.session.SessionUser

/**
 * Gate de consentimento — primeira tela após login/restore enquanto o aceite da
 * versão vigente não estiver registrado.
 *
 * Não há botão de voltar nem de recusar: recusar é fechar o app. Isso é
 * deliberado — sem aceite não há base legal para operar os dados.
 */
@Composable
fun ConsentScreen(
    viewModel: ConsentViewModel,
    onNavigateHome: (SessionUser) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConsentEffect.NavigateHome -> onNavigateHome(effect.session)
                is ConsentEffect.NavigateLogin -> onNavigateLogin()
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Antes de continuar",
                style = MaterialTheme.typography.headlineSmall,
            )

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.policyText.isBlank() -> {
                    Text(
                        text = state.error ?: "Não foi possível carregar a política.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.handleIntent(ConsentIntent.Retry) }) {
                        Text("Tentar de novo")
                    }
                }

                else -> {
                    Text(
                        text = state.policyText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.hasRead,
                            onCheckedChange = { viewModel.handleIntent(ConsentIntent.ToggleRead) },
                        )
                        Text("Li e concordo com a Política de Privacidade")
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = { viewModel.handleIntent(ConsentIntent.Accept) },
                        enabled = state.canAccept,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isAccepting) "Registrando..." else "Aceitar e continuar")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Registrar o ViewModel no Koin**

Em `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt`, adicionar o import
`br.com.sprena.presentation.consent.ConsentViewModel` e, junto às outras declarações:

```kotlin
        viewModel {
            ConsentViewModel(
                policyLoader = get(),
                acceptConsent = get(),
                sessionStore = get(),
            )
        }
```

- [ ] **Step 8: Verificar build e testes**

```bash
./gradlew :composeApp:testDebugUnitTest --no-daemon && ./gradlew :composeApp:assembleDebug --no-daemon
```

Esperado: PASS + BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/presentation/consent \
        composeApp/src/commonTest/kotlin/br/com/sprena/presentation/consent \
        composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt
git commit -m "feat(consent): tela de aceite da politica com gate fail-closed"
```

---

### Task 7: Gate no NavGraph

Liga a tela da Task 6 ao fluxo de cold start e de login.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `CheckConsentUseCase`, `ConsentStatus` (Task 2); `ConsentScreen`, `ConsentViewModel` (Task 6); `Routes`, `RestoreSessionUseCase`, `SessionUser` (existentes).
- Produces: `const val CONSENT = "consent"` em `Routes`; funções privadas `homeRoute(...)` e `homeRouteFor(session)`.

- [ ] **Step 1: Adicionar a rota e os helpers de rota**

Em `NavGraph.kt`, no `object Routes`, junto às outras constantes:

```kotlin
    const val CONSENT = "consent"
```

E, no nível de arquivo (fora do `@Composable`), os helpers que eliminam a montagem duplicada da rota
da Home — hoje ela é construída em dois lugares com a mesma regra:

```kotlin
/** Monta a rota da Home com os argumentos que ela espera no path. */
private fun homeRoute(
    uid: String,
    email: String,
    name: String,
    role: UserRole,
): String = "${Routes.HOME}/$uid/$email/${name.replace(" ", "+")}/${role.name}"

/** Rota da Home a partir da sessão persistida — o nome sai do prefixo do email. */
private fun homeRouteFor(session: SessionUser): String =
    homeRoute(
        uid = session.uid,
        email = session.email,
        name = session.email.substringBefore('@'),
        role = session.role,
    )
```

Adicionar o import `br.com.sprena.shared.auth.session.SessionUser`.

- [ ] **Step 2: Consultar o consentimento ao resolver o start destination**

Adicionar os imports:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import br.com.sprena.presentation.consent.ConsentScreen
import br.com.sprena.presentation.consent.ConsentViewModel
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import kotlinx.coroutines.launch
```

Trocar a injeção e o `LaunchedEffect` do start destination por:

```kotlin
    val restoreUseCase: RestoreSessionUseCase = koinInject()
    val checkConsent: CheckConsentUseCase = koinInject()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination =
            when (val result = restoreUseCase()) {
                is RestoreResult.Authenticated -> {
                    val session = result.user
                    // Gate fail-closed: só Granted entra na Home. Required e
                    // Unavailable (falha de leitura) vão para o consentimento.
                    if (checkConsent(session.uid) is ConsentStatus.Granted) {
                        homeRouteFor(session)
                    } else {
                        Routes.CONSENT
                    }
                }
                is RestoreResult.NotAuthenticated -> Routes.LOGIN
            }
    }
```

- [ ] **Step 3: Aplicar o gate também no login**

No `composable(route = Routes.LOGIN)`, o callback `onNavigateHome` não é suspenso — a checagem roda
num escopo. Substituir o bloco por:

```kotlin
        composable(route = Routes.LOGIN) {
            val loginViewModel: LoginViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            LoginScreen(
                viewModel = loginViewModel,
                themeViewModel = themeViewModel,
                onNavigateHome = { user ->
                    scope.launch {
                        val destination =
                            if (checkConsent(user.id) is ConsentStatus.Granted) {
                                homeRoute(
                                    uid = user.id,
                                    email = user.email,
                                    name = user.name,
                                    role = user.role,
                                )
                            } else {
                                Routes.CONSENT
                            }
                        navController.navigate(destination) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
            )
        }
```

- [ ] **Step 4: Declarar a rota de consentimento**

Adicionar, logo depois do `composable(route = Routes.LOGIN) { ... }`:

```kotlin
        composable(route = Routes.CONSENT) {
            val consentViewModel: ConsentViewModel = koinViewModel()
            ConsentScreen(
                viewModel = consentViewModel,
                onNavigateHome = { session ->
                    navController.navigate(homeRouteFor(session)) {
                        popUpTo(Routes.CONSENT) { inclusive = true }
                    }
                },
                onNavigateLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
```

- [ ] **Step 5: Trocar a montagem duplicada da rota pelo helper**

Na `composable(route = Routes.HOME_WITH_ARGS)` e em qualquer outro ponto que construa a string
`"${Routes.HOME}/..."` à mão, usar `homeRoute(...)`. Verificar com:

```bash
grep -n '\${Routes.HOME}/' composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
```

Esperado após a limpeza: as únicas ocorrências estão dentro de `homeRoute`.

- [ ] **Step 6: Verificar build e testes**

```bash
./gradlew :composeApp:assembleDebug --no-daemon && ./gradlew :composeApp:testDebugUnitTest --no-daemon
```

Esperado: BUILD SUCCESSFUL + todos os testes passando.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
git commit -m "feat(consent): gate de consentimento no cold start e no login"
```

---

### Task 8: Política acessível pelo Settings

**Files:**
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/privacy/PrivacyPolicyScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsNavigation.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `PolicyTextLoader` (Task 5), `Routes` (Task 7).
- Produces: `@Composable fun PrivacyPolicyScreen(loader: PolicyTextLoader, onNavigateBack: () -> Unit)`; `Routes.PRIVACY_POLICY`; campo `onNavigatePrivacyPolicy` em `SettingsNavigation`.

- [ ] **Step 1: Escrever a tela de leitura**

`PrivacyPolicyScreen.kt`:

```kotlin
package br.com.sprena.presentation.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Leitura da política de privacidade fora do gate — acessível pelo Settings a
 * qualquer momento. Não grava nada: só exibe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    loader: PolicyTextLoader,
    onNavigateBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { loader.load() }
            .onSuccess { text = it }
            .onFailure { error = "Não foi possível carregar a política." }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de Privacidade") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = error ?: text,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}
```

- [ ] **Step 2: Acrescentar o callback de navegação**

Em `SettingsNavigation.kt`, adicionar o campo:

```kotlin
    val onNavigatePrivacyPolicy: () -> Unit = {},
```

- [ ] **Step 3: Acrescentar a seção no Settings**

Em `SettingsScreen.kt`, adicionar o import `androidx.compose.material.icons.filled.Lock`, criar a
seção seguindo o padrão de `ComandasSection`:

```kotlin
@Composable
private fun PrivacidadeSection(onNavigatePrivacyPolicy: () -> Unit) {
    SectionTitle(title = "Privacidade")
    SettingsItem(
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = "Política de Privacidade",
        subtitle = "Como seus dados são tratados",
        onClick = onNavigatePrivacyPolicy,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
```

e chamá-la no corpo da tela, depois de `FinanceiroSection(...)`:

```kotlin
            PrivacidadeSection(onNavigatePrivacyPolicy = navigation.onNavigatePrivacyPolicy)
```

- [ ] **Step 4: Ligar a rota**

Em `NavGraph.kt`, adicionar em `Routes`:

```kotlin
    const val PRIVACY_POLICY = "privacy_policy"
```

Adicionar a rota (o loader vem do Koin, não há ViewModel — a tela não tem estado de negócio):

```kotlin
        composable(route = Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(
                loader = koinInject(),
                onNavigateBack = { navController.popBackStack() },
            )
        }
```

com o import `br.com.sprena.presentation.privacy.PrivacyPolicyScreen`.

Nos **dois** blocos que constroem `SettingsNavigation(...)` (rota `Routes.SETTINGS` e o uso dentro de
`HomeWithBottomNav`), acrescentar:

```kotlin
                        onNavigatePrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
```

Confirmar que os dois foram cobertos:

```bash
grep -n "SettingsNavigation(" composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
grep -c "onNavigatePrivacyPolicy" composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
```

Esperado: 2 ocorrências de cada.

- [ ] **Step 5: Verificar build**

```bash
./gradlew :composeApp:assembleDebug --no-daemon
```

Esperado: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/presentation/privacy/PrivacyPolicyScreen.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings \
        composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
git commit -m "feat(privacy): politica acessivel pelo settings"
```

---

### Task 9: Masking de CPF no detalhe do cliente

Único ponto do app que exibe CPF em modo leitura hoje: `ClientDetailSheet.kt:135`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail/ClientDetailState.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail/ClientDetailIntent.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail/ClientDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail/ClientDetailSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt` (linha ~856)
- Test: `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/bar/ClientDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `maskCpf`, `formatCpf` (Task 1); `SessionStore`, `SessionUser`, `UserRole` (existentes).
- Produces: `ClientDetailState.displayCpf`, `ClientDetailState.canRevealCpf`, `ClientDetailIntent.ToggleCpfReveal`; `ClientDetailViewModel(client, sessionStore)`.

- [ ] **Step 1: Escrever os testes que falham**

Em `ClientDetailViewModelTest.kt`, adicionar os imports:

```kotlin
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.presentation.bar.clientdetail.ClientDetailIntent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.assertFalse
```

(mantendo os imports já existentes no arquivo), adicionar o fake de sessão dentro da classe:

```kotlin
    private class FakeSessionStore(
        private val role: UserRole?,
    ) : SessionStore {
        override suspend fun save(user: SessionUser) = Unit

        override suspend fun load(): SessionUser? =
            role?.let {
                SessionUser(
                    uid = "uid_1",
                    email = "user@sprena.com",
                    role = it,
                    lastLoginEpochMillis = 1L,
                )
            }

        override suspend fun clear() = Unit
    }
```

e atualizar o helper de construção do ViewModel (hoje `ClientDetailViewModel(client = client)`) para:

```kotlin
    private fun viewModel(
        client: BarClient = sampleClient,
        role: UserRole? = UserRole.CLIENT,
    ) = ClientDetailViewModel(client = client, sessionStore = FakeSessionStore(role))
```

> Ajustar as chamadas existentes do helper no arquivo para o novo nome/assinatura, mantendo o
> comportamento anterior (papel padrão `CLIENT`).

Adicionar os testes novos:

```kotlin
    @Test
    fun `CPF aparece mascarado por padrao`() =
        runTest {
            val vm = viewModel(client = sampleClient.copy(cpf = "12345678900"))
            advanceUntilIdle()

            assertEquals("***.***.789-00", vm.state.first().displayCpf)
        }

    @Test
    fun `CLIENT nao pode revelar o CPF`() =
        runTest {
            val vm = viewModel(client = sampleClient.copy(cpf = "12345678900"), role = UserRole.CLIENT)
            advanceUntilIdle()

            vm.handleIntent(ClientDetailIntent.ToggleCpfReveal)
            advanceUntilIdle()

            val state = vm.state.first()
            assertFalse(state.canRevealCpf)
            assertEquals("***.***.789-00", state.displayCpf)
        }

    @Test
    fun `ADM revela o CPF completo`() =
        runTest {
            val vm = viewModel(client = sampleClient.copy(cpf = "12345678900"), role = UserRole.ADM)
            advanceUntilIdle()

            vm.handleIntent(ClientDetailIntent.ToggleCpfReveal)
            advanceUntilIdle()

            assertEquals("123.456.789-00", vm.state.first().displayCpf)
        }

    @Test
    fun `MOD revela o CPF completo`() =
        runTest {
            val vm = viewModel(client = sampleClient.copy(cpf = "12345678900"), role = UserRole.MOD)
            advanceUntilIdle()

            vm.handleIntent(ClientDetailIntent.ToggleCpfReveal)
            advanceUntilIdle()

            assertEquals("123.456.789-00", vm.state.first().displayCpf)
        }

    @Test
    fun `sem sessao o CPF permanece mascarado`() =
        runTest {
            val vm = viewModel(client = sampleClient.copy(cpf = "12345678900"), role = null)
            advanceUntilIdle()

            vm.handleIntent(ClientDetailIntent.ToggleCpfReveal)
            advanceUntilIdle()

            assertEquals("***.***.789-00", vm.state.first().displayCpf)
        }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "br.com.sprena.presentation.bar.ClientDetailViewModelTest" --no-daemon
```

Esperado: FAIL na compilação — `Unresolved reference: displayCpf` / `ToggleCpfReveal`.

- [ ] **Step 3: Estender o State**

Em `ClientDetailState.kt`, adicionar os campos e o derivado (mantendo `clientCpf`, que a edição
consome), com os imports `br.com.sprena.shared.core.privacy.formatCpf` e
`br.com.sprena.shared.core.privacy.maskCpf`:

```kotlin
    val isCpfRevealed: Boolean = false,
    val canRevealCpf: Boolean = false,
```

e, no corpo da data class:

```kotlin
    /**
     * CPF como deve aparecer na tela. Mascarado por padrão; só ADM/MOD conseguem
     * revelar — [isCpfRevealed] nunca é ligado sem [canRevealCpf].
     */
    val displayCpf: String
        get() = if (isCpfRevealed) formatCpf(clientCpf) else maskCpf(clientCpf)
```

- [ ] **Step 4: Adicionar o Intent**

Em `ClientDetailIntent.kt`:

```kotlin
    /** Alterna a exibição do CPF completo. Ignorado sem permissão. */
    data object ToggleCpfReveal : ClientDetailIntent
```

- [ ] **Step 5: Resolver a permissão no ViewModel**

Em `ClientDetailViewModel.kt`, adicionar os imports

```kotlin
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
```

mudar a assinatura para

```kotlin
class ClientDetailViewModel(
    private val client: BarClient,
    private val sessionStore: SessionStore,
) : ViewModel(),
```

adicionar o bloco `init` que resolve a permissão (a role vem da sessão, não de nav args):

```kotlin
    init {
        viewModelScope.launch {
            val role = sessionStore.load()?.role
            _state.value = _state.value.copy(canRevealCpf = role != null && role in STAFF_ROLES)
        }
    }
```

tratar o intent no `handleIntent`:

```kotlin
            is ClientDetailIntent.ToggleCpfReveal -> {
                if (_state.value.canRevealCpf) {
                    _state.value = _state.value.copy(isCpfRevealed = !_state.value.isCpfRevealed)
                }
            }
```

e declarar as roles no companion existente (ou criar um `private companion object` se não houver):

```kotlin
    private companion object {
        val STAFF_ROLES = setOf(UserRole.ADM, UserRole.MOD)
    }
```

- [ ] **Step 6: Rodar os testes e confirmar que passam**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "br.com.sprena.presentation.bar.ClientDetailViewModelTest" --no-daemon
```

Esperado: PASS — os testes antigos do arquivo continuam passando e os 5 novos passam.

- [ ] **Step 7: Atualizar a UI e o call site**

Em `ClientDetailSheet.kt`, trocar a linha 135 por um `Row` com o CPF exibido e o botão de revelar,
adicionando os imports `androidx.compose.material.icons.Icons`,
`androidx.compose.material.icons.filled.Visibility`,
`androidx.compose.material.icons.filled.VisibilityOff`, `androidx.compose.material3.Icon`,
`androidx.compose.material3.IconButton`, `androidx.compose.foundation.layout.Row` e
`androidx.compose.ui.Alignment` (se ainda não estiverem no arquivo):

```kotlin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "CPF: ${state.displayCpf}")
                    if (state.canRevealCpf) {
                        IconButton(
                            onClick = { viewModel.handleIntent(ClientDetailIntent.ToggleCpfReveal) },
                        ) {
                            Icon(
                                imageVector =
                                    if (state.isCpfRevealed) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                contentDescription =
                                    if (state.isCpfRevealed) "Ocultar CPF" else "Revelar CPF",
                            )
                        }
                    }
                }
```

> Se o `Text` original tiver `style`/`color`, preservar os mesmos parâmetros no `Text` de dentro do
> `Row`.

Em `NavGraph.kt` (linha ~856), trocar a construção do ViewModel por:

```kotlin
                ClientDetailViewModel(client = selectedClient, sessionStore = koinInject())
```

- [ ] **Step 8: Verificar build e suíte completa**

```bash
./gradlew :composeApp:assembleDebug --no-daemon && ./gradlew :composeApp:testDebugUnitTest --no-daemon
```

Esperado: BUILD SUCCESSFUL + todos os testes passando.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail \
        composeApp/src/commonTest/kotlin/br/com/sprena/presentation/bar/ClientDetailViewModelTest.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
git commit -m "feat(privacy): CPF mascarado no detalhe com revelacao restrita a ADM e MOD"
```

---

### Task 10: Documentação e verificação final

**Files:**
- Modify: `SECURITY.md`
- Modify: `ARCHITECTURE.md`
- Modify: `ROADMAP.md`

**Interfaces:**
- Consumes: tudo das Tasks 1–9.
- Produces: nada de código.

- [ ] **Step 1: Documentar em `SECURITY.md`**

Adicionar uma seção `## F1.5 — Baseline LGPD` seguindo o formato das seções existentes, cobrindo:

- **Base legal e escopo:** consentimento do usuário do app registrado no aceite; dados de clientes
  cadastrados tratados pelo operador, que declara ter autorização do titular ao cadastrar.
- **O que é coletado:** email, role e último acesso do usuário; nome, apelido, CPF, telefone, email,
  modalidades, presenças e histórico de pagamento dos clientes.
- **Onde o aceite é gravado:** `user_consents/{uid}` (doc corrente) e
  `user_consents/{uid}/history/{policyVersion}` (append-only, sustenta o ônus da prova do art. 8 §1).
  Escrita restrita ao próprio uid pelas rules; delete negado para todos.
- **Por que não em `users/{uid}`:** as rules de F1.4 negam escrita do app naquele doc, para impedir
  auto-promoção de role.
- **Gate fail-closed:** falha de leitura vira bloqueio com retry, nunca acesso liberado.
- **Versionamento:** `PrivacyPolicy.VERSION` casa com a linha `Versão` do texto embarcado; mudou o
  texto, todos reaceitam.
- **Masking de CPF:** mascarado por padrão na exibição; revelação restrita a ADM/MOD, decidida no
  ViewModel a partir da sessão. Campos de edição seguem completos porque as rules já exigem staff
  para escrever em `sport_clients`.
- **Limite conhecido:** o masking é controle de UI. Quem chamar a API do Firestore direto lê o CPF
  completo — as rules permitem leitura a qualquer autenticado (`sport_clients`). Restringir isso é
  decisão de F2/RBAC, não de F1.5.

- [ ] **Step 2: Documentar em `ARCHITECTURE.md`**

Registrar o módulo `shared/privacy` (domain em commonMain, data em androidMain), a nova coleção
Firestore, o gate no NavGraph e `shared/core/privacy/CpfMasker`. Marcar F1.5 como concluída onde a
lista de sub-fases aparece.

- [ ] **Step 3: Atualizar o `ROADMAP.md`**

Marcar F1.5 como concluída:

```markdown
- ✅ **F1.5** — Consentimento LGPD + política de privacidade + masking de CPF
```

Adicionar a sub-fase nova logo abaixo:

```markdown
- ⬜ **F1.6** — Direitos do titular (LGPD art. 18): acesso, exportação e exclusão de dados.
  Inclui exclusão de conta in-app — **exigência da Play Store** para apps com login, bloqueia
  publicação. Depende de decidir retenção/anonimização de dados financeiros históricos e
  provavelmente de uma Cloud Function.
```

Atualizar o bloco **Status atual** no fim do arquivo: F1 fechada (F1.1–F1.5), F1.6 e F2–F6 pendentes.
Manter a **Pendência operacional** do App Check e acrescentar a de F1.5: as rules de `user_consents`
só valem em produção após `firebase deploy --only firestore:rules`.

- [ ] **Step 4: Rodar a suíte completa de verificação**

```bash
./gradlew ktlintCheck --no-daemon
./gradlew detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug --no-daemon
./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon
./gradlew :composeApp:lint --no-daemon
./gradlew :composeApp:assembleDebug --no-daemon
cd tools/firestore-rules-tests && npm run test:emulator
```

Todos precisam passar. Se o detekt reclamar de complexidade em `NavGraph.kt` (arquivo já grande),
**não** suprimir com baseline novo sem checar: extrair o bloco reclamado para uma função privada
resolve na maioria dos casos.

- [ ] **Step 5: Commit e push**

```bash
git add SECURITY.md ARCHITECTURE.md ROADMAP.md
git commit -m "docs(security): documentar baseline LGPD de F1.5 e abrir F1.6"
git push -u origin feature/f1-5-lgpd-baseline
```

- [ ] **Step 6: Abrir o PR**

```bash
gh pr create --title "F1.5: baseline LGPD (consentimento + política + masking de CPF)" --body-file - <<'MD'
## O que entra

- Gate de consentimento no cold start e no login, fail-closed: falha de leitura leva à tela de
  aceite com retry, nunca à Home.
- Aceite gravado em `user_consents/{uid}` + `history/{policyVersion}` append-only, com rules
  restringindo escrita ao próprio uid e negando delete.
- Política de privacidade versionada (`PrivacyPolicy.VERSION = "2026-08-12"`), embarcada em
  `composeResources` como fonte única, acessível pelo Settings a qualquer momento.
- CPF mascarado na exibição (`***.***.789-00`); revelação restrita a ADM/MOD, decidida no ViewModel.

## Testes

- 8 testes de `CpfMasker`, 6 de use cases de consentimento, 7 de `ConsentViewModel`, 5 de masking no
  `ClientDetailViewModel`, 6 novos casos de rules no emulador (18 no total).

## Ação obrigatória pós-merge

```
firebase deploy --only firestore:rules --project <projeto>
```

Sem isso, as rules de `user_consents` não valem em produção e o aceite falha ao gravar.

## Revisão pendente do mantenedor

O texto da política é um baseline técnico que descreve fielmente o que o app faz — **não é
aconselhamento jurídico**. Revisar antes de publicar, e preencher o contato do item 8 conforme a
operação real.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
MD
```

---

## Notas de verificação do plano

**Cobertura da spec:** consentimento de usuário (Tasks 2, 4, 6, 7) · registro de base legal do
cliente cadastrado (item 4 do texto da política, Task 5) · política versionada e embarcada
(Task 5) · acesso fora do gate (Task 8) · masking com revelação por role (Tasks 1, 9) · rules e
testes (Task 3) · fail-closed (Tasks 2, 6, 7) · docs e F1.6 (Task 10).

**Fora de escopo (confirmado na spec):** direitos do art. 18, Consent Mode v2, hash de CPF em
repouso, retenção automática.
