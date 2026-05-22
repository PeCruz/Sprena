package br.com.sprena.presentation.eventos.createevent

import br.com.sprena.presentation.eventos.EventCategory
import br.com.sprena.shared.core.mvi.UiIntent

sealed interface CreateEventIntent : UiIntent {
    data class NameChanged(
        val name: String,
    ) : CreateEventIntent

    data class CategoryChanged(
        val category: EventCategory,
    ) : CreateEventIntent

    data class DateChanged(
        val dateEpochDay: Long,
    ) : CreateEventIntent

    data class ContactChanged(
        val contact: String,
    ) : CreateEventIntent

    data class DescriptionChanged(
        val description: String,
    ) : CreateEventIntent

    data object Submit : CreateEventIntent

    data object BackClicked : CreateEventIntent

    /** Carrega dados existentes para edicao. */
    data class LoadForEdit(
        val eventId: String,
        val name: String,
        val category: EventCategory,
        val dateEpochDay: Long,
        val contact: String?,
        val description: String?,
    ) : CreateEventIntent
}
