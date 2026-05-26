package br.com.sprena

import android.app.Application
import br.com.sprena.di.appModule
import br.com.sprena.di.platformModule
import br.com.sprena.shared.auth.di.sessionModule
import br.com.sprena.shared.core.di.sharedModules
import br.com.sprena.shared.core.logger.LoggerBootstrap
import br.com.sprena.shared.core.logger.di.loggerModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class — ponto de entrada do app Android.
 *
 * Inicializa o stack de logging (Napier + Crashlytics) e o Koin com todos os
 * módulos de DI na ordem correta:
 *  1. [loggerModule]     → Logger (Napier + Crashlytics)
 *  2. [platformModule]   → Firebase Firestore (Android-specific)
 *  3. [sharedModules]    → domínios compartilhados (kanban, financial)
 *  4. [appModule]        → ViewModels da camada de apresentação
 */
class SprenaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        LoggerBootstrap.init(isDebug = BuildConfig.DEBUG)

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@SprenaApplication)
            modules(
                buildList {
                    add(loggerModule())
                    add(sessionModule())
                    add(platformModule())
                    addAll(sharedModules())
                    add(appModule())
                },
            )
        }
    }
}
