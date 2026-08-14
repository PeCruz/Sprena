# F1.6a — Direitos do titular sobre a própria conta (LGPD art. 18) — Plano

> **Status:** plano aprovado em 2026-08-14. Spec em
> [`docs/superpowers/specs/2026-08-14-f1-6a-direitos-do-titular-design.md`](../specs/2026-08-14-f1-6a-direitos-do-titular-design.md).

## Contexto

F1.5 (baseline LGPD) foi merged no `master` via PR #14. O próximo item do `ROADMAP.md:18-21` é
**F1.6 — Direitos do titular**, que é o único item restante que **bloqueia a publicação na Play
Store**: apps com login são obrigados a oferecer exclusão de conta dentro do app.

O escopo original conversado incluía multi-tenancy (estabelecimentos) e gestão de dados de
`sport_clients` por MOD. Esse conceito **não existe em lugar nenhum do código** — `sport_clients` é
uma coleção global plana e `users/{uid}` guarda apenas `email`, `name` e `role`. Introduzi-lo agora
atrasaria o desbloqueio da Play Store. Por decisão do mantenedor, esta fase entrega **apenas os
direitos do titular sobre a própria conta**, e multi-tenancy vira **F1.7**.

Resultado pretendido: a aba "Config" vira **"Perfil"**, onde o titular vê e edita os próprios dados,
exporta tudo em JSON, redefine a senha e exclui a conta — com a exclusão executada por uma Cloud
Function que apaga em cascata e remove o usuário do Firebase Auth.

## Decisões travadas

| # | Decisão | Justificativa |
|---|---|---|
| 1 | Escopo = **conta do próprio usuário**. Estabelecimentos e escopo do MOD → F1.7 | Destrava a Play Store primeiro |
| 2 | Exclusão via **Cloud Function callable** (Blaze já ativo) | Cascade + delete do Auth user exigem Admin SDK |
| 3 | Financeiro histórico é **anonimizado**, não apagado (art. 16 I) | Integridade contábil. Hoje anonimiza **zero** registros — financial/bar/menu são in-memory |
| 4 | Campos autodeclarados em **sidecar `user_profiles/{uid}`**; `users/{uid}` permanece `write: if false` | Ver "Por que sidecar" abaixo |
| 5 | Perfil **editável** pelo titular | Sem backfill, um perfil read-only nasceria todo "Não informado" |
| 6 | Exportação = **JSON via share sheet** do Android | Sem backend novo, sem provedor de e-mail |
| 7 | **Bump da política** para a data do merge, no mesmo commit do texto | CPF/telefone/modalidades são categoria nova de dado pessoal |
| 8 | Role `USER` (jogador) apenas **documentado**; enum inalterado | `UserRole.valueOf` aceitaria o valor e `sport_clients` tem `read: if isSignedIn()` — um `USER` leria o CPF de todos os clientes. Hoje `USER` no doc derruba o login, e essa falha é a proteção |

### Por que sidecar (decisão 4)

`SECURITY.md` §"Por que não em `users/{uid}`" já registrou, no F1.5, que as rules negam **toda**
escrita naquele doc para impedir auto-promoção de role. Abrir exceção lá reabriria a superfície que
o F1.4 fechou. O ponto decisivo é o **F1.7**: `establishmentIds` (quais estabelecimentos um MOD
gerencia) parece campo de perfil mas é **autorização** — se entrar na allowlist por reflexo, um MOD
concede a si mesmo qualquer estabelecimento, silenciosamente. Separando os documentos, o erro deixa
de ser desencorajado e passa a ser estruturalmente impossível: a palavra `role` nunca aparece na
coleção que o cliente escreve.

`role` continua **visível** no perfil de todos os papéis — é leitura de `users/{uid}`, que já é
permitida para o próprio dono. Sidecar restringe escrita, não leitura.

## Pré-requisitos

1. `git checkout master && git pull` — o `master` **local** está 24 commits atrás; `origin/master` já
   tem o merge do PR #14. Ramificar de `feature/f1-5-lgpd-baseline` produziria um PR contendo todo o
   F1.5.
2. Blaze ativo (confirmado). Configurar limpeza do Artifact Registry
   (`firebase functions:artifacts:setpolicy`) na mesma sessão do primeiro deploy.

---

## 1. Modelo de dados

