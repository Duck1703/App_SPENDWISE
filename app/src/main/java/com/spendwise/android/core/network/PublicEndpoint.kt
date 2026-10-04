package com.spendwise.android.core.network

/**
 * Marks a Retrofit service method as **unauthenticated** (permitAll on the
 * backend — login/register). The [ErrorInterceptor] reads the Retrofit
 * `Invocation` tag and must not treat a 401 from such a call as session
 * expiry — those 401s are ordinary auth failures (§13).
 *
 * Deterministic contract, no URL substring matching.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint
