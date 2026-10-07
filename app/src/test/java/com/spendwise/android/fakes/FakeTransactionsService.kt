package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CreateTransactionRequestDto
import com.spendwise.android.data.remote.TransactionDto
import com.spendwise.android.data.remote.TransactionPageDto
import com.spendwise.android.data.remote.TransactionsService
import java.math.BigDecimal
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Scripted [TransactionsService] — throws the configured error or returns
 * canned DTOs, records calls (including the exact query params sent).
 */
class FakeTransactionsService : TransactionsService {

    var listResponse: List<TransactionDto> = emptyList()
    var listError: Throwable? = null
    var createResponse: TransactionDto = transactionDto(id = "txn-new")
    var createError: Throwable? = null

    /** Suspending responders for deterministic concurrency gates. */
    var listResponder: (suspend (accountId: String?, type: String?, category: String?) -> List<TransactionDto>)? =
        null
    var createResponder: (suspend (CreateTransactionRequestDto) -> TransactionDto)? = null
    var exportResponder: (suspend (String?) -> Response<ResponseBody>)? = null
    var pageResponder: (suspend (PageCall) -> TransactionPageDto)? = null
    var pageError: Throwable? = null

    data class ListCall(val accountId: String?, val type: String?, val category: String?)

    /** The exact query params a page request carried. */
    data class PageCall(
        val accountId: String?,
        val type: String?,
        val category: String?,
        val uncategorized: Boolean?,
        val provenance: String?,
        val from: String?,
        val to: String?,
        val page: Int,
        val size: Int,
    )

    val listCalls = mutableListOf<ListCall>()
    val createCalls = mutableListOf<CreateTransactionRequestDto>()
    val exportCalls = mutableListOf<String?>()
    val pageCalls = mutableListOf<PageCall>()

    override suspend fun list(
        accountId: String?,
        type: String?,
        category: String?,
    ): List<TransactionDto> {
        listCalls += ListCall(accountId, type, category)
        listResponder?.let { return it(accountId, type, category) }
        listError?.let { throw it }
        return listResponse
    }

    override suspend fun page(
        accountId: String?,
        type: String?,
        category: String?,
        uncategorized: Boolean?,
        provenance: String?,
        from: String?,
        to: String?,
        page: Int,
        size: Int,
    ): TransactionPageDto {
        val call = PageCall(accountId, type, category, uncategorized, provenance, from, to, page, size)
        pageCalls += call
        pageResponder?.let { return it(call) }
        pageError?.let { throw it }
        return TransactionPageDto(
            content = listResponse,
            page = page,
            size = size,
            totalElements = listResponse.size.toLong(),
            totalPages = 1,
            hasNext = false,
        )
    }

    override suspend fun create(body: CreateTransactionRequestDto): TransactionDto {
        createCalls += body
        createResponder?.let { return it(body) }
        createError?.let { throw it }
        return createResponse
    }

    override suspend fun export(accountId: String?): Response<ResponseBody> {
        exportCalls += accountId
        exportResponder?.let { return it(accountId) }
        return Response.success(
            "occurredOn,type,amount,currency,category,sourceRef\n".toResponseBody(CSV_MEDIA_TYPE),
            okhttp3.Headers.headersOf(
                "Content-Disposition", "attachment; filename=\"transactions.csv\"",
            ),
        )
    }

    companion object {
        val CSV_MEDIA_TYPE = "text/csv".toMediaType()
        fun transactionDto(
            id: String = "txn-1",
            accountId: String = "acc-1",
            type: String? = "EXPENSE",
            amount: BigDecimal = BigDecimal("25.50"),
            currency: String = "VND",
            category: String? = "FOOD",
            sourceRef: String? = "MANUAL:$id",
            occurredOn: String = "2026-10-04",
        ) = TransactionDto(
            id = id,
            accountId = accountId,
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            sourceRef = sourceRef,
            occurredOn = occurredOn,
        )
    }
}
