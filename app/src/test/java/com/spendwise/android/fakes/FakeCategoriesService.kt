package com.spendwise.android.fakes

import com.spendwise.android.data.remote.CategoriesService
import com.spendwise.android.data.remote.CategoryDto

/** Scripted [CategoriesService] — canned `{value}` items, call counting. */
class FakeCategoriesService : CategoriesService {

    var listResponse: List<CategoryDto> = listOf(
        CategoryDto("FOOD"),
        CategoryDto("TRANSPORT"),
        CategoryDto("HOUSING"),
        CategoryDto("ENTERTAINMENT"),
        CategoryDto("SALARY"),
    )
    var listError: Throwable? = null
    var listCalls = 0

    override suspend fun list(): List<CategoryDto> {
        listCalls++
        listError?.let { throw it }
        return listResponse
    }
}
