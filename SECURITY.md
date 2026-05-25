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
