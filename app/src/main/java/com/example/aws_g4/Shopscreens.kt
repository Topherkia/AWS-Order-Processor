package com.example.aws_g4

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

private const val CURRENCY = "€"   // change if needed

// TODO: replace with the real support contact shown in the error popup
const val SUPPORT_CONTACT = "support@example.com"

fun formatPrice(value: Double): String = String.format(Locale.US, "%s%.2f", CURRENCY, value)

// =====================================================
// Login
// =====================================================
@Composable
fun LoginScreen(state: ShopUiState, onLogin: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AWS Shop", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        state.loginError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(username, password) },
            enabled = !state.loggingIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loggingIn) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }
    }
}

// =====================================================
// Item list
// =====================================================
@Composable
fun ShopScreen(
    state: ShopUiState,
    onAdd: (Int) -> Unit,
    onReload: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Shop", style = MaterialTheme.typography.headlineSmall)
                Text("Hello, ${state.session?.username}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onLogout) { Text("Sign out") }
        }

        when {
            state.itemsLoading && state.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.items.isEmpty() ->
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Could not load items")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReload) { Text("Try again") }
                }

            else ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(state.items, key = { it.id }) { item ->
                        ItemCard(item, state.cart[item.id] ?: 0, onAdd)
                    }
                }
        }
    }
}

@Composable
private fun ItemCard(item: Item, inCart: Int, onAdd: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatPrice(item.price), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onAdd(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (inCart > 0) "Add to cart ($inCart)" else "Add to cart")
                }
            }
        }
    }
}

// =====================================================
// Cart + checkout
// =====================================================
@Composable
fun CartScreen(
    state: ShopUiState,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSimulate: (SimulateMode) -> Unit,
    onOrder: () -> Unit
) {
    val lines = state.cart.mapNotNull { (id, qty) ->
        state.items.find { it.id == id }?.let { it to qty }
    }
    val total = lines.sumOf { (item, qty) -> item.price * qty }
    val processing = state.checkout is CheckoutState.Processing

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cart", style = MaterialTheme.typography.headlineSmall)

        Box(Modifier.weight(1f)) {
            if (lines.isEmpty()) {
                Text("Your cart is empty")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lines, key = { it.first.id }) { (item, qty) ->
                        CartRow(item, qty, onAdd, onRemove)
                    }
                }
            }
        }

        // Remove this panel from production builds.
        SimulationPanel(state.simulate, onSimulate)

        Button(
            onClick = onOrder,
            enabled = lines.isNotEmpty() && !processing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Place order · ${formatPrice(total)}")
        }
    }
}

@Composable
private fun CartRow(item: Item, qty: Int, onAdd: (Int) -> Unit, onRemove: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatPrice(item.price)} × $qty = ${formatPrice(item.price * qty)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = { onRemove(item.id) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp)
            ) { Text("−") }
            Text("$qty", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { onAdd(item.id) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp)
            ) { Text("+") }
        }
    }
}

@Composable
private fun SimulationPanel(selected: SimulateMode, onSelect: (SimulateMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Developer options · Failure simulation", style = MaterialTheme.typography.labelLarge)
            SimulateMode.values().forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// =====================================================
// Checkout popups: "processing" and "something has gone wrong"
// =====================================================
@Composable
fun CheckoutDialogs(
    checkout: CheckoutState,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    when (checkout) {
        is CheckoutState.Processing -> AlertDialog(
            onDismissRequest = {},          // cannot be dismissed while the order is in flight
            confirmButton = {},
            title = { Text("Processing your order") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    Text("Please wait a moment.")
                }
            }
        )

        is CheckoutState.Failed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Something has gone wrong") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    checkout.hint?.let { Text(it) }
                    Text("Please try again. If this issue persists, please contact $SUPPORT_CONTACT.")
                }
            },
            confirmButton = { Button(onClick = onRetry) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
        )

        else -> Unit
    }
}

// =====================================================
// Order confirmation (shown after the order succeeds)
// =====================================================
@Composable
fun ConfirmationScreen(order: OrderSummary, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Text(
            "Your order has been placed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            "Thank you! You'll receive an order confirmation shortly.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text("Order #${order.orderId.take(8)}", style = MaterialTheme.typography.titleSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${line.name} ×${line.quantity}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatPrice(line.unitPrice * line.quantity))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(formatPrice(order.total), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue shopping")
        }
    }
}