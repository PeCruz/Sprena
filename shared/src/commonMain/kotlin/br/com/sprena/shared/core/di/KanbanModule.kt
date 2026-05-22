package br.com.sprena.shared.core.di

import org.koin.dsl.module

/**
 * Módulo Koin do domínio Kanban.
 *
 * Registra UseCases, Repositories e DataSources do domínio Kanban.
 * Preenchido progressivamente via TDD (Day 3+).
 *
 * Ordem de registro (quando implementado):
 *  1. DataSources (Firebase Firestore remote)
 *  2. Repositories (implementações)
 *  3. UseCases
 *
 * Exemplo futuro:
 * ```kotlin
 * val kanbanModule = module {
 *     single<TaskRepository> { TaskRepositoryImpl(get(), get()) }
 *     singleOf(::GetBoardTasksUseCase)
 *     singleOf(::MoveTaskUseCase)
 *     singleOf(::CreateBoardUseCase)
 * }
 * ```
 */
val kanbanModule =
    module {
        // TODO (Day 3+): adicionar dependências conforme features forem implementadas com TDD
    }
