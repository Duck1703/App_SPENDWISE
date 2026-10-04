package com.spendwise.android.data

import com.spendwise.android.data.repository.AuthRepository
import com.spendwise.android.data.repository.RemoteAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository bindings — interfaces in, remote impls wired here (AD-016). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: RemoteAuthRepository): AuthRepository
}
