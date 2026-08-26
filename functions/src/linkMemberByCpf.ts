import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import { isValidCpf, maskCpf } from './cpf';
import { cpfHmac } from './cpfHmac';
import {
  assertAssignableRole,
  assertOnlyKeys,
  assertSafeId,
  canGrantRole,
  resolveCaller,
  writeAudit,
} from './membership';

const ALLOWED_KEYS = ['establishmentId', 'cpf', 'name', 'role'] as const;
const NAME_MAX_LENGTH = 60;

export interface LinkMemberResponse {
  /** `linked` = a pessoa já tinha conta e o vínculo existe agora. `pending` = fica esperando. */
  status: 'linked' | 'pending' | 'already';
}

/**
 * O **único** caminho de vinculação do sistema (F1.7.3c).
 *
 * ADM→MOD, MOD→CLIENT e CLIENT→USER são a mesma operação com papel diferente. Antes havia o
 * desenho de buscar a pessoa por e-mail e vincular pelo uid; ele foi descartado porque a busca
 * seria um oráculo de "esta pessoa tem conta nesta plataforma", consultável por qualquer
 * moderador sobre qualquer endereço, sem deixar de parecer uso legítimo.
 *
 * ## Write-only: quem vincula não descobre nada
 *
 * A resposta distingue `linked` de `pending`, e essa é uma escolha consciente — quem vinculou
 * precisa saber se deve avisar a pessoa para entrar no app. O que ela **não** revela é
 * qualquer coisa sobre CPFs que não sejam o que ela mesma digitou: não há listagem, não há
 * consulta, e o id do documento é um HMAC que só o servidor sabe calcular.
 *
 * ## O risco que sobra
 *
 * Um dígito errado num CPF que por acaso também é válido cria uma pendência presa a um número
 * de outra pessoa. Se essa pessoa entrar no app e informar aquele CPF, ela assume o vínculo —
 * e, se o papel era MOD, assume o estabelecimento. Três defesas: o dígito verificador barra a
 * maioria dos erros de digitação, a pendência aparece na lista revisável do estabelecimento,
 * e todo vínculo consumado vira um `member_event` que o staff vê e pode desfazer.
 */
export async function handleLinkMemberByCpf(
  request: CallableRequest<unknown>,
): Promise<LinkMemberResponse> {
  const callerUid = request.auth?.uid;
  if (!callerUid) {
    throw new HttpsError('unauthenticated', 'É preciso estar autenticado.');
  }

  const payload = assertOnlyKeys(request.data, ALLOWED_KEYS);
  const establishmentId = assertSafeId(payload.establishmentId, 'establishmentId');
  const role = await assertAssignableRole(payload.role, {
    uid: callerUid,
    establishmentId,
    operation: 'linkMemberByCpf',
  });

  const rawCpf = typeof payload.cpf === 'string' ? payload.cpf : '';
  // Dígito verificador antes de tudo: um número que não fecha é recusado sem gastar leitura,
  // escrita nem a única tentativa útil de quem estivesse tentando adivinhar.
  if (!isValidCpf(rawCpf)) {
    throw new HttpsError('invalid-argument', 'CPF inválido.');
  }

  const name = normalizeName(payload.name);
  const caller = await resolveCaller(callerUid, establishmentId);
  if (!canGrantRole(caller, role)) {
    logger.warn('linkMemberByCpf: sem permissao', { callerUid, establishmentId, role });
    throw new HttpsError('permission-denied', 'Você não pode vincular alguém com este papel.');
  }

  const db = getFirestore();
  const hash = cpfHmac(rawCpf);
  const claim = await db.doc(`cpf_claims/${hash}`).get();
  const ownerUid = claim.get('uid');

  if (typeof ownerUid === 'string') {
    return linkExistingAccount({ establishmentId, ownerUid, role, name, callerUid, hash, rawCpf });
  }

  return createPending({ establishmentId, role, name, callerUid, hash, rawCpf });
}

/** A pessoa já reivindicou este CPF: o vínculo nasce agora, sem etapa pendente. */
async function linkExistingAccount(args: {
  establishmentId: string;
  ownerUid: string;
  role: string;
  name: string;
  callerUid: string;
  hash: string;
  rawCpf: string;
}): Promise<LinkMemberResponse> {
  const { establishmentId, ownerUid, role, name, callerUid, hash, rawCpf } = args;
  const db = getFirestore();
  const memberRef = db.doc(`establishments/${establishmentId}/members/${ownerUid}`);

  const existing = await memberRef.get();
  if (existing.exists && existing.get('active') === true && existing.get('role') === role) {
    // Idempotente: repetir a mesma vinculação não gera evento nem trilha duplicada.
    return { status: 'already' };
  }

  const batch = db.batch();
  batch.set(memberRef, {
    uid: ownerUid,
    role,
    active: true,
    // O nome mora aqui porque as rules impedem o ADM de ler `users` ou `user_profiles` de
    // outra pessoa. Sem esta cópia, a tela de membros lista identificadores opacos.
    displayName: name,
    addedBy: callerUid,
    addedAt: FieldValue.serverTimestamp(),
    source: 'cpf_link',
  });
  batch.set(db.doc(`establishments/${establishmentId}/member_events/${hash}`), {
    type: 'manual_link',
    uid: ownerUid,
    apelido: name,
    cpfMasked: maskCpf(rawCpf),
    role,
    at: FieldValue.serverTimestamp(),
    reviewed: false,
  });
  await batch.commit();

  await writeAudit(establishmentId, {
    action: 'link_member',
    actorUid: callerUid,
    targetUid: ownerUid,
    to: role,
    // Só o prefixo do hash: identifica o registro no log sem reconstituir o CPF.
    cpfRef: hash.slice(0, 8),
  });

  logger.info('linkMemberByCpf: vinculado', { establishmentId, role, cpfRef: hash.slice(0, 8) });
  return { status: 'linked' };
}

/** Ninguém reivindicou este CPF ainda: fica pendente até o primeiro login. */
async function createPending(args: {
  establishmentId: string;
  role: string;
  name: string;
  callerUid: string;
  hash: string;
  rawCpf: string;
}): Promise<LinkMemberResponse> {
  const { establishmentId, role, name, callerUid, hash, rawCpf } = args;
  const ref = getFirestore().doc(`establishments/${establishmentId}/preregistrations/${hash}`);

  const existing = await ref.get();
  if (existing.exists && existing.get('status') === 'pending') {
    return { status: 'already' };
  }

  await ref.set({
    cpfHmac: hash,
    // Nunca o CPF completo: a pessoa ainda não existe no sistema e não consentiu com nada.
    cpfMasked: maskCpf(rawCpf),
    apelido: name,
    role,
    status: 'pending',
    createdBy: callerUid,
    createdAt: FieldValue.serverTimestamp(),
  });

  await writeAudit(establishmentId, {
    action: 'create_preregistration',
    actorUid: callerUid,
    to: role,
    cpfRef: hash.slice(0, 8),
  });

  logger.info('linkMemberByCpf: pendencia criada', {
    establishmentId,
    role,
    cpfRef: hash.slice(0, 8),
  });
  return { status: 'pending' };
}

function normalizeName(value: unknown): string {
  const name = typeof value === 'string' ? value.trim() : '';
  if (name.length === 0) {
    throw new HttpsError('invalid-argument', 'Informe um nome para identificar a pessoa.');
  }
  return name.slice(0, NAME_MAX_LENGTH);
}
