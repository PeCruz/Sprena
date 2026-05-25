package br.com.sprena.shared.core.logger

/**
 * Interface comum de logging do Sprena.
 *
 * Convenções:
 * - `tag`: nome da classe/feature, ex.: "SportClientRepo", "LoginUseCase"
 * - `message`: passa por [pii.PiiScrubber] na impl antes de emitir
 * - `throwable`: opcional; em release vira `recordException` no Crashlytics
 *
 * NÃO incluir PII bruto em `message` — use [pii.PiiMasker] no call site.
 */
interface Logger {
    fun debug(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun info(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
