package com.spendwise.android.data.repository

import com.spendwise.android.core.model.CategorizationRule
import com.spendwise.android.core.model.RuleConditionType
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CategorizationRuleDto
import com.spendwise.android.data.remote.CreateRuleRequestDto
import com.spendwise.android.data.remote.RulesService
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categorization-rule boundary (PRODUCT §30 S21–S23, backend `8194daa`).
 *
 * Rules are create/list/get/disable — `DELETE /{id}` flips `enabled` and
 * keeps the row; there is no edit or re-enable endpoint. The server
 * orders list results by ascending priority; the client renders that
 * order verbatim and never re-sorts.
 *
 * Session safety: generation captured before every call, re-validated
 * before returning — a stale-generation result can never surface
 * (EXT-MAJOR-1). [create]/[disable] emit [mutations] so list surfaces
 * reload; neither touches the ledger.
 */
interface RuleRepository {

    /** `GET /api/categorization-rules` — always network-fresh, priority-ordered. */
    suspend fun list(): List<CategorizationRule>

    /** `GET /api/categorization-rules/{id}`. Foreign/absent → [AppError.NotFound]. */
    suspend fun findById(id: String): CategorizationRule

    /**
     * `POST /api/categorization-rules`. `enabled` is never sent — the
     * server default applies (S22 note). Duplicate priority →
     * [AppError.Conflict]; unknown category/condition type → 400;
     * `fields{}` validation → [AppError.Validation]. On success emits
     * [mutations].
     */
    suspend fun create(
        conditionType: RuleConditionType,
        conditionValue: String,
        targetCategory: String,
        priority: Int,
    ): CategorizationRule

    /**
     * `DELETE /{id}` — disables the rule (`enabled=false`). The server
     * returns the updated rule in the 200 body. Emits [mutations] on
     * success.
     */
    suspend fun disable(id: String): CategorizationRule

    /** Emitted once per successful [create]/[disable]. */
    val mutations: SharedFlow<Unit>
}

@Singleton
class RemoteRuleRepository @Inject constructor(
    private val service: RulesService,
    private val sessionManager: SessionManager,
) : RuleRepository {

    private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()


    override suspend fun list(): List<CategorizationRule> {
        val generation = sessionManager.sessionGeneration
        val rules = fetch { service.list() }.map { it.toDomain() }
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return rules
    }

    override suspend fun findById(id: String): CategorizationRule {
        val generation = sessionManager.sessionGeneration
        val rule = fetch { service.getById(id) }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        return rule
    }

    override suspend fun create(
        conditionType: RuleConditionType,
        conditionValue: String,
        targetCategory: String,
        priority: Int,
    ): CategorizationRule {
        require(
            conditionType == RuleConditionType.CONTAINS || conditionType == RuleConditionType.EQUALS,
        ) { "rule condition type is CONTAINS or EQUALS" }
        val generation = sessionManager.sessionGeneration
        val rule = fetch {
            service.create(
                CreateRuleRequestDto(
                    conditionType = conditionType.name,
                    conditionValue = conditionValue,
                    targetCategory = targetCategory,
                    priority = priority,
                ),
            )
        }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return rule
    }

    override suspend fun disable(id: String): CategorizationRule {
        val generation = sessionManager.sessionGeneration
        val rule = fetch { service.disable(id) }.toDomain()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        _mutations.tryEmit(Unit)
        return rule
    }

    private suspend fun <T> fetch(call: suspend () -> T): T =
        try {
            call()
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }

    private fun CategorizationRuleDto.toDomain(): CategorizationRule =
        try {
            CategorizationRule(
                id = id,
                conditionType = RuleConditionType.fromWire(conditionType),
                conditionValue = conditionValue,
                targetCategory = targetCategory ?: "",
                priority = priority,
                enabled = enabled,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AppErrorException(AppError.Unknown(e), e)
        }
}
