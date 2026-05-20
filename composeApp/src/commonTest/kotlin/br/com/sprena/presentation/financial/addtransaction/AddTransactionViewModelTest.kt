package br.com.sprena.presentation.financial.addtransaction

import app.cash.turbine.test
import br.com.sprena.presentation.financial.FinancialTransactionSummary
import br.com.sprena.presentation.financial.TransactionType
import br.com.sprena.presentation.financial.addtransaction.DateGranularity
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — AddTransactionViewModel
 *
 * Campos:
 *  - Name: obrigatório, máx 50
 *  - Amount: obrigatório, > 0 (digits → cents)
 *  - PersonName: obrigatório, máx 50
 *  - Description: opcional, máx 3000
 *  - Type: INCOME / EXPENSE (default EXPENSE)
 *  - InputDate: dia atual (auto)
 */
class AddTransactionViewModelTest {
    private val env = MainDispatcherEnv()
    private val today: Long = 20_000L

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm() = AddTransactionViewModel(today = { today })

    // ── Initial State ─────────────────────────────────────

    @Test
    fun `initial inputEpochDay is today`() =
        runTest {
            assertEquals(today, vm().state.first().inputEpochDay)
        }

    @Test
    fun `initial name is empty`() =
        runTest {
            assertEquals("", vm().state.first().name)
        }

    @Test
    fun `initial nameError is null`() =
        runTest {
            assertNull(vm().state.first().nameError)
        }

    @Test
    fun `initial amountRaw is empty`() =
        runTest {
            assertEquals("", vm().state.first().amountRaw)
        }

    @Test
    fun `initial amountCents is zero`() =
        runTest {
            assertEquals(0L, vm().state.first().amountCents)
        }

    @Test
    fun `initial canSubmit is false`() =
        runTest {
            assertFalse(vm().state.first().canSubmit)
        }

    @Test
    fun `initial personName is empty`() =
        runTest {
            assertEquals("", vm().state.first().personName)
        }

    @Test
    fun `initial category is empty`() =
        runTest {
            assertEquals("", vm().state.first().category)
        }

    @Test
    fun `initial description is empty`() =
        runTest {
            assertEquals("", vm().state.first().description)
        }

    // ── Name — obrigatório, máx 50 ──────────────────────

