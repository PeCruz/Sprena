# Roadmap — Sprena

Roadmap de evolução do MVP até nível production-ready. As fases são independentes e podem ser executadas em qualquer ordem, mas a numeração reflete a recomendação por risco mitigado.

## F0 — Fundação documental + tooling — em execução
- Docs: README, ARCHITECTURE, CONTRIBUTING, ROADMAP
- Static analysis: detekt + ktlint
- CI: GitHub Actions (build + test + lint)
- Hygiene: CODEOWNERS, PR template, .editorconfig

## F1 — Segurança crítica + LGPD baseline
- ✅ **F1.1** — Build hardening: R8/Proguard, `allowBackup=false`, networkSecurityConfig, FLAG_SECURE
- ✅ **F1.2** — Logging seguro (Napier + Crashlytics) com sanitização de PII
- ✅ **F1.3** — Auth real (Firebase Auth) substituindo o mock; sessão em DataStore criptografado
- ✅ **F1.4** — Firestore Security Rules (testadas no emulador, no CI)
- ✅ **F1.4b** — Firebase App Check (Play Integrity/release, Debug Provider/debug)
- ✅ **F1.5** — Consentimento LGPD + política de privacidade + masking de CPF
- ✅ **F1.6a** — Direitos do titular sobre a **própria conta** (LGPD art. 18): tela de perfil
  (acesso), exportação em JSON (portabilidade) e exclusão de conta via Cloud Function
  (eliminação). Destrava a publicação na Play Store. Financeiro histórico é anonimizado
  (art. 16, I) — hoje o hook anonimiza zero registros, porque `financial`/`bar`/`menu` ainda são
  in-memory.
- 🔄 **F1.7** — Multi-tenancy (estabelecimentos), papel `USER`, Google Sign-In e matriz de
  permissões por aba. A matriz alvo está documentada em [SECURITY.md § F1.6a](./SECURITY.md).
  Fatiada em partes mergeáveis; `1 → 2 → 3` é ordem rígida, `6/7/8` podem ser paralelas.
  - ✅ **F1.7.1** — Estabelecimentos + grafo de membros. Role em dois níveis (`users.role`
    responde só "é ADM?"; `MOD`/`CLIENT`/`USER` viram papel por tenant em
    `establishments/{id}/members/{uid}`). `members` é `write: if false` — a aresta de
    autorização só é escrita pelo Admin SDK. "Meus estabelecimentos" é um collection group,
    e não o campo `establishmentIds` que F1.6a previa: sem cópia, sem drift, e a allowlist
    de `user_profiles` segue intocada.
  - ✅ **F1.7.2** — `sport_clients` entra no tenant. Fecha o `read: if isSignedIn()` que dava
    a qualquer conta autenticada o CPF e o telefone de todos os clientes. **Pré-requisito
    duro de F1.7.3**, que abre o cadastro: sem esta fase antes, abrir o cadastro seria
    publicar a base de CPFs. USER é membro do estabelecimento e mesmo assim não lê esta
    coleção — `isStaffOf`, não `canReadTenant`.
  - 🔄 **F1.7.3** — Papel `USER`, contexto ativo, abas por papel, administração de
    estabelecimentos e callables de vínculo.
    - ✅ Papel `USER`, `TenantContext`, `tabsFor()` e tela de "sem estabelecimento vinculado".
      A barra deixou de ser fixa e passou a ser montada pelo papel efetivo no estabelecimento
      ativo.
    - ✅ Telas de Estabelecimentos (CRUD) e Moderadores (leitura) na aba Config do ADM.
      `Membership` ganhou `displayName` porque as rules impedem o ADM de ler o nome de
      qualquer outra pessoa.
    - ✅ Callables `bootstrapAccount`, `linkMemberByCpf`, `setMemberRole`, `removeMember` e
      `leaveEstablishment`; `deleteMyAccount` varre vínculos e libera a trava de CPF.
      Vinculação é **write-only por CPF**: quem vincula não descobre se a pessoa já tem conta.
      Exige o segredo `CPF_PEPPER` antes do deploy (Parte J do runbook).
    - ⬜ Ligar o app às callables: `bootstrapAccount` no login e os botões de vincular,
      promover e desligar na tela de Moderadores.
  - ⬜ **F1.7.4** — Google Sign-In + account linking.
  - ⬜ **F1.7.5** — Pré-cadastro por CPF, claim no primeiro login e vínculos recentes.
  - ⬜ **F1.7.6 a F1.7.9** — Comandas, eventos, financeiro e cardápio saem da memória para
    subcoleções do estabelecimento. É aqui que "os dados não somem ao trocar de celular"
    passa a valer, e que `anonymizeFinancial` deixa de anonimizar zero registros.

