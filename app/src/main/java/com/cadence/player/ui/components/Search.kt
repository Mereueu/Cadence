package com.cadence.player.ui.components

import com.cadence.player.data.Album
import com.cadence.player.data.Artist
import com.cadence.player.data.Song
import java.text.Normalizer

private val diacritics = Regex("\\p{Mn}+")

/** Lower-cases and strips accents so "sa" matches "Șa" and "beyonce" matches "Beyoncé". */
fun normalizeForSearch(text: String): String =
    diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

private fun String.containsAllWords(query: String): Boolean {
    val haystack = normalizeForSearch(this)
    return normalizeForSearch(query).split(' ').filter { it.isNotBlank() }.all { haystack.contains(it) }
}

fun Song.matchesQuery(query: String): Boolean = "$title $artist $album".containsAllWords(query)

fun Album.matchesQuery(query: String): Boolean = "$title $artist".containsAllWords(query)

fun Artist.matchesQuery(query: String): Boolean = name.containsAllWords(query)

/** Ranks results so that titles starting with the query come first. */
fun searchScore(primary: String, query: String): Int {
    val p = normalizeForSearch(primary)
    val q = normalizeForSearch(query)
    return when {
        p == q -> 0
        p.startsWith(q) -> 1
        p.contains(" $q") -> 2
        p.contains(q) -> 3
        else -> 4
    }
}
