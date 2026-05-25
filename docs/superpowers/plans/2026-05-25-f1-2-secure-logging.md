# F1.2 — Logging Seguro (Napier + Crashlytics + Sanitização PII) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Estabelecer a baseline de logging seguro do Sprena — interface `Logger` em `shared/core`, impl Android com Napier + Firebase Crashlytics, sanitização de PII (CPF/phone/email/password) por TDD, e instrumentação inicial de 2 call sites críticos (`SportClientRepositoryImpl`, `LoginUseCase`).

**Architecture:** Logger é definido como interface KMP em `shared/commonMain/core/logger/`. A impl Android (`shared/androidMain/core/logger/`) usa Napier (console em debug) + Firebase Crashlytics `log`/`recordException` em release. Toda mensagem passa por `PiiScrubber` (regex safety net) antes de sair; helpers de masking em `PiiMasker` para uso explícito pelos call sites. Crashlytics fica desabilitado em debug (evita ruído no dashboard). Logger é injetado via Koin como singleton e consumido por Repositories/UseCases. Nenhuma feature Compose é tocada — instrumentação fica nas camadas data/domain.

**Tech Stack:** Napier 2.7.1 (KMP logger, console em debug), Firebase Crashlytics (via Firebase BOM 34.12.0 já presente), `com.google.firebase.crashlytics` Gradle plugin 3.0.2, Koin 4.0.2 (DI já existente).

---

## Context

F1.1 (build hardening + FLAG_SECURE) já está mergeada via `feature/f1-1-build-hardening`. A próxima sub-fase F1.2 endereça o último item de F1 que é pré-requisito de todo o resto: **observabilidade segura**. Sem logging estruturado e crash reporting:

- Bugs em produção viram caixa-preta — F1.3 (Firebase Auth) e F1.4 (Firestore Rules) vão gerar falhas que precisamos diagnosticar
- LGPD (F1.5) exige garantia de que PII não vaza em logs — esta fase entrega o sanitizador que F1.5 vai depender
- Codebase atual tem **zero logging** (verificado via grep em `composeApp/` e `shared/`), então é um campo limpo: instalamos a infra correta de uma vez, sem ter que refatorar `println` espalhados

Decisões já tomadas (via brainstorming):
- **Escopo:** infra + masking + 2 call sites de exemplo (não instrumentar app inteiro — features se instrumentam quando refatoradas em F2)
- **Crashlytics em debug:** desabilitado (`setCrashlyticsCollectionEnabled(false)` quando `BuildConfig.DEBUG`)

---

## Premissas

- Branch base: `master` atualizado após merge de F1.1 (PR criado em `feature/f1-1-build-hardening`).
- Nova branch: `feature/f1-2-secure-logging`.
- Plan canônico será movido para `docs/superpowers/plans/2026-05-25-f1-2-secure-logging.md` no commit inicial (manter convenção F0/F1.1).
- Detekt + ktlint + CI continuam passando (baseline F0).
- `applicationId = "br.com.sprena"`, `minSdk = 26`, Firebase Firestore já configurado (`google-services.json` em `composeApp/`).
- Firebase Crashlytics está coberto pelo BOM 34.12.0 — basta declarar a lib e o plugin.

## Estrutura de arquivos

**Novos (commonMain — pure Kotlin, KMP-safe):**
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/Logger.kt` — interface `Logger` (debug/info/warn/error)
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/LogLevel.kt` — enum
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/NoOpLogger.kt` — default para tests e fallback
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiMasker.kt` — masking explícito (cpf, phone, email)
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubber.kt` — regex safety net (aplicado pela impl antes de emitir)

**Novos (commonTest):**
- `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiMaskerTest.kt`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubberTest.kt`

**Novos (androidMain):**
- `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/AndroidLogger.kt` — impl Napier + Crashlytics, aplica `PiiScrubber`
- `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/LoggerBootstrap.kt` — `initLogging(isDebug: Boolean)` para chamada na Application

**Novos (DI):**
- `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.kt` — declara `Logger` no commonMain via `expect fun loggerModule(): Module`
- `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.android.kt` — `actual` retornando módulo com `AndroidLogger`

**Modificados:**
- `gradle/libs.versions.toml` — versões `napier`, `firebase-crashlytics-plugin`; libs `napier`, `firebase-crashlytics`; plugin `firebase-crashlytics`
- `shared/build.gradle.kts` — `napier` em `commonMain.dependencies`; `firebase-crashlytics` em `androidMain.dependencies`
- `composeApp/build.gradle.kts` — aplicar plugin `firebase-crashlytics` (após `google-services`)
- `composeApp/src/androidMain/kotlin/br/com/sprena/SprenaApplication.kt` — chamar `initLogging(BuildConfig.DEBUG)` antes de `startKoin`, registrar `loggerModule()` em `modules(...)`
- `composeApp/proguard-rules.pro` — adicionar regras Napier + Crashlytics
- `shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/data/repository/SportClientRepositoryImpl.kt` — receber `Logger` no construtor; wrap de `add/update/delete/getById` com try-catch que loga e re-throwa
- `shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/di/SportClientPlatformModule.android.kt` (ou equivalente) — injetar `Logger` no `SportClientRepositoryImpl`
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCase.kt` — receber `Logger`, logar info no path de sucesso e warn em falha (sem password)
- `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/di/AuthModule.kt` — injetar `Logger` no `LoginUseCase`
- `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCaseTest.kt` — passar `NoOpLogger()` nos construtores
- `SECURITY.md` — nova seção "F1.2 — Logging Seguro"
- `ARCHITECTURE.md` — atualizar parágrafo de Segurança mencionando F1.2

---

## Task 1: Criar branch a partir do master atualizado

**Files:**
- (none — branch operation only)

- [ ] **Step 1: Sincronizar master local com origin**

Run:
```bash
git fetch origin
git checkout master
git pull origin master --ff-only
```

Expected: `master` no mesmo SHA de `origin/master`. Se F1.1 ainda não foi mergeado, parar e perguntar ao Pedro antes de prosseguir (F1.2 depende do proguard-rules.pro de F1.1).

- [ ] **Step 2: Criar branch de feature**

Run:
```bash
git checkout -b feature/f1-2-secure-logging
```

Expected: shell prompt mostra `feature/f1-2-secure-logging`.

- [ ] **Step 3: Validar baseline antes de qualquer mudança**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest detekt ktlintCheck
```

