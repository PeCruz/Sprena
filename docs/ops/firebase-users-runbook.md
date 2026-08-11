# Runbook — Criar usuário no Firebase + validar em device

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
- `adb` no PATH (`adb devices` deve listar o aparelho).

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
| "Erro de autenticação" (genérico) | qualquer outra exceção — inclusive **falha na leitura do Firestore** | ver logcat: a tag `FirebaseAuthRepo` traz `cause=<NomeDaExceção>` |

**Armadilha para F1.4:** quando as Security Rules entrarem, um `PERMISSION_DENIED` na leitura de
`users/{uid}` cai no `catch` genérico → o usuário vê **"Erro de autenticação"** *depois* de o Firebase Auth
já ter aceitado a senha. Ou seja: autenticado no Firebase, mas sem sessão no app. Diagnóstico só pelo logcat.

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
- `shared/src/androidMain/.../auth/data/repository/FirebaseAuthRepositoryImpl.kt` — leitura do doc e mapa de erros
- `shared/src/commonMain/.../auth/domain/model/UserRole.kt` — enum das roles
- `shared/src/commonMain/.../auth/domain/usecase/RestoreSessionUseCase.kt` — regra do auto-login
