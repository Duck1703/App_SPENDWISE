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
