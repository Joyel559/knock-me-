package com.joyel.tripguard.share

/** One resolve's debug trail, shown on Settings > Link debug (debug builds only). Memory-only. */
data class LinkDebugEntry(
    val input: String,
    val resolverWon: String,
    val elapsedMs: Long,
    val result: String
)

/** In-memory log of the last 20 [PlaceInputResolver.resolve] calls, debug builds only — never
 * written to in a release build (every call site is `if (BuildConfig.DEBUG)` gated). */
object LinkDebugLog {
    private const val MAX_ENTRIES = 20
    private val _entries = ArrayDeque<LinkDebugEntry>()
    val entries: List<LinkDebugEntry> get() = _entries.toList()

    @Synchronized
    fun record(entry: LinkDebugEntry) {
        _entries.addFirst(entry)
        while (_entries.size > MAX_ENTRIES) _entries.removeLast()
    }

    @Synchronized
    fun clear() = _entries.clear()
}
