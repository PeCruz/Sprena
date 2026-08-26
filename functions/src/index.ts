import { initializeApp } from 'firebase-admin/app';
import { onCall } from 'firebase-functions/v2/https';
import { handleBootstrapAccount } from './bootstrapAccount';
import { handleDeleteMyAccount } from './deleteMyAccount';
import { handleLinkMemberByCpf } from './linkMemberByCpf';
import {
  handleLeaveEstablishment,
  handleRemoveMember,
  handleSetMemberRole,
} from './manageMember';
import { CPF_PEPPER } from './cpfHmac';

initializeApp();

/**
 * Região fixa, e ela precisa bater com a constante `FUNCTIONS_REGION` do cliente
 * (`composeApp/androidMain/di/PlatformModule.android.kt`). Divergência devolve
 * `NOT_FOUND` no cliente, que é indistinguível de "função não deployada" — por isso as
 * duas constantes estão documentadas juntas em SECURITY.md.
 */
export const FUNCTIONS_REGION = 'southamerica-east1';

/**
 * O emulador de Functions **aplica** `enforceAppCheck` — sem token de App Check, toda
 * chamada vira `unauthenticated`, inclusive as da suíte de testes. Fornecer um token de
 * App Check válido em teste exigiria montar `initializeAppCheck` com debug provider no
 * cliente de teste, o que testaria o App Check e não a exclusão.
 *
 * Por isso a enforcement é desligada **apenas** sob `FUNCTIONS_EMULATOR`. Em produção
 * ela vale sempre, e independe da chave de enforcement do Console (que segue desligada —
 * ver a pendência do ROADMAP).
 *
 * Consequência do trade-off: a suíte prova a lógica de exclusão, **não** a enforcement.
 * Esta é verificada em device, no passo H.6 do runbook — e um build debug sem token de
 * App Check registrado recebe `unauthenticated`, o que parece bug e não é.
 */
const isEmulator = process.env.FUNCTIONS_EMULATOR === 'true';

export const deleteMyAccount = onCall(
  {
    region: FUNCTIONS_REGION,
    enforceAppCheck: !isEmulator,
    memory: '256MiB',
    timeoutSeconds: 120,
  },
  handleDeleteMyAccount,
);


/**
 * Opcoes comuns das callables de F1.7.3c.
 *
 * `enforceAppCheck` importa mais aqui do que em `deleteMyAccount`: `bootstrapAccount` e
 * chamada por qualquer conta autenticada e cria documento, entao sem App Check ela vira uma
 * forma barata de encher a colecao `users`. Ela vale por conta propria, independente da
 * chave de enforcement do Console.
 */
const callableOptions = {
  region: FUNCTIONS_REGION,
  enforceAppCheck: !isEmulator,
  memory: '256MiB',
  timeoutSeconds: 60,
} as const;

/** Cria `users/{uid}` no primeiro acesso, sempre com papel `USER`. Idempotente. */
export const bootstrapAccount = onCall(callableOptions, handleBootstrapAccount);

/**
 * O unico caminho de vinculacao. Precisa do pepper para transformar CPF em id de documento —
 * sem o segredo declarado aqui, o Secret Manager nao injeta o valor no runtime.
 */
export const linkMemberByCpf = onCall(
  { ...callableOptions, secrets: [CPF_PEPPER] },
  handleLinkMemberByCpf,
);

export const setMemberRole = onCall(callableOptions, handleSetMemberRole);
export const removeMember = onCall(callableOptions, handleRemoveMember);
export const leaveEstablishment = onCall(callableOptions, handleLeaveEstablishment);
