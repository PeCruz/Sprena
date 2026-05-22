package br.com.sprena.shared.sportclient.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — SportClientValidator (validação dos campos do cliente de esportes).
 *
 * Campos:
 * - Nome: obrigatório, max 100 chars
 * - CPF: obrigatório, exatamente 11 dígitos
 * - Telefone: obrigatório, 10-15 dígitos
 * - Modalidade: obrigatória (FUTEVOLEI, BEACH_TENNIS, VOLEI)
 * - Frequência: obrigatório, 1~4
 * - Pagamento: WELLHUB, TOTALPASS, CASH
 * - Valor em dinheiro: obrigatório (>= 0) para TODOS os métodos de pagamento
 * - Mês Pagamento: obrigatório, formato MM/YYYY
 */
class SportClientValidatorTest {
    // =========================================================================
    // validateName — obrigatório, max 100 chars
    // =========================================================================

    @Test
    fun `name valid returns Valid`() {
        val result = SportClientValidator.validateName("Pedro Cruz")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `name empty returns invalid`() {
        val result = SportClientValidator.validateName("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `name only spaces returns invalid`() {
        val result = SportClientValidator.validateName("   ")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `name at max length returns Valid`() {
        val name = "A".repeat(SportClientValidator.NAME_MAX_LENGTH)
        assertTrue(SportClientValidator.validateName(name).isValid)
    }

    @Test
    fun `name exceeds max length returns invalid`() {
        val name = "A".repeat(SportClientValidator.NAME_MAX_LENGTH + 1)
        val result = SportClientValidator.validateName(name)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.NAME_MAX_LENGTH}"))
    }

    // =========================================================================
    // validateCpf — obrigatório, exatamente 11 dígitos
    // =========================================================================

    @Test
    fun `cpf valid 11 digits returns Valid`() {
        val result = SportClientValidator.validateCpf("12345678901")
        assertTrue(result.isValid)
    }

    @Test
    fun `cpf with formatting returns Valid`() {
        val result = SportClientValidator.validateCpf("123.456.789-01")
        assertTrue(result.isValid)
    }

    @Test
    fun `cpf empty returns invalid`() {
        val result = SportClientValidator.validateCpf("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `cpf too few digits returns invalid`() {
        val result = SportClientValidator.validateCpf("1234567890")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.CPF_LENGTH}"))
    }

    @Test
    fun `cpf too many digits returns invalid`() {
        val result = SportClientValidator.validateCpf("123456789012")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.CPF_LENGTH}"))
    }

    // =========================================================================
    // validatePhone — obrigatório, 10-15 dígitos
    // =========================================================================

    @Test
    fun `phone valid 11 digits returns Valid`() {
        val result = SportClientValidator.validatePhone("11999998888")
        assertTrue(result.isValid)
    }

    @Test
    fun `phone with formatting returns Valid`() {
        val result = SportClientValidator.validatePhone("(11) 99999-8888")
        assertTrue(result.isValid)
    }

    @Test
    fun `phone empty returns invalid`() {
        val result = SportClientValidator.validatePhone("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `phone too few digits returns invalid`() {
        val result = SportClientValidator.validatePhone("123456789")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.PHONE_MIN_LENGTH}"))
    }

    @Test
    fun `phone at min length returns Valid`() {
        val result = SportClientValidator.validatePhone("1234567890")
        assertTrue(result.isValid)
    }

    @Test
    fun `phone at max length returns Valid`() {
        val result = SportClientValidator.validatePhone("1".repeat(SportClientValidator.PHONE_MAX_LENGTH))
        assertTrue(result.isValid)
    }

    @Test
    fun `phone exceeds max length returns invalid`() {
        val result = SportClientValidator.validatePhone("1".repeat(SportClientValidator.PHONE_MAX_LENGTH + 1))
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.PHONE_MAX_LENGTH}"))
    }

    // =========================================================================
    // validateModalidade — obrigatória
    // =========================================================================

    @Test
    fun `modalidade null returns invalid`() {
        val result = SportClientValidator.validateModalidade(null)
        assertFalse(result.isValid)
    }

