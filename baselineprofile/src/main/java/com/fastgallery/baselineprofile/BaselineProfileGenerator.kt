package com.fastgallery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical user journeys record karta hai: cold start, grid scroll, sort/filter, tabs, settings,
 * select mode, viewer swipe, video, editor.
 *
 * Chalane ke liye (device/emulator API 28+, gallery me kuch photos AUR kam se kam ek video hona chahiye):
 *   ./gradlew :app:generateBaselineProfile
 * Output app/src/main/generated/baselineProfiles/ me save hota hai -> commit kar do.
 * Uske baad app/src/main/baseline-prof.txt (hand-curated starter) hata sakte ho.
 *
 * Har step null-safe hai: koi label na mile (ya screen alag ho) to journey rukti nahi, aage badh jaati hai.
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

        // 1) Photos grid scroll + fast scroll wapas upar
        repeat(3) { swipeUp() }
        repeat(2) { swipeDown() }

        // 2) Select mode: long-press -> Select all -> back
        longPress(device.displayWidth / 4, device.displayHeight / 3)
        device.wait(Until.hasObject(By.desc("Select all")), 2_000)
        device.findObject(By.desc("Select all"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // 3) Sort / filter dialog (content-description "Sort and filter"): Videos filter lagao
        openFilterDialog()
        device.findObject(By.text("Videos"))?.click()
        device.findObject(By.text("Done"))?.click()
        device.waitForIdle()

        // 4) Video: pehla video kholo (ExoPlayer init + gesture layer warm), thoda ruko, wapas
        device.click(device.displayWidth / 4, device.displayHeight / 3)
        device.waitForIdle()
        Thread.sleep(2_000)
        device.click(device.displayWidth / 2, device.displayHeight / 2) // controls toggle
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // 5) Filter wapas "All media"
        openFilterDialog()
        device.findObject(By.text("All media"))?.click()
        device.findObject(By.text("Done"))?.click()
        device.waitForIdle()

        // 6) Bottom tabs + settings scroll
        listOf("Albums", "Favorites", "Trash", "Settings").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
        swipeUp()
        swipeDown()
        device.findObject(By.text("Albums"))?.click()
        device.waitForIdle()
        swipeUp()
        device.findObject(By.text("Photos"))?.click()
        device.waitForIdle()

        // 6b) Tab swipe: Photos -> Albums -> wapas (TabSwipeContainer ka drag + commit + slide-in path)
        swipeLeft()
        swipeRight()

        // 7) Viewer: photo kholo, pager swipe (agla/pichhla), double-tap zoom, details/edit
        device.click(device.displayWidth / 4, device.displayHeight / 3)
        device.waitForIdle()
        repeat(2) { swipeLeft() }
        swipeRight()
        doubleTap(device.displayWidth / 2, device.displayHeight / 2)
        doubleTap(device.displayWidth / 2, device.displayHeight / 2)

        // 8) Editor: Edit -> Adjust -> Filters -> Crop, phir bina save kiye band
        device.findObject(By.desc("Edit"))?.click()
        if (device.wait(Until.hasObject(By.text("Adjust")), 2_000) == true) {
            listOf("Adjust", "Filters", "Crop").forEach { tab ->
                device.findObject(By.text(tab))?.click()
                device.waitForIdle()
            }
            device.pressBack()
            device.waitForIdle()
        }

        // 9) Copy/move album picker: More options -> Copy / move -> band
        device.findObject(By.desc("More options"))?.click()
        device.wait(Until.hasObject(By.text("Copy / move")), 1_500)
        device.findObject(By.text("Copy / move"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // 10) Viewer band (swipe-down-to-close wala path bhi warm)
        swipeDown()
        device.pressBack()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openFilterDialog() {
        device.findObject(By.desc("Sort and filter"))?.click()
        device.wait(Until.hasObject(By.text("Done")), 2_000)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.longPress(x: Int, y: Int) {
        // Same point par dheere "swipe" = long-press (steps * ~5ms).
        device.swipe(x, y, x, y, 160)
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.doubleTap(x: Int, y: Int) {
        device.click(x, y)
        device.click(x, y)
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeLeft() {
        device.swipe(
            device.displayWidth * 4 / 5, device.displayHeight / 2,
            device.displayWidth / 5, device.displayHeight / 2, 12,
        )
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeRight() {
        device.swipe(
            device.displayWidth / 5, device.displayHeight / 2,
            device.displayWidth * 4 / 5, device.displayHeight / 2, 12,
        )
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
