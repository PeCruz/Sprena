# Runbook — Firebase: usuários, validação em device, rules e App Check

> Sprena não tem self-signup. **Todo** usuário é criado à mão: 1 registro no Firebase
> Authentication **e** 1 documento `users/{uid}` no Firestore. Faltando qualquer um dos dois,
> o login falha mesmo com a senha correta.
>
> Este documento é versionado e **não contém dado real**. Emails, senhas e UIDs dos usuários
> de teste vão em `docs/ops/test-users.local.md` (gitignorado) — ver [Registro local](#registro-local).

---

## Pré-requisitos

- Acesso ao projeto **`sprena-a9b55`** no [Firebase Console](https://console.firebase.google.com/).
- `composeApp/google-services.json` presente localmente (gitignorado — ver [README](../../README.md#setup)).
- Device físico ou emulador **com Google Play Services** (Firebase Auth não funciona em imagem AOSP pura).
- `adb` acessível (`adb devices` deve listar o aparelho).

> **Windows/PowerShell:** o `adb` normalmente **não** está no PATH. Ele vem com o Android SDK em
> `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Os comandos deste runbook estão em sintaxe
> POSIX; no PowerShell, resolva o caminho uma vez por terminal e chame com o operador `&`:
>
> ```powershell
> $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
> & $adb devices
> ```
>
> Se não estiver lá, procure com `Get-ChildItem "$env:LOCALAPPDATA\Android" -Recurse -Filter adb.exe`.
> Daqui em diante, onde se lê `adb`, use `& $adb`.

---

## Parte A — Criar o usuário no Authentication

1. Console → **Build → Authentication**.
2. Primeira vez apenas: aba **Sign-in method** → habilitar **Email/Password**.
   Não habilitar Google Sign-In — está fora de escopo até F6.
3. Aba **Users** → **Add user**.
4. Preencher:
   - **Email**: precisa casar com o regex de `LoginValidator` — `algo@dominio.tld`, sem espaços, máx. 254 chars.
   - **Password**: mínimo **6 caracteres**, sem espaço no início ou fim (o app rejeita antes de chamar o Firebase).
5. **Add user** → a linha aparece na tabela.
6. Copiar o **User UID** (coluna à direita, ícone de copiar). Ele é o **document ID** da Parte B.

- [ ] Usuário aparece na aba Users
- [ ] UID copiado

---

## Parte B — Criar o documento `users/{uid}`

O app lê a role aqui **depois** que o Firebase Auth aceita a senha — é o segundo portão de entrada.

1. Console → **Build → Firestore Database**.
2. Coleção **`users`** (nome exato, minúsculo). Se não existir: **Start collection** → Collection ID `users`.
3. **Add document** → em **Document ID**, **colar o UID da Parte A**.
   ⚠️ **Nunca clicar em "Auto-ID"** — um doc com ID aleatório é invisível para o app e o login cai em
   "Conta não autorizada".
4. Adicionar os campos (todos do tipo `string`):

| Campo   | Tipo   | Obrigatório | Valor                        |
|---------|--------|-------------|------------------------------|
| `role`  | string | **sim**     | `ADM` \| `MOD` \| `CLIENT`   |
| `name`  | string | não         | Nome de exibição             |
| `email` | string | não         | Mesmo email do Auth          |

5. **Save**.

### O que o app realmente lê

Fonte: `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/FirebaseAuthRepositoryImpl.kt`

- **`role`** — obrigatório. Parseado com `UserRole.valueOf(valor.uppercase())`, então `adm`, `Adm` e `ADM`
  funcionam, mas **`admin`, `ADMIN`, `Administrador`, `USER` derrubam o login** com "Conta sem perfil válido".
  Valores válidos e o que aparecem na UI:

  | `role`   | Exibição        |
  |----------|-----------------|
  | `ADM`    | Administrador   |
  | `MOD`    | Moderador       |
  | `CLIENT` | Funcionário     |

- **`name`** — opcional. Ausente → o app usa o trecho antes do `@` do email.
- **`email`** — **não é lido em runtime**. O `UserModel.email` vem do que o usuário digitou no login.
  O campo existe só para o doc ser legível no Console. Escrever um email errado aqui **não** quebra nada
  (e por isso também não protege nada — não confie nele).

- [ ] Document ID é exatamente o UID (não Auto-ID)
- [ ] `role` é um de `ADM` / `MOD` / `CLIENT`
- [ ] Doc salvo e visível em `users/{uid}`

---

## Parte C — Validação manual em device

### Comandos

```bash
# instalar o debug build (applicationId br.com.sprena)
./gradlew :composeApp:installDebug

# limpar buffer de log antes de cada cenário
adb logcat -c

# acompanhar só as tags de auth/sessão
adb logcat -s FirebaseAuthRepo LoginUseCase RestoreSession LogoutUseCase EncryptedSessionStore

# estado limpo: apaga a sessão criptografada + o cache local do Firebase Auth
adb shell pm clear br.com.sprena
```

> Logs só existem em **debug** — em release o Napier não planta antilog e vira no-op.

### Checklist

- [ ] **Login válido** → vai pra Home. Log: `FirebaseAuthRepo login ok uid=… email=p***@dominio`
- [ ] **Senha errada** → "Email ou senha incorretos"
- [ ] **Email sem doc `users/{uid}`** → "Conta não autorizada. Contate o administrador."
      Log: `user doc missing email=… uid=…`
- [ ] **`role` inválida** (trocar para `admin` no Console e tentar) → "Conta sem perfil válido".
      Log: `user doc has invalid role uid=… raw=admin`
- [ ] **Sem internet** (modo avião) → "Sem conexão. Verifique a internet"
- [ ] **Auto-login**: matar o app (swipe do recentes) e reabrir dentro de 24h → entra direto na Home,
      sem passar pelo Login. Log da tag `RestoreSession`
- [ ] **Expiração (TTL 24h)**: Ajustes do Android → Data e hora → desligar automático → avançar 2 dias →
      reabrir o app → volta pro **Login**. *(Voltar o relógio também expira — skew conta como expirado.)*
- [ ] **Logout**: no app, Ajustes → seção "Conta" → **Sair** → volta pro Login; reabrir não faz auto-login
- [ ] **Sem PII em claro**: varrer o logcat — nenhuma senha, nenhum email completo
      (o esperado é sempre a forma mascarada `p***@dominio`)
- [ ] **Sessão em disco ilegível**: `/data/data/br.com.sprena/files/datastore/session_prefs.preferences_pb`
      não contém o email/uid em texto (AES-256-GCM via Tink). Em device não-rooteado, dá pra inspecionar
      só com build debuggable: `adb shell run-as br.com.sprena cat files/datastore/session_prefs.preferences_pb`

### Notas de ambiente

- **`FLAG_SECURE` está ativo** em toda a `MainActivity`: `adb exec-out screencap` e gravação de tela saem
  **pretos**. Isso é o comportamento correto (F1.1), não um bug. Para registrar evidência, fotografe a tela.
- `adb shell pm clear` derruba também o keyset do Tink — a sessão anterior fica irrecuperável por design.

---

## Parte D — Troubleshooting

| Mensagem na tela | Causa provável | Correção |
|---|---|---|
| "Email inválido" | formato do email rejeitado pelo Firebase (`ERROR_INVALID_EMAIL`) | conferir digitação |
| "Email ou senha incorretos" | senha errada, usuário inexistente ou credencial inválida (mensagem única, anti-enumeração) | resetar senha na Parte E |
| "Conta desativada. Contate o administrador" | usuário marcado como **Disabled** no Console | Authentication → Users → menu ⋮ → Enable account |
| "Muitas tentativas. Tente em alguns minutos" | rate limit do Firebase após várias falhas | esperar ~5 min ou testar de outra rede |
| "Sem conexão. Verifique a internet" | device offline / sem rota pro Firebase | conferir Wi-Fi, modo avião |
| "Falha inesperada na autenticação" | Auth retornou sucesso sem `uid` (raro) | repetir; se persistir, checar `google-services.json` e o projeto Firebase |
| **"Conta não autorizada. Contate o administrador."** | doc `users/{uid}` **não existe** — quase sempre Auto-ID em vez do UID | refazer a Parte B com o Document ID correto |
| **"Conta sem perfil válido"** | `role` ausente, com typo, ou fora de `ADM`/`MOD`/`CLIENT` | corrigir o campo `role` no Console |
| **"Conta sem permissão de acesso. Contate o administrador"** | as Security Rules negaram a leitura de `users/{uid}` | ver bloco abaixo |
| "Erro ao carregar seu perfil" | outra falha do Firestore na leitura do perfil | ver logcat: `FirebaseAuthRepo … code=<CODE>` |
| "Erro de autenticação" (genérico) | qualquer outra exceção | ver logcat: a tag `FirebaseAuthRepo` traz `cause=<NomeDaExceção>` |

**`PERMISSION_DENIED` depois de a senha ser aceita** — o Auth aprovou, o Firestore recusou. Ou seja:
autenticado no Firebase, mas sem sessão no app. Checar, nesta ordem:

1. As rules foram publicadas neste projeto? `firebase deploy --only firestore:rules --project <projeto>`
   — o Console mostra a data do último deploy em Firestore Database → Rules.
2. O Document ID do doc de perfil bate **exatamente** com o UID do Auth? A regra é
   `request.auth.uid == uid`; com Auto-ID ela nega mesmo com as rules corretas.
3. Logcat confirma a origem: `FirebaseAuthRepo login failed … cause=FirebaseFirestoreException code=PERMISSION_DENIED`.

O modelo de acesso completo está em [SECURITY.md](../../SECURITY.md) (seção F1.4); as regras, em
`firestore.rules` na raiz. Mexeu nelas? Rode `npm run test:emulator` em `tools/firestore-rules-tests/`
antes do deploy.

**Nome errado na Home após auto-login:** esperado. O fluxo de restore deriva o nome do email
(`NavGraph.kt`); o campo `name` do Firestore só aparece no login "fresco". Não é regressão.

**Mudei a `role` e nada mudou:** a role fica gravada na sessão criptografada local. Exige **Sair** e logar
de novo (ou `adb shell pm clear br.com.sprena`).

---

## Parte E — Manutenção

- **Resetar senha** — Console → Authentication → Users → menu ⋮ → *Reset password* (envia email),
  ou pelo próprio app em "Esqueci a senha" no `LoginScreen`.
- **Desativar acesso temporariamente** — menu ⋮ → *Disable account*. O usuário passa a ver
  "Conta desativada. Contate o administrador". Reversível, preserva o histórico.
- **Trocar a role** — editar o campo `role` no doc `users/{uid}`. **Exige novo login** (ver armadilha acima).
- **Remover um usuário** — apagar **os dois**: o registro no Authentication **e** o doc `users/{uid}`.
  - Só o Auth → sobra doc órfão no Firestore.
  - Só o doc → o usuário ainda autentica, mas trava em "Conta não autorizada".
- **Device novo / reinstalação** — nada a fazer no Console. Basta logar; a sessão é recriada localmente.

- [ ] Após qualquer mudança de role ou remoção, revalidar com a Parte C

---

## Parte F — Publicar as Security Rules

As regras vivem em **`firestore.rules`** na raiz do repo. Editar o arquivo não muda nada em
produção — só o deploy publica. E o contrário também vale: publicar sem rodar os testes é como
mergear sem CI.

### F.1 — Autorizar o CLI (uma vez por máquina)

```bash
npm install -g firebase-tools   # se ainda não tiver
firebase login
```

Rode o `firebase login` num **terminal interativo de verdade** — ele abre o navegador e fecha o
ciclo sozinho pelo localhost. Em ambiente headless (incluindo agentes de IA) ele cai num fluxo de
código manual: imprime um *session ID* + URL, e espera o **código de autorização** que a página
devolve **depois** do login. Session ID e código de autorização são coisas diferentes — passar o
session ID falha com "Unable to authenticate using the provided code".

Logar com a conta que é dona do projeto. Conferir:

```bash
firebase login:list
firebase projects:list          # sprena-a9b55 tem que aparecer
```

### F.2 — Testar antes de publicar

```bash
npm --prefix tools/firestore-rules-tests install    # primeira vez
npm --prefix tools/firestore-rules-tests run test:emulator
```

Roda a suíte contra o emulador local no projeto `demo-sprena` — offline, sem tocar em nada real.
Esperado: `pass 30 / fail 0`. Falhou? Não publique.

> Nas negações, o emulador loga `evaluation error at L<n>` seguido de `false` na mesma linha.
> É o motor reavaliando depois de resolver o `get()` — a decisão que vale é a segunda. Não é bug.

### F.3 — Publicar

Da **raiz do repo** (é onde estão `firebase.json` e `firestore.rules`):

```bash
firebase deploy --only firestore:rules --project sprena-a9b55
```

O `--project` é obrigatório: o `.firebaserc` é gitignorado de propósito, mesma postura do
`google-services.json`.

Saída esperada:

```
+  cloud.firestore: rules file firestore.rules compiled successfully
+  firestore: released rules firestore.rules to cloud.firestore
+  Deploy complete!
```

- [ ] Console → Firestore Database → aba **Rules**: conteúdo bate com o arquivo e a data do último
      deploy é de agora
- [ ] Login em device continua funcionando (Parte C)

### F.4 — Reverter

Console → Firestore Database → **Rules** → histórico de versões → selecionar a anterior →
**Restore**. O Firebase versiona cada deploy; a volta é um clique.

### F.5 — Ordem de release: rules primeiro, app depois

> ⚠️ **Bloqueante de release.** Uma versão do app que estreia uma coleção nova só pode chegar aos
> usuários **depois** de as rules dessa coleção estarem publicadas. Inverter a ordem tira **todos os
> usuários existentes** do ar — não é degradação parcial, é bloqueio total.

O caso concreto é o gate de consentimento (F1.5). Se o APK com o gate for distribuído antes do
deploy das rules:

1. o app lê `user_consents/{uid}` e bate no `match /{document=**} { allow read, write: if false }`;
2. a leitura falha com `PERMISSION_DENIED` → `CheckConsentUseCase` devolve `ConsentStatus.Unavailable`;
3. o gate é **fail-closed**: `Unavailable` nunca vira acesso, então o usuário cai na tela de
   consentimento;
4. o aceite também é negado pela mesma razão — a gravação bate no default-deny.

Resultado: **todo usuário já cadastrado fica sem acesso ao app**, inclusive quem nunca teve nada a
ver com a mudança, e sem nenhum caminho para frente dentro do app. Não é "um retry falhou".

**Ordem obrigatória de cada release que toca em `firestore.rules`:**

| # | Passo | Como validar antes de seguir |
|---|---|---|
| 1 | Rodar a suíte de rules (F.2) | `pass 30 / fail 0` |
| 2 | `firebase deploy --only firestore:rules --project sprena-a9b55` (F.3) | Console → Firestore → Rules: conteúdo e data do deploy conferem |
| 3 | Validar em device com o **build novo**, ainda não distribuído | login entra na Home; aceite grava `user_consents/{uid}` e um doc em `history/` |
| 4 | Só então publicar/distribuir o APK | — |

- [ ] Rules publicadas **antes** da distribuição do app
- [ ] Um usuário existente (que já aceitou) entra na Home sem passar pelo gate
- [ ] Um usuário novo aceita e o aceite grava sem erro

**Se a ordem foi invertida e os usuários já estão travados:** publicar as rules (passo 2) resolve na
hora, sem rollback de APK e sem ação do usuário — o `Retry` da tela de consentimento reconsulta o
aceite e libera quem já tinha aceitado. Rollback das rules (F.4) é o caminho contrário e **não**
ajuda aqui.

> A ordem inversa (app antes das rules) é segura só quando a versão nova **não** lê nem grava em
> coleção alguma que ainda não esteja liberada. Na dúvida, trate como bloqueante.

### Troubleshooting do deploy

| Erro | Causa | Correção |
|---|---|---|
| `Failed to get Firebase project sprena-a9b55` | logado com a conta Google errada | `firebase logout` e refazer F.1 |
| `Missing permissions required for functions deploy` | conta sem papel de Editor/Owner no projeto | pedir acesso ao dono do projeto |
| `Unable to authenticate using the provided code` | passou o *session ID* em vez do código de autorização | refazer F.1 num terminal interativo |
| compilação falhou | erro de sintaxe nas rules | rodar F.2 — o emulador aponta linha e coluna |

---

## Parte G — Ativar o App Check (F1.4b)

O app já instala o provider de atestação no `onCreate` — isso é código, e está feito. O que esta
parte cobre é o lado do Console, que é onde o App Check de fato passa a valer.

**Ordem importa.** Registrar o token de debug *antes* de ligar a enforcement; ligar a enforcement
*depois* de as métricas mostrarem tráfego verificado. Invertido, você derruba o seu próprio login.

### G.1 — Registrar o token de debug (uma vez por máquina/emulador)

Sem isso, todo build debug feito num clone novo falha a atestação. É o primeiro tropeço garantido.

> **Quando fazer:** enquanto a enforcement estiver desligada (antes de G.4), o app funciona sem
> token registrado — o backend ainda aceita request não verificado. Registrar **antes** de ligar a
> chave é justamente o que evita você derrubar o próprio login no passo seguinte.

Precisa de um device ou emulador **com Google Play Services** conectado (`adb devices` tem que
listar). O código do App Check vive em `composeApp/src/androidDebug` e `src/androidMain` — se a
branch em uso não tiver o `AppCheckBootstrap`, nenhum UUID vai aparecer.

```bash
./gradlew :composeApp:installDebug
adb logcat -c
adb logcat -s DebugAppCheckProvider
```

No PowerShell (ver [Pré-requisitos](#pré-requisitos) sobre o caminho do `adb`):

```powershell
./gradlew :composeApp:installDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -c
& $adb logcat -s DebugAppCheckProvider
```

Abrir o app. O logcat imprime uma linha assim:

```
Enter this debug secret into the allow list in the Firebase Console for your project: 123e4567-e89b-12d3-a456-426614174000
```

Console → **App Check** → aba **Apps** → app Android → menu ⋮ → **Gerenciar tokens de depuração** →
**Adicionar token** → colar o UUID → nomear (ex.: `pc-pedro-emulador`) → salvar.

- O UUID é **por instalação**: reinstalar o app, limpar dados ou trocar de emulador gera outro.
- Ele vale como credencial. Não colar em issue, PR ou screenshot — mesma regra do `test-users.local.md`.

### G.2 — Habilitar Play Integrity (release)

1. Console → **App Check** → **Apps** → app Android → **Play Integrity** → **Registrar**.
2. Conferir que o **SHA-256 da chave de assinatura de release** está em Configurações do projeto →
   Seus apps → app Android. Se a Play Store faz o *app signing*, o SHA que vale é o da chave de
   **assinatura do app** no Play Console (Configuração → Integridade do app), **não** o da chave de
   upload — trocar os dois é o erro clássico, e a atestação falha 100% em produção.
3. Google Cloud Console → APIs e serviços → habilitar **Play Integrity API** no projeto vinculado.

> Enquanto `signingConfig` do release for a chave de debug (F1.1, ver `composeApp/build.gradle.kts`),
> o build de release **não** atesta com sucesso. Isso se resolve junto com a signing config real.

### G.3 — Observar antes de ligar

Console → **App Check** → aba **APIs** → **Cloud Firestore** e **Authentication**. Cada um mostra a
divisão entre requests verificados e não verificados nas últimas 24h.

Só siga para G.4 quando a fatia verificada estiver perto de 100%. Se ainda houver tráfego não
verificado, é build antigo em uso ou token de debug não registrado — ligar agora derruba esses.

### G.4 — Ligar a enforcement

Na mesma aba, por produto: **Cloud Firestore** → **Aplicar**. Depois **Authentication** → **Aplicar**.

Um de cada vez, validando o login em device (Parte C) entre os dois. A propagação leva alguns
minutos.

- [ ] Login em device funciona com o build debug (token registrado em G.1)
- [ ] `adb logcat -s AppCheck` sem erro de atestação
- [ ] Métricas seguem em ~100% verificado depois de ligar

### G.5 — Reverter

Mesma tela → **Desaplicar**. Efeito em minutos. É a saída se a enforcement derrubar usuários — não
tem por que sofrer com rollback de APK.

### Troubleshooting do App Check

| Sintoma | Causa provável | Correção |
|---|---|---|
| App mostra "Não foi possível validar o app neste dispositivo" | enforcement ligada e atestação recusada | G.1 (debug) ou G.2 (release) |
| Login falha só depois de G.4, e voltava ao desaplicar | token de debug não registrado nessa instalação | refazer G.1 — o UUID muda por instalação |
| `code=UNAUTHENTICATED` no logcat | token de App Check ausente/inválido | distinto de `PERMISSION_DENIED`, que é rules (F1.4) |
| Nenhum UUID aparece no logcat | build release, branch sem o `AppCheckBootstrap`, ou nada conectado | conferir `adb devices`, que foi `installDebug` e que a tag é `DebugAppCheckProvider` |
| `adb` não é reconhecido como comando | não está no PATH (padrão no Windows) | ver [Pré-requisitos](#pré-requisitos) |
| Release falha atestação, debug funciona | SHA-256 errado (upload vs app signing) | G.2 passo 2 |
| `Integrity API error (-1)` | Play Integrity API não habilitada no Cloud | G.2 passo 3 |

---

## Registro local

Guardar os dados reais (email, senha, UID) em **`docs/ops/test-users.local.md`** — gitignorado via
`*.local.md`. Se o arquivo não existir no seu clone, crie com este conteúdo:

```markdown
# Usuários de teste — LOCAL (não commitar)

> Gitignorado via `*.local.md` no `.gitignore` da raiz.
> Nunca colar em issue, PR, chat ou screenshot.

| Email | Senha | UID | role | name | Criado em |
|---|---|---|---|---|---|
|  |  |  | ADM |  |  |
```

Confirmar que está ignorado antes de commitar qualquer coisa:

```bash
git check-ignore -v docs/ops/test-users.local.md   # deve casar com a regra
git status --short                                  # o arquivo NÃO pode aparecer
```

---

## Referências

- [SECURITY.md § F1.3](../../SECURITY.md#f13--firebase-auth--sessão-criptografada) — decisões e trade-offs
- [SECURITY.md § F1.4b](../../SECURITY.md#f14b--firebase-app-check-play-integrity) — por que o provider é escolhido por build type
- `composeApp/src/androidMain/.../core/security/AppCheckBootstrap.kt` — instalação do App Check
- `shared/src/androidMain/.../auth/data/repository/FirebaseAuthRepositoryImpl.kt` — leitura do doc e mapa de erros
- `shared/src/commonMain/.../auth/domain/model/UserRole.kt` — enum das roles
- `shared/src/commonMain/.../auth/domain/usecase/RestoreSessionUseCase.kt` — regra do auto-login
