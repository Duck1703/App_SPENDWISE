package com.spendwise.android.fakes

import com.spendwise.android.core.model.Budget
import com.spendwise.android.core.model.BudgetCalculation
import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.data.repository.BudgetRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Scripted [BudgetRepository] for ViewModel tests. */
class FakeBudgetRepository : BudgetRepository {

    var listResult: suspend () -> List<Budget> = { emptyList() }
    var calculationResult: suspend (String) -> BudgetCalculation = { calculation(id = it) }
    var createResult: suspend (String, String, Money) -> Budget = { c, p, l -> budget(category = c, period = p, limit = l) }

    val listCalls = mutableListOf<Unit>()
    val createCalls = mutableListOf<Triple<String, String, Money>>()

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    override suspend fun list(): List<Budget> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun calculation(id: String): BudgetCalculation = calculationResult(id)

    override suspend fun create(category: String, period: String, limit: Money): Budget {
        createCalls += Triple(category, period, limit)
        return createResult(category, period, limit)
    }

    companion object {
        fun budget(
            id: String = "b1",
            category: String = "FOOD",
            period: String = "2026-10",
            limit: Money = Money(BigDecimal("500"), CurrencyCode("VND")),
        ) = Budget(id = id, category = category, period = period, limit = limit)

        fun calculation(
            id: String = "b1",
            category: String = "FOOD",
            period: String = "2026-10",
            limit: Money = Money(BigDecimal("500"), CurrencyCode("VND")),
            spent: Money = Money(BigDecimal("325.75"), limit.currency),
            remaining: Money = Money(BigDecimal("174.25"), limit.currency),
            percentage: BigDecimal = BigDecimal("65.15"),
            status: BudgetStatus = BudgetStatus.WITHIN_BUDGET,
        ) = BudgetCalculation(
            budgetId = id,
            category = category,
            period = period,
            limit = limit,
            spent = spent,
            remaining = remaining,
            percentage = percentage,
            status = status,
        )
    }
}
