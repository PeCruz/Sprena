# F1.6a — Direitos do titular sobre a própria conta (LGPD art. 18) — Design

> **Status:** spec aprovada via brainstorming em 2026-08-14. Plano de execução em
> [`docs/superpowers/plans/2026-08-14-f1-6a-direitos-do-titular.md`](../plans/2026-08-14-f1-6a-direitos-do-titular.md).

## Contexto

F1.5 fechou o baseline LGPD: gate de consentimento, política versionada e masking de CPF. O que
sobrou de F1 é o item que o próprio spec de F1.5 empurrou para frente — os **direitos do titular**
(art. 18). Esse é o último item que **bloqueia a publicação na Play Store**: apps com login são
obrigados a oferecer exclusão de conta *dentro do app*, e hoje o Sprena não tem nem tela de perfil.

O escopo conversado inicialmente incluía multi-tenancy (estabelecimentos) e gestão de dados de
`sport_clients` por moderador. Esse conceito **não existe em lugar nenhum do código**:
`sport_clients` é uma coleção global plana e `users/{uid}` guarda apenas `email`, `name` e `role`.
Introduzi-lo aqui atrasaria o desbloqueio da Play Store por uma refatoração que atinge rules,
repositórios e todos os ViewModels. Por isso a fase foi partida:

- **F1.6a (esta)** — direitos do titular sobre a **própria conta**.
- **F1.7** — multi-tenancy, role `USER` e matriz de permissões por aba.

## Decisões já tomadas (brainstorming)

| Decisão | Valor | Por quê |
|---|---|---|
| Titular coberto | **Apenas o usuário do app**, sobre a própria conta | Destrava a Play Store primeiro; dados de `sport_clients` são de terceiros e seguem sob o operador |
| Direitos entregues | Acesso (art. 18 II), portabilidade (V), eliminação (VI) | Correção (III) segue por requisição ao controlador, como a política já promete |
| Motor da exclusão | **Cloud Function callable** | Cascade delete e remoção do usuário do Auth exigem Admin SDK; o cliente não tem como fazer |
| Financeiro histórico | **Anonimizado**, não apagado (art. 16 I) | Integridade contábil. Hoje anonimiza **zero** registros — `financial`/`bar`/`menu` são in-memory |
| Onde vivem os campos autodeclarados | Sidecar **`user_profiles/{uid}`** | Ver ADR abaixo |
| Perfil editável | **Sim** | Sem backfill, um perfil read-only nasceria todo "Não informado" |
| Exportação | JSON via **share sheet** do Android | Sem backend novo, sem provedor de e-mail; já satisfaz "formato legível" |
| Versão da política | **Bump** para a data do merge | CPF/telefone/modalidades são categoria nova de dado pessoal |
| Role `USER` (jogador) | **Documentado, não implementado** | Ver ADR abaixo |

### ADR — por que sidecar `user_profiles/{uid}` e não estender `users/{uid}`

O `SECURITY.md`, na seção "Por que não em `users/{uid}`", já registrou em F1.5 que as rules de F1.4
negam **toda** escrita naquele documento — inclusive do próprio dono — para impedir auto-promoção de
role, e que `user_consents` virou coleção própria exatamente para não tocar nessa garantia. Guardar
os campos autodeclarados em `users/{uid}` faria justamente o que aquele parágrafo diz para não
fazer.

O contra-argumento ("uma allowlist `hasOnly()` resolve") é verdadeiro hoje. O ponto decisivo é
**F1.7**: será preciso guardar quais estabelecimentos cada MOD gerencia, provavelmente um
`establishmentIds` em `users/{uid}`. Esse campo *parece* dado de perfil, mas é **autorização** — se
entrar na allowlist por reflexo, um moderador concede a si mesmo qualquer estabelecimento, sem
quebrar nenhum teste.

Separando os documentos, dado de autorização (`role`, futuramente `establishmentIds`) e dado
autodeclarado (`apelido`, `cpf`, `phone`, `modalities`) ficam fisicamente apartados: a palavra `role`
nunca aparece na coleção que o cliente escreve, então nenhuma allowlist pode ser esquecida. Custo:
uma leitura extra ao abrir o perfil e um delete extra na Cloud Function.

`role` continua **visível** no perfil de todos os papéis — leitura de `users/{uid}` já é permitida
para o próprio dono. O sidecar restringe escrita, não leitura.

### ADR — por que não adicionar `UserRole.USER` agora

`FirebaseAuthRepositoryImpl` resolve a role com `UserRole.valueOf(it.uppercase())`. No momento em que
`USER` for uma constante válida, um typo no Console produz um usuário logado que **nenhuma rule e
nenhuma tela restringem** — e `firestore.rules` dá `allow read: if isSignedIn()` em `sport_clients`,
então esse "jogador" leria o CPF e o telefone de todos os clientes cadastrados. Hoje `USER` no
documento derruba o login com "Conta sem perfil válido", e essa falha é a proteção. A constante entra
em F1.7, no mesmo commit das rules que a restringem.

## Arquitetura

### 1. Modelo de dados

`users/{uid}` — **inalterado**: `role`, `name`, `email`, provisionado via Console/Admin SDK,
`allow write: if false`.

`user_profiles/{uid}` — novo, escrito pelo próprio titular. Documento inteiro opcional, **sem
backfill**: ausente ⇒ `Result.success(null)` e a UI mostra "Não informado".

