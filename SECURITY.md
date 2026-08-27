# Security — Sprena

Decisões de segurança aplicadas ao projeto. Cada sub-fase de F1 adiciona uma seção aqui.

## F1.1 — Build hardening + FLAG_SECURE

### R8 / Minify / Shrink (release build)

- `isMinifyEnabled = true` e `isShrinkResources = true` no `release`.
- Regras em `composeApp/proguard-rules.pro`. Cobertura:
  - Kotlin metadata, coroutines, kotlinx.serialization
  - Koin (DI por reflection)
  - Firebase Firestore (mapeamento DTO ↔ documento por reflection)
  - Compose / Lifecycle / Navigation
- DTOs e modelos de domínio em `shared/**/data/dto/` e `shared/**/domain/model/` são preservados.

**Trade-off:** debug build NÃO é minificado (mantém stack traces legíveis). O `signingConfig = signingConfigs.getByName("debug")` permite `assembleRelease` rodar local; substituir por chave real ao publicar.

### `allowBackup="false"` + Data extraction rules

- `android:allowBackup="false"` no manifest — desabilita auto-backup Android.
- `data_extraction_rules.xml` (Android 12+) — exclui todos os domínios de cloud backup e device transfer.

**Trade-off:** usuário perde "auto-backup pro Google Drive" e "transfer ao trocar de device". Justificativa: cadastros de clientes contêm CPF (mesmo que mascarado/hash em F1.5) — não vamos confiar nesse dado a backup automático fora do nosso controle.

### Network Security Config

- `network_security_config.xml` com `cleartextTrafficPermitted="false"` + `usesCleartextTraffic="false"` no manifest.
- Bloqueia HTTP em runtime, exige HTTPS. Firestore já é HTTPS — esta config formaliza a baseline.

### FLAG_SECURE global em `MainActivity`

- `window.setFlags(FLAG_SECURE, FLAG_SECURE)` em `onCreate`.
- Bloqueia screenshots e screen recording do app inteiro.

**Trade-off:** abordagem coarse (afeta TODAS as telas, inclusive Home, Settings, etc.). Alternativa per-screen exigiria wrap em cada `Composable` — descartada por complexidade e risco de esquecer alguma tela com CPF/valores.

### Verificação manual (pré-merge)

- [ ] APK release gera sem warnings novos (`./gradlew :composeApp:assembleRelease`)
- [ ] Manifest mergeado contém os atributos (Task 6 Step 3)
- [ ] Screenshot do app em device real sai preto / é bloqueado pelo sistema
- [ ] App ainda abre, navega Login → Home, consegue ler/escrever Firestore

## F1.2 — Logging seguro (Napier + Crashlytics + sanitização PII)

### Stack
- **Napier 2.7.1** — logger KMP, `DebugAntilog` plantado apenas em debug.
- **Firebase Crashlytics** (BOM 34.12.0) — `log` para warn/error, `recordException` para throwables. Desabilitado em debug (`setCrashlyticsCollectionEnabled(false)`).
- **Interface `Logger`** em `shared/commonMain/core/logger/` — única superfície usada por Repositories/UseCases. `AndroidLogger` é a impl injetada via Koin.

### Sanitização PII
- **`PiiMasker`** (commonMain) — masking explícito pelo call site:
  - `cpf("123.456.789-90")` → `"***.***.***-90"`
  - `phone("11987654321")` → `"(11)*******-21"`
  - `email("pedro@gmail.com")` → `"p***@gmail.com"`
- **`PiiScrubber`** (commonMain) — defense-in-depth: a impl `AndroidLogger` aplica regex sweep ANTES de emitir (CPF formatado, email, password=). Cobre o caso "esqueci de mascarar".

### Convenção de uso
1. **Sempre** receba `Logger` via construtor (Koin injeta).
2. **Nunca** logue objetos de domínio inteiros (`logger.info(TAG, "$client")`) — use campos específicos com `PiiMasker`.
3. **Nunca** logue `password`, mesmo "uma vez para debug".
4. Tag = nome curto da classe (ex.: `"SportClientRepo"`, `"LoginUseCase"`).
5. `error` é para falhas que devem ir ao Crashlytics; `warn` para situações esperadas mas anômalas.

### Trade-offs
- **Crashlytics desligado em debug**: evita poluir o painel com crashes de desenvolvimento. Custo: integração só é validada end-to-end após instalar release build.
- **Scrubber por regex**: pode dar falso-positivo (qualquer 11 dígitos após "cpf" vira mask). Aceito — falso-positivo em log é inofensivo, falso-negativo seria vazamento.
- **Instrumentação parcial**: apenas `SportClientRepositoryImpl` e `LoginUseCase` instrumentados. Demais Repos/UseCases entram conforme F2 (Clean Architecture) os refatorar.

### Verificação manual (pré-merge)
- [ ] `./gradlew :shared:testDebugUnitTest --tests "*Pii*"` — 22 tests pass
- [ ] Login com credencial inválida loga "login rejected" sem mostrar a senha em logcat
- [ ] Salvar cliente com Firestore offline gera `error` log com CPF mascarado
- [ ] APK release abre, navega normalmente, sem logs em logcat (Napier no-op em release)

## F1.3 — Firebase Auth + Sessão Criptografada

### Stack
- **Firebase Authentication** (BOM 34.12.0) com email + senha. `MockAuthRepository` removido.
- **Roles**: doc Firestore `users/{uid}` com `role: "ADM" | "MOD" | "CLIENT"`. Protegido via Security Rules em F1.4.
- **Sessão local**: `EncryptedSessionStore` usa Google Tink 1.13.0 (AEAD AES-256-GCM, chave no Android Keystore via `AndroidKeysetManager`) sobre `androidx.datastore:datastore-preferences`. Persiste: `uid`, `email`, `role`, `lastLoginEpochMillis`.
- **TTL**: 24h. Validado por `SessionValidator.isExpired`.
- **Clock**: abstração injetável (`SystemClock` em prod, `FixedClock` em testes).

### Fluxos
- **Login**: `LoginUseCase` valida → `FirebaseAuthRepositoryImpl.authenticate` → lê role no Firestore → `SessionStore.save`
- **Cold start**: `RestoreSessionUseCase` → se sessão local válida e uid bate com `auth.currentUser?.uid`, vai pra Home; senão Login
- **Logout**: `LogoutUseCase` → `auth.signOut()` + `SessionStore.clear()` (botão na `SettingsScreen` seção "Conta")
- **Reset de senha**: `RequestPasswordResetUseCase` → `auth.sendPasswordResetEmail` (link no `LoginScreen`)

### Erros mapeados (FirebaseAuth → PT-BR)
- `ERROR_INVALID_EMAIL` → "Email inválido"
- `ERROR_USER_NOT_FOUND` / `ERROR_WRONG_PASSWORD` / `ERROR_INVALID_CREDENTIAL` → "Email ou senha incorretos" (mesma mensagem — anti-enumeração)
- `ERROR_USER_DISABLED` → "Conta desativada. Contate o administrador"
- `ERROR_TOO_MANY_REQUESTS` → "Muitas tentativas. Tente em alguns minutos"
- `FirebaseNetworkException` → "Sem conexão. Verifique a internet"
- Outros → "Erro de autenticação"

Mapeamento em `AuthErrorMapper.kt` (`mapAuthError`), coberto por `AuthErrorMapperTest`.
Os ramos de `FirebaseFirestoreException` foram adicionados em F1.4 — ver abaixo.