**`users/{uid}`** — inalterado. `role`, `name`, `email`, provisionado via Console/Admin SDK.

**`user_profiles/{uid}`** — novo, escrito pelo próprio titular. Documento inteiro opcional; **não há
backfill**. Ausente ⇒ `Result.success(null)` e a UI mostra "Não informado".

| Campo | Tipo | Ausente ⇒ |
|---|---|---|
| `apelido` | string (≤60) | "Não informado" |
| `cpf` | string, só dígitos (≤14) | "Não informado" |
| `phone` | string, só dígitos (≤20) | "Não informado" |
| `modalities` | array\<string\> (≤10), valores do enum `SportModality` | "Nenhuma informada" |
| `updatedAt` | timestamp | obrigatório; `== request.time` na rule |

**`account_deletions/{uid}`** — auditoria, escrita só pelo Admin SDK, **sem PII**.

## 2. `firestore.rules` (peça de maior risco)

`users/{uid}` **não muda**. Dois blocos novos:

```
match /user_profiles/{uid} {
  function isOwner() { return isSignedIn() && request.auth.uid == uid; }
  function onlyKnownKeys() {
    return request.resource.data.keys().hasOnly(
      ['apelido', 'cpf', 'phone', 'modalities', 'updatedAt']);
  }
  function validPayload() { /* tipo + tamanho por campo; updatedAt == request.time */ }

  allow read: if isOwner();
  allow create, update: if isOwner() && onlyKnownKeys() && validPayload();
  allow delete: if false;   // exclusão passa pela Cloud Function
}

match /account_deletions/{uid} { allow read, write: if false; }
```

`keys().hasOnly(...)` e não `diff().affectedKeys().hasOnly(...)`: valida o estado final inteiro e
funciona igual em `create` (onde `resource` é null) e em `update`. `create` é permitido aqui — ao
contrário de `users/{uid}` — porque o doc nasce no primeiro save do titular.

**Novos casos em `tools/firestore-rules-tests/rules.test.mjs`** (~14, numerados a partir de 29): lê
próprio perfil inexistente (sucesso, não falha); nega ler perfil alheio; cria e atualiza o próprio;
nega criar em nome de outro uid; nega campo desconhecido (`role`, `isAdmin`); nega `updatedAt`
forjado e ausente; nega tipos errados; nega `modalities` > 10; nega delete; nega sem auth; **prova
que `updateDoc(users/CLIENT_UID, {role:'ADM'})` continua falhando**; nega `account_deletions` para
ADM e para o dono.

⚠️ A contagem "30 casos" aparece em `SECURITY.md`, no runbook F.2 e na tabela F.5 — atualizar os três.

## 3. Módulo `shared/account`

```
shared/src/commonMain/.../shared/account/
├── domain/model/       UserProfile, ProfilePatch, ProfileResult,
│                       AccountDeletionResult, DataExportPayload
├── domain/repository/  UserProfileRepository, AccountDeletionRepository
├── domain/usecase/     GetMyProfileUseCase, SaveMyProfileUseCase,
│                       ExportMyDataUseCase, DeleteMyAccountUseCase
└── di/AccountModule.kt
shared/src/androidMain/.../shared/account/data/
├── dto/UserProfileDto.kt
└── repository/  FirestoreUserProfileRepository, FunctionsAccountDeletionRepository,
                 AccountErrorMapper.kt   ← espelha AuthErrorMapper.kt
```

- `UserProfileRepository.current(uid)` faz as **duas** leituras (`users` + `user_profiles`)
  internamente — o use case não conhece coleção. `save(uid, patch)` escreve só no sidecar.
- `SportModality` é **reusado** de `shared.sportclient.domain.validation`, não duplicado (dependência
  entre pacotes do mesmo módulo Gradle; documentar em KDoc).
- `DeleteMyAccountUseCase`: **chama o callable primeiro**, e só em `success` faz
  `authRepository.signOut()` + `sessionStore.clear()`. Inverter derruba o ID token antes de o backend
  validá-lo. Em falha, a sessão fica intacta e o usuário pode tentar de novo.
- `RequestPasswordResetUseCase` já existe — **reusar**, não recriar.
- Registrar `accountModule()` em `SharedModules.kt` (o KDoc enumera módulos por número — atualizar).

## 4. `PhoneMasker`

