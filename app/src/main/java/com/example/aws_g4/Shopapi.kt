package com.example.aws_g4

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): PlaceOrderResponse

    /** Status of one order. The server must only return orders owned by the caller. */
    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: String): OrderSummary
}

object ShopClient {
    // true  = use the in-memory FakeShopApi (no backend needed, for UI work)
    // false = call the real API Gateway at BASE_URL
    private val USE_FAKE_API = true

    // TODO: replace with the real URL after deploying API Gateway (the trailing / is required)
    private const val BASE_URL = "https://YOUR_API_ID.execute-api.eu-north-1.amazonaws.com/prod/"

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