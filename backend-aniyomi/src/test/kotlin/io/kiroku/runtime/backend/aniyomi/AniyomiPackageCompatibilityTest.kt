package io.kiroku.runtime.backend.aniyomi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniyomiPackageCompatibilityTest {

    @Test
    fun `anime library versions supported by Animiru are accepted`() {
        listOf("14.2.1", "16.59", "17.0.1").forEach { version ->
            assertTrue(version, AniyomiPackage.isSupportedLibVersion(version))
        }
    }

    @Test
    fun `older, unsupported and missing anime library versions are rejected`() {
        listOf("11.9.0", "12.0.0", "13.4.0", "15.1.0", "18.0.0", "0.1.0").forEach { version ->
            assertFalse(version, AniyomiPackage.isSupportedLibVersion(version))
        }
        assertFalse(AniyomiPackage.isSupportedLibVersion(null))
    }

    @Test
    fun `manga library versions follow the Mihon window`() {
        listOf("1.4", "1.5", "1.6").forEach { version ->
            assertTrue(version, AniyomiPackage.isMangaLibVersionSupported(version))
        }
        listOf<String?>("1.3", "1.7", null).forEach { version ->
            assertFalse(version ?: "null", AniyomiPackage.isMangaLibVersionSupported(version))
        }
    }

    @Test
    fun `the two library windows stay separate`() {
        assertTrue(AniyomiPackage.isMangaLibVersionSupported("1.6"))
        assertFalse(AniyomiPackage.isMangaLibVersionSupported("14.0.0"))
        assertFalse(AniyomiPackage.isMangaLibVersionSupported(null))
        assertTrue(AniyomiPackage.isSupportedLibVersion("14.0.0"))
        assertFalse(AniyomiPackage.isSupportedLibVersion("1.6.0"))
        assertEquals(listOf(1.4, 1.5, 1.6), AniyomiPackage.SUPPORTED_MANGA_LIB_VERSIONS)
        assertEquals(listOf(14, 16, 17), AniyomiPackage.SUPPORTED_ANIME_LIB_VERSIONS)
    }
}
