package br.com.sprena.shared.core.logger

import br.com.sprena.shared.core.logger.pii.PiiScrubber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Napier

/**
 * Impl Android: Napier (console em debug) + Firebase Crashlytics (release).
 *
 * Todas as mensagens passam por [PiiScrubber] antes de emitir.
 * - `debug`/`info`: apenas Napier
 * - `warn`: Napier + Crashlytics.log (breadcrumb)
 * - `error`: Napier + Crashlytics.log + recordException (se throwable não-null)
 *
 * Crashlytics em si é ligado/desligado em [LoggerBootstrap.init].
 */
class AndroidLogger : Logger {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun debug(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.d(message = safe, throwable = throwable, tag = tag)
    }

    override fun info(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.i(message = safe, throwable = throwable, tag = tag)
    }

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.w(message = safe, throwable = throwable, tag = tag)
        crashlytics.log("[$tag] WARN: $safe")
    }

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val safe = PiiScrubber.scrub(message) ?: ""
        Napier.e(message = safe, throwable = throwable, tag = tag)
        crashlytics.log("[$tag] ERROR: $safe")
        throwable?.let { crashlytics.recordException(it) }
    }
}
