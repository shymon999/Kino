package com.nuvio.tv.data.mediaserver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class FamilyCredentials(val address: String, val username: String, val password: String) {
    override fun toString(): String = "FamilyCredentials(redacted)"
}

data class FamilySetupState(val loading: Boolean = true, val failure: ServerFailure? = null)

/** First-run sign-in uses this installation's client identity, never a copied token. */
class FamilySetupCoordinator(
    private val repository: ServerRepository,
    private val credentials: FamilyCredentials,
    private val applyDefaults: suspend () -> Unit,
    private val timeoutMs: Long = 60_000
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FamilySetupState())
    val state = mutableState.asStateFlow()

    suspend fun prepare() = mutex.withLock {
        mutableState.value = FamilySetupState()
        // A saved session opens the app at once. Only a renewal that fails afterwards blocks it.
        var resumed = false
        var renewing = false
        try {
            withTimeout(timeoutMs) {
                applyDefaults()
                repository.ensureLoaded()
                // Respect an existing setup and its local history across updates.
                val existing = repository.enabledConnections().firstOrNull { connection ->
                    connection.providerId == "emby" && repository.session(connection.id) != null
                }
                val needsSignIn = if (existing == null) true else {
                    resumed = true
                    mutableState.value = FamilySetupState(loading = false)
                    try {
                        repository.call(existing.id) { provider, session -> provider.libraries(session) }
                        false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // An offline or slow server keeps the saved session; Home reports its own errors.
                        if (error !is ServerException || error.failure != ServerFailure.AUTH_REQUIRED) false
                        else if (existing.userName == credentials.username &&
                            existing.address.trimEnd('/') == credentials.address.trimEnd('/')) true
                        else throw error
                    }
                }
                if (needsSignIn) {
                    renewing = resumed
                    if (credentials.address.isBlank() || credentials.username.isBlank() || credentials.password.isBlank()) {
                        throw ServerException(ServerFailure.INCOMPLETE)
                    }
                    val provider = repository.providers.firstOrNull { it.id == "emby" }
                        ?: throw ServerException(ServerFailure.UNSUPPORTED)
                    repository.connect(provider, credentials.address, credentials.username, credentials.password)
                }
            }
            mutableState.value = FamilySetupState(loading = false)
        } catch (_: TimeoutCancellationException) {
            if (!resumed || renewing) {
                mutableState.value = FamilySetupState(loading = false, failure = ServerFailure.UNREACHABLE)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = FamilySetupState(loading = false, failure = error.serverFailure())
        }
    }
}
