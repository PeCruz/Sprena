package br.com.sprena.presentation.core.tenant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.establishment.domain.model.TenantContext
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Monta o [TenantContext] a partir das três fontes que o definem: o papel global (sessão), os
 * vínculos (collection group) e o estabelecimento ativo (`user_settings`).
 *
 * Fica fora do `NavGraph` de propósito. A barra precisa saber apenas *quais abas mostrar*, e
 * juntar essas três leituras dentro de um Composable de 950 linhas espalharia a decisão por um
 * arquivo que já é difícil de acompanhar.
 */
class TenantViewModel(
    memberships: MembershipRepository,
    activeEstablishment: ActiveEstablishmentRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _context = MutableStateFlow<TenantContext?>(null)

    /**
     * `null` significa **ainda não sei**, e a UI trata isso como carregamento.
     *
     * Uma falha de leitura mantém o valor anterior (ou `null`, se nunca houve um) em vez de
     * emitir um contexto vazio. A diferença importa: contexto vazio leva à tela de "sem
     * estabelecimento vinculado", que manda a pessoa procurar um Moderador — conselho errado
     * e caro quando o problema era só a rede.
     */
    val context: StateFlow<TenantContext?> = _context.asStateFlow()

    init {
        viewModelScope.launch {
            val globalRole = sessionStore.load()?.role ?: UserRole.USER

            combine(
                memberships.observeMine(),
                activeEstablishment.observe(),
            ) { mine, active ->
                mine.getOrNull()?.let { list ->
                    TenantContext(
                        globalRole = globalRole,
                        memberships = list,
                        // Falha ao ler a preferência é benigna: sem id ativo o contexto cai no
                        // único vínculo, ou pede uma escolha. Só a falha dos vínculos é fatal.
                        activeEstablishmentId = active.getOrNull(),
                    )
                }
            }.collect { resolved -> resolved?.let { _context.value = it } }
        }
    }
}
