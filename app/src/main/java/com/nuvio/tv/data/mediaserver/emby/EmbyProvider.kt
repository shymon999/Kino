package com.nuvio.tv.data.mediaserver.emby

import com.nuvio.tv.data.mediaserver.mediabrowser.Endpoint
import com.nuvio.tv.data.mediaserver.mediabrowser.MediaBrowserProvider
import com.nuvio.tv.data.mediaserver.mediabrowser.PublicInfo
import com.nuvio.tv.data.mediaserver.mediabrowser.ServerClientIdentity
import com.nuvio.tv.data.mediaserver.ServerLibrary
import com.nuvio.tv.data.mediaserver.ServerMediaKind
import com.nuvio.tv.data.mediaserver.ServerSession
import com.nuvio.tv.data.mediaserver.ServerTitle
import com.nuvio.tv.data.mediaserver.fallbackServerSearchTerms
import com.nuvio.tv.data.mediaserver.serverSearchScore
import kotlinx.coroutines.withTimeoutOrNull
import com.nuvio.tv.data.mediaserver.mediabrowser.pathSegment
import okhttp3.OkHttpClient

internal class EmbyProvider(
    http: OkHttpClient,
    identity: ServerClientIdentity
) : MediaBrowserProvider(authorizationHeader = "X-Emby-Authorization", apiPath = "/emby", http = http, identity = identity) {
    override val id: String = "emby"
    override val displayName: String = "Emby"
    override val minimumVersion: String = "4.7"
    override val deviceLocalWatchState: Boolean get() = true
    override val listFields: String = "${super.listFields},CommunityRating"

    override suspend fun search(
        session: ServerSession,
        kind: ServerMediaKind,
        libraries: List<ServerLibrary>,
        query: String,
        limit: Int
    ): List<ServerTitle> {
        if (libraries.isEmpty() || limit <= 0) return emptyList()
        val exact = super.search(session, kind, libraries, query, limit)
        if (exact.isNotEmpty() || query.length > 160) return exact
        return withTimeoutOrNull(6_000) {
            for (term in fallbackServerSearchTerms(query)) {
                val candidates = super.search(session, kind, libraries, term, limit.coerceAtMost(50))
                val ranked = candidates.map { it to serverSearchScore(query, it.preview.name) }
                    .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
                    .distinctBy { it.preview.id }.take(limit)
                if (ranked.isNotEmpty()) return@withTimeoutOrNull ranked
            }
            emptyList()
        } ?: emptyList()
    }

    override fun isSupported(info: PublicInfo): Boolean =
        info.productName?.contains("Jellyfin", ignoreCase = true) != true && isSupportedVersion(info.version)

    // A single seed per process keeps pagination stable; a cold launch gets a fresh order.
    override fun librarySortQuery(library: com.nuvio.tv.data.mediaserver.ServerLibrary): Map<String, String> =
        if (library.kind == com.nuvio.tv.data.mediaserver.ServerMediaKind.MOVIE ||
            library.kind == com.nuvio.tv.data.mediaserver.ServerMediaKind.SERIES) {
            mapOf("sortBy" to "Random", "sortOrder" to "Ascending", "randomSeed" to browsingSeed)
        } else super.librarySortQuery(library)

    private companion object {
        val browsingSeed = kotlin.random.Random.nextInt(1, Int.MAX_VALUE).toString()
    }

    override fun viewsEndpoint(userId: String) = Endpoint("/Users/${pathSegment(userId)}/Views")

    override fun itemsEndpoint(userId: String) = Endpoint("/Users/${pathSegment(userId)}/Items")

    override fun itemEndpoint(userId: String, itemId: String) =
        Endpoint("/Users/${pathSegment(userId)}/Items/${pathSegment(itemId)}")

    override fun resumeEndpoint(userId: String) = Endpoint("/Users/${pathSegment(userId)}/Items/Resume")

    override fun playedEndpoint(userId: String, itemId: String) =
        Endpoint("/Users/${pathSegment(userId)}/PlayedItems/${pathSegment(itemId)}")
}
