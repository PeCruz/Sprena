package br.com.sprena.shared.sportclient.di

import org.koin.dsl.module

/**
 * Módulo Koin do domínio SportClient.
 *
 * Registra UseCases e Repository do domínio SportClient.
 *
 * ⚠️ A binding concreta [SportClientRepository] → [SportClientRepositoryImpl]
 * é feita no platformModule (Android), pois a implementação depende de Firebase.
 */
val sportClientModule =
    module {
        // Repository binding é feito no platformModule (depende de Firebase)
        // UseCases serão adicionados aqui conforme implementados via TDD
    }