    @Test
    fun `modalidade empty list returns invalid`() {
        val result = SportClientValidator.validateModalidade(emptyList())
        assertFalse(result.isValid)
    }

    @Test
    fun `modalidade single FUTEVOLEI returns Valid`() {
        assertTrue(SportClientValidator.validateModalidade(listOf(SportModality.FUTEVOLEI)).isValid)
    }

    @Test
    fun `modalidade single BEACH_TENNIS returns Valid`() {
        assertTrue(SportClientValidator.validateModalidade(listOf(SportModality.BEACH_TENNIS)).isValid)
    }

    @Test
    fun `modalidade single VOLEI returns Valid`() {
        assertTrue(SportClientValidator.validateModalidade(listOf(SportModality.VOLEI)).isValid)
    }

    @Test
    fun `modalidade multiple modalities returns Valid`() {
        assertTrue(
            SportClientValidator
                .validateModalidade(
                    listOf(SportModality.FUTEVOLEI, SportModality.BEACH_TENNIS, SportModality.VOLEI),
                ).isValid,
        )
    }

    // =========================================================================
    // validateAttendance — obrigatório, 1~4
    // =========================================================================

    @Test
    fun `attendance null returns invalid`() {
        val result = SportClientValidator.validateAttendance(null)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatória"))
    }

