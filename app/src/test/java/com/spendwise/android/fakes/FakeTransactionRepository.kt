package com.spendwise.android.fakes

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionPage
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.model.TransferOutcome
import com.spendwise.android.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.math.BigDecimal
import java.time.LocalDate

/** Scripted [TransactionRepository] for ViewModel tests — records calls, runs configured results. */
class FakeTransactionRepository : TransactionRepository {

    var listResult: suspend (accountId: String?, type: TransactionType?, category: String?) -> List<Transaction> =
        { _, _, _ -> emptyList() }
    /** M15: scripted page responder — receives the full filter + page index. */
    var listPageResult: suspend (PageCall) -> TransactionPage = { call ->
        TransactionPage(
            items = emptyList(),
            page = call.page,
            size = call.size,
            totalElements = 0,
            totalPages = 0,
            hasNext = false,
        )
    }
    var findByIdResult: suspend (id: String) -> Transaction = { transaction() }
    var createResult: suspend (accountId: String, type: TransactionType, amount: Money, occurredOn: LocalDate, category: String?, description: String?, clientRef: String?) -> Transaction =
        { _, _, _, _, _, _, _ -> transaction() }
    var transferResult: suspend (sourceAccountId: String, destinationAccountId: String, amount: Money) -> TransferOutcome =
        { source, destination, _ ->
            TransferOutcome(
                transferRef = "ref-1",
                outTransaction = transaction(
                    id = "txn-out-1",
                    accountId = source,
                    type = TransactionType.TRANSFER_OUT,
                    category = null,
                    sourceRef = "TRANSFER:ref-1:OUT",
                ),
                inTransaction = transaction(
                    id = "txn-in-1",
                    accountId = destination,
                    type = TransactionType.TRANSFER_IN,
                    category = null,
                    sourceRef = "TRANSFER:ref-1:IN",
                ),
            )
        }

    data class ListCall(val accountId: String?, val type: TransactionType?, val category: String?)
    data class CreateCall(
        val accountId: String,
        val type: TransactionType,
        val amount: Money,
        val occurredOn: LocalDate,
        val category: String?,
        val description: String?,
        val clientRef: String?,
    )

    val listCalls = mutableListOf<ListCall>()
    val pageCalls = mutableListOf<PageCall>()
    val findByIdCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateCall>()
    val transferCalls = mutableListOf<TransferCall>()

    data class TransferCall(
        val sourceAccountId: String,
        val destinationAccountId: String,
        val amount: Money,
    )

    /** The exact arguments a page request carried. */
    data class PageCall(
        val accountId: String?,
        val type: TransactionType?,
        val category: String?,
        val uncategorized: Boolean,
        val provenance: TransactionProvenance?,
        val from: LocalDate?,
        val to: LocalDate?,
        val q: String?,
        val page: Int,
        val size: Int,
    )

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    /** Test hook — simulate a server-side write landing. */
    suspend fun emitMutation() = _mutations.emit(Unit)

    var notifyLedgerChangedCalls = 0
        private set

    override suspend fun notifyLedgerChanged() {
        notifyLedgerChangedCalls++
        _mutations.emit(Unit)
    }

    override suspend fun list(
        accountId: String?,
        type: TransactionType?,
        category: String?,
    ): List<Transaction> {
        listCalls += ListCall(accountId, type, category)
        return listResult(accountId, type, category)
    }

    override suspend fun listPage(
        accountId: String?,
        type: TransactionType?,
        category: String?,
        uncategorized: Boolean,
        provenance: TransactionProvenance?,
        from: LocalDate?,
        to: LocalDate?,
        q: String?,
        page: Int,
        size: Int,
    ): TransactionPage {
        val call = PageCall(accountId, type, category, uncategorized, provenance, from, to, q, page, size)
        pageCalls += call
        return listPageResult(call)
    }

    override suspend fun findById(id: String): Transaction {
        findByIdCalls += id
        return findByIdResult(id)
    }

    override suspend fun create(
        accountId: String,
        type: TransactionType,
        amount: Money,
        occurredOn: LocalDate,
        category: String?,
        description: String?,
        clientRef: String?,
    ): Transaction {
        createCalls += CreateCall(accountId, type, amount, occurredOn, category, description, clientRef)
        return createResult(accountId, type, amount, occurredOn, category, description, clientRef)
    }

    override suspend fun transfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: Money,
    ): TransferOutcome {
        transferCalls += TransferCall(sourceAccountId, destinationAccountId, amount)
        return transferResult(sourceAccountId, destinationAccountId, amount)
    }

    companion object {
        fun transaction(
            id: String = "txn-1",
            accountId: String = "acc-1",
            type: TransactionType = TransactionType.EXPENSE,
            amount: String = "25.50",
            currency: String = "VND",
            category: String? = "FOOD",
            sourceRef: String? = "MANUAL:$id",
            occurredOn: LocalDate = LocalDate.of(2026, 10, 4),
            description: String? = null,
        ) = Transaction(
            id = id,
            accountId = accountId,
            type = type,
            amount = Money(BigDecimal(amount), CurrencyCode(currency)),
            category = category,
            sourceRef = sourceRef,
            occurredOn = occurredOn,
            description = description,
        )
    }
}
