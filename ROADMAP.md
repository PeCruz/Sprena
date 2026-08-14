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
- ⬜ **F1.7** — Multi-tenancy (estabelecimentos), papel `USER` e matriz de permissões por aba.
  Hoje `sport_clients` é uma coleção global plana e as rules não distinguem MOD por
  estabelecimento. A matriz alvo está documentada em [SECURITY.md § F1.6a](./SECURITY.md).

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

**Status atual:** F0 concluída. F1 fechada (F1.1–F1.6a). F1.7 e F2–F6 pendentes de priorização do
mantenedor.

**Pendência operacional:** o App Check (F1.4b) está no código, mas a *enforcement* de Firestore e
Auth é uma chave no Firebase Console — enquanto não for ligada (Parte G do
[runbook](./docs/ops/firebase-users-runbook.md)), a proteção não está valendo em produção.
(O callable `deleteMyAccount` de F1.6a aplica App Check por conta própria, independente dessa chave.)

**Pendência operacional:** F1.6a exige o **plano Blaze** ativo — Cloud Functions não deploya no
Spark. O free tier cobre a carga; configurar a limpeza do Artifact Registry no primeiro deploy
(Parte H.1 do runbook).

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
