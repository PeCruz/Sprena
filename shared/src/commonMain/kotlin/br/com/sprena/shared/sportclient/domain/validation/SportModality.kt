package br.com.sprena.shared.sportclient.domain.validation

/**
 * Modalidades esportivas disponíveis para os clientes.
 *
 * [label] é o rótulo de exibição, no mesmo molde de
 * [br.com.sprena.shared.auth.domain.model.UserRole.displayName]. Mantido aqui para
 * que a lista de modalidades tenha um único rótulo canônico — antes cada tela
 * carregava a própria função `modalityLabel` privada.
 */
enum class SportModality(
    val label: String,
) {
    FUTEVOLEI("Futevôlei"),
    BEACH_TENNIS("Beach Tennis"),
    VOLEI("Vôlei"),
}
