package com.cadence.player.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Station directories:
 *  - DI.fm: official channel list; streams need the listener's own Premium listen key.
 *  - SomaFM: listener-supported stations that welcome third-party players.
 *  - World: the open community directory at radio-browser.info.
 */
object RadioRepository {
    private const val USER_AGENT = "Cadence/1.2 (Android music player)"

    private val radioBrowserHosts = listOf(
        "https://all.api.radio-browser.info",
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
    )

    // ---------------------------------------------------------------- DI.fm

    suspend fun diChannels(): List<RadioStation> = withContext(Dispatchers.IO) {
        val text = firstSuccessful(
            "https://listen.di.fm/premium_high.json",
            "http://listen.di.fm/premium_high.json",
        )
        val arr = JSONArray(text)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val key = o.optString("key")
            RadioStation(
                id = "di:$key",
                name = o.optString("name", key),
                subtitle = "DI.fm · Premium 320k",
                source = RadioSource.DIFM,
                streamUrl = o.optString("playlist").ifBlank { "https://listen.di.fm/premium_high/$key.pls" },
            )
        }.filter { it.id != "di:" }.sortedBy { it.name.lowercase() }
    }

    // ---------------------------------------------------------------- SomaFM

    suspend fun somaChannels(): List<RadioStation> = withContext(Dispatchers.IO) {
        val text = firstSuccessful("https://somafm.com/channels.json", "https://api.somafm.com/channels.json")
        val arr = JSONObject(text).getJSONArray("channels")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val playlists = o.optJSONArray("playlists") ?: JSONArray()
            val candidates = List(playlists.length()) { playlists.getJSONObject(it) }
            val best = candidates.firstOrNull { it.optString("format") == "mp3" && it.optString("quality") == "highest" }
                ?: candidates.firstOrNull { it.optString("format") == "mp3" }
                ?: candidates.firstOrNull()
            RadioStation(
                id = "soma:$id",
                name = o.optString("title", id),
                subtitle = o.optString("genre").replace("|", " · ").ifBlank { o.optString("description") },
                source = RadioSource.SOMAFM,
                streamUrl = best?.optString("url")?.takeIf { it.isNotBlank() } ?: "https://ice2.somafm.com/$id-128-mp3",
                imageUrl = o.optString("largeimage").ifBlank { o.optString("image") }.ifBlank { null },
                listeners = o.optString("listeners").toIntOrNull() ?: 0,
            )
        }.filter { it.id != "soma:" }.sortedByDescending { it.listeners }
    }

    // ---------------------------------------------------------------- World (radio-browser.info)

    suspend fun worldStations(query: String = "", tag: String? = null, countryCode: String? = null): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val params = buildList {
                if (query.isNotBlank()) add("name=" + URLEncoder.encode(query.trim(), "UTF-8"))
                if (!tag.isNullOrBlank()) add("tag=" + URLEncoder.encode(tag, "UTF-8"))
                if (!countryCode.isNullOrBlank()) add("countrycode=$countryCode")
                add("hidebroken=true")
                add("order=clickcount")
                add("reverse=true")
                add("limit=120")
            }.joinToString("&")
            val text = firstSuccessful(*radioBrowserHosts.map { "$it/json/stations/search?$params" }.toTypedArray())
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val details = listOf(
                    o.optString("country"),
                    o.optString("codec").takeIf { it.isNotBlank() && it != "UNKNOWN" },
                    o.optInt("bitrate").takeIf { it > 0 }?.let { "${it}k" },
                ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                RadioStation(
                    id = "rb:" + o.optString("stationuuid"),
                    name = o.optString("name").trim().ifBlank { "Unnamed station" },
                    subtitle = details,
                    source = RadioSource.WORLD,
                    streamUrl = o.optString("url_resolved").ifBlank { o.optString("url") },
                    imageUrl = o.optString("favicon").ifBlank { null },
                    listeners = o.optInt("clickcount"),
                )
            }.filter { it.streamUrl.startsWith("http") && it.id != "rb:" }
                .distinctBy { it.name.lowercase() + it.streamUrl }
        }

    // ---------------------------------------------------------------- Streams

    /** Turns a station into a URL the player can open, expanding .pls/.m3u playlists. */
    suspend fun resolveStream(station: RadioStation, diListenKey: String): String = withContext(Dispatchers.IO) {
        val base = station.streamUrl
        val url = if (station.source == RadioSource.DIFM) {
            if (diListenKey.isBlank()) throw IOException("A DI.fm listen key is needed")
            base + (if ('?' in base) "&" else "?") + diListenKey.trim()
        } else {
            base
        }
        val lower = url.substringBefore('?').lowercase()
        // HLS (.m3u8) is played directly by the player; only classic playlists are expanded here.
        val isPlaylist = lower.endsWith(".pls") || lower.endsWith(".m3u")
        if (!isPlaylist) return@withContext url
        val entries = runCatching { parsePlaylist(get(url)) }.getOrDefault(emptyList())
        entries.firstOrNull() ?: when (station.source) {
            RadioSource.DIFM -> "http://prem2.di.fm:80/${station.id.removePrefix("di:")}_hi?${diListenKey.trim()}"
            RadioSource.SOMAFM -> "https://ice2.somafm.com/${station.id.removePrefix("soma:")}-128-mp3"
            RadioSource.WORLD -> url
        }
    }

    /** Reads stream URLs from a PLS or M3U playlist. */
    fun parsePlaylist(text: String): List<String> {
        val pls = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("File", ignoreCase = true) && '=' in it }
            .map { it.substringAfter('=').trim() }
            .filter { it.startsWith("http") }
            .toList()
        if (pls.isNotEmpty()) return pls
        return text.lineSequence().map { it.trim() }.filter { it.startsWith("http") }.toList()
    }

    // ---------------------------------------------------------------- HTTP

    private fun firstSuccessful(vararg urls: String): String {
        var last: Exception? = null
        for (u in urls) {
            try {
                return get(u)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("No response")
    }

    private fun get(url: String, redirects: Int = 0): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "*/*")
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                return get(URL(URL(url), location).toString(), redirects + 1)
            }
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
