import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';

export interface BootstrapAccountResponse {
  role: string;
  created: boolean;
}

/**
 * Cria `users/{uid}` no primeiro acesso, com papel `USER`.
 *
 * **Sem payload.** O uid vem exclusivamente de `request.auth.uid`, pelo mesmo motivo de
 * `deleteMyAccount`: aceitar um uid no corpo seria a escalada de privilégio óbvia.
 *
 * Existe porque `users/{uid}` é `write: if false` nas rules — o cliente não pode criar o
 * próprio documento de papel, senão criaria com `role: 'ADM'`. Toda conta nasce aqui, com o
 * papel mais restrito, e só sobe por decisão de alguém que já tem poder para isso.
 *
 * ## Idempotência, e por que ela é uma garantia de segurança
 *
 * A função **nunca faz update**. Se o documento já existe, ela lê e devolve o que está lá.
 *
 * Isso não é economia de escrita: é o que impede a chamada repetida de rebaixar um
 * administrador. Um ADM que abrisse o app de novo, ou um atacante chamando a callable em
 * loop, faria um `set` com `role: 'USER'` por cima do papel real — e a conta mais poderosa do
 * sistema viraria a mais fraca, sem nada no log parecendo um ataque.
 */
export async function handleBootstrapAccount(
  request: CallableRequest<unknown>,
): Promise<BootstrapAccountResponse> {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError('unauthenticated', 'É preciso estar autenticado.');
  }

  const payload = request.data;
  if (payload && typeof payload === 'object' && Object.keys(payload).length > 0) {
    logger.warn('bootstrapAccount: payload rejeitado', { uid, keys: Object.keys(payload) });
    throw new HttpsError(
      'invalid-argument',
      'Esta operação não aceita parâmetros — a conta criada é sempre a do chamador.',
    );
  }

  const db = getFirestore();
  const ref = db.doc(`users/${uid}`);
  // O token vem tipado como DecodedIdToken, mas os campos que interessam aqui sao opcionais
  // e dependem do provedor — um login anonimo nao tem e-mail nem nome.
  const token = (request.auth?.token ?? {}) as Record<string, unknown>;
  const email = typeof token.email === 'string' ? token.email : '';

  try {
    // `create` em vez de `set`: falha se o documento existir, e é essa falha que garante
    // que nenhum papel existente seja sobrescrito. `set({merge:true})` não serviria — ele
    // gravaria `role` por cima igual.
    await ref.create({
      role: 'USER',
      name: displayNameFrom(token, email),
      email,
      provider: providerFrom(token),
      cpfHmac: null,
      createdAt: FieldValue.serverTimestamp(),
    });

    logger.info('bootstrapAccount: conta criada', { uid });
    return { role: 'USER', created: true };
  } catch (error) {
    if ((error as { code?: number | string }).code !== 6 &&
        (error as { code?: string }).code !== 'already-exists') {
      logger.error('bootstrapAccount: falha ao criar', { uid });
      throw new HttpsError('internal', 'Não foi possível preparar sua conta.');
    }

    // Já existia: devolve o papel real, sem tocar no documento.
    const existing = await ref.get();
    const role = existing.get('role');
    return { role: typeof role === 'string' ? role : 'USER', created: false };
  }
}

/** Como a pessoa entrou (`password`, `google.com`, …). Informativo, para suporte. */
function providerFrom(token: Record<string, unknown>): string {
  const firebase = token.firebase;
  if (typeof firebase === 'object' && firebase !== null) {
    const provider = (firebase as { sign_in_provider?: unknown }).sign_in_provider;
    if (typeof provider === 'string') return provider;
  }
  return 'unknown';
}

/** Nome do provedor, ou o trecho antes do `@`. É rótulo de exibição, não identidade. */
function displayNameFrom(token: Record<string, unknown>, email: string): string {
  const name = token.name;
  if (typeof name === 'string' && name.trim().length > 0) return name.trim();
  return email.split('@')[0] || 'Usuário';
}
