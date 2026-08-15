import * as logger from 'firebase-functions/logger';

/**
 * Anonimiza os registros financeiros históricos do titular (LGPD art. 16, I).
 *
 * A política é: os lançamentos **permanecem** para integridade contábil, mas perdem o
 * vínculo com o titular (uid, nome, CPF). Excluí-los quebraria o fechamento; mantê-los
 * identificados contrariaria o direito de eliminação.
 *
 * HOJE ISSO ANONIMIZA ZERO REGISTROS. `financial`, `bar` e `menu` são in-memory no app
 * e não existem no Firestore — não há o que anonimizar. Esta função existe agora, e não
 * depois, por dois motivos:
 *
 *  1. Ela é chamada **antes** dos deletes, porque anonimizar exige a identidade que os
 *     passos seguintes destroem. Descobrir isso depois custaria reordenar o fluxo.
 *  2. Quando F2 migrar essas coleções para o Firestore, a implementação entra aqui e
 *     nada mais do fluxo de exclusão muda.
 *
 * O retorno vai para `account_deletions.financialAnonymized`, o que torna a afirmação
 * do SECURITY.md falsificável: enquanto for 0, o controle é declaradamente vazio.
 */
export async function anonymizeFinancial(uid: string): Promise<number> {
  logger.info('anonymizeFinancial: nada a anonimizar', {
    uid,
    anonymized: 0,
    reason: 'financial-not-in-firestore',
  });
  return 0;
}
