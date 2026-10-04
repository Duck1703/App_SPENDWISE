package com.spendwise.android.fakes

import com.spendwise.android.data.remote.AccountDto
import com.spendwise.android.data.remote.AccountsService
import com.spendwise.android.data.remote.CreateAccountRequestDto
import java.math.BigDecimal

/** Scripted [AccountsService] — throws the configured error or returns canned DTOs, records calls. */
class FakeAccountsService : AccountsService {

    var listResponse: List<AccountDto> = emptyList()
    var listError: Throwable? = null
    var getByIdResponse: AccountDto = accountDto()
    var getByIdError: Throwable? = null
    var createResponse: AccountDto = accountDto()
    var createError: Throwable? = null

    /**
     * Optional suspending responders for deterministic concurrency tests —
     * a `CompletableDeferred` gate inside controls exactly when the
     * "network" call completes.
     */
    var listResponder: (suspend () -> List<AccountDto>)? = null
    var getByIdResponder: (suspend (id: String) -> AccountDto)? = null
    var createResponder: (suspend (CreateAccountRequestDto) -> AccountDto)? = null

    var listCalls = 0
    val getByIdCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateAccountRequestDto>()

    override suspend fun list(): List<AccountDto> {
        listCalls++
        listResponder?.let { return it() }
        listError?.let { throw it }
        return listResponse
    }

    override suspend fun getById(id: String): AccountDto {
        getByIdCalls += id
        getByIdResponder?.let { return it(id) }
        getByIdError?.let { throw it }
        return getByIdResponse
    }

    override suspend fun create(body: CreateAccountRequestDto): AccountDto {
        createCalls += body
        createResponder?.let { return it(body) }
        createError?.let { throw it }
        return createResponse
    }

    companion object {
        fun accountDto(
            id: String = "acc-1",
            name: String = "Everyday cash",
            type: String? = "CASH",
            openingBalance: BigDecimal = BigDecimal("1000.00"),
            currency: String = "VND",
            balance: BigDecimal = BigDecimal("1250.50"),
        ) = AccountDto(
            id = id,
            name = name,
            type = type,
            openingBalance = openingBalance,
            currency = currency,
            balance = balance,
        )
    }
}
