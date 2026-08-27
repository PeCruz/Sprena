package br.com.sprena.shared.auth.domain.model

/**
 * Perfis de acesso do sistema Sprena.
 *
 * A partir de F1.7 este enum reflete a role **global**, guardada em `users/{uid}.role`, cuja
 * única pergunta relevante para as rules é: é [ADM]? Os papéis operacionais valem por
 * estabelecimento e vivem em [br.com.sprena.shared.establishment.domain.model.MemberRole], em
 * `establishments/{estId}/members/{uid}.role`.
 *
 * [MOD] e [CLIENT] continuam aqui pelas contas provisionadas à mão antes de F1.7 (Parte B do
 * runbook). Elas seguem funcionando; o que o papel global concede a elas, porém, é apenas o que
 * qualquer conta tem — o acesso real vem do vínculo.
 *
 * @property displayName Nome legível para exibição na UI.
 */
enum class UserRole(
    val displayName: String,
) {
    /** Acesso total ao sistema, em todos os estabelecimentos. Só o Console cria um ADM. */
    ADM("Administrador"),

    /** Legado pré-F1.7: hoje o papel de moderador vale por estabelecimento. */
    MOD("Moderador"),

    /** Legado pré-F1.7: hoje o papel de funcionário vale por estabelecimento. */
    CLIENT("Funcionário"),

    /**
     * Papel padrão de toda conta criada pelo próprio login (F1.7.3).
     *
     * A constante só pôde nascer depois de F1.7.2. Antes dela, `sport_clients` era uma coleção
     * global com `read: if isSignedIn()`, então dar existência a um papel obtido sem aprovação
     * humana significaria entregar o CPF e o telefone de todos os clientes a qualquer login —
     * risco que `SECURITY.md` registrava por antecipação.
     *
     * **Consequência operacional:** as rules que restringem este papel precisam estar
     * publicadas antes de um APK que o crie chegar aos usuários.
     */
    USER("Usuário"),
}
