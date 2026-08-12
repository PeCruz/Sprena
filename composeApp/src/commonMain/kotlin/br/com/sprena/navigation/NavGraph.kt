package br.com.sprena.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.sprena.core.platform.rememberFilePicker
import br.com.sprena.presentation.bar.BarIntent
import br.com.sprena.presentation.bar.BarScreen
import br.com.sprena.presentation.bar.BarViewModel
import br.com.sprena.presentation.bar.addclient.AddClientDialog
import br.com.sprena.presentation.bar.addclient.AddClientViewModel
import br.com.sprena.presentation.bar.clientdetail.ClientDetailSheet
import br.com.sprena.presentation.bar.clientdetail.ClientDetailViewModel
import br.com.sprena.presentation.category.CategoryScreen
import br.com.sprena.presentation.category.CategoryViewModel
import br.com.sprena.presentation.consent.ConsentScreen
import br.com.sprena.presentation.consent.ConsentViewModel
import br.com.sprena.presentation.core.navigation.BottomNavIntent
import br.com.sprena.presentation.core.navigation.BottomNavViewModel
import br.com.sprena.presentation.core.navigation.BottomTab
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.presentation.eventos.EventCategory
import br.com.sprena.presentation.eventos.EventosIntent
import br.com.sprena.presentation.eventos.EventosScreen
import br.com.sprena.presentation.eventos.EventosViewModel
import br.com.sprena.presentation.eventos.createevent.CreateEventScreen
import br.com.sprena.presentation.eventos.createevent.CreateEventViewModel
import br.com.sprena.presentation.financial.FinancialScreen
import br.com.sprena.presentation.financial.FinancialViewModel
import br.com.sprena.presentation.financial.addtransaction.AddTransactionDialog
import br.com.sprena.presentation.financial.addtransaction.AddTransactionViewModel
import br.com.sprena.presentation.home.HomeUiEvent
import br.com.sprena.presentation.home.HomeViewModel
import br.com.sprena.presentation.kanban.KanbanIntent
import br.com.sprena.presentation.kanban.KanbanViewModel
import br.com.sprena.presentation.kanban.createtask.CreateTaskIntent
import br.com.sprena.presentation.kanban.createtask.CreateTaskScreen
import br.com.sprena.presentation.kanban.createtask.CreateTaskViewModel
import br.com.sprena.presentation.login.LoginScreen
import br.com.sprena.presentation.login.LoginViewModel
import br.com.sprena.presentation.menu.MenuScreen
import br.com.sprena.presentation.menu.MenuViewModel
import br.com.sprena.presentation.privacy.PrivacyPolicyScreen
import br.com.sprena.presentation.settings.SettingsNavigation
import br.com.sprena.presentation.settings.SettingsScreen
import br.com.sprena.presentation.sportclient.SportClient
import br.com.sprena.presentation.sportclient.SportClientEffect
import br.com.sprena.presentation.sportclient.SportClientIntent
import br.com.sprena.presentation.sportclient.SportClientScreen
import br.com.sprena.presentation.sportclient.SportClientViewModel
import br.com.sprena.presentation.sportclient.edit.SportClientEditScreen
import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.usecase.RestoreSessionUseCase
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Rotas de navegacao do app.
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val HOME_WITH_ARGS = "home/{userId}/{username}/{userName}/{userRole}"
    const val CREATE_TASK = "create_task"
    const val CREATE_EVENT = "create_event"
    const val EDIT_SPORT_CLIENT = "edit_sport_client"
    const val SETTINGS = "settings"
    const val MENU = "menu"
    const val CATEGORY = "category"
    const val CONSENT = "consent"
    const val PRIVACY_POLICY = "privacy_policy"
}

/** Monta a rota da Home com os argumentos que ela espera no path. */
private fun homeRoute(
    uid: String,
    email: String,
    name: String,
    role: UserRole,
): String = "${Routes.HOME}/$uid/$email/${name.replace(" ", "+")}/${role.name}"

