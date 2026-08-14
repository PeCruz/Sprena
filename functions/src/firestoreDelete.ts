import { getFirestore } from 'firebase-admin/firestore';

/**
 * Apaga todos os documentos de uma subcoleção, em lotes.
 *
 * O `BulkWriter` do Admin SDK já agrupa e faz retry; o que este helper garante é que a
 * coleção seja varrida por páginas em vez de carregada inteira na memória — uma trilha
 * de consentimento pode ter centenas de aceites.
 */
export async function deleteCollection(path: string, batchSize = 500): Promise<number> {
  const db = getFirestore();
  const collection = db.collection(path);
  let deleted = 0;

  for (;;) {
    const snapshot = await collection.limit(batchSize).get();
    if (snapshot.empty) {
      return deleted;
    }

    const writer = db.bulkWriter();
    snapshot.docs.forEach((doc) => {
      void writer.delete(doc.ref);
    });
    await writer.close();
    deleted += snapshot.size;

    // Página incompleta significa que a coleção acabou.
    if (snapshot.size < batchSize) {
      return deleted;
    }
  }
}
