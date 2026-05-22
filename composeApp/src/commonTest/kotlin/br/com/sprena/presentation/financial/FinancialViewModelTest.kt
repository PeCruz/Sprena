package br.com.sprena.presentation.financial

import app.cash.turbine.test
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — FinancialViewModel
 *
 * Cobre:
 *  - Estado inicial (vazio, diálogo fechado, período mensal)
 *  - Botão "Adicionar Transação" abre diálogo / emite efeito
 *  - DismissAddDialog fecha diálogo
 *  - Load (placeholder para futura integração)
 *  - Period filter: troca de filtro, navegação anterior/próximo
 */
class FinancialViewModelTest {
    private val env = MainDispatcherEnv()
    private val fixedClock = { YearMonth(year = 2026, month = 4) } // Abr 2026

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm() = FinancialViewModel(clock = fixedClock)

    // ── Estado inicial ───────────────────────────────────

    @Test
    fun `initial state has dialog hidden`() =
        runTest {
            assertFalse(vm().state.first().isAddDialogVisible)
        }

    @Test
    fun `initial state has empty transactions and zero balance`() =
        runTest {
            val s = vm().state.first()
            assertTrue(s.transactions.isEmpty())
            assertEquals(0L, s.balanceCents)
            assertEquals(0L, s.incomeCents)
            assertEquals(0L, s.expenseCents)
        }

    @Test
    fun `initial state is not loading`() =
        runTest {
            assertFalse(vm().state.first().isLoading)
        }

    @Test
    fun `initial period filter is MONTHLY`() =
        runTest {
            assertEquals(PeriodFilter.MONTHLY, vm().state.first().periodFilter)
        }

    @Test
    fun `initial period label is current month`() =
        runTest {
            assertEquals("Abr 2026", vm().state.first().periodLabel)
        }

    // ── Add Transaction button ───────────────────────────

