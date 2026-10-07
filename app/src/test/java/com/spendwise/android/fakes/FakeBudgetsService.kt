package com.spendwise.android.fakes

import com.spendwise.android.data.remote.BudgetCalculationDto
import com.spendwise.android.data.remote.BudgetDto
import com.spendwise.android.data.remote.BudgetsService
import com.spendwise.android.data.remote.CreateBudgetRequestDto
import com.spendwise.android.data.remote.UpdateBudgetRequestDto
import java.math.BigDecimal

/** Scripted [BudgetsService] — records calls, runs configured results. */
class FakeBudgetsService : BudgetsService {

    var listResult: suspend () -> List<BudgetDto> = { listOf(budget()) }
    var getResult: suspend (String) -> BudgetDto = { budget(id = it) }
    var calculationResult: suspend (String) -> BudgetCalculationDto = { calculation(budgetId = it) }
    var createResult: suspend (CreateBudgetRequestDto) -> BudgetDto =
        { budget(category = it.category, period = it.period, limit = it.limit, currency = it.currency) }

    var updateResult: suspend (String, UpdateBudgetRequestDto) -> BudgetDto =
        { id, b -> budget(id = id, period = b.period ?: "2026-10", limit = b.limit ?: BigDecimal("500.00")) }
    var deleteResult: suspend (String) -> Unit = {}

    val listCalls = mutableListOf<Unit>()
    val getCalls = mutableListOf<String>()
    val calculationCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateBudgetRequestDto>()
    val updateCalls = mutableListOf<Pair<String, UpdateBudgetRequestDto>>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun list(): List<BudgetDto> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun getById(id: String): BudgetDto {
        getCalls += id
        return getResult(id)
    }

    override suspend fun calculation(id: String): BudgetCalculationDto {
        calculationCalls += id
        return calculationResult(id)
    }

    override suspend fun create(body: CreateBudgetRequestDto): BudgetDto {
        createCalls += body
        return createResult(body)
    }

    override suspend fun update(id: String, body: UpdateBudgetRequestDto): BudgetDto {
        updateCalls += id to body
        return updateResult(id, body)
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        deleteResult(id)
    }

    companion object {
        fun budget(
            id: String = "b1",
            category: String = "FOOD",
            period: String = "2026-10",
            limit: BigDecimal = BigDecimal("500.00"),
            currency: String = "VND",
        ) = BudgetDto(id = id, category = category, period = period, limit = limit, currency = currency)

        fun calculation(
            budgetId: String = "b1",
            category: String = "FOOD",
            period: String = "2026-10",
            limit: BigDecimal = BigDecimal("500.00"),
            currency: String = "VND",
            spent: BigDecimal = BigDecimal("325.75"),
            remaining: BigDecimal = BigDecimal("174.25"),
            percentage: BigDecimal = BigDecimal("65.15"),
            status: String = "WITHIN_BUDGET",
        ) = BudgetCalculationDto(
            budgetId = budgetId,
            category = category,
            period = period,
            limit = limit,
            currency = currency,
            spent = spent,
            remaining = remaining,
            percentage = percentage,
            status = status,
        )
    }
}
