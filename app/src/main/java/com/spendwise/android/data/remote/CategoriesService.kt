package com.spendwise.android.data.remote

import retrofit2.http.GET

/**
 * `GET /api/categories` → `List<CategoryResponse>` where each item is
 * `{value}` — the category code string itself (BACKEND-CONTRACT §11,
 * `CategoryController` at backend `8194daa`). The set is server-fixed
 * reference data (FOOD/TRANSPORT/HOUSING/ENTERTAINMENT/SALARY today) but
 * always read live — never hardcoded client-side.
 */
interface CategoriesService {

    @GET("api/categories")
    suspend fun list(): List<CategoryDto>
}