### Convenção de uso
1. Nunca logar `password` — `LoginUseCase` e `FirebaseAuthRepositoryImpl` já garantem isso.
2. Sempre mascarar email no log via `PiiMasker.email(...)`.
3. Criar novos usuários SEMPRE pela Firebase Console (Auth + doc Firestore `users/{uid}` com role).
   Passo-a-passo operacional: [docs/ops/firebase-users-runbook.md](./docs/ops/firebase-users-runbook.md).
4. Em testes, injetar `FakeSessionStore` + `FixedClock` + `FakeAuthRepository`.

### Trade-offs
- **TTL 24h**: balanço entre conforto do operador (uma diária) e janela de exposição em device perdido.
- **Role no Firestore (vs Custom Claims)**: solo dev sem backend; aceita 1 leitura/login. Migrar para Custom Claims em F2 se F1.4 mostrar overhead.
- **Cadastro off-band**: zero superfície de abuso, mas exige intervenção manual do admin pra cada novo operador. Self-signup volta em F6 se houver demanda.
- **Tink + DataStore**: lib não-deprecated, AES-256-GCM com chave Hardware-backed (Android Keystore). Falha de decifragem → `load()` retorna null e força novo login.

### Verificação manual (pré-merge)
- [ ] Criar via Firebase Console: 1 user (auth) + doc `users/{uid}` com `email`, `role`, `name`
- [ ] Login → vai pra Home; logs com email mascarado via `PiiMasker.email`
- [ ] Fechar/abrir app → auto-login (não passa por Login)
- [ ] Sessão > 24h → cold start volta pra Login
- [ ] Settings → "Sair" → volta pra Login, auto-login desligado
- [ ] "Esqueci a senha" → email chega
- [ ] `session_prefs.preferences_pb` em disco é ilegível (Tink AEAD)

---

## F1.4 — Firestore Security Rules

### O que motivou
Login falhava com `PERMISSION_DENIED` **depois** de o Firebase Auth aceitar a senha: o banco estava
com as regras default de *production mode* (`allow read, write: if false`), então a leitura de
`users/{uid}` que resolve a role era negada. Nenhuma regra tinha sido escrita até aqui — o app
dependia inteiramente do RBAC client-side, que não vale nada contra quem chama a API direto.

### Modelo de acesso (`firestore.rules`, na raiz)

| Path | read | write |
|---|---|---|
| `users/{uid}` | só o próprio dono (`request.auth.uid == uid`) | **ninguém** |
| `sport_clients/{id}` | qualquer autenticado | só `ADM`/`MOD` |
| qualquer outra | negado | negado |

- **`users` é read-only pelo app** de propósito: provisionamento é Console/Admin SDK
  ([runbook](./docs/ops/firebase-users-runbook.md)). Se o app pudesse escrever ali, qualquer conta
  logada se auto-promoveria a `ADM` — a role está no mesmo doc que ela mesma controlaria.
- **Default deny explícito**: `kanban` e `financial` ainda são in-memory. Quando migrarem para o
  Firestore, vão bater no deny até ganharem seu próprio bloco `match`. Isso é intencional — falha
  fechada, não aberta.
- **`isStaff()` usa `get()`** no doc de perfil: custa 1 document access por escrita em `sport_clients`.
  É o mesmo trade-off já aceito em F1.3 (role no Firestore em vez de Custom Claims).

### Testes
`tools/firestore-rules-tests/` — 12 casos contra o emulador (`npm run test:emulator`), rodados no CI
no job `firestore-rules`. Projeto `demo-sprena`: emulador 100% offline, sem credencial nem
`firebase login`. Cobre auto-promoção de role, leitura cruzada entre usuários, escrita por `CLIENT`,
usuário autenticado sem doc de perfil e coleção não mapeada.
(F1.5 acrescentou os casos de `user_consents`; a suíte inteira está em 30.)

Deploy: `firebase deploy --only firestore:rules --project <projeto>`.
O `.firebaserc` é gitignorado (mesma postura do `google-services.json`) — daí o `--project` explícito.

### Erro na UI
`PERMISSION_DENIED` agora vira **"Conta sem permissão de acesso. Contate o administrador"** em vez do
genérico "Erro de autenticação", e o log carrega `code=PERMISSION_DENIED`. Sem isso, o sintoma aponta
para credencial quando o problema é autorização.

### Trade-offs
- **Leitura de `sport_clients` liberada para qualquer autenticado**: `CLIENT` (funcionário) precisa
  ver a lista para operar. PII (CPF/telefone) fica exposta a toda conta válida — o masking/hash em
  repouso é F1.5, e é ele que fecha essa ponta, não as rules.
- **Role em doc vs Custom Claims**: mantido. Se o `get()` por escrita virar custo real, Custom Claims
  elimina a leitura extra e permite `request.auth.token.role` direto na regra.

### Fora de escopo (F1.4b)
**Firebase App Check** — implementado em seguida, ver seção abaixo.

## F1.4b — Firebase App Check (Play Integrity)

### O que motivou
As rules de F1.4 respondem *quem* pode ler e gravar, mas não *de onde vem a chamada*. A `apiKey` do
`google-services.json` é extraível de qualquer APK, e com ela mais uma credencial válida (a de um
funcionário `CLIENT`, por exemplo) dá para falar com a REST API do Firestore fora do app — script,
Postman, emulador modificado. As rules continuam valendo, mas todo o rate limiting e a lógica de
tela deixam de existir. App Check fecha essa ponta: cada request carrega um token de atestação e o
backend recusa o que não vier de uma instalação genuína do app.

### Providers por build type

| Variante | Provider | Artefato | Como o token é aceito |
|---|---|---|---|
| `release` | `PlayIntegrityAppCheckProviderFactory` | `firebase-appcheck-playintegrity` | Play Integrity API + SHA-256 da chave de release registrado no Console |
| `debug` | `DebugAppCheckProviderFactory` | `firebase-appcheck-debug` (`debugImplementation`) | UUID do logcat registrado à mão no Console |

A escolha **não é um `if (BuildConfig.DEBUG)`**. `appCheckProviderFactory()` tem uma implementação
por build type (`composeApp/src/androidRelease/` e `composeApp/src/androidDebug/`), e o artefato do
provider de debug entra só via `debugImplementation`. O release, portanto, não compila contra a
classe insegura nem a empacota — verificável com
`./gradlew :composeApp:dependencies --configuration releaseRuntimeClasspath`, que só lista
`firebase-appcheck`, `-interop` e `-playintegrity`. Com um branch em runtime, a classe viajaria no
APK de produção e a garantia seria só convenção.

Instalação em `AppCheckBootstrap.init()`, chamada em `SprenaApplication.onCreate()` **antes do
`startKoin`** — o `FirebaseFirestore` e o `FirebaseAuth` só são construídos quando o Koin resolve as
dependências, então nenhum request escapa sem token.

### Enforcement é uma chave no Console, não no código
Instalar o provider **não protege nada sozinho**. Enquanto Firestore e Auth estiverem em modo
monitoramento no Console, requests sem token continuam passando — de propósito, para dar tempo de
ver na aba *Métricas* qual porcentagem de tráfego já chega verificada antes de ligar a chave.
Procedimento completo (registro de SHA, token de debug, ordem de ativação) na
[Parte G do runbook](./docs/ops/firebase-users-runbook.md).

### Erro na UI
Com enforcement ligado, uma atestação recusada derruba a leitura de `users/{uid}` com
`UNAUTHENTICATED` — que antes caía no genérico **"Erro ao carregar seu perfil"** e mandava
investigar o doc de perfil, que está intacto. Agora vira **"Não foi possível validar o app neste
dispositivo. Atualize e tente de novo"**, com `code=UNAUTHENTICATED` no log. Note a diferença de
ação em relação a `PERMISSION_DENIED` (F1.4): lá o admin resolve, aqui é a instalação do app que não
foi reconhecida.

