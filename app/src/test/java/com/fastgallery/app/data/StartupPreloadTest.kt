package com.fastgallery.app.data

import android.app.Application
import com.fastgallery.app.testItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StartupPreloadTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        FirstPageCache.shared(app).clear()
        StartupPreload.consume() // pichhla (agar koi) one-shot task saaf
    }

    @After
    fun tearDown() {
        FirstPageCache.shared(app).clear()
        StartupPreload.consume()
    }

    @Test
    fun read_withoutCache_isNull() {
        assertNull(StartupPreload.read(app))
    }

    @Test
    fun read_returnsSavedItems() {
        FirstPageCache.shared(app).save(listOf(testItem(1, bucketId = 5L), testItem(2)))
        val snapshot = StartupPreload.read(app)
        assertNotNull(snapshot)
        assertEquals(listOf(1L, 2L), snapshot!!.items.map { it.id })
        assertEquals(5L, snapshot.items[0].bucketId)
    }

    @Test
    fun start_thenConsume_givesResultOnce() {
        FirstPageCache.shared(app).save(listOf(testItem(3)))
        StartupPreload.start(app)
        val task = StartupPreload.consume()
        assertNotNull(task)
        assertEquals(listOf(3L), task!!.get()!!.items.map { it.id })
        assertNull(StartupPreload.consume()) // one-shot
    }

    @Test
    fun start_twice_keepsSameTask() {
        StartupPreload.start(app)
        StartupPreload.start(app)
        val first = StartupPreload.consume()
        assertNotNull(first)
        assertNull(StartupPreload.consume())
    }

    @Test
    fun sharedCache_isSingleton() {
        assertSame(FirstPageCache.shared(app), FirstPageCache.shared(app))
    }
}