Expected: BUILD SUCCESSFUL em tudo. Se algo regredir, investigar em master antes de prosseguir.

---

## Task 2: Adicionar dependências Napier e Crashlytics ao `libs.versions.toml`

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Adicionar versões em `[versions]`**

Localizar o bloco `# --- Firebase ---` (linha 17) e atualizar para:

```toml
# --- Firebase ---
firebase-bom = "34.12.0"
firebase-crashlytics-plugin = "3.0.2"
google-services = "4.4.4"
```

E adicionar uma nova seção `# --- Logging ---` antes de `# --- Static Analysis ---`:

```toml
# --- Logging ---
napier = "2.7.1"
```

- [ ] **Step 2: Adicionar libraries em `[libraries]`**

Logo após `firebase-firestore = ...` (linha 52), adicionar:

```toml
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
```

E adicionar nova seção `# --- Logging ---` antes de `# --- Kotlinx ---`:

```toml
# --- Logging ---
napier = { module = "io.github.aakira:napier", version.ref = "napier" }
```

- [ ] **Step 3: Adicionar plugin em `[plugins]`**

Logo após `google-services = ...`, adicionar:

```toml
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebase-crashlytics-plugin" }
```

- [ ] **Step 4: Sync gradle e validar**

Run:
```bash
./gradlew :composeApp:help
```

Expected: BUILD SUCCESSFUL — gradle parseou o catálogo sem erros.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build(deps): declare napier + firebase-crashlytics in version catalog"
```

---

## Task 3: TDD `PiiMasker` — masking explícito de CPF/phone/email

**Files:**
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiMaskerTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiMasker.kt`

- [ ] **Step 1: Escrever testes ANTES da implementação**

Conteúdo INTEIRO de `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiMaskerTest.kt`:

```kotlin
package br.com.sprena.shared.core.logger.pii

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiMaskerTest {
    // --- CPF ---
    @Test
    fun `cpf with 11 digits unformatted returns masked keeping last 2 digits`() {
        assertEquals("***.***.***-90", PiiMasker.cpf("12345678990"))
    }

    @Test
    fun `cpf formatted with dots and dash returns masked`() {
        assertEquals("***.***.***-90", PiiMasker.cpf("123.456.789-90"))
    }

    @Test
    fun `cpf null returns null`() {
        assertEquals(null, PiiMasker.cpf(null))
    }

    @Test
    fun `cpf blank returns empty string`() {
        assertEquals("", PiiMasker.cpf(""))
    }

    @Test
    fun `cpf with less than 11 digits returns all asterisks no last digits exposed`() {
        assertEquals("***", PiiMasker.cpf("1234"))
    }

    // --- Phone ---
    @Test
    fun `phone 11 digits returns masked keeping ddd and last 2`() {
        assertEquals("(11)*******-21", PiiMasker.phone("11987654321"))
    }

    @Test
    fun `phone 10 digits (landline) returns masked keeping ddd and last 2`() {
        assertEquals("(11)******-21", PiiMasker.phone("1132654321"))
    }

    @Test
    fun `phone null returns null`() {
        assertEquals(null, PiiMasker.phone(null))
    }

    @Test
    fun `phone with formatting returns masked`() {
        assertEquals("(11)*****-21", PiiMasker.phone("(11) 98765-4321"))
    }

    // --- Email ---
    @Test
    fun `email returns first char of local part plus domain`() {
        assertEquals("p***@gmail.com", PiiMasker.email("pedro@gmail.com"))
    }

    @Test
    fun `email with single char local returns mask of that char plus domain`() {
        assertEquals("*@gmail.com", PiiMasker.email("a@gmail.com"))
    }

    @Test
    fun `email null returns null`() {
        assertEquals(null, PiiMasker.email(null))
    }

    @Test
    fun `email without arroba returns all asterisks`() {
        assertEquals("***", PiiMasker.email("not-an-email"))
    }
}
```

- [ ] **Step 2: Rodar testes — devem FALHAR (classe não existe)**

Run: `./gradlew :shared:testDebugUnitTest --tests "*PiiMaskerTest*"`
Expected: FAILED — `Unresolved reference: PiiMasker`. Confirma que estamos em red.

- [ ] **Step 3: Implementar `PiiMasker` para tornar testes verdes**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiMasker.kt`:

```kotlin
package br.com.sprena.shared.core.logger.pii