### Trade-offs
- **Debug quebra até o token ser registrado.** É o custo de não ter um bypass em runtime: quem
  clonar o repo roda um build debug que falha a atestação até colar o UUID no Console. Documentado
  no runbook porque é o primeiro tropeço garantido de qualquer máquina nova.
- **Play Integrity depende do device.** Aparelho sem Google Play Services, root ou bootloader
  destravado falha a atestação mesmo com o app legítimo. Para MVP interno é aceitável; se aparecer
  usuário real nesse cenário, o fallback é adicionar o provider SafetyNet/reCAPTCHA ou afrouxar a
  enforcement em Auth mantendo em Firestore.
- **Cota da Play Integrity API**: 10 mil requests/dia no tier padrão. O SDK cacheia o token (TTL ~1h),
  então o consumo é por sessão, não por request — folgado para a escala atual, mas é o número a
  vigiar se o app crescer.
- **Não cobre o insider.** App Check atesta o *app*, não a *intenção*. Um funcionário `CLIENT`
  legítimo continua vendo a PII de `sport_clients` pelo app real — quem fecha isso é o F1.5.

### Verificação manual (pré-merge)

O provider de debug não pode estar no APK de release. Conferir no dex (o APK é multidex — tem que
varrer todos, não só `classes.dex`):

```bash
cd composeApp/build/outputs/apk
for v in debug release; do
  f=$(ls $v/*.apk | head -1); n=0
  for d in $(unzip -l "$f" | grep -o "classes[0-9]*\.dex" | sort -u); do
    n=$((n + $(unzip -p "$f" "$d" | grep -ac "DebugAppCheckProvider")))
  done
  echo "$v: $n"
done
```

Esperado: `debug: 5` (ou qualquer valor > 0) e **`release: 0`**. Release diferente de zero significa
que a separação por build type foi quebrada — provavelmente alguém moveu o provider para
`androidMain` ou trocou `debugImplementation` por `implementation`.

## F1.5 — Baseline LGPD (consentimento + política de privacidade + masking de CPF)

### Base legal e escopo
Dois tratamentos distintos, com base legal diferente cada um:
- **Usuário do app** (`users/{uid}`): consentimento explícito, registrado no aceite gravado por esta
  sub-fase.
- **Cliente cadastrado** (`sport_clients/{id}`): tratado pelo *operador* da conta — quem cadastra o
  cliente declara ter autorização do titular para inserir os dados no aplicativo (item 4 da política,
  `composeApp/src/commonMain/composeResources/files/privacy-policy.md`). O app não coleta esse
  consentimento diretamente; ele é responsabilidade contratual do operador.

### O que é coletado
- **Do usuário do app**: email, role (`ADM`/`MOD`/`CLIENT`) e data do último acesso.
- **Dos clientes cadastrados**: nome, apelido, CPF, telefone, email (quando informado), modalidades
  praticadas, presenças, forma de pagamento e histórico de pagamento/consumo.

### Onde o aceite é gravado
`firestore.rules`, bloco `user_consents/{uid}`:
- **Doc corrente** `user_consents/{uid}` — sobrescrito a cada nova versão aceita. Campos: `uid`,
  `policyVersion`, `acceptedAt` (`FieldValue.serverTimestamp()`), `appVersion`.
- **Subcoleção `user_consents/{uid}/history/{acceptanceId}`** — append puro, um doc por aceite, com
  id gerado pelo Firestore. É ela que sustenta o ônus da prova do consentimento (LGPD art. 8 §1)
  quando o doc corrente é sobrescrito por uma versão nova.
  O id é automático, e **não** a versão da política: com `history/{policyVersion}`, reaceitar a mesma
  versão vira `set` sobre doc existente, que em Rules conta como `update` e é negado — derrubando o
  batch inteiro, que é atômico. Como o gate é fail-closed, isso prendia o usuário na tela de
  consentimento em qualquer reaceite (falha transitória de leitura, doc raiz apagado no Console,
  retry de rede). Append puro elimina o conflito e casa com a semântica de trilha auditável.
- Escrita (`create`/`update`) restrita ao próprio uid, validada campo a campo pela rule
  (`request.resource.data.uid == uid`, `policyVersion` string não vazia, `acceptedAt == request.time`
  — bloqueia timestamp forjado pelo cliente). `delete` negado para todos, no doc corrente e no
  histórico.
- Gravação feita em batch atômico por `FirestoreConsentRepository`
  (`shared/src/androidMain/kotlin/br/com/sprena/shared/privacy/data/repository/FirestoreConsentRepository.kt`):
  o doc corrente e o doc de histórico são escritos na mesma transação — nunca um sem o outro.

