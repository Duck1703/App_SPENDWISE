package com.spendwise.android.data

import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.AuthRepository
import com.spendwise.android.data.repository.CategoryRepository
import com.spendwise.android.data.repository.ContentResolverExportStore
import com.spendwise.android.data.repository.ContentResolverStatementStore
import com.spendwise.android.data.repository.DashboardRepository
import com.spendwise.android.data.repository.ExportFileStore
import com.spendwise.android.data.repository.ImportRepository
import com.spendwise.android.data.repository.LedgerExportRepository
import com.spendwise.android.data.repository.BudgetRepository
import com.spendwise.android.data.repository.RecurringRepository
import com.spendwise.android.data.repository.RemoteAccountRepository
import com.spendwise.android.data.repository.RemoteAuthRepository
import com.spendwise.android.data.repository.RemoteBudgetRepository
import com.spendwise.android.data.repository.RemoteCategoryRepository
import com.spendwise.android.data.repository.RemoteDashboardRepository
import com.spendwise.android.data.repository.RemoteImportRepository
import com.spendwise.android.data.repository.RemoteLedgerExportRepository
import com.spendwise.android.data.repository.RemoteRecurringRepository
import com.spendwise.android.data.repository.RemoteRuleRepository
import com.spendwise.android.data.repository.RemoteTransactionRepository
import com.spendwise.android.data.repository.RuleRepository
import com.spendwise.android.data.repository.StatementStore
import com.spendwise.android.data.repository.TransactionRepository
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

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RemoteAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RemoteTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RemoteCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: RemoteDashboardRepository): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindImportRepository(impl: RemoteImportRepository): ImportRepository

    @Binds
    @Singleton
    abstract fun bindStatementStore(impl: ContentResolverStatementStore): StatementStore

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: RemoteBudgetRepository): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRepository(impl: RemoteRecurringRepository): RecurringRepository

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: RemoteRuleRepository): RuleRepository

    @Binds
    @Singleton
    abstract fun bindLedgerExportRepository(
        impl: RemoteLedgerExportRepository,
    ): LedgerExportRepository

    @Binds
    @Singleton
    abstract fun bindExportFileStore(impl: ContentResolverExportStore): ExportFileStore
}
