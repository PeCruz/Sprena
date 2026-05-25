package br.com.sprena.shared.core.logger.di

import br.com.sprena.shared.core.logger.AndroidLogger
import br.com.sprena.shared.core.logger.Logger
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun loggerModule(): Module = module {
    single<Logger> { AndroidLogger() }
}
