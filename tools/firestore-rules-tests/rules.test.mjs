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
 *  - establishments/{id}  → membro le o proprio; so ADM lista e escreve (F1.7.1)
 *  - .../members/{uid}    → membro le, ninguem escreve: a aresta de autorizacao e da callable
 *  - user_settings/{uid}  → contexto ativo do dono; nenhuma rule deste arquivo le esta colecao
 *  - cnpj_index/{cnpj}    → so create de ADM; leitura negada para nao virar oraculo de CNPJ
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
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

const RULES_PATH = new URL('../../firestore.rules', import.meta.url);

const ADM_UID = 'uid_adm';
const MOD_UID = 'uid_mod';
const CLIENT_UID = 'uid_client';
/** Autenticado no Firebase Auth mas sem doc em `users/` — o caso "Conta nao autorizada". */
const GHOST_UID = 'uid_sem_perfil';

// F1.7.1 — multi-tenancy.
/** Membro de EST_A com role USER (o papel que so chega ao Kotlin em F1.7.3). */
const USER_UID = 'uid_user';
/** Autenticado e com doc em `users/`, porem sem membership em estabelecimento nenhum. */
const OUTSIDER_UID = 'uid_outsider';
/** Membro de EST_A com `active: false` — desligado, nao deve valer como membro. */
const INACTIVE_UID = 'uid_inativo';
const EST_A = 'est_a';
const EST_B = 'est_b';

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

    // F1.7.1 — grafo de estabelecimentos e membros.
    //
    // `role: 'USER'` ja aparece aqui porque as rules so comparam com 'ADM'; a constante
    // Kotlin `UserRole.USER` so nasce em F1.7.3, junto das rules que a restringem.
    await setDoc(doc(db, 'users', USER_UID), { role: 'USER', name: 'User' });
    await setDoc(doc(db, 'users', OUTSIDER_UID), { role: 'USER', name: 'Outsider' });
    await setDoc(doc(db, 'users', INACTIVE_UID), { role: 'USER', name: 'Inativo' });

    for (const estId of [EST_A, EST_B]) {
      await setDoc(doc(db, 'establishments', estId), {
        name: `Estabelecimento ${estId}`,
        active: true,
        cnpj: '11222333000181',
        razaoSocial: 'Razao Social LTDA',
        phone: '11987654321',
        email: 'contato@exemplo.com',
      });
    }

    const membersA = (uid, role, active = true) =>
      setDoc(doc(db, 'establishments', EST_A, 'members', uid), { uid, role, active });
    await membersA(MOD_UID, 'MOD');
    await membersA(CLIENT_UID, 'CLIENT');
    await membersA(USER_UID, 'USER');
    await membersA(INACTIVE_UID, 'USER', false);
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

/**
 * F1.7.1 — estabelecimentos e grafo de membros.
 *
 * O tenant vive no PATH (`establishments/{estId}/...`), nunca num campo. E isso que
 * permite `isMemberOf(estId)` ser um get() de path determinado: 1 leitura, cacheada
 * dentro da avaliacao, entao uma query de N documentos continua custando 1 get.
 *
 * `members` e a aresta de autorizacao do sistema — quem esta em qual estabelecimento e
 * com que papel. Por isso e `write: if false`: toda mutacao passa por callable (F1.7.3),
 * o que da um unico ponto de decisao e auditoria garantida. As rules LEEM o grafo,
 * nunca o escrevem.
 */