Novo `shared/src/commonMain/.../core/privacy/PhoneMasker.kt`, espelhando `CpfMasker.kt` linha a linha.
Não existe masker de telefone para UI hoje — só `PiiMasker.phone`, que é **para log** (DDD + 2
últimos). O KDoc precisa avisar da diferença, como o `CpfMasker` já faz.

- `maskPhone("11987654321")` → `"(11) *****-4321"`; `maskPhone("1133334444")` → `"(11) ****-4444"`
- Qualquer coisa fora de 10–11 dígitos → `"(**) *****-****"` (máscara total, nunca dígito parcial)
- `formatPhone` → `"(11) 98765-4321"`; entrada inválida volta crua

`PhoneMaskerTest.kt` com ~11 casos, incluindo `+55` (13 dígitos → máscara total; DDI não é suportado).

## 5. Cloud Function

```
functions/{package.json, tsconfig.json, eslint.config.mjs,
           src/{index.ts, deleteMyAccount.ts, anonymizeFinancial.ts, firestoreDelete.ts},
           test/deleteMyAccount.test.mts}
```

`firebase.json` ganha o bloco `functions` (com `predeploy: npm run build`) e o emulador **auth**
(porta 9099) — sem ele não dá para testar a exclusão do usuário do Auth.

**Callable** `deleteMyAccount`, `onCall` v2, Node 22, `enforceAppCheck: true`, região constante nos
**dois lados** (`FUNCTIONS_REGION` no `PlatformModule.android.kt`). Divergência de região devolve
`NOT_FOUND`, indistinguível de "não deployada".

- **Sem payload.** O uid vem só de `request.auth.uid`. Qualquer chave em `request.data` →
  `invalid-argument` (negar explicitamente documenta que a escalada foi considerada).

**Ordem de exclusão** (o *porquê* de cada posição vai no SECURITY.md):
1. lê `users/{uid}` e `user_consents/{uid}` — precisa dos dados antes de apagar
2. `anonymizeFinancial(uid, profile)` — **antes**, porque anonimizar exige a identidade que os passos
   seguintes destroem. Hoje é no-op que retorna `0` e loga `reason: 'financial-not-in-firestore'`.
   É o *home* óbvio para quando F2 migrar financial/bar/menu
3. `user_consents/{uid}/history/*` — recursivo, lotes de 500 (`BulkWriter`), **antes do pai**
4. `user_consents/{uid}` → 5. `user_profiles/{uid}` → 6. `users/{uid}`
7. `account_deletions/{uid}` — auditoria sem PII, com `financialAnonymized`
8. `admin.auth().deleteUser(uid)` — **por último**: assim que o Auth user some, o token morre e
   qualquer retry vira `unauthenticated`

**Idempotência:** delete de doc inexistente é no-op; `auth/user-not-found` é tratado como sucesso.
Torna seguro re-executar sobre um uid órfão (procedimento H.7).

**Testes** (`node --test` contra emulador, projeto `demo-sprena`, zero credencial — mesma postura da
suíte de rules): exclusão completa; conta mínima sem sidecar/history; sem auth → `unauthenticated`;
**`data.uid` de outro usuário → `invalid-argument` e o outro continua existindo**; segunda chamada
idempotente; `history` > 500 docs; auditoria sem PII.
Limite conhecido: **o emulador não aplica `enforceAppCheck`** — só se verifica em device.

**CI:** terceiro job `cloud-functions` espelhando o de rules (Node 22 + JDK 21, `npm ci`, `lint`,
`build`, `test:emulator`). ~2–3 min por PR; um `tsc` quebrado descoberto no `firebase deploy` é uma
release parada.

**Cliente:** `libs.versions.toml` ganha `firebase-functions` (sem `version.ref` — a BOM resolve) e
`androidx-core-ktx` (para `FileProvider`, hoje só transitivo via `activity-compose`). Nenhuma
`FirebaseFunctionsException` cruza para `commonMain` — `AccountErrorMapper` traduz para PT-BR
(`UNAUTHENTICATED`, `PERMISSION_DENIED`, `NOT_FOUND`, `UNAVAILABLE`, `INTERNAL`).

## 6. Exportação

`ExportMyDataUseCase` monta o JSON com `kotlinx.serialization`, chaves em PT-BR (o destinatário é o
titular): `conta` (id, email, papel), `perfil` (nome, apelido, **CPF e telefone completos** —
portabilidade com dado mascarado não é portabilidade), `consentimento` (versão vigente + histórico),
e `observacoes` explicando o que não consta e por quê. `exportadoEm` usa o `Clock` injetado.

