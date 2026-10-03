package com.fastgallery.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grid scroll ka frame time (BOM upgrade, thumbnail prefetch, ThumbFetcher parallel limit ka asar naapne ke liye).
 * Device/emulator par kam se kam ~200 photos chahiye. Chalane ke liye:
 *   ./gradlew :baselineprofile:connectedBenchmarkAndroidTest
 * Results me frameDurationCpuMs P50/P90/P95/P99 aur frameOverrunMs dekho. Pehle BASELINE (purana build) ka number
 * note karo, phir naya: jo badlav naapne me na dikhe use rakhne ka koi faida nahi.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollSlowWithBaselineProfile() = scroll(steps = 60)

    /** Zyada steps = dheemi swipe (prefetch/dheere scroll path); kam steps = fling jaisa tez (deferLoad path). */
    @Test
    fun scrollFastWithBaselineProfile() = scroll(steps = 8)

    private fun scroll(steps: Int) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_EXTERNAL_STORAGE",
            ).forEach { device.executeShellCommand("pm grant $PACKAGE $it") }
            pressHome()
            startActivityAndWait()
        },
    ) {
        val x = device.displayWidth / 2
        repeat(4) {
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, steps)
            device.waitForIdle()
        }
        repeat(4) {
            device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, steps)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.fastgallery.app"
    }
}
