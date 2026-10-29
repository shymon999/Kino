package com.nuvio.tv.data.mediaserver

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilySetupCoordinatorTest {
    private val credentials = FamilyCredentials("https://family.example", "viewer", "private-password")

    private class ProvisionProvider(private val deviceToken: String = "device-token") : ServerProvider by FakeServerProvider() {
        override val id = "emby"
        override val deviceLocalWatchState = true
        var attempts = 0
        var failure: ServerFailure? = null
        var delayMs = 0L
        var nextValidationFailure: ServerFailure? = null
        var validationDelayMs = 0L
        override suspend fun libraries(session: ServerSession): List<ServerLibrary> {
            delay(validationDelayMs)
            nextValidationFailure?.let { failure ->
                nextValidationFailure = null
                throw ServerException(failure)
            }
            return listOf(FakeServerProvider.MOVIE_LIBRARY, FakeServerProvider.SERIES_LIBRARY)
        }
        override suspend fun signIn(address: String, username: String, password: String): ServerSignIn {
            attempts++
            delay(delayMs)
            failure?.let { throw ServerException(it, "sensitive response") }
            return ServerSignIn(address, "Family server", "server-id", "user-id", username, deviceToken)
        }
    }

    @Test fun firstRunConnectsOnceAndRepeatedSetupKeepsLocalIdentity() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {})
        setup.prepare()
        val first = repository.enabledConnections().single()
        setup.prepare()
        assertEquals(1, provider.attempts)
        assertEquals(first.id, repository.enabledConnections().single().id)
        assertEquals("device-token", repository.session(first.id)?.token)
        assertFalse(first.importWatchState)
        assertFalse(setup.state.value.loading)
        assertNull(setup.state.value.failure)
    }

    @Test fun unavailableServerHasPersistentFailureAndRetryCanRecover() = runTest {
        val provider = ProvisionProvider().apply { failure = ServerFailure.UNREACHABLE }
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {})
        setup.prepare()
        assertEquals(ServerFailure.UNREACHABLE, setup.state.value.failure)
        assertTrue(repository.enabledConnections().isEmpty())
        provider.failure = null
        setup.prepare()
        assertEquals(2, provider.attempts)
        assertNull(setup.state.value.failure)
        assertEquals(1, repository.enabledConnections().size)
    }

    @Test fun authenticationFailureDoesNotExposeCredentialsOrStoreConnection() = runTest {
        val provider = ProvisionProvider().apply { failure = ServerFailure.AUTH_REQUIRED }
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {})
        setup.prepare()
        assertEquals(ServerFailure.AUTH_REQUIRED, setup.state.value.failure)
        assertFalse(setup.state.value.toString().contains("sensitive"))
        assertFalse(credentials.toString().contains(credentials.password))
        assertTrue(repository.enabledConnections().isEmpty())
    }

    @Test fun slowSetupTimesOutInsteadOfLoadingForever() = runTest {
        val provider = ProvisionProvider().apply { delayMs = 2_000 }
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {}, timeoutMs = 100)
        setup.prepare()
        assertFalse(setup.state.value.loading)
        assertEquals(ServerFailure.UNREACHABLE, setup.state.value.failure)
    }

    @Test fun twoInstallationsUseSeparateFreshSessionsAndLocalConnections() = runTest {
        val first = ServerRepository(MemoryServerPersistence(), listOf(ProvisionProvider("token-a")), backgroundScope)
        val second = ServerRepository(MemoryServerPersistence(), listOf(ProvisionProvider("token-b")), backgroundScope)
        FamilySetupCoordinator(first, credentials, {}).prepare()
        FamilySetupCoordinator(second, credentials, {}).prepare()
        val a = first.enabledConnections().single()
        val b = second.enabledConnections().single()
        assertEquals(a.remoteUserId, b.remoteUserId)
        assertNotEquals(a.id, b.id)
        assertNotEquals(first.session(a.id)?.token, second.session(b.id)?.token)
    }

    @Test fun incompletePrivateBuildDoesNotAttemptSignIn() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, FamilyCredentials("", "", ""), {})
        setup.prepare()
        assertEquals(ServerFailure.INCOMPLETE, setup.state.value.failure)
        assertEquals(0, provider.attempts)
    }

    @Test fun rejectedSavedSessionIsRenewedWithoutChangingTheLocalConnectionId() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {})
        setup.prepare()
        val original = repository.enabledConnections().single()
        provider.nextValidationFailure = ServerFailure.AUTH_REQUIRED
        setup.prepare()
        val renewed = repository.enabledConnections().single()
        assertEquals(2, provider.attempts)
        assertEquals(original.id, renewed.id)
        assertNotEquals(original.credentialRef, renewed.credentialRef)
        assertNull(setup.state.value.failure)
    }

    @Test fun networkFailureKeepsSavedSessionWithoutBlockingStartup() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {})
        setup.prepare()
        val original = repository.enabledConnections().single()
        provider.nextValidationFailure = ServerFailure.UNREACHABLE
        setup.prepare()
        assertEquals(1, provider.attempts)
        assertEquals(original, repository.enabledConnections().single())
        assertFalse(setup.state.value.loading)
        assertNull(setup.state.value.failure)
    }

    @Test fun savedSessionOpensBeforeSlowServerCheckFinishes() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        val setup = FamilySetupCoordinator(repository, credentials, {}, timeoutMs = 1_000)
        setup.prepare()
        provider.validationDelayMs = 5_000
        val check = launch { setup.prepare() }
        runCurrent()
        assertFalse(setup.state.value.loading)
        check.join()
        assertNull(setup.state.value.failure)
        assertEquals(1, provider.attempts)
    }

    @Test fun rejectedSessionForDifferentAccountStillReportsAuthentication() = runTest {
        val provider = ProvisionProvider()
        val repository = ServerRepository(MemoryServerPersistence(), listOf(provider), backgroundScope)
        FamilySetupCoordinator(repository, credentials, {}).prepare()
        provider.nextValidationFailure = ServerFailure.AUTH_REQUIRED
        val changed = FamilyCredentials(credentials.address, "another-viewer", credentials.password)
        val setup = FamilySetupCoordinator(repository, changed, {})
        setup.prepare()
        assertEquals(1, provider.attempts)
        assertEquals(ServerFailure.AUTH_REQUIRED, setup.state.value.failure)
    }
}
