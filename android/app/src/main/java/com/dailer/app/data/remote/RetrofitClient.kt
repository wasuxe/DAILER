package com.dailer.app.data.remote

import com.dailer.app.BuildConfig
import com.dailer.app.util.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Thread-safe singleton providing [ApiService] instances backed by OkHttp and Gson,
 * with support for updating the backend base URL at runtime.
 */
object RetrofitClient {

    @Volatile
    private var currentBaseUrl: String = normalizeBaseUrl(Constants.BASE_URL)

    @Volatile
    private var retrofitInstance: Retrofit? = null

    @Volatile
    private var apiServiceInstance: ApiService? = null

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
    }

    private val gson: Gson by lazy {
        GsonBuilder().create()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Bypass-Tunnel-Remainder", "true")
                    .addHeader("bypass-tunnel-reminder", "true")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService
        get() = apiServiceInstance ?: synchronized(this) {
            apiServiceInstance ?: buildRetrofit(currentBaseUrl)
                .also { retrofitInstance = it }
                .create(ApiService::class.java)
                .also { apiServiceInstance = it }
        }

    fun getBaseUrl(): String = currentBaseUrl

    @Synchronized
    fun setBaseUrl(baseUrl: String) {
        val normalized = normalizeBaseUrl(baseUrl)
        if (normalized != currentBaseUrl) {
            currentBaseUrl = normalized
            val newRetrofit = buildRetrofit(normalized)
            retrofitInstance = newRetrofit
            apiServiceInstance = newRetrofit.create(ApiService::class.java)
        }
    }

    fun createService(baseUrl: String): ApiService {
        return buildRetrofit(normalizeBaseUrl(baseUrl)).create(ApiService::class.java)
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "Base URL must not be blank" }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
