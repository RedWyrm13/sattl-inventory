package org.sattl.inventory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.sattl.inventory.AppContainer
import org.sattl.inventory.session.SessionUser
import org.sattl.inventory.ui.inventory.InventoryScreen
import org.sattl.inventory.ui.inventory.InventoryViewModel
import org.sattl.inventory.ui.item.ItemDetailScreen
import org.sattl.inventory.ui.item.ItemDetailViewModel
import org.sattl.inventory.ui.item.ItemFormScreen
import org.sattl.inventory.ui.item.ItemFormViewModel
import org.sattl.inventory.ui.login.LoginScreen
import org.sattl.inventory.ui.login.LoginViewModel
import org.sattl.inventory.ui.pin.ChangePinScreen
import org.sattl.inventory.ui.pin.ChangePinViewModel
import org.sattl.inventory.ui.setup.SetupScreen
import org.sattl.inventory.ui.setup.SetupViewModel

/** Screen routes. */
object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val CHANGE_PIN = "change_pin"
    const val INVENTORY = "inventory"
    const val ITEM = "item/{itemId}"
    const val ITEM_FORM = "item_form?itemId={itemId}"

    fun item(id: Long) = "item/$id"

    /** Add a new item when [id] is null, otherwise edit that item. */
    fun itemForm(id: Long?) = "item_form?itemId=${id ?: NO_ID}"

    const val NO_ID = -1L

    /** Screens that can be shown without anyone logged in. */
    val PUBLIC = setOf(SETUP, LOGIN)
}

/**
 * All screens and how to move between them (single activity, Navigation Compose).
 *
 * Whenever the session ends (Log out, idle timeout, or after checkout/check-in), the back
 * stack is cleared and Login is shown, so nobody can press Back into someone else's session.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    startDestination: String,
    user: SessionUser?,
    navController: NavHostController = rememberNavController(),
) {
    val session = container.sessionManager

    // Every screen except Setup and Login requires a logged-in user (spec section 5).
    LaunchedEffect(user) {
        val route = navController.currentDestination?.route
        if (user == null && route != null && route !in Routes.PUBLIC) {
            navController.navigateClearingStack(Routes.LOGIN)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            val vm: SetupViewModel = viewModel(factory = viewModelFactory {
                initializer { SetupViewModel(container.authRepository) }
            })
            SetupScreen(vm, onFinished = { navController.navigateClearingStack(Routes.LOGIN) })
        }

        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(factory = viewModelFactory {
                initializer { LoginViewModel(container.authRepository, session) }
            })
            LoginScreen(vm, onLoggedIn = { mustChangePin ->
                navController.navigateClearingStack(if (mustChangePin) Routes.CHANGE_PIN else Routes.INVENTORY)
            })
        }

        composable(Routes.CHANGE_PIN) {
            val current = user ?: return@composable
            val vm: ChangePinViewModel = viewModel(factory = viewModelFactory {
                initializer { ChangePinViewModel(container.authRepository, current.id) }
            })
            ChangePinScreen(
                viewModel = vm,
                user = current,
                onLogout = session::logout,
                onDone = {
                    // After a forced change there is nothing to go back to, so go to Inventory.
                    if (!navController.popBackStack()) navController.navigateClearingStack(Routes.INVENTORY)
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Routes.INVENTORY) {
            val current = user ?: return@composable
            val vm: InventoryViewModel = viewModel(factory = viewModelFactory {
                initializer { InventoryViewModel(container.itemRepository, current) }
            })
            InventoryScreen(
                viewModel = vm,
                user = current,
                onLogout = session::logout,
                onChangePin = { navController.navigate(Routes.CHANGE_PIN) },
                onOpenItem = { navController.navigate(Routes.item(it)) },
                onAddItem = { navController.navigate(Routes.itemForm(null)) },
            )
        }

        composable(
            Routes.ITEM,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val current = user ?: return@composable
            val itemId = entry.arguments!!.getLong("itemId")
            val vm: ItemDetailViewModel = viewModel(factory = viewModelFactory {
                initializer { ItemDetailViewModel(container.itemRepository, current, itemId) }
            })
            ItemDetailScreen(
                viewModel = vm,
                user = current,
                onLogout = session::logout,
                onChangePin = { navController.navigate(Routes.CHANGE_PIN) },
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.itemForm(itemId)) },
            )
        }

        composable(
            Routes.ITEM_FORM,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType; defaultValue = Routes.NO_ID }),
        ) { entry ->
            val current = user ?: return@composable
            // Admin only (spec section 3). The UI never offers this route to users.
            if (!current.isAdmin) return@composable
            val itemId = entry.arguments!!.getLong("itemId").takeIf { it != Routes.NO_ID }
            val vm: ItemFormViewModel = viewModel(factory = viewModelFactory {
                initializer { ItemFormViewModel(container.itemRepository, current, itemId) }
            })
            ItemFormScreen(
                viewModel = vm,
                user = current,
                onLogout = session::logout,
                onSaved = { savedId ->
                    if (itemId == null) {
                        // New item: show its detail page in place of the form.
                        navController.navigate(Routes.item(savedId)) {
                            popUpTo(Routes.ITEM_FORM) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}

/** Navigates to [route] and removes everything else from the back stack. */
private fun NavHostController.navigateClearingStack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
