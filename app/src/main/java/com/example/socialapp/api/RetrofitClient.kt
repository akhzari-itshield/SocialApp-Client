package com.example.socialapp.api

import android.content.Context
import com.example.socialapp.data.SessionManager
import com.example.socialapp.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var api: SocialApi? = null

    fun init(context: Context) {
        if (api != null) return
        val session = SessionManager(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                session.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .build()

        api = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SocialApi::class.java)
    }

    fun get(): SocialApi = api ?: error("RetrofitClient.init() not called")
}