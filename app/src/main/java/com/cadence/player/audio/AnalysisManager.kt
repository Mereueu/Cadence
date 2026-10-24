package com.cadence.player.audio

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Analyses songs in the background, one at a time, most urgent first,
 * and caches the results on disk so each song is analysed only once.
 */
object AnalysisManager {
    private lateinit var appContext: Context
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _results = MutableStateFlow<Map<Long, TrackAnalysis>>(emptyMap())
    val results: StateFlow<Map<Long, TrackAnalysis>> = _results.asStateFlow()

    /** Song currently being analysed and its progress 0..1. */
    private val _working = MutableStateFlow<Pair<Long, Float>?>(null)
    val working: StateFlow<Pair<Long, Float>?> = _working.asStateFlow()

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val urgent = ArrayDeque<Long>()
    private val background = ArrayDeque<Long>()
    private val failed = HashSet<Long>()
    private val lock = Any()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        dir = File(appContext.filesDir, "analysis").apply { mkdirs() }
        scope.launch { worker() }
    }

    fun get(id: Long): TrackAnalysis? = _results.value[id]

    /** Asks for these songs to be analysed soon (e.g. current and next track). */
    fun requestUrgent(vararg ids: Long) = enqueue(ids.toList(), urgent = true)

    /** Queues many songs at low priority (e.g. "analyse whole library"). */
    fun requestBackground(ids: List<Long>) = enqueue(ids, urgent = false)

    private fun enqueue(ids: List<Long>, urgent: Boolean) {
        if (!::appContext.isInitialized) return
        synchronized(lock) {
            for (id in ids) {
                if (id <= 0 || id in _results.value || id in failed) continue
                if (urgent) {
                    this.urgent.remove(id)
                    this.urgent.addFirst(id)
                    background.remove(id)
                } else if (id !in background && id !in this.urgent) {
                    background.addLast(id)
                }
            }
            _pending.value = this.urgent.size + background.size
        }
        wake.trySend(Unit)
    }

    private fun next(): Long? = synchronized(lock) {
        val id = urgent.removeFirstOrNull() ?: background.removeFirstOrNull()
        _pending.value = urgent.size + background.size
        id
    }

    private suspend fun worker() {
        while (true) {
            val id = next()
            if (id == null) {
                wake.receive()
                continue
            }
            if (id in _results.value) continue
            val cached = File(dir, "$id.json").takeIf { it.exists() }?.let { f ->
                runCatching { TrackAnalysis.fromJson(f.readText()) }.getOrNull()
            }
            if (cached != null) {
                publish(cached)
                continue
            }
            _working.value = id to 0f
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            val result = runCatching {
                TrackAnalyzer.analyze(appContext, id, uri) { p -> _working.value = id to p }
            }.getOrNull()
            _working.value = null
            if (result != null) {
                runCatching { File(dir, "$id.json").writeText(result.toJson()) }
                publish(result)
            } else {
                synchronized(lock) { failed += id }
            }
        }
    }

    private fun publish(a: TrackAnalysis) {
        _results.update { old ->
            val m = if (old.size > 400) old.entries.drop(100).associate { it.key to it.value } else old
            m + (a.songId to a)
        }
    }

    fun clearCache() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        _results.value = emptyMap()
        synchronized(lock) { failed.clear() }
    }

    fun cachedCount(): Int = runCatching { dir.list()?.size ?: 0 }.getOrDefault(0)
}
