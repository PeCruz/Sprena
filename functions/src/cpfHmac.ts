import { createHmac } from 'node:crypto';
import { defineSecret } from 'firebase-functions/params';
import { cpfDigits } from './cpf';

/**
 * Pepper do HMAC de CPF.
 *
 * Precisa existir no Secret Manager antes do deploy:
 * `openssl rand -base64 32 | firebase functions:secrets:set CPF_PEPPER`
 *
 * **O valor nunca pode mudar depois que houver pré-cadastro.** O HMAC é o *id do documento*,
 * então trocar o pepper torna toda pendência irreclamável — o CPF da pessoa passa a gerar
 * outro id e ela nunca encontra o próprio vínculo. Rotacionar seria migração de dados.
 */
export const CPF_PEPPER = defineSecret('CPF_PEPPER');

/** Valor fixo de desenvolvimento, usado só no emulador. Nunca vale em produção. */
const EMULATOR_PEPPER = 'pepper-de-desenvolvimento-nao-usar-em-producao';

/**
 * Identificador derivado do CPF, usado como id de documento.
 *
 * O CPF tem 11 dígitos, dos quais 2 são verificadores — são cerca de 10⁹ combinações
 * possíveis. Um hash sem segredo seria varrido por força bruta em minutos por quem
 * conseguisse ler a coleção, e voltaríamos a ter CPF em claro. O pepper fica só no servidor,
 * então o mesmo ataque exige também vazar o Secret Manager.
 *
 * É por isso que o cliente nunca calcula este valor: ele manda o CPF pela callable, e só o
 * servidor sabe transformá-lo em id.
 */
export function cpfHmac(rawCpf: string): string {
  return createHmac('sha256', resolvePepper()).update(cpfDigits(rawCpf)).digest('hex');
}

/**
 * No emulador, **não** se chama `CPF_PEPPER.value()`.
 *
 * O parâmetro de segredo resolve indo ao Secret Manager, e o projeto `demo-*` do emulador não
 * existe lá — a chamada volta `403` e derruba a função, com um erro que parece falta de
 * permissão da conta de serviço e não é.
 */
function resolvePepper(): string {
  if (process.env.FUNCTIONS_EMULATOR === 'true') {
    return process.env.CPF_PEPPER || EMULATOR_PEPPER;
  }

  const pepper = CPF_PEPPER.value();
  if (!pepper) {
    throw new Error('CPF_PEPPER ausente — o segredo precisa existir antes do deploy.');
  }
  return pepper;
}
