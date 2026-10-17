package com.nuvio.tv.data.mediaserver.emby

import com.google.gson.reflect.TypeToken
import com.nuvio.tv.data.mediaserver.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Family-only persistent index. Stale data stays usable while a complete replacement is fetched. */
internal class FamilyLibraryIndex(
    private val disk: BoundedMetadataDiskCache,
    private val scope: CoroutineScope,
    private val seed: String,
    private val loadPage: suspend (ServerSession, ServerLibrary, Int) -> ServerPage<ServerTitle>,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Snapshot(val at: Long, val titles: List<ServerTitle>, val shelves: Map<String, List<ServerTitle>>, val search: FamilySearchIndex)
    @androidx.annotation.Keep
    private data class StoredLibrary(val titles: List<ServerTitle>, val words: Map<String, List<String>>?)
    private val lock = Any()
    private val snapshots = mutableMapOf<String, Snapshot>()
    private val loads = ConcurrentHashMap<String, Mutex>()
    private val refreshes = mutableMapOf<String, Job>()
    private val lastAttempt = mutableMapOf<String, Long>()
    private val generations = ConcurrentHashMap<String, Long>()
    private val pages = object : LinkedHashMap<String, List<ServerTitle>>(32, .75f, true) {}

    suspend fun titles(session: ServerSession, library: ServerLibrary): List<ServerTitle> = snapshot(session, library).titles
    suspend fun search(session: ServerSession, library: ServerLibrary): FamilySearchIndex = snapshot(session, library).search

    suspend fun page(session: ServerSession, library: ServerLibrary, genre: String, start: Int, limit: Int): ServerPage<ServerTitle> {
        val key = key(session, library) + "|" + genre
        // Pin a row's ordering until its first page is opened again, including during refreshes.
        val pinned = synchronized(lock) { if (start > 0) pages[key] else null }
        val titles = pinned ?: snapshot(session, library).shelves[genre].orEmpty().also { items ->
            synchronized(lock) {
                while (pages.isNotEmpty() && (pages.size >= 64 || pages.values.sumOf { it.size } + items.size > 20_000)) {
                    pages.remove(pages.keys.first())
                }
                pages[key] = items
            }
        }
        return ServerPage(titles.drop(start).take(limit), titles.size)
    }

    private suspend fun snapshot(session: ServerSession, library: ServerLibrary): Snapshot = withContext(Dispatchers.Default) {
        val key = key(session, library)
        val ready = synchronized(lock) { snapshots[key] }
        if (ready != null && now() - ready.at in 0..MAX_AGE) {
            if (now() - ready.at >= FRESH_AGE) refresh(session, library)
            return@withContext ready
        }
        loads.getOrPut(key) { Mutex() }.withLock {
            var cached = synchronized(lock) { snapshots[key] }
            if (cached == null) {
                val account = cacheScope(session)
                val generation = generations[account] ?: 0
                var migrated = false
                val stored = withContext(Dispatchers.IO) {
                    disk.get<StoredLibrary>(account, "library:v2:${library.id}", StoredLibrary::class.java, MAX_AGE)
                        ?: disk.get<List<ServerTitle>>(account, "library:${library.id}", object : TypeToken<List<ServerTitle>>() {}.type, MAX_AGE)
                            ?.let { old -> migrated = true; BoundedMetadataDiskCache.Entry(StoredLibrary(old.value, null), old.storedAt) }
                }
                if (stored != null && runCatching { stored.value.titles.all { it.preview.id.isNotBlank() && it.preview.name.isNotBlank() } }.getOrDefault(false)) {
                    val prepared = prepare(stored.storedAt, stored.value.titles, library, stored.value.words)
                    cached = prepared
                    currentCoroutineContext().ensureActive()
                    if ((generations[account] ?: 0) != generation) throw CancellationException("Account cache invalidated")
                    if (migrated) withContext(Dispatchers.IO) {
                        disk.put(account, "library:v2:${library.id}", StoredLibrary(prepared.titles, prepared.search.words), stored.storedAt) { (generations[account] ?: 0) == generation }
                        disk.remove(account, "library:${library.id}")
                    }
                    remember(key, prepared)
                } else if (stored != null) {
                    withContext(Dispatchers.IO) { disk.remove(cacheScope(session), "library:v2:${library.id}") }
                }
            }
            if (cached != null && now() - cached.at in 0..MAX_AGE) {
                if (now() - cached.at >= FRESH_AGE) refresh(session, library)
                cached
            } else fetch(session, library)
        }
    }

    private fun refresh(session: ServerSession, library: ServerLibrary) {
        val key = key(session, library)
        synchronized(lock) {
            if (refreshes[key]?.isActive == true || now() - (lastAttempt[key] ?: 0) < RETRY_DELAY) return
            lastAttempt[key] = now()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    loads.getOrPut(key) { Mutex() }.withLock { fetch(session, library) }
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) {
                    // Retain the last complete snapshot and retry after a bounded delay.
                } finally { synchronized(lock) { refreshes.remove(key) } }
            }
            refreshes[key] = job
            job.start()
        }
    }

    private suspend fun fetch(session: ServerSession, library: ServerLibrary): Snapshot {
        val account = cacheScope(session)
        val generation = generations[account] ?: 0
        val titles = withTimeoutOrNull(60_000) {
            val result = mutableListOf<ServerTitle>()
            val seen = mutableSetOf<String>()
            var offset = 0
            while (true) {
                val page = loadPage(session, library, offset)
                val added = page.items.filter { seen.add(it.preview.id) }
                if (page.items.isNotEmpty() && added.isEmpty()) throw ServerException(ServerFailure.FAILED)
                result += added
                offset += page.items.size
                if (page.items.isEmpty() || (page.totalCount?.let { offset >= it } ?: (page.items.size < 200))) break
                if (offset >= 20_000) throw ServerException(ServerFailure.FAILED)
            }
            result.toList()
        } ?: throw ServerException(ServerFailure.UNREACHABLE)
        currentCoroutineContext().ensureActive()
        if ((generations[account] ?: 0) != generation) throw CancellationException("Account cache invalidated")
        val snapshot = prepare(now(), titles, library)
        withContext(Dispatchers.IO) {
            disk.put(account, "library:v2:${library.id}", StoredLibrary(titles, snapshot.search.words), snapshot.at) { (generations[account] ?: 0) == generation }
            disk.remove(account, "library:${library.id}")
        }
        if ((generations[account] ?: 0) != generation) throw CancellationException("Account cache invalidated")
        remember(key(session, library), snapshot)
        return snapshot
    }

    private fun prepare(at: Long, titles: List<ServerTitle>, library: ServerLibrary, words: Map<String, List<String>>? = null) = Snapshot(
        at, titles, titles.groupBy { primaryServerGenre(it, library.kind) }.mapValues { (genre, items) ->
            items.sortedBy { it.preview.id }.shuffled(kotlin.random.Random((seed + library.id + genre).hashCode()))
        }, FamilySearchIndex(titles, words)
    )

    private fun remember(key: String, value: Snapshot) = synchronized(lock) {
        if (snapshots.size >= 24 || snapshots.values.sumOf { it.titles.size } - (snapshots[key]?.titles?.size ?: 0) + value.titles.size > 20_000) snapshots.clear()
        snapshots[key] = value
    }

    suspend fun clear(session: ServerSession) {
        val account = cacheScope(session)
        generations.compute(account) { _, old -> (old ?: 0) + 1 }
        synchronized(lock) {
            refreshes.filterKeys { it.startsWith("$account|") }.values.forEach { it.cancel() }
            snapshots.keys.removeAll { it.startsWith("$account|") }
            pages.keys.removeAll { it.startsWith("$account|") }
            lastAttempt.keys.removeAll { it.startsWith("$account|") }
        }
        withContext(Dispatchers.IO) { disk.clear(account) }
    }

    private fun key(session: ServerSession, library: ServerLibrary) = "${cacheScope(session)}|${library.id}"

    companion object {
        private const val FRESH_AGE = 15 * 60_000L
        private const val MAX_AGE = 24 * 60 * 60_000L
        private const val RETRY_DELAY = 60_000L

        fun cacheScope(session: ServerSession): String = with(session.connection) {
            "emby-v1|$id|$credentialRef|$address|$remoteServerId|$remoteUserId"
        }
    }
}
