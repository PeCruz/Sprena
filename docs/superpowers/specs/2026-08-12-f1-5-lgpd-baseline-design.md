# F1.5 — Baseline LGPD: consentimento, política de privacidade e masking de CPF — Design

> **Status:** spec aprovada via brainstorming em 2026-08-12. Próximo passo: gerar plano via `writing-plans` skill.

## Contexto

F1.1–F1.4b entregaram o endurecimento técnico: build hardening, logging com sanitização de PII,
Firebase Auth real com sessão cifrada, Firestore Security Rules testadas no emulador e App Check.
O que falta em F1 é a camada de **tratamento de dados pessoais**: o app coleta CPF e telefone —
inclusive **de terceiros** (clientes cadastrados por um operador) — sem nenhum aceite de política e
exibindo o CPF completo na UI para qualquer usuário autenticado.

F1.5 fecha esse baseline com três entregas: gate de consentimento no cold start, política de
privacidade versionada e embarcada, e masking de CPF na exibição.

## Decisões já tomadas (brainstorming)

| Decisão | Valor | Por quê |
|---|---|---|
| Titular coberto | Usuário do app **e** cliente cadastrado | O app processa CPF de terceiros; cobrir só o operador deixa a maior exposição descoberta |
| Onde vive a política | Markdown embarcado em `composeResources` | Funciona offline, versionada em git, o aceite amarra a versão exata do texto lido |
| Regra de masking | Mascarado por padrão; revelar só ADM/MOD | A role já é resolvida no login; equilibra minimização com a operação real |
| Storage do aceite | Coleção `user_consents/{uid}` | Rules de F1.4 bloqueiam escrita do app em `users/{uid}` (há teste garantindo) |
| Gate | Rota própria no NavGraph | Isola consentimento de auth; não altera contratos de `LoginUseCase`/`RestoreSessionUseCase` |
| Direitos do titular (art. 18) | **Fora de escopo** → F1.6 | Exclusão/exportação exigem decidir retenção de dados financeiros e provavelmente Cloud Function |
| Comportamento em falha de leitura | Fail-closed | Nunca entra na Home por falha de rede nem grava aceite implícito |

## Arquitetura

Quatro unidades com fronteiras claras.

### 1. `shared/privacy` — domínio de consentimento

Segue o shape de `shared/auth`.

**commonMain** (`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/`):

- `domain/model/ConsentRecord.kt`
  ```kotlin
  data class ConsentRecord(
      val uid: String,
      val policyVersion: String,
      val acceptedAtEpochMillis: Long,
  )
  ```
- `domain/model/ConsentStatus.kt`
  ```kotlin
  sealed interface ConsentStatus {
      data object Granted : ConsentStatus
      data class Required(val reason: Reason) : ConsentStatus
      data class Unavailable(val message: String) : ConsentStatus

      enum class Reason { MISSING, OUTDATED }
  }
  ```
- `domain/model/PrivacyPolicy.kt` — `object PrivacyPolicy { const val VERSION = "2026-08-12" }`.
  A versão é a data de publicação do texto. Mudou o texto → muda a constante → todos reaceitam.
- `domain/repository/ConsentRepository.kt`
  ```kotlin
  interface ConsentRepository {
      suspend fun current(uid: String): Result<ConsentRecord?>   // null = nunca aceitou
      suspend fun accept(uid: String, policyVersion: String): Result<Unit>
  }
  ```
  `Result<ConsentRecord?>` distingue as três situações que o gate precisa separar: aceitou,
  não aceitou, não deu para saber. O campo `appVersion` do doc é preenchido pela implementação
  Android a partir do `BuildConfig` — não polui o contrato de commonMain.
- `domain/usecase/CheckConsentUseCase.kt` — `suspend operator fun invoke(uid: String): ConsentStatus`.
  Mapeia: `null` → `Required(Missing)`; versão diferente da atual → `Required(Outdated)`;
  igual → `Granted`; `Result.failure` → `Unavailable(mensagem)`. Loga via `Logger` (F1.2).
- `domain/usecase/AcceptConsentUseCase.kt` — `suspend operator fun invoke(uid: String): Result<Unit>`.
  Delega ao repositório passando `PrivacyPolicy.VERSION`.
- `di/PrivacyModule.kt` — Koin: os dois use cases. O binding do repositório fica em
  `composeApp/androidMain` junto dos outros bindings Firestore, seguindo o padrão do projeto.