/** Rota da Home a partir da sessão persistida — o nome sai do prefixo do email. */
private fun homeRouteFor(session: SessionUser): String =
    homeRoute(
        uid = session.uid,
        email = session.email,
        name = session.email.substringBefore('@'),
        role = session.role,
    )

/**
 * Grafo de navegacao principal.
 * Login -> Home (BottomNav: Home / Eventos / Comandas / Financeiro / Config)
 *       -> CreateTask | CreateEvent | Settings | Menu | Category.
 */
@Composable
fun NavGraph(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()
    val menuViewModel: MenuViewModel = koinViewModel()
    val categoryViewModel: CategoryViewModel = koinViewModel()

    val restoreUseCase: RestoreSessionUseCase = koinInject()
    val checkConsent: CheckConsentUseCase = koinInject()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination =
            when (val result = restoreUseCase()) {
                is RestoreResult.Authenticated -> {
                    val session = result.user
                    // Gate fail-closed: só Granted entra na Home. Required e
                    // Unavailable (falha de leitura) vão para o consentimento.
                    if (checkConsent(session.uid) is ConsentStatus.Granted) {
                        homeRouteFor(session)
                    } else {
                        Routes.CONSENT
                    }
                }
                is RestoreResult.NotAuthenticated -> Routes.LOGIN
            }
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
    ) {
        composable(route = Routes.LOGIN) {
            val loginViewModel: LoginViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            LoginScreen(
                viewModel = loginViewModel,
                themeViewModel = themeViewModel,
                onNavigateHome = { user ->
                    scope.launch {
                        val destination =
                            if (checkConsent(user.id) is ConsentStatus.Granted) {
                                homeRoute(
                                    uid = user.id,
                                    email = user.email,
                                    name = user.name,
                                    role = user.role,
                                )
                            } else {
                                Routes.CONSENT
                            }
                        navController.navigate(destination) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(route = Routes.CONSENT) {
            val consentViewModel: ConsentViewModel = koinViewModel()
            ConsentScreen(
                viewModel = consentViewModel,
                onNavigateHome = { session ->
                    navController.navigate(homeRouteFor(session)) {
                        popUpTo(Routes.CONSENT) { inclusive = true }
                    }
                },
                onNavigateLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.HOME_WITH_ARGS,
            arguments =
                listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("username") { type = NavType.StringType },
                    navArgument("userName") { type = NavType.StringType },
                    navArgument("userRole") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val userName = (backStackEntry.arguments?.getString("userName") ?: "").replace("+", " ")
            val userRoleStr = backStackEntry.arguments?.getString("userRole") ?: "CLIENT"
            val userRole = UserRole.valueOf(userRoleStr)
            val authenticatedUser =
                UserModel(
                    id = userId,
                    email = username,
                    name = userName,
                    role = userRole,
                )

            val savedStateHandle = backStackEntry.savedStateHandle
            val createdName = savedStateHandle.get<String>("created_task_name")
            val createdPriority = savedStateHandle.get<Int>("created_task_priority")

            val createdEventId = savedStateHandle.get<String?>("created_event_id")
            val createdEventName = savedStateHandle.get<String>("created_event_name")
            val createdEventCategory = savedStateHandle.get<String>("created_event_category")
            val createdEventDate = savedStateHandle.get<Long>("created_event_date")
            val createdEventContact = savedStateHandle.get<String?>("created_event_contact")
            val createdEventDescription = savedStateHandle.get<String?>("created_event_description")

            HomeWithBottomNav(
                navController = navController,
                themeViewModel = themeViewModel,
                menuViewModel = menuViewModel,
                categoryViewModel = categoryViewModel,
                authenticatedUser = authenticatedUser,
                createdTaskName = createdName,
                createdTaskPriority = createdPriority,
                onTaskConsumed = {
                    savedStateHandle.remove<String>("created_task_name")
                    savedStateHandle.remove<Int>("created_task_priority")
                },
                createdEventId = createdEventId,
                createdEventName = createdEventName,
                createdEventCategory = createdEventCategory,
                createdEventDate = createdEventDate,
                createdEventContact = createdEventContact,
                createdEventDescription = createdEventDescription,
                onEventConsumed = {
                    savedStateHandle.remove<String?>("created_event_id")
                    savedStateHandle.remove<String>("created_event_name")
                    savedStateHandle.remove<String>("created_event_category")
                    savedStateHandle.remove<Long>("created_event_date")
                    savedStateHandle.remove<String?>("created_event_contact")
                    savedStateHandle.remove<String?>("created_event_description")
                    // Clear edit data keys
                    savedStateHandle.remove<String>("edit_event_id")
                    savedStateHandle.remove<String>("edit_event_name")
                    savedStateHandle.remove<String>("edit_event_category")
                    savedStateHandle.remove<Long>("edit_event_date")
                    savedStateHandle.remove<String?>("edit_event_contact")
                    savedStateHandle.remove<String?>("edit_event_description")
                },
            )
        }

        composable(route = Routes.CREATE_TASK) {
            val createTaskViewModel: CreateTaskViewModel = koinViewModel()

            val launchFilePicker =
                rememberFilePicker { pickedFile ->
                    createTaskViewModel.handleIntent(
                        CreateTaskIntent.AttachmentSelected(
                            name = pickedFile.name,
                            sizeBytes = pickedFile.sizeBytes,
                        ),
                    )
                }

            CreateTaskScreen(
                viewModel = createTaskViewModel,
                themeViewModel = themeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onTaskCreated = { name, priority ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_task_name", name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_task_priority", priority)
                    navController.popBackStack()
                },
                onPickFile = launchFilePicker,
            )
        }

        composable(route = Routes.CREATE_EVENT) {
            val createEventViewModel: CreateEventViewModel = koinViewModel()

            // Check if we're editing an existing event (data set by HOME before navigating)
            val previousEntry = navController.previousBackStackEntry
            val editEventId = previousEntry?.savedStateHandle?.get<String>("edit_event_id")
            val editEventName = previousEntry?.savedStateHandle?.get<String>("edit_event_name")
            val editEventCategory = previousEntry?.savedStateHandle?.get<String>("edit_event_category")
            val editEventDate = previousEntry?.savedStateHandle?.get<Long>("edit_event_date")
            val editEventContact = previousEntry?.savedStateHandle?.get<String?>("edit_event_contact")
            val editEventDescription = previousEntry?.savedStateHandle?.get<String?>("edit_event_description")

            LaunchedEffect(editEventId) {
                if (editEventId != null &&
                    editEventName != null &&
                    editEventCategory != null &&
                    editEventDate != null
                ) {
                    createEventViewModel.handleIntent(
                        br.com.sprena.presentation.eventos.createevent.CreateEventIntent.LoadForEdit(
                            eventId = editEventId,
                            name = editEventName,
                            category = EventCategory.valueOf(editEventCategory),
                            dateEpochDay = editEventDate,
                            contact = editEventContact,
                            description = editEventDescription,
                        ),
                    )
                    // Clear edit keys so they don't persist on re-navigation
                    previousEntry?.savedStateHandle?.remove<String>("edit_event_id")
                    previousEntry?.savedStateHandle?.remove<String>("edit_event_name")
                    previousEntry?.savedStateHandle?.remove<String>("edit_event_category")
                    previousEntry?.savedStateHandle?.remove<Long>("edit_event_date")
                    previousEntry?.savedStateHandle?.remove<String?>("edit_event_contact")
                    previousEntry?.savedStateHandle?.remove<String?>("edit_event_description")
                }
            }

            CreateEventScreen(
                viewModel = createEventViewModel,
                onNavigateBack = { navController.popBackStack() },
                onEventSaved = { eventId, name, category, dateEpochDay, contact, description ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_id", eventId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_name", name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_category", category.name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_date", dateEpochDay)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_contact", contact)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_event_description", description)
                },
            )
        }

        composable(route = Routes.EDIT_SPORT_CLIENT) {
            val previousEntry = navController.previousBackStackEntry
            val editClientId = previousEntry?.savedStateHandle?.get<String>("edit_client_id") ?: ""
            val editClientName = previousEntry?.savedStateHandle?.get<String>("edit_client_name") ?: ""
            val editClientApelido = previousEntry?.savedStateHandle?.get<String>("edit_client_apelido") ?: ""
            val editClientCpf = previousEntry?.savedStateHandle?.get<String>("edit_client_cpf") ?: ""
            val editClientPhone = previousEntry?.savedStateHandle?.get<String>("edit_client_phone") ?: ""
            val editClientModalities = previousEntry?.savedStateHandle?.get<String>("edit_client_modalities") ?: ""
            val editClientAttendance = previousEntry?.savedStateHandle?.get<Int>("edit_client_attendance") ?: 1
            val editClientPayment = previousEntry?.savedStateHandle?.get<String>("edit_client_payment") ?: "CASH"
            val editClientCashCents = previousEntry?.savedStateHandle?.get<Long>("edit_client_cash_cents") ?: 0L
            val editClientPaymentHistory =
                previousEntry?.savedStateHandle?.get<String>("edit_client_payment_history") ?: ""

            val modalities =
                editClientModalities
                    .split(",")
                    .filter { it.isNotBlank() }
                    .mapNotNull { name ->
                        br.com.sprena.shared.sportclient.domain.validation.SportModality.entries
                            .firstOrNull { it.name == name }
                    }
            val paymentMethod =
                br.com.sprena.shared.sportclient.domain.validation.PaymentMethod.entries
                    .firstOrNull { it.name == editClientPayment }
                    ?: br.com.sprena.shared.sportclient.domain.validation.PaymentMethod.CASH
            val history = editClientPaymentHistory.split(",").filter { it.isNotBlank() }

            val clientToEdit =
                SportClient(
                    id = editClientId,
                    name = editClientName,
                    apelido = editClientApelido,
                    cpf = editClientCpf,
                    phone = editClientPhone,
                    modalities = modalities,
                    attendance = editClientAttendance,
                    paymentMethod = paymentMethod,
                    cashAmountCents = editClientCashCents,
                    paymentHistory = history,
                )

            SportClientEditScreen(
                client = clientToEdit,
                onNavigateBack = { navController.popBackStack() },
                onSave = { updatedClient ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_id", updatedClient.id)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_name", updatedClient.name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_apelido", updatedClient.apelido)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_cpf", updatedClient.cpf)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_phone", updatedClient.phone)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_modalities", updatedClient.modalities.joinToString(",") { it.name })
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_attendance", updatedClient.attendance)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_payment", updatedClient.paymentMethod.name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_cash_cents", updatedClient.cashAmountCents)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("updated_client_payment_history", updatedClient.paymentHistory.joinToString(","))
                    navController.popBackStack()
                },
            )
        }

        composable(route = Routes.SETTINGS) {
            SettingsScreen(
                themeViewModel = themeViewModel,
                navigation =
                    SettingsNavigation(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateMenu = { navController.navigate(Routes.MENU) },
                        onNavigateCategory = { navController.navigate(Routes.CATEGORY) },
                        onNavigateToLogin = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigatePrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                    ),
            )
        }

        composable(route = Routes.MENU) {
            MenuScreen(
                viewModel = menuViewModel,
                themeViewModel = themeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = Routes.CATEGORY) {
            CategoryScreen(
                viewModel = categoryViewModel,
                themeViewModel = themeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(
                loader = koinInject(),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWithBottomNav(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    menuViewModel: MenuViewModel,
    categoryViewModel: CategoryViewModel,
    authenticatedUser: UserModel? = null,
    createdTaskName: String? = null,
    createdTaskPriority: Int? = null,
    onTaskConsumed: () -> Unit = {},
    createdEventId: String? = null,
    createdEventName: String? = null,
    createdEventCategory: String? = null,
    createdEventDate: Long? = null,
    createdEventContact: String? = null,
    createdEventDescription: String? = null,
    onEventConsumed: () -> Unit = {},
) {
    val bottomNavViewModel: BottomNavViewModel = koinViewModel()
    val bottomNavState by bottomNavViewModel.state.collectAsState()

    val homeViewModel: HomeViewModel = koinViewModel()
    val sportClientViewModel: SportClientViewModel = koinViewModel()
    val kanbanViewModel: KanbanViewModel = koinViewModel()
    val eventosViewModel: EventosViewModel = koinViewModel()

    // Carrega dados do usuário autenticado no HomeViewModel
    LaunchedEffect(authenticatedUser) {
        if (authenticatedUser != null) {
            homeViewModel.onEvent(HomeUiEvent.UserLoaded(authenticatedUser))
        }
    }

    LaunchedEffect(createdTaskName, createdTaskPriority) {
        if (createdTaskName != null && createdTaskPriority != null) {
            kanbanViewModel.handleIntent(
                KanbanIntent.TaskCreated(createdTaskName, createdTaskPriority),
            )
            onTaskConsumed()
        }
    }

    LaunchedEffect(createdEventName, createdEventCategory, createdEventDate) {
        if (createdEventName != null && createdEventCategory != null && createdEventDate != null) {
            val category = EventCategory.valueOf(createdEventCategory)
            if (createdEventId != null) {
                // Edit mode — update existing event
                eventosViewModel.handleIntent(
                    EventosIntent.EventUpdated(
                        eventId = createdEventId,
                        name = createdEventName,
                        category = category,
                        dateEpochDay = createdEventDate,
                        contact = createdEventContact,
                        description = createdEventDescription,
                    ),
                )
            } else {
                // Create mode — new event
                eventosViewModel.handleIntent(
                    EventosIntent.EventCreated(
                        name = createdEventName,
                        category = category,
                        dateEpochDay = createdEventDate,
                        contact = createdEventContact,
                        description = createdEventDescription,
                    ),
                )
            }
            onEventConsumed()
        }
    }

    // Collect SportClient effects for navigation
    LaunchedEffect(Unit) {
        sportClientViewModel.effects.collect { effect ->
            when (effect) {
                is SportClientEffect.NavigateToEdit -> {
                    val client = effect.client
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_id", client.id)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_name", client.name)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_apelido", client.apelido)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_cpf", client.cpf)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_phone", client.phone)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_modalities", client.modalities.joinToString(",") { it.name })
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_attendance", client.attendance)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_payment", client.paymentMethod.name)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_cash_cents", client.cashAmountCents)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("edit_client_payment_history", client.paymentHistory.joinToString(","))
                    navController.navigate(Routes.EDIT_SPORT_CLIENT)
                }
                is SportClientEffect.ShowError -> { /* handled in screen */ }
            }
        }
    }

    // Handle updated client returning from edit screen
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val updatedClientId = savedStateHandle?.get<String>("updated_client_id")
    LaunchedEffect(updatedClientId) {
        if (updatedClientId != null) {
            val updatedName = savedStateHandle.get<String>("updated_client_name") ?: ""
            val updatedApelido = savedStateHandle.get<String>("updated_client_apelido") ?: ""
            val updatedCpf = savedStateHandle.get<String>("updated_client_cpf") ?: ""
            val updatedPhone = savedStateHandle.get<String>("updated_client_phone") ?: ""
            val updatedModalities =
                (savedStateHandle.get<String>("updated_client_modalities") ?: "")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .mapNotNull { name ->
                        br.com.sprena.shared.sportclient.domain.validation.SportModality.entries
                            .firstOrNull { it.name == name }
                    }
            val updatedAttendance = savedStateHandle.get<Int>("updated_client_attendance") ?: 1
            val updatedPayment =
                br.com.sprena.shared.sportclient.domain.validation.PaymentMethod.entries
                    .firstOrNull { it.name == (savedStateHandle.get<String>("updated_client_payment") ?: "") }
                    ?: br.com.sprena.shared.sportclient.domain.validation.PaymentMethod.CASH
            val updatedCashCents = savedStateHandle.get<Long>("updated_client_cash_cents") ?: 0L
            val updatedPaymentHistory =
                (savedStateHandle.get<String>("updated_client_payment_history") ?: "")
                    .split(",")
                    .filter { it.isNotBlank() }

            val updatedClient =
                SportClient(
                    id = updatedClientId,
                    name = updatedName,
                    apelido = updatedApelido,
                    cpf = updatedCpf,
                    phone = updatedPhone,
                    modalities = updatedModalities,
                    attendance = updatedAttendance,
                    paymentMethod = updatedPayment,
                    cashAmountCents = updatedCashCents,
                    paymentHistory = updatedPaymentHistory,
                )
            sportClientViewModel.handleIntent(SportClientIntent.ClientUpdated(updatedClient))

            // Clean up
            savedStateHandle.remove<String>("updated_client_id")
            savedStateHandle.remove<String>("updated_client_name")
            savedStateHandle.remove<String>("updated_client_apelido")
            savedStateHandle.remove<String>("updated_client_cpf")
            savedStateHandle.remove<String>("updated_client_phone")
            savedStateHandle.remove<String>("updated_client_modalities")
            savedStateHandle.remove<Int>("updated_client_attendance")
            savedStateHandle.remove<String>("updated_client_payment")
            savedStateHandle.remove<Long>("updated_client_cash_cents")
            savedStateHandle.remove<String>("updated_client_payment_history")
        }
    }

    val financialViewModel: FinancialViewModel = koinViewModel()
    val financialState by financialViewModel.state.collectAsState()

    val barViewModel: BarViewModel = koinViewModel()
    val barState by barViewModel.state.collectAsState()

    val menuState by menuViewModel.state.collectAsState()
    val categoryState by categoryViewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = bottomNavState.current == BottomTab.HOME,
                    onClick = {
                        bottomNavViewModel.handleIntent(
                            BottomNavIntent.TabSelected(BottomTab.HOME),
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                        )
                    },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = bottomNavState.current == BottomTab.EVENTOS,
                    onClick = {
                        bottomNavViewModel.handleIntent(
                            BottomNavIntent.TabSelected(BottomTab.EVENTOS),
                        )
                    },
                    icon = {
                        Text(
                            text = "📅",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    label = { Text("Eventos") },
                )
                NavigationBarItem(
                    selected = bottomNavState.current == BottomTab.BAR,
                    onClick = {
                        bottomNavViewModel.handleIntent(
                            BottomNavIntent.TabSelected(BottomTab.BAR),
                        )
                    },
                    icon = {
                        Text(
                            text = "🍺",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    label = { Text("Comandas") },
                )
                NavigationBarItem(
                    selected = bottomNavState.current == BottomTab.FINANCIAL,
                    onClick = {
                        bottomNavViewModel.handleIntent(
                            BottomNavIntent.TabSelected(BottomTab.FINANCIAL),
                        )
                    },
                    icon = {
                        Text(
                            text = "R$",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    label = { Text("Financeiro") },
                )
                NavigationBarItem(
                    selected = bottomNavState.current == BottomTab.SETTINGS,
                    onClick = {
                        bottomNavViewModel.handleIntent(
                            BottomNavIntent.TabSelected(BottomTab.SETTINGS),
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuracoes",
                        )
                    },
                    label = { Text("Config") },
                )
            }
        },
    ) { bottomNavPadding ->
        when (bottomNavState.current) {
            BottomTab.HOME -> {
                SportClientScreen(
                    viewModel = sportClientViewModel,
                    themeViewModel = themeViewModel,
                    modifier = Modifier.padding(bottomNavPadding),
                )
            }

            BottomTab.EVENTOS -> {
                EventosScreen(
                    viewModel = eventosViewModel,
                    themeViewModel = themeViewModel,
                    modifier = Modifier.padding(bottomNavPadding),
                    onNavigateCreateEvent = {
                        navController.navigate(Routes.CREATE_EVENT)
                    },
                    onNavigateEditEvent = { event ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_id", event.id)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_name", event.name)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_category", event.category.name)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_date", event.dateEpochDay)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_contact", event.contact)
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("edit_event_description", event.description)
                        navController.navigate(Routes.CREATE_EVENT)
                    },
                )
            }

            BottomTab.FINANCIAL -> {
                FinancialScreen(
                    viewModel = financialViewModel,
                    themeViewModel = themeViewModel,
                    modifier = Modifier.padding(bottomNavPadding),
                )
            }

            BottomTab.BAR -> {
                BarScreen(
                    viewModel = barViewModel,
                    themeViewModel = themeViewModel,
                    modifier = Modifier.padding(bottomNavPadding),
                )
            }

            BottomTab.SETTINGS -> {
                SettingsScreen(
                    themeViewModel = themeViewModel,
                    modifier = Modifier.padding(bottomNavPadding),
                    navigation =
                        SettingsNavigation(
                            onNavigateMenu = { navController.navigate(Routes.MENU) },
                            onNavigateCategory = { navController.navigate(Routes.CATEGORY) },
                            onNavigateToLogin = {
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigatePrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                        ),
                )
            }
        }
    }

    if (financialState.isAddDialogVisible) {
        val addTransactionViewModel = remember { AddTransactionViewModel() }
        AddTransactionDialog(
            viewModel = addTransactionViewModel,
            categories = categoryState.categories,
            onDismiss = {
                financialViewModel.handleIntent(
                    br.com.sprena.presentation.financial.FinancialIntent.DismissAddDialog,
                )
            },
            onTransactionCreated = { transaction ->
                financialViewModel.handleIntent(
                    br.com.sprena.presentation.financial.FinancialIntent
                        .TransactionAdded(transaction),
                )
            },
        )
    }

    if (financialState.isEditDialogVisible && financialState.editingTransactionId != null) {
        val editingTx =
            financialState.transactions.find {
                it.id == financialState.editingTransactionId
            }
        if (editingTx != null) {
            val editTransactionViewModel =
                remember(editingTx.id) {
                    AddTransactionViewModel()
                }
            LaunchedEffect(editingTx.id) {
                editTransactionViewModel.handleIntent(
                    br.com.sprena.presentation.financial.addtransaction.AddTransactionIntent
                        .LoadForEdit(editingTx),
                )
            }
            AddTransactionDialog(
                viewModel = editTransactionViewModel,
                categories = categoryState.categories,
                isEditMode = true,
                onDismiss = {
                    financialViewModel.handleIntent(
                        br.com.sprena.presentation.financial.FinancialIntent.DismissEditDialog,
                    )
                },
                onTransactionCreated = { },
                onTransactionUpdated = { transaction ->
                    financialViewModel.handleIntent(
                        br.com.sprena.presentation.financial.FinancialIntent
                            .TransactionUpdated(transaction),
                    )
                },
                onTransactionDeleted = {
                    financialViewModel.handleIntent(
                        br.com.sprena.presentation.financial.FinancialIntent
                            .TransactionDeleted(editingTx.id),
                    )
                },
            )
        }
    }

    if (barState.isAddClientDialogVisible) {
        val addClientViewModel = remember { AddClientViewModel() }
        AddClientDialog(
            viewModel = addClientViewModel,
            onDismiss = {
                barViewModel.handleIntent(BarIntent.DismissAddClientDialog)
            },
            onClientCreated = { client ->
                barViewModel.handleIntent(BarIntent.ClientAdded(client))
                barViewModel.handleIntent(BarIntent.DismissAddClientDialog)
            },
        )
    }

    if (barState.selectedClient != null) {
        val selectedClient = barState.selectedClient!!
        val clientDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clientDetailViewModel =
            remember(selectedClient.id) {
                ClientDetailViewModel(client = selectedClient)
            }
        ClientDetailSheet(
            viewModel = clientDetailViewModel,
            sheetState = clientDetailSheetState,
            menuItems = menuState.items,
            onDismiss = {
                barViewModel.handleIntent(BarIntent.DismissClientDetail)
            },
            onClientUpdated = { updatedClient ->
                barViewModel.handleIntent(BarIntent.ClientUpdated(updatedClient))
            },
            onClientDeleted = { clientId ->
                barViewModel.handleIntent(BarIntent.ClientDeleted(clientId))
                barViewModel.handleIntent(BarIntent.DismissClientDetail)
            },
        )
    }
}
