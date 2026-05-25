package br.com.sprena.shared.core.logger

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Inicialização do stack de logging. Chamar uma vez na Application.onCreate.
 *
 * Em debug: planta DebugAntilog (println com formatação) e desabilita Crashlytics.
 * Em release: NÃO planta antilog (Napier vira no-op) e habilita Crashlytics —
 * o envio para Crashlytics acontece via [AndroidLogger] explicitamente.
 */
object LoggerBootstrap {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!isDebug)
    }
}
