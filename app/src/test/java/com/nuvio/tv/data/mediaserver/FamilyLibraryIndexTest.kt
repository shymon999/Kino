package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.data.mediaserver.emby.FamilyLibraryIndex
import com.nuvio.tv.domain.model.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FamilyLibraryIndexTest {
    @get:Rule val folder = TemporaryFolder()
    private val library = ServerLibrary("movies", "Filmy", ServerMediaKind.MOVIE)
    private val session = ServerSession(ServerConnection("c1", "emby", "Home", "https://media.example.com", "s1", "u1", "viewer", "k1"), "secret")
    private fun title(id: String) = ServerTitle(MetaPreview("srv1:c1:$id", ContentType.MOVIE, name = id,
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = listOf("Drama")), peopleNames = listOf("Arnold Schwarzenegger"))

    @Test fun restartUsesSavedIndexAndKeepsFullSearchFields() = runBlocking<Unit> {
        val disk = BoundedMetadataDiskCache(folder.newFolder())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = FamilyLibraryIndex(disk, scope, "seed", { _, _, _ -> ServerPage(listOf(title("one")), 1) })
            assertEquals(1, first.titles(session, library).size)
            val restarted = FamilyLibraryIndex(disk, scope, "seed", { _, _, _ -> error("Network must not be called") })
            assertEquals(listOf("Arnold Schwarzenegger"), restarted.titles(session, library).single().peopleNames)
            val other = ServerSession(session.connection.copy(credentialRef = "other"), "other-token")
            assertTrue(runCatching { restarted.titles(other, library) }.isFailure)
        } finally { scope.cancel() }
    }

    @Test fun backgroundRefreshNeverBlocksCachedReadsAndKeepsPaginationPinned() = runBlocking<Unit> {
        val now = AtomicLong(1_000_000)
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = BoundedMetadataDiskCache(folder.newFolder(), now = now::get)
        try {
            val index = FamilyLibraryIndex(disk, scope, "seed", { _, _, _ ->
                if (calls.incrementAndGet() > 1) {
                    entered.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    ServerPage((1..4).map { title("$it") }, 4)
                } else ServerPage((1..3).map { title("$it") }, 3)
            }, now::get)
            val original = index.page(session, library, "drama", 0, 3).items
            now.addAndGet(16 * 60_000L)
            assertEquals(3, withTimeout(500) { index.titles(session, library) }.size)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(3, withTimeout(500) { index.titles(session, library) }.size)
            release.countDown()
            withTimeout(3000) { while (index.titles(session, library).size != 4) delay(10) }
            assertEquals(original.drop(1), index.page(session, library, "drama", 1, 3).items)
            assertEquals(4, index.page(session, library, "drama", 0, 10).items.size)
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun logoutDuringFetchCannotRepopulateDisk() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = BoundedMetadataDiskCache(folder.newFolder())
        try {
            val index = FamilyLibraryIndex(disk, scope, "seed", { _, _, _ ->
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                ServerPage(listOf(title("one")), 1)
            })
            val pending = async(Dispatchers.Default) { index.titles(session, library) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            index.clear(session)
            release.countDown()
            assertTrue(runCatching { pending.await() }.exceptionOrNull() is CancellationException)
            assertEquals(0L, disk.sizeBytes())
        } finally { release.countDown(); scope.cancel() }
    }
}