> **Ordem de release (bloqueante).** Publicar as rules **antes** de distribuir o app. Na ordem
> inversa, a leitura de `user_consents` cai no default-deny e o gate fail-closed deixa **todos os
> usuários existentes sem acesso**. Procedimento e recuperação em
> [runbook § F.5](./docs/ops/firebase-users-runbook.md#f5--ordem-de-release-rules-primeiro-app-depois).

### Por que não em `users/{uid}`
As rules de F1.4 negam **toda** escrita do app em `users/{uid}` — inclusive do próprio dono — para
impedir auto-promoção de role (ver seção F1.4 acima). Gravar o aceite ali exigiria abrir uma exceção
nessa regra, o que reabriria a superfície que F1.4 fechou. `user_consents` é uma coleção própria
exatamente para não tocar nessa garantia.

### Gate fail-closed
`CheckConsentUseCase`
(`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/usecase/CheckConsentUseCase.kt`)
resolve `ConsentRepository.current(uid): Result<ConsentRecord?>` em três estados:
- `Result.success(null)` → nunca aceitou → `ConsentStatus.Required(MISSING)`
- `Result.success(record)` com `policyVersion` diferente da vigente → `ConsentStatus.Required(OUTDATED)`
- `Result.success(record)` com `policyVersion` igual → `ConsentStatus.Granted`
- `Result.failure(...)` (erro de leitura) → `ConsentStatus.Unavailable(mensagem)` — **nunca** vira
  acesso liberado. A tela de consentimento trata `Unavailable` como bloqueio com botão de retry, não
  como aceite implícito.

O `ConsentViewModel` reconsulta o consentimento no `init` e no `Retry`, não só carrega o texto:
`Granted` emite `NavigateHome` na hora (o usuário já tinha aceitado e só caiu no gate por falha
transitória de leitura), `Unavailable` deixa a mensagem visível com "tentar de novo", `Required`
segue pedindo o aceite. Sem essa reconsulta, a única ação oferecida a quem foi bloqueado por falha de
leitura seria "Aceitar" — e um aceite não corrige falha de rede.

A tela também tem **"Sair"** (`LogoutUseCase` → `NavigateLogin`). Não há "recusar" — recusar é fechar
o app, e isso é deliberado —, mas quem fica preso no gate precisa poder ao menos encerrar a sessão e
trocar de conta.

### Versionamento
`PrivacyPolicy.VERSION` (`shared/src/commonMain/kotlin/br/com/sprena/shared/privacy/domain/model/PrivacyPolicy.kt`)
é uma constante de código que precisa bater manualmente com a linha `Versão AAAA-MM-DD` do texto
embarcado em `composeApp/src/commonMain/composeResources/files/privacy-policy.md`. Vigente:
`"2026-08-12"`. Mudou o texto da política → muda a constante → todo usuário com `policyVersion`
antiga cai em `Required(OUTDATED)` no próximo `CheckConsentUseCase` e reaceita, sem perder o registro
anterior (ele fica em `history`). Procedimento documentado em `docs/legal/privacy-policy.md`.

### Masking de CPF
`maskCpf`/`formatCpf` em
`shared/src/commonMain/kotlin/br/com/sprena/shared/core/privacy/CpfMasker.kt`:
- `maskCpf("12345678900")` → `"***.***.789-00"`. Entrada que não normaliza para 11 dígitos vira
  máscara total (`"***.***.***-**"`) — malformado não vaza dígito parcial.
- `formatCpf` é o inverso (CPF completo pontuado), usado só quando a revelação já foi autorizada.
- **Não confundir com `PiiMasker.cpf` (F1.2)**: aquele mascara para log e preserva só os 2 últimos
  dígitos; este é para exibição em tela e preserva 3 dígitos do meio, propositalmente diferente.
- **Dois pontos de exibição**, com o mesmo tratamento — mascarado por padrão, `isCpfRevealed` só liga
  se `canRevealCpf` for `true`, e quem resolve `canRevealCpf` é sempre o ViewModel a partir da role
  da sessão (`sessionStore.load()`, `role in {ADM, MOD}`). A UI nunca decide sozinha quem revela:
  - `ClientDetailState.displayCpf` — detalhe do cliente de comanda
    (`composeApp/src/commonMain/kotlin/br/com/sprena/presentation/bar/clientdetail/`).
  - `SportClientState.displayCpf` — diálogo read-only de detalhe da aba Clientes
    (`composeApp/src/commonMain/kotlin/br/com/sprena/presentation/sportclient/`). Este é a aba HOME
    de ADM, MOD **e** CLIENT, então era o caminho de maior exposição: até F1.5 ele exibia o CPF
    completo para qualquer role. Reabrir o diálogo volta ao mascarado, mesmo para ADM/MOD.
- Campos de **edição** de cliente (`SportClientEditScreen`) seguem com o CPF completo, sem masking.
  Isso só é seguro porque a **visibilidade das ações de escrita** (adicionar, editar, excluir) é
  gate no `SportClientViewModel`, via `SportClientState.canManageClients` — resolvida no `init` a
  partir da mesma role da sessão, mas como campo próprio, independente de `canRevealCpf` (autorizações
  diferentes; hoje coincidem no mesmo conjunto de roles só por coincidência). Sem isso, um `CLIENT`
  conseguia abrir o lápis do diálogo de detalhe e ver o CPF completo no formulário de edição, mesmo
  sem conseguir gravar — a política de privacidade afirma que "a visualização do número completo é
  restrita a ADM/MOD", e isso vale para qualquer tela, não só o diálogo read-only. Defesa em
  profundidade, igual ao toggle de CPF: os intents de escrita (`AddClientClicked`,
  `EditClientClicked`, `ClientDeleted`) são ignorados no `handleIntent` quando `canManageClients` é
  `false` — esconder o botão na UI não é a única barreira. As Firestore Rules de F1.4 (`isStaff()`
  para escrever em `sport_clients`) continuam sendo a barreira de servidor: mesmo que a UI falhasse
  em esconder algo, a gravação de um `CLIENT` seria recusada lá.

### Limite conhecido
O masking é controle de **UI**, não de dado em repouso. As rules de `sport_clients` (F1.4) permitem
`read` a qualquer autenticado — inclusive `CLIENT` (funcionário). Quem chamar a API do Firestore
direto (script, Postman) com uma credencial `CLIENT` válida lê o CPF em texto plano, porque a rule
não distingue campos dentro do doc. Restringir a leitura de CPF por role nas próprias rules, ou
armazená-lo com hash/criptografia em repouso, é decisão de F2/RBAC — fora do escopo desta sub-fase.

### Verificação manual (pré-merge)
- [ ] `./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest` — testes de `CpfMasker`,
  `CheckConsentUseCase`/`AcceptConsentUseCase`, `ConsentViewModel` e masking no
  `ClientDetailViewModel` e no `SportClientViewModel` passam
- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` — a suíte passa inteira, incluindo os
  18 casos de `user_consents/{uid}` (ownership, append-only e anti-adulteração)
- [ ] **Rules publicadas antes de distribuir o app** — ver ordem de release acima
- [ ] Login novo (sem doc em `user_consents`) cai na tela de consentimento, não na Home
- [ ] Aceitar grava `user_consents/{uid}` e um doc novo em `user_consents/{uid}/history/` no mesmo
  instante (batch)
- [ ] Reaceitar (apagar só o doc raiz no Console e entrar de novo) funciona e acrescenta um segundo
  doc no histórico, sem erro
- [ ] Desligar a rede antes de abrir o app com sessão válida → tela de bloqueio com retry, nunca Home
- [ ] Religar a rede e tocar "Tentar de novo" com o aceite já registrado → entra na Home sem reaceitar
- [ ] "Sair" na tela de consentimento volta ao Login e não faz auto-login ao reabrir
- [ ] Detalhe de cliente com sessão `CLIENT` (comanda **e** aba Clientes): CPF mascarado, sem opção
  de revelar
- [ ] Aba Clientes com sessão `CLIENT`: sem FAB de adicionar, e o diálogo de detalhe sem lápis de
  editar nem lixeira de excluir
- [ ] Detalhe de cliente com sessão `ADM`/`MOD` (comanda **e** aba Clientes): CPF mascarado por
  padrão, revelável pelo botão 👁, e mascarado de novo ao reabrir
- [ ] Aba Clientes com sessão `ADM`/`MOD`: FAB de adicionar visível, diálogo de detalhe com lápis e
  lixeira funcionando
- [ ] Texto integral da política visível na própria tela de consentimento, antes de aceitar
  (é lá que ele precisa estar — com o aceite pendente o usuário não alcança o Settings)
- [ ] Depois do aceite: Settings → Política de Privacidade abre o mesmo texto, para releitura

---

## F1.6a â€” Direitos do titular sobre a prÃ³pria conta (LGPD art. 18)

### Base legal e escopo

TrÃªs direitos do art. 18 passam a ser exercÃ­veis dentro do app: **acesso** (II, tela de perfil),
**portabilidade** (V, exportaÃ§Ã£o em JSON) e **eliminaÃ§Ã£o** (VI, exclusÃ£o de conta). A exclusÃ£o
in-app Ã© tambÃ©m **exigÃªncia da Play Store** para apps com login â€” sem ela a publicaÃ§Ã£o Ã© reprovada.

Escopo Ã© a **prÃ³pria conta do usuÃ¡rio do app**. Os `sport_clients` continuam sob responsabilidade do
operador: sÃ£o titulares terceiros, e os direitos deles sÃ£o exercidos junto ao controlador, como a
polÃ­tica sempre disse. Multi-tenancy e o papel `USER` ficam para F1.7 (ver *Fora de escopo*).

A retenÃ§Ã£o de lanÃ§amentos financeiros anonimizados se apoia na **LGPD art. 16, I** (cumprimento de
obrigaÃ§Ã£o legal pelo controlador).

### Onde vivem os dados autodeclarados

`users/{uid}` **nÃ£o mudou**: continua `allow write: if false`, com `role`, `name` e `email`
provisionados pelo Console/Admin SDK. Os campos que o titular declara sobre si â€” `apelido`, `cpf`,
`phone`, `modalities` â€” vivem numa coleÃ§Ã£o separada, `user_profiles/{uid}`.

A separaÃ§Ã£o Ã© a mesma decisÃ£o de F1.5 (Â§ *Por que nÃ£o em `users/{uid}`*), levada adiante. O
contra-argumento "uma allowlist `hasOnly()` resolve" Ã© verdadeiro hoje; o ponto decisivo Ã© **F1.7**,
que precisarÃ¡ guardar quais estabelecimentos cada MOD gerencia. Um `establishmentIds` parece campo
de perfil mas Ã© **autorizaÃ§Ã£o** â€” se entrar na allowlist por reflexo, um moderador concede a si
mesmo qualquer estabelecimento, sem quebrar teste nenhum. Com os documentos apartados, a palavra
`role` nunca aparece na coleÃ§Ã£o que o cliente escreve, entÃ£o nÃ£o hÃ¡ allowlist a esquecer.

O documento Ã© **inteiramente opcional e nÃ£o tem backfill**: quem nunca editou nÃ£o tem o doc, e a UI
mostra "NÃ£o informado". `create` Ã© permitido (ao contrÃ¡rio de `users`) exatamente por isso.

### O que a tela expÃµe

Papel, nome, apelido, e-mail, CPF, telefone e modalidades. CPF e telefone aparecem **mascarados**,
com botÃ£o de revelar.

Aqui `canReveal` Ã© **sempre verdadeiro**, e isso Ã© deliberadamente diferente de
`SportClientState.canRevealCpf`: naquele caso a pergunta Ã© "este usuÃ¡rio pode ver o CPF de outra
pessoa?", e a resposta depende da role. Aqui a autorizaÃ§Ã£o Ã© **propriedade** â€” o dado Ã© do prÃ³prio
titular. A mÃ¡scara existe contra ombro e gravaÃ§Ã£o de tela, nÃ£o contra o dono. `FLAG_SECURE` (F1.1)
jÃ¡ bloqueia screenshot.

### ExclusÃ£o via Cloud Function, e nÃ£o pelo cliente

As rules negam `delete` em `users`, `user_consents` e `user_profiles`, e o usuÃ¡rio do Firebase Auth
sÃ³ cai por Admin SDK. O callable `deleteMyAccount` (`functions/src/`) Ã© o Ãºnico caminho.

Ele roda **sem payload**: o uid vem sÃ³ de `request.auth.uid`, e qualquer chave em `request.data` Ã©
recusada com `invalid-argument`. Aceitar um `uid` no corpo seria a escalada de privilÃ©gio Ã³bvia;
negar explicitamente documenta que a possibilidade foi considerada. HÃ¡ teste cobrindo o vetor.

### Ordem de exclusÃ£o (os 8 passos)

| # | Passo | Por que nesta posiÃ§Ã£o |
|---|---|---|
| 1 | ler `users/{uid}` e `user_consents/{uid}` | a versÃ£o de polÃ­tica vai para a trilha; ler depois de apagar Ã© tarde |
| 2 | `anonymizeFinancial(uid)` | anonimizar exige a identidade que os passos seguintes destroem |
| 3 | `user_consents/{uid}/history/*` (lotes de 500) | antes do pai: apagar o pai primeiro deixaria a subcoleÃ§Ã£o viva e **invisÃ­vel no Console**, e o operador acreditaria que o dado sumiu |
| 4 | `user_consents/{uid}` | â€” |
| 5 | `user_profiles/{uid}` | â€” |
| 6 | `users/{uid}` | â€” |
| 7 | `account_deletions/{uid}` | trilha de auditoria |
| 8 | `admin.auth().deleteUser(uid)` | **por Ãºltimo**: assim que o usuÃ¡rio some, o token morre e qualquer retry vira `unauthenticated`. Antes, uma falha no meio deixaria os dados no Firestore e o titular sem caminho para pedir de novo |

Idempotente: delete de doc inexistente Ã© no-op e `auth/user-not-found` conta como sucesso. Ã‰ isso que
torna seguro reexecutar a funÃ§Ã£o sobre um uid Ã³rfÃ£o (runbook, H.7).

### O que fica: `account_deletions/{uid}`

Trilha **sem PII** â€” nem e-mail, nem nome, nem CPF. Guarda `uid`, `deletedAt`,
`policyVersionAtDeletion`, `financialAnonymized`, `consentHistoryDeleted` e `appCheckVerified`. Ã‰
prova de que a exclusÃ£o aconteceu, nÃ£o backup dela. Cliente nÃ£o lÃª nem grava (regra escrita Ã  mÃ£o,
alÃ©m do default deny).

### AnonimizaÃ§Ã£o financeira â€” o que existe e o que nÃ£o existe

A polÃ­tica Ã©: os lanÃ§amentos **permanecem** para integridade contÃ¡bil, mas perdem o vÃ­nculo com o
titular (art. 16, I).

**Hoje a funÃ§Ã£o anonimiza ZERO registros.** `financial`, `bar` e `menu` sÃ£o in-memory no app e nÃ£o
existem no Firestore â€” nÃ£o hÃ¡ o que anonimizar. Sem esta frase, esta seÃ§Ã£o afirmaria um controle
inexistente. O campo `financialAnonymized` na trilha Ã© o que torna a afirmaÃ§Ã£o falsificÃ¡vel:
enquanto for `0`, o controle Ã© declaradamente vazio.

`anonymizeFinancial.ts` existe agora, e nÃ£o depois, por dois motivos: ele precisa rodar **antes** dos
deletes (passo 2), e quando F2 migrar essas coleÃ§Ãµes para o Firestore a implementaÃ§Ã£o entra ali sem
que nada mais do fluxo mude.

### App Check no callable

`enforceAppCheck` estÃ¡ ligado em produÃ§Ã£o e **independe** da chave de enforcement do Console (que
segue desligada â€” ver pendÃªncia do ROADMAP). ConsequÃªncia operacional: um build debug sem token de
App Check registrado recebe `unauthenticated` e parece bug (runbook, G.1).

**Limite conhecido:** o emulador de Functions *aplica* `enforceAppCheck`, e fornecer um token vÃ¡lido
na suÃ­te exigiria montar `initializeAppCheck` no cliente de teste â€” o que testaria o App Check, nÃ£o
a exclusÃ£o. Por isso a enforcement Ã© desligada **apenas** sob `FUNCTIONS_EMULATOR`. Os testes provam
a lÃ³gica de exclusÃ£o; a enforcement se verifica em device (runbook, H.6).

### RegiÃ£o

`FUNCTIONS_REGION` estÃ¡ declarada **nos dois lados** â€” `functions/src/index.ts` e
`composeApp/src/androidMain/.../PlatformModule.android.kt` â€” e precisam bater. DivergÃªncia devolve
`NOT_FOUND` no cliente, indistinguÃ­vel de "funÃ§Ã£o nÃ£o deployada": o sintoma nÃ£o aponta para a causa,
por isso as duas constantes estÃ£o documentadas juntas aqui.

### ExportaÃ§Ã£o

**Entra:** identidade da conta, perfil autodeclarado com CPF e telefone **completos** (mascarar nÃ£o
seria portabilidade â€” o destinatÃ¡rio Ã© o dono do dado) e a trilha de consentimento.

**Nunca entra:** qualquer documento de `sport_clients`. SÃ£o dados de terceiros, e exportÃ¡-los pela
porta de "meus dados" seria vazamento com aparÃªncia de direito. `ExportMyDataUseCase` nÃ£o recebe
`SportClientRepository` â€” **a omissÃ£o Ã© a garantia**, e hÃ¡ teste travando a regressÃ£o. TambÃ©m ficam
de fora token do Firebase, keyset do Tink, conteÃºdo do `session_prefs` e token do App Check.

**Risco do arquivo em claro:** o JSON vai para onde o titular mandar no share sheet. MitigaÃ§Ãµes: o
cache de export Ã© limpo a cada uso, o `FileProvider` Ã© `exported="false"` com caminho restrito a
`cache/exports`, e hÃ¡ diÃ¡logo de confirmaÃ§Ã£o avisando que o arquivo contÃ©m CPF e telefone sem
mÃ¡scara.

### RessurreiÃ§Ã£o pÃ³s-exclusÃ£o (corrigido)

Se o processo morresse entre o sucesso do callable e o `sessionStore.clear()`, o cold start
encontraria sessÃ£o nÃ£o expirada (TTL 24h) e `currentUid()` ainda devolveria o uid â€” o SDK mantÃ©m o
usuÃ¡rio local atÃ© renovar o token. O gate leria `user_consents`, nÃ£o acharia, e jogaria o titular na
tela de consentimento **de uma conta excluÃ­da**, onde "Aceitar" recriaria o documento.

Esse Ã© exatamente o roteiro que um revisor da Play executa: *excluir conta, reabrir o app*.

`AuthRepository.refreshToken()`, consumido por `RestoreSessionUseCase`, fecha a janela.
**Contrato deliberado: falha de rede devolve sucesso.** Tratar rede como "conta inexistente"
deslogaria todo mundo que abrisse o app offline â€” mesma classe de erro do incidente descrito na
Parte F.5 do runbook. Dois testes cobrem os dois lados.

BÃ´nus: o operador excluir um usuÃ¡rio pelo Console tambÃ©m passa a refletir no app, em vez de esperar
o TTL de 24h.

### Estado degradado conhecido

Se o passo 8 falhar depois do 6, os dados foram apagados e o usuÃ¡rio do Auth sobreviveu. O login bate
em `doc.exists() == false` â†’ "Conta nÃ£o autorizada. Contate o administrador." Sem crash e sem
vazamento, mas o Auth user Ã³rfÃ£o precisa de limpeza manual. `account_deletions` Ã© o que permite
detectar; o procedimento estÃ¡ em H.7.

### Fora de escopo (F1.7)

- **Estabelecimentos (multi-tenancy)** e o escopo do MOD por estabelecimento. O conceito nÃ£o existe
  no cÃ³digo: `sport_clients` Ã© uma coleÃ§Ã£o global plana. A seÃ§Ã£o "Estabelecimentos" **nÃ£o Ã©
  renderizada** na tela de perfil â€” uma linha "em breve" numa tela cujo propÃ³sito Ã© "estes sÃ£o os
  dados que temos sobre vocÃª" anuncia dado que o app nÃ£o sabe produzir, e lÃª como produto inacabado
  para um revisor da Play.
- **Papel `USER`** (o jogador): documentado, **nÃ£o implementado**. Adicionar a constante cedo Ã©
  ativamente perigoso â€” `FirebaseAuthRepositoryImpl` resolve a role com `UserRole.valueOf`, e
  `firestore.rules` dÃ¡ `allow read: if isSignedIn()` em `sport_clients`. Um typo no Console
  produziria um "jogador" com leitura do CPF e telefone de **todos** os clientes. Hoje `USER` no
  documento derruba o login com "Conta sem perfil vÃ¡lido", e essa falha Ã© a proteÃ§Ã£o. A constante
  entra em F1.7, no mesmo commit das rules que a restringem.
- **Matriz de permissÃµes** (alvo, nÃ£o estado atual):

| Papel | Hoje | Alvo (F1.7) |
|---|---|---|
| `ADM` | tudo | tudo, em todos os estabelecimentos |
| `MOD` | igual a ADM nas rules (`isStaff()`) | seu(s) estabelecimento(s): financeiro, cardÃ¡pio, categoria |
| `CLIENT` ("FuncionÃ¡rio") | lÃª `sport_clients`, sem escrita | comandas + consulta de clientes do seu estabelecimento |
| `USER` (jogador) | **nÃ£o existe** â€” derruba o login | sÃ³ consulta de eventos, prÃ³pria comanda, prÃ³prio perfil |

> Hoje as rules **nÃ£o** distinguem MOD por estabelecimento e **nÃ£o** conhecem `USER`.

### Ordem de release (bloqueante)

Publicar **rules e Cloud Function antes** de distribuir o APK.

Rules na ordem invertida travam todo mundo (bloqueio total, mesma liÃ§Ã£o de F1.5). A CF na ordem
invertida degrada sÃ³ o botÃ£o de exclusÃ£o â€” mas Ã© justamente o botÃ£o que a review da Play vai testar,
que Ã© o motivo de a fase existir. Passo a passo na Parte H do
[runbook](./docs/ops/firebase-users-runbook.md).

### VerificaÃ§Ã£o manual (prÃ©-merge)

- [ ] `./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest` verde
- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` â€” `fail 0`
- [ ] `cd functions && npm run test:emulator` â€” `fail 0`
- [ ] **Rules e Cloud Function publicadas antes de distribuir o app** â€” ver ordem de release acima
- [ ] Aba inferior mostra "Perfil" (Ã­cone de pessoa), e ConfiguraÃ§Ãµes abre por dentro dela com seta
  de voltar
- [ ] Perfil recÃ©m-provisionado (sem `user_profiles`) abre sem erro, com "NÃ£o informado" nos campos
  autodeclarados
- [ ] Preencher apelido, CPF, telefone e modalidades, salvar, e reabrir o app: os valores persistiram
- [ ] CPF e telefone comeÃ§am mascarados e revelam no botÃ£o ðŸ‘; ao reabrir a tela voltam mascarados
- [ ] CPF com menos de 11 dÃ­gitos Ã© recusado no formulÃ¡rio, sem tocar a rede
- [ ] "Redefinir senha" dispara o e-mail do Firebase e mostra a confirmaÃ§Ã£o
- [ ] "Exportar meus dados" mostra o aviso, abre o share sheet e gera um JSON legÃ­vel
- [ ] O JSON exportado **nÃ£o contÃ©m** nada de `sport_clients` e traz CPF e telefone sem mÃ¡scara
- [ ] "Excluir conta" sÃ³ habilita depois de digitar `EXCLUIR`
- [ ] Excluir uma conta de teste: `users`, `user_profiles`, `user_consents` e o `history` sumiram no
  Console, e `account_deletions/{uid}` existe **sem PII**
- [ ] Reabrir o app depois de excluir cai no **Login**, nÃ£o no gate de consentimento
- [ ] Abrir o app offline com sessÃ£o vÃ¡lida **nÃ£o** desloga
- [ ] Tentar logar com a credencial da conta excluÃ­da mostra mensagem de erro coerente
- [ ] Aceite da nova versÃ£o da polÃ­tica (2026-08-14) Ã© solicitado no prÃ³ximo acesso, e o aceite
  anterior continua no `history`

---

## F1.7.1 — Multi-tenancy: estabelecimentos e grafo de membros

Primeira fatia de F1.7. Introduz o tenant (`establishments`) e a aresta de autorização
(`members`), sem ainda criar o papel `USER`, o Google Sign-In ou as callables de vínculo —
esses vêm nas fatias seguintes, e a ordem entre elas não é livre (ver abaixo).

### Role em dois níveis

`users/{uid}.role` passa a responder uma única pergunta — **é ADM?** — e os papéis
operacionais migram para `establishments/{estId}/members/{uid}.role`, com valores `MOD`,
`CLIENT` e `USER`.

Sem isso, "MOD gere os **seus** estabelecimentos" não tem como ser expresso: uma role
global só sabe dizer que alguém é moderador, não de onde. O papel por tenant também deixa
a mesma pessoa ser MOD num lugar e CLIENT noutro.

No Kotlin isso aparece como dois enums, `UserRole` e `MemberRole`. A duplicação é
deliberada e tem um segundo motivo, de sequenciamento: este documento já registrava que
criar `UserRole.USER` antes das rules que a restringem é ativamente perigoso, porque
`sport_clients` ainda tem `read: if isSignedIn()`. `MemberRole.USER` não toca aquele
caminho.

### `members` é `write: if false`

Toda mutação do grafo passa por callable (F1.7.3) executando com Admin SDK. As rules
**leem** o grafo e nunca o escrevem.

Isso dá um ponto único de decisão e auditoria para a única coisa que concede acesso no
sistema, e deixa o arquivo de rules trivial de auditar nesse ponto: não existe escrita
alguma, em lugar nenhum, capaz de elevar privilégio. Enquanto as callables não existem, os
vínculos são semeados pelo Console (Parte I do runbook).

### O tenant vem do path, nunca de um campo

Todo helper novo recebe `estId` do path do documento:

```
isAdm()           → users/{uid}.role == 'ADM'                      1 get
isMemberOf(estId) → establishments/{estId}/members/{uid}.active    1 get
isStaffOf(estId)  → isMemberOf && role in ['MOD','CLIENT']
canReadTenant(e)  → isMemberOf(e) || isAdm()   (membro primeiro: caso comum, 1 get)
```

Não é estilo. Numa query o motor avalia a rule por documento, mas reaproveita o resultado
do `get()` quando a expressão do path é idêntica — então listar N documentos de um tenant
custa 1 get. Com o tenant num campo, o path mudaria a cada documento e o cache morreria,
estourando o orçamento de document access da query.

### `establishmentIds` não existe

O comentário de F1.6a neste documento previa que F1.7 acrescentaria `establishmentIds` — um
campo que "parece perfil e é autorização" — e alertava para o risco de alguém incluí-lo na
allowlist de `user_profiles` por reflexo.

O campo **não foi criado**. "Meus estabelecimentos" é
`collectionGroup('members').where('uid','==',me)`: uma query, sempre consistente com o
grafo, sem cópia para envelhecer. A allowlist de `user_profiles` segue intocada, e o caso
34 dos testes de rules continua sendo o que garante isso.

Como contrapartida, essa query exige índice de escopo *collection group*, agora declarado
em `firestore.indexes.json` (arquivo que não existia). Sem ele a consulta falha com
`FAILED_PRECONDITION` — e falha **só em produção**, porque o emulador cria índices sozinho.

### `user_settings` e a invariante que a sustenta

O estabelecimento ativo do seletor global fica em `user_settings/{uid}`, escrito pelo
próprio dono. Não pode morar em `users` (escrita negada) nem em `user_profiles` (seria
exatamente a allowlist que o parágrafo acima evita).

Deixar o cliente escrever ali só é seguro por uma invariante anotada no próprio
`firestore.rules`:

> **Nenhuma regra do arquivo lê `user_settings`.**

É por isso que apontar o contexto para um estabelecimento alheio é permitido e inútil: todo
acesso continua barrado por `isMemberOf(estId)`, que vem do path. Quem for editar as rules
depois precisa preservar essa invariante — o caso 70 dos testes existe para quebrar se ela
cair.

### Unicidade de CNPJ

`cnpj_index/{cnpjDigits}` tem o CNPJ como id do documento, e `update`/`delete` negados.
É o `create` sobre id existente que garante a unicidade, inclusive numa corrida entre dois
cadastros simultâneos — a consulta prévia do app é só para poder dizer "CNPJ já cadastrado"
em vez de "erro ao salvar".

A leitura é liberada **apenas ao ADM**. Não lhe entrega nada novo (ele já tem `list` em
`establishments`, onde o CNPJ está em texto claro) e é o que separa "CNPJ duplicado" de
"sem permissão" — sem ela, os dois chegariam como o mesmo `PERMISSION_DENIED` do batch.

O dígito verificador é validado no domínio (`CnpjValidator`), não nas rules: o motor de
rules não faz aritmética, e o máximo que consegue é a forma `^[0-9]{14}$`. Isso importa
para a unicidade — um CNPJ digitado errado não colide com o correto, então o mesmo
estabelecimento entraria duas vezes sem que o índice percebesse.

### Fora de escopo desta fatia

- **Papel `USER` no Kotlin e Google Sign-In** (F1.7.3 e F1.7.4). `bootstrapAccount` ainda
  não existe, então continua valendo o provisionamento manual da Parte B do runbook.
- **`sport_clients` segue global e plana**, com `read: if isSignedIn()`. Movê-la para
  dentro do tenant é F1.7.2, e é **pré-requisito duro** de F1.7.3: criar o papel `USER`
  antes disso tornaria todo login novo um leitor do CPF e do telefone de todos os clientes.
- **Comandas, eventos e financeiro continuam em memória** (F1.7.6 a F1.7.8). Enquanto
  estiverem, `anonymizeFinancial` segue anonimizando zero registros.
- **Callables de vínculo** (F1.7.3) e **pré-cadastro por CPF** (F1.7.5).

### Ordem de release

As rules de `establishments`, `members`, `user_settings` e `cnpj_index` são **puramente
aditivas**: nenhuma regra existente foi afrouxada ou removida, então publicá-las não afeta
o app em produção. Não há a ordem bloqueante que F1.5 e F1.6a tiveram.

O que **não** pode faltar antes de a fase ser usada de verdade é o
`firestore.indexes.json`: `firebase deploy --only firestore` publica rules e índices juntos.
Publicar só as rules deixa a consulta de vínculos falhando em produção.

### Verificação manual (pré-merge)

- [ ] `./gradlew ktlintCheck detektMetadataMain :composeApp:detektAndroidDebug :shared:detektAndroidDebug :composeApp:testDebugUnitTest :shared:testDebugUnitTest` verde
- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` — `fail 0` (76 casos)
- [ ] `firestore.indexes.json` referenciado em `firebase.json`
- [ ] `user_profiles` continua sem `establishmentIds` na allowlist (caso 34)
- [ ] Nenhuma regra do arquivo lê `user_settings` (caso 70)

---

## F1.7.2 — `sport_clients` entra no estabelecimento

Segunda fatia de F1.7, e a que fecha o buraco que este documento vinha registrando desde
F1.4. Move `sport_clients/{id}` para `establishments/{estId}/sport_clients/{id}`.

### O que estava aberto

A regra anterior era:

```
match /sport_clients/{clientId} {
  allow read: if isSignedIn();          // <- qualquer conta autenticada
  allow create, update, delete: if isStaff();
}
```

`sport_clients` guarda **CPF e telefone** de cada cliente. `read: if isSignedIn()` significa
que qualquer conta autenticada lia a base inteira, independentemente de papel ou de
estabelecimento.

Enquanto só existiam ADM, MOD e CLIENT — todos provisionados à mão pelo Console — o risco
era contido pelo cadastro fechado: não havia como obter uma conta sem que o dono do sistema
a criasse. F1.7.3 remove exatamente essa contenção, porque o primeiro login passa a criar a
conta sozinho. Sem esta fase antes daquela, **abrir o cadastro seria publicar a base de
CPFs**.

É a razão de `1.7.2 → 1.7.3` ser a única ordem rígida do plano, e é o alerta que a seção
"Fora de escopo (F1.7)" de F1.6a já fazia ao dizer que criar a constante `USER` cedo seria
"ativamente perigoso".

### A regra nova

```
match /sport_clients/{clientId} {
  allow read: if isStaffOf(estId) || isAdm();
  allow create, update, delete: if isStaffOf(estId) || isAdm();
}
```

`isStaffOf` e não `canReadTenant`: o **USER é membro do estabelecimento e ainda assim não
entra aqui**. Frequentar o lugar não dá acesso ao cadastro de quem mais frequenta. O caso 7
dos testes é o que guarda essa garantia, e o 9d falha se o bloco global voltar ao arquivo.

### `isStaff()` foi removido

O helper de F1.4 tinha `sport_clients` como único consumidor. Com o papel MOD deixando de
ser global, a pergunta que ele respondia — "é ADM ou MOD?" — não tem mais resposta sem um
estabelecimento em mãos. Quem precisar dela agora usa `isStaffOf(estId)`.

### Contrato Kotlin

`SportClientRepository` passa a receber `establishmentId` em cada chamada (e não no
construtor, porque o estabelecimento ativo muda em runtime) e a devolver `Result<T>` em vez
de lançar.

A troca de contrato foi feita agora porque a interface **não tem nenhum consumidor**: o
`SportClientViewModel` guarda os clientes em memória e nunca chegou a injetar o repositório,
apesar de ele estar registrado no Koin desde F0. Ligá-lo é F1.7.3, quando o seletor de
contexto existir na UI — e é mais simples ligá-lo a um contrato já no formato definitivo do
que trocar o formato depois, com consumidor em cima.

### Ordem de release

Esta é a primeira fase de F1.7 com uma remoção, mas **não é bloqueante**: o único código que
lia `sport_clients` nunca foi ligado, então nenhum APK em campo perde acesso. Publicar as
rules e depois apagar os documentos da coleção antiga pelo Console é suficiente.

Se em algum momento a coleção global voltar a ter dados (por exemplo, um APK antigo de
desenvolvimento), eles ficam inacessíveis, não expostos — o default deny cobre.

### Verificação manual (pré-merge)

- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` — `fail 0` (78 casos)
- [ ] `grep -c "match /sport_clients" firestore.rules` devolve `0`
- [ ] `grep -c "isStaff()" firestore.rules` devolve `0`
- [ ] Caso 7 (USER não lê) e caso 9d (coleção global morta) presentes e passando



---

## F1.7.3c — Callables de vínculo

As funções que escrevem a aresta de autorização. `members` é `write: if false` desde F1.7.1
justamente para que este seja o único caminho — um ponto de decisão, com auditoria garantida.

### `bootstrapAccount` e a idempotência como controle de segurança

Cria `users/{uid}` com papel `USER` no primeiro acesso. Existe porque `users` é
`write: if false`: se o cliente pudesse criar o próprio documento de papel, criaria com
`role: 'ADM'`.

A função usa **`create`, nunca `set`**. Se o documento já existe, ela lê e devolve o que está
lá. Isso não é economia de escrita — é o que impede a chamada repetida de rebaixar um
administrador. Um `set` transformaria a conta mais poderosa do sistema na mais fraca, e nada
no log pareceria um ataque: seria só alguém reabrindo o app.

O caso 2 de `functions/test/membership.test.mjs` é o que guarda essa propriedade.

### Vinculação write-only

`linkMemberByCpf` é o **único** caminho de vinculação. ADM→MOD, MOD→CLIENT e CLIENT→USER são a
mesma operação com papel diferente.

O desenho anterior buscava a pessoa por e-mail e vinculava pelo uid. Foi descartado: a busca
seria um oráculo de *"esta pessoa tem conta nesta plataforma"*, consultável por qualquer
moderador sobre qualquer endereço, sem em momento algum deixar de parecer uso legítimo.

Agora quem vincula digita o CPF e recebe apenas `linked`, `pending` ou `already` — o suficiente
para saber se deve avisar a pessoa a entrar no app, e nada além disso sobre CPFs que não sejam
o que ele mesmo digitou.

### O CPF nunca é gravado

O id do documento é `HMAC-SHA256(pepper, cpfDigits)`, e o pré-cadastro guarda apenas
`***.456.789-**`.

O CPF tem 11 dígitos, dois dos quais são verificadores — cerca de 10⁹ combinações úteis. Um
hash **sem** segredo seria varrido por força bruta em minutos por quem conseguisse ler a
coleção, e o efeito prático seria ter CPF em claro. Com o pepper no Secret Manager, o mesmo
ataque exige também vazar o segredo.

É por isso que o cliente nunca calcula esse valor: ele manda o CPF pela callable, e só o
servidor sabe transformá-lo em id.

**O pepper nunca pode mudar depois que houver pré-cadastro.** O HMAC é o id do documento;
trocá-lo torna toda pendência irreclamável, porque o CPF da pessoa passa a gerar outro id.
Rotacionar seria migração de dados, não troca de variável.

### A escada de papéis

| Quem chama | Pode conceder |
|---|---|
| ADM | MOD, CLIENT, USER |
| MOD do estabelecimento | CLIENT, USER |
| CLIENT do estabelecimento | USER |
| USER, forasteiro | nada |

Ninguém concede o próprio papel nem um acima dele, então nenhuma corrente de vinculações
produz alguém mais poderoso que quem a iniciou.

`setMemberRole` e `removeMember` aplicam a escada **duas vezes**: sobre o papel novo e sobre o
papel **atual** do alvo. Sem a segunda checagem, um CLIENT — que pode conceder `USER` —
rebaixaria o MOD do estabelecimento para `USER` e assumiria o lugar dele. Uma promoção
disfarçada de remoção. É o caso 14 dos testes.

### `ADM` é recusado antes de tudo

`assertAssignableRole` rejeita `role: 'ADM'` em qualquer callable, **inclusive quando quem
chama é um ADM**, e registra a tentativa em `security_events`.

Não é redundante com a escada: aquela responde "este chamador pode conceder este papel?", e
para um ADM a resposta seria sim. Esta responde "este papel pode existir num member doc?",
cuja resposta é sempre não. Administrador é papel global, criado só pelo Console — esta é a
única garantia que impede o app inteiro de fabricar um administrador.

### Desligar, não apagar

`removeMember` e `leaveEstablishment` marcam `active: false`. As rules tratam `active != true`
como "não é membro", então o efeito de acesso é imediato; manter o documento preserva a trilha
de quem já teve acesso, junto do `displayName` — a única forma de saber depois quem era.

`leaveEstablishment` não pede permissão a ninguém, e é isso que a torna necessária: é o remédio
de quem foi vinculado sem pedir, o caso do CPF digitado errado que alcançou a pessoa errada.
Sem ela, a saída dependeria de convencer quem fez o vínculo a desfazê-lo.

### Exclusão de conta

`deleteMyAccount` passa a varrer os vínculos (collection group), apagar `user_settings` e
liberar a trava em `cpf_claims`.

Vínculo órfão não vazaria nada — as rules leem o grafo, não o contrário —, mas carrega
`displayName` e continuaria aparecendo na lista de membros de cada estabelecimento, apontando
para uma conta que não existe mais. O titular pediu para sumir; sumir pela metade é pior que
não sumir. Liberar a trava permite reivindicar o mesmo CPF numa conta futura.

### App Check

Todas as callables aplicam `enforceAppCheck` por conta própria, independente da chave do
Console. Importa mais aqui do que em `deleteMyAccount`: `bootstrapAccount` é chamável por
qualquer conta autenticada e **cria documento**, então sem App Check ela seria uma forma barata
de encher a coleção `users`.

### O risco que sobra

Um dígito errado num CPF que por acaso também seja válido cria uma pendência presa ao número de
outra pessoa. Se essa pessoa entrar no app e informar aquele CPF, assume o vínculo — e, se o
papel era MOD, assume o estabelecimento.

Três defesas, nenhuma completa isoladamente: o dígito verificador barra a maioria dos erros de
digitação (só passa quem errar para outro CPF válido); a pendência fica visível na lista do
estabelecimento; e todo vínculo consumado vira um `member_event` que o staff vê e pode desfazer.

### Verificação manual (pré-merge)

- [ ] `cd functions && npm run lint && npm run build`
- [ ] `cd functions && npm run test:emulator` — `fail 0` (28 casos)
- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` — `fail 0` (86 casos)
- [ ] `grep -c "'ADM'" functions/src/membership.ts` — a recusa continua explícita