/**
 * Masking explícito de PII para uso em call sites.
 *
 * Usar quando QUEM LOGA conhece o tipo do campo. Exemplo:
 * ```
 * logger.info("AuthRepo", "login ok para usuario=${PiiMasker.email(user.email)}")
 * ```
 *
 * Para defense-in-depth (regex sweep antes de emitir), ver [PiiScrubber].
 */
object PiiMasker {
    /**
     * CPF: preserva apenas os 2 últimos dígitos. Aceita formatado ou não.
     * Ex.: "123.456.789-90" -> "***.***.***-90"
     */
    fun cpf(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val digits = value.filter { it.isDigit() }
        if (digits.length < 11) return "***"
        val last2 = digits.takeLast(2)
        return "***.***.***-$last2"
    }

    /**
     * Telefone: preserva DDD (2 primeiros dígitos) e os 2 últimos.
     * Aceita 10 ou 11 dígitos com qualquer formatação.
     */
    fun phone(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val digits = value.filter { it.isDigit() }
        if (digits.length !in 10..11) return "***"
        val ddd = digits.take(2)
        val last2 = digits.takeLast(2)
        val middleStars = "*".repeat(digits.length - 4)
        return "($ddd)$middleStars-$last2"
    }

    /**
     * Email: preserva 1 char do local + domínio inteiro.
     * Ex.: "pedro@gmail.com" -> "p***@gmail.com"
     */
    fun email(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val atIndex = value.indexOf('@')
        if (atIndex <= 0) return "***"
        val first = value[0]
        val domain = value.substring(atIndex)
        return if (atIndex == 1) "*$domain" else "$first***$domain"
    }
}
```

- [ ] **Step 4: Rodar testes — devem PASSAR**

Run: `./gradlew :shared:testDebugUnitTest --tests "*PiiMaskerTest*"`
Expected: BUILD SUCCESSFUL, 14 tests passed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiMasker.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiMaskerTest.kt
git commit -m "feat(logger): add PiiMasker for cpf/phone/email with TDD"
```

---

## Task 4: TDD `PiiScrubber` — regex safety net aplicado pela impl

**Files:**
- Create: `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubberTest.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubber.kt`

- [ ] **Step 1: Escrever testes**

Conteúdo INTEIRO de `shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubberTest.kt`:

