package dev.robertmichalak.aaactionbuttons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerAllowlistTest {
    @Test
    fun debugBuildAllowsRandomPackage() {
        assertTrue(
            CallerAllowlist.allows(
                callerPackage = "com.random.package",
                selfPackage = SELF,
                debugBuild = true,
            ),
        )
    }

    @Test
    fun releaseAllowsSelfAndAllowlistPackages() {
        assertTrue(
            CallerAllowlist.allows(
                callerPackage = SELF,
                selfPackage = SELF,
                debugBuild = false,
            ),
        )
        val expected = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.autosimulator",
            "com.android.car.media",
            "com.google.android.car.media",
        )
        assertEquals(expected, CallerAllowlist.packages)
        expected.forEach { pkg ->
            assertTrue(
                CallerAllowlist.allows(
                    callerPackage = pkg,
                    selfPackage = SELF,
                    debugBuild = false,
                ),
            )
        }
    }

    @Test
    fun releaseRejectsOtherPackageAndEmptyString() {
        assertFalse(
            CallerAllowlist.allows(
                callerPackage = "com.example.other",
                selfPackage = SELF,
                debugBuild = false,
            ),
        )
        assertFalse(
            CallerAllowlist.allows(
                callerPackage = "",
                selfPackage = SELF,
                debugBuild = false,
            ),
        )
    }

    private companion object {
        const val SELF = "dev.robertmichalak.aaactionbuttons"
    }
}
