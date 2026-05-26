# F1.3 — Firebase Auth real + Sessão Criptografada Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir `MockAuthRepository` por Firebase Authentication real (email + senha), adicionar sessão criptografada local em DataStore com TTL de 24h, reset de senha, auto-login no cold start e logout em Settings.

**Architecture:** Quatro unidades coordenadas. (1) `shared/auth/data` ganha `FirebaseAuthRepositoryImpl` em androidMain. (2) `shared/auth/session` nasce com `SessionUser` + `SessionValidator` (pure, commonMain) + `SessionStore` interface + `EncryptedSessionStore` impl (Tink AEAD + DataStore Preferences, androidMain). (3) `shared/auth/domain` ganha 3 use cases novos (`Logout`, `RequestPasswordReset`, `RestoreSession`) e o `LoginUseCase` é refatorado para persistir sessão. (4) `composeApp/presentation` refatora `LoginScreen` (email/senha + esqueci-a-senha), `SettingsScreen` (seção Conta + Sair) e `NavGraph` (startDestination dinâmico baseado em `RestoreSessionUseCase`). Todos os modelos campos `username` viram `email`.

**Tech Stack:** Firebase Auth (via BOM 34.12.0 já presente), Google Tink 1.13.0 (AEAD AES-256-GCM com chave no Android Keystore), AndroidX DataStore Preferences 1.1.1, Koin 4.0.2 (DI existente), Logger F1.2 (Napier + Crashlytics) com `PiiMasker.email`.

**Spec:** [`docs/superpowers/specs/2026-05-25-f1-3-firebase-auth-design.md`](../specs/2026-05-25-f1-3-firebase-auth-design.md)

---

## Premissas

- Branch base: `master` atualizado após merge de F1.2 PR #10.
- Branch desta sub-fase: `feature/f1-3-firebase-auth`.
- F1.2 entregou `Logger` interface e `PiiMasker.email(...)` — reusados aqui sem mudanças.
- Detekt + ktlint + CI continuam passando (baseline F0 + ajustes F1.2).
- Comando CI-equivalente local (memória `sprena_tooling`):
  ```
  ./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest :composeApp:lint :composeApp:assembleDebug
  ```
- Firebase Console: criar 1 usuário test (email + senha) e doc Firestore `users/{uid}` com `role=ADM` ANTES da verificação manual final.

## Estrutura de arquivos

**Novos (commonMain — pure Kotlin):**
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/time/Clock.kt` — interface `Clock { fun nowEpochMillis(): Long }`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionUser.kt` — `data class SessionUser(uid, email, role, lastLoginEpochMillis)`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionValidator.kt` — `isExpired(lastLogin, now, ttlMillis)`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionStore.kt` — interface
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/PasswordResetResult.kt` — sealed
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/RestoreResult.kt` — sealed
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCase.kt`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCase.kt`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCase.kt`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.kt` — `expect fun sessionModule(): Module`

**Novos (commonTest):**
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/session/SessionValidatorTest.kt`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCaseTest.kt`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCaseTest.kt`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCaseTest.kt`
- Helpers em testes: `FakeAuthRepository`, `FakeSessionStore`, `FixedClock` (inline em cada teste, ou em `commonTest/.../auth/_fakes/`)

**Novos (androidMain):**
- `shared/src/androidMain/kotlin/br/com/sprena/shared/core/time/SystemClock.kt` — impl que usa `System.currentTimeMillis()`
- `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/FirebaseAuthRepositoryImpl.kt`
- `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/session/EncryptedSessionStore.kt`
- `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.android.kt` — `actual fun sessionModule()`

**Novos (composeApp commonMain):**
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/ForgotPasswordDialog.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsState.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsIntent.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsEffect.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsViewModel.kt`

**Modificados:**
- `gradle/libs.versions.toml` — versões `datastore`, `tink-android`; libs `androidx-datastore-preferences`, `tink-android`, `firebase-auth`
- `shared/build.gradle.kts` — `firebase-auth` em androidMain; `datastore-preferences` + `tink-android` em androidMain
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/UserModel.kt` — `username` → `email`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/repository/AuthRepository.kt` — adiciona `sendPasswordReset`, `signOut`, `currentUid`; renomeia arg `username` para `email`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidator.kt` — `validateEmail` substitui `validateUsername`; `validatePassword` muda para mínimo 6 chars (qualquer caractere)
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCase.kt` — recebe `SessionStore` + `Clock`; em sucesso, persiste sessão
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/AuthModule.kt` — wire `LogoutUseCase`, `RequestPasswordResetUseCase`, `RestoreSessionUseCase`, `Clock`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt` — **DELETE** (substituído por FirebaseAuthRepositoryImpl em androidMain)
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCaseTest.kt` — adapta com `FakeSessionStore` e `FixedClock`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidatorTest.kt` — testes para `validateEmail` (substituem testes de `validateUsername`)
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginState.kt` — `email` substitui `username`; adiciona `passwordResetDialogOpen`, `passwordResetEmail`, `passwordResetSending`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginIntent.kt` — adiciona `RequestPasswordReset`, `UpdatePasswordResetEmail`, `SubmitPasswordReset`, `DismissPasswordResetDialog`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginEffect.kt` — adiciona `ShowPasswordResetSent`, `ShowPasswordResetError(message)`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginViewModel.kt` — handles novos intents, chama `RequestPasswordResetUseCase`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginScreen.kt` — input email + senha + link "Esqueci a senha"
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsScreen.kt` — adiciona seção "Conta" + botão Sair
- `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/LoginViewModelTest.kt` — adapta com `email` e nova validação
- `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt` — startDestination dinâmico via `RestoreSessionUseCase`
- `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt` — registra `SettingsViewModel`
- `composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt` — provê `FirebaseAuth`, `FirebaseAuthRepositoryImpl`, `Clock`
- `composeApp/proguard-rules.pro` — regras Tink + DataStore + Firebase Auth
- `SECURITY.md` — seção "F1.3 — Firebase Auth + Sessão Criptografada"
- `ARCHITECTURE.md` — atualiza Segurança incluindo F1.3

---

## Task 1: Branch + baseline

**Files:**
- (none — git operation)

- [ ] **Step 1: Sincronizar master**

Run:
```bash
git fetch origin
git checkout master
git pull origin master --ff-only
```

Expected: master no mesmo SHA de origin/master. **Pré-requisito: F1.2 PR #10 deve estar mergeada.** Se ainda OPEN, parar e perguntar a Pedro.

- [ ] **Step 2: Criar branch**

Run:
```bash
git checkout -b feature/f1-3-firebase-auth
```

- [ ] **Step 3: Baseline pipeline**

Run:
```bash
./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest :composeApp:lint :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Se falhar, investigar regressão em master antes de prosseguir.

- [ ] **Step 4: Mover spec + plan para git**

Copiar (do working tree corrente do executor, se for fresh checkout regerar pela skill) os arquivos:
- `docs/superpowers/specs/2026-05-25-f1-3-firebase-auth-design.md`
- `docs/superpowers/plans/2026-05-25-f1-3-firebase-auth.md`

Run:
```bash
git add docs/superpowers/specs/2026-05-25-f1-3-firebase-auth-design.md docs/superpowers/plans/2026-05-25-f1-3-firebase-auth.md
git commit -m "docs(plan): add f1.3 firebase-auth spec + implementation plan"
```

---

## Task 2: Adicionar dependências (gradle/libs.versions.toml + shared/build.gradle.kts)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Adicionar versões em `[versions]`**

Em `gradle/libs.versions.toml`, no bloco `[versions]`, adicionar duas seções novas (alfabetizar mantendo o estilo atual). Acrescentar APÓS `firebase-bom`:

```toml
# --- AndroidX DataStore ---
androidx-datastore = "1.1.1"

# --- Cripto (Tink) ---
tink-android = "1.13.0"
```

- [ ] **Step 2: Adicionar libs em `[libraries]`**

Após `firebase-crashlytics = ...`, adicionar:

```toml
firebase-auth = { module = "com.google.firebase:firebase-auth" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidx-datastore" }
tink-android = { module = "com.google.crypto.tink:tink-android", version.ref = "tink-android" }
```

- [ ] **Step 3: Atualizar `shared/build.gradle.kts`**

No bloco `androidMain.dependencies`, depois de `implementation(libs.firebase.crashlytics)`, adicionar:

```kotlin
implementation(libs.firebase.auth)
implementation(libs.androidx.datastore.preferences)
implementation(libs.tink.android)
```

- [ ] **Step 4: Validar sync**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build(deps): add firebase-auth, datastore-preferences and tink-android"
```

---

## Task 3: TDD `Clock` interface + `SystemClock` impl

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/time/Clock.kt`
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/core/time/SystemClock.kt`

- [ ] **Step 1: Criar `Clock.kt` (commonMain)**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/core/time/Clock.kt`:

```kotlin
package br.com.sprena.shared.core.time

/**
 * Abstração de tempo para injeção. Em produção: [SystemClock] (Android).
 * Em testes: fakes que retornam timestamps fixos.
 */
interface Clock {
    fun nowEpochMillis(): Long
}
```