    @Test
    fun `attendance 0 returns invalid`() {
        val result = SportClientValidator.validateAttendance(0)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("1 a 4"))
    }

    @Test
    fun `attendance 5 returns invalid`() {
        val result = SportClientValidator.validateAttendance(5)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("1 a 4"))
    }

    @Test
    fun `attendance negative returns invalid`() {
        val result = SportClientValidator.validateAttendance(-1)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("1 a 4"))
    }

    @Test
    fun `attendance 1 returns Valid`() {
        assertTrue(SportClientValidator.validateAttendance(1).isValid)
    }

    @Test
    fun `attendance 2 returns Valid`() {
        assertTrue(SportClientValidator.validateAttendance(2).isValid)
    }

    @Test
    fun `attendance 3 returns Valid`() {
        assertTrue(SportClientValidator.validateAttendance(3).isValid)
    }

    @Test
    fun `attendance 4 returns Valid`() {
        assertTrue(SportClientValidator.validateAttendance(4).isValid)
    }

    // =========================================================================
    // validatePaymentMethod — obrigatório
    // =========================================================================

    @Test
    fun `paymentMethod null returns invalid`() {
        val result = SportClientValidator.validatePaymentMethod(null)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `paymentMethod CASH returns Valid`() {
        assertTrue(SportClientValidator.validatePaymentMethod(PaymentMethod.CASH).isValid)
    }

    @Test
    fun `paymentMethod WELLHUB returns Valid`() {
        assertTrue(SportClientValidator.validatePaymentMethod(PaymentMethod.WELLHUB).isValid)
    }

    @Test
    fun `paymentMethod TOTALPASS returns Valid`() {
        assertTrue(SportClientValidator.validatePaymentMethod(PaymentMethod.TOTALPASS).isValid)
    }

    // =========================================================================
    // validateCashAmount — obrigatório (>= 0) para TODOS os métodos
    // =========================================================================

    @Test
    fun `cashAmount negative returns invalid`() {
        val result = SportClientValidator.validateCashAmount(-1L, PaymentMethod.CASH)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("negativo"))
    }

    @Test
    fun `cashAmount null when payment is CASH returns invalid`() {
        val result = SportClientValidator.validateCashAmount(null, PaymentMethod.CASH)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `cashAmount zero when payment is CASH returns Valid`() {
        val result = SportClientValidator.validateCashAmount(0L, PaymentMethod.CASH)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount valid when payment is CASH returns Valid`() {
        val result = SportClientValidator.validateCashAmount(5000L, PaymentMethod.CASH)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount null when payment is WELLHUB returns invalid`() {
        val result = SportClientValidator.validateCashAmount(null, PaymentMethod.WELLHUB)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `cashAmount zero when payment is WELLHUB returns Valid`() {
        val result = SportClientValidator.validateCashAmount(0L, PaymentMethod.WELLHUB)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount valid when payment is WELLHUB returns Valid`() {
        val result = SportClientValidator.validateCashAmount(1500L, PaymentMethod.WELLHUB)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount null when payment is TOTALPASS returns invalid`() {
        val result = SportClientValidator.validateCashAmount(null, PaymentMethod.TOTALPASS)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `cashAmount zero when payment is TOTALPASS returns Valid`() {
        val result = SportClientValidator.validateCashAmount(0L, PaymentMethod.TOTALPASS)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount valid when payment is TOTALPASS returns Valid`() {
        val result = SportClientValidator.validateCashAmount(2000L, PaymentMethod.TOTALPASS)
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount exceeds max returns invalid`() {
        val result =
            SportClientValidator.validateCashAmount(
                SportClientValidator.MAX_CASH_CENTS + 1,
                PaymentMethod.WELLHUB,
            )
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("100.000"))
    }

    @Test
    fun `cashAmount at max returns Valid`() {
        val result =
            SportClientValidator.validateCashAmount(
                SportClientValidator.MAX_CASH_CENTS,
                PaymentMethod.WELLHUB,
            )
        assertTrue(result.isValid)
    }

    @Test
    fun `cashAmount exceeds max for CASH returns invalid`() {
        val result =
            SportClientValidator.validateCashAmount(
                SportClientValidator.MAX_CASH_CENTS + 1,
                PaymentMethod.CASH,
            )
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("100.000"))
    }

    // =========================================================================
    // validateLastPaymentMonth — obrigatório, formato MM/YYYY
    // =========================================================================

    @Test
    fun `lastPaymentMonth null returns invalid`() {
        val result = SportClientValidator.validateLastPaymentMonth(null)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `lastPaymentMonth empty returns invalid`() {
        val result = SportClientValidator.validateLastPaymentMonth("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `lastPaymentMonth valid format returns Valid`() {
        val result = SportClientValidator.validateLastPaymentMonth("04/2026")
        assertTrue(result.isValid)
    }

    @Test
    fun `lastPaymentMonth invalid format returns invalid`() {
        val result = SportClientValidator.validateLastPaymentMonth("2026-04")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("MM/AAAA"))
    }

    @Test
    fun `lastPaymentMonth invalid month 13 returns invalid`() {
        val result = SportClientValidator.validateLastPaymentMonth("13/2026")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("MM/AAAA"))
    }

    @Test
    fun `lastPaymentMonth invalid month 00 returns invalid`() {
        val result = SportClientValidator.validateLastPaymentMonth("00/2026")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("MM/AAAA"))
    }

    @Test
    fun `lastPaymentMonth month 01 returns Valid`() {
        assertTrue(SportClientValidator.validateLastPaymentMonth("01/2026").isValid)
    }

    @Test
    fun `lastPaymentMonth month 12 returns Valid`() {
        assertTrue(SportClientValidator.validateLastPaymentMonth("12/2026").isValid)
    }

    // =========================================================================
    // validateApelido — opcional, max 50 chars
    // =========================================================================

    @Test
    fun `apelido empty returns Valid`() {
        val result = SportClientValidator.validateApelido("")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `apelido null returns Valid`() {
        val result = SportClientValidator.validateApelido(null)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `apelido only spaces returns Valid`() {
        val result = SportClientValidator.validateApelido("   ")
        assertTrue(result.isValid)
    }

    @Test
    fun `apelido valid returns Valid`() {
        val result = SportClientValidator.validateApelido("Pedrinho")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `apelido at max length returns Valid`() {
        val apelido = "A".repeat(SportClientValidator.APELIDO_MAX_LENGTH)
        assertTrue(SportClientValidator.validateApelido(apelido).isValid)
    }

    @Test
    fun `apelido exceeds max length returns invalid`() {
        val apelido = "A".repeat(SportClientValidator.APELIDO_MAX_LENGTH + 1)
        val result = SportClientValidator.validateApelido(apelido)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${SportClientValidator.APELIDO_MAX_LENGTH}"))
    }
}