**androidMain** (`shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/`):

- `data/dto/ConsentDto.kt` — mapeia de/para o doc Firestore.
- `data/repository/FirestoreConsentRepository.kt` — implementa `ConsentRepository`:
  - `current(uid)`: `get` em `user_consents/{uid}`; doc ausente → `Result.success(null)`;
    exceção → `Result.failure`.
  - `accept(...)`: `WriteBatch` com `set` no doc corrente e `set` em
    `user_consents/{uid}/history/{policyVersion}`.

### 2. Modelo de dados no Firestore

```
user_consents/{uid}                          { uid, policyVersion, acceptedAt, appVersion }
user_consents/{uid}/history/{policyVersion}  { policyVersion, acceptedAt }
```

O id do doc é o `uid` **de propósito**: a regra de leitura é baseada no path (`request.auth.uid == uid`),
não em `resource.data`. Uma regra que lesse `resource.data.uid` daria evaluation error num doc
inexistente e o app não conseguiria distinguir "não aceitou" de "sem permissão".

A subcoleção `history` é append-only e é o que sustenta o ônus da prova do consentimento
(LGPD art. 8 §1) quando a política ganhar uma versão nova: o doc corrente é sobrescrito, o
histórico não.

Regras a adicionar em `firestore.rules`:

```
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
    allow read:   if isSignedIn() && request.auth.uid == uid;
    allow create: if isSignedIn()
      && request.auth.uid == uid
      && request.resource.data.policyVersion == policyVersion
      && request.resource.data.acceptedAt == request.time;
    allow update, delete: if false;
  }
}
```

O bloco entra **antes** do `match /{document=**}` de default-deny.

### 3. `shared/core/privacy/CpfMasker.kt` — masking

Função pura em commonMain:

```kotlin
fun maskCpf(digits: String): String
```

- Entrada = string de dígitos crus (é como o CPF já trafega no state hoje).
- 11 dígitos → `***.***.789-00` (mantém os 3 últimos dígitos do corpo + os 2 do DV).
- Qualquer outro tamanho, ou entrada com não-dígitos que não normalize para 11 → `***.***.***-**`.
  Entrada malformada **nunca** vaza dígito parcial.

### 4. Presentation

**`composeApp/src/commonMain/.../presentation/consent/`** — MVI padrão do projeto:

- `ConsentState` — `policyMarkdown: String`, `isLoading`, `isAccepting`, `hasReadCheckbox: Boolean`,
  `error: String?`.
- `ConsentIntent` — `ToggleRead`, `Accept`, `Retry`.
- `ConsentEffect` — `NavigateHome`.
- `ConsentViewModel` — injeta `AcceptConsentUseCase` e `SessionStore` (de onde tira o uid); o botão
  "Aceito" só habilita com o checkbox marcado; erro de gravação mantém a tela com mensagem e não
  navega.
- `ConsentScreen` — renderiza o texto da política com scroll, o checkbox e o botão.

**`composeApp/src/commonMain/.../presentation/privacy/PrivacyPolicyScreen.kt`** — mesma leitura do
markdown, sem gate; alcançável pelo Settings a qualquer momento.

**Texto da política**: `composeApp/src/commonMain/composeResources/files/privacy-policy.md`,
lido via `Res.readBytes("files/privacy-policy.md")` (`compose.components.resources` já é dependência
do módulo). É a **fonte única** — `docs/legal/privacy-policy.md` é só um ponteiro para esse arquivo,
com a instrução de publicá-lo como URL pública no release (exigência do listing da Play Store).

**Gate no `NavGraph`**: hoje o start destination sai de `RestoreSessionUseCase`. Passa a sair de
`RestoreSessionUseCase` + `CheckConsentUseCase`:

```
restore → NotAuthenticated                       → Routes.LOGIN
        → Authenticated(session)
              → CheckConsent(uid) = Granted      → Routes.HOME/...
              → Required | Unavailable           → Routes.CONSENT
```

No caminho de login bem-sucedido (`onNavigateHome`), a mesma checagem roda antes de navegar.
`ConsentEffect.NavigateHome` faz `popUpTo(Routes.CONSENT) { inclusive = true }`.

`Unavailable` cai na tela de consentimento com o erro visível e um botão "tentar de novo" — é o
comportamento fail-closed: sem confirmação de aceite, não se entra na Home.

