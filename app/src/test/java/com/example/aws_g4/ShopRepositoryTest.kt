package com.example.aws_g4

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRepositoryTest {

    private val repo = ShopRepository(FakeShopApi())

    @Test
    fun items_returns_success_with_nine_items() = runTest {
        val result = repo.items()
        assertTrue(result.isSuccess)
        assertEquals(9, result.getOrNull()?.size)
    }

    @Test
    fun login_stores_token_in_session() = runTest {
        SessionStore.token = null
        val result = repo.login("cafe", "1234")
        assertTrue(result.isSuccess)
        assertEquals("fake-token", SessionStore.token)
    }

    @Test
    fun logout_clears_token() = runTest {
        repo.login("cafe", "1234")
        repo.logout()
        assertNull(SessionStore.token)
    }

    @Test
    fun login_with_wrong_password_returns_failure() = runTest {
        assertTrue(repo.login("cafe", "wrong").isFailure)
    }

    @Test
    fun placeOrder_returns_orderId() = runTest {
        val id = "55555555-5555-4555-8555-555555555555"
        val result = repo.placeOrder(
            orderId = id,
            cart = mapOf(1 to 2, 3 to 1),
            simulate = SimulateMode.NONE
        )
        assertTrue(result.isSuccess)
        assertEquals(id, result.getOrNull())
    }

    @Test
    fun placeOrder_with_empty_cart_returns_failure() = runTest {
        val result = repo.placeOrder(
            orderId = "66666666-6666-4666-8666-666666666666",
            cart = emptyMap(),
            simulate = SimulateMode.NONE
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun orders_returns_seeded_history() = runTest {
        val result = repo.orders()
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }
}