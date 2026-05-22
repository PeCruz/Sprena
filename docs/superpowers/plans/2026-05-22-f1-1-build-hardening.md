# F1.1 — Build Hardening + FLAG_SECURE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Endurecer o build de release e bloquear capturas de tela em telas com dados sensíveis — primeira sub-fase de F1 (segurança crítica). Nenhuma alteração de feature/UX, apenas configuração e um hook de Activity.

**Architecture:** Mudanças concentradas em três superfícies: (1) `composeApp/build.gradle.kts` ganha buildType `release` com R8/minify+shrinkResources e referência ao Proguard; (2) `AndroidManifest.xml` recebe `allowBackup=false`, `dataExtractionRules`, `networkSecurityConfig` e remove cleartext; (3) `MainActivity.onCreate()` aplica `WindowManager.LayoutParams.FLAG_SECURE` globalmente — abordagem coarse (bloqueia screenshots/recording do app inteiro) escolhida por simplicidade vs. wrapping per-screen. Nenhuma mudança em `commonMain`. F1.2–F1.5 dependem desta baseline.

**Tech Stack:** Android Gradle Plugin 8.7.3 (R8 já integrado), Proguard rules manuais, `network_security_config.xml` (Android resource), `data_extraction_rules.xml` (Android 12+ resource), `FLAG_SECURE` (window flag nativo).

---

## Premissas

- Branch atual: `feature/f0-foundation` já foi mergeada via PR #8 em master.
- Trabalhar a partir do **master atualizado**, criar branch nova `feature/f1-1-build-hardening`.
- Detekt + ktlint + CI já existem (F0). Tudo precisa continuar passando.
- `applicationId = "br.com.sprena"`, `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` (não mexer).
- Nenhuma rota HTTP cleartext existe hoje (Firestore é HTTPS) — `networkSecurityConfig` apenas formaliza a baseline.

## Estrutura de arquivos

**Novos:**
- `composeApp/proguard-rules.pro` — regras Proguard para Firebase, Koin, kotlinx.serialization, Compose e modelos de domínio
- `composeApp/src/androidMain/res/xml/network_security_config.xml` — força HTTPS, sem cleartext
- `composeApp/src/androidMain/res/xml/data_extraction_rules.xml` — exclui dados sensíveis de backup/transfer (Android 12+)
- `SECURITY.md` — registra decisões: allowBackup=false, FLAG_SECURE global, network security baseline. Curto, na raiz.

**Modificados:**
- `composeApp/build.gradle.kts` — adicionar bloco `buildTypes { release { ... } }` com `isMinifyEnabled = true`, `isShrinkResources = true` e `proguardFiles(...)`. Também `signingConfig` placeholder via debug pra `assembleRelease` rodar local sem chave de release real.
- `composeApp/src/androidMain/AndroidManifest.xml` — `allowBackup="false"`, `dataExtractionRules`, `networkSecurityConfig`, `usesCleartextTraffic="false"`
- `composeApp/src/androidMain/kotlin/br/com/sprena/MainActivity.kt` — aplicar `FLAG_SECURE` em `onCreate` antes de `setContent`
- `ARCHITECTURE.md` — adicionar entrada na seção de ADRs apontando para `SECURITY.md`

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

Expected: `master` no mesmo SHA de `origin/master`. Sem conflitos.

- [ ] **Step 2: Criar e checkout da branch de feature**

Run:
```bash
git checkout -b feature/f1-1-build-hardening
```

Expected: shell prompt agora mostra `feature/f1-1-build-hardening`.

