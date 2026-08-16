package br.com.sprena.presentation.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sprena.shared.auth.domain.model.UserRole

/**
 * Mostrada quando a conta existe mas não tem vínculo com estabelecimento algum.
 *
 * Não é um erro: é o estado normal de toda conta recém-criada, já que o primeiro login passa a
 * criar a conta sozinho a partir de F1.7.3. O texto precisa dizer **a quem recorrer**, e isso
 * muda com o papel — um Moderador é vinculado pelo ADM, um frequentador pelo estabelecimento
 * onde ele vai.
 *
 * O ADM nunca chega aqui: `tabsFor` sempre lhe devolve abas, porque ele precisa alcançar a
 * Config para criar o primeiro estabelecimento.
 */
@Composable
fun NoEstablishmentScreen(
    role: UserRole,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "🏷️", style = MaterialTheme.typography.displayMedium)
        Text(
            text = "Sem estabelecimento vinculado",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = contactMessageFor(role),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Um Moderador só pode ser vinculado por um ADM; Funcionários e frequentadores são vinculados
 * por quem gere o estabelecimento. Mandar a pessoa à porta errada faz o suporte ficar mais
 * lento, não mais rápido.
 */
internal fun contactMessageFor(role: UserRole): String =
    when (role) {
        UserRole.MOD -> "Você não possui Estabelecimento vinculado, favor contatar algum ADM."
        else -> "Você não possui Estabelecimento vinculado, favor contatar algum Moderador."
    }
