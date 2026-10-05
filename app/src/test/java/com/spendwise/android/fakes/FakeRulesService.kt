package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CategorizationRuleDto
import com.spendwise.android.data.remote.CreateRuleRequestDto
import com.spendwise.android.data.remote.RulesService

/** Scripted [RulesService] — records calls, runs configured results. */
class FakeRulesService : RulesService {

    var listResult: suspend () -> List<CategorizationRuleDto> = { listOf(rule()) }
    var getResult: suspend (String) -> CategorizationRuleDto = { rule(id = it) }
    var createResult: suspend (CreateRuleRequestDto) -> CategorizationRuleDto = {
        rule(conditionType = it.conditionType, conditionValue = it.conditionValue,
            targetCategory = it.targetCategory, priority = it.priority)
    }
    var disableResult: suspend (String) -> CategorizationRuleDto = { rule(id = it, enabled = false) }

    val listCalls = mutableListOf<Unit>()
    val getCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateRuleRequestDto>()
    val disableCalls = mutableListOf<String>()

    override suspend fun list(): List<CategorizationRuleDto> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun getById(id: String): CategorizationRuleDto {
        getCalls += id
        return getResult(id)
    }

    override suspend fun create(body: CreateRuleRequestDto): CategorizationRuleDto {
        createCalls += body
        return createResult(body)
    }

    override suspend fun disable(id: String): CategorizationRuleDto {
        disableCalls += id
        return disableResult(id)
    }

    companion object {
        fun rule(
            id: String = "rl1",
            conditionType: String = "CONTAINS",
            conditionValue: String = "coffee",
            targetCategory: String = "FOOD",
            priority: Int = 1,
            enabled: Boolean = true,
        ) = CategorizationRuleDto(
            id = id,
            conditionType = conditionType,
            conditionValue = conditionValue,
            targetCategory = targetCategory,
            priority = priority,
            enabled = enabled,
        )
    }
}
