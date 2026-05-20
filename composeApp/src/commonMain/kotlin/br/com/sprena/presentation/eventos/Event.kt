package br.com.sprena.presentation.eventos

/**
 * Modelo de evento exibido na tela Eventos.
 *
 * @property id identificador unico.
 * @property name nome do evento (obrigatorio).
 * @property category categoria do evento: RESERVA, ALUGUEL ou DAY_USE (obrigatorio).
 * @property dateEpochDay data do evento em epoch days (obrigatorio).
 * @property contact telefone do cliente (opcional).
 * @property description descricao do evento (opcional).
 */
data class Event(
    val id: String,
    val name: String,
    val category: EventCategory,
    val dateEpochDay: Long,
    val contact: String? = null,
    val description: String? = null,
)
