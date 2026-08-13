package br.com.sprena.di

import br.com.sprena.presentation.bar.BarViewModel
import br.com.sprena.presentation.bar.addclient.AddClientViewModel
import br.com.sprena.presentation.category.CategoryViewModel
import br.com.sprena.presentation.consent.ConsentViewModel
import br.com.sprena.presentation.core.navigation.BottomNavViewModel
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.presentation.eventos.EventosViewModel
import br.com.sprena.presentation.eventos.createevent.CreateEventViewModel
import br.com.sprena.presentation.financial.FinancialViewModel
import br.com.sprena.presentation.financial.addtransaction.AddTransactionViewModel
import br.com.sprena.presentation.home.HomeViewModel
import br.com.sprena.presentation.kanban.KanbanViewModel
import br.com.sprena.presentation.kanban.addtask.AddTaskViewModel
import br.com.sprena.presentation.kanban.createtask.CreateTaskViewModel
import br.com.sprena.presentation.login.LoginViewModel
import br.com.sprena.presentation.menu.MenuViewModel
import br.com.sprena.presentation.privacy.ComposeResourcePolicyTextLoader
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.presentation.settings.SettingsViewModel
import br.com.sprena.presentation.sportclient.SportClientViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun appModule() =
    module {
        single<PolicyTextLoader> { ComposeResourcePolicyTextLoader() }
        viewModelOf(::HomeViewModel)
        viewModel { LoginViewModel(loginUseCase = get(), requestPasswordReset = get()) }
        viewModelOf(::KanbanViewModel)
        viewModelOf(::AddTaskViewModel)
        viewModel { CreateTaskViewModel() }
        viewModel { FinancialViewModel() }
        viewModel { AddTransactionViewModel() }
        viewModelOf(::BottomNavViewModel)
        viewModelOf(::ThemeViewModel)
        viewModelOf(::BarViewModel)
        viewModelOf(::AddClientViewModel)
        viewModelOf(::MenuViewModel)
        viewModelOf(::CategoryViewModel)
        viewModel { SportClientViewModel(sessionStore = get()) }
        viewModel { EventosViewModel() }
        viewModel { CreateEventViewModel() }
        viewModel { SettingsViewModel(sessionStore = get(), logout = get()) }
        viewModel {
            ConsentViewModel(
                policyLoader = get(),
                acceptConsent = get(),
                sessionStore = get(),
            )
        }
    }
