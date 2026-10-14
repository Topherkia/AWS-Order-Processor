package com.example.aws_g4

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Where the current checkout is. Drives the processing popup, the error popup and the confirmation screen. */
sealed interface CheckoutState {
    object Idle : CheckoutState
    object Processing : CheckoutState
    data class Success(val order: OrderSummary) : CheckoutState

    /** hint: optional extra line, e.g. "Please check your network connection." (null = the order failed in the backend / DLQ) */
    data class Failed(val hint: String? = null) : CheckoutState
}

data class ShopUiState(
    val session: Session? = null,
    val loggingIn: Boolean = false,
    val loginError: String? = null,

    val items: List<Item> = emptyList(),
    val itemsLoading: Boolean = false,

    val cart: Map<Int, Int> = emptyMap(),          // itemId -> quantity
    val simulate: SimulateMode = SimulateMode.NONE,
    val checkout: CheckoutState = CheckoutState.Idle,

    val message: String? = null                    // one-shot notice (snackbar)
)

class ShopViewModel(
    private val repo: ShopRepository = ShopRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state.asStateFlow()

    // Reuse the same orderId when retrying with the same cart, so a retry can never create a duplicate order.
    // It is reset after a confirmed success or failure, and whenever the cart changes.
    private var pendingOrderId: String? = null
    private var checkoutJob: Job? = null

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
                }
                .onFailure { e ->
                    _state.update { it.copy(loggingIn = false, loginError = friendlyMessage(e)) }
                }
        }
    }

    fun logout() {
        checkoutJob?.cancel()
        pendingOrderId = null
        repo.logout()
        _state.value = ShopUiState()
    }

    // ---------- Items ----------
    fun loadItems() {
        viewModelScope.launch {
            _state.update { it.copy(itemsLoading = true) }
            repo.items()
                .onSuccess { list -> _state.update { it.copy(items = list) } }
                .onFailure { e -> postMessage(friendlyMessage(e)) }
            _state.update { it.copy(itemsLoading = false) }
        }
    }

    // ---------- Cart ----------
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

    // ---------- Checkout ----------
    /**
     * Sends the order, then polls its status until the backend finishes:
     *  DONE   -> CheckoutState.Success (the UI navigates to the confirmation screen)
     *  FAILED -> CheckoutState.Failed  (the order ended up in the DLQ; the UI shows the error popup)
     * Also used by the "Try again" button of the error popup.
     */
    fun placeOrder() {
        val st = _state.value
        if (st.checkout is CheckoutState.Processing || st.cart.isEmpty()) return
        val orderId = pendingOrderId ?: UUID.randomUUID().toString().also { pendingOrderId = it }

        checkoutJob?.cancel()
        checkoutJob = viewModelScope.launch {
            setCheckout(CheckoutState.Processing)

            val sendError = repo.placeOrder(orderId, st.cart, st.simulate).exceptionOrNull()
            if (sendError != null) {
                // pendingOrderId is kept, so trying again resubmits the same order.
                setCheckout(CheckoutState.Failed(friendlyMessage(sendError)))
                return@launch
            }

            val deadline = System.currentTimeMillis() + POLL_LIMIT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_INTERVAL_MS)
                // A failed poll (e.g. a brief network drop) is ignored; the next poll tries again.
                val order = repo.order(orderId).getOrNull() ?: continue
                when (order.status) {
                    "DONE" -> {
                        pendingOrderId = null
                        _state.update { it.copy(cart = emptyMap(), checkout = CheckoutState.Success(order)) }
                        return@launch
                    }
                    "FAILED" -> {
                        pendingOrderId = null   // the failed order is final; a retry is a brand-new order
                        setCheckout(CheckoutState.Failed())
                        return@launch
                    }
                }
            }
            // Still processing after the time limit. pendingOrderId is kept, so a retry cannot duplicate the order.
            setCheckout(CheckoutState.Failed("This is taking longer than expected."))
        }
    }

    /** Closes the error popup, or leaves the confirmation screen. */
    fun dismissCheckout() {
        _state.update { it.copy(checkout = CheckoutState.Idle) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---------- Internal ----------
    private fun setCheckout(value: CheckoutState) {
        _state.update { it.copy(checkout = value) }
    }

    private fun postMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_LIMIT_MS = 60_000L
    }
}