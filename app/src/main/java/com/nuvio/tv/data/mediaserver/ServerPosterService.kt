package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.local.PosterPreferencesDataStore
import com.nuvio.tv.data.local.PosterPreferences
import com.nuvio.tv.domain.model.MetaPreview
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ServerPosterService internal constructor(
    private val api: TmdbApi,
    private val scope: CoroutineScope,
    private val apiKey: String,
    private val disk: BoundedMetadataDiskCache? = null,
    private val configuredApiKey: () -> String = { apiKey },
    configuration: StateFlow<PosterPreferences>? = null
) {
    @Inject constructor(api: TmdbApi, metadataCache: ServerMetadataCache, preferences: PosterPreferencesDataStore) : this(
        api, CoroutineScope(SupervisorJob() + Dispatchers.IO), BuildConfig.TMDB_API_KEY,
        metadataCache.disk, preferences::activeApiKey, preferences.state
    )

    private val requests = Semaphore(4)
    private val pending = mutableSetOf<String>()
    private val mutableUpdates = MutableStateFlow(0L)
    val updates = mutableUpdates.asStateFlow()
    private val mutableRejectedApiKey = MutableStateFlow<String?>(null)
    /** The key TMDB answered with 401. It sends no further lookups until the key changes. */
    val rejectedApiKey = mutableRejectedApiKey.asStateFlow()
    private val cache = object : LinkedHashMap<String, String?>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > MEMORY_ENTRIES
    }
    private val diskLock = Mutex()

    init {
        if (configuration != null) scope.launch {
            configuration.collect { mutableUpdates.update { it + 1 } }
        }
        // One file per title crowded details and filmographies out of the shared entry limit.
        if (disk != null) scope.launch { disk.clear(LEGACY_DISK_SCOPE) }
    }

    fun cached(items: List<MetaPreview>): List<MetaPreview> = items.map { item ->
        if (configuredApiKey().isBlank()) return@map originalPoster(item)
        val poster = synchronized(cache) { cache[key(item)] }
        if (poster == null) originalPoster(item) else item.copy(poster = poster, rawPosterUrl = item.rawPosterUrl ?: item.poster)
    }

    // Artwork is decoration, not another catalog page. Return Emby immediately.
    fun cachedAndRequest(items: List<MetaPreview>): List<MetaPreview> {
        val requestApiKey = configuredApiKey()
        if (requestApiKey.isBlank()) return items.map(::originalPoster)
        if (requestApiKey == rejectedApiKey.value) return cached(items)
        items.filter { !it.imdbId.isNullOrBlank() && it.apiType in listOf("movie", "series") }
            .distinctBy(::key).forEach { item ->
                val key = key(item)
                val queued = synchronized(cache) {
                    if (cache.containsKey(key) || key in pending || pending.size >= 256) false
                    else pending.add(key)
                }
                if (queued) scope.launch {
                    try {
                        requests.withPermit { withTimeoutOrNull(5_000) { fetch(item, key, requestApiKey) } }
                    } finally {
                        synchronized(cache) { pending.remove(key) }
                    }
                }
            }
        return cached(items)
    }

    private suspend fun fetch(item: MetaPreview, key: String, requestApiKey: String) {
        try {
            if (configuredApiKey() != requestApiKey) return
            if (restore(key)) return
            val found = api.findByExternalId(item.imdbId!!, requestApiKey)
            if (found.code() == 401) {
                if (configuredApiKey() == requestApiKey) mutableRejectedApiKey.value = requestApiKey
                return
            }
            if (!found.isSuccessful) return
            if (configuredApiKey() != requestApiKey) return
            val ids = found.body()
            val id = (if (item.apiType == "series") ids?.tvResults else ids?.movieResults)?.firstOrNull()?.id
            if (id == null) {
                synchronized(cache) { cache[key] = null }
                store(key, "")
                return
            }
            val response = if (item.apiType == "series") api.getTvImages(id, requestApiKey, "pl")
                else api.getMovieImages(id, requestApiKey, "pl")
            if (!response.isSuccessful) return
            if (configuredApiKey() != requestApiKey) return
            val path = response.body()?.posters.orEmpty()
                .firstOrNull { it.iso6391 == "pl" && !it.filePath.isNullOrBlank() }?.filePath
            synchronized(cache) { cache[key] = path?.let { "https://image.tmdb.org/t/p/w500$it" } }
            store(key, path?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty())
            if (path != null) mutableUpdates.update { it + 1 }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the existing artwork. A later catalog fetch can retry transient errors.
        }
    }

    /** Loads the whole stored bucket, so neighbouring titles need no further disk reads. */
    private suspend fun restore(key: String): Boolean {
        val disk = disk ?: return false
        val now = System.currentTimeMillis()
        val stored = diskLock.withLock {
            disk.get<Map<String, String>>(DISK_SCOPE, bucket(key), BUCKET_TYPE, POSTER_TTL_MS)?.value
        }.orEmpty().mapNotNull { (title, entry) -> freshUrl(entry, now)?.let { title to it } }.toMap()
        var found = false
        var restoredPoster = false
        synchronized(cache) {
            for ((title, url) in stored) {
                if (cache.containsKey(title) && title != key) continue
                cache[title] = url.takeIf { it.isNotBlank() }
                if (url.isNotBlank()) restoredPoster = true
                if (title == key) found = true
            }
        }
        if (restoredPoster) mutableUpdates.update { it + 1 }
        return found
    }

    private suspend fun store(key: String, url: String) {
        val disk = disk ?: return
        val now = System.currentTimeMillis()
        diskLock.withLock {
            val current = disk.get<Map<String, String>>(DISK_SCOPE, bucket(key), BUCKET_TYPE, POSTER_TTL_MS)?.value.orEmpty()
            disk.put(DISK_SCOPE, bucket(key), current.filterValues { freshUrl(it, now) != null } + (key to "$now|$url"))
        }
    }

    private fun freshUrl(entry: String, now: Long): String? {
        val storedAt = entry.substringBefore('|').toLongOrNull() ?: return null
        return entry.substringAfter('|').takeIf { now - storedAt in 0..POSTER_TTL_MS }
    }

    private fun bucket(key: String): String = "posters:" + (key.hashCode() and (POSTER_BUCKETS - 1))

    private fun key(item: MetaPreview): String = "${item.apiType}:${item.imdbId}"

    private companion object {
        const val DISK_SCOPE = "tmdb-pl-v2"
        const val LEGACY_DISK_SCOPE = "tmdb-pl-v1"
        const val POSTER_BUCKETS = 32
        const val POSTER_TTL_MS = 24 * 60 * 60_000L
        const val MEMORY_ENTRIES = 4_096
        val BUCKET_TYPE = object : TypeToken<Map<String, String>>() {}.type
    }

    private fun originalPoster(item: MetaPreview): MetaPreview =
        item.rawPosterUrl?.let { item.copy(poster = it) } ?: item
}
