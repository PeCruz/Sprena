/**
 * Testes das Firestore Security Rules do Sprena (F1.4).
 *
 * Rode sempre pelo emulador — `npm run test:emulator` na pasta deste arquivo.
 * Nenhuma credencial real e usada: o prefixo `demo-` faz o emulador rodar 100%
 * offline, sem projeto no Firebase e sem `firebase login`.
 *
 * Modelo de acesso sob teste:
 *  - users/{uid}          → cada um le so o proprio doc; escrita e Console/Admin SDK apenas
 *  - sport_clients/{id}   → leitura para qualquer autenticado; escrita so ADM/MOD
 *  - user_consents/{uid}  → cada um le e grava so o proprio aceite; history e append-only
 *  - qualquer outra path  → default deny
 */
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

const RULES_PATH = new URL('../../firestore.rules', import.meta.url);

const ADM_UID = 'uid_adm';
const MOD_UID = 'uid_mod';
const CLIENT_UID = 'uid_client';
/** Autenticado no Firebase Auth mas sem doc em `users/` — o caso "Conta nao autorizada". */
const GHOST_UID = 'uid_sem_perfil';

let testEnv;

/** Firestore autenticado como `uid`. */
const as = (uid) => testEnv.authenticatedContext(uid).firestore();
/** Firestore sem autenticacao (usuario deslogado). */
const anon = () => testEnv.unauthenticatedContext().firestore();

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-sprena',
    firestore: { rules: readFileSync(RULES_PATH, 'utf8') },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users', ADM_UID), { role: 'ADM', name: 'Adm' });
    await setDoc(doc(db, 'users', MOD_UID), { role: 'MOD', name: 'Mod' });
    await setDoc(doc(db, 'users', CLIENT_UID), { role: 'CLIENT', name: 'Client' });
    await setDoc(doc(db, 'sport_clients', 'c1'), { name: 'Fulano', cpf: '00000000000' });
    await setDoc(doc(db, 'sport_clients', 'c_del'), { name: 'Para deletar' });
    await setDoc(doc(db, 'kanban_tasks', 't1'), { title: 'Colecao ainda nao mapeada' });
  });
});

describe('users/{uid}', () => {
  it('1. nega leitura para nao autenticado', async () => {
    await assertFails(getDoc(doc(anon(), 'users', CLIENT_UID)));
  });

  it('2. permite ler o proprio doc — resolve a role no login', async () => {
    await assertSucceeds(getDoc(doc(as(CLIENT_UID), 'users', CLIENT_UID)));
  });

  it('3. nega leitura do doc de outro usuario', async () => {
    await assertFails(getDoc(doc(as(CLIENT_UID), 'users', ADM_UID)));
  });

  it('4. nega auto-promocao de role', async () => {
    await assertFails(updateDoc(doc(as(CLIENT_UID), 'users', CLIENT_UID), { role: 'ADM' }));
  });

  it('4b. nega que ADM escreva em users pelo app', async () => {
    await assertFails(updateDoc(doc(as(ADM_UID), 'users', CLIENT_UID), { role: 'MOD' }));
  });
});

