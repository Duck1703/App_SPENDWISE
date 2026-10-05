package com.spendwise.android.fakes

import com.spendwise.android.data.repository.SelectedStatement
import com.spendwise.android.data.repository.StatementStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * JVM [StatementStore] — describe is scripted; toPart emits a real
 * multipart part over in-memory bytes so wire-level assertions still
 * see the real filename + `file` part name.
 */
class FakeStatementStore : StatementStore {

    var describeResult: suspend (String) -> SelectedStatement = { uri ->
        SelectedStatement(
            uri = uri,
            displayName = "statement.csv",
            sizeBytes = 4,
        )
    }

    val describeCalls = mutableListOf<String>()
    val toPartCalls = mutableListOf<SelectedStatement>()

    var partContent: ByteArray = "a,b,".toByteArray()

    override suspend fun describe(uri: String): SelectedStatement {
        describeCalls += uri
        return describeResult(uri)
    }

    override fun toPart(file: SelectedStatement): MultipartBody.Part {
        toPartCalls += file
        return MultipartBody.Part.createFormData(
            "file",
            file.displayName,
            partContent.toRequestBody("text/csv".toMediaType()),
        )
    }
}
