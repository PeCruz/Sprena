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
- Campos de **edição** de cliente (`SportClientEditScreen`) seguem com o CPF completo, sem masking:
  as rules de F1.4 já exigem `isStaff()` (ADM/MOD) para escrever em `sport_clients`, então quem edita
  já passou pelo mesmo crivo de role que libera a revelação.

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
- [ ] `cd tools/firestore-rules-tests && npm run test:emulator` — 30 casos passam, incluindo os 18 de
  `user_consents/{uid}` (ownership, append-only e anti-adulteração)
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
- [ ] Detalhe de cliente com sessão `ADM`/`MOD` (comanda **e** aba Clientes): CPF mascarado por
  padrão, revelável pelo botão 👁, e mascarado de novo ao reabrir
- [ ] Texto integral da política visível na própria tela de consentimento, antes de aceitar
  (é lá que ele precisa estar — com o aceite pendente o usuário não alcança o Settings)
- [ ] Depois do aceite: Settings → Política de Privacidade abre o mesmo texto, para releitura
