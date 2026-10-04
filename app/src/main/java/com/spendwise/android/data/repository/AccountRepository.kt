package com.spendwise.android.data.repository

import com.spendwise.android.core.common.ApplicationScope
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.AccountDto
import com.spendwise.android.data.remote.AccountsService
import com.spendwise.android.data.remote.CreateAccountRequestDto
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accounts boundary (AD-016, PRODUCT §18).
 *
 * `balance` is always the server-stored value — never recomputed
 * client-side (INV-012). The repository keeps a session-scoped in-memory
 * list cache (AD-013/§19): it backs detail lookups and survives
 * same-identity re-authentication, and it is dropped on every
 * `cacheInvalidations` signal — sign-out, expiry, or a different identity —
 * so one user's accounts can never reach another session.
 */
interface AccountRepository {

    /** Server list, or the session cache when present and [refresh] is false. */
    suspend fun list(refresh: Boolean = false): List<Account>

    /** Cache-first unless [refresh]; falls back to `GET /api/accounts/{id}`; 404 → NotFound. */
    suspend fun findById(id: String, refresh: Boolean = false): Account

    suspend fun create(name: String, type: AccountType, openingBalance: Money): Account
}

@Singleton
class RemoteAccountRepository @Inject constructor(
    private val service: AccountsService,
    sessionManager: SessionManager,
    @ApplicationScope appScope: CoroutineScope,
) : AccountRepository {

    @Volatile
    private var cache: List<Account>? = null

    init {
        appScope.launch {
            sessionManager.cacheInvalidations.collect { cache = null }
        }
    }

    override suspend fun list(refresh: Boolean): List<Account> {
        cache?.takeIf { !refresh }?.let { return it }
        val accounts = try {
            service.list()
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }.map { it.toDomain() }
        cache = accounts
        return accounts
    }

    override suspend fun findById(id: String, refresh: Boolean): Account {
        if (!refresh) {
            cache?.firstOrNull { it.id == id }?.let { return it }
        }
        val account = try {
            service.getById(id)
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }.toDomain()
        // Merge into the cache so a later list() keeps this account.
        cache = cache?.let { current ->
            if (current.any { it.id == account.id }) {
                current.map { if (it.id == account.id) account else it }
            } else {
                current + account
            }
        }
        return account
    }

    override suspend fun create(name: String, type: AccountType, openingBalance: Money): Account {
        val account = try {
            service.create(
                CreateAccountRequestDto(
                    name = name,
                    type = type.name,
                    openingBalance = openingBalance.amount,
                    currency = openingBalance.currency.code,
                ),
            )
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }.toDomain()
        cache = cache?.plus(account)
        return account
    }

    private fun AccountDto.toDomain(): Account =
        try {
            Account(
                id = id,
                name = name,
                type = AccountType.fromWire(type),
                openingBalance = Money(openingBalance, CurrencyCode(currency)),
                balance = Money(balance, CurrencyCode(currency)),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }
}