- [ ] **Step 3: Validar baseline antes de mudar nada**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest detekt ktlintCheck
```

Expected: BUILD SUCCESSFUL em todos os módulos. Se algo falhar antes de F1.1 começar, parar e investigar a regressão de master antes de prosseguir.

---

## Task 2: Adicionar buildType `release` com R8 + shrink + proguard

**Files:**
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Adicionar bloco `buildTypes` em `android { ... }`**

Editar `composeApp/build.gradle.kts`. Logo após o bloco `buildFeatures { compose = true }` (linha 89), antes do `}` que fecha `android { ... }`, adicionar:

```kotlin
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // F1.1: assina com a chave debug para `assembleRelease` rodar local.
            // Substituir por signingConfig real ao publicar (F6/Pós-MVP).
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
```

O bloco completo `android { ... }` deve ficar assim:

```kotlin
android {
    namespace = "br.com.sprena"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.sprena"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
```

- [ ] **Step 2: Tentar build release — deve falhar por falta do `proguard-rules.pro`**

Run: `./gradlew :composeApp:assembleRelease`
Expected: FAILED. A mensagem deve mencionar que `proguard-rules.pro` não existe (ou que minificação não encontrou regras). Esta falha confirma que o plugin está exigindo o arquivo — vamos criá-lo na Task 3.

- [ ] **Step 3: Commit parcial (build config)**

```bash
git add composeApp/build.gradle.kts
git commit -m "build(release): enable R8 minify + shrink for release"
```

---

## Task 3: Criar `proguard-rules.pro` com regras para Firebase, Koin, Compose, kotlinx.serialization

**Files:**
- Create: `composeApp/proguard-rules.pro`

- [ ] **Step 1: Criar o arquivo `composeApp/proguard-rules.pro`**

Conteúdo INTEIRO do arquivo:

```proguard
# ============================================================================
# Sprena — Proguard rules (F1.1 baseline)
# ============================================================================
# proguard-android-optimize.txt (do Android SDK) já cobre o básico.
# Aqui adicionamos regras específicas das libs do projeto.

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlin.**
-keepclasseswithmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation

# ---------------------------------------------------------------------------
# kotlinx.coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}
-keepclassmembers class ** implements kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}

# ---------------------------------------------------------------------------
# kotlinx.serialization (usado no módulo shared)
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class <1>.<2> {
    <1>.<2>$Companion Companion;
}

# ---------------------------------------------------------------------------
# Koin DI
# ---------------------------------------------------------------------------
-keep class org.koin.** { *; }
-keepclassmembers class * {
    public <init>(...);
}

# ---------------------------------------------------------------------------
# Firebase (Firestore + futuras libs F1.3/F1.4)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore usa reflection para mapear DTOs ↔ documentos.
# Preservar TODOS os DTOs e modelos serializados.
-keep class br.com.sprena.shared.**.data.dto.** { *; }
-keep class br.com.sprena.shared.**.domain.model.** { *; }
-keepclassmembers class br.com.sprena.shared.**.data.dto.** {
    <init>(...);
    <fields>;
}

# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# AndroidX Lifecycle & Navigation
# ---------------------------------------------------------------------------
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation.**
```

- [ ] **Step 2: Build release com as regras**

Run: `./gradlew :composeApp:assembleRelease`
Expected: BUILD SUCCESSFUL. Gera `composeApp/build/outputs/apk/release/composeApp-release.apk`.

Se aparecerem warnings tipo `R8: Missing class ...`, copiar o nome da classe e adicionar `-dontwarn <fqcn>` no `.pro`. Se aparecerem erros de runtime que indicam shrinking incorreto, adicionar `-keep` específico.

- [ ] **Step 3: Verificar que o APK foi gerado e está minificado**

Run:
```bash
ls -lh composeApp/build/outputs/apk/release/
```

Expected: arquivo `composeApp-release.apk` existe. Tamanho < `composeApp-debug.apk` (sinal de shrink ativo).

- [ ] **Step 4: Commit**

```bash
git add composeApp/proguard-rules.pro
git commit -m "build(release): add proguard rules for firebase/koin/compose/serialization"
```

---

## Task 4: Criar `network_security_config.xml` (HTTPS only)

**Files:**
- Create: `composeApp/src/androidMain/res/xml/network_security_config.xml`

- [ ] **Step 1: Criar diretório e arquivo**

Run (PowerShell):
```powershell
New-Item -ItemType Directory -Force composeApp/src/androidMain/res/xml | Out-Null
```

Run (bash):
```bash
mkdir -p composeApp/src/androidMain/res/xml
```

- [ ] **Step 2: Escrever `network_security_config.xml`**

Conteúdo INTEIRO:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    F1.1 — Sprena network security baseline.
    - cleartextTrafficPermitted=false: bloqueia HTTP em runtime.
    - Sem domain-config: aplica a tudo (Firestore já é HTTPS por padrão).
    - Quando F1.3 adicionar OAuth (Google Sign-In), `accounts.google.com`
      continua HTTPS, não precisa override.
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/res/xml/network_security_config.xml
git commit -m "security: add network security config forcing https"
```

