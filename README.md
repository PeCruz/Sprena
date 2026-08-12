# Sprena

Aplicativo de gestão de quadras esportivas de areia (Futevôlei, Beach Tennis, Vôlei de Praia).

Construído com **Kotlin Multiplatform** + **Compose Multiplatform** seguindo Clean Architecture + MVI.

## Stack

- Kotlin 2.1.10 · Compose Multiplatform 1.7.3 · Material 3
- Koin 4.0.2 (DI)
- Firebase Firestore 34.x (backend)
- Coroutines 1.9.0 · Flow · StateFlow

## Requisitos

- JDK 17+ (JDK 21+ para rodar os testes de Firestore Rules no emulador)
- Android SDK 35 (compile) / minSdk 26
- Docker (opcional — para builds isolados)

## Setup

```bash
git clone https://github.com/PeCruz/Kanoas.git
cd Kanoas
./gradlew :composeApp:assembleDebug
```

Coloque o arquivo `google-services.json` em `composeApp/` (não commitado — solicite ao mantenedor).

## Comandos comuns

| Tarefa | Local | Docker (sandbox) |
|--------|-------|------------------|
| Build debug | `./gradlew :composeApp:assembleDebug` | `docker compose run --rm android-build` |
| Testes unitários | `./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest` | `docker compose run --rm android-test` |
| Lint Android | `./gradlew :composeApp:lint` | `docker compose run --rm android-lint` |
| Static analysis | `./gradlew detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug ktlintCheck` | — |
| Format Kotlin | `./gradlew ktlintFormat` | — |
| Testar Firestore Rules | `npm --prefix tools/firestore-rules-tests run test:emulator` | — |
| Publicar Firestore Rules | `firebase deploy --only firestore:rules --project <projeto>` | — |

## Documentação

- [ARCHITECTURE.md](./ARCHITECTURE.md) — Diagrama de módulos e fluxo MVI
- [SECURITY.md](./SECURITY.md) — Decisões de segurança por sub-fase
- [docs/ops/firebase-users-runbook.md](./docs/ops/firebase-users-runbook.md) — Criar usuário no Console, validar login em device e publicar as Security Rules
- [CONTRIBUTING.md](./CONTRIBUTING.md) — Como contribuir
- [ROADMAP.md](./ROADMAP.md) — Roadmap de evolução
- [CLAUDE.md](./CLAUDE.md) — Padrões e diretrizes para IA assistente

## Licença

Proprietária — todos os direitos reservados.
