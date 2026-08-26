/**
 * Validação de CPF no servidor.
 *
 * Duplica deliberadamente o `CpfValidator` do Kotlin. O cliente valida para dar retorno
 * imediato ao usuário; aqui se valida porque o cliente não é confiável — a callable pode ser
 * chamada direto pela API, sem passar pelo app.
 *
 * É também o primeiro filtro do `linkMemberByCpf`: um número que não fecha os dígitos
 * verificadores é recusado antes de gastar rate limit, leitura ou escrita.
 */

const CPF_LENGTH = 11;
const CPF_BASE_LENGTH = 9;
const MODULUS = 11;
const NO_REMAINDER_THRESHOLD = 2;

/** Só os dígitos — é esta forma que entra no HMAC e vira chave de busca. */
export function cpfDigits(raw: string): string {
  return raw.replace(/\D/g, '');
}

export function isValidCpf(raw: string): boolean {
  const digits = cpfDigits(raw);
  if (digits.length !== CPF_LENGTH) return false;
  // Sequências repetidas fecham a aritmética dos dois verificadores (111.111.111-11 é
  // "válido" pelo algoritmo), então precisam de recusa explícita.
  if (digits.split('').every((d) => d === digits[0])) return false;

  return (
    digits[CPF_BASE_LENGTH] === checkDigit(digits, CPF_BASE_LENGTH, 10) &&
    digits[CPF_BASE_LENGTH + 1] === checkDigit(digits, CPF_BASE_LENGTH + 1, 11)
  );
}

/**
 * `***.456.789-**` — o que se pode guardar e mostrar de um CPF sem ter o número.
 *
 * O pré-cadastro é criado antes de a pessoa existir no sistema, então ela não consentiu com
 * nada. Guardar o CPF inteiro ali seria PII de quem nunca foi perguntado; a máscara é o
 * suficiente para o Client reconhecer quem cadastrou.
 */
export function maskCpf(raw: string): string {
  const digits = cpfDigits(raw);
  if (digits.length !== CPF_LENGTH) return '***.***.***-**';
  return `***.${digits.slice(3, 6)}.${digits.slice(6, 9)}-**`;
}

function checkDigit(digits: string, upTo: number, firstWeight: number): string {
  let sum = 0;
  for (let i = 0; i < upTo; i += 1) {
    sum += Number(digits[i]) * (firstWeight - i);
  }
  const remainder = sum % MODULUS;
  return remainder < NO_REMAINDER_THRESHOLD ? '0' : String(MODULUS - remainder);
}