    @Test
    fun `AddTransactionClicked opens dialog in state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.AddTransactionClicked)
            assertTrue(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `AddTransactionClicked emits OpenAddTransactionDialog effect`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(FinancialIntent.AddTransactionClicked)
                assertEquals(FinancialEffect.OpenAddTransactionDialog, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Dismiss dialog ───────────────────────────────────

    @Test
    fun `DismissAddDialog hides dialog`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.AddTransactionClicked)
            vm.handleIntent(FinancialIntent.DismissAddDialog)
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    // ── Load ─────────────────────────────────────────────

    @Test
    fun `Load does not crash`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.Load)
            val s = vm.state.first()
            assertTrue(s.transactions.isEmpty() || s.transactions.isNotEmpty())
        }

    // ── Period Filter ────────────────────────────────────

    @Test
    fun `changing to QUARTERLY sets correct label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            val s = vm.state.first()
            assertEquals(PeriodFilter.QUARTERLY, s.periodFilter)
            assertEquals("2º Tri 2026", s.periodLabel)
        }

    @Test
    fun `changing to SEMI_ANNUAL sets correct label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            val s = vm.state.first()
            assertEquals(PeriodFilter.SEMI_ANNUAL, s.periodFilter)
            assertEquals("1º Sem 2026", s.periodLabel)
        }

    @Test
    fun `changing to ANNUAL sets correct label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            val s = vm.state.first()
            assertEquals(PeriodFilter.ANNUAL, s.periodFilter)
            assertEquals("2026", s.periodLabel)
        }

    @Test
    fun `changing filter resets offset to 0`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PreviousPeriod) // offset = -1
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            assertEquals(0, vm.state.first().periodOffset)
        }

    // ── Period Navigation ────────────────────────────────

    @Test
    fun `PreviousPeriod decrements offset and updates label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            val s = vm.state.first()
            assertEquals(-1, s.periodOffset)
            assertEquals("Mar 2026", s.periodLabel)
        }

    @Test
    fun `NextPeriod increments offset and updates label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.NextPeriod)
            val s = vm.state.first()
            assertEquals(1, s.periodOffset)
            assertEquals("Mai 2026", s.periodLabel)
        }

    @Test
    fun `navigating back across year boundary works`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 1) })
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("Dez 2025", vm.state.first().periodLabel)
        }

    @Test
    fun `navigating forward across year boundary works`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2025, month = 12) })
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("Jan 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `multiple PreviousPeriod navigations accumulate offset`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            val s = vm.state.first()
            assertEquals(-3, s.periodOffset)
            assertEquals("Jan 2026", s.periodLabel)
        }

    @Test
    fun `multiple NextPeriod navigations accumulate offset`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.NextPeriod)
            vm.handleIntent(FinancialIntent.NextPeriod)
            val s = vm.state.first()
            assertEquals(2, s.periodOffset)
            assertEquals("Jun 2026", s.periodLabel)
        }

    @Test
    fun `previous then next returns to original period`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            vm.handleIntent(FinancialIntent.NextPeriod)
            val s = vm.state.first()
            assertEquals(0, s.periodOffset)
            assertEquals("Abr 2026", s.periodLabel)
        }

    // ── Quarterly Navigation ─────────────────────────────

    @Test
    fun `quarterly previous navigates to previous quarter`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("1º Tri 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `quarterly next navigates to next quarter`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("3º Tri 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `quarterly navigation across year boundary Q1 to previous`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 2) }) // Q1
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("4º Tri 2025", vm.state.first().periodLabel)
        }

    @Test
    fun `quarterly navigation across year boundary Q4 to next`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2025, month = 11) }) // Q4
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("1º Tri 2026", vm.state.first().periodLabel)
        }

    // ── Semi-Annual Navigation ───────────────────────────

    @Test
    fun `semi-annual previous navigates to previous semester`() =
        runTest {
            val vm = vm() // Abr 2026 → Sem1
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("2º Sem 2025", vm.state.first().periodLabel)
        }

    @Test
    fun `semi-annual next navigates to next semester`() =
        runTest {
            val vm = vm() // Abr 2026 → Sem1
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("2º Sem 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `semi-annual S1 previous crosses year boundary`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 3) }) // Sem1
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("2º Sem 2025", vm.state.first().periodLabel)
        }

    @Test
    fun `semi-annual S2 next crosses year boundary`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2025, month = 8) }) // Sem2
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("1º Sem 2026", vm.state.first().periodLabel)
        }

    // ── Annual Navigation ────────────────────────────────

    @Test
    fun `annual previous navigates to previous year`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("2025", vm.state.first().periodLabel)
        }

    @Test
    fun `annual next navigates to next year`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            vm.handleIntent(FinancialIntent.NextPeriod)
            assertEquals("2027", vm.state.first().periodLabel)
        }

    @Test
    fun `annual navigating multiple years back`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            vm.handleIntent(FinancialIntent.PreviousPeriod)
            assertEquals("2023", vm.state.first().periodLabel)
        }

    // ── Filter change resets navigation ──────────────────

    @Test
    fun `switching from MONTHLY with offset to QUARTERLY resets offset and label`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PreviousPeriod) // Mar 2026
            vm.handleIntent(FinancialIntent.PreviousPeriod) // Fev 2026
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            val s = vm.state.first()
            assertEquals(0, s.periodOffset)
            assertEquals("2º Tri 2026", s.periodLabel)
        }

    @Test
    fun `switching from QUARTERLY with offset to ANNUAL resets offset`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.NextPeriod) // Q3
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            val s = vm.state.first()
            assertEquals(0, s.periodOffset)
            assertEquals("2026", s.periodLabel)
        }

    // ── Period label for different months ────────────────

    @Test
    fun `monthly label for January`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 1) })
            assertEquals("Jan 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `monthly label for December`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 12) })
            assertEquals("Dez 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `quarterly label for Q1 month Jan`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 1) })
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            assertEquals("1º Tri 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `quarterly label for Q4 month Oct`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 10) })
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            assertEquals("4º Tri 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `semi-annual label for S2 month Jul`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 7) })
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            assertEquals("2º Sem 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `semi-annual label for S1 month Jun`() =
        runTest {
            val vm = FinancialViewModel(clock = { YearMonth(year = 2026, month = 6) })
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.SEMI_ANNUAL))
            assertEquals("1º Sem 2026", vm.state.first().periodLabel)
        }

    // ── TransactionAdded ─────────────────────────────────

    private fun incomeTransaction(
        cents: Long = 10_000L,
        description: String = "Venda",
        month: Int = 4,
        year: Int = 2026,
    ) = FinancialTransactionSummary(
        id = "inc-$cents",
        description = description,
        cents = cents,
        type = TransactionType.INCOME,
        month = month,
        year = year,
    )

    private fun expenseTransaction(
        cents: Long = 5_000L,
        description: String = "Compra",
        month: Int = 4,
        year: Int = 2026,
    ) = FinancialTransactionSummary(
        id = "exp-$cents",
        description = description,
        cents = cents,
        type = TransactionType.EXPENSE,
        month = month,
        year = year,
    )

    @Test
    fun `TransactionAdded INCOME appears in transactions list`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            val s = vm.state.first()
            assertEquals(1, s.transactions.size)
            assertEquals(tx, s.transactions.first())
        }

    @Test
    fun `TransactionAdded INCOME increases incomeCents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 15_000L)))
            assertEquals(15_000L, vm.state.first().incomeCents)
        }

    @Test
    fun `TransactionAdded INCOME does not change expenseCents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 15_000L)))
            assertEquals(0L, vm.state.first().expenseCents)
        }

    @Test
    fun `TransactionAdded INCOME updates balanceCents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 15_000L)))
            assertEquals(15_000L, vm.state.first().balanceCents)
        }

    @Test
    fun `TransactionAdded EXPENSE increases expenseCents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 7_500L)))
            assertEquals(7_500L, vm.state.first().expenseCents)
        }

    @Test
    fun `TransactionAdded EXPENSE does not change incomeCents`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 7_500L)))
            assertEquals(0L, vm.state.first().incomeCents)
        }

    @Test
    fun `TransactionAdded EXPENSE makes balanceCents negative`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 7_500L)))
            assertEquals(-7_500L, vm.state.first().balanceCents)
        }

    @Test
    fun `multiple transactions accumulate correctly`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 20_000L)))
            vm.handleIntent(
                FinancialIntent.TransactionAdded(incomeTransaction(cents = 10_000L, description = "Venda 2")),
            )
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 5_000L)))
            val s = vm.state.first()
            assertEquals(3, s.transactions.size)
            assertEquals(30_000L, s.incomeCents)
            assertEquals(5_000L, s.expenseCents)
            assertEquals(25_000L, s.balanceCents)
        }

    @Test
    fun `balance is negative when expenses exceed income`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 5_000L)))
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 15_000L)))
            val s = vm.state.first()
            assertEquals(5_000L, s.incomeCents)
            assertEquals(15_000L, s.expenseCents)
            assertEquals(-10_000L, s.balanceCents)
        }

    @Test
    fun `TransactionAdded closes add dialog`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.AddTransactionClicked)
            assertTrue(vm.state.first().isAddDialogVisible)
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction()))
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `new transaction is added at the beginning of the list`() =
        runTest {
            val vm = vm()
            val first = incomeTransaction(cents = 1_000L, description = "Primeira")
            val second = expenseTransaction(cents = 2_000L, description = "Segunda")
            vm.handleIntent(FinancialIntent.TransactionAdded(first))
            vm.handleIntent(FinancialIntent.TransactionAdded(second))
            assertEquals(
                "Segunda",
                vm.state
                    .first()
                    .transactions
                    .first()
                    .description,
            )
        }

    // ── Edit Transaction ─────────────────────────────────

    @Test
    fun `EditTransactionClicked opens edit dialog with transaction id`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            vm.handleIntent(FinancialIntent.EditTransactionClicked(tx.id))
            val s = vm.state.first()
            assertTrue(s.isEditDialogVisible)
            assertEquals(tx.id, s.editingTransactionId)
        }

    @Test
    fun `DismissEditDialog hides edit dialog`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            vm.handleIntent(FinancialIntent.EditTransactionClicked(tx.id))
            vm.handleIntent(FinancialIntent.DismissEditDialog)
            val s = vm.state.first()
            assertFalse(s.isEditDialogVisible)
            assertNull(s.editingTransactionId)
        }

    @Test
    fun `TransactionUpdated replaces transaction in list`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction(cents = 10_000L, description = "Venda")
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            val updated = tx.copy(description = "Venda atualizada", cents = 15_000L)
            vm.handleIntent(FinancialIntent.TransactionUpdated(updated))
            val s = vm.state.first()
            assertEquals(1, s.transactions.size)
            assertEquals("Venda atualizada", s.transactions.first().description)
            assertEquals(15_000L, s.transactions.first().cents)
        }

    @Test
    fun `TransactionUpdated recalculates income totals`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction(cents = 10_000L)
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            assertEquals(10_000L, vm.state.first().incomeCents)
            val updated = tx.copy(cents = 20_000L)
            vm.handleIntent(FinancialIntent.TransactionUpdated(updated))
            assertEquals(20_000L, vm.state.first().incomeCents)
        }

    @Test
    fun `TransactionUpdated recalculates expense totals`() =
        runTest {
            val vm = vm()
            val tx = expenseTransaction(cents = 5_000L)
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            assertEquals(5_000L, vm.state.first().expenseCents)
            val updated = tx.copy(cents = 8_000L)
            vm.handleIntent(FinancialIntent.TransactionUpdated(updated))
            assertEquals(8_000L, vm.state.first().expenseCents)
        }

    @Test
    fun `TransactionUpdated can change type from INCOME to EXPENSE`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction(cents = 10_000L)
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            assertEquals(10_000L, vm.state.first().incomeCents)
            val updated = tx.copy(type = TransactionType.EXPENSE)
            vm.handleIntent(FinancialIntent.TransactionUpdated(updated))
            val s = vm.state.first()
            assertEquals(0L, s.incomeCents)
            assertEquals(10_000L, s.expenseCents)
        }

    @Test
    fun `TransactionUpdated closes edit dialog`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            vm.handleIntent(FinancialIntent.EditTransactionClicked(tx.id))
            vm.handleIntent(FinancialIntent.TransactionUpdated(tx.copy(description = "Updated")))
            assertFalse(vm.state.first().isEditDialogVisible)
        }

    // ── Delete Transaction ───────────────────────────────

    @Test
    fun `TransactionDeleted removes transaction from list`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            assertEquals(
                1,
                vm.state
                    .first()
                    .transactions.size,
            )
            vm.handleIntent(FinancialIntent.TransactionDeleted(tx.id))
            assertTrue(
                vm.state
                    .first()
                    .transactions
                    .isEmpty(),
            )
        }

    @Test
    fun `TransactionDeleted recalculates income totals`() =
        runTest {
            val vm = vm()
            val tx1 = incomeTransaction(cents = 10_000L, description = "V1")
            val tx2 = incomeTransaction(cents = 5_000L, description = "V2")
            vm.handleIntent(FinancialIntent.TransactionAdded(tx1))
            vm.handleIntent(FinancialIntent.TransactionAdded(tx2))
            assertEquals(15_000L, vm.state.first().incomeCents)
            vm.handleIntent(FinancialIntent.TransactionDeleted(tx1.id))
            assertEquals(5_000L, vm.state.first().incomeCents)
        }

    @Test
    fun `TransactionDeleted recalculates expense totals`() =
        runTest {
            val vm = vm()
            val tx = expenseTransaction(cents = 7_000L)
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            assertEquals(7_000L, vm.state.first().expenseCents)
            vm.handleIntent(FinancialIntent.TransactionDeleted(tx.id))
            assertEquals(0L, vm.state.first().expenseCents)
        }

    @Test
    fun `TransactionDeleted updates balance`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionAdded(incomeTransaction(cents = 20_000L)))
            vm.handleIntent(FinancialIntent.TransactionAdded(expenseTransaction(cents = 5_000L)))
            assertEquals(15_000L, vm.state.first().balanceCents)
            vm.handleIntent(FinancialIntent.TransactionDeleted("exp-5000"))
            assertEquals(20_000L, vm.state.first().balanceCents)
        }

    @Test
    fun `TransactionDeleted closes edit dialog`() =
        runTest {
            val vm = vm()
            val tx = incomeTransaction()
            vm.handleIntent(FinancialIntent.TransactionAdded(tx))
            vm.handleIntent(FinancialIntent.EditTransactionClicked(tx.id))
            vm.handleIntent(FinancialIntent.TransactionDeleted(tx.id))
            assertFalse(vm.state.first().isEditDialogVisible)
        }

    @Test
    fun `deleting nonexistent id does not crash`() =
        runTest {
            val vm = vm()
            vm.handleIntent(FinancialIntent.TransactionDeleted("nonexistent"))
            assertTrue(
                vm.state
                    .first()
                    .transactions
                    .isEmpty(),
            )
        }

    // ── Period-based Filtering ───────────────────────���───

    @Test
    fun `filteredTransactions shows only transactions matching current month`() =
        runTest {
            val vm = vm() // Abr 2026
            val txApril = incomeTransaction(cents = 10_000L, description = "April").copy(month = 4, year = 2026)
            val txMarch = expenseTransaction(cents = 5_000L, description = "March").copy(month = 3, year = 2026)
            vm.handleIntent(FinancialIntent.TransactionAdded(txApril))
            vm.handleIntent(FinancialIntent.TransactionAdded(txMarch))
            val s = vm.state.first()
            assertEquals(1, s.filteredTransactions.size)
            assertEquals("April", s.filteredTransactions.first().description)
        }

    @Test
    fun `filteredTransactions updates when navigating to different month`() =
        runTest {
            val vm = vm() // Abr 2026
            val txApril = incomeTransaction(cents = 10_000L, description = "April").copy(month = 4, year = 2026)
            val txMarch = expenseTransaction(cents = 5_000L, description = "March").copy(month = 3, year = 2026)
            vm.handleIntent(FinancialIntent.TransactionAdded(txApril))
            vm.handleIntent(FinancialIntent.TransactionAdded(txMarch))
            vm.handleIntent(FinancialIntent.PreviousPeriod) // → Mar 2026
            val s = vm.state.first()
            assertEquals(1, s.filteredTransactions.size)
            assertEquals("March", s.filteredTransactions.first().description)
        }

    @Test
    fun `filteredTransactions for quarterly shows transactions in that quarter`() =
        runTest {
            val vm = vm() // Abr 2026 → Q2
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            val txApril = incomeTransaction(cents = 10_000L, description = "April").copy(month = 4, year = 2026)
            val txMay = incomeTransaction(cents = 5_000L, description = "May").copy(month = 5, year = 2026)
            val txJan = expenseTransaction(cents = 3_000L, description = "Jan").copy(month = 1, year = 2026)
            vm.handleIntent(FinancialIntent.TransactionAdded(txApril))
            vm.handleIntent(FinancialIntent.TransactionAdded(txMay))
            vm.handleIntent(FinancialIntent.TransactionAdded(txJan))
            val s = vm.state.first()
            assertEquals(2, s.filteredTransactions.size)
        }

    @Test
    fun `filteredTransactions for annual shows all transactions in that year`() =
        runTest {
            val vm = vm() // 2026
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            val tx2026 = incomeTransaction(cents = 10_000L, description = "This year").copy(month = 4, year = 2026)
            val tx2025 = expenseTransaction(cents = 5_000L, description = "Last year").copy(month = 12, year = 2025)
            vm.handleIntent(FinancialIntent.TransactionAdded(tx2026))
            vm.handleIntent(FinancialIntent.TransactionAdded(tx2025))
            val s = vm.state.first()
            assertEquals(1, s.filteredTransactions.size)
            assertEquals("This year", s.filteredTransactions.first().description)
        }

    @Test
    fun `filtered totals reflect only visible period`() =
        runTest {
            val vm = vm() // Abr 2026
            val txApril = incomeTransaction(cents = 10_000L, description = "April").copy(month = 4, year = 2026)
            val txMarch = incomeTransaction(cents = 20_000L, description = "March").copy(month = 3, year = 2026)
            vm.handleIntent(FinancialIntent.TransactionAdded(txApril))
            vm.handleIntent(FinancialIntent.TransactionAdded(txMarch))
            val s = vm.state.first()
            // Only April should be in filtered totals
            assertEquals(10_000L, s.filteredIncomeCents)
            assertEquals(0L, s.filteredExpenseCents)
        }

    @Test
    fun `filteredTransactions empty for month with no transactions`() =
        runTest {
            val vm = vm() // Abr 2026
            val txMarch = incomeTransaction(cents = 10_000L, description = "March").copy(month = 3, year = 2026)
            vm.handleIntent(FinancialIntent.TransactionAdded(txMarch))
            assertEquals(
                0,
                vm.state
                    .first()
                    .filteredTransactions.size,
            )
        }

    // ── Jump to Date ─────────────────────────────────────

    @Test
    fun `JumpToDate sets correct offset for monthly`() =
        runTest {
            val vm = vm() // Abr 2026
            vm.handleIntent(FinancialIntent.JumpToDate(month = 1, year = 2026))
            val s = vm.state.first()
            assertEquals("Jan 2026", s.periodLabel)
        }

    @Test
    fun `JumpToDate sets correct offset for different year`() =
        runTest {
            val vm = vm() // Abr 2026
            vm.handleIntent(FinancialIntent.JumpToDate(month = 12, year = 2025))
            assertEquals("Dez 2025", vm.state.first().periodLabel)
        }

    @Test
    fun `JumpToDate works with quarterly filter`() =
        runTest {
            val vm = vm() // Abr 2026 → Q2
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.QUARTERLY))
            vm.handleIntent(FinancialIntent.JumpToDate(month = 1, year = 2026))
            assertEquals("1º Tri 2026", vm.state.first().periodLabel)
        }

    @Test
    fun `JumpToDate works with annual filter`() =
        runTest {
            val vm = vm() // 2026
            vm.handleIntent(FinancialIntent.PeriodFilterChanged(PeriodFilter.ANNUAL))
            vm.handleIntent(FinancialIntent.JumpToDate(month = 6, year = 2025))
            assertEquals("2025", vm.state.first().periodLabel)
        }
}
