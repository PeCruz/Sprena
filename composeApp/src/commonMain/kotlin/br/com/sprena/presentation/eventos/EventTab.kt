package br.com.sprena.presentation.eventos

/**
 * Tabs da tela Eventos — separa eventos futuros de eventos realizados.
 *
 * EVENTOS: exibe eventos com data >= hoje, ordenados por data ascendente.
 * EVENTOS_REALIZADOS: exibe eventos com data < hoje, ordenados por data descendente.
 *
 * @property label texto exibido na UI (tab).
 */
enum class EventTab(val label: String) {
    EVENTOS("Eventos"),
    EVENTOS_REALIZADOS("Eventos Realizados"),
}
