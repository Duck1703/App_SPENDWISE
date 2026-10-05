package com.spendwise.android.fakes

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.data.repository.AccountRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Scripted [AccountRepository] for ViewModel tests — records calls, runs configured results. */
class FakeAccountRepository : AccountRepository {

    var listResult: suspend (refresh: Boolean) -> List<Account> = { emptyList() }
    var findByIdResult: suspend (id: String, refresh: Boolean) -> Account =
        { _, _ -> account() }
    var createResult: suspend (name: String, type: AccountType, openingBalance: Money) -> Account =
        { _, _, _ -> account() }

    val listCalls = mutableListOf<Boolean>()
    val findByIdCalls = mutableListOf<Pair<String, Boolean>>()
    val createCalls = mutableListOf<Triple<String, AccountType, Money>>()
    var invalidateCalls = 0

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    /** Test hook — simulate an account write landing. */
    suspend fun emitMutation() = _mutations.emit(Unit)

    override suspend fun list(refresh: Boolean): List<Account> {
        listCalls += refresh
        return listResult(refresh)
    }

    override suspend fun findById(id: String, refresh: Boolean): Account {
        findByIdCalls += id to refresh
        return findByIdResult(id, refresh)
    }

    override suspend fun create(name: String, type: AccountType, openingBalance: Money): Account {
        createCalls += Triple(name, type, openingBalance)
        return createResult(name, type, openingBalance)
    }

    override suspend fun invalidate() {
        invalidateCalls++
    }

    companion object {
        fun account(
            id: String = "acc-1",
            name: String = "Everyday cash",
            type: AccountType = AccountType.CASH,
            currency: String = "VND",
            opening: String = "1000.00",
            balance: String = "1250.50",
        ) = Account(
            id = id,
            name = name,
            type = type,
            openingBalance = Money(BigDecimal(opening), CurrencyCode(currency)),
            balance = Money(BigDecimal(balance), CurrencyCode(currency)),
        )
    }
}
