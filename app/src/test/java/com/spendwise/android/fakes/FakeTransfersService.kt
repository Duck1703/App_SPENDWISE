package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CreateTransferRequestDto
import com.spendwise.android.data.remote.TransferResponseDto
import com.spendwise.android.data.remote.TransfersService

/**
 * Scripted [TransfersService] — throws the configured error or returns
 * a canned pair, records the exact request bodies sent (M12).
 */
class FakeTransfersService : TransfersService {

    var createResponse: TransferResponseDto = transferResponse()
    var createError: Throwable? = null
    var createResponder: (suspend (CreateTransferRequestDto) -> TransferResponseDto)? = null

    val createCalls = mutableListOf<CreateTransferRequestDto>()

    override suspend fun create(body: CreateTransferRequestDto): TransferResponseDto {
        createCalls += body
        createResponder?.let { return it(body) }
        createError?.let { throw it }
        return createResponse
    }

    companion object {
        fun transferResponse(
            transferRef: String = "ref-1",
            sourceAccountId: String = "acc-1",
            destinationAccountId: String = "acc-2",
            amount: java.math.BigDecimal = java.math.BigDecimal("25.00"),
            currency: String = "USD",
        ) = TransferResponseDto(
            transferRef = transferRef,
            outTransaction = FakeTransactionsService.transactionDto(
                id = "txn-out-$transferRef",
                accountId = sourceAccountId,
                type = "TRANSFER_OUT",
                amount = amount,
                currency = currency,
                category = null,
                sourceRef = "TRANSFER:$transferRef:OUT",
            ),
            inTransaction = FakeTransactionsService.transactionDto(
                id = "txn-in-$transferRef",
                accountId = destinationAccountId,
                type = "TRANSFER_IN",
                amount = amount,
                currency = currency,
                category = null,
                sourceRef = "TRANSFER:$transferRef:IN",
            ),
        )
    }
}