```kotlin
package br.com.sprena.shared.core.logger.pii

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiScrubberTest {
    @Test
    fun `scrubs formatted cpf in middle of message`() {
        val input = "Cliente cadastrado: CPF 123.456.789-90 confirmado"
        val expected = "Cliente cadastrado: CPF ***.***.***-** confirmado"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs unformatted 11-digit cpf when prefixed by cpf keyword`() {
        val input = "cpf=12345678990 falhou"
        val expected = "cpf=*********** falhou"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs email anywhere in message`() {
        val input = "Falha no login para pedro@gmail.com retry"
        val expected = "Falha no login para ***@*** retry"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs multiple PIIs in same message`() {
        val input = "User pedro@gmail.com com CPF 111.222.333-44 logou"
        val expected = "User ***@*** com CPF ***.***.***-** logou"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs password keyword followed by value`() {
        val input = "tentativa com password=secret123 negada"
        val expected = "tentativa com password=*** negada"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `passes through message without PII unchanged`() {
        val input = "Firestore add document ok"
        assertEquals(input, PiiScrubber.scrub(input))
    }

    @Test
    fun `null returns null`() {
        assertEquals(null, PiiScrubber.scrub(null))
    }

    @Test
    fun `empty returns empty`() {
        assertEquals("", PiiScrubber.scrub(""))
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*PiiScrubberTest*"`
Expected: FAILED — `Unresolved reference: PiiScrubber`.

- [ ] **Step 3: Implementar `PiiScrubber`**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubber.kt`:

```kotlin
package br.com.sprena.shared.core.logger.pii

/**
 * Defense-in-depth: aplicado pela impl de `Logger` antes de emitir QUALQUER mensagem.
 *
 * Cobre os padrões mais comuns de PII que podem escapar de stack traces, mensagens
 * de exceção do Firestore, ou logs ad-hoc que esqueceram de usar [PiiMasker].
 *
 * NÃO substitui [PiiMasker] — é a última linha de defesa, não a primeira.
 */
object PiiScrubber {
    // CPF formatado: 123.456.789-90
    private val cpfFormattedRegex = Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}""")

    // CPF cru após keyword "cpf" (qualquer caixa): cpf=12345678900 ou cpf: 12345678900
    private val cpfRawAfterKeywordRegex = Regex("""(?i)(cpf\s*[:=]\s*)\d{11}""")

    // Email RFC-simplificado
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    // Password após keyword (password=, senha=, etc.) até espaço/fim
    private val passwordAfterKeywordRegex = Regex("""(?i)(password|senha|pwd)\s*[:=]\s*\S+""")

    fun scrub(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        var result = value
        result = cpfFormattedRegex.replace(result, "***.***.***-**")
        result = cpfRawAfterKeywordRegex.replace(result, "$1***********")
        result = emailRegex.replace(result, "***@***")
        result = passwordAfterKeywordRegex.replace(result) { match ->
            val keyword = match.value.substringBefore('=').substringBefore(':')
            val sep = if ('=' in match.value) '=' else ':'
            "$keyword$sep***"
        }
        return result
    }
}
```

- [ ] **Step 4: Rodar — deve passar**

Run: `./gradlew :shared:testDebugUnitTest --tests "*PiiScrubberTest*"`
Expected: BUILD SUCCESSFUL, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubber.kt \
        shared/src/commonTest/kotlin/br/com/sprena/shared/core/logger/pii/PiiScrubberTest.kt
git commit -m "feat(logger): add PiiScrubber regex safety net with TDD"
```

---

## Task 5: Criar interface `Logger`, `LogLevel`, `NoOpLogger` em commonMain

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/LogLevel.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/Logger.kt`
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/NoOpLogger.kt`

- [ ] **Step 1: Criar `LogLevel.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.core.logger

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
```

- [ ] **Step 2: Criar `Logger.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.core.logger

/**
 * Interface comum de logging do Sprena.
 *
 * Convenções:
 * - `tag`: nome da classe/feature, ex.: "SportClientRepo", "LoginUseCase"
 * - `message`: passa por [pii.PiiScrubber] na impl antes de emitir
 * - `throwable`: opcional; em release vira `recordException` no Crashlytics
 *
 * NÃO incluir PII bruto em `message` — use [pii.PiiMasker] no call site.
 */
interface Logger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
```

- [ ] **Step 3: Criar `NoOpLogger.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.core.logger

/**
 * Logger que descarta tudo. Usar em commonTest e como fallback DI.
 */
class NoOpLogger : Logger {
    override fun debug(tag: String, message: String, throwable: Throwable?) = Unit
    override fun info(tag: String, message: String, throwable: Throwable?) = Unit
    override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
```

- [ ] **Step 4: Build do módulo shared**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/
git commit -m "feat(logger): add Logger interface, LogLevel and NoOpLogger"
```

---

## Task 6: Adicionar Napier + Crashlytics ao `shared/build.gradle.kts`

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Adicionar dependências**

Em `shared/build.gradle.kts`, dentro do bloco `sourceSets { ... }`, atualizar:

```kotlin
commonMain.dependencies {
    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Koin DI (módulos shared exportam definições de módulo)
    implementation(libs.koin.core)

    // Logging (KMP)
    implementation(libs.napier)
}

androidMain.dependencies {
    // Firebase (project.dependencies.platform() is required for BOM in KMP)
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
}
```

- [ ] **Step 2: Sync e compilar**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/build.gradle.kts
git commit -m "build(shared): add napier and firebase-crashlytics dependencies"
```

---

## Task 7: Implementar `AndroidLogger` com Napier + Crashlytics + Scrubber

**Files:**
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/AndroidLogger.kt`
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/LoggerBootstrap.kt`

- [ ] **Step 1: Criar `AndroidLogger.kt`**

Conteúdo INTEIRO de `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/AndroidLogger.kt`:

```kotlin
package br.com.sprena.shared.core.logger

import br.com.sprena.shared.core.logger.pii.PiiScrubber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Napier

/**
 * Impl Android: Napier (console em debug) + Firebase Crashlytics (release).
 *
 * Todas as mensagens passam por [PiiScrubber] antes de emitir.
 * - `debug`/`info`: apenas Napier
 * - `warn`: Napier + Crashlytics.log (breadcrumb)
 * - `error`: Napier + Crashlytics.log + recordException (se throwable não-null)
 *
 * Crashlytics em si é ligado/desligado em [LoggerBootstrap.init].
 */
class AndroidLogger : Logger {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun debug(tag: String, message: String, throwable: Throwable?) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.d(message = safe, throwable = throwable, tag = tag)
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.i(message = safe, throwable = throwable, tag = tag)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.w(message = safe, throwable = throwable, tag = tag)
        crashlytics.log("[$tag] WARN: $safe")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.e(message = safe, throwable = throwable, tag = tag)
        crashlytics.log("[$tag] ERROR: $safe")
        throwable?.let { crashlytics.recordException(it) }
    }
}
```

- [ ] **Step 2: Criar `LoggerBootstrap.kt`**

Conteúdo INTEIRO de `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/LoggerBootstrap.kt`:

```kotlin
package br.com.sprena.shared.core.logger

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Inicialização do stack de logging. Chamar uma vez na Application.onCreate.
 *
 * Em debug: planta DebugAntilog (println com formatação) e desabilita Crashlytics.
 * Em release: NÃO planta antilog (Napier vira no-op) e habilita Crashlytics —
 * o envio para Crashlytics acontece via [AndroidLogger] explicitamente.
 */
object LoggerBootstrap {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!isDebug)
    }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/
git commit -m "feat(logger): add AndroidLogger (napier + crashlytics) and bootstrap"
```

---

## Task 8: Koin module — `expect`/`actual` para fornecer `Logger` singleton

**Files:**
- Create: `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.kt`
- Create: `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.android.kt`

- [ ] **Step 1: Criar declaração `expect` em commonMain**

Conteúdo INTEIRO de `shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.kt`:

```kotlin
package br.com.sprena.shared.core.logger.di

import org.koin.core.module.Module

/**
 * Cada plataforma fornece sua impl de [br.com.sprena.shared.core.logger.Logger].
 * Android: AndroidLogger (Napier + Crashlytics). iOS (futuro): NSLog + Crashlytics iOS.
 */
expect fun loggerModule(): Module
```

- [ ] **Step 2: Criar `actual` em androidMain**

Conteúdo INTEIRO de `shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/di/LoggerModule.android.kt`:

```kotlin
package br.com.sprena.shared.core.logger.di

import br.com.sprena.shared.core.logger.AndroidLogger
import br.com.sprena.shared.core.logger.Logger
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun loggerModule(): Module = module {
    single<Logger> { AndroidLogger() }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/core/logger/di/ \
        shared/src/androidMain/kotlin/br/com/sprena/shared/core/logger/di/
git commit -m "feat(logger): add Koin loggerModule expect/actual"
```

---

## Task 9: Aplicar plugin Crashlytics no `composeApp/build.gradle.kts`

**Files:**
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Adicionar plugin**

Em `composeApp/build.gradle.kts`, atualizar o bloco `plugins { ... }` (linhas 4-10) para:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}
```

(Ordem importa: `firebase-crashlytics` DEPOIS de `google-services`.)

- [ ] **Step 2: Build release para validar plugin**

Run: `./gradlew :composeApp:assembleRelease`
Expected: BUILD SUCCESSFUL. Plugin Crashlytics gera mapping para deobfuscação de stack traces.

Se aparecer erro `Crashlytics could not find a properly registered google-services.json`, parar — o google-services.json precisa ter `firebase_crashlytics_collection_enabled` (deve já estar; F1.1 não mexeu nele).

- [ ] **Step 3: Commit**

```bash
git add composeApp/build.gradle.kts
git commit -m "build(release): apply firebase-crashlytics gradle plugin"
```

---

## Task 10: Inicializar logging na `SprenaApplication` + registrar Koin module

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/br/com/sprena/SprenaApplication.kt`

- [ ] **Step 1: Atualizar `SprenaApplication.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena

import android.app.Application
import br.com.sprena.shared.core.logger.LoggerBootstrap
import br.com.sprena.shared.core.logger.di.loggerModule
import br.com.sprena.shared.di.sharedModules
import br.com.sprena.shared.di.platformModule
import br.com.sprena.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SprenaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // F1.2: inicializar Napier + Crashlytics ANTES do Koin
        // (módulos podem precisar do Logger no resolve).
        LoggerBootstrap.init(isDebug = BuildConfig.DEBUG)

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@SprenaApplication)
            modules(
                loggerModule(),
                platformModule(),
                *sharedModules().toTypedArray(),
                appModule(),
            )
        }
    }
}
```

> **Nota sobre `BuildConfig`:** o módulo `composeApp` é `android-application` (com `defaultConfig`), então `BuildConfig.DEBUG` é gerado automaticamente. Se o build reclamar de `BuildConfig`, habilitar em `composeApp/build.gradle.kts` dentro de `android { buildFeatures { buildConfig = true } }`. Validar no Step 2.

- [ ] **Step 2: Build debug**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Se falhar com `unresolved reference: BuildConfig`, adicionar em `composeApp/build.gradle.kts`:

```kotlin
buildFeatures {
    compose = true
    buildConfig = true
}
```

E re-rodar. Commit dessa alteração junto se necessária.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/com/sprena/SprenaApplication.kt composeApp/build.gradle.kts
git commit -m "feat(app): initialize napier + crashlytics and register loggerModule"
```

---

## Task 11: Instrumentar `SportClientRepositoryImpl` com try-catch + log

**Files:**
- Modify: `shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/data/repository/SportClientRepositoryImpl.kt`
- Modify: módulo Koin que provê `SportClientRepository` (procurar via `Grep` por `SportClientRepositoryImpl` em `shared/src/androidMain/**/di/`)

- [ ] **Step 1: Localizar o módulo Koin que injeta o repo**

Run: `Grep` por `SportClientRepositoryImpl(` em `shared/src/androidMain/kotlin/`. Anotar o path do módulo (provavelmente `shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/di/SportClientPlatformModule.android.kt` ou similar).

- [ ] **Step 2: Atualizar `SportClientRepositoryImpl.kt`**

Conteúdo INTEIRO de `shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/data/repository/SportClientRepositoryImpl.kt`:

```kotlin
package br.com.sprena.shared.sportclient.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.logger.pii.PiiMasker
import br.com.sprena.shared.sportclient.data.dto.SportClientDto
import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Implementação do [SportClientRepository] usando Firebase Firestore.
 *
 * Coleção Firestore: `sport_clients`. Cada documento mapeia 1:1 com [SportClientDto].
 * Erros são logados via [logger] (com PII mascarado) e re-lançados — ViewModel decide UX.
 */
class SportClientRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val logger: Logger,
) : SportClientRepository {
    private val collection get() = firestore.collection(COLLECTION_NAME)

    override fun observeAll(): Flow<List<SportClientModel>> =
        collection.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
                }
            }
            .catch { e ->
                logger.error(TAG, "observeAll firestore stream failed", e)
                throw e
            }

    override suspend fun getById(id: String): SportClientModel? =
        runCatching {
            val doc = collection.document(id).get().await()
            doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
        }.onFailure { e ->
            logger.error(TAG, "getById failed id=$id", e)
        }.getOrThrow()

    override suspend fun add(client: SportClientModel): String =
        runCatching {
            val dto = SportClientDto.fromDomain(client)
            collection.add(dto).await().id
        }.onFailure { e ->
            logger.error(
                TAG,
                "add failed cpf=${PiiMasker.cpf(client.cpf)} phone=${PiiMasker.phone(client.phone)}",
                e,
            )
        }.getOrThrow()

    override suspend fun update(client: SportClientModel) {
        runCatching {
            val dto = SportClientDto.fromDomain(client)
            collection.document(client.id).set(dto).await()
        }.onFailure { e ->
            logger.error(TAG, "update failed id=${client.id} cpf=${PiiMasker.cpf(client.cpf)}", e)
        }.getOrThrow()
    }

    override suspend fun delete(id: String) {
        runCatching {
            collection.document(id).delete().await()
        }.onFailure { e ->
            logger.error(TAG, "delete failed id=$id", e)
        }.getOrThrow()
    }

    companion object {
        const val COLLECTION_NAME = "sport_clients"
        private const val TAG = "SportClientRepo"
    }
}
```

- [ ] **Step 3: Atualizar o módulo Koin de SportClient para injetar `Logger`**

Abrir o módulo identificado no Step 1. Atualizar a definição (exemplo — o nome exato pode variar):

```kotlin
single<SportClientRepository> {
    SportClientRepositoryImpl(
        firestore = get(),
        logger = get(),
    )
}
```

- [ ] **Step 4: Build + testes**

Run:
```bash
./gradlew :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. Se algum teste de `SportClientRepositoryImpl` existir (procurar em `shared/src/androidUnitTest/`) e quebrar por construtor, adicionar `NoOpLogger()` no fixture.

- [ ] **Step 5: Commit**

```bash
git add shared/src/androidMain/kotlin/br/com/sprena/shared/sportclient/
git commit -m "feat(logger): instrument SportClientRepositoryImpl with masked PII logging"
```

---

## Task 12: Instrumentar `LoginUseCase` com info/warn (sem password)

**Files:**
- Modify: `shared/src/commonMain/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCase.kt`
- Modify: módulo Koin que provê `LoginUseCase` (procurar via `Grep` por `LoginUseCase(`)
- Modify: `shared/src/commonTest/kotlin/br/com/sprena/shared/auth/domain/usecase/LoginUseCaseTest.kt`

- [ ] **Step 1: Atualizar `LoginUseCase.kt`**

Conteúdo INTEIRO:

```kotlin
package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.logger.Logger

/**
 * Caso de uso de login.
 *
 * Responsabilidade única: validar inputs e delegar ao [AuthRepository].
 * Loga sucesso/falha SEM expor a senha (senha nunca entra na string de log).
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(
        username: String,
        password: String,
    ): AuthResult {
        val usernameResult = LoginValidator.validateUsername(username)
        if (!usernameResult.isValid) {
            logger.warn(TAG, "login rejected invalid username")
            return AuthResult.Error(usernameResult.errorMessage ?: "Usuário inválido")
        }

        val passwordResult = LoginValidator.validatePassword(password)
        if (!passwordResult.isValid) {
            logger.warn(TAG, "login rejected invalid password for username=$username")
            return AuthResult.Error(passwordResult.errorMessage ?: "Senha inválida")
        }

        val result = authRepository.authenticate(username, password)
        when (result) {
            is AuthResult.Success -> logger.info(TAG, "login ok username=$username")
            is AuthResult.Error -> logger.warn(TAG, "login failed username=$username reason=${result.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "LoginUseCase"
    }
}
```

- [ ] **Step 2: Atualizar módulo Koin de Auth**

Localizar via `Grep` por `LoginUseCase(` em `shared/src/commonMain/**/di/`. Adicionar `logger = get()`:

```kotlin
factory { LoginUseCase(authRepository = get(), logger = get()) }
```

- [ ] **Step 3: Atualizar `LoginUseCaseTest.kt` para passar `NoOpLogger`**

Ler o arquivo e atualizar TODAS as construções de `LoginUseCase(...)` para incluir `logger = NoOpLogger()`. Adicionar import:

```kotlin
import br.com.sprena.shared.core.logger.NoOpLogger
```

Em cada `val useCase = LoginUseCase(repo)`, substituir por:

```kotlin
val useCase = LoginUseCase(repo, NoOpLogger())
```

- [ ] **Step 4: Rodar testes**

Run: `./gradlew :shared:testDebugUnitTest --tests "*LoginUseCaseTest*"`
Expected: BUILD SUCCESSFUL, todos os testes pré-existentes ainda passam.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/br/com/sprena/shared/auth/ \
        shared/src/commonTest/kotlin/br/com/sprena/shared/auth/
git commit -m "feat(logger): instrument LoginUseCase without leaking password"
```

---

## Task 13: Adicionar regras Proguard para Napier e Crashlytics

**Files:**
- Modify: `composeApp/proguard-rules.pro`

- [ ] **Step 1: Adicionar bloco ao final do arquivo**

Ler `composeApp/proguard-rules.pro` (existente desde F1.1) e adicionar antes do EOF:

```proguard
# ---------------------------------------------------------------------------
# F1.2 — Napier
# ---------------------------------------------------------------------------
-keep class io.github.aakira.napier.** { *; }
-dontwarn io.github.aakira.napier.**

# ---------------------------------------------------------------------------
# F1.2 — Firebase Crashlytics
# ---------------------------------------------------------------------------
# Crashlytics SDK (já coberto pelo bloco geral firebase.**, reforçar para deobfuscation):
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
```

- [ ] **Step 2: Build release com regras novas**

Run: `./gradlew :composeApp:assembleRelease`
Expected: BUILD SUCCESSFUL. Sem novos warnings R8.

- [ ] **Step 3: Commit**

```bash
git add composeApp/proguard-rules.pro
git commit -m "build(release): add proguard rules for napier and crashlytics deobfuscation"
```

---

## Task 14: Pipeline completo + verificação manual

**Files:**
- (none — verification only)

- [ ] **Step 1: Build + test + lint do projeto inteiro**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:assembleRelease \
    :composeApp:testDebugUnitTest :shared:testDebugUnitTest \
    detekt ktlintCheck
```

Expected: BUILD SUCCESSFUL em tudo. Os novos testes (`PiiMaskerTest`, `PiiScrubberTest`) aparecem na contagem.

- [ ] **Step 2: Se detekt acusar novo issue (ex.: `MagicNumber` no regex)**

Corrigir o código — NÃO atualizar o baseline. Re-rodar Step 1.

- [ ] **Step 3: Se ktlint acusar formatação**

Run: `./gradlew ktlintFormat` e re-rodar `ktlintCheck`. Commit separado:
```bash
git add -u
git commit -m "style: ktlintFormat after f1.2 changes"
```

- [ ] **Step 4: Verificação manual em device (debug)**

1. `./gradlew :composeApp:installDebug`
2. Abrir o app, fazer login com credenciais inválidas (ex.: usuário ok, senha "000000" se inválida).
3. Conectar `adb logcat -s LoginUseCase:* SportClientRepo:*` e validar:
   - Mensagens aparecem com tag correta
   - Senha NÃO aparece em nenhum log
   - CPF e phone aparecem mascarados (`***.***.***-XX`, `(11)*****-XX`)
4. Forçar uma falha de Firestore (ex.: desabilitar rede e tentar salvar cliente) — confirmar `error` log com stack trace.

- [ ] **Step 5: Verificação manual em release (opcional pré-merge)**

Se houver dispositivo e chave de assinatura debug disponível:
1. `./gradlew :composeApp:assembleRelease`
2. `adb install composeApp/build/outputs/apk/release/composeApp-release.apk`
3. Confirmar que app abre, navega login → home, e Napier NÃO emite logs (DebugAntilog não foi plantado).
4. Crashlytics dashboard só será visível após mergeado em master + APK distribuído.

---

## Task 15: Documentar em `SECURITY.md` e `ARCHITECTURE.md`

**Files:**
- Modify: `SECURITY.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: Adicionar seção em `SECURITY.md`**

Após a seção "## F1.1 — Build hardening + FLAG_SECURE", adicionar:

````markdown
## F1.2 — Logging seguro (Napier + Crashlytics + sanitização PII)

### Stack
- **Napier 2.7.1** — logger KMP, `DebugAntilog` plantado apenas em debug.
- **Firebase Crashlytics** (BOM 34.12.0) — `log` para warn/error, `recordException` para throwables. Desabilitado em debug (`setCrashlyticsCollectionEnabled(false)`).
- **Interface `Logger`** em `shared/commonMain/core/logger/` — única superfície usada por Repositories/UseCases. `AndroidLogger` é a impl injetada via Koin.

### Sanitização PII
- **`PiiMasker`** (commonMain) — masking explícito pelo call site:
  - `cpf("123.456.789-90")` → `"***.***.***-90"`
  - `phone("11987654321")` → `"(11)*******-21"`
  - `email("pedro@gmail.com")` → `"p***@gmail.com"`
- **`PiiScrubber`** (commonMain) — defense-in-depth: a impl `AndroidLogger` aplica regex sweep ANTES de emitir (CPF formatado, email, password=). Cobre o caso "esqueci de mascarar".

### Convenção de uso
1. **Sempre** receba `Logger` via construtor (Koin injeta).
2. **Nunca** logue objetos de domínio inteiros (`logger.info(TAG, "$client")`) — use campos específicos com `PiiMasker`.
3. **Nunca** logue `password`, mesmo "uma vez para debug".
4. Tag = nome curto da classe (ex.: `"SportClientRepo"`, `"LoginUseCase"`).
5. `error` é para falhas que devem ir ao Crashlytics; `warn` para situações esperadas mas anômalas.

### Trade-offs
- **Crashlytics desligado em debug**: evita poluir o painel com crashes de desenvolvimento. Custo: integração só é validada end-to-end após instalar release build.
- **Scrubber por regex**: pode dar falso-positivo (qualquer 11 dígitos após "cpf" vira mask). Aceito — falso-positivo em log é inofensivo, falso-negativo seria vazamento.
- **Instrumentação parcial**: apenas `SportClientRepositoryImpl` e `LoginUseCase` instrumentados. Demais Repos/UseCases entram conforme F2 (Clean Architecture) os refatorar.

### Verificação manual (pré-merge)
- [ ] `./gradlew :shared:testDebugUnitTest --tests "*Pii*"` — 22 tests pass
- [ ] Login com credencial inválida loga "login rejected" sem mostrar a senha em logcat
- [ ] Salvar cliente com Firestore offline gera `error` log com CPF mascarado
- [ ] APK release abre, navega normalmente, sem logs em logcat (Napier no-op em release)
````

- [ ] **Step 2: Atualizar `ARCHITECTURE.md`**

Localizar a seção "## Segurança" (criada em F1.1). Atualizar para mencionar F1.2:

```markdown
## Segurança

Decisões de segurança e endurecimento de build estão documentadas em [SECURITY.md](./SECURITY.md). Fases aplicadas:

- **F1.1** — minificação R8, bloqueio de backup, network security config, `FLAG_SECURE`.
- **F1.2** — logging seguro (Napier + Crashlytics) com sanitização de PII (`PiiMasker`/`PiiScrubber`) e instrumentação inicial de Repositories críticos.

Próximas sub-fases de F1 adicionarão Firebase Auth (F1.3), Firestore Rules + App Check (F1.4) e baseline LGPD (F1.5).
```

- [ ] **Step 3: Commit docs**

```bash
git add SECURITY.md ARCHITECTURE.md
git commit -m "docs(security): document f1.2 logging architecture and pii sanitization"
```

---

## Task 16: Mover este plano para `docs/superpowers/plans/` e commit final

**Files:**
- Create: `docs/superpowers/plans/2026-05-25-f1-2-secure-logging.md`

- [ ] **Step 1: Copiar este plano para o local canônico**

Copiar o conteúdo deste arquivo (`C:\Users\pecru\.claude\plans\escreva-o-plano-f1-2-sprightly-lightning.md`) para `docs/superpowers/plans/2026-05-25-f1-2-secure-logging.md`. Mesmo conteúdo, novo nome alinhado com a convenção F0/F1.1.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-05-25-f1-2-secure-logging.md
git commit -m "docs(plan): add f1.2 secure-logging implementation plan"
```

---

## Task 17: Push + abrir PR + esperar CI

**Files:**
- (none — Git/GitHub)

- [ ] **Step 1: Push da branch**

Run:
```bash
git push -u origin feature/f1-2-secure-logging
```

- [ ] **Step 2: Abrir PR**

Run:
```bash
gh pr create \
  --title "F1.2: secure logging (Napier + Crashlytics) + PII sanitization" \
  --body "$(cat <<'EOF'
## Summary

Segunda sub-fase de F1 (segurança crítica). Instala o stack de logging seguro do Sprena:

- `Logger` interface em `shared/commonMain/core/logger/`; impl Android com Napier 2.7.1 + Firebase Crashlytics
- `PiiMasker` (cpf/phone/email) + `PiiScrubber` (regex safety net) — ambos TDD, 22 testes
- Crashlytics desabilitado em debug, habilitado em release; plugin gradle aplicado em `composeApp`
- `LoggerBootstrap.init(isDebug)` chamado na `SprenaApplication` antes do Koin
- Instrumentação inicial: `SportClientRepositoryImpl` (try-catch + log com PII mascarado) e `LoginUseCase` (sem password no log)
- Proguard rules para Napier e deobfuscation Crashlytics
- `SECURITY.md` documenta convenção de uso e trade-offs

F1.3 (Firebase Auth) e F1.4 (Firestore Rules) virão em sub-fases independentes.

## Test plan

- [x] `./gradlew :shared:testDebugUnitTest --tests "*Pii*"` — 22 tests pass (TDD)
- [x] `./gradlew :composeApp:assembleDebug :composeApp:assembleRelease` BUILD SUCCESSFUL
- [x] `./gradlew detekt ktlintCheck` sem novos issues
- [ ] Login com credencial inválida: senha NÃO aparece em logcat (manual)
- [ ] Salvar cliente offline: error log com CPF mascarado `***.***.***-XX` (manual)
- [ ] Release APK: Napier no-op (sem logs em logcat) (manual)
EOF
)"
```

- [ ] **Step 3: Esperar CI verde**

Run:
```bash
gh pr checks --watch
```

Expected: todos os checks (build, test, detekt, ktlint) passam.

- [ ] **Step 4: Reportar URL do PR ao maintainer**

Não fazer merge automaticamente — Pedro revisa o PR.

---

## Self-review checklist

- [x] Cobertura do spec roadmap F1 item "Logging seguro": Napier ✓, Crashlytics ✓, sanitização PII ✓
- [x] TDD aplicado em `PiiMasker` e `PiiScrubber` (testes ANTES da impl)
- [x] Nenhum placeholder ("TBD", "fill in", "handle edge cases")
- [x] Cada Task tem step de commit
- [x] Build verification em tasks que mexem em gradle/manifest/proguard
- [x] PR template segue padrão F0/F1.1 (Summary + Test plan)
- [x] Sem dependência circular com F1.3-F1.5
- [x] CLAUDE.md respeitado: Firebase só em androidMain ✓, Koin (não Hilt) ✓, sem mutar State ✓, sem GlobalScope ✓
- [x] Tipos consistentes entre tasks: `Logger.error(tag, message, throwable?)` mesma assinatura em Task 5, 7, 11, 12

## Out of scope (vai pra F1.3–F1.5 ou F2)

- Logging de todos os ViewModels e UseCases existentes — F2 (Clean Architecture) instrumenta conforme refatora
- Firebase Auth + DataStore criptografado — F1.3
- BiometricPrompt 2FA — F1.3
- Firestore Security Rules + App Check — F1.4
- Consentimento LGPD + masking de CPF na UI + hash de CPF em rest — F1.5
- Analytics com Consent Mode v2 — F5
- Sentry KMP como alternativa a Crashlytics — F5 (decisão futura)
