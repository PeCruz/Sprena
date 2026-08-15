# Runbook â€” Firebase: usuÃ¡rios, validaÃ§Ã£o em device, rules e App Check

> Sprena nÃ£o tem self-signup. **Todo** usuÃ¡rio Ã© criado Ã  mÃ£o: 1 registro no Firebase
> Authentication **e** 1 documento `users/{uid}` no Firestore. Faltando qualquer um dos dois,
> o login falha mesmo com a senha correta.
>
> Este documento Ã© versionado e **nÃ£o contÃ©m dado real**. Emails, senhas e UIDs dos usuÃ¡rios
> de teste vÃ£o em `docs/ops/test-users.local.md` (gitignorado) â€” ver [Registro local](#registro-local).

---

## PrÃ©-requisitos

- Acesso ao projeto **`sprena-a9b55`** no [Firebase Console](https://console.firebase.google.com/).
- `composeApp/google-services.json` presente localmente (gitignorado â€” ver [README](../../README.md#setup)).
- Device fÃ­sico ou emulador **com Google Play Services** (Firebase Auth nÃ£o funciona em imagem AOSP pura).
- `adb` acessÃ­vel (`adb devices` deve listar o aparelho).

> **Windows/PowerShell:** o `adb` normalmente **nÃ£o** estÃ¡ no PATH. Ele vem com o Android SDK em
> `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Os comandos deste runbook estÃ£o em sintaxe
> POSIX; no PowerShell, resolva o caminho uma vez por terminal e chame com o operador `&`:
>
> ```powershell
> $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
> & $adb devices
> ```
>
> Se nÃ£o estiver lÃ¡, procure com `Get-ChildItem "$env:LOCALAPPDATA\Android" -Recurse -Filter adb.exe`.
> Daqui em diante, onde se lÃª `adb`, use `& $adb`.

---

## Parte A â€” Criar o usuÃ¡rio no Authentication

1. Console â†’ **Build â†’ Authentication**.
2. Primeira vez apenas: aba **Sign-in method** â†’ habilitar **Email/Password**.
   NÃ£o habilitar Google Sign-In â€” estÃ¡ fora de escopo atÃ© F6.
3. Aba **Users** â†’ **Add user**.
4. Preencher:
   - **Email**: precisa casar com o regex de `LoginValidator` â€” `algo@dominio.tld`, sem espaÃ§os, mÃ¡x. 254 chars.
   - **Password**: mÃ­nimo **6 caracteres**, sem espaÃ§o no inÃ­cio ou fim (o app rejeita antes de chamar o Firebase).
5. **Add user** â†’ a linha aparece na tabela.
6. Copiar o **User UID** (coluna Ã  direita, Ã­cone de copiar). Ele Ã© o **document ID** da Parte B.

- [ ] UsuÃ¡rio aparece na aba Users
- [ ] UID copiado

---

## Parte B â€” Criar o documento `users/{uid}`

O app lÃª a role aqui **depois** que o Firebase Auth aceita a senha â€” Ã© o segundo portÃ£o de entrada.

1. Console â†’ **Build â†’ Firestore Database**.
2. ColeÃ§Ã£o **`users`** (nome exato, minÃºsculo). Se nÃ£o existir: **Start collection** â†’ Collection ID `users`.
3. **Add document** â†’ em **Document ID**, **colar o UID da Parte A**.
   âš ï¸ **Nunca clicar em "Auto-ID"** â€” um doc com ID aleatÃ³rio Ã© invisÃ­vel para o app e o login cai em
   "Conta nÃ£o autorizada".
4. Adicionar os campos (todos do tipo `string`):

| Campo   | Tipo   | ObrigatÃ³rio | Valor                        |
|---------|--------|-------------|------------------------------|
| `role`  | string | **sim**     | `ADM` \| `MOD` \| `CLIENT`   |
| `name`  | string | nÃ£o         | Nome de exibiÃ§Ã£o             |
| `email` | string | nÃ£o         | Mesmo email do Auth          |

5. **Save**.

### O que o app realmente lÃª

Fonte: `shared/src/androidMain/kotlin/br/com/sprena/shared/auth/data/repository/FirebaseAuthRepositoryImpl.kt`

- **`role`** â€” obrigatÃ³rio. Parseado com `UserRole.valueOf(valor.uppercase())`, entÃ£o `adm`, `Adm` e `ADM`
  funcionam, mas **`admin`, `ADMIN`, `Administrador`, `USER` derrubam o login** com "Conta sem perfil vÃ¡lido".
  Valores vÃ¡lidos e o que aparecem na UI:

  | `role`   | ExibiÃ§Ã£o        |
  |----------|-----------------|
  | `ADM`    | Administrador   |
  | `MOD`    | Moderador       |
  | `CLIENT` | FuncionÃ¡rio     |

- **`name`** â€” opcional. Ausente â†’ o app usa o trecho antes do `@` do email.
- **`email`** â€” **nÃ£o Ã© lido em runtime**. O `UserModel.email` vem do que o usuÃ¡rio digitou no login.
  O campo existe sÃ³ para o doc ser legÃ­vel no Console. Escrever um email errado aqui **nÃ£o** quebra nada
  (e por isso tambÃ©m nÃ£o protege nada â€” nÃ£o confie nele).

- [ ] Document ID Ã© exatamente o UID (nÃ£o Auto-ID)
- [ ] `role` Ã© um de `ADM` / `MOD` / `CLIENT`
- [ ] Doc salvo e visÃ­vel em `users/{uid}`

---

## Parte C â€” ValidaÃ§Ã£o manual em device

### Comandos

```bash
# instalar o debug build (applicationId br.com.sprena)
./gradlew :composeApp:installDebug

# limpar buffer de log antes de cada cenÃ¡rio
adb logcat -c

# acompanhar sÃ³ as tags de auth/sessÃ£o
adb logcat -s FirebaseAuthRepo LoginUseCase RestoreSession LogoutUseCase EncryptedSessionStore

# estado limpo: apaga a sessÃ£o criptografada + o cache local do Firebase Auth
adb shell pm clear br.com.sprena
```

> Logs sÃ³ existem em **debug** â€” em release o Napier nÃ£o planta antilog e vira no-op.

### Checklist

- [ ] **Login vÃ¡lido** â†’ vai pra Home. Log: `FirebaseAuthRepo login ok uid=â€¦ email=p***@dominio`
- [ ] **Senha errada** â†’ "Email ou senha incorretos"
- [ ] **Email sem doc `users/{uid}`** â†’ "Conta nÃ£o autorizada. Contate o administrador."
      Log: `user doc missing email=â€¦ uid=â€¦`
- [ ] **`role` invÃ¡lida** (trocar para `admin` no Console e tentar) â†’ "Conta sem perfil vÃ¡lido".
      Log: `user doc has invalid role uid=â€¦ raw=admin`
- [ ] **Sem internet** (modo aviÃ£o) â†’ "Sem conexÃ£o. Verifique a internet"
- [ ] **Auto-login**: matar o app (swipe do recentes) e reabrir dentro de 24h â†’ entra direto na Home,
      sem passar pelo Login. Log da tag `RestoreSession`
- [ ] **ExpiraÃ§Ã£o (TTL 24h)**: Ajustes do Android â†’ Data e hora â†’ desligar automÃ¡tico â†’ avanÃ§ar 2 dias â†’
      reabrir o app â†’ volta pro **Login**. *(Voltar o relÃ³gio tambÃ©m expira â€” skew conta como expirado.)*
- [ ] **Logout**: no app, Ajustes â†’ seÃ§Ã£o "Conta" â†’ **Sair** â†’ volta pro Login; reabrir nÃ£o faz auto-login
- [ ] **Sem PII em claro**: varrer o logcat â€” nenhuma senha, nenhum email completo
      (o esperado Ã© sempre a forma mascarada `p***@dominio`)
- [ ] **SessÃ£o em disco ilegÃ­vel**: `/data/data/br.com.sprena/files/datastore/session_prefs.preferences_pb`
      nÃ£o contÃ©m o email/uid em texto (AES-256-GCM via Tink). Em device nÃ£o-rooteado, dÃ¡ pra inspecionar
      sÃ³ com build debuggable: `adb shell run-as br.com.sprena cat files/datastore/session_prefs.preferences_pb`

### Notas de ambiente

- **`FLAG_SECURE` estÃ¡ ativo** em toda a `MainActivity`: `adb exec-out screencap` e gravaÃ§Ã£o de tela saem
  **pretos**. Isso Ã© o comportamento correto (F1.1), nÃ£o um bug. Para registrar evidÃªncia, fotografe a tela.
- `adb shell pm clear` derruba tambÃ©m o keyset do Tink â€” a sessÃ£o anterior fica irrecuperÃ¡vel por design.

---

## Parte D â€” Troubleshooting

| Mensagem na tela | Causa provÃ¡vel | CorreÃ§Ã£o |
|---|---|---|
| "Email invÃ¡lido" | formato do email rejeitado pelo Firebase (`ERROR_INVALID_EMAIL`) | conferir digitaÃ§Ã£o |
| "Email ou senha incorretos" | senha errada, usuÃ¡rio inexistente ou credencial invÃ¡lida (mensagem Ãºnica, anti-enumeraÃ§Ã£o) | resetar senha na Parte E |
| "Conta desativada. Contate o administrador" | usuÃ¡rio marcado como **Disabled** no Console | Authentication â†’ Users â†’ menu â‹® â†’ Enable account |
| "Muitas tentativas. Tente em alguns minutos" | rate limit do Firebase apÃ³s vÃ¡rias falhas | esperar ~5 min ou testar de outra rede |
| "Sem conexÃ£o. Verifique a internet" | device offline / sem rota pro Firebase | conferir Wi-Fi, modo aviÃ£o |
| "Falha inesperada na autenticaÃ§Ã£o" | Auth retornou sucesso sem `uid` (raro) | repetir; se persistir, checar `google-services.json` e o projeto Firebase |
| **"Conta nÃ£o autorizada. Contate o administrador."** | doc `users/{uid}` **nÃ£o existe** â€” quase sempre Auto-ID em vez do UID | refazer a Parte B com o Document ID correto |
| **"Conta sem perfil vÃ¡lido"** | `role` ausente, com typo, ou fora de `ADM`/`MOD`/`CLIENT` | corrigir o campo `role` no Console |
| **"Conta sem permissÃ£o de acesso. Contate o administrador"** | as Security Rules negaram a leitura de `users/{uid}` | ver bloco abaixo |
| "Erro ao carregar seu perfil" | outra falha do Firestore na leitura do perfil | ver logcat: `FirebaseAuthRepo â€¦ code=<CODE>` |
| "Erro de autenticaÃ§Ã£o" (genÃ©rico) | qualquer outra exceÃ§Ã£o | ver logcat: a tag `FirebaseAuthRepo` traz `cause=<NomeDaExceÃ§Ã£o>` |

**`PERMISSION_DENIED` depois de a senha ser aceita** â€” o Auth aprovou, o Firestore recusou. Ou seja:
autenticado no Firebase, mas sem sessÃ£o no app. Checar, nesta ordem:

1. As rules foram publicadas neste projeto? `firebase deploy --only firestore:rules --project <projeto>`
   â€” o Console mostra a data do Ãºltimo deploy em Firestore Database â†’ Rules.
2. O Document ID do doc de perfil bate **exatamente** com o UID do Auth? A regra Ã©
   `request.auth.uid == uid`; com Auto-ID ela nega mesmo com as rules corretas.
3. Logcat confirma a origem: `FirebaseAuthRepo login failed â€¦ cause=FirebaseFirestoreException code=PERMISSION_DENIED`.

O modelo de acesso completo estÃ¡ em [SECURITY.md](../../SECURITY.md) (seÃ§Ã£o F1.4); as regras, em
`firestore.rules` na raiz. Mexeu nelas? Rode `npm run test:emulator` em `tools/firestore-rules-tests/`
antes do deploy.

**Nome errado na Home apÃ³s auto-login:** esperado. O fluxo de restore deriva o nome do email
(`NavGraph.kt`); o campo `name` do Firestore sÃ³ aparece no login "fresco". NÃ£o Ã© regressÃ£o.

**Mudei a `role` e nada mudou:** a role fica gravada na sessÃ£o criptografada local. Exige **Sair** e logar
de novo (ou `adb shell pm clear br.com.sprena`).

---

## Parte E â€” ManutenÃ§Ã£o

- **Resetar senha** â€” Console â†’ Authentication â†’ Users â†’ menu â‹® â†’ *Reset password* (envia email),
  ou pelo prÃ³prio app em "Esqueci a senha" no `LoginScreen`.
- **Desativar acesso temporariamente** â€” menu â‹® â†’ *Disable account*. O usuÃ¡rio passa a ver
  "Conta desativada. Contate o administrador". ReversÃ­vel, preserva o histÃ³rico.
- **Trocar a role** â€” editar o campo `role` no doc `users/{uid}`. **Exige novo login** (ver armadilha acima).
- **Remover um usuÃ¡rio** â€” apagar **os dois**: o registro no Authentication **e** o doc `users/{uid}`.
  - SÃ³ o Auth â†’ sobra doc Ã³rfÃ£o no Firestore.
  - SÃ³ o doc â†’ o usuÃ¡rio ainda autentica, mas trava em "Conta nÃ£o autorizada".
- **Device novo / reinstalaÃ§Ã£o** â€” nada a fazer no Console. Basta logar; a sessÃ£o Ã© recriada localmente.

- [ ] ApÃ³s qualquer mudanÃ§a de role ou remoÃ§Ã£o, revalidar com a Parte C

---

## Parte F â€” Publicar as Security Rules

As regras vivem em **`firestore.rules`** na raiz do repo. Editar o arquivo nÃ£o muda nada em
produÃ§Ã£o â€” sÃ³ o deploy publica. E o contrÃ¡rio tambÃ©m vale: publicar sem rodar os testes Ã© como
mergear sem CI.

### F.1 â€” Autorizar o CLI (uma vez por mÃ¡quina)

```bash
npm install -g firebase-tools   # se ainda nÃ£o tiver
firebase login
```

Rode o `firebase login` num **terminal interativo de verdade** â€” ele abre o navegador e fecha o
ciclo sozinho pelo localhost. Em ambiente headless (incluindo agentes de IA) ele cai num fluxo de
cÃ³digo manual: imprime um *session ID* + URL, e espera o **cÃ³digo de autorizaÃ§Ã£o** que a pÃ¡gina
devolve **depois** do login. Session ID e cÃ³digo de autorizaÃ§Ã£o sÃ£o coisas diferentes â€” passar o
session ID falha com "Unable to authenticate using the provided code".

Logar com a conta que Ã© dona do projeto. Conferir:

```bash
firebase login:list
firebase projects:list          # sprena-a9b55 tem que aparecer
```

### F.2 â€” Testar antes de publicar

```bash
npm --prefix tools/firestore-rules-tests install    # primeira vez
npm --prefix tools/firestore-rules-tests run test:emulator
```

Roda a suÃ­te contra o emulador local no projeto `demo-sprena` â€” offline, sem tocar em nada real.
Esperado: `fail 0`. Falhou? NÃ£o publique. (O total de casos cresce a cada fase â€” o que importa Ã©
que nenhum falhe, nÃ£o o nÃºmero.)

> Nas negaÃ§Ãµes, o emulador loga `evaluation error at L<n>` seguido de `false` na mesma linha.
> Ã‰ o motor reavaliando depois de resolver o `get()` â€” a decisÃ£o que vale Ã© a segunda. NÃ£o Ã© bug.

### F.3 â€” Publicar

Da **raiz do repo** (Ã© onde estÃ£o `firebase.json` e `firestore.rules`):

```bash
firebase deploy --only firestore:rules --project sprena-a9b55
```

O `--project` Ã© obrigatÃ³rio: o `.firebaserc` Ã© gitignorado de propÃ³sito, mesma postura do
`google-services.json`.

SaÃ­da esperada:

```
+  cloud.firestore: rules file firestore.rules compiled successfully
+  firestore: released rules firestore.rules to cloud.firestore
+  Deploy complete!
```

- [ ] Console â†’ Firestore Database â†’ aba **Rules**: conteÃºdo bate com o arquivo e a data do Ãºltimo
      deploy Ã© de agora
- [ ] Login em device continua funcionando (Parte C)

### F.4 â€” Reverter

Console â†’ Firestore Database â†’ **Rules** â†’ histÃ³rico de versÃµes â†’ selecionar a anterior â†’
**Restore**. O Firebase versiona cada deploy; a volta Ã© um clique.

### F.5 â€” Ordem de release: rules primeiro, app depois

> âš ï¸ **Bloqueante de release.** Uma versÃ£o do app que estreia uma coleÃ§Ã£o nova sÃ³ pode chegar aos
> usuÃ¡rios **depois** de as rules dessa coleÃ§Ã£o estarem publicadas. Inverter a ordem tira **todos os
> usuÃ¡rios existentes** do ar â€” nÃ£o Ã© degradaÃ§Ã£o parcial, Ã© bloqueio total.

O caso concreto Ã© o gate de consentimento (F1.5). Se o APK com o gate for distribuÃ­do antes do
deploy das rules:

1. o app lÃª `user_consents/{uid}` e bate no `match /{document=**} { allow read, write: if false }`;
2. a leitura falha com `PERMISSION_DENIED` â†’ `CheckConsentUseCase` devolve `ConsentStatus.Unavailable`;
3. o gate Ã© **fail-closed**: `Unavailable` nunca vira acesso, entÃ£o o usuÃ¡rio cai na tela de
   consentimento;
4. o aceite tambÃ©m Ã© negado pela mesma razÃ£o â€” a gravaÃ§Ã£o bate no default-deny.

Resultado: **todo usuÃ¡rio jÃ¡ cadastrado fica sem acesso ao app**, inclusive quem nunca teve nada a
ver com a mudanÃ§a, e sem nenhum caminho para frente dentro do app. NÃ£o Ã© "um retry falhou".

**Ordem obrigatÃ³ria de cada release que toca em `firestore.rules`:**

| # | Passo | Como validar antes de seguir |
|---|---|---|
| 1 | Rodar a suÃ­te de rules (F.2) | `fail 0` |
| 2 | `firebase deploy --only firestore:rules --project sprena-a9b55` (F.3) | Console â†’ Firestore â†’ Rules: conteÃºdo e data do deploy conferem |
| 3 | Validar em device com o **build novo**, ainda nÃ£o distribuÃ­do | login entra na Home; aceite grava `user_consents/{uid}` e um doc em `history/` |
| 4 | SÃ³ entÃ£o publicar/distribuir o APK | â€” |

- [ ] Rules publicadas **antes** da distribuiÃ§Ã£o do app
- [ ] Um usuÃ¡rio existente (que jÃ¡ aceitou) entra na Home sem passar pelo gate
- [ ] Um usuÃ¡rio novo aceita e o aceite grava sem erro

**Se a ordem foi invertida e os usuÃ¡rios jÃ¡ estÃ£o travados:** publicar as rules (passo 2) resolve na
hora, sem rollback de APK e sem aÃ§Ã£o do usuÃ¡rio â€” o `Retry` da tela de consentimento reconsulta o
aceite e libera quem jÃ¡ tinha aceitado. Rollback das rules (F.4) Ã© o caminho contrÃ¡rio e **nÃ£o**
ajuda aqui.

> A ordem inversa (app antes das rules) Ã© segura sÃ³ quando a versÃ£o nova **nÃ£o** lÃª nem grava em
> coleÃ§Ã£o alguma que ainda nÃ£o esteja liberada. Na dÃºvida, trate como bloqueante.

### Troubleshooting do deploy

| Erro | Causa | CorreÃ§Ã£o |
|---|---|---|
| `Failed to get Firebase project sprena-a9b55` | logado com a conta Google errada | `firebase logout` e refazer F.1 |
| `Missing permissions required for functions deploy` | conta sem papel de Editor/Owner no projeto | pedir acesso ao dono do projeto |
| `Unable to authenticate using the provided code` | passou o *session ID* em vez do cÃ³digo de autorizaÃ§Ã£o | refazer F.1 num terminal interativo |
| compilaÃ§Ã£o falhou | erro de sintaxe nas rules | rodar F.2 â€” o emulador aponta linha e coluna |

---

## Parte G â€” Ativar o App Check (F1.4b)

O app jÃ¡ instala o provider de atestaÃ§Ã£o no `onCreate` â€” isso Ã© cÃ³digo, e estÃ¡ feito. O que esta
parte cobre Ã© o lado do Console, que Ã© onde o App Check de fato passa a valer.

**Ordem importa.** Registrar o token de debug *antes* de ligar a enforcement; ligar a enforcement
*depois* de as mÃ©tricas mostrarem trÃ¡fego verificado. Invertido, vocÃª derruba o seu prÃ³prio login.

### G.1 â€” Registrar o token de debug (uma vez por mÃ¡quina/emulador)

Sem isso, todo build debug feito num clone novo falha a atestaÃ§Ã£o. Ã‰ o primeiro tropeÃ§o garantido.

> **Quando fazer:** enquanto a enforcement estiver desligada (antes de G.4), o app funciona sem
> token registrado â€” o backend ainda aceita request nÃ£o verificado. Registrar **antes** de ligar a
> chave Ã© justamente o que evita vocÃª derrubar o prÃ³prio login no passo seguinte.

Precisa de um device ou emulador **com Google Play Services** conectado (`adb devices` tem que
listar). O cÃ³digo do App Check vive em `composeApp/src/androidDebug` e `src/androidMain` â€” se a
branch em uso nÃ£o tiver o `AppCheckBootstrap`, nenhum UUID vai aparecer.

```bash
./gradlew :composeApp:installDebug
adb logcat -c
adb logcat -s DebugAppCheckProvider
```

No PowerShell (ver [PrÃ©-requisitos](#prÃ©-requisitos) sobre o caminho do `adb`):

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

Console â†’ **App Check** â†’ aba **Apps** â†’ app Android â†’ menu â‹® â†’ **Gerenciar tokens de depuraÃ§Ã£o** â†’
**Adicionar token** â†’ colar o UUID â†’ nomear (ex.: `pc-pedro-emulador`) â†’ salvar.

- O UUID Ã© **por instalaÃ§Ã£o**: reinstalar o app, limpar dados ou trocar de emulador gera outro.
- Ele vale como credencial. NÃ£o colar em issue, PR ou screenshot â€” mesma regra do `test-users.local.md`.

### G.2 â€” Habilitar Play Integrity (release)

1. Console â†’ **App Check** â†’ **Apps** â†’ app Android â†’ **Play Integrity** â†’ **Registrar**.
2. Conferir que o **SHA-256 da chave de assinatura de release** estÃ¡ em ConfiguraÃ§Ãµes do projeto â†’
   Seus apps â†’ app Android. Se a Play Store faz o *app signing*, o SHA que vale Ã© o da chave de
   **assinatura do app** no Play Console (ConfiguraÃ§Ã£o â†’ Integridade do app), **nÃ£o** o da chave de
   upload â€” trocar os dois Ã© o erro clÃ¡ssico, e a atestaÃ§Ã£o falha 100% em produÃ§Ã£o.
3. Google Cloud Console â†’ APIs e serviÃ§os â†’ habilitar **Play Integrity API** no projeto vinculado.

> Enquanto `signingConfig` do release for a chave de debug (F1.1, ver `composeApp/build.gradle.kts`),
> o build de release **nÃ£o** atesta com sucesso. Isso se resolve junto com a signing config real.

### G.3 â€” Observar antes de ligar

Console â†’ **App Check** â†’ aba **APIs** â†’ **Cloud Firestore** e **Authentication**. Cada um mostra a
divisÃ£o entre requests verificados e nÃ£o verificados nas Ãºltimas 24h.

SÃ³ siga para G.4 quando a fatia verificada estiver perto de 100%. Se ainda houver trÃ¡fego nÃ£o
verificado, Ã© build antigo em uso ou token de debug nÃ£o registrado â€” ligar agora derruba esses.

### G.4 â€” Ligar a enforcement

Na mesma aba, por produto: **Cloud Firestore** â†’ **Aplicar**. Depois **Authentication** â†’ **Aplicar**.

Um de cada vez, validando o login em device (Parte C) entre os dois. A propagaÃ§Ã£o leva alguns
minutos.

- [ ] Login em device funciona com o build debug (token registrado em G.1)
- [ ] `adb logcat -s AppCheck` sem erro de atestaÃ§Ã£o
- [ ] MÃ©tricas seguem em ~100% verificado depois de ligar

### G.5 â€” Reverter

Mesma tela â†’ **Desaplicar**. Efeito em minutos. Ã‰ a saÃ­da se a enforcement derrubar usuÃ¡rios â€” nÃ£o
tem por que sofrer com rollback de APK.

### Troubleshooting do App Check

| Sintoma | Causa provÃ¡vel | CorreÃ§Ã£o |
|---|---|---|
| App mostra "NÃ£o foi possÃ­vel validar o app neste dispositivo" | enforcement ligada e atestaÃ§Ã£o recusada | G.1 (debug) ou G.2 (release) |
| Login falha sÃ³ depois de G.4, e voltava ao desaplicar | token de debug nÃ£o registrado nessa instalaÃ§Ã£o | refazer G.1 â€” o UUID muda por instalaÃ§Ã£o |
| `code=UNAUTHENTICATED` no logcat | token de App Check ausente/invÃ¡lido | distinto de `PERMISSION_DENIED`, que Ã© rules (F1.4) |
| Nenhum UUID aparece no logcat | build release, branch sem o `AppCheckBootstrap`, ou nada conectado | conferir `adb devices`, que foi `installDebug` e que a tag Ã© `DebugAppCheckProvider` |
| `adb` nÃ£o Ã© reconhecido como comando | nÃ£o estÃ¡ no PATH (padrÃ£o no Windows) | ver [PrÃ©-requisitos](#prÃ©-requisitos) |
| Release falha atestaÃ§Ã£o, debug funciona | SHA-256 errado (upload vs app signing) | G.2 passo 2 |
| `Integrity API error (-1)` | Play Integrity API nÃ£o habilitada no Cloud | G.2 passo 3 |

---


---

## Parte H â€” Cloud Functions: exclusÃ£o de conta (F1.6a)

A exclusÃ£o de conta Ã© a Ãºnica operaÃ§Ã£o do app que precisa do Admin SDK: cascade delete e remoÃ§Ã£o do
usuÃ¡rio do Firebase Auth nÃ£o podem sair do cliente. Ela vive em `functions/` e Ã© publicada separada
do APK.

### H.1 â€” PrÃ©-requisito: plano Blaze

Cloud Functions **nÃ£o deploya no plano Spark** â€” nÃ£o Ã© degradado, Ã© impossÃ­vel.

Firebase Console â†’ âš™ï¸ â†’ *Uso e faturamento* â†’ *Detalhes e configuraÃ§Ãµes* â†’ *Modificar plano* â†’
Blaze. O free tier (2 milhÃµes de invocaÃ§Ãµes/mÃªs) cobre folgadamente esta carga: uma invocaÃ§Ã£o por
conta excluÃ­da.

O custo que costuma escapar nÃ£o Ã© execuÃ§Ã£o, Ã© o **Artifact Registry** acumulando imagens de
container a cada deploy. Defina a polÃ­tica de limpeza na mesma sessÃ£o do primeiro deploy:

```bash
firebase functions:artifacts:setpolicy --project <projeto>
```

### H.2 â€” Instalar e compilar

```bash
cd functions
npm ci
npm run build
```

`npm run build` Ã© sÃ³ `tsc`. Ele tambÃ©m roda no `predeploy` do `firebase.json`, mas rodar aqui evita
descobrir um erro de compilaÃ§Ã£o no meio do deploy.

### H.3 â€” Testar no emulador

```bash
cd functions
npm run test:emulator
```

Sobe Auth, Firestore e Functions no projeto `demo-sprena` â€” offline, sem credencial e sem
`firebase login`. Esperado: `fail 0`. Falhou? NÃ£o publique.

> **O emulador nÃ£o valida o App Check.** A funÃ§Ã£o desliga `enforceAppCheck` sob `FUNCTIONS_EMULATOR`
> (senÃ£o toda chamada da suÃ­te viraria `unauthenticated`). Em produÃ§Ã£o a enforcement vale sempre â€” e
> Ã© por isso que o passo H.6 existe.

### H.4 â€” Publicar

```bash
firebase deploy --only functions:deleteMyAccount --project <projeto>
```

A primeira execuÃ§Ã£o habilita as APIs `cloudfunctions`, `cloudbuild`, `artifactregistry`, `run` e
`eventarc`. Pode levar vÃ¡rios minutos e exige que a conta tenha papel de Owner no projeto.

### H.5 â€” Conferir regiÃ£o e App Check

Console â†’ *Functions*. A funÃ§Ã£o precisa aparecer como `deleteMyAccount` na regiÃ£o
**`southamerica-east1`**.

âš ï¸ A regiÃ£o estÃ¡ declarada nos **dois** lados e precisa bater:

| Lado | Arquivo | Constante |
|---|---|---|
| Backend | `functions/src/index.ts` | `FUNCTIONS_REGION` |
| Cliente | `composeApp/src/androidMain/.../di/PlatformModule.android.kt` | `FUNCTIONS_REGION` |

DivergÃªncia devolve `NOT_FOUND` no app, **indistinguÃ­vel de "funÃ§Ã£o nÃ£o deployada"** â€” o sintoma nÃ£o
aponta para a causa. Se o botÃ£o de excluir falhar com "ServiÃ§o de exclusÃ£o indisponÃ­vel", confira a
regiÃ£o antes de qualquer outra coisa.

Para validar em device com build debug, o token de App Check precisa estar registrado (Parte G.1),
senÃ£o a chamada volta `unauthenticated` e parece bug.

### H.6 â€” Validar em device com conta de teste

Crie uma conta descartÃ¡vel (Partes A e B), entre no app e:

1. Aba **Perfil** â†’ preencher apelido, CPF, telefone e modalidades â†’ *Salvar*
2. Console â†’ `user_profiles/{uid}` existe com os campos
3. *Exportar meus dados* â†’ o share sheet abre e o JSON tem os dados **sem mÃ¡scara**
4. *Excluir conta* â†’ digitar `EXCLUIR` â†’ *Excluir definitivamente*
5. Console, conferir que sumiram: `users/{uid}`, `user_profiles/{uid}`, `user_consents/{uid}` e toda
   a subcoleÃ§Ã£o `history`
6. Console â†’ `account_deletions/{uid}` **existe**, e **nÃ£o** contÃ©m e-mail, nome nem CPF
7. Authentication â†’ o usuÃ¡rio nÃ£o estÃ¡ mais na lista
8. **Reabrir o app** â†’ cai no Login, e nÃ£o na tela de consentimento

O passo 8 Ã© o roteiro que a review da Play executa. Se ele cair no consentimento, a correÃ§Ã£o de
`refreshToken` regrediu (ver SECURITY.md Â§ F1.6a).

### H.7 â€” Recuperar um uid Ã³rfÃ£o

Se a funÃ§Ã£o falhar no Ãºltimo passo, os dados somem mas o usuÃ¡rio do Auth sobrevive. O sintoma Ã© o
login parar em "Conta nÃ£o autorizada. Contate o administrador", e `account_deletions/{uid}` existir
com o usuÃ¡rio ainda presente em Authentication.

A funÃ§Ã£o Ã© idempotente: delete de doc inexistente Ã© no-op e `auth/user-not-found` conta como
sucesso. O caminho mais simples Ã© apagar o usuÃ¡rio direto no Console â†’ *Authentication* â†’
â‹® â†’ *Excluir conta*. A trilha em `account_deletions` jÃ¡ registra que a exclusÃ£o foi pedida.

### H.8 â€” Ordem de release: funÃ§Ã£o antes do APK (bloqueante)

| # | Passo | ValidaÃ§Ã£o antes de seguir |
|---|---|---|
| 1 | `cd functions && npm run test:emulator` | `fail 0` |
| 2 | `firebase deploy --only firestore:rules` (Parte F) | Console â†’ Rules confere |
| 3 | `firebase deploy --only functions:deleteMyAccount` | funÃ§Ã£o listada, regiÃ£o correta |
| 4 | Validar em device com o build novo, **ainda nÃ£o distribuÃ­do** | checklist H.6 inteira |
| 5 | SÃ³ entÃ£o publicar o APK | â€” |

O modo de falha aqui Ã© diferente do de F.5. Rules na ordem invertida travam **todos** os usuÃ¡rios;
a funÃ§Ã£o na ordem invertida degrada **sÃ³** o botÃ£o de excluir conta. Ainda assim Ã© bloqueante: Ã©
justamente esse botÃ£o que a review da Play vai testar, e ele Ã© o motivo de a fase existir.

### Troubleshooting

| Sintoma | Causa provÃ¡vel | O que fazer |
|---|---|---|
| `Error: HTTP Error: 400 ... billing account` no deploy | projeto ainda no Spark | H.1 |
| Deploy trava em "enabling APIs" | primeira publicaÃ§Ã£o do projeto | aguardar; se falhar, rodar de novo |
| App: "ServiÃ§o de exclusÃ£o indisponÃ­vel" | funÃ§Ã£o nÃ£o deployada **ou** regiÃ£o divergente | H.5 |
| App: "NÃ£o foi possÃ­vel validar o aplicativo" | App Check recusou a instalaÃ§Ã£o | Parte G.1 (debug) ou G.4 (release) |
| App: "Sua sessÃ£o expirou" ao excluir | token vencido, ou App Check em build debug sem token | relogar; conferir G.1 |
| Emulador: toda chamada volta `unauthenticated` | `FUNCTIONS_EMULATOR` nÃ£o chegou na funÃ§Ã£o | rodar via `npm run test:emulator`, nÃ£o `node --test` solto |
| Dados sumiram mas o usuÃ¡rio continua no Auth | falha no passo 8 | H.7 |

---

## Parte I — Estabelecimentos e vínculos (F1.7.1)

Enquanto as callables de vínculo não existem (F1.7.3), `establishments/{id}/members/{uid}`
é `write: if false` para **todo mundo, inclusive o ADM**. Isso é deliberado: a aresta que
concede acesso só é escrita pelo Admin SDK. Até lá, semear pelo Console é o caminho.

### I.1 — Publicar rules e índices juntos

```bash
firebase deploy --only firestore --project <projeto>
```

Note `--only firestore`, e não `--only firestore:rules`: o comando restrito publica só as
rules e deixa `firestore.indexes.json` para trás. O sintoma aparece depois, e só em
produção — a consulta de vínculos falha com `FAILED_PRECONDITION` e um link de criação de
índice no log. O emulador não reproduz isso porque cria índices sozinho.

As rules desta fase são puramente aditivas (nada existente foi afrouxado ou removido), então
não há a ordem bloqueante que F1.5 e F1.6a tiveram.

### I.2 — Criar um estabelecimento pelo Console

Firestore → coleção `establishments` → **Add document** → ID automático.

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `name` | string | sim | 1 a 80 caracteres |
| `active` | boolean | sim | `false` é como se "exclui" — não há delete |
| `cnpj` | string | sim | **só os 14 dígitos**, sem pontuação |
| `phone` | string | sim | **só dígitos**, 10 ou 11 |
| `email` | string | sim | até 120 caracteres |
| `razaoSocial` | string | não | até 120 |
| `address` | map | não | `street`, `number`, `complement`, `district`, `city`, `state`, `zipCode` |
| `updatedAt` | timestamp | sim | pelo app; no Console, qualquer timestamp serve |

CNPJ e telefone **precisam** estar sem pontuação. Com máscara a rule recusa a escrita do
app, e a unicidade de CNPJ deixa de funcionar: `11222333000181` e `11.222.333/0001-81` são
ids diferentes em `cnpj_index`.

Crie também `cnpj_index/{os14digitos}` com o campo `establishmentId` apontando para o id do
documento acima. É esse índice que impede o mesmo CNPJ de entrar duas vezes — sem ele, o
cadastro pelo app funcionaria e criaria a duplicata.

### I.3 — Vincular alguém a um estabelecimento

Subcoleção `members` do estabelecimento → **Add document** → **o ID do documento precisa
ser o uid** da pessoa (o mesmo de `users/{uid}`).

| Campo | Tipo | Valor |
|---|---|---|
| `uid` | string | o mesmo uid do ID do documento |
| `role` | string | `MOD`, `CLIENT` ou `USER` |
| `active` | boolean | `true` |

O campo `uid` repetir o ID do documento não é redundância à toa: a rule do collection group
compara `resource.data.uid`, porque numa query o motor não consegue casar a condição com o
ID do documento. Se os dois divergirem, a pessoa some do próprio seletor de
estabelecimentos — e ninguém recebe erro, a lista só volta vazia.

`role` fora de `MOD`/`CLIENT`/`USER` faz o vínculo ser descartado silenciosamente pelo app
(`MemberRole.fromRaw` devolve `null`), com o mesmo efeito. `active` ausente conta como
desligado.

### I.4 — Conferir

Com o APK da F1.7.1 instalado e logado como a pessoa vinculada:

1. `establishments/{id}` é legível para ela e para o ADM; para quem não é membro, negado.
2. `active: false` no vínculo tira o acesso sem apagar o documento.
3. O ADM lista `establishments`; MOD e CLIENT recebem `PERMISSION_DENIED` no `list` — é o
   esperado, eles chegam pelo próprio vínculo.

## Registro local

Guardar os dados reais (email, senha, UID) em **`docs/ops/test-users.local.md`** â€” gitignorado via
`*.local.md`. Se o arquivo nÃ£o existir no seu clone, crie com este conteÃºdo:

```markdown
# UsuÃ¡rios de teste â€” LOCAL (nÃ£o commitar)

> Gitignorado via `*.local.md` no `.gitignore` da raiz.
> Nunca colar em issue, PR, chat ou screenshot.

| Email | Senha | UID | role | name | Criado em |
|---|---|---|---|---|---|
|  |  |  | ADM |  |  |
```

Confirmar que estÃ¡ ignorado antes de commitar qualquer coisa:

```bash
git check-ignore -v docs/ops/test-users.local.md   # deve casar com a regra
git status --short                                  # o arquivo NÃƒO pode aparecer
```

---

## ReferÃªncias

- [SECURITY.md Â§ F1.3](../../SECURITY.md#f13--firebase-auth--sessÃ£o-criptografada) â€” decisÃµes e trade-offs
- [SECURITY.md Â§ F1.4b](../../SECURITY.md#f14b--firebase-app-check-play-integrity) â€” por que o provider Ã© escolhido por build type
- `composeApp/src/androidMain/.../core/security/AppCheckBootstrap.kt` â€” instalaÃ§Ã£o do App Check
- `shared/src/androidMain/.../auth/data/repository/FirebaseAuthRepositoryImpl.kt` â€” leitura do doc e mapa de erros
- `shared/src/commonMain/.../auth/domain/model/UserRole.kt` â€” enum das roles
- `shared/src/commonMain/.../auth/domain/usecase/RestoreSessionUseCase.kt` â€” regra do auto-login