---

## Task 5: Criar `data_extraction_rules.xml` (Android 12+ backup/transfer)

**Files:**
- Create: `composeApp/src/androidMain/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Escrever `data_extraction_rules.xml`**

Conteúdo INTEIRO de `composeApp/src/androidMain/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    F1.1 — Regras de extração de dados (Android 12+).
    Mesmo com allowBackup=false, declarar explicitamente para D2D transfer.
    cloud-backup="false" + device-transfer="false" => nada sai do device.

    Quando F1.3 adicionar DataStore com sessão criptografada, este arquivo
    continua válido — DataStore fica em files/datastore/, já bloqueado.
-->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/androidMain/res/xml/data_extraction_rules.xml
git commit -m "security: add data extraction rules excluding all domains"
```

---

## Task 6: Atualizar `AndroidManifest.xml` (allowBackup=false, configs, no cleartext)

**Files:**
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Substituir o `AndroidManifest.xml` inteiro**

Conteúdo INTEIRO:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".SprenaApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.Sprena"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.Sprena">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

- [ ] **Step 2: Build debug e release para garantir manifest válido**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:assembleRelease
```

Expected: ambos BUILD SUCCESSFUL. Se aparecer "AAPT: resource xml/network_security_config not found" ou similar, revisar caminhos das Tasks 4 e 5.

- [ ] **Step 3: Verificar que o manifest final mergeado contém os atributos**

Run:
```bash
cat composeApp/build/intermediates/merged_manifest/release/AndroidManifest.xml
```

Expected: linhas contendo `android:allowBackup="false"`, `android:networkSecurityConfig="@xml/network_security_config"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:usesCleartextTraffic="false"`.

Se o caminho exato variar por versão do AGP, alternativa:
```bash
find composeApp/build -name "AndroidManifest.xml" -path "*/merged_manifest*"
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/AndroidManifest.xml
git commit -m "security(manifest): allowBackup=false, no cleartext, link xml configs"
```

---

## Task 7: Aplicar `FLAG_SECURE` global em `MainActivity`

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/br/com/sprena/MainActivity.kt`

- [ ] **Step 1: Substituir `MainActivity.kt` inteira**

Conteúdo INTEIRO de `composeApp/src/androidMain/kotlin/br/com/sprena/MainActivity.kt`:

```kotlin
package br.com.sprena

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * MainActivity — única Activity do app.
 *
 * F1.1: aplica [WindowManager.LayoutParams.FLAG_SECURE] em todo o app.
 * Why: bloqueia screenshots/screen recording em telas com dados sensíveis
 * (login, CPF do cliente, valores financeiros). Abordagem global por
 * simplicidade — alternativa per-screen exigiria wrapping em todo Composable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
```

- [ ] **Step 2: Build debug**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verificação manual em dispositivo**

(Manual — não é automatizável em CI sem device farm.)

1. Instalar o APK debug em um device/emulator: `./gradlew :composeApp:installDebug`
2. Abrir o app, tentar tirar screenshot (Power+VolDown).
3. Expected: o sistema mostra "screenshot blocked by app" OU o screenshot sai preto.
4. Tentar gravar a tela: a gravação também deve sair preta na região do app.

Se não puder testar em device agora, registrar em `SECURITY.md` (Task 9) que esta verificação é prerequisite antes do merge final.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/br/com/sprena/MainActivity.kt
git commit -m "security: apply FLAG_SECURE in MainActivity to block screenshots"
```

---

## Task 8: Rodar pipeline completo localmente

