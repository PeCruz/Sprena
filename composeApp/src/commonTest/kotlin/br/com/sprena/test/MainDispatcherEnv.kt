package br.com.sprena.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Helpers de teste para viewModelScope (que depende de Dispatchers.Main).
 *
 * Uso:
 * ```kotlin
 * class MyViewModelTest {
 *     private val env = MainDispatcherEnv()
 *
 *     @BeforeTest fun setUp() = env.install()
 *     @AfterTest  fun tearDown() = env.uninstall()
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherEnv(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) {
    val testScope: TestScope = TestScope(dispatcher)

    fun install() {
        Dispatchers.setMain(dispatcher)
    }

    fun uninstall() {
        Dispatchers.resetMain()
    }
}
