package com.nuvio.tv.data.mediaserver.emby

import com.nuvio.tv.data.mediaserver.ServerSearchMatcher
import com.nuvio.tv.data.mediaserver.ServerTitle
import com.nuvio.tv.data.mediaserver.preparedSearchWords
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One vocabulary scan per query instead of repeatedly tokenizing every credit in the library. */
internal class FamilySearchIndex(
    val titles: List<ServerTitle>,
    savedWords: Map<String, List<String>>? = null
) {
    val words: Map<String, List<String>>
    private val postings: Map<String, IntArray>

    init {
        val compiled = savedWords?.toMutableMap() ?: HashMap()
        val inverted = HashMap<String, MutableList<Int>>()
        for ((index, title) in titles.withIndex()) {
            val seen = HashSet<String>()
            for (name in listOf(title.preview.name) + title.searchNames + title.peopleNames) {
                for (word in compiled.getOrPut(name) { preparedSearchWords(name) }) {
                    if (seen.add(word)) inverted.getOrPut(word) { ArrayList() }.add(index)
                }
            }
        }
        words = compiled
        postings = inverted.mapValues { (_, indexes) -> indexes.toIntArray() }
    }

    suspend fun candidates(query: String): List<ServerTitle> {
        val matcher = ServerSearchMatcher(query, fast = true)
        val included = BooleanArray(titles.size)
        var visited = 0
        for ((word, indexes) in postings) {
            if (visited++ % 128 == 0) currentCoroutineContext().ensureActive()
            if (matcher.mightMatchWord(word)) for (index in indexes) included[index] = true
        }
        return titles.filterIndexed { index, _ -> included[index] }
    }
}
