package com.spendwise.android.fakes

import com.spendwise.android.core.model.CategorizationRule
import com.spendwise.android.core.model.RuleConditionType
import com.spendwise.android.data.repository.RuleRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Scripted [RuleRepository] for ViewModel tests. */
class FakeRuleRepository : RuleRepository {

    var listResult: suspend () -> List<CategorizationRule> = { emptyList() }
    var findResult: suspend (String) -> CategorizationRule = { rule(id = it) }
    var createResult: suspend (CreateCall) -> CategorizationRule = { rule() }
    var disableResult: suspend (String) -> CategorizationRule = { rule(id = it, enabled = false) }

    data class CreateCall(
        val conditionType: RuleConditionType,
        val conditionValue: String,
        val targetCategory: String,
        val priority: Int,
    )

    val listCalls = mutableListOf<Unit>()
    val createCalls = mutableListOf<CreateCall>()
    val disableCalls = mutableListOf<String>()

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

    override suspend fun list(): List<CategorizationRule> {
        listCalls += Unit
        return listResult()
    }

    override suspend fun findById(id: String): CategorizationRule = findResult(id)

    override suspend fun create(
        conditionType: RuleConditionType,
        conditionValue: String,
        targetCategory: String,
        priority: Int,
    ): CategorizationRule {
        val call = CreateCall(conditionType, conditionValue, targetCategory, priority)
        createCalls += call
        return createResult(call)
    }

    override suspend fun disable(id: String): CategorizationRule {
        disableCalls += id
        return disableResult(id)
    }

    companion object {
        fun rule(
            id: String = "rl1",
            conditionType: RuleConditionType = RuleConditionType.CONTAINS,
            conditionValue: String = "coffee",
            targetCategory: String = "FOOD",
            priority: Int = 1,
            enabled: Boolean = true,
        ) = CategorizationRule(
            id = id,
            conditionType = conditionType,
            conditionValue = conditionValue,
            targetCategory = targetCategory,
            priority = priority,
            enabled = enabled,
        )
    }
}
