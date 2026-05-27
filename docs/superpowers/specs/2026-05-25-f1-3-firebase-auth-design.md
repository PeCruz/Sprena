# F1.3 — Firebase Auth real + sessão criptografada — Design

> **Status:** spec aprovada via brainstorming em 2026-05-25. Próximo passo: gerar plano via `writing-plans` skill.

## Contexto

F1.2 entregou logging seguro com `Logger`/`PiiMasker`/`PiiScrubber` e Crashlytics ativo em release. O auth do app continua sendo um **mock** (`MockAuthRepository` em `shared/commonMain`) que aceita username 3-8 chars + senha de 6 dígitos. F1.3 substitui esse mock por **Firebase Authentication** real (email + senha), introduz **sessão criptografada local** em DataStore com TTL de 24h, e adiciona reset de senha + auto-login + logout. Esta sub-fase é pré-requisito para F1.4 (Firestore Security Rules — regras vão depender de `request.auth.uid` real).

## Decisões já tomadas (brainstorming)

| Decisão | Valor | Por quê |
|---|---|---|
| Método de auth | Email + senha | Suportado nativo pelo Firebase, sem backend |
| Cadastro de users | Admin via Firebase Console | MVP solo, sem self-signup; remove superfície de abuso |
| Storage de role | Firestore `users/{uid}` doc | Sem backend; 1 leitura/login é irrisório; F1.4 vai usar mesmo doc |
| Cripto da sessão | Tink AEAD + DataStore Preferences | Sem APIs deprecated; AES-256-GCM com chave no Android Keystore |
| Conteúdo persistido | `uid`, `email`, `role`, `lastLoginEpochMillis` | Mínimo necessário para auto-login e enriquecimento de logs |
| TTL de sessão | 24h | Janela de "uma diária" típica do domínio (operação esportiva) |
| Reset de senha | Sim, via `sendPasswordResetEmail` | Sem backend; mata risco de esquecimento sem intervenção manual |
| Auto-login | Sim, se `currentUser != null` E sessão < 24h | UX padrão; Firebase já persiste o token |
| Logout button | `SettingsScreen` (já existe) | Não inventa tela nova |

## Arquitetura

Quatro unidades com fronteiras claras:

### 1. `shared/auth/data` — Firebase Auth impl

- **`FirebaseAuthRepositoryImpl : AuthRepository`** em `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/`
- Substitui `MockAuthRepository` (que será deletado).
- API pública (igual à existente `AuthRepository`, mas com argumentos renomeados):
  - `suspend authenticate(email: String, password: String): AuthResult`
  - `suspend sendPasswordReset(email: String): Result<Unit>` (novo método na interface)
  - `suspend signOut()` (novo)
  - `fun currentUid(): String?` (novo, expõe `FirebaseAuth.currentUser?.uid`)
- Após `signInWithEmailAndPassword`, lê `users/{uid}` no Firestore:
  - Doc shape: `{ email: String, role: String, name: String }`
  - `role` é parseado para `UserRole` (case-insensitive); falha de parse → `AuthResult.Error("Conta sem perfil válido")`
  - Se doc não existe → `AuthResult.Error("Conta não autorizada")`
- Erros mapeados de `FirebaseAuthException` para PT-BR:
  - `ERROR_INVALID_EMAIL` → "Email inválido"
  - `ERROR_USER_NOT_FOUND` / `ERROR_WRONG_PASSWORD` / `ERROR_INVALID_CREDENTIAL` → "Email ou senha incorretos" (mesma mensagem, anti-enumeração)
  - `ERROR_USER_DISABLED` → "Conta desativada. Contate o administrador"
  - `ERROR_TOO_MANY_REQUESTS` → "Muitas tentativas. Tente em alguns minutos"
  - `FirebaseNetworkException` → "Sem conexão. Verifique a internet"
  - Outros → "Erro de autenticação" (genérico)
- Todos os erros são logados via `Logger.warn` (do F1.2) com email mascarado por `PiiMasker.email(...)`.

### 2. `shared/auth/session` — Sessão local cifrada

Camadas:

**Pure (commonMain):**
- `data class SessionUser(uid: String, email: String, role: UserRole, lastLoginEpochMillis: Long)`
- `object SessionValidator { fun isExpired(lastLoginEpochMillis: Long, nowEpochMillis: Long, ttlMillis: Long = SESSION_TTL_MILLIS): Boolean }`
- `const val SESSION_TTL_MILLIS = 24L * 60L * 60L * 1000L`
- `interface SessionStore { suspend fun save(user: SessionUser); suspend fun load(): SessionUser?; suspend fun clear() }`

