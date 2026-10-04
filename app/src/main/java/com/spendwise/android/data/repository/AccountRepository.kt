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
import com.spendwise.android.core.session.SessionCache
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
 * list cache (AD-013/§19) behind [SessionCache]: every entry is stamped
 * with the session generation that produced it, so one generation's
 * accounts can never be read into — or written over — another
 * generation's session (EXT-MAJOR-1). The async `cacheInvalidations`
 * signal still drops the cache promptly, but the stamp alone already
 * guarantees isolation.
 *
 * A response whose session generation ended while the request was
 * in-flight is discarded via [CancellationException] — the operation's
 * context is gone; its result is never cached and never surfaced.
 */
interface AccountRepository {

    /** Server list, or the live-generation cache when present and [refresh] is false. */
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

    private val cache = SessionCache<List<Account>>(sessionManager)

    init {
        // Prompt cleanup only — isolation is already enforced by the
        // generation stamp even if this collector is never scheduled.
        appScope.launch {
            sessionManager.cacheInvalidations.collect { cache.invalidate() }
        }
    }

    override suspend fun list(refresh: Boolean): List<Account> {
        val generation = cache.generation()
        if (!refresh) cache.read()?.let { return it }
        val accounts = fetchAccounts()
        if (!cache.write(generation, accounts)) throw SessionEndedException()
        return accounts
    }

    override suspend fun findById(id: String, refresh: Boolean): Account {
        val generation = cache.generation()
        if (!refresh) {
            cache.read()?.firstOrNull { it.id == id }?.let { return it }
        }
        val account = fetchAccount(id)
        // Merge into the cache so a later list() keeps this account —
        // atomically and only while the captured generation is still live.
        cache.update(generation) { current ->
            current?.let {
                if (it.any { a -> a.id == account.id }) {
                    it.map { a -> if (a.id == account.id) account else a }
                } else {
                    it + account
                }
            }
        }
        if (cache.generation() != generation) throw SessionEndedException()
        return account
    }

    override suspend fun create(name: String, type: AccountType, openingBalance: Money): Account {
        val generation = cache.generation()
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
        cache.update(generation) { current -> current?.plus(account) }
        if (cache.generation() != generation) throw SessionEndedException()
        return account
    }

    private suspend fun fetchAccounts(): List<Account> =
        try {
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

    private suspend fun fetchAccount(id: String): Account =
        try {
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

/**
 * The session generation captured at operation start is no longer live —
 * the operation's context ended mid-flight. Thrown as a
 * [CancellationException] subtype so callers unwind quietly (ViewModels
 * only surface [AppErrorException]); a stale result can never reach a
 * newer session's UI.
 */
internal class SessionEndedException : CancellationException("session generation ended")
