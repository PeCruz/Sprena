package br.com.sprena.presentation.category

import androidx.lifecycle.ViewModel
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class CategoryViewModel :
    ViewModel(),
    MviViewModel<CategoryState, CategoryIntent, CategoryEffect> {
    private val _state =
        MutableStateFlow(
            CategoryState(
                categories = DEFAULT_CATEGORIES.toList(),
            ),
        )
    override val state: StateFlow<CategoryState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CategoryEffect>()
    override val effects: SharedFlow<CategoryEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: CategoryIntent) {
        when (intent) {
            is CategoryIntent.AddCategoryClicked -> {
                _state.value = _state.value.copy(isAddDialogVisible = true)
            }

            is CategoryIntent.DismissAddDialog -> {
                _state.value = _state.value.copy(isAddDialogVisible = false)
            }

            is CategoryIntent.CategoryAdded -> {
                val name = intent.name.trim()
                if (name.isBlank()) return
                val current = _state.value.categories
                if (current.contains(name)) return
                _state.value =
                    _state.value.copy(
                        categories = current + name,
                        isAddDialogVisible = false,
                    )
            }

            is CategoryIntent.CategoryClicked -> {
                _state.value = _state.value.copy(editingCategory = intent.name)
            }

            is CategoryIntent.DismissEditDialog -> {
                _state.value = _state.value.copy(editingCategory = null)
            }

            is CategoryIntent.CategoryRenamed -> {
                val newName = intent.newName.trim()
                if (newName.isBlank()) return
                val updated =
                    _state.value.categories.map { cat ->
                        if (cat == intent.oldName) newName else cat
                    }
                _state.value =
                    _state.value.copy(
                        categories = updated,
                        editingCategory = null,
                    )
            }

            is CategoryIntent.CategoryDeleted -> {
                val updated = _state.value.categories.filter { it != intent.name }
                _state.value =
                    _state.value.copy(
                        categories = updated,
                        editingCategory = null,
                    )
            }
        }
    }

    companion object {
        val DEFAULT_CATEGORIES =
            listOf(
                "Salário",
                "Vendas",
                "Mercado",
                "Transporte",
                "Serviços",
                "Material",
                "Outros",
            )
    }
}
