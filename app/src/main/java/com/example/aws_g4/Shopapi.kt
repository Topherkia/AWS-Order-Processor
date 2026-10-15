package com.example.aws_g4

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** Keeps the login token in memory only (demo). In production use EncryptedSharedPreferences or similar. */
object SessionStore {
    @Volatile
    var token: String? = null
}



interface ShopApi {
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Session

    @GET("items")
    suspend fun getItems(): List<Item>

    /** Returns 202 as soon as the order is queued. Processing happens asynchronously (SQS -> Lambda). */
    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): PlaceOrderResponse

    /** Order history of the signed-in customer. The server must only return the caller's own orders. */
    @GET("orders")
    suspend fun getOrders(): List<OrderSummary>
}

object ShopClient {

    private const val REAL_LOGIN = false
    private const val REAL_ITEMS = false
    private const val REAL_ORDER_HISTORY = false
    private const val BASE_URL = "too lazy to setup env config rn"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            SessionStore.token?.let { builder.addHeader("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .build()

    val api: ShopApi by lazy {
        val real = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ShopApi::class.java)
        val fake = FakeShopApi()

        object : ShopApi {
            override suspend fun login(body: LoginRequest) =
                if (REAL_LOGIN) real.login(body) else fake.login(body)

            override suspend fun getItems() =
                if (REAL_ITEMS) real.getItems() else fake.getItems()

            override suspend fun placeOrder(body: PlaceOrderRequest) = real.placeOrder(body)

            override suspend fun getOrders() =
                if (REAL_ORDER_HISTORY) real.getOrders() else fake.getOrders()
        }
    }
}