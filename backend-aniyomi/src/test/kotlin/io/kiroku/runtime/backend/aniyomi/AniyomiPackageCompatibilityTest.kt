package io.kiroku.runtime.backend.aniyomi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniyomiPackageCompatibilityTest {

    @Test
    fun `library versions inside the supported window are accepted`() {
        listOf("12.1.0", "13.0.0", "14.2.1", "16.5.2", "17.0.1").forEach { version ->
            assertTrue(version, AniyomiPackage.isSupportedLibVersion(version))
        }
    }

    @Test
    fun `older, newer and missing library versions are rejected`() {
        listOf("11.9.0", "18.0.0", "0.1.0").forEach { version ->
            assertFalse(version, AniyomiPackage.isSupportedLibVersion(version))
        }
        assertFalse(AniyomiPackage.isSupportedLibVersion(null))
    }
}
