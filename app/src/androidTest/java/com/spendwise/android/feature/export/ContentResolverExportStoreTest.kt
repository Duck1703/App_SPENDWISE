package com.spendwise.android.feature.export

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.data.repository.ContentResolverExportStore
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `ContentResolverExportStore` on a real Android device — the temp
 * artifact lives under `cacheDir/ledger-export/`, streams byte-exact,
 * and `ContentResolver.openOutputStream` to a `file://` destination
 * exercises the same code path as a SAF-picked `content://` URI.
 */
@RunWith(AndroidJUnit4::class)
class ContentResolverExportStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun body(bytes: ByteArray) =
        bytes.toResponseBody("text/csv".toMediaType())

    @Test
    fun temp_artifact_lives_in_the_private_cache_dir() = runTest {
        val store = ContentResolverExportStore(context)
        val artifact = store.writeTemp("transactions.csv", body("a,b\n".toByteArray()))

        try {
            assertTrue(artifact.file.absolutePath.startsWith(context.cacheDir.absolutePath))
            assertTrue(artifact.file.absolutePath.contains("ledger-export"))
            assertTrue(artifact.file.exists())
        } finally {
            store.discard(artifact)
        }
    }

    @Test
    fun writeTemp_streams_bytes_byte_identically() = runTest {
        val store = ContentResolverExportStore(context)
        val raw = ("occurredOn,type,amount,currency,category,sourceRef\r\n" +
            "2026-01-05,EXPENSE,\"Tiền, café\",VND,FOOD,t1\r\n" +
            "2026-01-06,INCOME,99999999999999999.99,VND,,t2\r\n")
            .toByteArray(StandardCharsets.UTF_8)

        val artifact = store.writeTemp("x.csv", body(raw))
        try {
            assertArrayEquals(raw, artifact.file.readBytes())
            assertEquals(raw.size.toLong(), artifact.sizeBytes)
        } finally {
            store.discard(artifact)
        }
    }

    @Test
    fun copyTo_streams_byte_identical_through_ContentResolver() = runTest {
        val store = ContentResolverExportStore(context)
        val raw = "a,\"b,c\"\nđồng,1.00\n".toByteArray(StandardCharsets.UTF_8)
        val artifact = store.writeTemp("x.csv", body(raw))
        val dest = File(context.cacheDir, "export-dest-${System.nanoTime()}.csv")

        try {
            store.copyTo(artifact, dest.toUri().toString())
            assertArrayEquals(raw, dest.readBytes())
        } finally {
            store.discard(artifact)
            dest.delete()
        }
    }

    @Test
    fun discard_deletes_the_artifact_and_tolerates_a_repeat() = runTest {
        val store = ContentResolverExportStore(context)
        val artifact = store.writeTemp("x.csv", body("a\n".toByteArray()))
        val file = artifact.file

        store.discard(artifact)
        assertFalse(file.exists())
        store.discard(artifact) // idempotent
    }

    @Test
    fun discardOrphans_clears_every_leftover_export() = runTest {
        val store = ContentResolverExportStore(context)
        // True orphans are files whose owners are gone — e.g. leftovers
        // from a dead process. Files created by writeTemp are LIVE
        // (registry-owned) and must survive the sweep.
        val dir = File(context.cacheDir, "ledger-export").apply { mkdirs() }
        val a = File(dir, "orphan-a.csv").apply { writeText("a") }
        val b = File(dir, "orphan-b.csv").apply { writeText("b") }

        store.discardOrphans()

        assertFalse(a.exists())
        assertFalse(b.exists())
    }

    @Test
    fun discardOrphans_never_deletes_a_live_artifact() = runTest {
        val store = ContentResolverExportStore(context)
        val live = store.writeTemp("live.csv", body("live\n".toByteArray()))

        // A second screen instance sweeping on entry must not kill an
        // artifact still owned by an in-flight export (EXT-M9-MAJOR-1).
        store.discardOrphans()

        assertTrue(live.file.exists())

        // Once the owner discards it, the sweep can reclaim it.
        store.discard(live)
        store.discardOrphans()
        assertFalse(live.file.exists())
    }

    @Test
    fun failed_download_leaves_no_partial_temp_file() = runTest {
        val store = ContentResolverExportStore(context)
        val dir = File(context.cacheDir, "ledger-export")
        val before = dir.listFiles()?.size ?: 0

        // A body whose stream dies mid-read.
        val broken = object : okhttp3.ResponseBody() {
            override fun contentType() = "text/csv".toMediaType()
            override fun contentLength() = -1L
            override fun source() = okio.Buffer().apply {
                writeUtf8("partial")
                close()
            }.let { throw java.io.IOException("stream died") }
        }
        try {
            store.writeTemp("x.csv", broken)
        } catch (e: java.io.IOException) {
            // expected
        }

        assertEquals(before, dir.listFiles()?.size ?: 0)
    }
}
