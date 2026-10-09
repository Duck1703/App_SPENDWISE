package com.spendwise.android.core.session

import android.content.Context
import com.spendwise.android.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionDataStore

/** API origin for the bare (interceptor-free) refresh client — injectable
 *  so tests can point [SessionRefresher] at a MockWebServer. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    @SessionDataStore
    fun provideSessionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("spendwise_session") },
        )

    @Provides
    @Singleton
    fun provideTokenStore(impl: DataStoreTokenStore): TokenStore = impl

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.SPENDWISE_API_BASE_URL
}
