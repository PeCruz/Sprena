# Roadmap — Sprena

Roadmap de evolução do MVP até nível production-ready. As fases são independentes e podem ser executadas em qualquer ordem, mas a numeração reflete a recomendação por risco mitigado.

## F0 — Fundação documental + tooling — em execução
- Docs: README, ARCHITECTURE, CONTRIBUTING, ROADMAP
- Static analysis: detekt + ktlint
- CI: GitHub Actions (build + test + lint)
- Hygiene: CODEOWNERS, PR template, .editorconfig

## F1 — Segurança crítica + LGPD baseline
- Build hardening: R8/Proguard, `allowBackup=false`, networkSecurityConfig
- Firestore Security Rules + Firebase App Check
- Auth real (Firebase Auth) substituindo o mock; sessão em DataStore criptografado
- Consentimento LGPD + política de privacidade + masking de CPF
- FLAG_SECURE em telas sensíveis
- Logging seguro (Napier + Crashlytics) com sanitização de PII

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

**Status atual:** F0 em execução. Demais fases pendentes de priorização do mantenedor.
