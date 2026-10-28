package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val releaseJson = """
        {
          "url": "https://api.github.com/repos/Dimakut228/PlateCam/releases/123",
          "tag_name": "latest",
          "name": "PlateCam (build 123)",
          "body": "PlateCam build 123\n\n- Коммит: abc",
          "assets": [
            {
              "name": "PlateCam.apk",
              "browser_download_url": "https://github.com/Dimakut228/PlateCam/releases/download/latest/PlateCam.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesBuildAndApkUrl() {
        val release = UpdateChecker.parseRelease(releaseJson)
        assertNotNull(release)
        assertEquals(123L, release?.build)
        assertEquals(
            "https://github.com/Dimakut228/PlateCam/releases/download/latest/PlateCam.apk",
            release?.apkUrl
        )
    }

    @Test
    fun ignoresUnknownJson() {
        assertNull(UpdateChecker.parseRelease("""{"name": "без номера"}"""))
        assertNull(UpdateChecker.parseRelease(""))
        // Номер сборки есть, но ссылки на APK нет.
        assertNull(UpdateChecker.parseRelease("""{"name": "PlateCam (build 50)"}"""))
    }

    @Test
    fun newerBuildIsUpdate() {
        assertTrue(UpdateChecker.isNewer(124L, 123L))
        assertFalse(UpdateChecker.isNewer(123L, 123L))
        assertFalse(UpdateChecker.isNewer(122L, 123L))
    }
}
