package com.example.aws_g4

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory fake backend for UI work while the real AWS backend does not exist yet.
 * Enabled by USE_FAKE_API in ShopClient (ShopApi.kt). Data resets when the app process restarts.
 *
 * Things you can try:
 *  - Login: any username/password works. Use the password "wrong" to see the 401 error state.
 *  - Items: 9 items. #8 has a very long name, #9 has a broken image URL (to check edge cases).
 *  - Orders: the status advances over time, driven by the developer option in the cart:
 *      Process normally   -> Received -> Processing -> Done            (about 3s)  -> confirmation screen
 *      Temporary failure  -> Processing attempts 1, 2, 3 -> Done       (about 10s) -> confirmation screen
 *      Permanent failure  -> Processing attempts 1, 2, 3 -> Failed     (about 10s) -> error popup
 */
class FakeShopApi : ShopApi {

    private class FakeOrder(
        val orderId: String,
        val createdAtMs: Long,
        val lines: List<OrderLine>,
        val mode: String?                  // null / "TRANSIENT" / "POISON"
    ) {
        val total: Double get() = lines.sumOf { it.unitPrice * it.quantity }
    }

    private val items = listOf(
        Item(1, "Wireless Headphones", "https://picsum.photos/seed/headphones/400/400", 59.90),
        Item(2, "Mechanical Keyboard", "https://picsum.photos/seed/keyboard/400/400", 89.00),
        Item(3, "USB-C Hub", "https://picsum.photos/seed/usbhub/400/400", 34.50),
        Item(4, "Desk Lamp", "https://picsum.photos/seed/desklamp/400/400", 24.99),
        Item(5, "Water Bottle", "https://picsum.photos/seed/bottle/400/400", 15.00),
        Item(6, "Laptop Stand", "https://picsum.photos/seed/laptopstand/400/400", 42.00),
        Item(7, "Notebook Set", "https://picsum.photos/seed/notebook/400/400", 12.50),
        Item(8, "Ergonomic Wireless Vertical Mouse with Adjustable DPI and Silent Clicks", "https://picsum.photos/seed/mouse/400/400", 29.90),
        Item(9, "Item with broken image (test)", "https://example.invalid/missing.png", 9.99)
    )

    private val orders = mutableMapOf<String, FakeOrder>()
    private val lock = Any()

    // ---------------- ShopApi ----------------

    override suspend fun login(body: LoginRequest): Session {
        delay(NETWORK_DELAY_MS)
        if (body.password == "wrong") throw httpError(401)
        return Session(customerId = 1L, username = body.username, token = "fake-token")
    }

    override suspend fun getItems(): List<Item> {
        delay(NETWORK_DELAY_MS)
        return items
    }

    override suspend fun placeOrder(body: PlaceOrderRequest): PlaceOrderResponse {
        delay(NETWORK_DELAY_MS)
        val invalid = body.lines.isEmpty() ||
                body.lines.any { l -> l.quantity <= 0 || items.none { it.id == l.itemId } }
        if (invalid) throw httpError(400)

        synchronized(lock) {
            // Idempotent: the same orderId never creates a second order.
            if (!orders.containsKey(body.orderId)) {
                orders[body.orderId] = FakeOrder(
                    orderId = body.orderId,
                    createdAtMs = System.currentTimeMillis(),
                    lines = body.lines.map { l ->
                        val item = items.first { it.id == l.itemId }
                        OrderLine(item.id, item.name, l.quantity, item.price)
                    },
                    mode = body.simulate
                )
            }
        }
        return PlaceOrderResponse(body.orderId)
    }

    override suspend fun getOrder(id: String): OrderSummary {
        delay(NETWORK_DELAY_MS / 2)
        val order = synchronized(lock) { orders[id] } ?: throw httpError(404)
        val (status, attempts) = snapshot(order)
        return OrderSummary(
            orderId = order.orderId,
            status = status,
            attempts = attempts,
            createdAt = formatTime(order.createdAtMs),
            total = order.total,
            lines = order.lines
        )
    }

    // ---------------- Internals ----------------

    /** Computes an order's status from elapsed time, mimicking SQS retries and the DLQ. */
    private fun snapshot(o: FakeOrder): Pair<String, Int> {
        val t = System.currentTimeMillis() - o.createdAtMs
        return when (o.mode) {
            "TRANSIENT", "POISON" -> when {
                t < 1_000 -> "RECEIVED" to 0
                t < 4_000 -> "PROCESSING" to 1
                t < 7_000 -> "PROCESSING" to 2
                t < 10_000 -> "PROCESSING" to 3
                else -> (if (o.mode == "POISON") "FAILED" else "DONE") to 3
            }
            else -> when {
                t < 1_000 -> "RECEIVED" to 0
                t < 3_000 -> "PROCESSING" to 1
                else -> "DONE" to 1
            }
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(ms))

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType()))
    )

    private companion object {
        const val NETWORK_DELAY_MS = 600L
    }
}