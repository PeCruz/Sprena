package br.com.sprena.shared.core.di

import org.koin.dsl.module

/**
 * Módulo Koin do domínio Financeiro.
 *
 * Registra UseCases, Repositories e DataSources do domínio financeiro.
 * Preenchido progressivamente via TDD (Day 3+).
 *
 * Exemplo futuro:
 * ```kotlin
 * val financialModule = module {
 *     single<TransactionRepository> { TransactionRepositoryImpl(get(), get()) }
 *     singleOf(::GetTransactionSummaryUseCase)
 *     singleOf(::CreateTransactionUseCase)
 *     singleOf(::GetDashboardMetricsUseCase)
 * }
 * ```
 */
val financialModule =
    module {
        // TODO (Day 3+): adicionar dependências conforme features forem implementadas com TDD
    }
