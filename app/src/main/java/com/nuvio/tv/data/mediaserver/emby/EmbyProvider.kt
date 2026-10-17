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
import com.nuvio.tv.data.mediaserver.ServerSearchMatcher
import com.nuvio.tv.data.mediaserver.primaryServerGenre
import com.nuvio.tv.data.mediaserver.canonicalServerGenre
import com.nuvio.tv.data.mediaserver.ServerGenre
import com.nuvio.tv.data.mediaserver.ServerPage
import com.nuvio.tv.data.mediaserver.ServerException
import com.nuvio.tv.data.mediaserver.ServerFailure
import com.nuvio.tv.data.mediaserver.BoundedMetadataDiskCache
import com.nuvio.tv.data.mediaserver.ServerItemDetails
import com.nuvio.tv.domain.model.PersonDetail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap
import com.nuvio.tv.data.mediaserver.mediabrowser.pathSegment
import okhttp3.OkHttpClient

internal class EmbyProvider(
    http: OkHttpClient,
    identity: ServerClientIdentity,
    private val disk: BoundedMetadataDiskCache? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    now: () -> Long = System::currentTimeMillis,
    private val familyCuration: Boolean = true
) : MediaBrowserProvider(authorizationHeader = "X-Emby-Authorization", apiPath = "/emby", http = http, identity = identity) {
    override val id: String = "emby"
    override val displayName: String = "Emby"
    override val minimumVersion: String = "4.7"
    override val deviceLocalWatchState: Boolean get() = true
    override val listFields: String = "${super.listFields},CommunityRating,OriginalTitle,People"

    private data class LibrarySnapshot(val loadedAt: Long, val titles: List<ServerTitle>)
    private val indexLock = Mutex()
    private val libraryIndex = mutableMapOf<String, LibrarySnapshot>()
    private val indexLoads = ConcurrentHashMap<String, Mutex>()
    private val revoked = ConcurrentHashMap.newKeySet<String>()
    private val localIndex = disk?.let { cache -> FamilyLibraryIndex(cache, scope, browsingSeed,
        { session, library, offset -> super.libraryPage(session, library, offset, 200, null) }, now) }

    private suspend fun indexedTitles(session: ServerSession, library: ServerLibrary): List<ServerTitle> = withContext(Dispatchers.Default) {
        localIndex?.let { return@withContext it.titles(session, library) }
        val key = "${session.connection.credentialRef}:${library.id}"
        indexLoads.getOrPut(key) { Mutex() }.withLock {
            val now = System.nanoTime() / 1_000_000
            val cached = indexLock.withLock { libraryIndex[key] }
            cached?.takeIf { now - it.loadedAt < 15 * 60_000L }?.let { return@withLock it.titles }
            val titles = withTimeoutOrNull(60_000L) {
                val result = mutableListOf<ServerTitle>()
                val seen = mutableSetOf<String>()
                var offset = 0
                while (true) {
                    val page = super.libraryPage(session, library, offset, 200, null)
                    val added = page.items.filter { seen.add(it.preview.id) }
                    if (page.items.isNotEmpty() && added.isEmpty()) throw ServerException(ServerFailure.FAILED)
                    result += added
                    offset += page.items.size
                    if (page.items.isEmpty() || (page.totalCount?.let { offset >= it } ?: (page.items.size < 200))) break
                    if (offset > 100_000) throw ServerException(ServerFailure.FAILED)
                }
                result.distinctBy { it.preview.id }
            } ?: throw ServerException(ServerFailure.UNREACHABLE)
            // Bound memory across profile/account changes. Never serve one account's cache to another.
            indexLock.withLock {
                if (libraryIndex.size >= 24 || libraryIndex.values.sumOf { it.titles.size } + titles.size > 20_000) libraryIndex.clear()
                libraryIndex[key] = LibrarySnapshot(System.nanoTime() / 1_000_000, titles)
            }
            titles
        }
    }

    override suspend fun signOut(session: ServerSession) {
        revoked.add(FamilyLibraryIndex.cacheScope(session))
        localIndex?.clear(session)
        indexLock.withLock { libraryIndex.keys.removeAll { it.startsWith("${session.connection.credentialRef}:") } }
        indexLoads.keys.removeAll { it.startsWith("${session.connection.credentialRef}:") }
        super.signOut(session)
    }

    override suspend fun libraryGenres(session: ServerSession, library: ServerLibrary): List<ServerGenre> {
        if (!familyCuration) return super.libraryGenres(session, library)
        if (library.kind != ServerMediaKind.MOVIE && library.kind != ServerMediaKind.SERIES) return super.libraryGenres(session, library)
        val original = super.libraryGenres(session, library)
        val counts = indexedTitles(session, library).groupingBy { primaryServerGenre(it, library.kind) }.eachCount()
        return counts.filterValues { it >= 4 }.keys.sorted().filterNot {
            it in setOf("children", "anime", "sport") || (library.kind == ServerMediaKind.SERIES && it in setOf("animation", "musical", "music"))
        }.map { genre ->
            ServerGenre(original.filter { canonicalServerGenre(it.name) == genre }.minByOrNull { it.id }?.id ?: "primary:$genre", genre)
        }
    }

    override suspend fun libraryPage(session: ServerSession, library: ServerLibrary, start: Int, limit: Int, genreId: String?): ServerPage<ServerTitle> {
        if (!familyCuration && genreId?.startsWith("primary:") != true) return super.libraryPage(session, library, start, limit, genreId)
        if (genreId == null) return super.libraryPage(session, library, start, limit, null)
        val genre = library.genres.filter { it.id in genreId.split(',') }.map { canonicalServerGenre(it.name) }.distinct().singleOrNull()
            ?: genreId.removePrefix("primary:").takeIf { genreId.startsWith("primary:") }
            ?: return super.libraryPage(session, library, start, limit, genreId)
        localIndex?.let { return it.page(session, library, genre, start, limit) }
        val titles = indexedTitles(session, library).filter { primaryServerGenre(it, library.kind) == genre }
            .sortedBy { it.preview.id }.shuffled(kotlin.random.Random((browsingSeed + library.id + genre).hashCode()))
        return ServerPage(titles.drop(start).take(limit), titles.size)
    }

    override suspend fun search(
        session: ServerSession,
        kind: ServerMediaKind,
        libraries: List<ServerLibrary>,
        query: String,
        limit: Int
    ): List<ServerTitle> {
        if (libraries.isEmpty() || limit <= 0) return emptyList()
        val localIndexes = if (localIndex != null) withContext(Dispatchers.Default) {
            withTimeoutOrNull(20_000) {
                try {
                    coroutineScope { libraries.map { async { localIndex.search(session, it) } }.awaitAll() }
                } catch (error: kotlinx.coroutines.CancellationException) { throw error
                } catch (_: ServerException) { null }
            }
        } else null
        val localTitles = localIndexes?.let { indexes -> withContext(Dispatchers.Default) { indexes.flatMap { it.candidates(query) } } }
        val tokens: ((String) -> List<String>)? = localIndexes?.let { indexes ->
            { name -> indexes.firstNotNullOfOrNull { it.words[name] } ?: com.nuvio.tv.data.mediaserver.preparedSearchWords(name) }
        }
        if (localTitles != null) rankTitles(localTitles, kind, query, limit, tokens).takeIf { it.isNotEmpty() }?.let { return it }
        val exact = withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) { super.search(session, kind, libraries, query, limit) }.orEmpty()
        }
        if (query.length > 160) return exact
        if (exact.isNotEmpty() && com.nuvio.tv.data.mediaserver.significantWords(query).size < 2) return exact
        if (localIndex != null && localTitles == null) {
            if (exact.isNotEmpty()) return exact
            throw ServerException(ServerFailure.UNREACHABLE)
        }
        val indexed = localTitles ?: try {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(20_000) {
                    coroutineScope {
                        libraries.map { library -> async { indexedTitles(session, library) } }.awaitAll().flatten()
                    }
                }
            } ?: if (exact.isNotEmpty()) return exact else throw ServerException(ServerFailure.UNREACHABLE)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: ServerException) {
            // A server that cannot enumerate its library can still answer ordinary searches.
            if (exact.isNotEmpty()) return exact
            throw ServerException(ServerFailure.UNREACHABLE)
        }
        val ranked = rankTitles(indexed, kind, query, limit, tokens)
        if (ranked.isNotEmpty()) return ranked
        if (exact.isNotEmpty()) return exact
        return withTimeoutOrNull(6_000) {
            for (term in fallbackServerSearchTerms(query)) {
                val candidates = super.search(session, kind, libraries, term, limit.coerceAtMost(50))
                val matches = candidates.map { it to serverSearchScore(query, it.preview.name) }
                    .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
                    .distinctBy { it.preview.id }.take(limit)
                if (matches.isNotEmpty()) return@withTimeoutOrNull matches
            }
            emptyList()
        } ?: emptyList()
    }

    private suspend fun rankTitles(indexed: List<ServerTitle>, kind: ServerMediaKind, query: String, limit: Int, tokens: ((String) -> List<String>)? = null): List<ServerTitle> = withContext(Dispatchers.Default) {
            val matcher = ServerSearchMatcher(query, fast = disk != null)
            val titleScores = mutableMapOf<String, Double>()
            val personScores = mutableMapOf<String, Double>()
            val scores = indexed.filter { it.preview.apiType == kind.contentType }.map { title ->
                coroutineContext.ensureActive()
                val titleScore = (listOf(title.preview.name) + title.searchNames).maxOf { name ->
                    titleScores.getOrPut(name) { if (tokens == null) matcher.score(name) else matcher.scorePrepared(tokens(name)) }
                }
                val personScore = title.peopleNames.maxOfOrNull { name ->
                    personScores.getOrPut(name) { if (tokens == null) matcher.score(name, person = true) else matcher.scorePrepared(tokens(name), person = true) }
                } ?: 0.0
                Triple(title, titleScore, personScore)
            }
            // Prefer the exact person when present. Similar surnames must not expand their filmography.
            val personThreshold = if (scores.any { it.third == 1.0 }) 1.0 else 0.7
            scores.filter { it.second > 0 || (it.third >= personThreshold && it.third > 0) }
                .sortedByDescending { maxOf(it.second, it.third) }.map { it.first }.distinctBy { it.preview.id }.take(limit)
    }

    override suspend fun details(session: ServerSession, itemId: String): ServerItemDetails {
        val cache = disk ?: return super.details(session, itemId)
        val account = FamilyLibraryIndex.cacheScope(session)
        val key = "details:$itemId"
        withContext(Dispatchers.IO) { cache.get<ServerItemDetails>(account, key, ServerItemDetails::class.java, 5 * 60_000L) }
            ?.let { return it.value }
        val details = super.details(session, itemId)
        withContext(Dispatchers.IO) { cache.put(account, key, details.copy(userStates = emptyList()), allowed = { account !in revoked }) }
        return details
    }

    override suspend fun personDetails(session: ServerSession, personId: String): PersonDetail {
        val cache = disk ?: return super.personDetails(session, personId)
        val account = FamilyLibraryIndex.cacheScope(session)
        val libraries = session.connection.selectedLibraries.map { it.id + ":" + it.kind.name }.sorted().joinToString("|")
        val key = "person:$personId:$libraries"
        withContext(Dispatchers.IO) { cache.get<PersonDetail>(account, key, PersonDetail::class.java, 15 * 60_000L) }
            ?.let { return it.value }
        val person = super.personDetails(session, personId)
        withContext(Dispatchers.IO) { cache.put(account, key, person, allowed = { account !in revoked }) }
        return person
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