**Impl (androidMain):**
- `EncryptedSessionStore(context: Context) : SessionStore`
  - Tink: `AeadConfig.register()` no init estático
  - Chave: `AndroidKeysetManager` com keyset name `sprena_session_keyset`, master key URI `android-keystore://sprena_session_key`, template `AES256_GCM`
  - DataStore: `PreferencesDataStore` com nome `session_prefs`
  - Persistência: 4 preferences keys (`uid_enc`, `email_enc`, `role_enc`, `last_login_enc`) — cada uma um ByteArray cifrado (base64 para storage em Preferences)
  - Em caso de falha de decifragem (corrupção, key rotation), `load()` retorna `null` e dispara `clear()` defensivamente (sem crash)

### 3. `shared/auth/domain` — Use cases

- **`LoginUseCase`** (refator):
  - Construtor: `(authRepository: AuthRepository, sessionStore: SessionStore, clock: Clock, logger: Logger)`
  - `Clock` é interface mínima (`fun nowEpochMillis(): Long`) — injeta `SystemClock` em prod, fake em testes
  - Fluxo: valida email + senha → `authRepository.authenticate(...)` → em `Success`, monta `SessionUser` com `clock.nowEpochMillis()` e chama `sessionStore.save(...)` → retorna `AuthResult.Success`

- **`LogoutUseCase`** (novo):
  - Construtor: `(authRepository, sessionStore, logger)`
  - `signOut()` no repo + `sessionStore.clear()` + log info "logout"

- **`RequestPasswordResetUseCase`** (novo):
  - Construtor: `(authRepository, logger)`
  - Valida email com `LoginValidator.validateEmail(...)`
  - Chama `authRepository.sendPasswordReset(...)`
  - Retorna sealed result `PasswordResetResult` (`Sent | InvalidEmail | NetworkError | UnknownError`)

- **`RestoreSessionUseCase`** (novo, chamado no cold start):
  - Construtor: `(authRepository, sessionStore, clock, logger)`
  - Lógica:
    ```
    val stored = sessionStore.load()
    if (stored == null) return NotAuthenticated
    if (SessionValidator.isExpired(stored.lastLoginEpochMillis, clock.nowEpochMillis())) {
        authRepository.signOut(); sessionStore.clear(); return NotAuthenticated
    }
    if (authRepository.currentUid() != stored.uid) {
        sessionStore.clear(); return NotAuthenticated
    }
    return Authenticated(stored)
    ```
  - Retorna `sealed interface RestoreResult { Authenticated(SessionUser); NotAuthenticated }`

### 4. `composeApp/presentation` — UI

- **`LoginValidator`** (refator):
  - `validateEmail(value: String): ValidationResult` — não-blank, contém `@` e `.`, regex simples `^[^@\s]+@[^@\s]+\.[^@\s]+$`, ≤ 254 chars
  - `validatePassword(value: String): ValidationResult` — não-blank, ≥ 6 chars (mantém mínimo do Firebase Auth), sem espaços nas extremidades

- **`LoginScreen` + `LoginViewModel`**:
  - Campos: `email: String`, `password: String` (eram `username`/`password`)
  - Adiciona `TextButton("Esqueci a senha")` que dispara `LoginIntent.RequestPasswordReset` e abre dialog `ForgotPasswordDialog`
  - `LoginEffect`: adiciona `ShowPasswordResetSent`, `ShowPasswordResetError(msg)`
  - `LoginState`: adiciona `passwordResetSending: Boolean`

- **`ForgotPasswordDialog`**:
  - Modal com input de email + 2 botões (Cancelar / Enviar)
  - Dispara `LoginIntent.SubmitPasswordReset(email)` no ViewModel

- **`SettingsScreen`**:
  - Adiciona seção "Conta" no topo: texto `Logado como: $email` + texto secundário `Perfil: $role.displayName`
  - Botão `Sair` (vermelho) → dispara `SettingsIntent.Logout`
  - `SettingsViewModel` (criar se não existir): expõe `state.user: SessionUser?`, intent `Logout` que chama `LogoutUseCase` e emite `SettingsEffect.NavigateToLogin`

