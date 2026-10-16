package com.nuvio.tv.data.mediaserver

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private val searchWords = Regex("[\\p{L}\\p{N}]+")
private val searchMarks = Regex("\\p{M}+")
private val searchStopWords = setOf("the", "and", "oraz", "dla", "film", "serial", "sezon", "odcinek", "pokaz", "prosze", "obejrzyj", "chce")

private fun normalizeSearchWord(value: String): String =
    searchMarks.replace(Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD), "")

private fun significantWords(value: String): List<String> = searchWords.findAll(value.take(160))
    .map { normalizeSearchWord(it.value) }.filter { it.length >= 3 && it !in searchStopWords }.distinct().toList()

internal fun fallbackServerSearchTerms(query: String): List<String> {
    val words = searchWords.findAll(query.take(160)).map { it.value }
        .filter { it.length >= 4 && normalizeSearchWord(it) !in searchStopWords }
        .distinctBy(::normalizeSearchWord).toList()
    if (words.size < 2) return emptyList()
    // Preserve accents in requests: servers need not use accent-insensitive indexes.
    return words.sortedByDescending { it.length }.take(2)
}

internal fun serverSearchScore(query: String, title: String): Double {
    val wanted = significantWords(query)
    val actual = significantWords(title)
    if (wanted.isEmpty() || actual.isEmpty()) return 0.0
    val similarities = wanted.map { word -> actual.maxOf { candidate ->
        when {
            word == candidate -> 1.0
            minOf(word.length, candidate.length) >= 4 && oneEditApart(word, candidate) -> 0.8
            else -> 0.0
        }
    } }
    val matched = similarities.count { it > 0 }
    val hasStrongWord = wanted.any { it.length >= 8 && it in actual }
    if (matched * 2 < wanted.size || (!hasStrongWord && matched < 2)) return 0.0
    return (similarities.sum() / wanted.size).takeIf { it >= 0.45 } ?: 0.0
}

private fun oneEditApart(first: String, second: String): Boolean {
    if (abs(first.length - second.length) > 1) return false
    var left = 0
    var right = 0
    var edits = 0
    while (left < first.length && right < second.length) {
        if (first[left] == second[right]) { left++; right++; continue }
        if (++edits > 1) return false
        when {
            first.length > second.length -> left++
            second.length > first.length -> right++
            else -> { left++; right++ }
        }
    }
    return true
}
