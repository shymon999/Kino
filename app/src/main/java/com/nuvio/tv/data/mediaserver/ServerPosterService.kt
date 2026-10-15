package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.MetaPreview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ServerPosterService @Inject constructor(
    private val api: TmdbApi
) {
    private val requests = Semaphore(4)
    private val cache = object : LinkedHashMap<String, String?>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 512
    }

    suspend fun enrich(items: List<MetaPreview>, apiKey: String = BuildConfig.TMDB_API_KEY): List<MetaPreview> {
        if (apiKey.isBlank()) return items
        val candidates = items.filter { !it.imdbId.isNullOrBlank() && it.apiType in listOf("movie", "series") }
            .distinctBy { "${it.apiType}:${it.imdbId}" }
        // A slow artwork provider must not hold up a server row indefinitely.
        withTimeoutOrNull(8_000) {
            coroutineScope {
                candidates.map { item -> async(Dispatchers.IO) {
                    val key = "${item.apiType}:${item.imdbId}"
                    requests.withPermit {
                        if (synchronized(cache) { cache.containsKey(key) }) return@withPermit
                        try {
                            val found = api.findByExternalId(item.imdbId!!, apiKey)
                            if (!found.isSuccessful) return@withPermit
                            val ids = found.body()
                            val id = (if (item.apiType == "series") ids?.tvResults else ids?.movieResults)
                                ?.firstOrNull()?.id ?: return@withPermit
                            val response = if (item.apiType == "series") api.getTvImages(id, apiKey, "pl")
                                else api.getMovieImages(id, apiKey, "pl")
                            if (!response.isSuccessful) return@withPermit
                            val path = response.body()?.posters.orEmpty()
                                .firstOrNull { it.iso6391 == "pl" && !it.filePath.isNullOrBlank() }?.filePath
                            synchronized(cache) { cache[key] = path?.let { "https://image.tmdb.org/t/p/w500$it" } }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Retain Emby artwork and retry later after transient network failures.
                        }
                    }
                } }.awaitAll()
            }
        }
        return items.map { item ->
            val poster = synchronized(cache) { cache["${item.apiType}:${item.imdbId}"] }
            if (poster == null) item else item.copy(poster = poster, rawPosterUrl = item.rawPosterUrl ?: item.poster)
        }
    }
}
