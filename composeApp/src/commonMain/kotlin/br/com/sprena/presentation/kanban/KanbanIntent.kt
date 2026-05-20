package br.com.sprena.presentation.kanban

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface KanbanIntent : UiIntent {
    data object LoadBoard : KanbanIntent

    data object AddTaskClicked : KanbanIntent

    data object DismissAddTaskDialog : KanbanIntent

    data class MoveTask(
        val taskId: String,
        val targetColumnId: String,
    ) : KanbanIntent

    data class SearchQueryChanged(
        val query: String,
    ) : KanbanIntent

    data object SettingsClicked : KanbanIntent

    data class TaskCreated(
        val name: String,
        val priority: Int,
    ) : KanbanIntent

    data class TaskClicked(
        val task: KanbanTask,
    ) : KanbanIntent

    data object DismissTaskDetail : KanbanIntent

    data class TaskUpdated(
        val taskId: String,
        val name: String,
        val priority: Int,
        val columnId: String,
    ) : KanbanIntent

    data class TaskDeleted(
        val taskId: String,
    ) : KanbanIntent

    /** Intent auxiliar para injetar tasks em testes. Não usar em produção. */
    data class AddTestTasks(
        val tasks: Map<String, List<KanbanTask>>,
    ) : KanbanIntent
}
