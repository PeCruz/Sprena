package br.com.sprena.presentation.financial

import br.com.sprena.test.MainDispatcherEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — computePeriodLabel (FinancialViewModel)
 *
 * Testes dedicados para a lógica de cálculo de labels de período.
 * Testa diretamente o método `computePeriodLabel` com diferentes
 * combinações de PeriodFilter e offset.
 *
 * Cenários:
 *  - Cada filtro com offset 0 (período corrente)
 *  - Offsets positivos e negativos
 *  - Transições entre anos (Dez→Jan, Q4→Q1, etc.)
 *  - Offsets grandes (anos no passado/futuro)
 *  - Meses-limite de cada trimestre/semestre
 */
class ComputePeriodLabelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    // Helper: cria VM com clock fixo para testar computePeriodLabel
    private fun vmAt(
        year: Int,
        month: Int,
    ) = FinancialViewModel(clock = { YearMonth(year, month) })

    // ── MONTHLY ─────────────────────────────────────────

    @Test
    fun `monthly offset 0 for Jan 2026`() {
        assertEquals("Jan 2026", vmAt(2026, 1).computePeriodLabel(PeriodFilter.MONTHLY, 0))
    }

    @Test
    fun `monthly offset 0 for Jun 2026`() {
        assertEquals("Jun 2026", vmAt(2026, 6).computePeriodLabel(PeriodFilter.MONTHLY, 0))
    }

    @Test
    fun `monthly offset 0 for Dec 2026`() {
        assertEquals("Dez 2026", vmAt(2026, 12).computePeriodLabel(PeriodFilter.MONTHLY, 0))
    }

    @Test
    fun `monthly offset -1 from Jan 2026 is Dec 2025`() {
        assertEquals("Dez 2025", vmAt(2026, 1).computePeriodLabel(PeriodFilter.MONTHLY, -1))
    }

    @Test
    fun `monthly offset +1 from Dec 2025 is Jan 2026`() {
        assertEquals("Jan 2026", vmAt(2025, 12).computePeriodLabel(PeriodFilter.MONTHLY, 1))
    }

    @Test
    fun `monthly offset -12 goes back exactly one year`() {
        assertEquals("Abr 2025", vmAt(2026, 4).computePeriodLabel(PeriodFilter.MONTHLY, -12))
    }

    @Test
    fun `monthly offset +12 goes forward exactly one year`() {
        assertEquals("Abr 2027", vmAt(2026, 4).computePeriodLabel(PeriodFilter.MONTHLY, 12))
    }

    @Test
    fun `monthly offset -24 goes back two years`() {
        assertEquals("Abr 2024", vmAt(2026, 4).computePeriodLabel(PeriodFilter.MONTHLY, -24))
    }

    @Test
    fun `monthly all 12 months produce correct labels`() {
        val vm = vmAt(2026, 1)
        val expected =
            listOf(
                "Jan 2026",
                "Fev 2026",
                "Mar 2026",
                "Abr 2026",
                "Mai 2026",
                "Jun 2026",
                "Jul 2026",
                "Ago 2026",
                "Set 2026",
                "Out 2026",
                "Nov 2026",
                "Dez 2026",
            )
        expected.forEachIndexed { index, label ->
            assertEquals(label, vm.computePeriodLabel(PeriodFilter.MONTHLY, index))
        }
    }

    // ── QUARTERLY ───────────────────────────────────────

    @Test
    fun `quarterly offset 0 for Jan 2026 is Q1`() {
        assertEquals("1º Tri 2026", vmAt(2026, 1).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset 0 for Mar 2026 is still Q1`() {
        assertEquals("1º Tri 2026", vmAt(2026, 3).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset 0 for Apr 2026 is Q2`() {
        assertEquals("2º Tri 2026", vmAt(2026, 4).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset 0 for Jul 2026 is Q3`() {
        assertEquals("3º Tri 2026", vmAt(2026, 7).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset 0 for Oct 2026 is Q4`() {
        assertEquals("4º Tri 2026", vmAt(2026, 10).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset 0 for Dec 2026 is Q4`() {
        assertEquals("4º Tri 2026", vmAt(2026, 12).computePeriodLabel(PeriodFilter.QUARTERLY, 0))
    }

    @Test
    fun `quarterly offset -1 from Q1 2026 is Q4 2025`() {
        assertEquals("4º Tri 2025", vmAt(2026, 1).computePeriodLabel(PeriodFilter.QUARTERLY, -1))
    }

    @Test
    fun `quarterly offset +1 from Q4 2025 is Q1 2026`() {
        assertEquals("1º Tri 2026", vmAt(2025, 10).computePeriodLabel(PeriodFilter.QUARTERLY, 1))
    }

    @Test
    fun `quarterly offset -4 goes back one full year`() {
        assertEquals("2º Tri 2025", vmAt(2026, 4).computePeriodLabel(PeriodFilter.QUARTERLY, -4))
    }

    @Test
    fun `quarterly offset +4 goes forward one full year`() {
        assertEquals("2º Tri 2027", vmAt(2026, 4).computePeriodLabel(PeriodFilter.QUARTERLY, 4))
    }

    // ── SEMI-ANNUAL ─────────────────────────────────────

    @Test
    fun `semi-annual offset 0 for Jan 2026 is S1`() {
        assertEquals("1º Sem 2026", vmAt(2026, 1).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 0))
    }

    @Test
    fun `semi-annual offset 0 for Jun 2026 is S1`() {
        assertEquals("1º Sem 2026", vmAt(2026, 6).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 0))
    }

    @Test
    fun `semi-annual offset 0 for Jul 2026 is S2`() {
        assertEquals("2º Sem 2026", vmAt(2026, 7).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 0))
    }

    @Test
    fun `semi-annual offset 0 for Dec 2026 is S2`() {
        assertEquals("2º Sem 2026", vmAt(2026, 12).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 0))
    }

    @Test
    fun `semi-annual offset -1 from S1 2026 is S2 2025`() {
        assertEquals("2º Sem 2025", vmAt(2026, 1).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, -1))
    }

    @Test
    fun `semi-annual offset +1 from S2 2025 is S1 2026`() {
        assertEquals("1º Sem 2026", vmAt(2025, 7).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 1))
    }

    @Test
    fun `semi-annual offset -2 goes back one full year`() {
        assertEquals("1º Sem 2025", vmAt(2026, 3).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, -2))
    }

    @Test
    fun `semi-annual offset +2 goes forward one full year`() {
        assertEquals("1º Sem 2027", vmAt(2026, 3).computePeriodLabel(PeriodFilter.SEMI_ANNUAL, 2))
    }

    // ── ANNUAL ──────────────────────────────────────────

    @Test
    fun `annual offset 0 for 2026`() {
        assertEquals("2026", vmAt(2026, 4).computePeriodLabel(PeriodFilter.ANNUAL, 0))
    }

    @Test
    fun `annual offset -1 for 2026 is 2025`() {
        assertEquals("2025", vmAt(2026, 4).computePeriodLabel(PeriodFilter.ANNUAL, -1))
    }

    @Test
    fun `annual offset +1 for 2026 is 2027`() {
        assertEquals("2027", vmAt(2026, 4).computePeriodLabel(PeriodFilter.ANNUAL, 1))
    }

    @Test
    fun `annual offset -5 for 2026 is 2021`() {
        assertEquals("2021", vmAt(2026, 4).computePeriodLabel(PeriodFilter.ANNUAL, -5))
    }

    @Test
    fun `annual offset +10 for 2026 is 2036`() {
        assertEquals("2036", vmAt(2026, 4).computePeriodLabel(PeriodFilter.ANNUAL, 10))
    }

    @Test
    fun `annual is independent of month`() {
        assertEquals(
            vmAt(2026, 1).computePeriodLabel(PeriodFilter.ANNUAL, 0),
            vmAt(2026, 12).computePeriodLabel(PeriodFilter.ANNUAL, 0),
        )
    }
}
