package com.spendwise.android.fakes

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.data.repository.RecurringRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Scripted [RecurringRepository] for ViewModel tests. */
class FakeRecurringRepository : RecurringRepository {

    var listResult: suspend () -> List<RecurringTemplate> = { emptyList() }
    var findResult: suspend (String) -> RecurringTemplate = { template(id = it) }
    var createResult: suspend (CreateCall) -> RecurringTemplate = { template() }
    var deactivateResult: suspend (String) -> RecurringTemplate = { template(id = it, active = false) }
    var updateResult: suspend (UpdateCall) -> RecurringTemplate = { call ->
        template(
            id = call.id,
            amount = call.amount ?: template(id = call.id).amount,
            category = if (call.clearCategory) null else call.category ?: "HOUSING",
            frequency = call.frequency ?: RecurringFrequency.MONTHLY,
            nextDueDate = call.nextDueDate ?: template(id = call.id).nextDueDate,
            endDate = if (call.clearEndDate) null else call.endDate,
        )
    }

    data class UpdateCall(
        val id: String,
        val amount: Money?,
        val category: String?,
        val clearCategory: Boolean,
        val frequency: RecurringFrequency?,
        val nextDueDate: LocalDate?,
        val endDate: LocalDate?,
        val clearEndDate: Boolean,
    )

    data class CreateCall(
        val accountId: String,
        val direction: TransactionType,
        val amount: Money,
        val category: String?,
        val frequency: RecurringFrequency,
        val nextDueDate: LocalDate,
        val endDate: LocalDate?,
    )

    val listCalls = mutableListOf<Unit>()
    val createCalls = mutableListOf<CreateCall>()
    val deactivateCalls = mutableListOf<String>()
    val updateCalls = mutableListOf<UpdateCall>()

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    override suspend fun list(): List<RecurringTemplate> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun findById(id: String): RecurringTemplate = findResult(id)

    override suspend fun create(
        accountId: String,
        direction: TransactionType,
        amount: Money,
        category: String?,
        frequency: RecurringFrequency,
        nextDueDate: LocalDate,
        endDate: LocalDate?,
    ): RecurringTemplate {
        val call = CreateCall(accountId, direction, amount, category, frequency, nextDueDate, endDate)
        createCalls += call
        return createResult(call)
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
        val call = UpdateCall(id, amount, category, clearCategory, frequency, nextDueDate, endDate, clearEndDate)
        updateCalls += call
        return updateResult(call)
    }

    override suspend fun deactivate(id: String): RecurringTemplate {
        deactivateCalls += id
        return deactivateResult(id)
    }

    companion object {
        fun template(
            id: String = "r1",
            accountId: String = "acc-1",
            direction: TransactionType = TransactionType.EXPENSE,
            amount: Money = Money(BigDecimal("1200"), CurrencyCode("VND")),
            category: String? = "HOUSING",
            frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
            nextDueDate: LocalDate = LocalDate.parse("2026-11-01"),
            endDate: LocalDate? = null,
            active: Boolean = true,
        ) = RecurringTemplate(
            id = id,
            accountId = accountId,
            direction = direction,
            amount = amount,
            category = category,
            frequency = frequency,
            nextDueDate = nextDueDate,
            endDate = endDate,
            active = active,
        )
    }
}
