package com.spendwise.android.data.repository

import com.spendwise.android.core.model.Budget
import com.spendwise.android.core.model.BudgetCalculation
import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.BudgetCalculationDto
import com.spendwise.android.data.remote.BudgetDto
import com.spendwise.android.data.remote.BudgetsService
import com.spendwise.android.data.remote.CreateBudgetRequestDto
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budgets boundary (PRODUCT §30 S18–S20, BACKEND-CONTRACT — verified at
 * backend `8194daa`).
 *
 * Budgets are create/read-only — the backend offers no update or delete.
 * [calculation] is the sole source of spent/remaining/percentage/status;
 * all four are derived server-side from the ledger and rendered
 * verbatim — the client never aggregates or recomputes (INV-012).
 *
 * Session safety: every call captures [SessionManager.sessionGeneration]
 * before the request and re-validates after the response — a result
 * produced under generation N can never surface in generation N+1
 * (EXT-MAJOR-1 discipline). A successful [create] emits [mutations] so
 * list surfaces reload.
 */
interface BudgetRepository {

    /** `GET /api/budgets` — always network-fresh. */
    suspend fun list(): List<Budget>

    /**
     * `GET /api/budgets/{id}/calculation` — the server-derived spend
     * figures for one budget. Foreign/absent id → [AppError.NotFound].
     */
    suspend fun calculation(id: String): BudgetCalculation

    /**
     * `POST /api/budgets`. Duplicate (category, period) →
     * [AppError.Conflict]; unknown category/malformed period → 400;
     * `fields{}` validation → [AppError.Validation]. On success emits
     * [mutations].
     */
    suspend fun create(category: String, period: String, limit: Money): Budget

    /** Emitted once per successful [create] — list surfaces reload. */
    val mutations: SharedFlow<Unit>
}

@Singleton
class RemoteBudgetRepository @Inject constructor(
    private val service: BudgetsService,
    private val sessionManager: SessionManager,
) : BudgetRepository {

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()


    override suspend fun list(): List<Budget> {
        val generation = sessionManager.sessionGeneration
        val budgets = fetch { service.list() }.map { it.toDomain() }
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return budgets
    }

    override suspend fun calculation(id: String): BudgetCalculation {
        val generation = sessionManager.sessionGeneration
        val calculation = fetch { service.calculation(id) }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return calculation
    }

    override suspend fun create(category: String, period: String, limit: Money): Budget {
        val generation = sessionManager.sessionGeneration
        val budget = fetch {
            service.create(
                CreateBudgetRequestDto(
                    category = category,
                    period = period,
                    limit = limit.amount,
                    currency = limit.currency.code,
                ),
            )
        }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return budget
    }

    private suspend fun <T> fetch(call: suspend () -> T): T =
        try {
            call()
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }

    private fun BudgetDto.toDomain(): Budget =
        try {
            Budget(
                id = id,
                category = category,
                period = period,
                limit = Money(limit, CurrencyCode(currency)),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }

    private fun BudgetCalculationDto.toDomain(): BudgetCalculation =
        try {
            BudgetCalculation(
                budgetId = budgetId,
                category = category ?: "",
                period = period ?: "",
                limit = Money(limit, CurrencyCode(currency)),
                spent = Money(spent, CurrencyCode(currency)),
                remaining = Money(remaining, CurrencyCode(currency)),
                percentage = percentage,
                status = BudgetStatus.fromWire(status),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }
}
