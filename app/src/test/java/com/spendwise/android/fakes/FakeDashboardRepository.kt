package com.spendwise.android.fakes

import com.spendwise.android.core.model.DashboardMonth
import com.spendwise.android.data.repository.DashboardRepository
import java.math.BigDecimal
import java.time.YearMonth

/** Scripted [DashboardRepository] for ViewModel tests — records calls, runs configured results. */
class FakeDashboardRepository : DashboardRepository {

    var monthResult: suspend (month: YearMonth) -> DashboardMonth = {
        FakeDashboardRepository.month()
    }

    val monthCalls = mutableListOf<YearMonth>()

    override suspend fun month(month: YearMonth): DashboardMonth {
        monthCalls += month
        return monthResult(month)
    }

    companion object {
        fun month(
            income: String = "4500.00",
            expense: String = "1200.50",
            month: String = "2026-10",
        ) = DashboardMonth(
            month = month,
            totalIncome = BigDecimal(income),
            totalExpense = BigDecimal(expense),
        )
    }
}
