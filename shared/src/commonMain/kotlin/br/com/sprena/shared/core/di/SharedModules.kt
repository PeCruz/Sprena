package br.com.sprena.shared.core.di

import br.com.sprena.shared.auth.di.authModule
import br.com.sprena.shared.privacy.di.privacyModule
import br.com.sprena.shared.sportclient.di.sportClientModule

/**
 * Agrega todos os módulos Koin do módulo `shared`.
 *
 * Use esta função no `startKoin` da aplicação para registrar
 * toda a infraestrutura e domínios compartilhados.
 *
 * Ordem de carregamento:
 *  1. [authModule]         → domínio Auth (autenticação + autorização)
 *  2. [kanbanModule]       → domínio Kanban
 *  3. [financialModule]    → domínio Financeiro
 *  4. [sportClientModule]  → domínio SportClient
 *  5. [privacyModule]      → domínio Privacidade (consentimento LGPD)
 *
 * Firebase Firestore é inicializado no platformModule (Android)
 * e injetado via Koin nas implementações de Repository.
 */
fun sharedModules() =
    listOf(
        authModule(),
        kanbanModule,
        financialModule,
        sportClientModule,
        privacyModule(),
    )
