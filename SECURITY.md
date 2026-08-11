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

## F1.3 — Firebase Auth + Sessão Criptografada

### Stack
- **Firebase Authentication** (BOM 34.12.0) com email + senha. `MockAuthRepository` removido.
- **Roles**: doc Firestore `users/{uid}` com `role: "ADM" | "MOD" | "CLIENT"`. Protegido via Security Rules em F1.4.
- **Sessão local**: `EncryptedSessionStore` usa Google Tink 1.13.0 (AEAD AES-256-GCM, chave no Android Keystore via `AndroidKeysetManager`) sobre `androidx.datastore:datastore-preferences`. Persiste: `uid`, `email`, `role`, `lastLoginEpochMillis`.
- **TTL**: 24h. Validado por `SessionValidator.isExpired`.
- **Clock**: abstração injetável (`SystemClock` em prod, `FixedClock` em testes).

### Fluxos
- **Login**: `LoginUseCase` valida → `FirebaseAuthRepositoryImpl.authenticate` → lê role no Firestore → `SessionStore.save`
- **Cold start**: `RestoreSessionUseCase` → se sessão local válida e uid bate com `auth.currentUser?.uid`, vai pra Home; senão Login
- **Logout**: `LogoutUseCase` → `auth.signOut()` + `SessionStore.clear()` (botão na `SettingsScreen` seção "Conta")
- **Reset de senha**: `RequestPasswordResetUseCase` → `auth.sendPasswordResetEmail` (link no `LoginScreen`)

### Erros mapeados (FirebaseAuth → PT-BR)
- `ERROR_INVALID_EMAIL` → "Email inválido"
- `ERROR_USER_NOT_FOUND` / `ERROR_WRONG_PASSWORD` / `ERROR_INVALID_CREDENTIAL` → "Email ou senha incorretos" (mesma mensagem — anti-enumeração)
- `ERROR_USER_DISABLED` → "Conta desativada. Contate o administrador"
- `ERROR_TOO_MANY_REQUESTS` → "Muitas tentativas. Tente em alguns minutos"
- `FirebaseNetworkException` → "Sem conexão. Verifique a internet"
- Outros → "Erro de autenticação"

Mapeamento em `AuthErrorMapper.kt` (`mapAuthError`), coberto por `AuthErrorMapperTest`.
Os ramos de `FirebaseFirestoreException` foram adicionados em F1.4 — ver abaixo.

### Convenção de uso
1. Nunca logar `password` — `LoginUseCase` e `FirebaseAuthRepositoryImpl` já garantem isso.
2. Sempre mascarar email no log via `PiiMasker.email(...)`.
3. Criar novos usuários SEMPRE pela Firebase Console (Auth + doc Firestore `users/{uid}` com role).
   Passo-a-passo operacional: [docs/ops/firebase-users-runbook.md](./docs/ops/firebase-users-runbook.md).
4. Em testes, injetar `FakeSessionStore` + `FixedClock` + `FakeAuthRepository`.

### Trade-offs
- **TTL 24h**: balanço entre conforto do operador (uma diária) e janela de exposição em device perdido.
- **Role no Firestore (vs Custom Claims)**: solo dev sem backend; aceita 1 leitura/login. Migrar para Custom Claims em F2 se F1.4 mostrar overhead.
- **Cadastro off-band**: zero superfície de abuso, mas exige intervenção manual do admin pra cada novo operador. Self-signup volta em F6 se houver demanda.
- **Tink + DataStore**: lib não-deprecated, AES-256-GCM com chave Hardware-backed (Android Keystore). Falha de decifragem → `load()` retorna null e força novo login.

### Verificação manual (pré-merge)
- [ ] Criar via Firebase Console: 1 user (auth) + doc `users/{uid}` com `email`, `role`, `name`
- [ ] Login → vai pra Home; logs com email mascarado via `PiiMasker.email`
- [ ] Fechar/abrir app → auto-login (não passa por Login)
- [ ] Sessão > 24h → cold start volta pra Login
- [ ] Settings → "Sair" → volta pra Login, auto-login desligado
- [ ] "Esqueci a senha" → email chega
- [ ] `session_prefs.preferences_pb` em disco é ilegível (Tink AEAD)

---

## F1.4 — Firestore Security Rules

### O que motivou
Login falhava com `PERMISSION_DENIED` **depois** de o Firebase Auth aceitar a senha: o banco estava
com as regras default de *production mode* (`allow read, write: if false`), então a leitura de
`users/{uid}` que resolve a role era negada. Nenhuma regra tinha sido escrita até aqui — o app
dependia inteiramente do RBAC client-side, que não vale nada contra quem chama a API direto.

### Modelo de acesso (`firestore.rules`, na raiz)

| Path | read | write |
|---|---|---|
| `users/{uid}` | só o próprio dono (`request.auth.uid == uid`) | **ninguém** |
| `sport_clients/{id}` | qualquer autenticado | só `ADM`/`MOD` |
| qualquer outra | negado | negado |

- **`users` é read-only pelo app** de propósito: provisionamento é Console/Admin SDK
  ([runbook](./docs/ops/firebase-users-runbook.md)). Se o app pudesse escrever ali, qualquer conta
  logada se auto-promoveria a `ADM` — a role está no mesmo doc que ela mesma controlaria.
- **Default deny explícito**: `kanban` e `financial` ainda são in-memory. Quando migrarem para o
  Firestore, vão bater no deny até ganharem seu próprio bloco `match`. Isso é intencional — falha
  fechada, não aberta.
- **`isStaff()` usa `get()`** no doc de perfil: custa 1 document access por escrita em `sport_clients`.
  É o mesmo trade-off já aceito em F1.3 (role no Firestore em vez de Custom Claims).

### Testes
`tools/firestore-rules-tests/` — 12 casos contra o emulador (`npm run test:emulator`), rodados no CI
no job `firestore-rules`. Projeto `demo-sprena`: emulador 100% offline, sem credencial nem
`firebase login`. Cobre auto-promoção de role, leitura cruzada entre usuários, escrita por `CLIENT`,
usuário autenticado sem doc de perfil e coleção não mapeada.

Deploy: `firebase deploy --only firestore:rules --project <projeto>`.
O `.firebaserc` é gitignorado (mesma postura do `google-services.json`) — daí o `--project` explícito.

### Erro na UI
`PERMISSION_DENIED` agora vira **"Conta sem permissão de acesso. Contate o administrador"** em vez do
genérico "Erro de autenticação", e o log carrega `code=PERMISSION_DENIED`. Sem isso, o sintoma aponta
para credencial quando o problema é autorização.

### Trade-offs
- **Leitura de `sport_clients` liberada para qualquer autenticado**: `CLIENT` (funcionário) precisa
  ver a lista para operar. PII (CPF/telefone) fica exposta a toda conta válida — o masking/hash em
  repouso é F1.5, e é ele que fecha essa ponta, não as rules.
- **Role em doc vs Custom Claims**: mantido. Se o `get()` por escrita virar custo real, Custom Claims
  elimina a leitura extra e permite `request.auth.token.role` direto na regra.

### Fora de escopo (F1.4b)
**Firebase App Check** (Play Integrity + Debug Provider). Sem ele, as rules protegem *quem* acessa,
mas não *de onde* — um cliente forjado com credencial válida ainda passa. Exige registro de SHA no
Console e cuidado para não travar builds debug.
