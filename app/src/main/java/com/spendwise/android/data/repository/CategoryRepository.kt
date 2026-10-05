package com.spendwise.android.data.repository

import com.spendwise.android.core.common.ApplicationScope
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionCache
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CategoriesService
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categories boundary (AD-016, BACKEND-CONTRACT §11): `GET /api/categories`
 * returns server-fixed reference codes (`{value}` items). Read live —
 * never hardcoded — and cached per session via [SessionCache] so the
 * picker/filter stay cheap without leaking across identities.
 */
interface CategoryRepository {

    /** Server category codes, cache-first unless [refresh]. */
    suspend fun list(refresh: Boolean = false): List<String>
}

@Singleton
class RemoteCategoryRepository @Inject constructor(
    private val service: CategoriesService,
    sessionManager: SessionManager,
    @ApplicationScope appScope: CoroutineScope,
) : CategoryRepository {

    private val cache = SessionCache<List<String>>(sessionManager)

    init {
        appScope.launch {
            sessionManager.cacheInvalidations.collect { cache.invalidate() }
        }
    }

    override suspend fun list(refresh: Boolean): List<String> {
        val generation = cache.generation()
        if (!refresh) cache.read()?.let { return it }
        val categories = try {
            service.list()
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }.map { it.value }
        if (!cache.write(generation, categories)) throw SessionEndedException()
        return categories
    }
}