describe('establishments/{estId}', () => {
  const establishment = (extra = {}) => ({
    name: 'Bar do Ze',
    active: true,
    cnpj: '11222333000181',
    razaoSocial: 'Ze Bebidas LTDA',
    address: { street: 'Rua A', number: '10', city: 'Sao Paulo', state: 'SP' },
    phone: '11987654321',
    email: 'ze@exemplo.com',
    updatedAt: serverTimestamp(),
    ...extra,
  });

  it('43. nega leitura para nao autenticado', async () => {
    await assertFails(getDoc(doc(anon(), 'establishments', EST_A)));
  });

  it('44. permite ADM ler qualquer estabelecimento', async () => {
    await assertSucceeds(getDoc(doc(as(ADM_UID), 'establishments', EST_A)));
    await assertSucceeds(getDoc(doc(as(ADM_UID), 'establishments', EST_B)));
  });

  it('45. permite que qualquer membro leia o proprio estabelecimento', async () => {
    for (const uid of [MOD_UID, CLIENT_UID, USER_UID]) {
      await assertSucceeds(getDoc(doc(as(uid), 'establishments', EST_A)));
    }
  });

  it('46. nega leitura para autenticado sem membership', async () => {
    await assertFails(getDoc(doc(as(OUTSIDER_UID), 'establishments', EST_A)));
  });

  it('47. nega que membro de um estabelecimento leia outro', async () => {
    await assertFails(getDoc(doc(as(MOD_UID), 'establishments', EST_B)));
  });

  it('48. permite list so para ADM — membro enxerga pelo proprio member doc', async () => {
    await assertSucceeds(getDocs(collection(as(ADM_UID), 'establishments')));
    await assertFails(getDocs(collection(as(MOD_UID), 'establishments')));
  });

  it('49. permite ADM criar com payload valido', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADM_UID), 'establishments', 'est_novo'), establishment()),
    );
  });

  it('50. nega criar e atualizar para MOD e CLIENT', async () => {
    for (const uid of [MOD_UID, CLIENT_UID, USER_UID]) {
      const db = as(uid);
      await assertFails(setDoc(doc(db, 'establishments', 'est_novo'), establishment()));
      await assertFails(updateDoc(doc(db, 'establishments', EST_A), { name: 'Renomeado' }));
    }
  });

  it('51. nega campo desconhecido — o cliente nao inventa campo de autorizacao', async () => {
    const db = as(ADM_UID);
    await assertFails(
      setDoc(doc(db, 'establishments', 'est_novo'), establishment({ ownerRole: 'ADM' })),
    );
    await assertFails(
      setDoc(doc(db, 'establishments', 'est_novo'), establishment({ members: [ADM_UID] })),
    );
  });

  it('52. nega nome vazio ou ausente', async () => {
    const db = as(ADM_UID);
    await assertFails(setDoc(doc(db, 'establishments', 'est_novo'), establishment({ name: '' })));
    const { name, ...semNome } = establishment();
    await assertFails(setDoc(doc(db, 'establishments', 'est_novo'), semNome));
  });

  it('53. nega CNPJ fora do formato de 14 digitos', async () => {
    const db = as(ADM_UID);
    for (const cnpj of ['11.222.333/0001-81', '1122233300018', '', 11222333000181]) {
      await assertFails(setDoc(doc(db, 'establishments', 'est_novo'), establishment({ cnpj })));
    }
  });

  it('54. nega telefone e email fora do formato', async () => {
    const db = as(ADM_UID);
    await assertFails(
      setDoc(doc(db, 'establishments', 'est_novo'), establishment({ phone: '(11) 98765-4321' })),
    );
    await assertFails(
      setDoc(doc(db, 'establishments', 'est_novo'), establishment({ email: 42 })),
    );
  });

  it('55. nega updatedAt forjado ou ausente — anti-backdating', async () => {
    const db = as(ADM_UID);
    await assertFails(
      setDoc(
        doc(db, 'establishments', 'est_novo'),
        establishment({ updatedAt: Timestamp.fromDate(new Date('2020-01-01T00:00:00Z')) }),
      ),
    );
    const { updatedAt, ...semTimestamp } = establishment();
    await assertFails(setDoc(doc(db, 'establishments', 'est_novo'), semTimestamp));
  });

  it('56. permite ADM atualizar — desativar e active:false, nao delete', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADM_UID), 'establishments', EST_A), establishment({ active: false })),
    );
  });

  it('57. nega delete ate para ADM', async () => {
    await assertFails(deleteDoc(doc(as(ADM_UID), 'establishments', EST_A)));
  });
});

describe('establishments/{estId}/members/{uid}', () => {
  it('58. permite que membro leia os membros do proprio estabelecimento', async () => {
    const db = as(MOD_UID);
    await assertSucceeds(getDoc(doc(db, 'establishments', EST_A, 'members', CLIENT_UID)));
    await assertSucceeds(getDocs(collection(db, 'establishments', EST_A, 'members')));
  });

  it('59. nega ler membros de estabelecimento onde nao e membro', async () => {
    await assertFails(getDocs(collection(as(MOD_UID), 'establishments', EST_B, 'members')));
    await assertFails(
      getDocs(collection(as(OUTSIDER_UID), 'establishments', EST_A, 'members')),
    );
  });

  it('60. permite ADM ler membros de qualquer estabelecimento', async () => {
    await assertSucceeds(getDocs(collection(as(ADM_UID), 'establishments', EST_A, 'members')));
  });

  it('61. nega toda escrita em members — inclusive ADM; a mutacao e da callable', async () => {
    for (const uid of [ADM_UID, MOD_UID, CLIENT_UID]) {
      const db = as(uid);
      await assertFails(
        setDoc(doc(db, 'establishments', EST_A, 'members', OUTSIDER_UID), {
          uid: OUTSIDER_UID,
          role: 'MOD',
          active: true,
        }),
      );
      await assertFails(
        updateDoc(doc(db, 'establishments', EST_A, 'members', USER_UID), { role: 'MOD' }),
      );
      await assertFails(deleteDoc(doc(db, 'establishments', EST_A, 'members', USER_UID)));
    }
  });

  it('62. membro com active:false nao vale como membro', async () => {
    await assertFails(getDoc(doc(as(INACTIVE_UID), 'establishments', EST_A)));
    await assertFails(
      getDocs(collection(as(INACTIVE_UID), 'establishments', EST_A, 'members')),
    );
  });

  it('63. permite ler o proprio member doc pelo collection group — alimenta o seletor', async () => {
    const meus = query(collectionGroup(as(USER_UID), 'members'), where('uid', '==', USER_UID));
    await assertSucceeds(getDocs(meus));
  });

  it('64. nega collection group que alcance o member doc de outro uid', async () => {
    const db = as(USER_UID);
    await assertFails(getDocs(collectionGroup(db, 'members')));
    await assertFails(
      getDocs(query(collectionGroup(db, 'members'), where('uid', '==', MOD_UID))),
    );
  });
});