**Nunca pode entrar:** qualquer doc de `sport_clients` (dados de **terceiros** — exportá-los pela
porta de "meus dados" é vazamento com aparência de direito); dados de outros usuários; token do
Firebase, keyset do Tink, conteúdo do `session_prefs`, token do App Check.

O histórico de consentimento exige `ConsentRepository.history(uid)` novo (as rules do F1.5 já
permitem ler o próprio `history`). **Escopo destacável** se o branch crescer.

**Costura KMP** — espelha `rememberFilePicker`, o precedente do repo para I/O de plataforma dentro de
Compose:

```kotlin
// composeApp/commonMain/.../core/platform/DataExportSharer.kt
@Composable expect fun rememberDataExportSharer(): (ExportPayload) -> Unit
```

A actual Android escreve em `cacheDir/exports/` (limpando o anterior, que contém CPF em claro),
gera URI via `FileProvider` e dispara `Intent.createChooser(ACTION_SEND)`. Fluxo MVI intacto: use
case monta o JSON (testável em `commonTest`), ViewModel emite `ProfileEffect.ShareExport`, a Screen
coleta e chama o sharer.

**Manifest:** `<provider>` do `FileProvider` com `authorities="${applicationId}.fileprovider"`,
`exported="false"`, mais `res/xml/file_paths.xml` (`<cache-path name="exports" path="exports/"/>`).
Sem `<queries>` e sem permissão nova. Diálogo de confirmação antes de exportar avisando que o
arquivo contém dados pessoais completos, inclusive CPF.

## 7. Apresentação — `presentation/profile/`

**Nova feature, mantendo `settings`.** "A aba Config vira Perfil" é navegação e rótulo, não fusão:
`SettingsScreen` continua dona de Cardápio, Categorias e Política — configuração do *operador*, não
"meus dados". Efeito colateral bom: hoje `SettingsScreen` é renderizada **duas vezes** (`Routes.SETTINGS`
~linha 463 e `BottomTab.SETTINGS` ~linha 828, com dois `SettingsNavigation` a manter em sincronia).
Com a aba passando a renderizar `ProfileScreen`, a duplicação **some** — a resposta para o
double-render é não criar o segundo.

Arquivos: `ProfileScreen/ViewModel/State/Intent/Effect/Navigation.kt`.

- **State** com `displayCpf` / `displayPhone` computados (mesmo padrão de `SportClientState.displayCpf`
  — decisão de exibição no State, nunca no Composable), `isEditing`, `canConfirmDelete`.
  Aqui `canReveal` é **sempre true**: a autorização é *propriedade*, não role — a máscara existe
  contra ombro/gravação de tela, não contra o dono. Comentar, senão parece inconsistência com
  `SportClientState.canRevealCpf`.
- **Effect**: `ShareExport(fileName, json)`, `ShowMessage(msg)`, `NavigateToLogin` (serve logout **e**
  pós-exclusão — mesmo destino e mesma semântica de back stack, `popUpTo(0)`). **Sem snackbar de
  sucesso após exclusão** — a tela está saindo da árvore; o Login já é a confirmação.
- **ViewModel** (5 deps: getProfile, saveProfile, exportMyData, deleteMyAccount,
  requestPasswordReset, logout — conferir `constructorThreshold` do detekt, que é 8). **Sem
  `SessionStore`**: os use cases resolvem a sessão internamente.
- **Diálogo de exclusão** (`AlertDialog` M3): irreversibilidade; o que é apagado; o que é **mantido**
  (financeiro anonimizado, art. 16 I); o que **não** é afetado (os clientes cadastrados continuam —
  são do estabelecimento); `OutlinedTextField` exigindo digitar **`EXCLUIR`**; botão em
  `colorScheme.error`. Em falha, `deleteError` renderiza **dentro do diálogo, que permanece aberto**.
- **NavGraph**: `BottomTab.SETTINGS` → `BottomTab.PROFILE`, ícone `Person`, rótulo "Perfil" (toca
  `BottomNavViewModel` + teste — **commit próprio**). Configurações vira uma linha dentro do Perfil
  apontando para `Routes.SETTINGS`, que já existe e já tem seta de voltar.
  ⚠️ Conferir `detekt LongMethod` / `detekt-baseline.xml` no `NavGraph.kt` (946 linhas).

