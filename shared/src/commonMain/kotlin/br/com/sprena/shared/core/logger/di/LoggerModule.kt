package br.com.sprena.shared.core.logger.di

import org.koin.core.module.Module

/**
 * Cada plataforma fornece sua impl de [br.com.sprena.shared.core.logger.Logger].
 * Android: AndroidLogger (Napier + Crashlytics). iOS (futuro): NSLog + Crashlytics iOS.
 */
expect fun loggerModule(): Module
