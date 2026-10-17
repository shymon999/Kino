package com.nuvio.tv.data.mediaserver

import com.nuvio.tv.core.tracking.TrackingExternalIds
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** Optional private-library integration fixture, supplied locally and never bundled in the APK. */
class LibraryClassificationAuditTest {
    @Test fun auditCompleteLibrarySnapshot() {
        val path = System.getenv("NUVIO_CATALOG_AUDIT")
        assumeNotNull(path)
        val snapshot = Json.parseToJsonElement(File(path!!).readText()).jsonArray
        val groups = mutableMapOf<String, MutableMap<String, Int>>()
        var classified = 0
        for (element in snapshot) {
            val item = element.jsonObject
            val library = item.getValue("Library").jsonPrimitive.content
            val kind = if (item.getValue("Type").jsonPrimitive.content == "Series") ServerMediaKind.SERIES else ServerMediaKind.MOVIE
            val title = ServerTitle(MetaPreview(
                id = item.getValue("Id").jsonPrimitive.content, type = kind.domainType(),
                name = item.getValue("Name").jsonPrimitive.content, poster = null, posterShape = PosterShape.POSTER,
                background = null, logo = null, description = item["Overview"]?.jsonPrimitive?.content,
                releaseInfo = null, imdbRating = null,
                genres = item["Genres"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            ), TrackingExternalIds(tmdb = item["ProviderIds"]?.jsonObject?.get("Tmdb")?.jsonPrimitive?.longOrNull))
            val genre = primaryServerGenre(title, kind)
            assertTrue("Unclassified: ${title.preview.name}", genre.isNotBlank())
            val counts = groups.getOrPut(library) { mutableMapOf() }
            counts[genre] = counts.getOrDefault(genre, 0) + 1
            classified++
        }
        assertEquals(snapshot.size, classified)
        val series = groups.getValue("Seriale")
        assertEquals(172, series.values.sum())
        assertTrue(series.getValue("comedy") > 20)
        assertTrue(series.getValue("crime") > 20)
        assertTrue(series.getValue("science fiction") > 15)
        assertFalse(series.containsKey("animation"))
        assertFalse(series.containsKey("musical"))
        assertFalse(groups.getValue("Filmy").containsKey("reality"))
        System.getenv("NUVIO_CATALOG_AUDIT_REPORT")?.let { output ->
            File(output).writeText(buildJsonObject {
                put("classified", classified)
                put("shelves", buildJsonObject {
                    groups.forEach { (library, counts) -> put(library, buildJsonObject {
                        counts.filterValues { it >= 4 }.toSortedMap().forEach { (genre, count) -> put(genre, count) }
                    }) }
                })
            }.toString())
        }
    }
}