**Testes** (`ProfileViewModelTest`, fakes à mão + `MainDispatcherEnv` + Turbine, sem MockK): carrega
no init; falha vira erro com retry, nunca perfil parcial; CPF e telefone começam mascarados; campo
ausente vira "Não informado"; salvar perfil persiste e recarrega; reset de senha usa o email do
perfil; export emite `ShareExport`; **export não inclui dados de clientes cadastrados** (o teste que
trava a regressão de vazamento); botão excluir só habilita após digitar `EXCLUIR`; sucesso limpa
sessão e navega; **falha mantém o diálogo aberto e a sessão intacta**; exclusão não chama `signOut`
antes do callable.

## 8. Exclusão × gate de consentimento (furo real a corrigir)

Se o processo morrer entre o sucesso da CF e o `sessionStore.clear()`: no cold start a sessão não
expirou (TTL 24h), `currentUid()` **ainda devolve o uid** (o SDK mantém o usuário local até refrescar
o token), o gate chama `checkConsent(uid)`, o ID token em cache vale até 1h, o doc não existe →
`Required(MISSING)` → **o usuário cai no consentimento de uma conta excluída e o botão "Aceitar"
recria `user_consents/{uid}`**. Não trava (o "Sair" do gate é a saída), mas é ressurreição parcial —
e é exatamente o roteiro que um revisor da Play executa: *excluir conta, reabrir o app*.

**Correção:** `AuthRepository.refreshToken(): Result<Unit>` (`getIdToken(true)`), consumido por
`RestoreSessionUseCase` após a checagem de uid. `FirebaseAuthInvalidUserException` → `signOut()` +
`clear()` → `NotAuthenticated`. **Qualquer outra exceção (rede) → mantém a sessão** — tratar falha de
rede como "não autenticado" deslogaria todo mundo que abrir o app offline.

Dois testes obrigatórios em `RestoreSessionUseCaseTest`: `sessao invalidada quando o usuario do Auth
nao existe mais` e `sessao preservada quando o refresh do token falha por rede`.

Bônus: cobre também o operador excluir um usuário pelo Console (hoje o app segue "logado" até o TTL).

**Estado degradado conhecido** (documentar): se o passo 8 da CF falhar depois do 6, o login bate em
`doc.exists() == false` → "Conta não autorizada. Contate o administrador." Sem crash, sem vazamento,
mas o Auth user órfão precisa de limpeza manual (H.7). `account_deletions` é o que permite detectar.

## 9. Documentação

- **`SECURITY.md`** — nova `## F1.6a` após F1.5, no estilo estabelecido: base legal (art. 18 II/V/VI,
  art. 16 I); o que a tela expõe e por que `canReveal` é sempre true aqui; por que a exclusão passa
  pela CF; os 8 passos com o porquê da ordem; `account_deletions`; **anonimização financeira com a
  frase explícita "hoje anonimiza zero registros porque financial/bar/menu são in-memory"** — sem ela
  o documento afirma um controle inexistente; App Check no callable; região; exportação (o que entra,
  o que não entra e por quê); estado degradado; `### Fora de escopo (F1.7)`; checklist de verificação
  manual pré-merge.
- **`docs/ops/firebase-users-runbook.md`** — nova `## Parte H — Cloud Functions: exclusão de conta`
  (H.1 Blaze + Artifact Registry, H.2 build, H.3 emulador, H.4 deploy, H.5 conferir região/App Check,
  H.6 validar em device, H.7 re-executar para uid órfão, H.8 **ordem de release: CF antes do APK**,
  troubleshooting). Parte B ganha os campos novos como opcionais. Corrigir "pass 30 / fail 0" em F.2
  e F.5.
- **`privacy-policy.md`** (composeResources) — item 2 passa a incluir nome, apelido, **CPF**, telefone
  e modalidades; item 6 (retenção) descreve exclusão in-app + anonimização; item 8 (direitos) passa a
  dizer que acesso/portabilidade/eliminação são exercíveis **dentro do app**. Bump da versão para a
  data do merge, no texto **e** em `PrivacyPolicy.VERSION`, **no mesmo commit** (nunca podem divergir).
- **`docs/legal/privacy-policy.md`** — drive-by: o passo 4 ainda diz `history/{policyVersion}`, e o id
  é automático desde `cb08a8e`.
