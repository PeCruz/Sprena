package br.com.sprena.shared.core.logger

/**
 * Logger que descarta tudo. Usar em commonTest e como fallback DI.
 */
class NoOpLogger : Logger {
    override fun debug(tag: String, message: String, throwable: Throwable?) = Unit
    override fun info(tag: String, message: String, throwable: Throwable?) = Unit
    override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
