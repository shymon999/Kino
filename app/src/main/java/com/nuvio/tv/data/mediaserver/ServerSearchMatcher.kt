package com.nuvio.tv.data.mediaserver

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private val searchWords = Regex("[\\p{L}\\p{N}]+")
private val searchMarks = Regex("\\p{M}+")
private val searchStopWords = setOf("the", "and", "oraz", "dla", "film", "serial", "sezon", "odcinek", "pokaz", "prosze", "obejrzyj", "chce", "filmy", "seriale", "aktora", "aktorem", "udzialem", "wystepuje", "znajdz", "wlacz")

private fun normalizeSearchWord(value: String): String =
    searchMarks.replace(Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD), "")

internal fun significantWords(value: String): List<String> = searchWords.findAll(value.take(160))
    .map { normalizeSearchWord(it.value) }.filter { it.length >= 3 && it !in searchStopWords }.distinct().toList()

internal fun preparedSearchWords(value: String): List<String> = searchWords.findAll(value.take(160)).map { match ->
    val word = match.value
    if (word.all { it.code < 128 }) word.lowercase(Locale.ROOT) else normalizeSearchWord(word)
}.filter { it.length >= 3 && it !in searchStopWords }.distinct().toList()

internal fun fallbackServerSearchTerms(query: String): List<String> {
    val words = searchWords.findAll(query.take(160)).map { it.value }
        .filter { it.length >= 4 && normalizeSearchWord(it) !in searchStopWords }
        .distinctBy(::normalizeSearchWord).toList()
    if (words.size < 2) return emptyList()
    // Preserve accents in requests: servers need not use accent-insensitive indexes.
    return words.sortedByDescending { it.length }.take(2)
}

internal fun serverSearchScore(query: String, title: String): Double {
    return ServerSearchMatcher(query).score(title)
}

internal class ServerSearchMatcher(query: String, private val fast: Boolean = false) {
    private val wanted = significantWords(query)
    private val ordered = wanted.sortedByDescending { it.length }
    private val distances = List(ordered.size) { HashMap<String, Int>() }
    private var distanceCount = 0

    fun score(title: String, person: Boolean = false): Double {
        if (!fast) return scoreWords(wanted, significantWords(title), person)
        return scorePrepared(preparedSearchWords(title), person)
    }

    // A permissive candidate filter. Final ranking still checks complete titles/person names.
    fun mightMatchWord(candidate: String): Boolean = ordered.indices.any { i ->
        val word = ordered[i]
        word == candidate ||
            (minOf(word.length, candidate.length) >= 4 && distance(i, candidate) <= 1) ||
            (minOf(word.length, candidate.length) >= 6 && distance(i, candidate) <= 2) ||
            (ordered.size >= 2 && word.length >= 3 && candidate.length - word.length in 1..3 && candidate.endsWith(word))
    }

    private fun distance(index: Int, candidate: String): Int {
        val word = ordered[index]
        if (abs(word.length - candidate.length) > 2) return 3
        return distances[index][candidate] ?: editDistance(word, candidate).also {
            if (distanceCount < 32_768) { distances[index][candidate] = it; distanceCount++ }
        }
    }

    fun scorePrepared(actual: List<String>, person: Boolean = false): Double {
        if (!fast) return scoreWords(wanted, actual, person)
        if (ordered.isEmpty() || actual.isEmpty()) return 0.0
        if (ordered.size == 1 && ordered[0].length < 4) return 0.0
        val strong = ordered.any { it.length >= 8 && it in actual }
        val required = if (ordered.size == 1) 1 else maxOf((ordered.size + 1) / 2, if (strong) 1 else 2)
        if (actual.size < required) return 0.0
        val used = BooleanArray(actual.size)
        var matched = 0
        var sum = 0.0
        for (i in ordered.indices) {
            val word = ordered[i]
            var bestIndex = -1
            var best = 0.0
            for (j in actual.indices) {
                if (used[j]) continue
                val candidate = actual[j]
                val similarity = if (word == candidate) 1.0 else {
                    val distance = distance(i, candidate)
                    when {
                        minOf(word.length, candidate.length) >= 4 && distance <= 1 -> 0.85
                        minOf(word.length, candidate.length) >= (if (person) 8 else 6) && distance <= 2 -> 0.7
                        person && ordered.size >= 2 && word.length >= 3 && candidate.length - word.length in 1..3 && candidate.endsWith(word) -> 0.75
                        else -> 0.0
                    }
                }
                if (similarity > best) { best = similarity; bestIndex = j }
                if (best == 1.0) break
            }
            if (bestIndex >= 0) { used[bestIndex] = true; matched++; sum += best }
            if (matched + ordered.size - i - 1 < required) return 0.0
        }
        return (sum / ordered.size).takeIf { matched >= required && (ordered.size == 1 || it >= 0.45) } ?: 0.0
    }
}

private fun scoreWords(wanted: List<String>, actual: List<String>, person: Boolean): Double {
    if (wanted.isEmpty() || actual.isEmpty()) return 0.0
    val available = actual.toMutableList()
    val similarities = wanted.sortedByDescending { it.length }.map { word ->
        val best = available.withIndex().map { (index, candidate) ->
            index to when {
                word == candidate -> 1.0
                else -> {
                    val distance = editDistance(word, candidate)
                    when {
                        minOf(word.length, candidate.length) >= 4 && distance <= 1 -> 0.85
                        // Two edits in a short person's name can identify a different person.
                        minOf(word.length, candidate.length) >= (if (person) 8 else 6) && distance <= 2 -> 0.7
                        person && wanted.size >= 2 && word.length >= 3 && candidate.length - word.length in 1..3 &&
                            candidate.endsWith(word) -> 0.75
                        else -> 0.0
                    }
                }
            }
        }.maxByOrNull { it.second }
        if (best != null && best.second > 0) { available.removeAt(best.first); best.second } else 0.0
    }
    val matched = similarities.count { it > 0 }
    if (wanted.size == 1) return similarities.single().takeIf { wanted.single().length >= 4 } ?: 0.0
    val hasStrongWord = wanted.any { it.length >= 8 && it in actual }
    if (matched * 2 < wanted.size || (!hasStrongWord && matched < 2)) return 0.0
    return (similarities.sum() / wanted.size).takeIf { it >= 0.45 } ?: 0.0
}

// Restricted Damerau-Levenshtein also tolerates adjacent letters swapped by dictation.
private fun editDistance(first: String, second: String): Int {
    if (abs(first.length - second.length) > 2) return 3
    var previousPrevious = IntArray(second.length + 1)
    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    for (i in 1..first.length) {
        current.fill(3)
        current[0] = i
        var rowMinimum = 3
        for (j in maxOf(1, i - 2)..minOf(second.length, i + 2)) {
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1,
                previous[j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]) {
                current[j] = minOf(current[j], previousPrevious[j - 2] + 1)
            }
            rowMinimum = minOf(rowMinimum, current[j])
        }
        if (rowMinimum > 2) return 3
        val reuse = previousPrevious
        previousPrevious = previous
        previous = current
        current = reuse
    }
    return previous[second.length]
}
