package com.spendwise.android.fakes

import com.spendwise.android.data.repository.CategoryRepository

/** Scripted [CategoryRepository] — canned code list, call recording. */
class FakeCategoryRepository : CategoryRepository {

    var listResult: suspend (refresh: Boolean) -> List<String> = {
        listOf("FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "SALARY")
    }

    val listCalls = mutableListOf<Boolean>()

    override suspend fun list(refresh: Boolean): List<String> {
        listCalls += refresh
        return listResult(refresh)
    }
}
