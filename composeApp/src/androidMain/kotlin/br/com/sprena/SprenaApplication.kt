package br.com.sprena

import android.app.Application
import br.com.sprena.di.appModule
import br.com.sprena.di.platformModule
import br.com.sprena.shared.core.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class — ponto de entrada do app Android.
 *
 * Inicializa o Koin com todos os módulos de DI na ordem correta:
 *  1. [platformModule]   → Firebase Firestore (Android-specific)
 *  2. [sharedModules]    → domínios compartilhados (kanban, financial)
 *  3. [appModule]        → ViewModels da camada de apresentação
 */
class SprenaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@SprenaApplication)
            modules(
                platformModule(),
                *sharedModules().toTypedArray(),
                appModule(),
            )
        }
    }
}
