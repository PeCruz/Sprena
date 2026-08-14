package br.com.sprena.shared.account.domain.model

import br.com.sprena.shared.sportclient.domain.validation.SportModality

/**
 * O que o titular pode gravar sobre si mesmo — exatamente o conteúdo de
 * `user_profiles/{uid}`, e nada de `users/{uid}`.
 *
 * O tipo existe para tornar impossível, por construção, mandar `role` num save:
 * a palavra não aparece aqui, então nenhum call site pode passá-la por engano.
 *
 * Campos em branco viram `null` na persistência — "não informado" e "string vazia"
 * são a mesma coisa para o titular, e guardar `""` faria a UI mostrar um campo vazio
 * em vez do texto de ausência.
 */
data class ProfilePatch(
    val apelido: String?,
    val cpf: String?,
    val phone: String?,
    val modalities: List<SportModality>,
)