## F2 — Completar Clean Architecture
- Para cada feature (`kanban`, `financial`, `bar`, `menu`):
  - Domain (model, usecase, repository interface)
  - Data (DTO + repository impl Firestore)
  - Refactor de ViewModels para consumir UseCases
- RBAC enforcado via `RoleGuardedUseCase` decorator (financeiro restrito a ADM/MOD)
- TDD obrigatório de UseCases e Repositories

## F3 — Design system + UX
- Componentes reutilizáveis em `core/ui/components`
- `UiStateScaffold` unificado (loading/error/empty/success)
- Paleta esportiva (revisão de Color.kt)
- Microinterações e transições
- Documentação em COMPONENTS.md + THEME_GUIDE.md

## F4 — Performance + Offline-first
- Firestore offline persistence ativado
- `derivedStateOf` em filtros computados
- Baseline Profile para cold start
- PERFORMANCE.md com checklist por PR

## F5 — Observabilidade + Analytics LGPD-aware
- Firebase Analytics com Consent Mode v2 (default = denied)
- Error tracking (Crashlytics ou Sentry KMP) com sanitização
- Eventos documentados em ANALYTICS.md

## F6 — Pós-MVP
- iOS target ativado
- Navigation type-safe (kotlinx.serialization routes)
- Feature flags (Remote Config)
- Exportação de relatórios financeiros (PDF/CSV)
- Biometria (BiometricPrompt)
- Settings: UI admin para roles e permissões

---

**Status atual:** F0 concluída. F1.1–F1.6a implementadas. F1.7 em execução. F2–F6 pendentes de
priorização do mantenedor.

**Cloud Functions:** as seis publicadas em `southamerica-east1` (2026-08-27) — `bootstrapAccount`,
`linkMemberByCpf`, `setMemberRole`, `removeMember`, `leaveEstablishment` e `deleteMyAccount`.
Com `deleteMyAccount` no ar, o bloqueio da Play Store caiu.

> **Ao mexer em Cloud Functions, lembrar:** implementado ≠ no ar. `firebase functions:list` é o
> que responde o que está publicado; teste verde no emulador não diz nada sobre produção.

**Pré-requisito de deploy — `CPF_PEPPER`:** as callables de vínculo exigem o segredo no Secret
Manager antes do primeiro deploy (Parte J.1 do [runbook](./docs/ops/firebase-users-runbook.md)).
O valor **nunca pode mudar** depois que houver pré-cadastro: o HMAC é o id do documento, então
trocá-lo torna toda pendência irreclamável.

**Pendência operacional:** o App Check (F1.4b) está no código, mas a *enforcement* de Firestore e
Auth é uma chave no Firebase Console — enquanto não for ligada (Parte G do
[runbook](./docs/ops/firebase-users-runbook.md)), a proteção não está valendo em produção.
(O callable `deleteMyAccount` de F1.6a aplica App Check por conta própria, independente dessa chave.)

**Plano Blaze:** ativo desde 2026-08-26. O free tier cobre a carga atual. Limpeza do Artifact
Registry configurada em 2026-08-27 (imagens com mais de 3 dias são apagadas) — era a única
cobrança recorrente que costuma aparecer num projeto deste tamanho.

**Ordem de release de F1.5 (bloqueante):** as rules de `user_consents` precisam ser publicadas
(`firebase deploy --only firestore:rules --project <projeto>`) **antes** de o APK com o gate de
consentimento chegar aos usuários. Na ordem inversa, a leitura de `user_consents` bate no
default-deny, o gate fail-closed trata isso como bloqueio e **todos os usuários existentes ficam sem
acesso ao app** — o aceite também é negado, então não há saída pela própria tela. Passo a passo e
recuperação na Parte F.5 do [runbook](./docs/ops/firebase-users-runbook.md).

**Ordem de release de F1.6a (bloqueante):** as rules de `user_profiles` e a Cloud Function
`deleteMyAccount` precisam ser publicadas **antes** de o APK chegar aos usuários. Na ordem inversa,
salvar o perfil e excluir a conta falham — e é justamente o botão de excluir que a review da Play
Store vai testar, que é o motivo de a fase existir. Passo a passo na Parte H.8 do
[runbook](./docs/ops/firebase-users-runbook.md).