- **`NavGraph`**:
  - Rota inicial dinâmica: o `App`/`SprenaNavHost` chama `RestoreSessionUseCase` ANTES de definir o startDestination
  - `Authenticated` → Home; `NotAuthenticated` → Login
  - Após login bem-sucedido: nav para Home (popUpTo Login inclusivo) — comportamento já existente
  - Após logout: nav para Login (popUpTo qualquer destination inclusivo)

### Models atualizados

- `UserModel(id: String, email: String, name: String, role: UserRole)` — campo `username` renomeado para `email`. 5 call sites: `MockAuthRepository`, `LoginViewModel`, `LoginScreen`, `LoginViewModelTest`, `NavGraph`. Todos atualizados em uma mudança coordenada.
- `UserRole` mantido como está (`ADM`, `MOD`, `CLIENT`).

## Dependências novas

Em `gradle/libs.versions.toml`:
- `firebase-auth` (sem versão — vem do BOM 34.12.0 já presente)
- `datastore-preferences = "1.1.1"` — `androidx.datastore:datastore-preferences`
- `tink-android = "1.13.0"` — `com.google.crypto.tink:tink-android`

## TDD obrigatório

| Componente | Testes (mínimos) |
|---|---|
| `SessionValidator.isExpired` | TTL exato, antes do TTL, depois do TTL, lastLogin no futuro (clock skew) |
| `LoginValidator.validateEmail` | válido, sem `@`, sem `.`, blank, > 254 chars |
| `LoginValidator.validatePassword` | válido (≥ 6), 5 chars, blank, espaços extremos |
| `RestoreSessionUseCase` | sessão ausente → NotAuth, expirada → NotAuth+clear, uid divergente → NotAuth+clear, válida → Authenticated |
| `LogoutUseCase` | chama signOut e clear, loga info |
| `RequestPasswordResetUseCase` | email inválido → InvalidEmail (sem hit Firebase), email válido + sucesso → Sent, falha network → NetworkError |
| `LoginUseCase` (refator) | valida primeiro, em Success persiste SessionUser com timestamp do clock, em Error não persiste |

`FirebaseAuthRepositoryImpl` é androidMain — testes Android unit com MockK ficam para a sub-fase F2 (Clean Architecture). Aceitamos verificação manual end-to-end (login real → ler doc Firestore → DataStore tem entrada cifrada).

## Verificação manual (pré-merge)

- [ ] `./gradlew detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug ktlintCheck :shared:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug` — todos verdes
- [ ] Criar 1 user no Firebase Console (Auth) + doc `users/{uid}` no Firestore com `role=ADM`
- [ ] Login na app com esse user → vai pra Home
- [ ] Inspecionar `/data/data/br.com.sprena/files/datastore/session_prefs.preferences_pb` — bytes não-legíveis (cifrado)
- [ ] Fechar/abrir app → auto-login (não passa por Login)
- [ ] Avançar relógio do device em 25h (ou esperar) → cold start volta pra Login
- [ ] Clicar "Sair" em Settings → volta pra Login; auto-login NÃO acontece mais
- [ ] Clicar "Esqueci a senha", enviar email → email chega na caixa
- [ ] Tentar login com senha errada 6× → mensagem "Muitas tentativas..."

## Out of scope (vai pra F1.4+ ou F6)

- **F1.4:** Firestore Security Rules protegendo `users/{uid}` e demais coleções por `request.auth.uid`
- **F1.4:** Firebase App Check
- **F1.5:** Consentimento LGPD + política de privacidade + masking de CPF
- **F6:** Biometria (BiometricPrompt) como 2º fator
- **F6:** Self-registration via UI
- **F6:** Google Sign-In adicional
- **F2:** Refactor ViewModels existentes para consumir UseCases (`RoleGuardedUseCase` decorator)

## Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Key rotation do Tink invalida sessões | `load()` retorna `null` + `clear()` defensivo → user faz login de novo. Aceitável. |
| Doc `users/{uid}` ausente em produção | Login retorna "Conta não autorizada" com link para suporte (texto na UI) |
| Network down durante reset → email não chega | UX mostra "Não foi possível enviar agora. Tente novamente" + log warn |
| Clock skew (device com data errada) | `SessionValidator` valida `lastLogin <= now`; se `lastLogin > now`, trata como expirada |
| Token JWT expira antes da sessão (rare) | `RestoreSessionUseCase` checa `currentUid() != null` — se o Firebase já invalidou, `currentUid()` retorna null e voltamos pra Login |
