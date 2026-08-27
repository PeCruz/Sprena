/**
 * Testes de integracao da Cloud Function `deleteMyAccount` (F1.6a).
 *
 * Rode sempre pelo emulador — `npm run test:emulator` na pasta deste arquivo.
 * Mesma postura da suite de rules: `node --test`, projeto `demo-*`, zero credencial,
 * zero mock. O que se testa e a funcao de verdade contra Firestore e Auth de verdade.
 *
 * LIMITE CONHECIDO: o emulador APLICA `enforceAppCheck`, e sem token toda chamada vira
 * `unauthenticated`. Por isso a funcao desliga a enforcement sob `FUNCTIONS_EMULATOR`
 * (ver src/index.ts). Consequencia: estes testes provam a logica de exclusao, nao a
 * enforcement — essa so se verifica em device (runbook, H.6).
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import { connectFunctionsEmulator, getFunctions, httpsCallable } from 'firebase/functions';
import { initializeApp as initAdmin } from 'firebase-admin/app';
import { getAuth as adminAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

const PROJECT_ID = 'demo-sprena';
const REGION = 'southamerica-east1';

const OWNER = { email: 'titular@example.com', password: 'senha-de-teste-123' };
const OTHER = { email: 'outro@example.com', password: 'senha-de-teste-456' };

let admin;
let db;
let auth;
let client;
let ownerUid;
let otherUid;

before(async () => {
  process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
  process.env.FIREBASE_AUTH_EMULATOR_HOST ??= '127.0.0.1:9099';

  admin = initAdmin({ projectId: PROJECT_ID });
  db = getFirestore();
  auth = adminAuth();

  client = initializeApp({ projectId: PROJECT_ID, apiKey: 'fake-api-key' });
  connectAuthEmulator(getAuth(client), 'http://127.0.0.1:9099', { disableWarnings: true });
  connectFunctionsEmulator(getFunctions(client, REGION), '127.0.0.1', 5001);
});

after(async () => {
  await admin?.delete?.();
});

/** Cria o usuario no Auth e semeia os cinco caminhos que a exclusao deve varrer. */
async function seedAccount({ email, password }, { withProfile = true, historyCount = 3 } = {}) {
  const user = await auth.createUser({ email, password });
  const uid = user.uid;

  await db.doc(`users/${uid}`).set({ role: 'MOD', name: 'Titular', email });
  if (withProfile) {
    await db.doc(`user_profiles/${uid}`).set({
      apelido: 'Pe',
      cpf: '12345678900',
      phone: '11987654321',
      modalities: ['VOLEI'],
      updatedAt: new Date(),
    });
  }
  await db.doc(`user_consents/${uid}`).set({
    uid,
    policyVersion: '2026-08-14',
    acceptedAt: new Date(),
    appVersion: '1.0.0',
  });
  for (let i = 0; i < historyCount; i += 1) {
    await db.collection(`user_consents/${uid}/history`).add({
      policyVersion: '2026-08-14',
      acceptedAt: new Date(),
    });
  }
  return uid;
}

async function callAsSignedIn({ email, password }, payload = {}) {
  await signInWithEmailAndPassword(getAuth(client), email, password);
  const callable = httpsCallable(getFunctions(client, REGION), 'deleteMyAccount');
  return callable(payload);
}

async function exists(path) {
  return (await db.doc(path).get()).exists;
}

async function historySize(uid) {
  return (await db.collection(`user_consents/${uid}/history`).get()).size;
}

beforeEach(async () => {
  // Limpa Auth e Firestore entre casos — cada teste semeia o proprio estado.
  const { users } = await auth.listUsers();
  await Promise.all(users.map((u) => auth.deleteUser(u.uid)));
  for (const name of [
    'users', 'user_profiles', 'user_consents', 'account_deletions',
    'establishments', 'cpf_claims', 'user_settings',
  ]) {
    const snap = await db.collection(name).get();
    await Promise.all(snap.docs.map((d) => db.recursiveDelete(d.ref)));
  }
  ownerUid = undefined;
  otherUid = undefined;
});

