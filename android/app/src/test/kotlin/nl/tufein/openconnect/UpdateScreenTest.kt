package nl.tufein.openconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.performClick
import nl.tufein.openconnect.core.updates.UpdateHttp
import nl.tufein.openconnect.data.SettingsStore
import nl.tufein.openconnect.data.SqliteRepository
import nl.tufein.openconnect.watch.FakeBle
import nl.tufein.openconnect.watch.FakeUsb
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** The Settings card drives the real updater against a fake GitHub; no phone or network needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi", application = OpenConnectApp::class)
class UpdateScreenTest : AppTestBase() {
    private val apk = "a tiny fake apk".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    private fun launchWithFakeReleases() {
        val release = """
            {"tag_name":"v0.48.0","body":"notes","assets":[
              {"name":"openconnect-v0.48.0.apk","size":${apk.size},
               "browser_download_url":"https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk"},
              {"name":"openconnect-v0.48.0.apk.sha256",
               "browser_download_url":"https://github.com/Tufein/OpenConnect/releases/download/v0.48.0/openconnect-v0.48.0.apk.sha256"}
            ]}
        """.trimIndent()
        val http = UpdateHttp { url, _ ->
            when {
                url.contains("releases/latest") -> release.toByteArray()
                url.endsWith(".apk") -> apk
                url.endsWith(".sha256") -> "$hash  openconnect-v0.48.0.apk".toByteArray()
                else -> throw IllegalStateException("unexpected URL $url")
            }
        }
        launchFresh { app ->
            app.containerOverride = AppContainer(
                SqliteRepository(app),
                SettingsStore(app, secure = { app.getSharedPreferences("secure-update-test", Context.MODE_PRIVATE) }),
                FakeUsb { error("no watch in this test") },
                FakeBle(),
                updateDir = File(app.cacheDir, "updates"),
                updateHttp = http,
            )
        }
    }

    @Test
    fun settings_card_checks_downloads_and_offers_install() {
        launchWithFakeReleases()
        tab("Settings").performClick()
        clickText("Check for updates") // scrolls the lazy list, then clicks
        waitForText("Version 0.48.0 is available", substring = true)
        clickText("Download and verify")
        waitForText("Install update")
        shot("update-card")
    }
}