- **`ROADMAP.md`** — F1.6a ✅; nova linha F1.7 (multi-tenancy + role `USER` + matriz de permissões);
  nota de ordem de release da CF.
- **Matriz de permissões (documentação, não implementação)**, em `SECURITY.md § Fora de escopo (F1.7)`:

| Papel | Hoje | Alvo (F1.7) |
|---|---|---|
| `ADM` | tudo | tudo, em todos os estabelecimentos |
| `MOD` | igual a ADM nas rules (`isStaff()`) | **seu(s) estabelecimento(s)**: financeiro, cardápio, categoria |
| `CLIENT` ("Funcionário") | lê `sport_clients`, sem escrita | comandas + consulta de clientes do seu estabelecimento |
| `USER` (jogador) | **não existe** — derruba o login | só consulta de eventos, própria comanda, próprio perfil |

## 10. Commits

Branch `feature/f1-6a-direitos-do-titular`, a partir do `master` atualizado. Convenção do repo:
`tipo(escopo): descricao`, PT-BR, sem acentos no assunto, teste antes do código.

1. `docs(spec)` design de F1.6a → 2. `docs(plan)` plano →
3. `feat(privacy)` PhoneMasker → 4. `feat(account)` domínio do perfil →
5. `feat(rules)` perfil do titular em colecao propria → 6. `test(rules)` ownership e allowlist →
7. `feat(account)` repositorio Firestore do perfil → 8. `feat(account)` exportacao em JSON →
9. `feat(account)` contrato de exclusao com erros em PT-BR →
10. `chore(functions)` runtime TypeScript → 11. `test(functions)` casos no emulador →
12. `feat(functions)` callable deleteMyAccount → 13. `feat(functions)` hook de anonimizacao (no-op) →
14. `feat(account)` chamada do callable no androidMain →
15. `feat(profile)` tela Meus dados com CPF e telefone mascarados →
16. `feat(profile)` edicao dos campos autodeclarados →
17. `feat(profile)` exportacao pelo share sheet com FileProvider →
18. `feat(profile)` redefinicao de senha reutilizando o fluxo de email →
19. `feat(profile)` exclusao de conta com confirmacao digitada →
20. `refactor(navigation)` aba Config vira Perfil →
21. `fix(auth)` invalida a sessao quando o usuario do Auth nao existe mais →
22. `ci(functions)` job de build, lint e teste →
23. `docs(legal)` politica com direitos do titular e novos campos (texto + `VERSION` juntos) →
24. `docs(security,ops,roadmap)` F1.6a documentada e Parte H

PR único `F1.6a — Direitos do titular (LGPD art. 18)`, base `master`.

## Verificação

**Automatizada** (tudo verde antes do PR):
```
./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug \
          :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest
cd tools/firestore-rules-tests && npm run test:emulator   # novo total, fail 0
cd functions && npm run test:emulator                     # fail 0
```

**Ordem de release (bloqueante):** rules → CF → validar em device com o build ainda **não
distribuído** → só então publicar o APK. Mesma lição do F1.5. Diferença: rules na ordem invertida
travam todo mundo; CF na ordem invertida degrada só o botão de exclusão — mas é justamente o botão
que a review da Play vai testar.

**Manual em device** (vira a checklist do SECURITY.md): abrir Perfil e ver role/nome/email; preencher
apelido, CPF, telefone e modalidades e reabrir o app (persistiu); CPF e telefone começam mascarados e
revelam no toque; exportar, abrir o JSON e conferir que **não há nada de `sport_clients`**; redefinir
senha e receber o e-mail; excluir uma conta de teste e conferir no Console que `users`,
`user_profiles`, `user_consents` + `history` sumiram e `account_deletions/{uid}` existe sem PII;
**reabrir o app depois de excluir → cai no Login, não no gate de consentimento**; abrir o app offline
com sessão válida **não** desloga.

## Fora de escopo (F1.7)

Estabelecimentos (multi-tenancy) e o escopo do MOD por estabelecimento; role `USER`; enforcement da
matriz de permissões por aba. A seção "Estabelecimentos" **não é renderizada** na tela de perfil —
uma linha "em breve" numa tela cujo propósito é "estes são os dados que temos sobre você" anuncia
dado que o app não sabe produzir, e lê como produto inacabado para um revisor da Play.
