package com.fastgallery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical user journeys record karta hai: cold start, grid scroll, sort/filter, tabs.
 *
 * Chalane ke liye (device/emulator API 28+, gallery me kuch photos/videos hon):
 *   ./gradlew :app:generateBaselineProfile
 * Output app/src/main/generated/baselineProfiles/ me save hota hai -> commit kar do.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        // Permission dialog ke bina seedha gallery khule.
        listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { device.executeShellCommand("pm grant $PACKAGE $it") }

        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Photos")), 5_000)

        // Photos grid scroll
        repeat(3) { swipeUp() }
        repeat(2) { swipeDown() }

        // Sort / filter flow (fix wala path bhi warm hota hai)
        device.findObject(By.text("Sort"))?.click()
        device.wait(Until.hasObject(By.text("Videos")), 2_000)
        device.findObject(By.text("Videos"))?.click()
        device.findObject(By.text("Done"))?.click()
        device.waitForIdle()
        swipeUp()

        // Bottom tabs
        listOf("Albums", "Favorites", "Trash", "Settings", "Photos").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }

        // Viewer: pehla photo kholo aur band karo
        device.click(device.displayWidth / 4, device.displayHeight / 3)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeUp() {
        device.swipe(
            device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 4, 20,
        )
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeDown() {
        device.swipe(
            device.displayWidth / 2, device.displayHeight / 4,
            device.displayWidth / 2, device.displayHeight * 3 / 4, 20,
        )
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.fastgallery.app"
    }
}
