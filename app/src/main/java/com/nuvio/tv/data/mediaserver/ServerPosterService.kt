package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.local.PosterPreferencesDataStore
import com.nuvio.tv.data.local.PosterPreferences
import com.nuvio.tv.domain.model.MetaPreview
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
import kotlinx.coroutines.sync.Semaphore
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
    private val cache = object : LinkedHashMap<String, String?>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 512
    }

    init {
        if (configuration != null) scope.launch {
            configuration.collect { mutableUpdates.update { it + 1 } }
        }
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
            disk?.get<String>("tmdb-pl-v1", key, String::class.java, 24 * 60 * 60_000L)?.let { stored ->
                synchronized(cache) { cache[key] = stored.value.takeIf { it.isNotBlank() } }
                if (stored.value.isNotBlank()) mutableUpdates.update { it + 1 }
                return
            }
            val found = api.findByExternalId(item.imdbId!!, requestApiKey)
            if (!found.isSuccessful) return
            if (configuredApiKey() != requestApiKey) return
            val ids = found.body()
            val id = (if (item.apiType == "series") ids?.tvResults else ids?.movieResults)?.firstOrNull()?.id
            if (id == null) {
                synchronized(cache) { cache[key] = null }
                disk?.put("tmdb-pl-v1", key, "")
                return
            }
            val response = if (item.apiType == "series") api.getTvImages(id, requestApiKey, "pl")
                else api.getMovieImages(id, requestApiKey, "pl")
            if (!response.isSuccessful) return
            if (configuredApiKey() != requestApiKey) return
            val path = response.body()?.posters.orEmpty()
                .firstOrNull { it.iso6391 == "pl" && !it.filePath.isNullOrBlank() }?.filePath
            synchronized(cache) { cache[key] = path?.let { "https://image.tmdb.org/t/p/w500$it" } }
            disk?.put("tmdb-pl-v1", key, path?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty())
            if (path != null) mutableUpdates.update { it + 1 }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the existing artwork. A later catalog fetch can retry transient errors.
        }
    }

    private fun key(item: MetaPreview): String = "${item.apiType}:${item.imdbId}"

    private fun originalPoster(item: MetaPreview): MetaPreview =
        item.rawPosterUrl?.let { item.copy(poster = it) } ?: item
}
