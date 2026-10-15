package com.nuvio.tv.data.mediaserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRepositoryTest {
    @Test
    fun libraryRefreshDoesNotCancelInFlightCallsEvenWhenGenresChange() = runTest {
        val provider = FakeServerProvider()
        val (repository, connection) = fakeServerRepository(provider)
        val release = CompletableDeferred<Unit>()
        val call = async(start = CoroutineStart.UNDISPATCHED) {
            repository.call(connection.id) { _, _ -> release.await(); "loaded" }
        }
        repository.refreshLibraries(connection.id)
        val revision = repository.uiState.value.revision
        repository.refreshLibraries(connection.id)
        assertEquals(revision, repository.uiState.value.revision)
        provider.genres[FakeServerProvider.MOVIE_LIBRARY.id] = listOf(ServerGenre("new", "Family"))
        repository.refreshLibraries(connection.id)
        release.complete(Unit)
        assertEquals("loaded", call.await())
    }

    @Test
    fun accessChangesStillInvalidateInFlightCalls() = runTest {
        for (change in listOf<(ServerRepository, String) -> Unit>(
            { repository, id -> repository.setEnabled(id, false) },
            { repository, _ -> repository.selectProfile(2) },
            { repository, id -> repository.setLibrarySelected(id, FakeServerProvider.MOVIE_LIBRARY.id, false) }
        )) {
            val (repository, connection) = fakeServerRepository(FakeServerProvider())
            val release = CompletableDeferred<Unit>()
            val call = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.call(connection.id) { _, _ -> release.await(); "stale" } }
            }
            change(repository, connection.id)
            release.complete(Unit)
            assertTrue(call.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        }
    }

    @Test
    fun refreshingLibrariesPersistsGenresAndPreservesSelection() = runTest {
        val provider = FakeServerProvider().apply {
            genres[FakeServerProvider.MOVIE_LIBRARY.id] = listOf(ServerGenre("g1", "Akcja"))
        }
        val (repository, connection) = fakeServerRepository(provider)
        repository.setLibrarySelected(connection.id, FakeServerProvider.MOVIE_LIBRARY.id, false)
        repository.refreshLibraries(connection.id)
        val library = repository.connection(connection.id)!!.libraries.first { it.id == FakeServerProvider.MOVIE_LIBRARY.id }
        assertFalse(library.selected)
        assertEquals(listOf(ServerGenre("g1", "Akcja")), library.genres)
        repository.selectProfile(2)
        repository.selectProfile(1)
        assertEquals(library, repository.connection(connection.id)!!.libraries.first { it.id == library.id })
    }

    private val persistence = MemoryServerPersistence()
    private val repository = ServerRepository(
        persistence = persistence,
        providers = emptyList(),
        scope = CoroutineScope(Dispatchers.Unconfined)
    )

    private fun connection(id: String, ref: String) = ServerConnection(
        id = id,
        providerId = "jellyfin",
        name = "Home",
        address = "http://192.168.1.10:8096",
        remoteServerId = "s1",
        remoteUserId = "u1",
        userName = "viewer",
        credentialRef = ref
    )

    @Test
    fun keepsConnectionsAndTokensPerProfile() {
        repository.store(connection("c1", "k1"), "token-one")
        assertEquals("token-one", repository.session("c1")?.token)

        repository.selectProfile(2)
        assertNull(repository.connection("c1"))
        repository.store(connection("c2", "k2"), "token-two")

        repository.selectProfile(1)
        assertEquals(listOf("c1"), repository.uiState.value.connections.map { it.id })
        assertEquals("token-one", repository.session("c1")?.token)
        assertFalse(persistence.values.getValue(1).contains("token-two"))
    }

    @Test
    fun removingAConnectionDropsItsToken() {
        repository.store(connection("c1", "k1"), "token-one")
        repository.remove("c1")

        assertNull(repository.session("c1"))
        assertFalse(persistence.values.getValue(1).contains("token-one"))
    }

    @Test
    fun replacingACredentialKeepsOnlyTheNewToken() {
        repository.store(connection("c1", "k1"), "old")
        repository.store(connection("c1", "k2"), "new", replacedCredential = "k1")

        assertEquals("new", repository.session("c1")?.token)
        assertFalse(persistence.values.getValue(1).contains("old"))
    }

    @Test
    fun profileRemovalAndAccountResetClearStoredServers() {
        repository.store(connection("c1", "k1"), "token-one")
        repository.selectProfile(2)
        repository.store(connection("c2", "k2"), "token-two")

        repository.removeProfile(1)
        assertFalse(persistence.values.containsKey(1))
        assertTrue(persistence.values.containsKey(2))

        repository.clearAllProfiles()
        assertTrue(persistence.values.isEmpty())
        assertTrue(repository.uiState.value.connections.isEmpty())
    }

    @Test
    fun disabledServersHaveNoSession() {
        repository.store(connection("c1", "k1"), "token-one")
        repository.setEnabled("c1", false)

        assertNull(repository.session("c1"))
        assertTrue(repository.enabledConnections().isEmpty())
    }

    @Test
    fun callsReportMissingAndDisabledServers() = runTest {
        repository.store(connection("c1", "k1"), "token-one")
        repository.setEnabled("c1", false)

        val missing = runCatching { repository.call("nope") { _, _ -> 1 } }.exceptionOrNull() as ServerException
        val disabled = runCatching { repository.call("c1") { _, _ -> 1 } }.exceptionOrNull() as ServerException

        assertEquals(ServerFailure.NOT_FOUND, missing.failure)
        assertEquals(ServerFailure.UNREACHABLE, disabled.failure)
    }
}
