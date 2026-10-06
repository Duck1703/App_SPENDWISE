package com.spendwise.android.fakes

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionType
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
    var findByIdResult: suspend (id: String) -> Transaction = { transaction() }
    var createResult: suspend (accountId: String, type: TransactionType, amount: Money, occurredOn: LocalDate, category: String?, hint: String?) -> Transaction =
        { _, _, _, _, _, _ -> transaction() }

    data class ListCall(val accountId: String?, val type: TransactionType?, val category: String?)
    data class CreateCall(
        val accountId: String,
        val type: TransactionType,
        val amount: Money,
        val occurredOn: LocalDate,
        val category: String?,
        val hint: String?,
    )

    val listCalls = mutableListOf<ListCall>()
    val findByIdCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateCall>()

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
        hint: String?,
    ): Transaction {
        createCalls += CreateCall(accountId, type, amount, occurredOn, category, hint)
        return createResult(accountId, type, amount, occurredOn, category, hint)
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
        ) = Transaction(
            id = id,
            accountId = accountId,
            type = type,
            amount = Money(BigDecimal(amount), CurrencyCode(currency)),
            category = category,
            sourceRef = sourceRef,
            occurredOn = occurredOn,
        )
    }
}
