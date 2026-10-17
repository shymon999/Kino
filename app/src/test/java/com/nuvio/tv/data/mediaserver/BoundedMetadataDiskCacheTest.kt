package com.nuvio.tv.data.mediaserver

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedMetadataDiskCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private var clock = 1_000_000L
    private fun cache(root: File, bytes: Long = 4096, entries: Int = 16, reserve: Long = 0) =
        BoundedMetadataDiskCache(root, bytes, entries, { clock }, reserve)

    @Test fun survivesRestartAndScopesEntriesWithoutPuttingScopeNamesInFilenames() {
        val root = folder.newFolder()
        cache(root).put("private-account", "details:1", "saved")
        val restarted = cache(root)
        assertEquals("saved", restarted.get<String>("private-account", "details:1", String::class.java, 1000)?.value)
        assertNull(restarted.get<String>("other-account", "details:1", String::class.java, 1000))
        assertTrue(root.listFiles()!!.none { "private-account" in it.name || "details" in it.name })
    }

    @Test fun readingDoesNotExtendFreshnessAndExpiredEntriesAreRemoved() {
        val root = folder.newFolder()
        val cache = cache(root)
        cache.put("a", "key", "value")
        clock += 900
        assertNotNull(cache.get<String>("a", "key", String::class.java, 1000))
        clock += 200
        assertNull(cache.get<String>("a", "key", String::class.java, 1000))
        assertEquals(0L, cache.sizeBytes())
    }

    @Test fun protectsLibraryFromPosterEvictionButNeverExceedsEntryLimit() {
        val root = folder.newFolder()
        val cache = cache(root, entries = 2)
        cache.put("a", "library:movies", "index")
        clock++
        cache.put("posters", "one", "one")
        clock++
        cache.put("posters", "two", "two")
        assertEquals(2, root.listFiles()!!.size)
        assertNotNull(cache.get<String>("a", "library:movies", String::class.java, 1000))
        assertNull(cache.get<String>("posters", "one", String::class.java, 1000))
    }

    @Test fun concurrentWritesStayWithinBudget() {
        val root = folder.newFolder()
        val cache = cache(root, bytes = 500, entries = 4)
        val pool = Executors.newFixedThreadPool(4)
        try {
            (0..30).map { id -> pool.submit { cache.put("a", "$id", "value-$id".repeat(10)) } }.forEach { it.get() }
        } finally { pool.shutdown(); pool.awaitTermination(5, TimeUnit.SECONDS) }
        assertTrue(cache.sizeBytes() <= 500)
        assertTrue(root.listFiles()!!.size <= 4)
    }

    @Test fun rejectsOversizeAndLowSpaceWithoutBreakingReads() {
        val root = folder.newFolder()
        cache(root, bytes = 16).put("a", "large", "value")
        cache(root, reserve = Long.MAX_VALUE / 2).put("a", "low-space", "value")
        assertEquals(0L, cache(root).sizeBytes())
    }

    @Test fun corruptEntryFallsBackToMissAndClearOnlyRemovesItsAccount() {
        val root = folder.newFolder()
        val cache = cache(root)
        cache.put("a", "key", "one")
        root.listFiles()!!.single().writeText("broken gzip")
        assertNull(cache.get<String>("a", "key", String::class.java, 1000))
        cache.put("a", "key", "one")
        cache.put("b", "key", "two")
        cache.clear("a")
        assertNull(cache.get<String>("a", "key", String::class.java, 1000))
        assertEquals("two", cache.get<String>("b", "key", String::class.java, 1000)?.value)
    }

    @Test fun revokedWriteCannotRestoreClearedData() {
        val root = folder.newFolder()
        val cache = cache(root)
        cache.put("a", "key", "old")
        cache.clear("a")
        cache.put("a", "key", "late response", allowed = { false })
        assertEquals(0L, cache.sizeBytes())
    }

    @Test fun recentlyReadEntrySurvivesEvictionAndRepeatedHitsDoNotRescanStatistics() {
        val root = folder.newFolder()
        val reports = mutableListOf<Long>()
        val cache = BoundedMetadataDiskCache(root, maxEntries = 2, now = { clock }, report = { bytes, _ -> reports += bytes })
        cache.put("a", "first", "one")
        clock += 100
        cache.put("a", "second", "two")
        clock += 100
        assertNotNull(cache.get<String>("a", "first", String::class.java, 1000))
        val count = reports.size
        assertNotNull(cache.get<String>("a", "first", String::class.java, 1000))
        assertEquals(count, reports.size)
        clock += 100
        cache.put("a", "third", "three")
        assertNotNull(cache.get<String>("a", "first", String::class.java, 1000))
        assertNull(cache.get<String>("a", "second", String::class.java, 1000))
    }
}
