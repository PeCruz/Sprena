# Contribuindo com o Sprena

## Setup local

1. JDK 17 instalado (`java -version` deve mostrar 17.x)
2. Android SDK 35 instalado via Android Studio ou `sdkmanager`
3. Clonar e abrir no Android Studio: `File → Open → Kanoas`
4. Sincronizar Gradle (Android Studio faz automaticamente)

Para builds isolados, usar Docker:

```bash
docker compose run --rm android-build
docker compose run --rm android-test
docker compose run --rm android-lint
```

## Antes de abrir PR

```bash
./gradlew ktlintFormat
./gradlew detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug ktlintCheck
./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest
./gradlew :composeApp:assembleDebug
```

CI roda os mesmos comandos. PRs com checks falhando não fazem merge.

## TDD obrigatório

Antes de implementar qualquer feature ou correção:

1. Escrever o teste falhando primeiro.
2. Rodar para confirmar que falha pelo motivo certo.
3. Implementar o mínimo para passar.
4. Refatorar.
5. Commit em incrementos pequenos.

Stack de testes:
- `kotlin-test` (assertions)
- `Turbine` (Flow)
- `MockK` (apenas `androidUnitTest` — não em `commonTest`)

Nomes de teste em backticks NÃO podem conter `.`, `>`, `<`, `;`, `:`, `/`, `\`, `[`, `]` — o compilador Kotlin/Android rejeita esses caracteres em identificadores.

## Convenção de commits

Formato:

```
tipo(escopo): descrição curta no imperativo

corpo opcional explicando o porquê (não o quê)
```

Tipos permitidos: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `style`, `perf`, `build`, `ci`.

Exemplos:
- `feat(financial): add transaction filter by date range`
- `fix(login): handle network timeout without crashing`
- `chore(build): bump kotlin to 2.1.20`

## Fluxo de PR

1. Branch a partir de `master`: `feature/<short-name>`, `fix/<short-name>`, `chore/<short-name>`.
2. Commits pequenos e granulares (idealmente um por unidade lógica).
3. Push e abrir PR usando o template (`.github/pull_request_template.md`).
4. Aguardar CI verde + revisão de @PeCruz (CODEOWNERS).
5. Squash & merge.

## Padrões do projeto

Padrões obrigatórios em [CLAUDE.md](./CLAUDE.md) (MVI, Koin, restrições). Arquitetura geral em [ARCHITECTURE.md](./ARCHITECTURE.md). Não usar Hilt, Supabase, Ktor (ver "Restrições" no CLAUDE.md).
