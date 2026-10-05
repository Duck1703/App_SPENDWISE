package com.spendwise.android.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * `POST /api/imports?accountId=<UUID>` (BACKEND-CONTRACT §16 — verified
 * against `ImportController` at backend `8194daa`).
 *
 * `multipart/form-data` with a single required `file` part; `accountId`
 * rides as a **query** param. Synchronous — the server parses and
 * commits the whole file before returning 201 +
 * `ImportBatchSummaryResponse`; there is no 202/pending contract.
 * Foreign `accountId` → 404; unsupported extension/parse failure →
 * 400; structural CSV failure/oversize/mid-loop crash → 500 (the
 * mid-loop case can leave a partially committed batch — re-upload of
 * the same file is safe: server dedup skips imported rows). A 401
 * drives `Session.Expired` — the client never auto-replays the upload.
 */
interface ImportsService {

    @Multipart
    @POST("api/imports")
    suspend fun upload(
        @Query("accountId") accountId: String,
        @Part file: MultipartBody.Part,
    ): ImportBatchSummaryDto
}
