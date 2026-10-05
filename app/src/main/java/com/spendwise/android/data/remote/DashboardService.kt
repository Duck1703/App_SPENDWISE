package com.spendwise.android.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * `GET /api/dashboard?month=YYYY-MM` (BACKEND-CONTRACT §22 — verified
 * against `DashboardController` at backend `8194daa`).
 *
 * `month` is required and must be `YYYY-MM`; a malformed value → 400.
 * Same-type mixed currencies inside the month → 400
 * (`CurrencyMismatchException`) — the client maps that to the §17
 * honest "combined totals unavailable" state. Authenticated — a 401
 * drives `Session.Expired`.
 */
interface DashboardService {

    @GET("api/dashboard")
    suspend fun month(@Query("month") month: String): DashboardMonthDto
}
