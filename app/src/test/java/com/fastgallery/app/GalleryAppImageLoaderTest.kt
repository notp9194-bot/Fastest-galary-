package com.fastgallery.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GalleryAppImageLoaderTest {
    private val loader get() = (RuntimeEnvironment.getApplication() as GalleryApp).newImageLoader()

    /** Disk cache koi thumbnail nahi bharta tha (ThumbFetcher use nahi karta); default cache bhi na bane. */
    @Test
    fun diskCache_isDisabled() {
        assertNull(loader.diskCache)
    }

    @Test
    fun memoryCache_isPresent() {
        assertNotNull(loader.memoryCache)
    }
}
