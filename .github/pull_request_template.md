## O que este PR faz

<!-- Resumo objetivo em 1-3 linhas. Foco no "porquê", não no "o quê" (o diff mostra o quê). -->

## Tipo

- [ ] feat (nova funcionalidade)
- [ ] fix (correção de bug)
- [ ] refactor (sem mudança de comportamento)
- [ ] test (adição de testes)
- [ ] docs
- [ ] chore / build / ci

## Checklist

- [ ] Testes adicionados/atualizados (TDD)
- [ ] `./gradlew ktlintFormat detektMetadataMain ktlintCheck` passa local
- [ ] `./gradlew :composeApp:testDebugUnitTest :shared:testDebugUnitTest` passa local
- [ ] `./gradlew :composeApp:assembleDebug` compila
- [ ] Sem novos warnings de lint Android
- [ ] CLAUDE.md / ARCHITECTURE.md atualizados se padrões mudaram
- [ ] Dados sensíveis (CPF, telefone, token) tratados conforme LGPD/SECURITY.md

## Como testar

<!-- Passos manuais para validar a mudança no app rodando -->

## Screenshots (se UI)

<!-- Light + Dark mode -->

## Riscos / pontos de atenção

<!-- O que pode quebrar. O que NÃO foi testado. -->
