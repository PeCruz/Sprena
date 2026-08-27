package br.com.sprena.presentation.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Rótulo e ícone de cada aba.
 *
 * Ficavam espalhados como literais dentro dos `NavigationBarItem` do `NavGraph`. Com a barra
 * passando a ser montada por iteração sobre `tabsFor(...)`, eles precisam morar junto do enum —
 * caso contrário cada aba nova exigiria mexer em dois lugares sem nada garantindo a sincronia.
 */
val BottomTab.label: String
    get() =
        when (this) {
            BottomTab.HOME -> "Home"
            BottomTab.EVENTOS -> "Eventos"
            BottomTab.BAR -> "Comandas"
            BottomTab.FINANCIAL -> "Financeiro"
            BottomTab.PROFILE -> "Perfil"
            BottomTab.CONFIG -> "Config"
        }

/**
 * Metade dos ícones é `ImageVector` do Material e a outra metade é emoji renderizado como
 * texto — herança de quando a barra era escrita item a item. Ficam juntos aqui para que a
 * inconsistência seja visível e endereçável de uma vez, em vez de reaparecer a cada aba nova.
 */
@Composable
fun BottomTab.TabIcon() {
    when (this) {
        BottomTab.HOME -> Icon(Icons.Default.Home, contentDescription = label)
        BottomTab.PROFILE -> Icon(Icons.Default.Person, contentDescription = label)
        BottomTab.CONFIG -> Icon(Icons.Default.Settings, contentDescription = label)
        BottomTab.EVENTOS -> Text("📅", style = MaterialTheme.typography.labelLarge)
        BottomTab.BAR -> Text("🍺", style = MaterialTheme.typography.labelLarge)
        BottomTab.FINANCIAL -> Text("R$", style = MaterialTheme.typography.labelLarge)
    }
}
