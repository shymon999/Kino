package com.nuvio.tv.data.mediaserver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UniversalPlaybackLimitTest {
    @Test fun unlimitedIsPreservedDuringFallbackAndAudioChanges() = runTest {
        val provider = FakeServerProvider(transcodingFallback = true)
        val (repository, connection) = fakeServerRepository(provider)
        val playback = ServerPlayback(repository, backgroundScope, clock = { testScheduler.currentTime }, configuredBitrate = { null })
        val first = playback.prepare(ServerPlaybackTarget(ServerItemRef(connection.id, "7"), "src-7"))
        assertNull(provider.playbackRequests.last().capabilities.maxStreamingBitrate)
        val fallback = playback.fallback(first.url)!!
        assertNull(provider.playbackRequests.last().capabilities.maxStreamingBitrate)
        assertNotNull(playback.switchAudio(fallback.url, 2))
        assertEquals(2, provider.playbackRequests.last().audioStreamIndex)
        assertNull(provider.playbackRequests.last().capabilities.maxStreamingBitrate)
    }

    @Test fun explicitLimitIsReadForEveryNewPlaybackAndSurvivesRestart() = runTest {
        val provider = FakeServerProvider(transcodes = true)
        val (repository, connection) = fakeServerRepository(provider)
        var limit: Long? = 80_000_000L
        val playback = ServerPlayback(repository, backgroundScope, clock = { testScheduler.currentTime }, configuredBitrate = { limit })
        val first = playback.prepare(ServerPlaybackTarget(ServerItemRef(connection.id, "7"), "src-7"))
        assertNotNull(playback.switchAudio(first.url, 2))
        assertEquals(80_000_000L, provider.playbackRequests.last().capabilities.maxStreamingBitrate)
        limit = 20_000_000L
        playback.prepare(ServerPlaybackTarget(ServerItemRef(connection.id, "8"), "src-8"))
        assertEquals(20_000_000L, provider.playbackRequests.last().capabilities.maxStreamingBitrate)
    }
}