- [ ] **Step 2: Criar `SystemClock.kt` (androidMain)**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.core.time

class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/time/Clock.kt \
        shared/src/androidMain/kotlin/br/com/sprena/shared/core/time/SystemClock.kt
git commit -m "feat(core): add Clock abstraction with Android SystemClock"
```

---

## Task 4: TDD `SessionUser` + `SessionValidator`

**Files:**
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/session/SessionValidatorTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionUser.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionValidator.kt`

- [ ] **Step 1: Escrever testes (red)**

Conteúdo INTEIRO de `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/session/SessionValidatorTest.kt`:

```kotlin
package br.com.sprena.shared.auth.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionValidatorTest {
    private val ttl = 24L * 60L * 60L * 1000L // 24h

    @Test
    fun `not expired when delta is zero`() {
        val now = 1_700_000_000_000L
        assertFalse(SessionValidator.isExpired(lastLoginEpochMillis = now, nowEpochMillis = now, ttlMillis = ttl))
    }

    @Test
    fun `not expired when delta is one millisecond before ttl`() {
        val last = 1_700_000_000_000L
        val now = last + ttl - 1L
        assertFalse(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when delta equals ttl exactly`() {
        val last = 1_700_000_000_000L
        val now = last + ttl
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when delta greater than ttl`() {
        val last = 1_700_000_000_000L
        val now = last + ttl + 1L
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `expired when lastLogin is in the future (clock skew)`() {
        val now = 1_700_000_000_000L
        val last = now + 5_000L
        assertTrue(SessionValidator.isExpired(last, now, ttl))
    }

    @Test
    fun `default ttl is 24 hours`() {
        val last = 1_700_000_000_000L
        val almost24h = last + (24L * 60L * 60L * 1000L) - 1L
        assertFalse(SessionValidator.isExpired(last, almost24h))
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SessionValidatorTest*"`
Expected: FAILED — `Unresolved reference: SessionValidator`.

- [ ] **Step 3: Criar `SessionUser.kt` (commonMain)**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.session

import br.com.sprena.shared.auth.domain.model.UserRole

/**
 * Snapshot da sessão persistido localmente (cifrado).
 *
 * Não inclui token Firebase — o SDK persiste por conta própria.
 * Inclui [lastLoginEpochMillis] para validação de TTL (24h).
 */
data class SessionUser(
    val uid: String,
    val email: String,
    val role: UserRole,
    val lastLoginEpochMillis: Long,
)
```

- [ ] **Step 4: Criar `SessionValidator.kt` (commonMain)**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.session

/**
 * Validação de TTL de sessão.
 *
 * Default: 24h (8.64e7 ms). Sessão expira quando `now - lastLogin >= ttl`.
 * Também trata "clock skew" — se lastLogin é maior que now, considera expirada
 * (defesa contra device com data trocada).
 */
object SessionValidator {
    const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun isExpired(
        lastLoginEpochMillis: Long,
        nowEpochMillis: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): Boolean {
        if (lastLoginEpochMillis > nowEpochMillis) return true
        return (nowEpochMillis - lastLoginEpochMillis) >= ttlMillis
    }
}
```

- [ ] **Step 5: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SessionValidatorTest*"`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionUser.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionValidator.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/session/SessionValidatorTest.kt
git commit -m "feat(auth): add SessionUser model and SessionValidator with TTL=24h (TDD)"
```

---

## Task 5: Criar `SessionStore` interface (commonMain)

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionStore.kt`

- [ ] **Step 1: Criar interface**

Conteúdo INTEIRO:

```kotlin
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
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/session/SessionStore.kt
git commit -m "feat(auth): add SessionStore interface"
```

---

## Task 6: Renomear `UserModel.username` para `email` e atualizar call sites

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/UserModel.kt`
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt` (será deletado em Task 13; por ora, ajustar)
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginScreen.kt`
- Modify: `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/LoginViewModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`

- [ ] **Step 1: Atualizar `UserModel.kt`**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/UserModel.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.model

/**
 * Representa o usuário autenticado no Sprena.
 *
 * @property id Identificador único (uid do Firebase Auth)
 * @property email Email do usuário (login)
 * @property name Nome para exibição na UI
 * @property role Perfil de acesso
 */
data class UserModel(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
)
```

- [ ] **Step 2: Atualizar os 4 outros arquivos**

Em cada arquivo abaixo, fazer find-and-replace literal:
- Property access: `.username` → `.email`
- Construtor: `username = ` → `email = `
- Argumentos posicionais: contexto-dependente, geralmente o segundo arg do UserModel

Arquivos a atualizar:
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginViewModel.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginScreen.kt`
- `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/LoginViewModelTest.kt`
- `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`

**Atenção:** se algum arquivo tiver UI strings em PT-BR com "usuário" (label de campo), NÃO mudar agora — Task 14 (UI refactor) cuida das strings.

- [ ] **Step 3: Build + testes**

Run: `./gradlew :composeApp:assembleDebug :shared:testDebugUnitTest :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Se algum teste falhar com mensagem do tipo "expected: email got: username" ou similar de assertion em valores, deixar para Task 14 ajustar — desde que compile. Se for erro de compilação, parar e corrigir aqui.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/UserModel.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/ \
        composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/LoginViewModelTest.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
git commit -m "refactor(auth): rename UserModel.username to email"
```

---

## Task 7: Atualizar `AuthRepository` interface

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/repository/AuthRepository.kt`
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt`

- [ ] **Step 1: Atualizar `AuthRepository.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.repository

import br.com.sprena.shared.auth.domain.model.AuthResult

/**
 * Contrato do repositório de autenticação.
 *
 * Implementação concreta em `shared/androidMain`: `FirebaseAuthRepositoryImpl`.
 */
interface AuthRepository {
    /**
     * Autentica com [email] e [password].
     * Em sucesso, retorna [AuthResult.Success] com `UserModel` populado a partir
     * do Firebase Auth + doc `users/{uid}` no Firestore.
     */
    suspend fun authenticate(email: String, password: String): AuthResult

    /**
     * Envia email de reset de senha. `Result.failure` se rede/Firebase falhar.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Encerra a sessão Firebase Auth local (não invalida no servidor).
     */
    suspend fun signOut()

    /**
     * Retorna o uid do usuário atualmente autenticado no Firebase Auth, ou null.
     * Não depende de cache local — consulta `FirebaseAuth.currentUser`.
     */
    fun currentUid(): String?
}
```

- [ ] **Step 2: Atualizar `MockAuthRepository` para satisfazer a nova interface**

(Vai ser deletado em Task 13, mas precisa compilar até lá.)

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt`:

```kotlin
package br.com.sprena.shared.auth.data.repository

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository

/**
 * Mock — será DELETADO em F1.3 Task 13 quando FirebaseAuthRepositoryImpl entrar.
 * Mantido aqui apenas para compilação intermediária.
 */
class MockAuthRepository : AuthRepository {
    override suspend fun authenticate(email: String, password: String): AuthResult =
        AuthResult.Success(
            UserModel(id = "mock", email = email, name = "Mock User", role = UserRole.ADM),
        )

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut() = Unit

    override fun currentUid(): String? = null
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/repository/AuthRepository.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt
git commit -m "feat(auth): expand AuthRepository with reset/signOut/currentUid"
```

---

## Task 8: TDD `LoginValidator` (email + senha ≥ 6)

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidator.kt`
- Modify: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidatorTest.kt` (ou criar se não existir)

- [ ] **Step 1: Reescrever `LoginValidatorTest.kt`**

Substituir o conteúdo INTEIRO por:

```kotlin
package br.com.sprena.shared.auth.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginValidatorTest {
    // --- validateEmail ---
    @Test
    fun `validateEmail accepts a typical email`() {
        assertTrue(LoginValidator.validateEmail("pedro@gmail.com").isValid)
    }

    @Test
    fun `validateEmail rejects blank`() {
        val result = LoginValidator.validateEmail("")
        assertEquals(false, result.isValid)
        assertEquals("Email é obrigatório", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects whitespace only`() {
        assertEquals(false, LoginValidator.validateEmail("   ").isValid)
    }

    @Test
    fun `validateEmail rejects missing arroba`() {
        val result = LoginValidator.validateEmail("pedro.gmail.com")
        assertEquals(false, result.isValid)
        assertEquals("Email inválido", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects missing dot`() {
        assertEquals(false, LoginValidator.validateEmail("pedro@gmail").isValid)
    }

    @Test
    fun `validateEmail rejects internal whitespace`() {
        assertEquals(false, LoginValidator.validateEmail("pe dro@gmail.com").isValid)
    }

    @Test
    fun `validateEmail rejects over 254 chars`() {
        val long = "a".repeat(250) + "@b.co" // 255 chars total
        assertEquals(false, LoginValidator.validateEmail(long).isValid)
    }

    @Test
    fun `validateEmail trims and accepts`() {
        assertTrue(LoginValidator.validateEmail("  pedro@gmail.com  ").isValid)
    }

    // --- validatePassword ---
    @Test
    fun `validatePassword accepts 6 chars`() {
        assertTrue(LoginValidator.validatePassword("abc123").isValid)
    }

    @Test
    fun `validatePassword accepts mixed chars`() {
        assertTrue(LoginValidator.validatePassword("S3nha@x").isValid)
    }

    @Test
    fun `validatePassword rejects blank`() {
        val result = LoginValidator.validatePassword("")
        assertEquals(false, result.isValid)
        assertEquals("Senha é obrigatória", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects fewer than 6 chars`() {
        val result = LoginValidator.validatePassword("abc12")
        assertEquals(false, result.isValid)
        assertEquals("Senha deve ter no mínimo 6 caracteres", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects leading whitespace`() {
        assertEquals(false, LoginValidator.validatePassword(" abc123").isValid)
    }

    @Test
    fun `validatePassword rejects trailing whitespace`() {
        assertEquals(false, LoginValidator.validatePassword("abc123 ").isValid)
    }
}
```

> **Nota:** se o arquivo `LoginValidatorTest.kt` original tinha testes para `validateUsername`, eles são substituídos integralmente — `validateUsername` deixa de existir nesta sub-fase.

- [ ] **Step 2: Rodar — deve falhar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LoginValidatorTest*"`
Expected: vai falhar com `Unresolved reference: validateEmail` ou similar.

- [ ] **Step 3: Reescrever `LoginValidator.kt`**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidator.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object LoginValidator {
    const val EMAIL_MAX_LENGTH: Int = 254
    const val PASSWORD_MIN_LENGTH: Int = 6

    // Regex simples — não pretende cobrir todo o RFC 5322, apenas o caso comum.
    private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun validateEmail(value: String): ValidationResult {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Email é obrigatório")
            trimmed.length > EMAIL_MAX_LENGTH -> ValidationResult.invalid("Email muito longo")
            !EMAIL_REGEX.matches(trimmed) -> ValidationResult.invalid("Email inválido")
            else -> ValidationResult.Valid
        }
    }

    fun validatePassword(value: String): ValidationResult =
        when {
            value.isEmpty() -> ValidationResult.invalid("Senha é obrigatória")
            value != value.trim() -> ValidationResult.invalid("Senha não pode começar ou terminar com espaço")
            value.length < PASSWORD_MIN_LENGTH ->
                ValidationResult.invalid("Senha deve ter no mínimo $PASSWORD_MIN_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
}
```

- [ ] **Step 4: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LoginValidatorTest*"`
Expected: BUILD SUCCESSFUL, 14 tests passed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidator.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/validation/LoginValidatorTest.kt
git commit -m "feat(auth): replace username validation with email + 6-char password (TDD)"
```

---

## Task 9: Refatorar `LoginUseCase` para persistir sessão (TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCase.kt`
- Modify: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCaseTest.kt`

- [ ] **Step 1: Atualizar `LoginUseCaseTest.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginUseCaseTest {
    private class FakeAuthRepository(
        var nextResult: AuthResult =
            AuthResult.Success(
                UserModel(id = "u1", email = "a@b.com", name = "A", role = UserRole.ADM),
            ),
    ) : AuthRepository {
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun authenticate(email: String, password: String): AuthResult {
            lastEmail = email
            lastPassword = password
            return nextResult
        }
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut() = Unit
        override fun currentUid(): String? = null
    }

    private class FakeSessionStore : SessionStore {
        var saved: SessionUser? = null
        var cleared = false
        override suspend fun save(user: SessionUser) { saved = user }
        override suspend fun load(): SessionUser? = saved
        override suspend fun clear() { saved = null; cleared = true }
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    @Test
    fun `returns Error and does not persist when email invalid`() = runTest {
        val repo = FakeAuthRepository()
        val store = FakeSessionStore()
        val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        val result = useCase("nao-eh-email", "abc123")

        assertTrue(result is AuthResult.Error)
        assertNull(store.saved)
        assertNull(repo.lastEmail)
    }

    @Test
    fun `returns Error and does not persist when password invalid`() = runTest {
        val repo = FakeAuthRepository()
        val store = FakeSessionStore()
        val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        val result = useCase("ok@ex.com", "12345") // < 6

        assertTrue(result is AuthResult.Error)
        assertNull(store.saved)
    }

    @Test
    fun `delegates to repository when validation passes`() = runTest {
        val repo = FakeAuthRepository()
        val store = FakeSessionStore()
        val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        useCase("ok@ex.com", "abc123")

        assertEquals("ok@ex.com", repo.lastEmail)
        assertEquals("abc123", repo.lastPassword)
    }

    @Test
    fun `persists session with clock timestamp on success`() = runTest {
        val repo =
            FakeAuthRepository(
                nextResult =
                    AuthResult.Success(
                        UserModel(id = "u42", email = "ok@ex.com", name = "P", role = UserRole.MOD),
                    ),
            )
        val store = FakeSessionStore()
        val useCase = LoginUseCase(repo, store, FixedClock(now = 9_999L), NoOpLogger())

        useCase("ok@ex.com", "abc123")

        assertEquals(
            SessionUser(uid = "u42", email = "ok@ex.com", role = UserRole.MOD, lastLoginEpochMillis = 9_999L),
            store.saved,
        )
    }

    @Test
    fun `does not persist when repository returns Error`() = runTest {
        val repo = FakeAuthRepository(nextResult = AuthResult.Error("falhou"))
        val store = FakeSessionStore()
        val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        val result = useCase("ok@ex.com", "abc123")

        assertTrue(result is AuthResult.Error)
        assertNull(store.saved)
    }
}
```

- [ ] **Step 2: Rodar — deve falhar (compilação)**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LoginUseCaseTest*"`
Expected: FAIL com `Unresolved reference: SessionStore` ou similar — porque LoginUseCase ainda só recebe authRepository + logger.

- [ ] **Step 3: Reescrever `LoginUseCase.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.time.Clock

/**
 * Caso de uso de login.
 *
 * 1. Valida email e senha (ver [LoginValidator])
 * 2. Delega ao [AuthRepository]
 * 3. Em sucesso, persiste [SessionUser] em [SessionStore] com timestamp do [Clock]
 *
 * Loga warn em rejeições, info em sucesso — sempre via [Logger] com email mascarado.
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        val emailResult = LoginValidator.validateEmail(email)
        if (!emailResult.isValid) {
            logger.warn(TAG, "login rejected invalid email")
            return AuthResult.Error(emailResult.errorMessage ?: "Email inválido")
        }

        val passwordResult = LoginValidator.validatePassword(password)
        if (!passwordResult.isValid) {
            logger.warn(TAG, "login rejected invalid password for email=$email")
            return AuthResult.Error(passwordResult.errorMessage ?: "Senha inválida")
        }

        val result = authRepository.authenticate(email.trim(), password)
        if (result is AuthResult.Success) {
            sessionStore.save(
                SessionUser(
                    uid = result.user.id,
                    email = result.user.email,
                    role = result.user.role,
                    lastLoginEpochMillis = clock.nowEpochMillis(),
                ),
            )
            logger.info(TAG, "login ok email=$email")
        } else {
            logger.warn(TAG, "login failed email=$email")
        }
        return result
    }

    private companion object {
        const val TAG = "LoginUseCase"
    }
}
```

- [ ] **Step 4: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LoginUseCaseTest*"`
Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCase.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCaseTest.kt
git commit -m "feat(auth): persist session on successful login (TDD)"
```

---

## Task 10: TDD `LogoutUseCase`

**Files:**
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCaseTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCase.kt`

- [ ] **Step 1: Escrever testes**

Conteúdo INTEIRO de `LogoutUseCaseTest.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogoutUseCaseTest {
    private class FakeAuthRepo : AuthRepository {
        var signOutCalled = false
        override suspend fun authenticate(email: String, password: String) =
            AuthResult.Error("not used")
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut() { signOutCalled = true }
        override fun currentUid(): String? = null
    }

    private class FakeStore(var current: SessionUser? = null) : SessionStore {
        var cleared = false
        override suspend fun save(user: SessionUser) { current = user }
        override suspend fun load(): SessionUser? = current
        override suspend fun clear() { current = null; cleared = true }
    }

    @Test
    fun `signs out and clears session`() = runTest {
        val repo = FakeAuthRepo()
        val store = FakeStore()
        val useCase = LogoutUseCase(repo, store, NoOpLogger())

        useCase()

        assertTrue(repo.signOutCalled)
        assertTrue(store.cleared)
        assertNull(store.current)
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LogoutUseCaseTest*"`
Expected: FAIL `Unresolved reference: LogoutUseCase`.

- [ ] **Step 3: Implementar**

Conteúdo INTEIRO de `LogoutUseCase.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger

/**
 * Encerra a sessão: Firebase signOut + limpa sessão local.
 */
class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val logger: Logger,
) {
    suspend operator fun invoke() {
        authRepository.signOut()
        sessionStore.clear()
        logger.info(TAG, "user logged out")
    }

    private companion object {
        const val TAG = "LogoutUseCase"
    }
}
```

- [ ] **Step 4: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LogoutUseCaseTest*"`
Expected: 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCase.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LogoutUseCaseTest.kt
git commit -m "feat(auth): add LogoutUseCase (signOut + clear session) with TDD"
```

---

## Task 11: TDD `RequestPasswordResetUseCase`

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/PasswordResetResult.kt`
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCaseTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCase.kt`

- [ ] **Step 1: Criar `PasswordResetResult.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.model

sealed interface PasswordResetResult {
    data object Sent : PasswordResetResult
    data class InvalidEmail(val message: String) : PasswordResetResult
    data object NetworkError : PasswordResetResult
    data class UnknownError(val message: String) : PasswordResetResult
}
```

- [ ] **Step 2: Escrever testes**

Conteúdo INTEIRO de `RequestPasswordResetUseCaseTest.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestPasswordResetUseCaseTest {
    private class FakeAuthRepo(
        var nextResult: Result<Unit> = Result.success(Unit),
    ) : AuthRepository {
        var lastEmail: String? = null
        override suspend fun authenticate(email: String, password: String) =
            AuthResult.Error("not used")
        override suspend fun sendPasswordReset(email: String): Result<Unit> {
            lastEmail = email
            return nextResult
        }
        override suspend fun signOut() = Unit
        override fun currentUid(): String? = null
    }

    @Test
    fun `returns InvalidEmail without calling repo when email is malformed`() = runTest {
        val repo = FakeAuthRepo()
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("no-arroba")

        assertTrue(result is PasswordResetResult.InvalidEmail)
        assertNull(repo.lastEmail)
    }

    @Test
    fun `returns Sent when repo succeeds`() = runTest {
        val repo = FakeAuthRepo()
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertEquals(PasswordResetResult.Sent, result)
        assertEquals("pedro@gmail.com", repo.lastEmail)
    }

    @Test
    fun `returns NetworkError when repo throws network exception`() = runTest {
        val repo =
            FakeAuthRepo(
                nextResult = Result.failure(java.net.UnknownHostException("offline")),
            )
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertEquals(PasswordResetResult.NetworkError, result)
    }

    @Test
    fun `returns UnknownError for other failures`() = runTest {
        val repo = FakeAuthRepo(nextResult = Result.failure(RuntimeException("boom")))
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertTrue(result is PasswordResetResult.UnknownError)
    }
}
```

> **Atenção KMP:** `java.net.UnknownHostException` é JVM. Se o módulo `commonTest` rejeitar (commonMain é multiplatform), substituir por uma exception customizada ou mover este teste para `androidUnitTest`. **Validar no Step 3 — se falhar, mover o arquivo de `commonTest` para `shared/src/androidUnitTest/kotlin/br/com/sprena/shared/auth/domain/usecase/`.**

- [ ] **Step 3: Rodar — verificar comportamento**

Run: `./gradlew :shared:testDebugUnitTest --tests "*RequestPasswordResetUseCaseTest*"`

Esperado-A: FAILED (Unresolved reference) — bom, segue para Step 4.
Esperado-B: erro de compilação com `java.net.UnknownHostException` — neste caso, mover o arquivo para `shared/src/androidUnitTest/...` antes do Step 4.

- [ ] **Step 4: Implementar `RequestPasswordResetUseCase.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.logger.Logger

/**
 * Solicita reset de senha por email.
 *
 * 1. Valida o email com [LoginValidator] (não bate na rede se inválido)
 * 2. Chama [AuthRepository.sendPasswordReset]
 * 3. Mapeia falhas para [PasswordResetResult]
 */
class RequestPasswordResetUseCase(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(email: String): PasswordResetResult {
        val emailResult = LoginValidator.validateEmail(email)
        if (!emailResult.isValid) {
            return PasswordResetResult.InvalidEmail(emailResult.errorMessage ?: "Email inválido")
        }

        return authRepository.sendPasswordReset(email.trim()).fold(
            onSuccess = {
                logger.info(TAG, "password reset email sent to email=$email")
                PasswordResetResult.Sent
            },
            onFailure = { e ->
                logger.warn(TAG, "password reset failed for email=$email", e)
                if (isNetworkError(e)) {
                    PasswordResetResult.NetworkError
                } else {
                    PasswordResetResult.UnknownError(e.message ?: "erro desconhecido")
                }
            },
        )
    }

    private fun isNetworkError(e: Throwable): Boolean {
        val name = e::class.simpleName ?: ""
        return name.contains("UnknownHostException") ||
            name.contains("FirebaseNetworkException") ||
            name.contains("IOException")
    }

    private companion object {
        const val TAG = "PasswordResetUseCase"
    }
}
```

- [ ] **Step 5: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*RequestPasswordResetUseCaseTest*"`
Expected: 4 tests passed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/PasswordResetResult.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCase.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCaseTest.kt
# Se o teste foi movido para androidUnitTest, ajustar o path:
# git add shared/src/androidUnitTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RequestPasswordResetUseCaseTest.kt
git commit -m "feat(auth): add RequestPasswordResetUseCase with email validation (TDD)"
```

---

## Task 12: TDD `RestoreSessionUseCase`

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/RestoreResult.kt`
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCaseTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCase.kt`

- [ ] **Step 1: Criar `RestoreResult.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.model

import br.com.sprena.shared.auth.session.SessionUser

sealed interface RestoreResult {
    data class Authenticated(val user: SessionUser) : RestoreResult
    data object NotAuthenticated : RestoreResult
}
```

- [ ] **Step 2: Escrever testes**

Conteúdo INTEIRO de `RestoreSessionUseCaseTest.kt`:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.auth.session.SessionValidator
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreSessionUseCaseTest {
    private class FakeRepo(var uid: String? = null) : AuthRepository {
        var signOutCalled = false
        override suspend fun authenticate(email: String, password: String) =
            AuthResult.Error("not used")
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut() { signOutCalled = true }
        override fun currentUid(): String? = uid
    }

    private class FakeStore(var current: SessionUser? = null) : SessionStore {
        var cleared = false
        override suspend fun save(user: SessionUser) { current = user }
        override suspend fun load(): SessionUser? = current
        override suspend fun clear() { current = null; cleared = true }
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    @Test
    fun `returns NotAuthenticated when store is empty`() = runTest {
        val repo = FakeRepo()
        val store = FakeStore(current = null)
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
    }

    @Test
    fun `returns NotAuthenticated and clears when session is expired`() = runTest {
        val last = 1_000L
        val now = last + SessionValidator.DEFAULT_TTL_MILLIS + 1L
        val repo = FakeRepo(uid = "u1")
        val store =
            FakeStore(
                current = SessionUser("u1", "a@b.com", UserRole.ADM, last),
            )
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(repo.signOutCalled)
        assertTrue(store.cleared)
        assertNull(store.current)
    }

    @Test
    fun `returns NotAuthenticated and clears when uid mismatch`() = runTest {
        val now = 1_000L
        val repo = FakeRepo(uid = "outro_uid")
        val store =
            FakeStore(
                current = SessionUser("u1", "a@b.com", UserRole.ADM, now - 1000L),
            )
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(store.cleared)
    }

    @Test
    fun `returns Authenticated when session valid and uid matches`() = runTest {
        val now = 1_000_000L
        val last = now - 5_000L
        val repo = FakeRepo(uid = "u1")
        val user = SessionUser("u1", "a@b.com", UserRole.MOD, last)
        val store = FakeStore(current = user)
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.Authenticated(user), result)
        assertEquals(false, store.cleared)
        assertEquals(false, repo.signOutCalled)
    }

    @Test
    fun `returns NotAuthenticated and clears when currentUid is null (firebase signed out)`() = runTest {
        val now = 1_000_000L
        val last = now - 5_000L
        val repo = FakeRepo(uid = null)
        val store = FakeStore(current = SessionUser("u1", "a@b.com", UserRole.ADM, last))
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(store.cleared)
    }
}
```

- [ ] **Step 3: Rodar — deve falhar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*RestoreSessionUseCaseTest*"`
Expected: FAIL.

- [ ] **Step 4: Implementar `RestoreSessionUseCase.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionValidator
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.time.Clock

/**
 * Restaura a sessão no cold start. Decide se navega para Home ou Login.
 *
 * - Sem sessão local → NotAuthenticated
 * - Sessão expirada (>= TTL) → signOut + clear → NotAuthenticated
 * - Firebase já sem currentUser (uid null) → clear → NotAuthenticated
 * - uid local != uid do Firebase → clear → NotAuthenticated
 * - Tudo OK → Authenticated(sessionUser)
 */
class RestoreSessionUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(): RestoreResult {
        val stored = sessionStore.load()
        if (stored == null) {
            logger.info(TAG, "no local session")
            return RestoreResult.NotAuthenticated
        }

        if (SessionValidator.isExpired(stored.lastLoginEpochMillis, clock.nowEpochMillis())) {
            logger.info(TAG, "session expired uid=${stored.uid}")
            authRepository.signOut()
            sessionStore.clear()
            return RestoreResult.NotAuthenticated
        }

        val currentUid = authRepository.currentUid()
        if (currentUid == null || currentUid != stored.uid) {
            logger.warn(TAG, "session uid mismatch stored=${stored.uid} firebase=$currentUid")
            sessionStore.clear()
            return RestoreResult.NotAuthenticated
        }

        logger.info(TAG, "session restored uid=${stored.uid}")
        return RestoreResult.Authenticated(stored)
    }

    private companion object {
        const val TAG = "RestoreSession"
    }
}
```

- [ ] **Step 5: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*RestoreSessionUseCaseTest*"`
Expected: 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/model/RestoreResult.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCase.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/RestoreSessionUseCaseTest.kt
git commit -m "feat(auth): add RestoreSessionUseCase for cold-start auto-login (TDD)"
```

---

## Task 13: Implementar `FirebaseAuthRepositoryImpl` e deletar `MockAuthRepository`

**Files:**
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/FirebaseAuthRepositoryImpl.kt`
- Delete: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt`

- [ ] **Step 1: Criar `FirebaseAuthRepositoryImpl.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.data.repository

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.logger.pii.PiiMasker
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Impl Android do [AuthRepository] usando Firebase Authentication (email/senha)
 * + Firestore para resolver a role (`users/{uid}` doc).
 *
 * Erros do FirebaseAuth são mapeados para mensagens em PT-BR — ver `mapError`.
 */
class FirebaseAuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val logger: Logger,
) : AuthRepository {
    override suspend fun authenticate(email: String, password: String): AuthResult {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid
                ?: return AuthResult.Error("Falha inesperada na autenticação")

            val doc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
            if (!doc.exists()) {
                logger.warn(TAG, "user doc missing email=${PiiMasker.email(email)} uid=$uid")
                return AuthResult.Error("Conta não autorizada. Contate o administrador.")
            }

            val roleStr = doc.getString("role")
            val role = roleStr?.let { runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull() }
                ?: run {
                    logger.warn(TAG, "user doc has invalid role uid=$uid raw=$roleStr")
                    return AuthResult.Error("Conta sem perfil válido")
                }
            val name = doc.getString("name") ?: email.substringBefore('@')

            logger.info(TAG, "login ok uid=$uid email=${PiiMasker.email(email)}")
            AuthResult.Success(UserModel(id = uid, email = email, name = name, role = role))
        } catch (e: Exception) {
            logger.warn(TAG, "login failed email=${PiiMasker.email(email)} cause=${e::class.simpleName}", e)
            AuthResult.Error(mapError(e))
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email).await(); Unit }
            .onFailure { e ->
                logger.warn(TAG, "sendPasswordReset failed email=${PiiMasker.email(email)}", e)
            }

    override suspend fun signOut() {
        auth.signOut()
        logger.info(TAG, "firebase auth signOut")
    }

    override fun currentUid(): String? = auth.currentUser?.uid

    private fun mapError(e: Throwable): String =
        when (e) {
            is FirebaseAuthInvalidUserException -> "Email ou senha incorretos"
            is FirebaseAuthInvalidCredentialsException -> {
                if (e.errorCode == "ERROR_INVALID_EMAIL") "Email inválido"
                else "Email ou senha incorretos"
            }
            is FirebaseNetworkException -> "Sem conexão. Verifique a internet"
            is FirebaseAuthException ->
                when (e.errorCode) {
                    "ERROR_USER_DISABLED" -> "Conta desativada. Contate o administrador"
                    "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Tente em alguns minutos"
                    else -> "Erro de autenticação"
                }
            else -> "Erro de autenticação"
        }

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val USERS_COLLECTION = "users"
    }
}
```

- [ ] **Step 2: Deletar `MockAuthRepository.kt`**

Run:
```bash
git rm shared/src/commonMain/kotlin/br/com/sprena/shared/auth/data/repository/MockAuthRepository.kt
```

(O DI será atualizado na próxima task; por ora o build vai falhar até Task 15.)

- [ ] **Step 3: Compile (vai falhar, OK)**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: FAIL — referência a `MockAuthRepository` quebrada em `AuthModule.kt`. Continuar para Task 14.

- [ ] **Step 4: Commit parcial**

```bash
git add shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/FirebaseAuthRepositoryImpl.kt
git commit -m "feat(auth): add FirebaseAuthRepositoryImpl mapping errors to PT-BR (and delete mock)"
```

(Branch fica temporariamente vermelha; Task 14 conserta.)

---

## Task 14: Implementar `EncryptedSessionStore` + Koin wiring

**Files:**
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/session/EncryptedSessionStore.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.kt`
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.android.kt`
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/AuthModule.kt`
- Modify: `composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt`

- [ ] **Step 1: Criar `EncryptedSessionStore.kt`**

Conteúdo INTEIRO:

```kotlin
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
            AndroidKeysetManager.Builder()
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
```

- [ ] **Step 2: Criar `SessionModule.kt` (commonMain expect)**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.di

import org.koin.core.module.Module

/** SessionStore impl é por plataforma. */
expect fun sessionModule(): Module
```

- [ ] **Step 3: Criar `SessionModule.android.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.di

import br.com.sprena.shared.auth.session.EncryptedSessionStore
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.time.Clock
import br.com.sprena.shared.core.time.SystemClock
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun sessionModule(): Module =
    module {
        single<Clock> { SystemClock() }
        single<SessionStore> { EncryptedSessionStore(context = androidContext(), logger = get()) }
    }
```

- [ ] **Step 4: Atualizar `AuthModule.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.di

import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import br.com.sprena.shared.auth.domain.usecase.RestoreSessionUseCase
import org.koin.dsl.module

/**
 * Módulo Koin de autenticação (commonMain).
 *
 * NÃO declara `AuthRepository` — a impl é Android-only (`FirebaseAuthRepositoryImpl`),
 * declarada em `composeApp/PlatformModule.android.kt`. Mesma estratégia para `SessionStore`
 * (ver [sessionModule]).
 */
fun authModule() =
    module {
        factory { LoginUseCase(authRepository = get(), sessionStore = get(), clock = get(), logger = get()) }
        factory { LogoutUseCase(authRepository = get(), sessionStore = get(), logger = get()) }
        factory { RequestPasswordResetUseCase(authRepository = get(), logger = get()) }
        factory { RestoreSessionUseCase(authRepository = get(), sessionStore = get(), clock = get(), logger = get()) }
    }
```

- [ ] **Step 5: Atualizar `composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt`**

Localizar o arquivo (provavelmente já provê `FirebaseFirestore`). Adicionar:

```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import br.com.sprena.shared.auth.data.repository.FirebaseAuthRepositoryImpl
import br.com.sprena.shared.auth.domain.repository.AuthRepository
```

E DENTRO do `module { ... }`, adicionar:

```kotlin
single<FirebaseAuth> { Firebase.auth }
single<AuthRepository> {
    FirebaseAuthRepositoryImpl(
        auth = get(),
        firestore = get(),
        logger = get(),
    )
}
```

- [ ] **Step 6: Atualizar `SprenaApplication.kt` para incluir `sessionModule()`**

Adicionar import:
```kotlin
import br.com.sprena.shared.auth.di.sessionModule
```

Dentro do `buildList { ... }` do `modules(...)`, adicionar `add(sessionModule())` IMEDIATAMENTE após `add(loggerModule())`. Resultado:

```kotlin
modules(
    buildList {
        add(loggerModule())
        add(sessionModule())
        add(platformModule())
        addAll(sharedModules())
        add(appModule())
    },
)
```

- [ ] **Step 7: Garantir que `sharedModules()` inclui `authModule()`**

Localizar `shared/src/commonMain/kotlin/br/com/sprena/shared/core/di/SharedModules.kt` (ou path equivalente). Confirmar que `authModule()` está na lista retornada. Se já estava (mock antes), continua estando.

- [ ] **Step 8: Build + testes completos**

Run:
```bash
./gradlew :composeApp:assembleDebug :shared:testDebugUnitTest :composeApp:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add shared/src/androidMain/kotlin/br/com/sprena/shared/auth/session/EncryptedSessionStore.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.kt \
        shared/src/androidMain/kotlin/br/com/sprena/shared/auth/di/SessionModule.android.kt \
        shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/AuthModule.kt \
        composeApp/src/androidMain/kotlin/br/com/sprena/di/PlatformModule.android.kt \
        composeApp/src/androidMain/kotlin/br/com/sprena/SprenaApplication.kt
git commit -m "feat(auth): wire FirebaseAuth, EncryptedSessionStore and SystemClock via Koin"
```

---

## Task 15: Refatorar `LoginViewModel`, `LoginState`, `LoginIntent`, `LoginEffect`, `LoginScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginState.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginIntent.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginEffect.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/LoginScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/ForgotPasswordDialog.kt`
- Modify: `composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/LoginViewModelTest.kt`

- [ ] **Step 1: Ler estado atual dos 5 arquivos**

Antes de editar, abrir cada um para entender shape e dependências. Em particular o `LoginViewModel` já recebe `LoginUseCase` injetado — o que muda agora é que LoginUseCase recebe sessionStore/clock (mas isso é via Koin, transparente pro VM). VM precisa:
- Renomear campo `username` → `email` no state + intent
- Adicionar fluxo de "esqueci a senha"

- [ ] **Step 2: Atualizar `LoginState.kt`**

Substituir o conteúdo (template — preservar o que já existia exceto onde indicado):

```kotlin
package br.com.sprena.presentation.login

import br.com.sprena.core.mvi.UiState

data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val passwordResetDialogOpen: Boolean = false,
    val passwordResetEmail: String = "",
    val passwordResetEmailError: String? = null,
    val passwordResetSending: Boolean = false,
) : UiState
```

Adaptar imports se o `UiState` tiver caminho diferente.

- [ ] **Step 3: Atualizar `LoginIntent.kt`**

```kotlin
package br.com.sprena.presentation.login

import br.com.sprena.core.mvi.UiIntent

sealed interface LoginIntent : UiIntent {
    data class UpdateEmail(val value: String) : LoginIntent
    data class UpdatePassword(val value: String) : LoginIntent
    data object Submit : LoginIntent

    // Reset de senha
    data object OpenPasswordResetDialog : LoginIntent
    data class UpdatePasswordResetEmail(val value: String) : LoginIntent
    data object SubmitPasswordReset : LoginIntent
    data object DismissPasswordResetDialog : LoginIntent
}
```

- [ ] **Step 4: Atualizar `LoginEffect.kt`**

```kotlin
package br.com.sprena.presentation.login

import br.com.sprena.core.mvi.UiEffect

sealed interface LoginEffect : UiEffect {
    data object NavigateToHome : LoginEffect
    data class ShowError(val message: String) : LoginEffect
    data object ShowPasswordResetSent : LoginEffect
    data class ShowPasswordResetError(val message: String) : LoginEffect
}
```

(Se nomes diferiam — ex.: `NavigateToHome` vs `GoToHome` — manter a convenção pré-existente; o ponto é apenas adicionar os 2 efeitos novos.)

- [ ] **Step 5: Atualizar `LoginViewModel.kt`**

Esqueleto (preservar imports/scope/scaffold já presente):

```kotlin
package br.com.sprena.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val requestPasswordReset: RequestPasswordResetUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UpdateEmail ->
                _state.value = _state.value.copy(email = intent.value, emailError = null)
            is LoginIntent.UpdatePassword ->
                _state.value = _state.value.copy(password = intent.value, passwordError = null)
            LoginIntent.Submit -> submit()
            LoginIntent.OpenPasswordResetDialog ->
                _state.value = _state.value.copy(
                    passwordResetDialogOpen = true,
                    passwordResetEmail = _state.value.email,
                    passwordResetEmailError = null,
                )
            is LoginIntent.UpdatePasswordResetEmail ->
                _state.value = _state.value.copy(
                    passwordResetEmail = intent.value,
                    passwordResetEmailError = null,
                )
            LoginIntent.SubmitPasswordReset -> submitPasswordReset()
            LoginIntent.DismissPasswordResetDialog ->
                _state.value = _state.value.copy(
                    passwordResetDialogOpen = false,
                    passwordResetEmail = "",
                    passwordResetEmailError = null,
                    passwordResetSending = false,
                )
        }
    }

    private fun submit() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = loginUseCase(_state.value.email, _state.value.password)
            _state.value = _state.value.copy(isLoading = false)
            when (result) {
                is AuthResult.Success -> _effects.send(LoginEffect.NavigateToHome)
                is AuthResult.Error -> _effects.send(LoginEffect.ShowError(result.message))
            }
        }
    }

    private fun submitPasswordReset() {
        viewModelScope.launch {
            _state.value = _state.value.copy(passwordResetSending = true)
            val result = requestPasswordReset(_state.value.passwordResetEmail)
            _state.value = _state.value.copy(passwordResetSending = false)
            when (result) {
                PasswordResetResult.Sent -> {
                    _state.value = _state.value.copy(
                        passwordResetDialogOpen = false,
                        passwordResetEmail = "",
                    )
                    _effects.send(LoginEffect.ShowPasswordResetSent)
                }
                is PasswordResetResult.InvalidEmail ->
                    _state.value = _state.value.copy(passwordResetEmailError = result.message)
                PasswordResetResult.NetworkError ->
                    _effects.send(LoginEffect.ShowPasswordResetError("Sem conexão. Verifique a internet"))
                is PasswordResetResult.UnknownError ->
                    _effects.send(LoginEffect.ShowPasswordResetError("Não foi possível enviar agora. Tente novamente"))
            }
        }
    }
}
```

> **Atenção:** se o VM existente tinha shape diferente (ex.: `MviViewModel<State, Intent, Effect>` abstrato em `shared/core/mvi`), seguir o pattern do projeto. O esqueleto acima é genérico — o subagent deve adaptar.

- [ ] **Step 6: Atualizar `AppModule.kt`** (composeApp DI) para injetar `RequestPasswordResetUseCase` no VM

Localizar onde `LoginViewModel { LoginViewModel(get()) }` (ou similar) está registrado. Atualizar para:

```kotlin
viewModel { LoginViewModel(loginUseCase = get(), requestPasswordReset = get()) }
```

- [ ] **Step 7: Criar `ForgotPasswordDialog.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.presentation.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun ForgotPasswordDialog(
    email: String,
    emailError: String?,
    sending: Boolean,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recuperar senha") },
        text = {
            Column {
                Text("Informe seu email cadastrado. Enviaremos um link para criar uma nova senha.")
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = { emailError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !sending) {
                Text(if (sending) "Enviando..." else "Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
```

- [ ] **Step 8: Atualizar `LoginScreen.kt`**

Mudanças mínimas no LoginScreen existente:
- Renomear o campo de input de "Usuário" para "Email", `KeyboardType.Email`
- Bind do TextField em `state.email` (em vez de `state.username`)
- Adicionar `TextButton("Esqueci a senha")` abaixo do botão de Submit
- Renderizar `ForgotPasswordDialog` se `state.passwordResetDialogOpen == true`
- Coletar `LoginEffect.ShowPasswordResetSent` e mostrar Snackbar "Email enviado. Verifique sua caixa de entrada."
- Coletar `LoginEffect.ShowPasswordResetError` e mostrar Snackbar com a mensagem

Trecho ilustrativo (executor deve fazer Edit cirúrgico baseado no arquivo real):

```kotlin
// Onde existia o campo username:
OutlinedTextField(
    value = state.email,
    onValueChange = { vm.handleIntent(LoginIntent.UpdateEmail(it)) },
    label = { Text("Email") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    singleLine = true,
    isError = state.emailError != null,
    supportingText = { state.emailError?.let { Text(it) } },
)

// Depois do botão Entrar:
TextButton(onClick = { vm.handleIntent(LoginIntent.OpenPasswordResetDialog) }) {
    Text("Esqueci a senha")
}

// No final do Composable, antes do fechamento do scaffold:
if (state.passwordResetDialogOpen) {
    ForgotPasswordDialog(
        email = state.passwordResetEmail,
        emailError = state.passwordResetEmailError,
        sending = state.passwordResetSending,
        onEmailChange = { vm.handleIntent(LoginIntent.UpdatePasswordResetEmail(it)) },
        onSubmit = { vm.handleIntent(LoginIntent.SubmitPasswordReset) },
        onDismiss = { vm.handleIntent(LoginIntent.DismissPasswordResetDialog) },
    )
}
```

- [ ] **Step 9: Atualizar `LoginViewModelTest.kt`**

Atualizar:
- Construtor agora recebe 2 use cases: `LoginUseCase` + `RequestPasswordResetUseCase`. Fakes para ambos.
- Renomear toda referência a `username` no test → `email`.
- Adicionar pelo menos 2 testes novos:
  - "submitPasswordReset envia para Sent fecha dialog" → emite `ShowPasswordResetSent`
  - "submitPasswordReset com email inválido seta passwordResetEmailError sem chamar useCase"

Manter o pattern existente do test (`Turbine` para effects, `runTest` para coroutines).

- [ ] **Step 10: Build + testes**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/presentation/login/ \
        composeApp/src/commonTest/kotlin/br/com/sprena/presentation/login/ \
        composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt
git commit -m "feat(login): switch UI to email + add forgot-password dialog"
```

---

## Task 16: Adicionar seção Conta + Sair em `SettingsScreen`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsState.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsIntent.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsEffect.kt`
- Create: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`

- [ ] **Step 1: Criar State/Intent/Effect**

`SettingsState.kt`:
```kotlin
package br.com.sprena.presentation.settings

import br.com.sprena.core.mvi.UiState
import br.com.sprena.shared.auth.session.SessionUser

data class SettingsState(
    val user: SessionUser? = null,
    val loggingOut: Boolean = false,
) : UiState
```

`SettingsIntent.kt`:
```kotlin
package br.com.sprena.presentation.settings

import br.com.sprena.core.mvi.UiIntent

sealed interface SettingsIntent : UiIntent {
    data object Logout : SettingsIntent
}
```

`SettingsEffect.kt`:
```kotlin
package br.com.sprena.presentation.settings

import br.com.sprena.core.mvi.UiEffect

sealed interface SettingsEffect : UiEffect {
    data object NavigateToLogin : SettingsEffect
}
```

- [ ] **Step 2: Criar `SettingsViewModel.kt`**

```kotlin
package br.com.sprena.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.session.SessionStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sessionStore: SessionStore,
    private val logout: LogoutUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(user = sessionStore.load())
        }
    }

    fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Logout -> doLogout()
        }
    }

    private fun doLogout() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loggingOut = true)
            logout()
            _effects.send(SettingsEffect.NavigateToLogin)
        }
    }
}
```

- [ ] **Step 3: Atualizar `SettingsScreen.kt`**

Adicionar parâmetro `settingsViewModel: SettingsViewModel` (com default `koinViewModel()` se a convenção do projeto for assim — adaptar pra como outras telas fazem) e parâmetro `onNavigateToLogin: () -> Unit`.

Inserir, ANTES da seção "Comandas" existente:

```kotlin
// ── Section: Conta ──
val settingsState by settingsViewModel.state.collectAsState()
LaunchedEffect(Unit) {
    settingsViewModel.effects.collect { effect ->
        when (effect) {
            SettingsEffect.NavigateToLogin -> onNavigateToLogin()
        }
    }
}
SectionTitle(title = "Conta")
val user = settingsState.user
if (user != null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.email, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Perfil: ${user.role.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
TextButton(
    onClick = { settingsViewModel.handleIntent(SettingsIntent.Logout) },
    enabled = !settingsState.loggingOut,
    modifier = Modifier.padding(horizontal = 16.dp),
    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
) {
    Text(if (settingsState.loggingOut) "Saindo..." else "Sair")
}
HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
Spacer(modifier = Modifier.height(8.dp))
```

Adicionar imports faltantes (`Row`, `Column`, `TextButton`, `ButtonDefaults`, `collectAsState`, `LaunchedEffect`).

- [ ] **Step 4: Registrar VM em `AppModule.kt`**

```kotlin
viewModel { SettingsViewModel(sessionStore = get(), logout = get()) }
```

- [ ] **Step 5: Atualizar `NavGraph.kt`**

Onde `SettingsScreen` é chamado, passar a callback `onNavigateToLogin`:

```kotlin
SettingsScreen(
    themeViewModel = themeViewModel,
    settingsViewModel = koinViewModel(),
    onNavigateToLogin = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    },
    // ... outros params já existentes
)
```

- [ ] **Step 6: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/presentation/settings/ \
        composeApp/src/commonMain/kotlin/br/com/sprena/di/AppModule.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt
git commit -m "feat(settings): add account section with logout button"
```

---

## Task 17: NavGraph com startDestination dinâmico (auto-login)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt`
- (possivelmente) Modify: `composeApp/src/commonMain/kotlin/br/com/sprena/App.kt`

- [ ] **Step 1: Localizar `App` Composable e `NavGraph`**

`App.kt` (ou `SprenaApp.kt`) é o entry point Composable. `NavGraph.kt` define rotas. Ler ambos para entender onde `NavHost` é criado.

- [ ] **Step 2: Adicionar Composable que resolve start destination**

Substituir o trecho onde `NavHost(startDestination = Routes.LOGIN, ...)` aparece (linhas próximas à 99-101 em NavGraph.kt) por algo como:

```kotlin
@Composable
fun rememberStartDestination(): String? {
    val restoreUseCase: RestoreSessionUseCase = koinInject()
    var destination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        destination = when (restoreUseCase()) {
            is RestoreResult.Authenticated -> Routes.HOME
            RestoreResult.NotAuthenticated -> Routes.LOGIN
        }
    }
    return destination
}
```

E onde o NavHost é chamado:

```kotlin
val startDestination = rememberStartDestination()
if (startDestination == null) {
    // Splash mínima — Box centralizada com CircularProgressIndicator
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    return
}

NavHost(
    navController = navController,
    startDestination = startDestination,
) {
    // ...rotas existentes
}
```

Adicionar imports: `koinInject` de `org.koin.compose`, `RestoreSessionUseCase`, `RestoreResult`, `mutableStateOf`, `remember`, `LaunchedEffect`, `Box`, `CircularProgressIndicator`, etc.

- [ ] **Step 3: Após login bem-sucedido, garantir popUpTo Login**

No `composable(Routes.LOGIN)` da `NavGraph.kt`, no callback `onNavigateToHome` ou equivalente do `LoginScreen`:

```kotlin
navController.navigate(Routes.HOME) {
    popUpTo(Routes.LOGIN) { inclusive = true }
}
```

(Verificar se já estava — se sim, não tocar.)

- [ ] **Step 4: Build + manual**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/br/com/sprena/navigation/NavGraph.kt \
        composeApp/src/commonMain/kotlin/br/com/sprena/App.kt
git commit -m "feat(nav): dynamic start destination via RestoreSessionUseCase"
```

---

## Task 18: Proguard rules para Tink + DataStore + Firebase Auth

**Files:**
- Modify: `composeApp/proguard-rules.pro`

- [ ] **Step 1: Adicionar bloco ao final do arquivo**

```proguard
# ---------------------------------------------------------------------------
# F1.3 — Firebase Auth (já coberto pelo bloco geral firebase.**)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ---------------------------------------------------------------------------
# F1.3 — Google Tink (cripto da sessão)
# ---------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
# Protocol buffers usados pelo Tink:
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ---------------------------------------------------------------------------
# F1.3 — AndroidX DataStore Preferences
# ---------------------------------------------------------------------------
-keep class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.preferences.**
```

- [ ] **Step 2: Build release**

Run: `./gradlew :composeApp:assembleRelease`
Expected: BUILD SUCCESSFUL. R8 sem warnings novos.

- [ ] **Step 3: Commit**

```bash
git add composeApp/proguard-rules.pro
git commit -m "build(release): proguard rules for firebase-auth, tink and datastore"
```

---

## Task 19: Pipeline completo CI-equivalente

**Files:**
- (none — verification)

- [ ] **Step 1: Comando CI-equivalente**

Run:
```bash
./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest :composeApp:lint :composeApp:assembleDebug :composeApp:assembleRelease
```

Use timeout 600000ms. Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Se ktlint falhar**

Run: `./gradlew ktlintFormat` e re-rodar `ktlintCheck`. Commit:
```bash
git add -u
git commit -m "style: ktlintFormat after f1.3 changes"
```

- [ ] **Step 3: Se detekt acusar novo issue**

Padrões esperados em F1.3 que podem disparar regras:
- `MagicNumber` em `SessionValidator.DEFAULT_TTL_MILLIS` cálculo → já está extraído em const
- `ReturnCount` em `FirebaseAuthRepositoryImpl.authenticate` (muitos early returns) → refatorar com `try { ... } catch { ... }` ou consolidar em `when`
- `LongMethod` em `LoginViewModel.handleIntent` → extrair handlers privados
- `TooManyFunctions` em `LoginViewModel` → mover handlers para top-level ou subclasse

Se aparecer, **refatorar o código** — não atualizar baseline. Cada fix em commit separado.

- [ ] **Step 4: Verificação manual em device**

Pré-requisito: criar via Firebase Console:
1. Auth → Users → adicionar `test@sprena.local` com senha `123456`
2. Firestore → coleção `users` → doc com ID = uid do user criado → campos: `email: "test@sprena.local"`, `role: "ADM"`, `name: "Teste"`

Depois:

1. `./gradlew :composeApp:installDebug`
2. Login com `test@sprena.local` / `123456` → Home aparece
3. Conectar `adb logcat -s LoginUseCase:* FirebaseAuthRepo:* RestoreSession:*` — verificar logs com email mascarado
4. Fechar/abrir app → Home aparece sem passar por Login (auto-login)
5. Settings → seção Conta mostra "test@sprena.local" + "Perfil: Administrador" + botão Sair (vermelho)
6. Clicar Sair → volta para Login → fechar/abrir app → ainda Login (sessão limpa)
7. Login → fechar/abrir → Home
8. (Opcional) Avançar relógio do device em 25h via Configurações do Android (ou aguardar) → cold start volta pra Login
9. Login (qualquer dispositivo conectado) → "Esqueci a senha" → input email → clicar Enviar → Snackbar "Email enviado" → verificar caixa de entrada
10. Inspecionar arquivo `/data/data/br.com.sprena/files/datastore/session_prefs.preferences_pb` via `adb shell run-as br.com.sprena cat ...` → bytes ilegíveis (cifrado)

Documentar resultados (passou/falhou) num arquivo de notas pra incluir no PR body.

---

## Task 20: Documentar em `SECURITY.md` e `ARCHITECTURE.md`

**Files:**
- Modify: `SECURITY.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: Adicionar seção em `SECURITY.md`**

Após a seção F1.2:

````markdown
## F1.3 — Firebase Auth + Sessão Criptografada

### Stack
- **Firebase Authentication** (BOM 34.12.0) com email + senha. `MockAuthRepository` removido.
- **Roles**: doc Firestore `users/{uid}` com `role: "ADM" | "MOD" | "CLIENT"`. F1.4 vai proteger via Security Rules.
- **Sessão local**: `EncryptedSessionStore` usa Google Tink 1.13.0 (AEAD AES-256-GCM, chave no Android Keystore via `AndroidKeysetManager`) sobre `androidx.datastore:datastore-preferences`. Persiste: `uid`, `email`, `role`, `lastLoginEpochMillis`.
- **TTL**: 24h. Validado por `SessionValidator.isExpired`.
- **Clock**: abstração injetável (`SystemClock` em prod, `FixedClock` em testes).

### Fluxos
- **Login**: `LoginUseCase` valida → `FirebaseAuthRepositoryImpl.authenticate` → lê role no Firestore → `SessionStore.save`
- **Cold start**: `RestoreSessionUseCase` → se sessão local válida e uid bate com `auth.currentUser?.uid`, vai pra Home; senão Login
- **Logout**: `LogoutUseCase` → `auth.signOut()` + `SessionStore.clear()` (botão na `SettingsScreen`)
- **Reset de senha**: `RequestPasswordResetUseCase` → `auth.sendPasswordResetEmail` (link no `LoginScreen`)

### Erros mapeados (FirebaseAuth → PT-BR)
- `ERROR_INVALID_EMAIL` → "Email inválido"
- `ERROR_USER_NOT_FOUND` / `ERROR_WRONG_PASSWORD` / `ERROR_INVALID_CREDENTIAL` → "Email ou senha incorretos" (mesma mensagem — anti-enumeração)
- `ERROR_USER_DISABLED` → "Conta desativada. Contate o administrador"
- `ERROR_TOO_MANY_REQUESTS` → "Muitas tentativas. Tente em alguns minutos"
- `FirebaseNetworkException` → "Sem conexão. Verifique a internet"
- Outros → "Erro de autenticação"

### Convenção de uso
1. Nunca logar `password` — `LoginUseCase` e `FirebaseAuthRepositoryImpl` já garantem isso.
2. Sempre mascarar email no log via `PiiMasker.email(...)`.
3. Criar novos usuários SEMPRE pela Firebase Console (Auth + doc Firestore `users/{uid}` com role).
4. Em testes, injetar `FakeSessionStore` + `FixedClock` + `FakeAuthRepository`.

### Trade-offs
- **TTL 24h**: balanço entre conforto do operador (uma diária) e janela de exposição em device perdido. Menor que 24h é UX ruim para o domínio; maior aumenta risco.
- **Role no Firestore (vs Custom Claims)**: solo dev sem backend; aceita 1 leitura/login. Migrar para Custom Claims em F2 só se F1.4 mostrar overhead relevante.
- **Cadastro off-band**: zero superfície de abuso, mas exige intervenção manual do admin pra cada novo operador. Self-signup volta em F6 se houver demanda.
- **Tink + DataStore**: lib não-deprecated, AES-256-GCM com chave Hardware-backed (Android Keystore). Falha de decifragem (chave invalidada por reset de dispositivo, p.ex.) → `load()` retorna null e força novo login.

### Verificação manual (pré-merge)
Ver checklist em [`docs/superpowers/plans/2026-05-25-f1-3-firebase-auth.md`](docs/superpowers/plans/2026-05-25-f1-3-firebase-auth.md) Task 19 Step 4.
````

- [ ] **Step 2: Atualizar `ARCHITECTURE.md`**

Atualizar a seção "## Segurança":

```markdown
## Segurança

Decisões de segurança e endurecimento documentadas em [SECURITY.md](./SECURITY.md). Fases aplicadas:

- **F1.1** — minificação R8, bloqueio de backup, network security config, `FLAG_SECURE`.
- **F1.2** — logging seguro (Napier + Crashlytics) com sanitização de PII (`PiiMasker`/`PiiScrubber`).
- **F1.3** — Firebase Authentication (email/senha), sessão local cifrada (Tink AEAD + DataStore Preferences, TTL 24h), reset de senha, auto-login, logout.

Próximas sub-fases: F1.4 (Firestore Security Rules + App Check) e F1.5 (LGPD baseline).
```

- [ ] **Step 3: Commit**

```bash
git add SECURITY.md ARCHITECTURE.md
git commit -m "docs(security): document f1.3 firebase auth and encrypted session"
```

---

## Task 21: Push + abrir PR + esperar CI

**Files:**
- (none — Git/GitHub)

- [ ] **Step 1: Push**

Run:
```bash
git push -u origin feature/f1-3-firebase-auth
```

- [ ] **Step 2: Abrir PR**

Run:
```bash
gh pr create \
  --title "F1.3: Firebase Auth + sessão criptografada" \
  --body "$(cat <<'EOF'
## Summary

Terceira sub-fase de F1 (segurança crítica). Substitui o MockAuthRepository por Firebase Authentication real.

- **Auth**: email/senha via Firebase Auth (BOM 34.12.0); mensagens de erro em PT-BR; reset de senha por email
- **Sessão**: Tink AEAD (AES-256-GCM + Android Keystore) sobre DataStore Preferences; TTL 24h
- **Roles**: lidas do doc Firestore `users/{uid}` após login
- **Auto-login**: `RestoreSessionUseCase` no cold start; navega Home se sessão válida + uid coincide
- **Logout**: botão na seção "Conta" do `SettingsScreen`
- **UI**: `LoginScreen` refatorada para email/senha; `ForgotPasswordDialog` novo
- **TDD**: `SessionValidator`, `LoginValidator`, `LoginUseCase`, `LogoutUseCase`, `RequestPasswordResetUseCase`, `RestoreSessionUseCase` (28+ testes novos)
- **DI**: `sessionModule()` (expect/actual) + wiring em `PlatformModule.android`
- **Proguard**: regras para Tink, DataStore e Firebase Auth

F1.4 (Firestore Rules + App Check) e F1.5 (LGPD) seguem em sub-fases independentes.

## Test plan

- [x] `./gradlew :shared:testDebugUnitTest` — todos verdes incluindo 28+ testes novos de auth
- [x] `./gradlew :composeApp:testDebugUnitTest` — VM tests verdes (login + settings)
- [x] CI-equivalente local: `ktlintCheck detektMetadataMain detektAndroidDebug testDebugUnitTest lint assembleDebug assembleRelease` BUILD SUCCESSFUL
- [ ] Login real com user criado no Console — vai pra Home (manual)
- [ ] Cold start com sessão válida → Home (manual)
- [ ] Cold start após 24h → Login (manual)
- [ ] Logout limpa sessão; auto-login NÃO acontece (manual)
- [ ] Esqueci a senha envia email (manual)
- [ ] `session_prefs.preferences_pb` em disco é ilegível (manual)

## Prerequisite

Antes de mergear, criar no Firebase Console:
- Auth → 1 user de teste (email + senha)
- Firestore → `users/{uid}` → `{ email, role: "ADM", name }`

Próxima sub-fase (F1.4) vai protege essas coleções via Security Rules.
EOF
)"
```

- [ ] **Step 3: Esperar CI**

Run:
```bash
gh pr checks --watch
```

Aceitar resultado. Se algum check falhar, ler log:
```bash
gh run view --log-failed
```
Refatorar para passar; commit; push; aguardar nova run.

- [ ] **Step 4: Reportar URL do PR**

Pedro revisa o PR antes do merge.

---

## Self-review checklist

- [x] Cobertura do spec F1.3: Firebase Auth ✓, sessão criptografada ✓, reset ✓, auto-login ✓, logout ✓, TTL 24h ✓
- [x] TDD aplicado nos componentes pure (SessionValidator, LoginValidator, LoginUseCase, LogoutUseCase, RequestPasswordResetUseCase, RestoreSessionUseCase)
- [x] Sem placeholder ("TBD", "fill in", "handle edge cases")
- [x] Cada Task tem step de commit (exceto Task 1 que é só setup e Task 19 que é só verify)
- [x] Build verification em tasks que mexem em gradle/proguard
- [x] CLAUDE.md respeitado: Firebase só em androidMain ✓, Koin (não Hilt) ✓, sem mutar State (`copy()`) ✓, sem GlobalScope ✓
- [x] Tipos consistentes: `SessionUser(uid, email, role, lastLoginEpochMillis)` mesma assinatura em Task 4, 9, 12, 14, 16
- [x] CI-equivalente local definido (Task 19) ANTES do push (Task 21) — não cair na armadilha que F1.2 caiu

## Out of scope (vai pra F1.4+ ou F6)

- Firestore Security Rules + App Check — F1.4
- LGPD: consentimento + privacy policy + masking de CPF em UI + hash de CPF em rest — F1.5
- BiometricPrompt 2FA — F6
- Self-registration via UI — F6
- Google Sign-In adicional — F6
- Multi-device session list / revogação — F6
- Migração para Firebase Custom Claims — só se F1.4 mostrar overhead na leitura de `users/{uid}`
