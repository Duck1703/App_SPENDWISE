package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CreateRecurringRequestDto
import com.spendwise.android.data.remote.RecurringService
import com.spendwise.android.data.remote.RecurringTransactionDto
import java.math.BigDecimal

/** Scripted [RecurringService] — records calls, runs configured results. */
class FakeRecurringService : RecurringService {

    var listResult: suspend () -> List<RecurringTransactionDto> = { listOf(template()) }
    var getResult: suspend (String) -> RecurringTransactionDto = { template(id = it) }
    var createResult: suspend (CreateRecurringRequestDto) -> RecurringTransactionDto = {
        template(accountId = it.accountId, direction = it.direction, amount = it.amount,
            currency = it.currency, category = it.category, frequency = it.frequency,
            nextDueDate = it.nextDueDate, endDate = it.endDate)
    }
    var deactivateResult: suspend (String) -> RecurringTransactionDto = { template(id = it, active = false) }

    val listCalls = mutableListOf<Unit>()
    val getCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateRecurringRequestDto>()
    val deactivateCalls = mutableListOf<String>()

    override suspend fun list(): List<RecurringTransactionDto> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun getById(id: String): RecurringTransactionDto {
        getCalls += id
        return getResult(id)
    }

    override suspend fun create(body: CreateRecurringRequestDto): RecurringTransactionDto {
        createCalls += body
        return createResult(body)
    }

    override suspend fun deactivate(id: String): RecurringTransactionDto {
        deactivateCalls += id
        return deactivateResult(id)
    }

    companion object {
        fun template(
            id: String = "r1",
            accountId: String = "a1",
            direction: String = "EXPENSE",
            amount: BigDecimal = BigDecimal("1200.00"),
            currency: String = "VND",
            category: String? = "HOUSING",
            frequency: String = "MONTHLY",
            nextDueDate: String = "2026-11-01",
            endDate: String? = null,
            active: Boolean = true,
        ) = RecurringTransactionDto(
            id = id,
            accountId = accountId,
            direction = direction,
            amount = amount,
            currency = currency,
            category = category,
            frequency = frequency,
            nextDueDate = nextDueDate,
            endDate = endDate,
            active = active,
        )
    }
}
