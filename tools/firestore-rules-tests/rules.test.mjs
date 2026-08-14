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
 *  - user_profiles/{uid}  → cada um le e grava so o proprio perfil autodeclarado (F1.6a)
 *  - account_deletions/{} → so o Admin SDK dentro da Cloud Function; cliente nao toca
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

  // ---------------------------------------------------------------------------
  // Anti-adulteracao — as clausulas que sustentam o valor probatorio do registro.
  // Sem elas o aceite continua sendo gravado, mas deixa de provar o que alega:
  // quando foi dado (acceptedAt) e a que texto se refere (policyVersion).
  // ---------------------------------------------------------------------------

  /** Timestamp escolhido pelo cliente — o vetor de backdating. */
  const forgedTime = () => Timestamp.fromDate(new Date('2020-01-01T00:00:00Z'));

  it('21. nega acceptedAt com timestamp do cliente — anti-backdating', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), {
        ...payload(),
        acceptedAt: forgedTime(),
      }),
    );
  });

  it('22. nega acceptedAt forjado tambem no historico', async () => {
    await assertFails(
      addDoc(collection(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history'), {
        policyVersion: VERSION,
        acceptedAt: forgedTime(),
      }),
    );
  });

  it('23. nega acceptedAt ausente', async () => {
    const { acceptedAt, ...semTimestamp } = payload();
    await assertFails(setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), semTimestamp));
  });

  it('24. nega policyVersion vazia', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), { ...payload(), policyVersion: '' }),
    );
  });

  it('25. nega policyVersion de tipo errado', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), { ...payload(), policyVersion: 42 }),
    );
  });

  it('26. nega policyVersion vazia ou de tipo errado no historico', async () => {
    const history = collection(as(CLIENT_UID), 'user_consents', CLIENT_UID, 'history');
    await assertFails(addDoc(history, { policyVersion: '', acceptedAt: serverTimestamp() }));
    await assertFails(addDoc(history, { policyVersion: 42, acceptedAt: serverTimestamp() }));
  });

  it('27. nega campo uid divergente do dono do doc', async () => {
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_consents', CLIENT_UID), { ...payload(), uid: ADM_UID }),
    );
  });

  it('28. nega gravar consentimento sem autenticacao', async () => {
    await assertFails(setDoc(doc(anon(), 'user_consents', CLIENT_UID), payload()));
    await assertFails(getDoc(doc(anon(), 'user_consents', CLIENT_UID)));
  });
});

/**
 * F1.6a — perfil autodeclarado do titular.
 *
 * A colecao e separada de `users/{uid}` de proposito: la vive a role, e a rule de F1.4
 * nega toda escrita justamente para impedir auto-promocao. Aqui nao existe campo `role`,
 * entao nenhuma allowlist pode ser esquecida quando F1.7 adicionar `establishmentIds`.
 * O caso 41 e o que prova que essa garantia continua de pe.
 */
