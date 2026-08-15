import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import { anonymizeFinancial } from './anonymizeFinancial';
import { deleteCollection } from './firestoreDelete';

export interface DeleteMyAccountResponse {
  status: 'deleted';
  uid: string;
  deletedAt: string;
  financialAnonymized: number;
  authUserDeleted: boolean;
}

/**
 * Exclusão da própria conta (LGPD art. 18, VI + exigência da Play Store).
 *
 * **Sem payload.** O uid vem exclusivamente de `request.auth.uid`. Qualquer chave em
 * `request.data` é rejeitada: aceitar um `uid` no corpo seria a escalada de privilégio
 * óbvia, e negar explicitamente documenta que a possibilidade foi considerada.
 *
 * A ordem dos passos não é arbitrária — ver os comentários de cada um.
 */
export async function handleDeleteMyAccount(
  request: CallableRequest<unknown>,
): Promise<DeleteMyAccountResponse> {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError('unauthenticated', 'É preciso estar autenticado.');
  }

  const payload = request.data;
  if (payload && typeof payload === 'object' && Object.keys(payload).length > 0) {
    logger.warn('deleteMyAccount: payload rejeitado', {
      uid,
      keys: Object.keys(payload),
    });
    throw new HttpsError(
      'invalid-argument',
      'Esta operação não aceita parâmetros — a conta excluída é sempre a do chamador.',
    );
  }

  const db = getFirestore();

  // 1. Ler antes de apagar: a versão de política vai para a trilha de auditoria, e a
  //    anonimização precisa da identidade que os passos seguintes destroem.
  const consentSnapshot = await db.doc(`user_consents/${uid}`).get();
  const policyVersionAtDeletion = consentSnapshot.get('policyVersion') ?? null;

  // 2. Anonimizar o financeiro ANTES dos deletes, pelo mesmo motivo.
  const financialAnonymized = await anonymizeFinancial(uid);

  // 3. Subcoleção antes do pai: apagar o pai primeiro deixaria o histórico vivo e
  //    invisível no Console, fazendo o operador acreditar que o dado sumiu.
  const historyDeleted = await deleteCollection(`user_consents/${uid}/history`);

  // 4-6. Documentos do titular. Delete de doc inexistente é no-op — é isso que torna a
  //      função idempotente e segura de reexecutar sobre um uid órfão (runbook H.7).
  await db.doc(`user_consents/${uid}`).delete();
  await db.doc(`user_profiles/${uid}`).delete();
  await db.doc(`users/${uid}`).delete();

  // 7. Trilha SEM PII: nem e-mail, nem nome, nem CPF. É prova de que a exclusão
  //    aconteceu, não backup dela.
  await db.doc(`account_deletions/${uid}`).set({
    uid,
    deletedAt: FieldValue.serverTimestamp(),
    policyVersionAtDeletion,
    financialAnonymized,
    consentHistoryDeleted: historyDeleted,
    requestedFrom: 'app',
    appCheckVerified: request.app !== undefined,
  });

  // 8. Auth por último: assim que o usuário some, o token do cliente morre e qualquer
  //    retry vira `unauthenticated`. Se viesse antes, uma falha no meio deixaria os
  //    dados no Firestore e o titular sem caminho para pedir de novo.
  const authUserDeleted = await deleteAuthUser(uid);

  logger.info('deleteMyAccount: concluido', {
    uid,
    financialAnonymized,
    consentHistoryDeleted: historyDeleted,
    authUserDeleted,
  });

  return {
    status: 'deleted',
    uid,
    deletedAt: new Date().toISOString(),
    financialAnonymized,
    authUserDeleted,
  };
}

/** `user-not-found` conta como sucesso: a segunda chamada precisa ser idempotente. */
async function deleteAuthUser(uid: string): Promise<boolean> {
  try {
    await getAuth().deleteUser(uid);
    return true;
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code === 'auth/user-not-found') {
      logger.info('deleteMyAccount: usuario do Auth ja nao existia', { uid });
      return false;
    }
    // Estado degradado: os dados já foram apagados e o usuário do Auth sobreviveu. O
    // login passa a cair em "Conta não autorizada", e a trilha em account_deletions é
    // o que permite detectar e limpar manualmente (runbook H.7).
    logger.error('deleteMyAccount: falha ao apagar o usuario do Auth', { uid, code });
    throw new HttpsError('internal', 'Não foi possível concluir a exclusão da conta.');
  }
}
