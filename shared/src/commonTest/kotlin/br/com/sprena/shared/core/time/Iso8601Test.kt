package br.com.sprena.shared.core.time

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — formatação de instante em ISO-8601 UTC (F1.6a).
 *
 * Existe porque o arquivo de exportação vai para o **titular**, e um número de
 * milissegundos não é resposta legível a "quando isso aconteceu". O projeto não tem
 * `kotlinx-datetime`, e o escopo aqui é pequeno o bastante para não justificar a
 * dependência: só UTC, só formatação, sem parsing e sem fuso.
 */
class Iso8601Test {
    @Test
    fun `epoch zero e a origem unix`() {
        assertEquals("1970-01-01T00:00:00Z", toIso8601Utc(0L))
    }

    @Test
    fun `formata uma data comum com hora minuto e segundo`() {
        // 2026-08-14T18:22:05Z
        assertEquals("2026-08-14T18:22:05Z", toIso8601Utc(1_786_731_725_000L))
    }

    @Test
    fun `descarta os milissegundos — a precisao de segundo basta para o titular`() {
        assertEquals("2026-08-14T18:22:05Z", toIso8601Utc(1_786_731_725_999L))
    }

    @Test
    fun `zero a esquerda em mes dia hora minuto e segundo`() {
        // 2001-02-03T04:05:06Z
        assertEquals("2001-02-03T04:05:06Z", toIso8601Utc(981_173_106_000L))
    }

    @Test
    fun `29 de fevereiro em ano bissexto`() {
        // 2024-02-29T12:00:00Z
        assertEquals("2024-02-29T12:00:00Z", toIso8601Utc(1_709_208_000_000L))
    }

    @Test
    fun `virada de ano`() {
        // 2025-12-31T23:59:59Z
        assertEquals("2025-12-31T23:59:59Z", toIso8601Utc(1_767_225_599_000L))
    }
}