describe('user_profiles/{uid}', () => {
  const profile = (extra = {}) => ({
    apelido: 'Pe',
    cpf: '12345678900',
    phone: '11987654321',
    modalities: ['VOLEI'],
    updatedAt: serverTimestamp(),
    ...extra,
  });

  it('29. permite ler o proprio perfil mesmo quando nao existe', async () => {
    // Doc ausente precisa devolver sucesso com snapshot vazio, nao permission-denied:
    // e assim que o app distingue "nunca preencheu" de "sem permissao".
    await assertSucceeds(getDoc(doc(as(CLIENT_UID), 'user_profiles', CLIENT_UID)));
  });

  it('30. nega ler o perfil de outro usuario', async () => {
    await assertFails(getDoc(doc(as(CLIENT_UID), 'user_profiles', ADM_UID)));
  });

  it('31. permite criar o proprio perfil — o doc nasce no primeiro save', async () => {
    await assertSucceeds(setDoc(doc(as(CLIENT_UID), 'user_profiles', CLIENT_UID), profile()));
  });

  it('32. permite atualizar o proprio perfil ja existente', async () => {
    const db = as(CLIENT_UID);
    await assertSucceeds(setDoc(doc(db, 'user_profiles', CLIENT_UID), profile()));
    await assertSucceeds(
      setDoc(doc(db, 'user_profiles', CLIENT_UID), profile({ apelido: 'Pedro' })),
    );
  });

  it('33. nega criar perfil em nome de outro uid', async () => {
    await assertFails(setDoc(doc(as(CLIENT_UID), 'user_profiles', ADM_UID), profile()));
  });

  it('34. nega campo desconhecido — role e isAdmin nao entram por aqui', async () => {
    const db = as(CLIENT_UID);
    await assertFails(
      setDoc(doc(db, 'user_profiles', CLIENT_UID), profile({ role: 'ADM' })),
    );
    await assertFails(
      setDoc(doc(db, 'user_profiles', CLIENT_UID), profile({ isAdmin: true })),
    );
  });

  it('35. nega updatedAt forjado pelo cliente', async () => {
    await assertFails(
      setDoc(
        doc(as(CLIENT_UID), 'user_profiles', CLIENT_UID),
        profile({ updatedAt: Timestamp.fromDate(new Date('2020-01-01T00:00:00Z')) }),
      ),
    );
  });

  it('36. nega updatedAt ausente', async () => {
    const { updatedAt, ...semTimestamp } = profile();
    await assertFails(setDoc(doc(as(CLIENT_UID), 'user_profiles', CLIENT_UID), semTimestamp));
  });

  it('37. nega tipos errados em cpf e modalities', async () => {
    const db = as(CLIENT_UID);
    await assertFails(setDoc(doc(db, 'user_profiles', CLIENT_UID), profile({ cpf: 12345678900 })));
    await assertFails(
      setDoc(doc(db, 'user_profiles', CLIENT_UID), profile({ modalities: 'VOLEI' })),
    );
  });

  it('38. nega modalities com mais de 10 itens', async () => {
    const demais = Array.from({ length: 11 }, (_, i) => `M${i}`);
    await assertFails(
      setDoc(doc(as(CLIENT_UID), 'user_profiles', CLIENT_UID), profile({ modalities: demais })),
    );
  });

  it('39. nega delete do proprio perfil — a exclusao passa pela Cloud Function', async () => {
    const db = as(CLIENT_UID);
    await assertSucceeds(setDoc(doc(db, 'user_profiles', CLIENT_UID), profile()));
    await assertFails(deleteDoc(doc(db, 'user_profiles', CLIENT_UID)));
  });

  it('40. nega leitura e escrita sem autenticacao', async () => {
    await assertFails(getDoc(doc(anon(), 'user_profiles', CLIENT_UID)));
    await assertFails(setDoc(doc(anon(), 'user_profiles', CLIENT_UID), profile()));
  });

  it('41. abrir user_profiles nao afrouxou users — auto-promocao continua negada', async () => {
    const db = as(CLIENT_UID);
    await assertSucceeds(setDoc(doc(db, 'user_profiles', CLIENT_UID), profile()));
    await assertFails(updateDoc(doc(db, 'users', CLIENT_UID), { role: 'ADM' }));
    await assertFails(setDoc(doc(db, 'users', CLIENT_UID), { role: 'ADM', name: 'Client' }));
  });
});

describe('account_deletions/{uid}', () => {
  it('42. nega leitura e escrita para o dono e para ADM — trilha e do Admin SDK', async () => {
    await assertFails(getDoc(doc(as(CLIENT_UID), 'account_deletions', CLIENT_UID)));
    await assertFails(setDoc(doc(as(CLIENT_UID), 'account_deletions', CLIENT_UID), { uid: CLIENT_UID }));
    await assertFails(getDoc(doc(as(ADM_UID), 'account_deletions', CLIENT_UID)));
    await assertFails(setDoc(doc(as(ADM_UID), 'account_deletions', CLIENT_UID), { uid: CLIENT_UID }));
  });
});

describe('default deny', () => {
  it('10. nega colecao nao mapeada mesmo para ADM', async () => {
    const db = as(ADM_UID);
    await assertFails(getDoc(doc(db, 'kanban_tasks', 't1')));
    await assertFails(setDoc(doc(db, 'kanban_tasks', 't2'), { title: 'Nova' }));
  });
});