**Files:**
- (none — verification only)

- [ ] **Step 1: Build + test + lint do projeto inteiro**

Run:
```bash
./gradlew :composeApp:assembleDebug :composeApp:assembleRelease \
    :composeApp:testDebugUnitTest :shared:testDebugUnitTest \
    detekt ktlintCheck
```

Expected: BUILD SUCCESSFUL. Nenhum teste regredido. Detekt e ktlint sem novos issues (o baseline F0 cobre os pré-existentes).

- [ ] **Step 2: Se detekt acusar novo issue**

Se algum issue novo aparecer (por ex. estilo no `MainActivity.kt`), corrigir o código — NÃO atualizar o baseline silenciosamente. Re-rodar o comando do Step 1.

- [ ] **Step 3: Se ktlint acusar formatação**

Run: `./gradlew ktlintFormat`
Depois re-rodar `./gradlew ktlintCheck`. Stage e commit em separado:
```bash
git add -u
git commit -m "style: ktlintFormat after f1.1 changes"
```

---

## Task 9: Documentar decisões em `SECURITY.md` e referenciar no `ARCHITECTURE.md`

**Files:**
- Create: `SECURITY.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: Criar `SECURITY.md`**

Conteúdo INTEIRO de `SECURITY.md` (raiz do repo):

````markdown
# Security — Sprena

Decisões de segurança aplicadas ao projeto. Cada sub-fase de F1 adiciona uma seção aqui.

## F1.1 — Build hardening + FLAG_SECURE

### R8 / Minify / Shrink (release build)

- `isMinifyEnabled = true` e `isShrinkResources = true` no `release`.
- Regras em `composeApp/proguard-rules.pro`. Cobertura:
  - Kotlin metadata, coroutines, kotlinx.serialization
  - Koin (DI por reflection)
  - Firebase Firestore (mapeamento DTO ↔ documento por reflection)
  - Compose / Lifecycle / Navigation
- DTOs e modelos de domínio em `shared/**/data/dto/` e `shared/**/domain/model/` são preservados.

**Trade-off:** debug build NÃO é minificado (mantém stack traces legíveis). O `signingConfig = signingConfigs.getByName("debug")` permite `assembleRelease` rodar local; substituir por chave real ao publicar.

### `allowBackup="false"` + Data extraction rules

- `android:allowBackup="false"` no manifest — desabilita auto-backup Android.
- `data_extraction_rules.xml` (Android 12+) — exclui todos os domínios de cloud backup e device transfer.

**Trade-off:** usuário perde "auto-backup pro Google Drive" e "transfer ao trocar de device". Justificativa: cadastros de clientes contêm CPF (mesmo que mascarado/hash em F1.5) — não vamos confiar nesse dado a backup automático fora do nosso controle.

### Network Security Config

- `network_security_config.xml` com `cleartextTrafficPermitted="false"` + `usesCleartextTraffic="false"` no manifest.
- Bloqueia HTTP em runtime, exige HTTPS. Firestore já é HTTPS — esta config formaliza a baseline.

### FLAG_SECURE global em `MainActivity`

- `window.setFlags(FLAG_SECURE, FLAG_SECURE)` em `onCreate`.
- Bloqueia screenshots e screen recording do app inteiro.

**Trade-off:** abordagem coarse (afeta TODAS as telas, inclusive Home, Settings, etc.). Alternativa per-screen exigiria wrap em cada `Composable` — descartada por complexidade e risco de esquecer alguma tela com CPF/valores.

### Verificação manual (pré-merge)

- [ ] APK release gera sem warnings novos (`./gradlew :composeApp:assembleRelease`)
- [ ] Manifest mergeado contém os atributos (Task 6 Step 3)
- [ ] Screenshot do app em device real sai preto / é bloqueado pelo sistema
- [ ] App ainda abre, navega Login → Home, consegue ler/escrever Firestore
````

- [ ] **Step 2: Adicionar referência no `ARCHITECTURE.md`**

Localizar a seção de ADRs (Architecture Decision Records) em `ARCHITECTURE.md`. Se não existir uma seção dedicada à segurança, adicionar ao final do arquivo (antes de qualquer "## Roadmap" ou EOF):

```markdown
## Segurança