    @Test
    fun `name empty sets nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged(""))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `name blank sets nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("   "))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `name exceeding 50 chars sets nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("a".repeat(51)))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `name at boundary of 50 chars clears error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("a".repeat(50)))
            assertNull(vm.state.first().nameError)
        }

    @Test
    fun `valid name updates state and clears error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra mensal"))
            val s = vm.state.first()
            assertEquals("Compra mensal", s.name)
            assertNull(s.nameError)
        }

    // ── Amount — Negative (digits-only input) ────────────

    @Test
    fun `amount empty sets amountError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged(""))
            assertNotNull(vm.state.first().amountError)
        }

    @Test
    fun `amount with letters filters to empty and sets error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("abc"))
            assertNotNull(vm.state.first().amountError)
        }

    @Test
    fun `amount zero sets amountError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("0"))
            assertNotNull(vm.state.first().amountError)
        }

    // ── Amount — Positive (digits-only input) ────────────

    @Test
    fun `valid digits parse to cents and clear error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("123456"))
            val s = vm.state.first()
            assertNull(s.amountError)
            assertEquals(123_456L, s.amountCents)
        }

    @Test
    fun `minimum non-zero amount is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("1"))
            val s = vm.state.first()
            assertNull(s.amountError)
            assertEquals(1L, s.amountCents)
        }

    @Test
    fun `amount with mixed digits and letters stores only digits`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("1a2b3"))
            val s = vm.state.first()
            assertEquals("123", s.amountRaw)
            assertEquals(123L, s.amountCents)
            assertNull(s.amountError)
        }

    @Test
    fun `amount large value stores correct cents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("999999999"))
            val s = vm.state.first()
            assertEquals(999_999_999L, s.amountCents)
            assertNull(s.amountError)
        }

    // ── PersonName — obrigatório, máx 50 ─────────────────

    @Test
    fun `personName empty sets personNameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.PersonNameChanged(""))
            assertNotNull(vm.state.first().personNameError)
        }

    @Test
    fun `personName blank sets personNameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("   "))
            assertNotNull(vm.state.first().personNameError)
        }

    @Test
    fun `personName exceeding 50 chars sets personNameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("a".repeat(51)))
            assertNotNull(vm.state.first().personNameError)
        }

    @Test
    fun `personName at boundary of 50 chars clears error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("a".repeat(50)))
            assertNull(vm.state.first().personNameError)
        }

    @Test
    fun `valid personName updates state and clears error`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro Cruz"))
            val s = vm.state.first()
            assertEquals("Pedro Cruz", s.personName)
            assertNull(s.personNameError)
        }

    // ── Description — opcional, máx 3000 ─────────────────

    @Test
    fun `description empty is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DescriptionChanged(""))
            assertNull(vm.state.first().descriptionError)
        }

    @Test
    fun `description at boundary of 3000 is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DescriptionChanged("d".repeat(3000)))
            assertNull(vm.state.first().descriptionError)
        }

    @Test
    fun `description exceeding 3000 sets descriptionError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DescriptionChanged("d".repeat(3001)))
            assertNotNull(vm.state.first().descriptionError)
        }

    // ── Category ─────────────────────────────────────────

    @Test
    fun `CategoryChanged updates category in state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.CategoryChanged("Mercado"))
            assertEquals("Mercado", vm.state.first().category)
        }

    @Test
    fun `CategoryChanged can be updated multiple times`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.CategoryChanged("Mercado"))
            vm.handleIntent(AddTransactionIntent.CategoryChanged("Salário"))
            assertEquals("Salário", vm.state.first().category)
        }

    // ── Type ─────────────────────────────────────────────

    @Test
    fun `default type is INCOME`() =
        runTest {
            assertEquals(TransactionType.INCOME, vm().state.first().type)
        }

    @Test
    fun `TypeChanged to INCOME updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.INCOME))
            assertEquals(TransactionType.INCOME, vm.state.first().type)
        }

    @Test
    fun `TypeChanged to EXPENSE updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.INCOME))
            vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.EXPENSE))
            assertEquals(TransactionType.EXPENSE, vm.state.first().type)
        }

    // ── Submit / canSubmit ───────────────────────────────

    @Test
    fun `canSubmit is false when name is missing`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit is false when amount is missing`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit is false when personName is missing`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit is false when name, amount and personName are valid but date is missing`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra mensal"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit is true when name, amount, personName and date are valid`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra mensal"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `submit with invalid form emits ShowError`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTransactionIntent.Submit)
                val e = awaitItem()
                assertTrue(e is AddTransactionEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with valid form emits TransactionCreated with correct data`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
                vm.handleIntent(AddTransactionIntent.AmountChanged("2500"))
                vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
                vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.INCOME))
                vm.handleIntent(AddTransactionIntent.CategoryChanged("Serviços"))
                vm.handleIntent(AddTransactionIntent.DescriptionChanged("Nota fiscal"))
                vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
                vm.handleIntent(AddTransactionIntent.Submit)
                val e = awaitItem()
                assertTrue(e is AddTransactionEffect.TransactionCreated)
                val tx = e.transaction
                assertEquals("Venda", tx.description)
                assertEquals(2_500L, tx.cents)
                assertEquals(TransactionType.INCOME, tx.type)
                assertEquals("Pedro", tx.personName)
                assertEquals("Serviços", tx.category)
                assertEquals("Nota fiscal", tx.notes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with EXPENSE type emits correct type in transaction`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
                vm.handleIntent(AddTransactionIntent.AmountChanged("5000"))
                vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
                vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.EXPENSE))
                vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
                vm.handleIntent(AddTransactionIntent.Submit)
                val e = awaitItem()
                assertTrue(e is AddTransactionEffect.TransactionCreated)
                assertEquals(TransactionType.EXPENSE, e.transaction.type)
                assertEquals("Compra", e.transaction.description)
                assertEquals(5_000L, e.transaction.cents)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismiss emits Dismissed`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTransactionIntent.Dismiss)
                assertEquals(AddTransactionEffect.Dismissed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── canSubmit transitions ────────────────────────────

    @Test
    fun `canSubmit becomes false when name is cleared after being valid`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(AddTransactionIntent.NameChanged(""))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit becomes false when amount is cleared after being valid`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(AddTransactionIntent.AmountChanged(""))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit becomes false when personName is cleared after being valid`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(AddTransactionIntent.PersonNameChanged(""))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false when description exceeds max length`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(AddTransactionIntent.DescriptionChanged("d".repeat(3001)))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit remains true with valid optional description`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            vm.handleIntent(AddTransactionIntent.DescriptionChanged("Pagamento fornecedor"))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true regardless of category value`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            vm.handleIntent(AddTransactionIntent.CategoryChanged("Serviços"))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true regardless of type change`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            vm.handleIntent(AddTransactionIntent.TypeChanged(TransactionType.INCOME))
            assertTrue(vm.state.first().canSubmit)
        }

    // ── Submit with ShowError message ────────────────────

    @Test
    fun `submit with only name filled emits ShowError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Venda"))
            vm.effects.test {
                vm.handleIntent(AddTransactionIntent.Submit)
                val e = awaitItem()
                assertTrue(e is AddTransactionEffect.ShowError)
                assertEquals("Preencha todos os campos obrigatórios", e.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Date Granularity — Initial State ────────────────

    @Test
    fun `initial dateGranularity is MONTH`() =
        runTest {
            assertEquals(DateGranularity.MONTH, vm().state.first().dateGranularity)
        }

    @Test
    fun `initial dateDisplay is empty`() =
        runTest {
            assertEquals("", vm().state.first().dateDisplay)
        }

    @Test
    fun `initial selectedDay is null`() =
        runTest {
            assertNull(vm().state.first().selectedDay)
        }

    @Test
    fun `initial selectedMonth is null`() =
        runTest {
            assertNull(vm().state.first().selectedMonth)
        }

    @Test
    fun `initial selectedYear is null`() =
        runTest {
            assertNull(vm().state.first().selectedYear)
        }

    // ── Date Granularity — Switching ────────────────────

    @Test
    fun `DateGranularityChanged to DAY updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            assertEquals(DateGranularity.DAY, vm.state.first().dateGranularity)
        }

    @Test
    fun `DateGranularityChanged to YEAR updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR))
            assertEquals(DateGranularity.YEAR, vm.state.first().dateGranularity)
        }

    @Test
    fun `DateGranularityChanged to MONTH updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR))
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.MONTH))
            assertEquals(DateGranularity.MONTH, vm.state.first().dateGranularity)
        }

    @Test
    fun `switching granularity resets dateDisplay`() =
        runTest {
            val vm = vm()
            // Select a date in DAY mode
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = 15, month = 3, year = 2026))
            assertTrue(
                vm.state
                    .first()
                    .dateDisplay
                    .isNotEmpty(),
            )
            // Switch to MONTH → resets display
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.MONTH))
            assertEquals("", vm.state.first().dateDisplay)
        }

    @Test
    fun `switching granularity resets selectedDay, selectedMonth, selectedYear`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = 15, month = 3, year = 2026))
            assertNotNull(vm.state.first().selectedDay)
            // Switch granularity
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR))
            assertNull(vm.state.first().selectedDay)
            assertNull(vm.state.first().selectedMonth)
            assertNull(vm.state.first().selectedYear)
        }

    // ── DateSelected — DAY granularity (DD/MM/YYYY) ────

    @Test
    fun `DateSelected in DAY mode formats as DD-MM-YYYY`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = 5, month = 3, year = 2026))
            assertEquals("05/03/2026", vm.state.first().dateDisplay)
        }

    @Test
    fun `DateSelected in DAY mode stores day, month, year`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = 25, month = 12, year = 2025))
            val s = vm.state.first()
            assertEquals(25, s.selectedDay)
            assertEquals(12, s.selectedMonth)
            assertEquals(2025, s.selectedYear)
        }

    // ── DateSelected — MONTH granularity (MM/YYYY) ─────

    @Test
    fun `DateSelected in MONTH mode formats as MM-YYYY`() =
        runTest {
            val vm = vm()
            // MONTH is default, no need to change granularity
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 7, year = 2026))
            assertEquals("07/2026", vm.state.first().dateDisplay)
        }

    @Test
    fun `DateSelected in MONTH mode stores month and year, day is null`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 7, year = 2026))
            val s = vm.state.first()
            assertNull(s.selectedDay)
            assertEquals(7, s.selectedMonth)
            assertEquals(2026, s.selectedYear)
        }

    // ── DateSelected — YEAR granularity (YYYY) ─────────

    @Test
    fun `DateSelected in YEAR mode formats as YYYY`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = null, year = 2025))
            assertEquals("2025", vm.state.first().dateDisplay)
        }

    @Test
    fun `DateSelected in YEAR mode stores year only`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = null, year = 2025))
            val s = vm.state.first()
            assertNull(s.selectedDay)
            assertNull(s.selectedMonth)
            assertEquals(2025, s.selectedYear)
        }

    // ── Date is Required — canSubmit depends on date ─────

    @Test
    fun `canSubmit false without any date selected`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            assertFalse(vm.state.first().canSubmit)
            // dateDisplay is still empty — blocks canSubmit
            assertEquals("", vm.state.first().dateDisplay)
        }

    @Test
    fun `canSubmit true with date selected`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit becomes false after switching granularity resets date`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.NameChanged("Compra"))
            vm.handleIntent(AddTransactionIntent.AmountChanged("1000"))
            vm.handleIntent(AddTransactionIntent.PersonNameChanged("Pedro"))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 4, year = 2026))
            assertTrue(vm.state.first().canSubmit)
            // Switch granularity resets date → canSubmit becomes false
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            assertFalse(vm.state.first().canSubmit)
        }

    // ── Date edge cases ─────────────────────────────────

    @Test
    fun `DateSelected overwrites previous selection in same granularity`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 1, year = 2025))
            assertEquals("01/2025", vm.state.first().dateDisplay)
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 11, year = 2026))
            assertEquals("11/2026", vm.state.first().dateDisplay)
        }

    @Test
    fun `DateSelected with single-digit month pads to two digits`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateSelected(day = null, month = 3, year = 2026))
            assertEquals("03/2026", vm.state.first().dateDisplay)
        }

    @Test
    fun `DateSelected in DAY with single-digit day and month pads both`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY))
            vm.handleIntent(AddTransactionIntent.DateSelected(day = 1, month = 2, year = 2026))
            assertEquals("01/02/2026", vm.state.first().dateDisplay)
        }

    // ── LoadForEdit round-trip ──────────────────────────

    @Test
    fun `LoadForEdit restores personName, category and notes`() =
        runTest {
            val vm = vm()
            val tx =
                FinancialTransactionSummary(
                    id = "tx-edit-1",
                    description = "Compra",
                    cents = 5000L,
                    type = TransactionType.EXPENSE,
                    month = 4,
                    year = 2026,
                    personName = "Maria",
                    category = "Mercado",
                    notes = "Compra semanal",
                )
            vm.handleIntent(AddTransactionIntent.LoadForEdit(tx))
            val s = vm.state.first()
            assertEquals("Maria", s.personName)
            assertEquals("Mercado", s.category)
            assertEquals("Compra semanal", s.description)
            assertEquals("tx-edit-1", s.editingId)
        }
}
