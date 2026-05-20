package br.com.sprena.presentation.eventos

/**
 * Categorias de evento — tipo do evento (Reserva, Aluguel, Day Use).
 *
 * Categorias são independentes das tabs (Eventos / Eventos Realizados).
 * Usadas para filtro por tipo e para exibição de badge/cor no card.
 *
 * @property label texto exibido na UI (badge e filtro dropdown).
 */
enum class EventCategory(
    val label: String,
) {
    RESERVA("Reserva"),
    ALUGUEL("Aluguel"),
    DAY_USE("Day Use"),
}
