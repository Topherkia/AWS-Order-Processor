package com.example.aws_g4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShopApp()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("shop", "Shop", Icons.Default.Home),
    Tab("cart", "Cart", Icons.Default.ShoppingCart)
)

private const val ROUTE_CONFIRMATION = "confirmation"

@Composable
fun ShopApp(vm: ShopViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    if (state.session == null) {
        LoginScreen(state = state, onLogin = vm::login)
    } else {
        MainScaffold(state = state, vm = vm)
    }
}

@Composable
private fun MainScaffold(state: ShopUiState, vm: ShopViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // When the order succeeds, go to the confirmation screen.
    LaunchedEffect(state.checkout) {
        if (state.checkout is CheckoutState.Success) {
            nav.navigate(ROUTE_CONFIRMATION) { launchSingleTop = true }
        }
    }

    // "Processing your order" popup and the "Something has gone wrong" popup.
    CheckoutDialogs(
        checkout = state.checkout,
        onRetry = vm::placeOrder,
        onClose = vm::dismissCheckout
    )

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val cartCount = state.cart.values.sum()

    val continueShopping = {
        vm.dismissCheckout()
        nav.popBackStack("shop", inclusive = false)
        Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute != ROUTE_CONFIRMATION) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val badge = if (tab.route == "cart") cartCount else 0
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "shop",
            modifier = Modifier.padding(padding)
        ) {
            composable("shop") {
                ShopScreen(
                    state = state,
                    onAdd = vm::addToCart,
                    onReload = vm::loadItems,
                    onLogout = vm::logout
                )
            }
            composable("cart") {
                CartScreen(
                    state = state,
                    onAdd = vm::addToCart,
                    onRemove = vm::removeFromCart,
                    onSimulate = vm::setSimulate,
                    onOrder = vm::placeOrder
                )
            }
            composable(ROUTE_CONFIRMATION) {
                BackHandler { continueShopping() }
                val order = (state.checkout as? CheckoutState.Success)?.order
                if (order != null) {
                    ConfirmationScreen(order = order, onContinue = { continueShopping() })
                }
            }
        }
    }
}