Decisões de segurança e endurecimento de build estão documentadas em [SECURITY.md](./SECURITY.md). A fase atual (F1.1) cobre minificação R8, bloqueio de backup, network security config e `FLAG_SECURE`. Próximas sub-fases de F1 adicionarão Firebase Auth, App Check, Firestore Rules e tratamento LGPD.
```

Se a seção "Roadmap" já existe em `ARCHITECTURE.md`, inserir a seção "## Segurança" imediatamente antes dela.

- [ ] **Step 3: Commit docs**

```bash
git add SECURITY.md ARCHITECTURE.md
git commit -m "docs(security): document f1.1 hardening decisions"
```

---

## Task 10: Push, abrir PR e validar CI

**Files:**
- (none — Git/GitHub operations)

- [ ] **Step 1: Push da branch**

Run:
```bash
git push -u origin feature/f1-1-build-hardening
```

Expected: branch publicada em `origin`.

- [ ] **Step 2: Abrir PR via gh CLI**

Run:
```bash
gh pr create \
  --title "F1.1: build hardening + FLAG_SECURE" \
  --body "$(cat <<'EOF'
## Summary

Primeira sub-fase de F1 (segurança crítica). Endurece o build de release e bloqueia capturas de tela do app.

- R8 minify + shrink no `release`, com proguard-rules para Firebase/Koin/Compose/kotlinx.serialization
- `allowBackup=false`, `data_extraction_rules.xml`, `network_security_config.xml`, `usesCleartextTraffic=false`
- `FLAG_SECURE` global em `MainActivity` — bloqueia screenshots/recording em todas as telas
- `SECURITY.md` registra cada decisão + trade-offs; `ARCHITECTURE.md` aponta pra ele

Nenhuma alteração de feature/UX. F1.2 (logging) e F1.3 (Firebase Auth) seguem em sub-fases independentes.

## Test plan

- [x] `./gradlew :composeApp:assembleDebug :composeApp:assembleRelease` BUILD SUCCESSFUL local
- [x] `./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest` sem regressões
- [x] `./gradlew detekt ktlintCheck` sem novos issues
- [ ] Manifest mergeado contém `allowBackup=false` + `networkSecurityConfig` + `dataExtractionRules` + `usesCleartextTraffic=false`
- [ ] Screenshot em device real é bloqueado pelo sistema (verificação manual antes do merge)
- [ ] App abre, navega Login → Home, lê/escreve Firestore normalmente
EOF
)"
```

Expected: PR criado, URL printada.

- [ ] **Step 3: Esperar CI verde**

Run:
```bash
gh pr checks --watch
```

Expected: todos os checks (build, test, detekt, ktlint) passam. Se algum falhar, ler o log com `gh run view --log-failed`, corrigir, commitar, push, e o CI re-roda.

- [ ] **Step 4: Reportar URL do PR ao maintainer**

Não fazer merge automaticamente — Pedro revisa o PR antes do merge.

---

## Self-review checklist (pré-execução do plano)

- [x] Cobertura do spec F1.1: R8/Proguard ✓, allowBackup=false ✓, networkSecurityConfig ✓, FLAG_SECURE ✓
- [x] Nenhum placeholder ("TBD", "fill in", "handle edge cases")
- [x] Cada Task tem step de commit
- [x] Build verification em todas as tasks que mexem em build/manifest/Activity
- [x] PR template segue padrão F0 (Summary + Test plan)
- [x] Nenhuma dependência de F1.2–F1.5 (esta sub-fase é independente)

## Out of scope (vai pra F1.2–F1.5)

- Firebase Auth + Google Sign-In (F1.3)
- DataStore criptografado para sessão (F1.3)
- BiometricPrompt 2FA (F1.3)
- Firestore Security Rules + App Check (F1.4)
- Consentimento LGPD + política de privacidade + masking + hash CPF (F1.5)
- Napier + Crashlytics + sanitização PII (F1.2)
