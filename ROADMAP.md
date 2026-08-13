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
- ⬜ **F1.6** — Direitos do titular (LGPD art. 18): acesso, exportação e exclusão de dados.
  Inclui exclusão de conta in-app — **exigência da Play Store** para apps com login, bloqueia
  publicação. Depende de decidir retenção/anonimização de dados financeiros históricos e
  provavelmente de uma Cloud Function.

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

**Status atual:** F0 concluída. F1 fechada (F1.1–F1.5). F1.6 e F2–F6 pendentes de priorização do
mantenedor.

**Pendência operacional:** o App Check (F1.4b) está no código, mas a *enforcement* de Firestore e
Auth é uma chave no Firebase Console — enquanto não for ligada (Parte G do
[runbook](./docs/ops/firebase-users-runbook.md)), a proteção não está valendo em produção.

**Ordem de release de F1.5 (bloqueante):** as rules de `user_consents` precisam ser publicadas
(`firebase deploy --only firestore:rules --project <projeto>`) **antes** de o APK com o gate de
consentimento chegar aos usuários. Na ordem inversa, a leitura de `user_consents` bate no
default-deny, o gate fail-closed trata isso como bloqueio e **todos os usuários existentes ficam sem
acesso ao app** — o aceite também é negado, então não há saída pela própria tela. Passo a passo e
recuperação na Parte F.5 do [runbook](./docs/ops/firebase-users-runbook.md).