/**
 * Preferencia de contexto ativo do seletor global.
 *
 * Nao pode morar em `users` (write: if false) nem em `user_profiles` — a allowlist de la
 * e exatamente o reflexo que o comentario de F1.6a pede para nao ter. Vive aqui, escrita
 * pelo proprio dono, sustentada por uma invariante: NENHUMA rule deste arquivo le
 * `user_settings`. E o caso 70 que prova que apontar para um estabelecimento alheio
 * continua sendo inutil.
 */
describe('user_settings/{uid}', () => {
  const settings = (extra = {}) => ({
    activeEstablishmentId: EST_A,
    updatedAt: serverTimestamp(),
    ...extra,
  });

  it('65. permite ao dono criar e ler a propria preferencia', async () => {
    const db = as(USER_UID);
    await assertSucceeds(setDoc(doc(db, 'user_settings', USER_UID), settings()));
    await assertSucceeds(getDoc(doc(db, 'user_settings', USER_UID)));
  });

  it('66. nega ler ou escrever a preferencia de outro usuario', async () => {
    const db = as(USER_UID);
    await assertFails(getDoc(doc(db, 'user_settings', MOD_UID)));
    await assertFails(setDoc(doc(db, 'user_settings', MOD_UID), settings()));
  });

  it('67. nega chave desconhecida — role nao entra por aqui', async () => {
    await assertFails(
      setDoc(doc(as(USER_UID), 'user_settings', USER_UID), settings({ role: 'ADM' })),
    );
  });

  it('68. nega updatedAt forjado', async () => {
    await assertFails(
      setDoc(
        doc(as(USER_UID), 'user_settings', USER_UID),
        settings({ updatedAt: Timestamp.fromDate(new Date('2020-01-01T00:00:00Z')) }),
      ),
    );
  });

  it('69. permite limpar o contexto com null e nega delete do doc', async () => {
    const db = as(USER_UID);
    await assertSucceeds(
      setDoc(doc(db, 'user_settings', USER_UID), settings({ activeEstablishmentId: null })),
    );
    await assertFails(deleteDoc(doc(db, 'user_settings', USER_UID)));
  });

  it('70. apontar para estabelecimento alheio e permitido e nao da acesso a ele', async () => {
    const db = as(USER_UID);
    await assertSucceeds(
      setDoc(doc(db, 'user_settings', USER_UID), settings({ activeEstablishmentId: EST_B })),
    );
    await assertFails(getDoc(doc(db, 'establishments', EST_B)));
    await assertFails(getDocs(collection(db, 'establishments', EST_B, 'members')));
  });
});

/**
 * Unicidade de CNPJ. O doc id e o proprio CNPJ, entao `create` sobre um id existente
 * falha nativamente — e a unica forma de garantir unicidade sem uma callable. Leitura e
 * negada para todo mundo: um indice legivel viraria oraculo de "este CNPJ ja e cliente".
 */
describe('cnpj_index/{cnpj}', () => {
  it('71. nega leitura para todos, inclusive ADM', async () => {
    await assertFails(getDoc(doc(as(ADM_UID), 'cnpj_index', '11222333000181')));
    await assertFails(getDocs(collection(as(ADM_UID), 'cnpj_index')));
  });

  it('72. permite ADM criar a entrada de indice', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADM_UID), 'cnpj_index', '11222333000181'), {
        establishmentId: EST_A,
        createdAt: serverTimestamp(),
      }),
    );
  });

  it('73. nega sobrescrever indice existente — e isso que garante a unicidade', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'cnpj_index', '11222333000181'), {
        establishmentId: EST_A,
      });
    });
    const db = as(ADM_UID);
    await assertFails(
      setDoc(doc(db, 'cnpj_index', '11222333000181'), { establishmentId: 'est_outro' }),
    );
    await assertFails(deleteDoc(doc(db, 'cnpj_index', '11222333000181')));
  });

  it('74. nega criar para MOD', async () => {
    await assertFails(
      setDoc(doc(as(MOD_UID), 'cnpj_index', '99888777000166'), { establishmentId: EST_A }),
    );
  });
});

describe('default deny', () => {
  it('10. nega colecao nao mapeada mesmo para ADM', async () => {
    const db = as(ADM_UID);
    await assertFails(getDoc(doc(db, 'kanban_tasks', 't1')));
    await assertFails(setDoc(doc(db, 'kanban_tasks', 't2'), { title: 'Nova' }));
  });
});
