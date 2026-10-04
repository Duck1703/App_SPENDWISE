package com.spendwise.android.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts every non-2xx response into an [ApiException] carrying the HTTP
 * status and any structured `fields` from a problem-detail body
 * (AD-012). Message text is never parsed for meaning.
 */
@Singleton
class ErrorInterceptor @Inject constructor(
    private val json: Json,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val body = runCatching { response.peekBody(MAX_BODY_PEEK).string() }.getOrNull()
        val problem = parseProblemDetail(body)
        response.close()
        throw ApiException(
            status = response.code,
            title = problem?.title,
            fields = problem?.fields.orEmpty(),
        )
    }

    private fun parseProblemDetail(body: String?): ProblemDetailDto? {
        if (body.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<ProblemDetailDto>(body) }.getOrNull()
    }

    private companion object {
        const val MAX_BODY_PEEK = 1024L * 64L
    }
}
