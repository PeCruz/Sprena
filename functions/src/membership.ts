import { getFirestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';

/**
 * Papéis por estabelecimento. Espelha `MemberRole` no Kotlin.
 *
 * `ADM` **não** está aqui, e a ausência é a regra: administrador é papel global, criado só
 * pelo Console. Nenhuma callable aceita esse valor — ver [assertAssignableRole].
 */
export const MEMBER_ROLES = ['MOD', 'CLIENT', 'USER'] as const;
export type MemberRole = (typeof MEMBER_ROLES)[number];

export function isMemberRole(value: unknown): value is MemberRole {
  return typeof value === 'string' && (MEMBER_ROLES as readonly string[]).includes(value);
}

/** O papel de quem chama, dentro de um estabelecimento. `null` = não é membro ativo. */
export interface CallerContext {
  uid: string;
  isAdm: boolean;
  roleInEstablishment: MemberRole | null;
}

/**
 * Resolve quem está chamando: papel global e papel no estabelecimento.
 *
 * Duas leituras, as mesmas que as rules fariam. A callable não pode confiar no que o cliente
 * diz sobre si — o corpo da requisição é controlado por quem chama.
 */
export async function resolveCaller(
  uid: string,
  establishmentId: string,
): Promise<CallerContext> {
  const db = getFirestore();
  const [userDoc, memberDoc] = await Promise.all([
    db.doc(`users/${uid}`).get(),
    db.doc(`establishments/${establishmentId}/members/${uid}`).get(),
  ]);

  const rawRole = memberDoc.get('role');
  const active = memberDoc.get('active') === true;

  return {
    uid,
    isAdm: userDoc.get('role') === 'ADM',
    // Vínculo desligado não vale nada — mesma leitura que `isMemberOf` faz nas rules.
    roleInEstablishment: active && isMemberRole(rawRole) ? rawRole : null,
  };
}

/**
 * Quem pode conceder qual papel.
 *
 * | Quem chama | Pode atribuir |
 * |---|---|
 * | ADM | MOD, CLIENT, USER |
 * | MOD do estabelecimento | CLIENT, USER |
 * | CLIENT do estabelecimento | USER |
 * | USER, forasteiro | nada |
 *
 * A escada é deliberada: ninguém concede o próprio papel nem um acima dele, então nenhuma
 * corrente de vinculações consegue produzir alguém mais poderoso que quem a iniciou.
 */
export function canGrantRole(caller: CallerContext, role: MemberRole): boolean {
  if (caller.isAdm) return true;
  if (caller.roleInEstablishment === 'MOD') return role === 'CLIENT' || role === 'USER';
  if (caller.roleInEstablishment === 'CLIENT') return role === 'USER';
  return false;
}

/**
 * `ADM` como papel de vínculo é recusado antes de qualquer outra checagem, e o intento vai
 * para `security_events`.
 *
 * Não é redundante com [canGrantRole]: aquela responde "este chamador pode conceder este
 * papel?", e a resposta para um ADM seria "sim". Esta responde "este papel pode existir num
 * member doc?", cuja resposta é sempre não. Administrador é papel global, criado só pelo
 * Console — é a única garantia que impede o app inteiro de fabricar um administrador.
 */
export async function assertAssignableRole(
  role: unknown,
  context: { uid: string; establishmentId: string; operation: string },
): Promise<MemberRole> {
  if (role === 'ADM') {
    logger.error('tentativa de conceder ADM por callable', context);
    await getFirestore()
      .collection('security_events')
      .add({
        type: 'adm_role_attempt',
        at: new Date().toISOString(),
        ...context,
      });
    throw new HttpsError('permission-denied', 'Este papel não pode ser concedido pelo aplicativo.');
  }

  if (!isMemberRole(role)) {
    throw new HttpsError('invalid-argument', 'Papel inválido.');
  }

  return role;
}

/** Ids de documento do Firestore, e nada mais — evita path traversal no `db.doc()`. */
const SAFE_ID = /^[A-Za-z0-9_-]{1,128}$/;

export function assertSafeId(value: unknown, field: string): string {
  if (typeof value !== 'string' || !SAFE_ID.test(value)) {
    throw new HttpsError('invalid-argument', `Campo inválido: ${field}.`);
  }
  return value;
}

/** Rejeita chaves inesperadas — o idioma de `deleteMyAccount`, aplicado a payloads com campos. */
export function assertOnlyKeys(payload: unknown, allowed: readonly string[]): Record<string, unknown> {
  if (!payload || typeof payload !== 'object') {
    throw new HttpsError('invalid-argument', 'Parâmetros ausentes.');
  }
  const extra = Object.keys(payload).filter((key) => !allowed.includes(key));
  if (extra.length > 0) {
    throw new HttpsError('invalid-argument', `Parâmetros não aceitos: ${extra.join(', ')}.`);
  }
  return payload as Record<string, unknown>;
}

/** Trilha por estabelecimento. Sem PII: nome e CPF ficam de fora, só o que aconteceu. */
export async function writeAudit(
  establishmentId: string,
  entry: Record<string, unknown>,
): Promise<void> {
  await getFirestore()
    .collection(`establishments/${establishmentId}/audit`)
    .add({ at: new Date().toISOString(), ...entry });
}
