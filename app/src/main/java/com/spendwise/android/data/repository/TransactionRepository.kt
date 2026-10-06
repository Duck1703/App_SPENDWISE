package com.spendwise.android.data.repository

import com.spendwise.android.core.common.ApplicationScope
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionCache
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CreateTransactionRequestDto
import com.spendwise.android.data.remote.TransactionDto
import com.spendwise.android.data.remote.TransactionsService
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transactions boundary (AD-016, PRODUCT §19/§20, BACKEND-CONTRACT §11).
 *
 * The ledger is append-only and read-only after creation: only
 * `GET /api/transactions` (+ the three real filter params) and
 * `POST /api/transactions` exist. There is no `GET /{id}`, so
 * [findById] resolves a row from the last fetched list, or from one
 * unfiltered fetch on a miss — detail renders exactly the list fields.
 *
 * Session safety: every list result is held in a [SessionCache] stamped
 * with the generation that produced it, and every publish path
 * re-validates the generation before returning data to a caller — a
 * response from session N can never populate session N+1's cache or UI
 * (EXT-MAJOR-1 discipline). The cache backs detail lookups only;
 * [list] always fetches — the ledger can change server-side at any
 * time (recurring generation, imports) and never serves stale rows.
 *
 * Mutations: a successful [create] invalidates this cache AND the
 * account cache (the server applies the amount to the account balance
 * inside the same DB transaction — INV-012 keeps balances server-
 * authoritative; the client never recomputes them), then emits
 * [mutations] so any live list ViewModel reloads.
 */
interface TransactionRepository {

    /**
     * `GET /api/transactions` with the three real server filters. Always
     * network-fresh; the response also replaces the detail-lookup cache.
     * `accountId`/`type`/`category` are omitted from the query when null.
     */
    suspend fun list(
        accountId: String? = null,
        type: TransactionType? = null,
        category: String? = null,
    ): List<Transaction>

    /**
     * Resolve a transaction from the last fetched list, or one
     * unfiltered fetch on a miss. No `GET /{id}` exists on the backend.
     * Absent after the fetch → [AppError.NotFound].
     */
    suspend fun findById(id: String): Transaction

    /**
     * `POST /api/transactions`. [type] must be [TransactionType.INCOME]
     * or [TransactionType.EXPENSE] — transfer types are server-rejected
     * and the client never offers them. [category] and [hint] are
     * optional; [hint] maps to the wire `description` field, which the
     * backend uses only for rule matching — it is never persisted as
     * transaction text. [occurredOn] is the user-selected economic date
     * (M11) — sent explicitly as ISO `yyyy-MM-dd`; the server may still
     * apply its sane future-date bound (400 surfaces honestly).
     *
     * On success: transaction + account caches invalidated, [mutations]
     * emitted.
     */
    suspend fun create(
        accountId: String,
        type: TransactionType,
        amount: Money,
        occurredOn: LocalDate,
        category: String? = null,
        hint: String? = null,
    ): Transaction

    /** Emitted once per successful [create] — list surfaces reload. */
    val mutations: SharedFlow<Unit>

    /**
     * Emit a ledger-changed signal for a server-side write that did NOT
     * go through [create] (M5: statement import). Same contract: list
     * surfaces (Home activity, Transactions) reload.
     */
    suspend fun notifyLedgerChanged()
}

@Singleton
class RemoteTransactionRepository @Inject constructor(
    private val service: TransactionsService,
    private val accountRepository: AccountRepository,
    sessionManager: SessionManager,
    @ApplicationScope appScope: CoroutineScope,
) : TransactionRepository {

    private val cache = SessionCache<List<Transaction>>(sessionManager)

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    init {
        // Prompt cleanup only — the generation stamp already guarantees
        // isolation even if this collector is never scheduled.
        appScope.launch {
            sessionManager.cacheInvalidations.collect { cache.invalidate() }
        }
    }

    override suspend fun list(
        accountId: String?,
        type: TransactionType?,
        category: String?,
    ): List<Transaction> {
        val generation = cache.generation()
        val transactions = fetchTransactions(accountId, type, category)
        if (!cache.write(generation, transactions)) throw SessionEndedException()
        return transactions
    }

    override suspend fun findById(id: String): Transaction {
        val generation = cache.generation()
        cache.read()?.firstOrNull { it.id == id }?.let { return it }
        // No GET /{id} exists — one unfiltered fetch, then look up.
        val all = fetchTransactions(accountId = null, type = null, category = null)
        if (!cache.write(generation, all)) throw SessionEndedException()
        return all.firstOrNull { it.id == id }
            ?: throw AppErrorException(AppError.NotFound)
    }

    override suspend fun create(
        accountId: String,
        type: TransactionType,
        amount: Money,
        occurredOn: LocalDate,
        category: String?,
        hint: String?,
    ): Transaction {
        // The backend rejects TRANSFER_* with a plain 400; the client
        // never sends them — this guard keeps a domain defect local
        // instead of writing an impossible ledger intent over the wire.
        require(type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            "manual transactions are INCOME or EXPENSE"
        }
        val generation = cache.generation()
        val transaction = try {
            service.create(
                CreateTransactionRequestDto(
                    accountId = accountId,
                    type = type.name,
                    amount = amount.amount,
                    currency = amount.currency.code,
                    category = category,
                    description = hint?.takeIf { it.isNotBlank() },
                    // ISO yyyy-MM-dd — LocalDate.toString() is exactly the
                    // wire format; no formatter needed.
                    occurredOn = occurredOn.toString(),
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

        // The server mutated the account balance in the same DB
        // transaction — both caches are stale. No local arithmetic on
        // canonical balances (INV-012). The generation check follows so
        // a dead-session write can never publish into a new session.
        cache.invalidate()
        accountRepository.invalidate()
        if (cache.generation() != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return transaction
    }

    override suspend fun notifyLedgerChanged() {
        _mutations.tryEmit(Unit)
    }

    private suspend fun fetchTransactions(
        accountId: String?,
        type: TransactionType?,
        category: String?,
    ): List<Transaction> =
        try {
            // Only real server params ever go on the wire — "All" is the
            // absence of a parameter, never a transmitted sentinel.
            service.list(accountId = accountId, type = type?.name, category = category)
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }.map { it.toDomain() }

    private fun TransactionDto.toDomain(): Transaction =
        try {
            Transaction(
                id = id,
                accountId = accountId,
                type = TransactionType.fromWire(type),
                amount = Money(amount, CurrencyCode(currency)),
                category = category,
                sourceRef = sourceRef,
                // ISO yyyy-MM-dd — strict parse; a malformed server date
                // fails as Unknown instead of degrading into a fake date.
                occurredOn = LocalDate.parse(occurredOn),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }
}
