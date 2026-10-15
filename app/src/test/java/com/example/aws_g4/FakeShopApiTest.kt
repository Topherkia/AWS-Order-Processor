package com.example.aws_g4

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class FakeShopApiTest {

    @Test
    fun login_succeeds_with_any_password() = runTest {
        val api = FakeShopApi()
        val session = api.login(LoginRequest("cafe", "1234"))
        assertEquals("cafe", session.username)
        assertEquals("fake-token", session.token)
    }

    @Test(expected = HttpException::class)
    fun login_throws_401_with_wrong_password() = runTest {
        FakeShopApi().login(LoginRequest("cafe", "wrong"))
    }

    @Test
    fun getItems_returns_nine_items() = runTest {
        assertEquals(9, FakeShopApi().getItems().size)
    }

    @Test
    fun getItems_has_a_broken_image_for_edge_case() = runTest {
        val items = FakeShopApi().getItems()
        assertTrue(items.any { it.imageUrl.startsWith("https://example.invalid") })
    }

    @Test
    fun placeOrder_is_idempotent() = runTest {
        val api = FakeShopApi()
        val req = PlaceOrderRequest(
            orderId = "11111111-1111-4111-8111-111111111111",
            lines = listOf(OrderLineRequest(1, 1)),
            simulate = null
        )
        api.placeOrder(req)
        api.placeOrder(req)

        val orders = api.getOrders()
        assertEquals(1, orders.count { it.orderId == req.orderId })
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_empty_lines() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "22222222-2222-4222-8222-222222222222",
                lines = emptyList(),
                simulate = null
            )
        )
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_unknown_item() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "33333333-3333-4333-8333-333333333333",
                lines = listOf(OrderLineRequest(99999, 1)),
                simulate = null
            )
        )
    }

    @Test
    fun order_history_has_three_seeded_orders() = runTest {
        assertEquals(3, FakeShopApi().getOrders().size)
    }

    @Test
    fun seeded_history_contains_a_failed_order() = runTest {
        val orders = FakeShopApi().getOrders()
        assertTrue(orders.any { it.status == "FAILED" })
    }
}