| Campo | Tipo | Ausente ⇒ |
|---|---|---|
| `apelido` | string (≤60) | "Não informado" |
| `cpf` | string, só dígitos (≤14) | "Não informado" |
| `phone` | string, só dígitos (≤20) | "Não informado" |
| `modalities` | array de strings do enum `SportModality` (≤10) | "Nenhuma informada" |
| `updatedAt` | timestamp | obrigatório; `== request.time` na rule (anti-backdating, espelha `acceptedAt` de F1.5) |

`account_deletions/{uid}` — trilha de auditoria, escrita **só** pelo Admin SDK dentro da Cloud
Function, **sem PII**. É prova de que a exclusão aconteceu, não backup dela.

### 2. `shared/account` — domínio da conta do titular

Segue o shape de `shared/privacy`. `commonMain`: modelos (`UserProfile`, `ProfilePatch`,
`ProfileResult`, `AccountDeletionResult`, `DataExportPayload`), interfaces de repositório
(`UserProfileRepository`, `AccountDeletionRepository`) e use cases (`GetMyProfileUseCase`,
`SaveMyProfileUseCase`, `ExportMyDataUseCase`, `DeleteMyAccountUseCase`). `androidMain`: DTO,
`FirestoreUserProfileRepository`, `FunctionsAccountDeletionRepository` e `AccountErrorMapper`
(espelhando `AuthErrorMapper`).

`UserProfileRepository.current(uid)` faz as **duas** leituras internamente — o use case não conhece
coleção. `SportModality` é reusado de `shared.sportclient.domain.validation`, não duplicado.

`DeleteMyAccountUseCase` chama o callable **primeiro** e só em sucesso faz `signOut()` +
`sessionStore.clear()`. A ordem inversa derrubaria o ID token antes de o backend poder validá-lo.

### 3. Cloud Function `deleteMyAccount`

`onCall` v2, Node 22, `enforceAppCheck: true`, região constante nos dois lados. **Sem payload** — o
uid vem só de `request.auth.uid`; qualquer chave em `request.data` é rejeitada com
`invalid-argument`, para documentar que a escalada foi considerada.

Ordem: ler `users`/`user_consents` → `anonymizeFinancial` (antes, porque anonimizar exige a
identidade que os passos seguintes destroem) → `user_consents/{uid}/history/*` em lotes de 500 →
`user_consents/{uid}` → `user_profiles/{uid}` → `users/{uid}` → auditoria → `deleteUser` **por
último** (assim que o Auth user some, o token morre e qualquer retry vira `unauthenticated`).

Idempotente: delete de doc inexistente é no-op, `auth/user-not-found` conta como sucesso. Isso é o
que torna seguro re-executar a função sobre um uid órfão.

### 4. `presentation/profile` — a tela "Meus dados"

Feature nova em MVI; `settings` permanece dono de Cardápio, Categorias e Política — configuração do
*operador*, não "meus dados". "A aba Config vira Perfil" é navegação e rótulo: a aba passa a
renderizar `ProfileScreen` e Configurações vira uma linha dentro dela apontando para a rota
`Routes.SETTINGS`, que já existe com seta de voltar.

Efeito colateral bom: hoje `SettingsScreen` é renderizada **duas vezes** (rota standalone + aba), com
dois `SettingsNavigation` a manter em sincronia. Com a mudança, a duplicação desaparece.

Máscara de CPF e telefone: aqui `canReveal` é **sempre true** — a autorização é *propriedade*, não
role. A máscara existe contra ombro e gravação de tela, não contra o dono. Isso é deliberadamente
diferente de `SportClientState.canRevealCpf` e precisa de comentário no código.

### 5. Invalidação de sessão pós-exclusão

Se o processo morrer entre o sucesso da Cloud Function e o `sessionStore.clear()`, o cold start
encontra sessão não expirada (TTL 24h) e `currentUid()` ainda devolve o uid — o SDK mantém o usuário
local até refrescar o token. O gate então lê `user_consents`, não acha, e joga o titular na tela de
consentimento **de uma conta excluída**, onde "Aceitar" recria o documento. É ressurreição parcial, e
é exatamente o roteiro que um revisor da Play executa: *excluir conta, reabrir o app*.

Correção: `AuthRepository.refreshToken()` consumido por `RestoreSessionUseCase`. Usuário inexistente
→ invalida a sessão. **Falha de rede → mantém a sessão** — tratar rede como "não autenticado"
deslogaria todo mundo que abrisse o app offline, que é a mesma classe de erro do incidente descrito
na Parte F.5 do runbook.

## Fora de escopo (F1.7)

Estabelecimentos (multi-tenancy) e o escopo do MOD por estabelecimento; role `USER`; enforcement da
matriz de permissões por aba. A seção "Estabelecimentos" **não é renderizada** na tela de perfil: uma
linha "em breve" numa tela cujo propósito é "estes são os dados que temos sobre você" anuncia dado
que o app não sabe produzir.

| Papel | Hoje | Alvo (F1.7) |
|---|---|---|
| `ADM` | tudo | tudo, em todos os estabelecimentos |
| `MOD` | igual a ADM nas rules (`isStaff()`) | seu(s) estabelecimento(s): financeiro, cardápio, categoria |
| `CLIENT` ("Funcionário") | lê `sport_clients`, sem escrita | comandas + consulta de clientes do seu estabelecimento |
| `USER` (jogador) | **não existe** — derruba o login | só consulta de eventos, própria comanda, próprio perfil |
