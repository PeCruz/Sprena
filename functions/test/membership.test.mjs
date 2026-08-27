/**
 * Testes de integracao das callables de vinculo (F1.7.3c).
 *
 * Mesma postura de `deleteMyAccount.test.mjs`: `node --test`, projeto `demo-*`, zero
 * credencial, zero mock. O que se testa e a funcao de verdade contra Firestore e Auth de
 * verdade — inclusive as regras de autorizacao, que sao o valor real destas funcoes.
 *
 * LIMITE CONHECIDO, herdado: o emulador aplica `enforceAppCheck`, entao a enforcement fica
 * desligada sob `FUNCTIONS_EMULATOR`. Estes testes provam a autorizacao, nao o App Check.
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
const EST = 'est_a';
const OTHER_EST = 'est_b';

/** CPFs com digito verificador correto — os invalidos sao construidos nos casos. */
const CPF_LIVRE = '11144477735';
const CPF_DA_MARIA = '52998224725';

const PASSWORD = 'senha-de-teste-123';
const ACCOUNTS = {
  adm: 'adm@example.com',
  mod: 'mod@example.com',
  client: 'client@example.com',
  user: 'user@example.com',
  maria: 'maria@example.com',
};

let admin;
let db;
let auth;
let client;
let uids;

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

async function call(name, who, payload = {}) {
  await signInWithEmailAndPassword(getAuth(client), ACCOUNTS[who], PASSWORD);
  return httpsCallable(getFunctions(client, REGION), name)(payload);
}

async function member(uid, estId = EST) {
  return (await db.doc(`establishments/${estId}/members/${uid}`).get()).data();
}

beforeEach(async () => {
  const { users } = await auth.listUsers();
  await Promise.all(users.map((u) => auth.deleteUser(u.uid)));
  for (const name of ['users', 'establishments', 'cpf_claims', 'security_events']) {
    const snap = await db.collection(name).get();
    await Promise.all(snap.docs.map((d) => db.recursiveDelete(d.ref)));
  }

  uids = {};
  for (const [key, email] of Object.entries(ACCOUNTS)) {
    const created = await auth.createUser({ email, password: PASSWORD });
    uids[key] = created.uid;
  }

  // Papel global: so o ADM e ADM. Os demais sao USER — o papel operacional vem do vinculo.
  await db.doc(`users/${uids.adm}`).set({ role: 'ADM', name: 'Adm' });
  for (const key of ['mod', 'client', 'user', 'maria']) {
    await db.doc(`users/${uids[key]}`).set({ role: 'USER', name: key });
  }

  for (const estId of [EST, OTHER_EST]) {
    await db.doc(`establishments/${estId}`).set({ name: estId, active: true });
  }
  await db.doc(`establishments/${EST}/members/${uids.mod}`).set({
    uid: uids.mod, role: 'MOD', active: true, displayName: 'Mod',
  });
  await db.doc(`establishments/${EST}/members/${uids.client}`).set({
    uid: uids.client, role: 'CLIENT', active: true, displayName: 'Client',
  });
  await db.doc(`establishments/${EST}/members/${uids.user}`).set({
    uid: uids.user, role: 'USER', active: true, displayName: 'User',
  });
});

describe('bootstrapAccount', () => {
  it('1. cria users/{uid} com papel USER no primeiro acesso', async () => {
    await db.doc(`users/${uids.maria}`).delete();

    const { data } = await call('bootstrapAccount', 'maria');

    assert.equal(data.created, true);
    assert.equal(data.role, 'USER');
    const doc = (await db.doc(`users/${uids.maria}`).get()).data();
    assert.equal(doc.role, 'USER');
    assert.equal(doc.email, ACCOUNTS.maria);
  });

  it('2. chamada repetida NAO rebaixa um ADM', async () => {
    // O caso que justifica `create` em vez de `set`. Um `set` aqui transformaria a conta
    // mais poderosa do sistema na mais fraca, sem nada no log parecendo um ataque.
    const { data } = await call('bootstrapAccount', 'adm');

    assert.equal(data.created, false);
    assert.equal(data.role, 'ADM');
    assert.equal((await db.doc(`users/${uids.adm}`).get()).get('role'), 'ADM');
  });

  it('3. recusa payload — a conta criada e sempre a do chamador', async () => {
    await assert.rejects(
      () => call('bootstrapAccount', 'maria', { uid: uids.adm, role: 'ADM' }),
      (err) => {
        assert.equal(err.code, 'functions/invalid-argument');
        return true;
      },
    );
  });
});

