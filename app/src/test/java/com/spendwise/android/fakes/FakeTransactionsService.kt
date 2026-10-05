package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CreateTransactionRequestDto
import com.spendwise.android.data.remote.TransactionDto
import com.spendwise.android.data.remote.TransactionsService
import java.math.BigDecimal

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

    data class ListCall(val accountId: String?, val type: String?, val category: String?)

    val listCalls = mutableListOf<ListCall>()
    val createCalls = mutableListOf<CreateTransactionRequestDto>()

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

    override suspend fun create(body: CreateTransactionRequestDto): TransactionDto {
        createCalls += body
        createResponder?.let { return it(body) }
        createError?.let { throw it }
        return createResponse
    }

    companion object {
        fun transactionDto(
            id: String = "txn-1",
            accountId: String = "acc-1",
            type: String? = "EXPENSE",
            amount: BigDecimal = BigDecimal("25.50"),
            currency: String = "VND",
            category: String? = "FOOD",
            sourceRef: String? = "MANUAL:$id",
        ) = TransactionDto(
            id = id,
            accountId = accountId,
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            sourceRef = sourceRef,
        )
    }
}