describe('sport_clients/{id}', () => {
  it('5. nega leitura para nao autenticado', async () => {
    await assertFails(getDoc(doc(anon(), 'sport_clients', 'c1')));
  });

  it('6. permite leitura para CLIENT', async () => {
    await assertSucceeds(getDoc(doc(as(CLIENT_UID), 'sport_clients', 'c1')));
  });

  it('7. nega create/update/delete para CLIENT', async () => {
    const db = as(CLIENT_UID);
    await assertFails(setDoc(doc(db, 'sport_clients', 'c_novo'), { name: 'Novo' }));
    await assertFails(updateDoc(doc(db, 'sport_clients', 'c1'), { name: 'Alterado' }));
    await assertFails(deleteDoc(doc(db, 'sport_clients', 'c_del')));
  });

  it('8a. permite create/update/delete para ADM', async () => {
    const db = as(ADM_UID);
    await assertSucceeds(setDoc(doc(db, 'sport_clients', 'c_novo'), { name: 'Novo' }));
    await assertSucceeds(updateDoc(doc(db, 'sport_clients', 'c1'), { name: 'Alterado' }));
    await assertSucceeds(deleteDoc(doc(db, 'sport_clients', 'c_del')));
  });

  it('8b. permite create/update/delete para MOD', async () => {
    const db = as(MOD_UID);
    await assertSucceeds(setDoc(doc(db, 'sport_clients', 'c_novo'), { name: 'Novo' }));
    await assertSucceeds(updateDoc(doc(db, 'sport_clients', 'c1'), { name: 'Alterado' }));
    await assertSucceeds(deleteDoc(doc(db, 'sport_clients', 'c_del')));
  });

  it('9. autenticado sem doc em users le, mas nao escreve', async () => {
    const db = as(GHOST_UID);
    await assertSucceeds(getDoc(doc(db, 'sport_clients', 'c1')));
    await assertFails(setDoc(doc(db, 'sport_clients', 'c_novo'), { name: 'Novo' }));
  });
});

describe('user_consents/{uid}', () => {
  const VERSION = '2026-08-12';
  const payload = () => ({
    uid: CLIENT_UID,
    policyVersion: VERSION,
    acceptedAt: serverTimestamp(),
    appVersion: '0.1.0',
  });

  it('11. le o proprio registro de consentimento — inclusive quando nao existe', async () => {
    await assertSucceeds(getDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID)));
  });

  it('12. nega ler o consentimento de outro usuario', async () => {
    await assertFails(getDoc(doc(as(CLIENT_UID), 'user_consents', ADM_UID)));
  });

  it('13. cria o proprio consentimento com payload valido', async () => {
    await assertSucceeds(setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), payload()));
  });

  it('14. nega criar consentimento em nome de outro uid', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', ADM_UID), { ...payload(), uid: ADM_UID }),
    );
  });

  it('15. nega delete do proprio consentimento', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'user_consents', CLIENT_UID), {
        uid: CLIENT_UID,
        policyVersion: VERSION,
      });
    });
    await assertFails(deleteDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID)));
  });

  /** Doc de historico ja gravado, com id automatico como em producao. */
  const seedHistory = async (id = 'acceptance_1') => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'user_consents', CLIENT_UID, 'history', id), {
        policyVersion: VERSION,
        acceptedAt: serverTimestamp(),
      });
    });
    return id;
  };

  it('16. nega update no historico — a trilha e append-only', async () => {
    const id = await seedHistory();
    await assertFails(
      updateDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history', id), {
        policyVersion: 'adulterado',
      }),
    );
  });

  it('17. aceita append no historico com id automatico', async () => {
    await assertSucceeds(
      addDoc(collection(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history'), {
        policyVersion: VERSION,
        acceptedAt: serverTimestamp(),
      }),
    );
  });

  it('18. reaceitar a mesma versao acrescenta um doc, nao conflita', async () => {
    const db = as(CLIENT_UID);
    const history = collection(db, 'user_consents', CLIENT_UID, 'history');
    const entry = () => ({ policyVersion: VERSION, acceptedAt: serverTimestamp() });

    await assertSucceeds(addDoc(history, entry()));
    await assertSucceeds(addDoc(history, entry()));

    const snapshot = await getDocs(history);
    if (snapshot.size !== 2) {
      throw new Error(`esperava 2 docs de historico, veio ${snapshot.size}`);
    }
  });

  it('19. nega delete no historico', async () => {
    const id = await seedHistory();
    await assertFails(deleteDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history', id)));
  });

  it('20. nega ler o historico de outro usuario', async () => {
    const id = await seedHistory();
    await assertFails(getDoc(doc(as(ADM_UID), 'user_consents', CLIENT_UID, 'history', id)));
  });
});

describe('default deny', () => {
  it('10. nega colecao nao mapeada mesmo para ADM', async () => {
    const db = as(ADM_UID);
    await assertFails(getDoc(doc(db, 'kanban_tasks', 't1')));
    await assertFails(setDoc(doc(db, 'kanban_tasks', 't2'), { title: 'Nova' }));
  });
});
