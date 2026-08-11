# Arquitetura — Sprena

> Este documento descreve a arquitetura **vigente** do projeto. Para diretrizes de codificação dirigidas à IA, ver [CLAUDE.md](./CLAUDE.md).

## Visão geral

Sprena segue **Clean Architecture** com **MVI** (Model-View-Intent) na camada de apresentação. Multi-módulo via Kotlin Multiplatform: o módulo `shared` contém domínio e dados (puro Kotlin + adapters Android), e o módulo `composeApp` contém UI + ViewModels + DI raiz.

```mermaid
flowchart TB
    subgraph composeApp[":composeApp"]
        UI["Composable Screens"]
        VM["ViewModels (MVI)"]
        UI -->|Intent| VM
        VM -->|State| UI
        VM -->|Effect one-shot| UI
    end

    subgraph shared[":shared"]
        UC["UseCases"]
        REPO_IF["Repository interface"]
        REPO_IMPL["Repository impl"]
        DTO["DTOs"]
        VM --> UC
        UC --> REPO_IF
        REPO_IF -.realizado por.-> REPO_IMPL
        REPO_IMPL --> DTO
    end

    subgraph platform["androidMain"]
        FIRESTORE[("Firebase Firestore")]
        REPO_IMPL --> FIRESTORE
    end
```

## Camadas

| Camada | Local | Conteúdo |
|--------|-------|----------|
| Presentation | `composeApp/commonMain/.../presentation/<feature>` | Screen + ViewModel + State + Intent + Effect |
| Domain | `shared/.../<feature>/domain` | Model, UseCase, Repository (interface), Validation |
| Data | `shared/.../<feature>/data` (commonMain) + `shared/androidMain/.../<feature>/data/repository` | DTO + Repository impl (Firestore) |
| Core | `shared/.../core` | Contratos MVI, ValidationResult, módulos Koin |
| Platform | `composeApp/androidMain` | Activity, Application, bindings de plataforma |

## Fluxo MVI

```mermaid
sequenceDiagram
    participant U as Usuário
    participant S as Screen (Composable)
    participant VM as ViewModel
    participant UC as UseCase
    participant R as Repository

    U->>S: Ação (tap, input)
    S->>VM: handleIntent(Intent)
    VM->>UC: invoke(params)
    UC->>R: ler/escrever
    R-->>UC: Result<Data>
    UC-->>VM: Result<DomainModel>
    VM->>VM: state.update { copy(...) }
    VM-->>S: emit(State)
    S-->>U: recompose
    opt One-shot
        VM->>S: emit(Effect)
        S->>U: navegar / toast
    end
```

Contrato base em `shared/.../core/mvi/MviViewModel.kt`:

```kotlin
interface MviViewModel<STATE : UiState, INTENT : UiIntent, EFFECT : UiEffect> {
    val state: StateFlow<STATE>
    val effects: SharedFlow<EFFECT>
    fun handleIntent(intent: INTENT)
}
```

## Features implementadas

| Feature | Domain | Data | Notas |
|---------|--------|------|-------|
| auth | Sim | Mock | A migrar para Firebase Auth em F1 |
| sportclient | Sim | Firestore | CPF/phone hoje em plain text — corrigir em F1 |
| kanban | Só validation | Não | Lógica vive no VM — completar em F2 |
| financial | Só validation | Não | Idem |
| bar | Só validation | Não | Idem |
| menu | Só validation | Não | Idem |
| eventos | Só presentation | Não | Sem shared/domain ainda |

## Decisões arquiteturais (ADRs leves)

### ADR-001: Koin sobre Hilt
Aceito. Razão: KMP-friendly; Hilt depende de Dagger + AGP. Trade-off: menos verificação em compile-time.

### ADR-002: Firebase Firestore como backend
Aceito. Razão: real-time sync, offline persistence nativa, sem backend próprio para manter. Trade-off: vendor lock-in e custo por leitura/escrita.

### ADR-003: MVI em vez de MVVM puro
Aceito. Razão: estado único imutável reduz bugs de race condition; intents tornam ações explícitas e testáveis. Trade-off: mais boilerplate.

### ADR-004: KMP com iOS adiado
Aceito. Targets iOS comentados (`composeApp/build.gradle.kts:21-23`). Razão: foco em Android-first para MVP. Reativar em F6.

### ADR-005: Docker como sandbox local, CI em Gradle direto
Aceito. Razão: Docker isola dev local mas adiciona latência em CI; GH Actions roda Gradle direto com cache nativo.

## Segurança

Decisões de segurança e endurecimento de build estão documentadas em [SECURITY.md](./SECURITY.md). Fases aplicadas:

- **F1.1** — minificação R8, bloqueio de backup, network security config, `FLAG_SECURE`.
- **F1.2** — logging seguro (Napier + Crashlytics) com sanitização de PII (`PiiMasker`/`PiiScrubber`) e instrumentação inicial de Repositories críticos.
- **F1.3** — Firebase Authentication (email/senha), sessão local cifrada (Tink AEAD + DataStore Preferences, TTL 24h), reset de senha, auto-login, logout.
- **F1.4** — Firestore Security Rules (`firestore.rules`): `users/{uid}` legível só pelo dono e nunca gravável pelo app, `sport_clients` gravável só por ADM/MOD, default deny no resto. Testadas no emulador via `tools/firestore-rules-tests`.

Próximas sub-fases: F1.4b (Firebase App Check) e F1.5 (LGPD baseline).