describe('deleteMyAccount', () => {
  it('1. apaga os cinco caminhos e o usuario do Auth', async () => {
    ownerUid = await seedAccount(OWNER);

    const { data } = await callAsSignedIn(OWNER);

    assert.equal(data.status, 'deleted');
    assert.equal(await exists(`users/${ownerUid}`), false);
    assert.equal(await exists(`user_profiles/${ownerUid}`), false);
    assert.equal(await exists(`user_consents/${ownerUid}`), false);
    assert.equal(await historySize(ownerUid), 0);
    await assert.rejects(() => auth.getUser(ownerUid));
  });

  it('2. conta minima sem sidecar e sem historico nao falha', async () => {
    ownerUid = await seedAccount(OWNER, { withProfile: false, historyCount: 0 });

    const { data } = await callAsSignedIn(OWNER);

    assert.equal(data.status, 'deleted');
    assert.equal(await exists(`users/${ownerUid}`), false);
  });

  it('2b. apaga vinculos, contexto ativo e a trava de CPF (F1.7.3c)', async () => {
    ownerUid = await seedAccount(OWNER);
    const cpfHmac = 'hash_do_cpf_do_titular';
    await db.doc(`users/${ownerUid}`).update({ cpfHmac });
    await db.doc(`cpf_claims/${cpfHmac}`).set({ uid: ownerUid });
    await db.doc(`user_settings/${ownerUid}`).set({ activeEstablishmentId: 'est_a' });
    for (const estId of ['est_a', 'est_b']) {
      await db.doc(`establishments/${estId}`).set({ name: estId, active: true });
      await db.doc(`establishments/${estId}/members/${ownerUid}`).set({
        uid: ownerUid, role: 'CLIENT', active: true, displayName: 'Titular',
      });
    }

    const { data } = await callAsSignedIn(OWNER);

    // Vinculo orfao nao vazaria nada — as rules leem o grafo, nao o contrario —, mas
    // carrega displayName e continuaria aparecendo na lista de membros apontando para uma
    // conta que nao existe mais. Sumir pela metade e pior que nao sumir.
    assert.equal(data.membershipsRemoved, 2);
    assert.equal(await exists('establishments/est_a/members/' + ownerUid), false);
    assert.equal(await exists('establishments/est_b/members/' + ownerUid), false);
    assert.equal(await exists(`user_settings/${ownerUid}`), false);
    // Liberar a trava permite reivindicar o mesmo CPF numa conta futura.
    assert.equal(await exists(`cpf_claims/${cpfHmac}`), false);
  });

  it('3. chamada sem autenticacao e recusada', async () => {
    const callable = httpsCallable(getFunctions(client, REGION), 'deleteMyAccount');
    await getAuth(client).signOut();

    await assert.rejects(() => callable({}), (err) => {
      assert.equal(err.code, 'functions/unauthenticated');
      return true;
    });
  });

  it('4. uid de outro usuario no payload e recusado e o outro sobrevive', async () => {
    ownerUid = await seedAccount(OWNER);
    otherUid = await seedAccount(OTHER);

    await assert.rejects(
      () => callAsSignedIn(OWNER, { uid: otherUid }),
      (err) => {
        assert.equal(err.code, 'functions/invalid-argument');
        return true;
      },
    );

    // Nem a vitima nem o proprio chamador foram tocados.
    assert.equal(await exists(`users/${otherUid}`), true);
    assert.equal(await exists(`users/${ownerUid}`), true);
    assert.ok(await auth.getUser(otherUid));
  });

  it('5. gravou a trilha de auditoria sem PII', async () => {
    ownerUid = await seedAccount(OWNER);

    await callAsSignedIn(OWNER);

    const audit = (await db.doc(`account_deletions/${ownerUid}`).get()).data();
    assert.ok(audit, 'trilha de auditoria deveria existir');
    assert.equal(audit.financialAnonymized, 0);
    assert.equal(audit.policyVersionAtDeletion, '2026-08-14');

    const serialized = JSON.stringify(audit);
    assert.ok(!serialized.includes('12345678900'), 'CPF nao pode entrar na trilha');
    assert.ok(!serialized.includes('11987654321'), 'telefone nao pode entrar na trilha');
    assert.ok(!serialized.includes(OWNER.email), 'email nao pode entrar na trilha');
  });

  it('6. apaga historico acima do limite de um lote', async () => {
    // 501 docs forcam mais de um lote de 500 — prova o batching do delete recursivo.
    ownerUid = await seedAccount(OWNER, { historyCount: 0 });
    const bulk = db.bulkWriter();
    for (let i = 0; i < 501; i += 1) {
      bulk.create(db.collection(`user_consents/${ownerUid}/history`).doc(), {
        policyVersion: '2026-08-14',
        acceptedAt: new Date(),
      });
    }
    await bulk.close();
    assert.equal(await historySize(ownerUid), 501);

    await callAsSignedIn(OWNER);

    assert.equal(await historySize(ownerUid), 0);
  });
});
