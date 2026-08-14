import { initializeApp } from 'firebase-admin/app';
import { onCall } from 'firebase-functions/v2/https';
import { handleDeleteMyAccount } from './deleteMyAccount';

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
