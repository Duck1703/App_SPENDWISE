package com.spendwise.android.data.repository

import com.spendwise.android.core.model.DashboardMonth
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.DashboardService
import java.io.IOException
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dashboard boundary (PRODUCT §17, BACKEND-CONTRACT §22).
 *
 * The server computes the summary per request — the client always
 * fetches fresh and caches nothing (§17: "Dashboard is computed per
 * request — show its own loading state"). There is no cache to
 * stamp, but the publish still re-validates the session generation:
 * a response that lands after a session boundary throws
 * [SessionEndedException] instead of surfacing dead-session figures
 * (EXT-MAJOR-1 discipline).
 */
interface DashboardRepository {

    /**
     * `GET /api/dashboard?month=<month>`. [month] defaults to the
     * device-local current month — the UTC `Clock` bean is NOT used
     * (a UTC month is wrong for non-UTC users near boundaries).
     * 400 (mixed-currency aggregation or bad month) →
     * [AppError.Server]-class error via `toAppError`.
     */
    suspend fun month(month: YearMonth = YearMonth.now()): DashboardMonth
}

@Singleton
class RemoteDashboardRepository @Inject constructor(
    private val service: DashboardService,
    private val sessionManager: SessionManager,
) : DashboardRepository {

    override suspend fun month(month: YearMonth): DashboardMonth {
        val generation = sessionManager.sessionGeneration
        val dto = try {
            service.month(month.toString()) // YearMonth → "YYYY-MM"
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return DashboardMonth(
            month = dto.month,
            totalIncome = dto.totalIncome,
            totalExpense = dto.totalExpense,
        )
    }
}