describe('linkMemberByCpf', () => {
  it('4. ADM vincula um MOD; fica pendente ate a pessoa reclamar o CPF', async () => {
    const { data } = await call('linkMemberByCpf', 'adm', {
      establishmentId: EST, cpf: CPF_LIVRE, name: 'Novo Mod', role: 'MOD',
    });

    assert.equal(data.status, 'pending');
    const pendentes = await db.collection(`establishments/${EST}/preregistrations`).get();
    assert.equal(pendentes.size, 1);
    const pendencia = pendentes.docs[0].data();
    assert.equal(pendencia.role, 'MOD');
    assert.equal(pendencia.status, 'pending');
    // O CPF completo nunca e gravado: a pessoa ainda nao existe e nao consentiu com nada.
    assert.equal(pendencia.cpfMasked, '***.444.777-**');
    assert.equal(pendencia.cpf, undefined);
    assert.ok(!JSON.stringify(pendencia).includes(CPF_LIVRE));
  });

  it('5. vincula na hora quando o CPF ja pertence a alguem', async () => {
    await db.doc(`cpf_claims/${await hashOf(CPF_DA_MARIA)}`).set({ uid: uids.maria });

    const { data } = await call('linkMemberByCpf', 'adm', {
      establishmentId: EST, cpf: CPF_DA_MARIA, name: 'Maria', role: 'CLIENT',
    });

    assert.equal(data.status, 'linked');
    const vinculo = await member(uids.maria);
    assert.equal(vinculo.role, 'CLIENT');
    assert.equal(vinculo.active, true);
    // O nome vai para o vinculo porque as rules impedem o ADM de ler `users` alheio.
    assert.equal(vinculo.displayName, 'Maria');
    const eventos = await db.collection(`establishments/${EST}/member_events`).get();
    assert.equal(eventos.size, 1);
  });

  it('6. CPF com digito verificador errado e recusado sem gravar nada', async () => {
    await assert.rejects(
      () => call('linkMemberByCpf', 'adm', {
        establishmentId: EST, cpf: '11144477700', name: 'X', role: 'USER',
      }),
      (err) => {
        assert.equal(err.code, 'functions/invalid-argument');
        return true;
      },
    );
    assert.equal((await db.collection(`establishments/${EST}/preregistrations`).get()).size, 0);
  });

  it('7. papel ADM e recusado e registrado como incidente', async () => {
    await assert.rejects(
      () => call('linkMemberByCpf', 'adm', {
        establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'ADM',
      }),
      (err) => {
        assert.equal(err.code, 'functions/permission-denied');
        return true;
      },
    );

    // Recusado ate para o ADM: administrador e papel global, criado so pelo Console. E a
    // garantia que impede o app inteiro de fabricar um administrador.
    const eventos = await db.collection('security_events').get();
    assert.equal(eventos.size, 1);
    assert.equal(eventos.docs[0].get('type'), 'adm_role_attempt');
  });

  it('8. MOD nao concede MOD; CLIENT nao concede CLIENT', async () => {
    // A escada: ninguem concede o proprio papel nem um acima. Sem isso, uma corrente de
    // vinculacoes produziria alguem mais poderoso que quem a iniciou.
    await assert.rejects(
      () => call('linkMemberByCpf', 'mod', {
        establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'MOD',
      }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
    await assert.rejects(
      () => call('linkMemberByCpf', 'client', {
        establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'CLIENT',
      }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
  });

  it('9. MOD concede CLIENT; CLIENT concede USER', async () => {
    const a = await call('linkMemberByCpf', 'mod', {
      establishmentId: EST, cpf: CPF_LIVRE, name: 'Funcionario', role: 'CLIENT',
    });
    assert.equal(a.data.status, 'pending');

    const b = await call('linkMemberByCpf', 'client', {
      establishmentId: EST, cpf: CPF_DA_MARIA, name: 'Frequentador', role: 'USER',
    });
    assert.equal(b.data.status, 'pending');
  });

  it('10. MOD de um estabelecimento nao vincula em outro', async () => {
    await assert.rejects(
      () => call('linkMemberByCpf', 'mod', {
        establishmentId: OTHER_EST, cpf: CPF_LIVRE, name: 'X', role: 'CLIENT',
      }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
  });

  it('11. repetir a mesma vinculacao e idempotente', async () => {
    await call('linkMemberByCpf', 'adm', {
      establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'USER',
    });
    const { data } = await call('linkMemberByCpf', 'adm', {
      establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'USER',
    });

    assert.equal(data.status, 'already');
    assert.equal((await db.collection(`establishments/${EST}/preregistrations`).get()).size, 1);
  });

  it('12. parametro inesperado e recusado', async () => {
    await assert.rejects(
      () => call('linkMemberByCpf', 'adm', {
        establishmentId: EST, cpf: CPF_LIVRE, name: 'X', role: 'USER', targetUid: uids.adm,
      }),
      (err) => (assert.equal(err.code, 'functions/invalid-argument'), true),
    );
  });
});

describe('setMemberRole', () => {
  it('13. ADM promove USER a CLIENT', async () => {
    const { data } = await call('setMemberRole', 'adm', {
      establishmentId: EST, targetUid: uids.user, role: 'CLIENT',
    });

    assert.equal(data.status, 'changed');
    assert.equal((await member(uids.user)).role, 'CLIENT');
  });

  it('14. CLIENT nao rebaixa o MOD do estabelecimento', async () => {
    // Sem a checagem sobre o papel ATUAL do alvo, isto passaria: um CLIENT pode conceder
    // `USER`, e rebaixar o MOD para USER seria uma promocao disfarcada de remocao.
    await assert.rejects(
      () => call('setMemberRole', 'client', {
        establishmentId: EST, targetUid: uids.mod, role: 'USER',
      }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
    assert.equal((await member(uids.mod)).role, 'MOD');
  });

  it('15. ninguem altera o proprio papel', async () => {
    await assert.rejects(
      () => call('setMemberRole', 'adm', {
        establishmentId: EST, targetUid: uids.adm, role: 'MOD',
      }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
  });

  it('16. alvo que nao e membro devolve not-found', async () => {
    await assert.rejects(
      () => call('setMemberRole', 'adm', {
        establishmentId: EST, targetUid: uids.maria, role: 'USER',
      }),
      (err) => (assert.equal(err.code, 'functions/not-found'), true),
    );
  });
});

describe('removeMember e leaveEstablishment', () => {
  it('17. desligar marca active:false e preserva a trilha', async () => {
    const { data } = await call('removeMember', 'adm', {
      establishmentId: EST, targetUid: uids.user,
    });

    assert.equal(data.status, 'changed');
    const vinculo = await member(uids.user);
    // Documento preservado: as rules ja negam acesso com active:false, e o displayName e a
    // unica forma de saber depois quem era.
    assert.equal(vinculo.active, false);
    assert.equal(vinculo.displayName, 'User');
  });

  it('18. CLIENT desliga USER, mas nao MOD', async () => {
    const ok = await call('removeMember', 'client', {
      establishmentId: EST, targetUid: uids.user,
    });
    assert.equal(ok.data.status, 'changed');

    await assert.rejects(
      () => call('removeMember', 'client', { establishmentId: EST, targetUid: uids.mod }),
      (err) => (assert.equal(err.code, 'functions/permission-denied'), true),
    );
  });

  it('19. sair do estabelecimento nao pede permissao a ninguem', async () => {
    // E o remedio de quem foi vinculado sem pedir — o CPF digitado errado que alcancou a
    // pessoa errada. Sem isto, a saida dependeria de convencer quem fez o vinculo.
    const { data } = await call('leaveEstablishment', 'user', { establishmentId: EST });

    assert.equal(data.status, 'changed');
    assert.equal((await member(uids.user)).active, false);
  });

  it('20. remover a si mesmo aponta para a saida propria', async () => {
    await assert.rejects(
      () => call('removeMember', 'mod', { establishmentId: EST, targetUid: uids.mod }),
      (err) => (assert.equal(err.code, 'functions/invalid-argument'), true),
    );
  });

  it('21. desligar duas vezes e idempotente', async () => {
    await call('removeMember', 'adm', { establishmentId: EST, targetUid: uids.user });
    const { data } = await call('removeMember', 'adm', {
      establishmentId: EST, targetUid: uids.user,
    });

    assert.equal(data.status, 'unchanged');
  });
});

/**
 * Recalcula o HMAC do mesmo jeito que a function faz.
 *
 * Precisa espelhar `cpfHmac.ts` — inclusive o pepper de desenvolvimento, que so vale sob
 * `FUNCTIONS_EMULATOR`. Se os dois divergirem, o teste 5 passa a criar pendencia em vez de
 * vincular, e a falha aponta para o lugar errado.
 */
async function hashOf(cpf) {
  const { createHmac } = await import('node:crypto');
  return createHmac('sha256', 'pepper-de-desenvolvimento-nao-usar-em-producao')
    .update(cpf.replace(/\D/g, ''))
    .digest('hex');
}
