import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import {
  assertAssignableRole,
  assertOnlyKeys,
  assertSafeId,
  canGrantRole,
  isMemberRole,
  resolveCaller,
  writeAudit,
  type CallerContext,
  type MemberRole,
} from './membership';

export interface MemberMutationResponse {
  status: 'changed' | 'unchanged';
}

/**
 * Muda o papel de quem já é membro.
 *
 * A regra é a mesma escada de [canGrantRole], aplicada **duas vezes**: quem chama precisa
 * poder conceder o papel novo *e* poder mexer em quem tem o papel atual. Sem a segunda
 * checagem, um CLIENT — que pode conceder `USER` — rebaixaria o MOD do estabelecimento para
 * `USER` e assumiria o lugar dele.
 *
 * Alterar o próprio papel é negado sempre. Um ADM tem poder para isso, mas fazê-lo pela
 * callable significa que um único endpoint comprometido reescreve o próprio acesso; e um MOD
 * não ganharia nada além de se rebaixar por engano.
 */
export async function handleSetMemberRole(
  request: CallableRequest<unknown>,
): Promise<MemberMutationResponse> {
  const callerUid = requireUid(request);
  const payload = assertOnlyKeys(request.data, ['establishmentId', 'targetUid', 'role']);
  const establishmentId = assertSafeId(payload.establishmentId, 'establishmentId');
  const targetUid = assertSafeId(payload.targetUid, 'targetUid');
  const role = await assertAssignableRole(payload.role, {
    uid: callerUid,
    establishmentId,
    operation: 'setMemberRole',
  });

  if (targetUid === callerUid) {
    throw new HttpsError('permission-denied', 'Você não pode alterar o próprio papel.');
  }

  const db = getFirestore();
  const caller = await resolveCaller(callerUid, establishmentId);
  const memberRef = db.doc(`establishments/${establishmentId}/members/${targetUid}`);
  const member = await memberRef.get();

  if (!member.exists) {
    throw new HttpsError('not-found', 'Esta pessoa não está vinculada a este estabelecimento.');
  }

  const currentRole = member.get('role');
  assertCanActOn(caller, currentRole, role);

  if (currentRole === role && member.get('active') === true) {
    return { status: 'unchanged' };
  }

  await memberRef.update({
    role,
    active: true,
    changedBy: callerUid,
    changedAt: FieldValue.serverTimestamp(),
  });
  await writeAudit(establishmentId, {
    action: 'set_member_role',
    actorUid: callerUid,
    targetUid,
    from: currentRole ?? null,
    to: role,
  });

  logger.info('setMemberRole: alterado', { establishmentId, from: currentRole, to: role });
  return { status: 'changed' };
}

/**
 * Desliga um membro.
 *
 * Marca `active: false` em vez de apagar: as rules tratam `active != true` como "não é
 * membro", então o efeito de acesso é imediato, e manter o documento preserva a trilha de
 * quem já teve acesso — junto do `displayName`, que é a única forma de saber depois quem era.
 *
 * O CLIENT pode remover apenas `USER`. É o "desfazer" da lista de vínculos recentes: ele
 * precisa conseguir corrigir um vínculo indevido sem poder desligar o moderador do lugar.
 */
export async function handleRemoveMember(
  request: CallableRequest<unknown>,
): Promise<MemberMutationResponse> {
  const callerUid = requireUid(request);
  const payload = assertOnlyKeys(request.data, ['establishmentId', 'targetUid']);
  const establishmentId = assertSafeId(payload.establishmentId, 'establishmentId');
  const targetUid = assertSafeId(payload.targetUid, 'targetUid');

  if (targetUid === callerUid) {
    // Sair do estabelecimento é `leaveEstablishment`, que não exige permissão nenhuma.
    throw new HttpsError('invalid-argument', 'Para sair do estabelecimento, use a opção de saída.');
  }

  const db = getFirestore();
  const caller = await resolveCaller(callerUid, establishmentId);
  const memberRef = db.doc(`establishments/${establishmentId}/members/${targetUid}`);
  const member = await memberRef.get();

  if (!member.exists) {
    return { status: 'unchanged' };
  }

  assertCanActOn(caller, member.get('role'), null);

  if (member.get('active') !== true) {
    return { status: 'unchanged' };
  }

  await memberRef.update({
    active: false,
    removedBy: callerUid,
    removedAt: FieldValue.serverTimestamp(),
  });
  await writeAudit(establishmentId, {
    action: 'remove_member',
    actorUid: callerUid,
    targetUid,
    from: member.get('role') ?? null,
  });

  logger.info('removeMember: desligado', { establishmentId });
  return { status: 'changed' };
}

/**
 * Sair de um estabelecimento por conta própria.
 *
 * Não pede permissão a ninguém, e é isso que a torna necessária: é o remédio de quem foi
 * vinculado sem pedir — o caso do CPF digitado errado que alcançou a pessoa errada. Sem ela,
 * a única saída dependeria de convencer quem fez o vínculo a desfazê-lo.
 */
export async function handleLeaveEstablishment(
  request: CallableRequest<unknown>,
): Promise<MemberMutationResponse> {
  const callerUid = requireUid(request);
  const payload = assertOnlyKeys(request.data, ['establishmentId']);
  const establishmentId = assertSafeId(payload.establishmentId, 'establishmentId');

  const memberRef = getFirestore().doc(
    `establishments/${establishmentId}/members/${callerUid}`,
  );
  const member = await memberRef.get();
  if (!member.exists || member.get('active') !== true) {
    return { status: 'unchanged' };
  }

  await memberRef.update({
    active: false,
    removedBy: callerUid,
    removedAt: FieldValue.serverTimestamp(),
  });
  await writeAudit(establishmentId, {
    action: 'leave_establishment',
    actorUid: callerUid,
    targetUid: callerUid,
    from: member.get('role') ?? null,
  });

  return { status: 'changed' };
}

function requireUid(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError('unauthenticated', 'É preciso estar autenticado.');
  }
  return uid;
}

/**
 * Checa a autoridade sobre o papel **atual** do alvo, além do papel novo.
 *
 * É a segunda metade da escada. `canGrantRole` responde "pode conceder isto?"; esta responde
 * "pode mexer em alguém que hoje é aquilo?". Faltando ela, um CLIENT rebaixaria o MOD do
 * estabelecimento para `USER` — uma promoção disfarçada de remoção.
 */
function assertCanActOn(
  caller: CallerContext,
  currentRole: unknown,
  nextRole: MemberRole | null,
): void {
  const current = isMemberRole(currentRole) ? currentRole : 'USER';
  const allowed = canGrantRole(caller, current) && (nextRole === null || canGrantRole(caller, nextRole));

  if (!allowed) {
    logger.warn('mutacao de membro negada', {
      callerUid: caller.uid,
      callerRole: caller.roleInEstablishment,
      current,
      nextRole,
    });
    throw new HttpsError('permission-denied', 'Você não pode alterar este vínculo.');
  }
}
