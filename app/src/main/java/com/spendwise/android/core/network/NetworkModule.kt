package com.spendwise.android.core.network

import com.spendwise.android.BuildConfig
import com.spendwise.android.core.serialization.BigDecimalSerializer
import com.spendwise.android.data.remote.AccountsService
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.CategoriesService
import com.spendwise.android.data.remote.DashboardService
import com.spendwise.android.data.remote.ImportsService
import com.spendwise.android.data.remote.TransactionsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * HTTP plumbing (AD-005/AD-006/AD-020). M0 wires the client + converter;
 * feature service interfaces are added per milestone.
 *
 * Logging policy: OkHttp headers-level logging exists **only** in debug
 * builds, with Authorization and Cookie redacted, never bodies (INV).
 *
 * Retry policy: `retryOnConnectionFailure(false)` — OkHttp's transparent
 * transport retry could re-transmit a repeatable POST body on a recoverable
 * connection failure, silently duplicating a financial write the app layer
 * can never detect. No automatic retry at any layer; every failure path
 * surfaces honestly instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(BigDecimal::class, BigDecimalSerializer)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        errorInterceptor: ErrorInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }
                            .apply {
                                level = HttpLoggingInterceptor.Level.HEADERS
                                redactHeader("Authorization")
                                redactHeader("Cookie")
                                redactHeader("Set-Cookie")
                            },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.SPENDWISE_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService = retrofit.create(AuthService::class.java)

    @Provides
    @Singleton
    fun provideAccountsService(retrofit: Retrofit): AccountsService = retrofit.create(AccountsService::class.java)

    @Provides
    @Singleton
    fun provideTransactionsService(retrofit: Retrofit): TransactionsService =
        retrofit.create(TransactionsService::class.java)

    @Provides
    @Singleton
    fun provideCategoriesService(retrofit: Retrofit): CategoriesService =
        retrofit.create(CategoriesService::class.java)

    @Provides
    @Singleton
    fun provideDashboardService(retrofit: Retrofit): DashboardService =
        retrofit.create(DashboardService::class.java)

    @Provides
    @Singleton
    fun provideImportsService(retrofit: Retrofit): ImportsService =
        retrofit.create(ImportsService::class.java)
}
