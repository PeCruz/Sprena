package br.com.sprena.shared.core.mvi

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato base para todos os ViewModels MVI do Sprena.
 *
 * Fluxo unidirecional de dados (UDF):
 * ```
 * User Action → [handleIntent] → ViewModel → [state] (UI re-renders)
 *                                           ↘ [effects] (navigation, toast…)
 * ```
 *
 * @param STATE  Snapshot imutável da UI — emitido via [state] (StateFlow).
 * @param INTENT Ações que o usuário pode disparar — recebidas via [handleIntent].
 * @param EFFECT Efeitos colaterais one-shot — emitidos via [effects] (SharedFlow).
 *
 * Regras:
 * - [state] NUNCA é mutado diretamente — sempre emitir via `copy()`
 * - [effects] é consumido uma única vez pela UI (`LaunchedEffect`)
 * - [handleIntent] é a ÚNICA porta de entrada de interações do usuário
 */
interface MviViewModel<STATE : UiState, INTENT : UiIntent, EFFECT : UiEffect> {
    val state: StateFlow<STATE>
    val effects: SharedFlow<EFFECT>

    fun handleIntent(intent: INTENT)
}