**Masking na UI**:

Levantamento do estado atual: o **único** lugar que exibe CPF em modo leitura é
`ClientDetailSheet.kt:135` (`"CPF: ${state.clientCpf}"`). Em `sportclient` o CPF só aparece dentro de
`OutlinedTextField` de cadastro/edição, que já usa `CpfMaskTransformation` como máscara de formato.

- `ClientDetailState` ganha `cpfMasked: String`, `isCpfRevealed: Boolean`, `canRevealCpf: Boolean`
  (o `clientCpf` cru continua no state porque a edição o consome — ver `ClientDetailViewModel:288`).
- `ClientDetailViewModel` injeta `SessionStore` (já existe em `shared/auth`) e resolve
  `canRevealCpf = role in setOf(UserRole.ADM, UserRole.MOD)` no `init` — a decisão de role fica no
  ViewModel, nunca no Composable (restrição 1 do CLAUDE.md), e não passa por nav args.
- Intent nova `ToggleCpfReveal`, ignorada quando `canRevealCpf` é `false`.
- **Exceção deliberada**: os campos de edição (`SportClientEditScreen`, `SportClientScreen`,
  `AddClientDialog`) seguem mostrando o CPF completo — as rules já exigem staff para escrever em
  `sport_clients`, e editar um campo mascarado não funciona.

## Tratamento de erros

| Situação | Comportamento |
|---|---|
| Leitura de `user_consents` falha (offline, App Check) | `Unavailable` → tela de consentimento com erro + "tentar de novo". Nunca entra na Home |
| Gravação do aceite falha | `state.error` preenchido, botão reabilitado, sem navegação |
| `uid` ausente ao montar a tela de consentimento | Volta para `Routes.LOGIN` (estado inconsistente) |
| Markdown da política falha ao carregar | Erro na tela com retry; o aceite fica desabilitado — não se aceita o que não se leu |

Todos os erros passam pelo `Logger` de F1.2, sem PII no corpo da mensagem.

## Testes (TDD — teste antes do código)

**`shared/commonTest`:**
- `CpfMaskerTest` — 11 dígitos; vazio; curto; string já mascarada; com pontuação; com letras.
- `CheckConsentUseCaseTest` — fake repository: `null` → `Required(Missing)`; versão antiga →
  `Required(Outdated)`; versão atual → `Granted`; `failure` → `Unavailable`.
- `AcceptConsentUseCaseTest` — repassa `PrivacyPolicy.VERSION`; propaga falha.

**`composeApp/commonTest`** (Turbine):
- `ConsentViewModelTest` — botão desabilitado sem checkbox; `Accept` com sucesso emite
  `NavigateHome`; falha preenche `error` e não emite efeito.
- `ClientDetailViewModelTest` — CPF mascarado por padrão; `ToggleCpfReveal` ignorado para CLIENT;
  revela para ADM e para MOD.

**`tools/firestore-rules-tests/rules.test.mjs`** — 6 casos novos:
1. usuário lê o próprio `user_consents/{uid}`
2. nega ler o consent de outro uid
3. cria o próprio consent com payload válido
4. nega criar consent em nome de outro uid
5. nega delete do consent
6. nega update em `history/{policyVersion}`

## Documentação a atualizar

- `SECURITY.md` — seção F1.5: base legal, o que é coletado, onde o aceite é gravado, regra de masking.
- `ARCHITECTURE.md` — módulo `shared/privacy` e o gate no NavGraph.
- `ROADMAP.md` — F1.5 ✅; **F1.6 nova**: direitos do titular (acesso, exportação, exclusão), com a
  exigência de exclusão de conta in-app da Play Store registrada como bloqueio de publicação.
- `docs/legal/privacy-policy.md` — ponteiro para o arquivo em `composeResources` + instrução de
  publicação como URL pública no release.

## Fora de escopo

- Direitos do titular do art. 18 (F1.6).
- Consent Mode v2 para Analytics (F5 — o app ainda não tem Analytics).
- Criptografia ou hash de CPF em repouso: as Firestore Rules de F1.4 já restringem leitura a
  autenticados e escrita a staff; hash quebraria busca e edição, e sem backend não há onde guardar
  a chave com segurança melhor do que o Firestore já oferece.
- Retenção/expurgo automático de dados de clientes inativos.
