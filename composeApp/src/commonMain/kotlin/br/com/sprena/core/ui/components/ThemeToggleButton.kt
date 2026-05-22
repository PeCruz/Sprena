package br.com.sprena.core.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import br.com.sprena.presentation.core.theme.ThemeIntent
import br.com.sprena.presentation.core.theme.ThemeMode
import br.com.sprena.presentation.core.theme.ThemeViewModel

@Composable
fun ThemeToggleButton(themeViewModel: ThemeViewModel) {
    val themeState by themeViewModel.state.collectAsState()
    val isDark = themeState.mode == ThemeMode.DARK

    IconButton(
        onClick = { themeViewModel.handleIntent(ThemeIntent.Toggle) },
    ) {
        Text(
            text = if (isDark) "\u2600" else "\uD83C\uDF19",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
