package com.example.aws_g4

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What the confirmation screen shows. Built from the basket at the moment the backend accepts
 * the order (HTTP 202), so it does not depend on how the asynchronous processing ends.
 */
data class PlacedOrder(
    val orderId: String,
    val lines: List<OrderLine>,
    val total: Double
)

data class ShopUiState(
    val session: Session? = null,
    val loggingIn: Boolean = false,
    val loginError: String? = null,

    val items: List<Item> = emptyList(),
    val itemsLoading: Boolean = false,

    val cart: Map<Int, Int> = emptyMap(),          // itemId -> quantity (cases)
    val simulate: SimulateMode = SimulateMode.NONE,
    val placing: Boolean = false,
    val placedOrder: PlacedOrder? = null,          // non-null = show the confirmation screen

    val orders: List<OrderSummary> = emptyList(),
    val ordersLoaded: Boolean = false,
    val ordersError: Boolean = false,

    val message: String? = null                    // one-shot notice (snackbar)
)

class ShopViewModel(
    private val repo: ShopRepository = ShopRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state.asStateFlow()

    // Reuse the same orderId when retrying with the same basket, so a retry can never create a duplicate order.
    private var pendingOrderId: String? = null

    // ---------- Authentication ----------
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(loginError = "Please enter your username and password") }
            return
        }
        if (_state.value.loggingIn) return
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, loginError = null) }
            repo.login(username.trim(), password)
                .onSuccess { session ->
                    _state.update { it.copy(session = session, loggingIn = false) }
                    loadItems()
                    fetchOrders()
                }
                .onFailure { e ->
                    _state.update { it.copy(loggingIn = false, loginError = friendlyMessage(e)) }
                }
        }
    }

    fun logout() {
        pendingOrderId = null
        repo.logout()
        _state.value = ShopUiState()
    }

    // ---------- Catalog ----------
    fun loadItems() {
        viewModelScope.launch {
            _state.update { it.copy(itemsLoading = true) }
            repo.items()
                .onSuccess { list -> _state.update { it.copy(items = list) } }
                .onFailure { e -> postMessage(friendlyMessage(e)) }
            _state.update { it.copy(itemsLoading = false) }
        }
    }

    // ---------- Basket ----------
    fun addToCart(itemId: Int) {
        pendingOrderId = null
        _state.update { st ->
            st.copy(cart = st.cart + (itemId to ((st.cart[itemId] ?: 0) + 1)))
        }
    }

    fun removeFromCart(itemId: Int) {
        pendingOrderId = null
        _state.update { st ->
            val qty = (st.cart[itemId] ?: 0) - 1
            st.copy(cart = if (qty <= 0) st.cart - itemId else st.cart + (itemId to qty))
        }
    }

    fun setSimulate(mode: SimulateMode) {
        _state.update { it.copy(simulate = mode) }
    }

    // ---------- Placing an order ----------
    /**
     * Sends the order. As soon as the backend accepts it (202) the customer sees the confirmation
     * screen, whatever happens afterwards. If the order later ends up in the DLQ, the customer is not
     * interrupted; the order history shows it as "Under review" while the operations team handles it.
     * Only a failure to SEND the order (network / 4xx / 5xx) is reported, because then it was not accepted.
     */
    fun placeOrder() {
        val st = _state.value
        if (st.placing || st.cart.isEmpty()) return
        val orderId = pendingOrderId ?: UUID.randomUUID().toString().also { pendingOrderId = it }

        // Snapshot for the confirmation screen.
        val lines = st.cart.mapNotNull { (itemId, qty) ->
            st.items.find { it.id == itemId }?.let { OrderLine(it.id, it.name, qty, it.price) }
        }
        val total = lines.sumOf { it.unitPrice * it.quantity }

        viewModelScope.launch {
            _state.update { it.copy(placing = true) }
            repo.placeOrder(orderId, st.cart, st.simulate)
                .onSuccess {
                    pendingOrderId = null
                    _state.update {
                        it.copy(cart = emptyMap(), placedOrder = PlacedOrder(orderId, lines, total))
                    }
                    fetchOrders()
                }
                .onFailure { e ->
                    // pendingOrderId is kept, so submitting again resubmits the same order.
                    postMessage("${friendlyMessage(e)} Your order was not sent. Please try again.")
                }
            _state.update { it.copy(placing = false) }
        }
    }

    /** Leaves the confirmation screen. */
    fun dismissConfirmation() {
        _state.update { it.copy(placedOrder = null) }
    }

    // ---------- Order history ----------
    fun refreshOrders() {
        viewModelScope.launch { fetchOrders() }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---------- Internal ----------
    private suspend fun fetchOrders() {
        repo.orders()
            .onSuccess { list ->
                _state.update { st ->
                    st.copy(
                        orders = list.sortedByDescending { o -> o.createdAt },
                        ordersLoaded = true,
                        ordersError = false
                    )
                }
            }
            .onFailure {
                _state.update { st -> st.copy(ordersError = true) }
            }
    }

    private fun postMessage(text: String) {
        _state.update { it.copy(message = text) }
    }
}