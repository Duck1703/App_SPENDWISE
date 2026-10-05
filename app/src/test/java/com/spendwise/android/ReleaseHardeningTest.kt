package com.spendwise.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7 hardening regression guard: the release build relies on reflective
 * kotlinx-serialization lookup (`serializer(Type)` via the Retrofit
 * converter), which resolves generated `$$serializer` classes and
 * `Companion.serializer()` members BY NAME. If the keep rules below are
 * ever removed from proguard-rules.pro, `assembleRelease` still passes —
 * but every API call crashes at runtime in the minified artifact.
 */
class ReleaseHardeningTest {

    private val rules: String by lazy {
        // Gradle runs unit tests with the module dir as working dir.
        File("proguard-rules.pro").readText()
    }

    @Test
    fun `release rules keep generated serializer class names`() {
        assertTrue(
            "missing \$\$serializer keep",
            rules.contains(
                "-keep,includedescriptorclasses class " +
                    "com.spendwise.android.**\$\$serializer",
            ),
        )
    }

    @Test
    fun `release rules keep serializable Companion members`() {
        assertTrue(
            "missing Companion keep",
            rules.contains("*** Companion;") &&
                rules.contains("@kotlinx.serialization.Serializable"),
        )
    }

    @Test
    fun `release rules keep serializer() factories`() {
        assertTrue(
            "missing serializer() keep",
            rules.contains("kotlinx.serialization.KSerializer serializer(...)"),
        )
    }
}
