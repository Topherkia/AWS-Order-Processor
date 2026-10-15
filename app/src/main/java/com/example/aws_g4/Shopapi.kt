package com.example.aws_g4

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ShopApi {
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
    // true  = use the in-memory FakeShopApi (no backend needed, for UI work)
    // false = call the real API Gateway at BASE_URL
    private val USE_FAKE_API = false

    // TODO: replace with the real Invoke URL from API Gateway (the trailing / is required).
    // Stage "$default" -> https://<id>.execute-api.<region>.amazonaws.com/
    // Stage "prod"     -> https://<id>.execute-api.<region>.amazonaws.com/prod/
    private const val BASE_URL = "https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            // MSAL returns a cached token or refreshes it silently, so expiry is handled here.
            AuthManager.currentToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .build()

    val api: ShopApi by lazy {
        if (USE_FAKE_API) {
            FakeShopApi()
        } else {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ShopApi::class.java)
        }
    }
}