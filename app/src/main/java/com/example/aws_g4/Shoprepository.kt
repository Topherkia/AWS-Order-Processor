package com.example.aws_g4

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class ShopRepository(private val api: ShopApi = ShopClient.api) {

    suspend fun login(username: String, password: String): Result<Session> =
        safeCall { api.login(LoginRequest(username, password)) }
            .onSuccess { SessionStore.token = it.token }

    fun logout() {
        SessionStore.token = null
    }

    suspend fun items(): Result<List<Item>> = safeCall { api.getItems() }

    suspend fun placeOrder(
        orderId: String,
        cart: Map<Int, Int>,
        simulate: SimulateMode
    ): Result<String> = safeCall {
        val request = PlaceOrderRequest(
            orderId = orderId,
            lines = cart.map { (itemId, qty) -> OrderLineRequest(itemId, qty) },
            simulate = simulate.wire
        )
        api.placeOrder(request).orderId
    }

    suspend fun order(orderId: String): Result<OrderSummary> = safeCall { api.getOrder(orderId) }

    // Coroutine cancellation is rethrown, never swallowed.
    private suspend inline fun <T> safeCall(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}

/** User-facing error text. Internal server messages are never shown as-is. */
fun friendlyMessage(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        400 -> "The request was not valid."
        401 -> "Authentication failed. Please sign in again."
        429 -> "Too many requests."
        in 500..599 -> "The server is having problems."
        else -> "The request could not be completed (${e.code()})."
    }
    is IOException -> "Please check your network connection."
    else -> "An unknown error occurred."
}