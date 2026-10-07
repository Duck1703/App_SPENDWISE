package com.spendwise.android.data.repository

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CreateRecurringRequestDto
import com.spendwise.android.data.remote.RecurringService
import com.spendwise.android.data.remote.RecurringTransactionDto
import com.spendwise.android.data.remote.UpdateRecurringRequestDto
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recurring-template boundary (PRODUCT §30 S15–S17, backend `8194daa`).
 *
 * Templates are create/list/get/deactivate — `DELETE /{id}` flips
 * `active` and never touches generated ledger rows; there is no edit
 * or reactivate endpoint, so deactivation is surfaced honestly as
 * permanent-in-place.
 *
 * Session safety: generation captured before every call, re-validated
 * before returning — a stale-generation result can never surface
 * (EXT-MAJOR-1). [create]/[deactivate] emit [mutations] so list
 * surfaces reload; neither touches the ledger.
 */
interface RecurringRepository {

    /** `GET /api/recurring-transactions` — always network-fresh. */
    suspend fun list(): List<RecurringTemplate>

    /** `GET /api/recurring-transactions/{id}`. Foreign/absent → [AppError.NotFound]. */
    suspend fun findById(id: String): RecurringTemplate

    /**
     * `POST /api/recurring-transactions`. Foreign/unknown account or
     * invalid category/frequency/direction → 400; `fields{}` validation
     * → [AppError.Validation]. On success emits [mutations].
     */
    suspend fun create(
        accountId: String,
        direction: TransactionType,
        amount: Money,
        category: String?,
        frequency: RecurringFrequency,
        nextDueDate: LocalDate,
        endDate: LocalDate?,
    ): RecurringTemplate

    /**
     * `PATCH /{id}` — sparse edit of future-generation inputs
     * (REQ-BE-012). Pass only the fields that changed; set
     * [clearCategory]/[clearEndDate] to null a field out (the caller
     * must not also pass a replacement — the server 400s that pair).
     * Account, direction, currency, and active are immutable and are
     * never sent. Emits [mutations] on success.
     */
    suspend fun update(
        id: String,
        amount: Money? = null,
        category: String? = null,
        clearCategory: Boolean = false,
        frequency: RecurringFrequency? = null,
        nextDueDate: LocalDate? = null,
        endDate: LocalDate? = null,
        clearEndDate: Boolean = false,
    ): RecurringTemplate

    /**
     * `DELETE /{id}` — deactivates the template (`active=false`). The
     * server returns the updated template in the 200 body; generated
     * transactions are untouched. Emits [mutations] on success.
     */
    suspend fun deactivate(id: String): RecurringTemplate

    /** Emitted once per successful [create]/[deactivate]. */
    val mutations: SharedFlow<Unit>
}

@Singleton
class RemoteRecurringRepository @Inject constructor(
    private val service: RecurringService,
    private val sessionManager: SessionManager,
) : RecurringRepository {

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()


    override suspend fun list(): List<RecurringTemplate> {
        val generation = sessionManager.sessionGeneration
        val templates = fetch { service.list() }.map { it.toDomain() }
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return templates
    }

    override suspend fun findById(id: String): RecurringTemplate {
        val generation = sessionManager.sessionGeneration
        val template = fetch { service.getById(id) }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return template
    }

    override suspend fun create(
        accountId: String,
        direction: TransactionType,
        amount: Money,
        category: String?,
        frequency: RecurringFrequency,
        nextDueDate: LocalDate,
        endDate: LocalDate?,
    ): RecurringTemplate {
        // The wire enum is a closed pair — a future/armored value never
        // writes a fabricated direction (same discipline as create() in
        // TransactionRepository).
        require(direction == TransactionType.INCOME || direction == TransactionType.EXPENSE) {
            "recurring templates are INCOME or EXPENSE"
        }
        require(frequency == RecurringFrequency.WEEKLY || frequency == RecurringFrequency.MONTHLY) {
            "recurring frequency is WEEKLY or MONTHLY"
        }
        val generation = sessionManager.sessionGeneration
        val template = fetch {
            service.create(
                CreateRecurringRequestDto(
                    accountId = accountId,
                    direction = direction.name,
                    amount = amount.amount,
                    currency = amount.currency.code,
                    category = category,
                    frequency = frequency.name,
                    nextDueDate = nextDueDate.toString(),
                    endDate = endDate?.toString(),
                ),
            )
        }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return template
    }

    override suspend fun update(
        id: String,
        amount: Money?,
        category: String?,
        clearCategory: Boolean,
        frequency: RecurringFrequency?,
        nextDueDate: LocalDate?,
        endDate: LocalDate?,
        clearEndDate: Boolean,
    ): RecurringTemplate {
        // Wire-armored enum values never write a fabricated frequency.
        require(
            frequency == null ||
                frequency == RecurringFrequency.WEEKLY ||
                frequency == RecurringFrequency.MONTHLY,
        ) { "recurring frequency is WEEKLY or MONTHLY" }
        require(!(clearCategory && category != null)) {
            "category and clearCategory are mutually exclusive"
        }
        require(!(clearEndDate && endDate != null)) {
            "endDate and clearEndDate are mutually exclusive"
        }
        val generation = sessionManager.sessionGeneration
        val template = fetch {
            service.update(
                id,
                UpdateRecurringRequestDto(
                    amount = amount?.amount,
                    category = category,
                    clearCategory = clearCategory,
                    frequency = frequency?.name,
                    nextDueDate = nextDueDate?.toString(),
                    endDate = endDate?.toString(),
                    clearEndDate = clearEndDate,
                ),
            )
        }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return template
    }

    override suspend fun deactivate(id: String): RecurringTemplate {
        val generation = sessionManager.sessionGeneration
        val template = fetch { service.deactivate(id) }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return template
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

    private fun RecurringTransactionDto.toDomain(): RecurringTemplate =
        try {
            RecurringTemplate(
                id = id,
                accountId = accountId,
                direction = TransactionType.fromWire(direction),
                amount = Money(amount, CurrencyCode(currency)),
                category = category,
                frequency = RecurringFrequency.fromWire(frequency),
                nextDueDate = LocalDate.parse(nextDueDate),
                endDate = endDate?.let(LocalDate::parse),
                active = active,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }
}
