package com.spendwise.android.core.network

import com.spendwise.android.BuildConfig
import com.spendwise.android.core.serialization.BigDecimalSerializer
import com.spendwise.android.data.remote.AccountsService
import com.spendwise.android.data.remote.BudgetsService
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.CategoriesService
import com.spendwise.android.data.remote.DashboardService
import com.spendwise.android.data.remote.ImportsService
import com.spendwise.android.data.remote.RecurringService
import com.spendwise.android.data.remote.RulesService
import com.spendwise.android.data.remote.TransactionsService
import com.spendwise.android.data.remote.TransfersService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            // M13: single-flight renewal backstop — retries only idempotent
            // requests once; never replays a transmitted mutation.
            .authenticator(tokenAuthenticator)
            // M21: URL policy fires at the wire boundary — after retrofit
            // builds (fake-backed tests compose real graphs but never dial)
            // and before auth attaches, so no credential is ever sent to a
            // misconfigured endpoint.
            .addInterceptor { chain ->
                // Rejection surfaces as IOException — a transport failure
                // every caller already handles. Throwing unchecked here
                // would escape the async dispatcher and crash the process.
                try {
                    ApiBaseUrl.validate(BuildConfig.SPENDWISE_API_BASE_URL, BuildConfig.DEBUG)
                } catch (e: IllegalArgumentException) {
                    throw java.io.IOException("API base URL rejected: ${e.message}", e)
                }
                chain.proceed(chain.request())
            }
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
            // M21: release validates eagerly — a bad URL can never construct
            // the graph. Debug tolerates any syntactically valid URL here so
            // unconfigured dev/test graphs still launch; every real call is
            // guarded by the OkHttp interceptor above regardless.
            .baseUrl(
                if (BuildConfig.DEBUG &&
                    BuildConfig.SPENDWISE_API_BASE_URL.toHttpUrlOrNull() != null
                ) {
                    BuildConfig.SPENDWISE_API_BASE_URL
                } else {
                    ApiBaseUrl.validate(BuildConfig.SPENDWISE_API_BASE_URL, BuildConfig.DEBUG)
                },
            )
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
    fun provideTransfersService(retrofit: Retrofit): TransfersService =
        retrofit.create(TransfersService::class.java)

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

    @Provides
    @Singleton
    fun provideBudgetsService(retrofit: Retrofit): BudgetsService =
        retrofit.create(BudgetsService::class.java)

    @Provides
    @Singleton
    fun provideRecurringService(retrofit: Retrofit): RecurringService =
        retrofit.create(RecurringService::class.java)

    @Provides
    @Singleton
    fun provideRulesService(retrofit: Retrofit): RulesService =
        retrofit.create(RulesService::class.java)
}
