package br.com.sprena.presentation.profile

import br.com.sprena.shared.core.mvi.UiEffect

/** Efeitos one-shot da tela "Meus dados" (F1.6a). */
sealed interface ProfileEffect : UiEffect {
    /**
     * O domínio produz conteúdo e nome; entregar é da plataforma (share sheet no
     * Android). Isso mantém o ViewModel livre de `Intent` e o use case testável.
     */
    data class ShareExport(
        val fileName: String,
        val json: String,
    ) : ProfileEffect

    data class ShowMessage(
        val message: String,
    ) : ProfileEffect

    /**
     * Serve logout **e** pós-exclusão: destino e semântica de back stack são idênticos
     * (`popUpTo(0)`). Não há efeito de sucesso após excluir — a tela está saindo da
     * árvore e um snackbar não apareceria; a tela de Login já é a confirmação.
     */
    data object NavigateToLogin : ProfileEffect
}
