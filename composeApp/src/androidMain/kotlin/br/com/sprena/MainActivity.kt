package br.com.sprena

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * MainActivity — única Activity do app.
 *
 * F1.1: aplica [WindowManager.LayoutParams.FLAG_SECURE] em todo o app.
 * Why: bloqueia screenshots/screen recording em telas com dados sensíveis
 * (login, CPF do cliente, valores financeiros). Abordagem global por
 * simplicidade — alternativa per-screen exigiria wrapping em todo Composable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
