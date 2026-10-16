package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ServerPosterService internal constructor(
    private val api: TmdbApi,
    private val scope: CoroutineScope,
    private val apiKey: String
) {
    @Inject constructor(api: TmdbApi) : this(
        api, CoroutineScope(SupervisorJob() + Dispatchers.IO), BuildConfig.TMDB_API_KEY
    )

    private val requests = Semaphore(4)
    private val pending = mutableSetOf<String>()
    private val mutableUpdates = MutableStateFlow(0L)
    val updates = mutableUpdates.asStateFlow()
    private val cache = object : LinkedHashMap<String, String?>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 512
    }

    fun cached(items: List<MetaPreview>): List<MetaPreview> = items.map { item ->
        val poster = synchronized(cache) { cache[key(item)] }
        if (poster == null) item else item.copy(poster = poster, rawPosterUrl = item.rawPosterUrl ?: item.poster)
    }

    // Artwork is decoration, not another catalog page. Return Emby immediately.
    fun cachedAndRequest(items: List<MetaPreview>): List<MetaPreview> {
        if (apiKey.isBlank()) return items
        items.filter { !it.imdbId.isNullOrBlank() && it.apiType in listOf("movie", "series") }
            .distinctBy(::key).forEach { item ->
                val key = key(item)
                val queued = synchronized(cache) {
                    if (cache.containsKey(key) || key in pending || pending.size >= 256) false
                    else pending.add(key)
                }
                if (queued) scope.launch {
                    try {
                        requests.withPermit { withTimeoutOrNull(5_000) { fetch(item, key) } }
                    } finally {
                        synchronized(cache) { pending.remove(key) }
                    }
                }
            }
        return cached(items)
    }

    private suspend fun fetch(item: MetaPreview, key: String) {
        try {
            val found = api.findByExternalId(item.imdbId!!, apiKey)
            if (!found.isSuccessful) return
            val ids = found.body()
            val id = (if (item.apiType == "series") ids?.tvResults else ids?.movieResults)?.firstOrNull()?.id
            if (id == null) {
                synchronized(cache) { cache[key] = null }
                return
            }
            val response = if (item.apiType == "series") api.getTvImages(id, apiKey, "pl")
                else api.getMovieImages(id, apiKey, "pl")
            if (!response.isSuccessful) return
            val path = response.body()?.posters.orEmpty()
                .firstOrNull { it.iso6391 == "pl" && !it.filePath.isNullOrBlank() }?.filePath
            synchronized(cache) { cache[key] = path?.let { "https://image.tmdb.org/t/p/w500$it" } }
            if (path != null) mutableUpdates.update { it + 1 }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the existing artwork. A later catalog fetch can retry transient errors.
        }
    }

    private fun key(item: MetaPreview): String = "${item.apiType}:${item.imdbId}"
}
