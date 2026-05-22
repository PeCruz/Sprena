package br.com.sprena

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import br.com.sprena.core.ui.theme.SprenaTheme
import br.com.sprena.navigation.NavGraph
import br.com.sprena.presentation.core.theme.ThemeMode
import br.com.sprena.presentation.core.theme.ThemeViewModel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    KoinContext {
        val themeViewModel: ThemeViewModel = koinViewModel()
        val themeState by themeViewModel.state.collectAsState()
        val systemDark = isSystemInDarkTheme()

        val isDark =
            when (themeState.mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

        SprenaTheme(darkTheme = isDark) {
            NavGraph(themeViewModel = themeViewModel)
        }
    }
}
