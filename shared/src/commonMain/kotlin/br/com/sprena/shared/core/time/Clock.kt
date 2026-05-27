package br.com.sprena.shared.core.time

/**
 * Abstração de tempo para injeção. Em produção: [SystemClock] (Android).
 * Em testes: fakes que retornam timestamps fixos.
 */
interface Clock {
    fun nowEpochMillis(): Long
}
