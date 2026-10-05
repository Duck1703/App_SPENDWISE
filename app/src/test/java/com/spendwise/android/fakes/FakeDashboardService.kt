package com.spendwise.android.fakes

import com.spendwise.android.data.remote.DashboardMonthDto
import com.spendwise.android.data.remote.DashboardService
import java.math.BigDecimal

/** Scripted [DashboardService] — throws the configured error or returns a canned DTO, records calls. */
class FakeDashboardService : DashboardService {

    var monthResponse: DashboardMonthDto = monthDto()
    var monthError: Throwable? = null

    /**
     * Optional suspending responder for deterministic concurrency tests —
     * a `CompletableDeferred` gate inside controls exactly when the
     * "network" call completes.
     */
    var monthResponder: (suspend (month: String) -> DashboardMonthDto)? = null

    val monthCalls = mutableListOf<String>()

    override suspend fun month(month: String): DashboardMonthDto {
        monthCalls += month
        monthResponder?.let { return it(month) }
        monthError?.let { throw it }
        return monthResponse
    }

    companion object {
        fun monthDto(
            month: String = "2026-10",
            totalIncome: BigDecimal = BigDecimal("4500.00"),
            totalExpense: BigDecimal = BigDecimal("1200.50"),
        ) = DashboardMonthDto(
            month = month,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
        )
    }
}
