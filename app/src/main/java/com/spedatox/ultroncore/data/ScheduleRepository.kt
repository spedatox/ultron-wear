package com.spedatox.ultroncore.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The schedule, with Igor as the source of truth and two fallbacks beneath it.
 *
 * Load order, and why:
 *   1. **Local cache** (`schedule.json` in filesDir) — read first and read
 *      synchronously-ish, because the watch must render a schedule the instant
 *      it is opened. Waiting on the network before drawing anything is the
 *      difference between a watch app and a bad watch app.
 *   2. **Bundled asset** — first run, before the first successful sync.
 *   3. **Empty** — the honest state. The app says so rather than inventing
 *      sample courses, which is how the current build ends up cheerfully
 *      displaying "Prof. John Smith".
 *
 * [refreshFromIgor] then updates the cache in the background and the UI reacts.
 */
class ScheduleRepository(private val context: Context) {

    private val cacheFile = File(context.filesDir, CACHE_NAME)
    private val tmpFile = File(context.filesDir, "$CACHE_NAME.tmp")
    private val writeLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _term = MutableStateFlow(DEFAULT_TERM)
    val term: StateFlow<Term> = _term.asStateFlow()

    /** True once anything has been loaded — lets the UI distinguish "no courses"
     *  from "not loaded yet" and avoid flashing an empty state on launch. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val dto = readCache() ?: readAsset()
        if (dto != null) apply(dto)
        _loaded.value = true
    }

    /** Fetch from Igor and persist. Returns false on any failure — offline is
     *  an expected outcome, not an error worth surfacing. */
    suspend fun refreshFromIgor(client: IgorClient): Boolean {
        val dto = client.fetchSchedule().getOrElse {
            Log.i(TAG, "Schedule refresh skipped: ${it.message}")
            return false
        }
        if (dto.courses.isEmpty()) {
            // An empty schedule from the server is almost certainly a
            // misconfiguration, not a semester with no classes. Refuse to let it
            // wipe a working local cache.
            Log.w(TAG, "Igor returned an empty schedule; keeping cache")
            return false
        }
        withContext(Dispatchers.IO) {
            writeLock.withLock { persist(dto) }
            apply(dto)
        }
        return true
    }

    private fun apply(dto: ScheduleDto) {
        _courses.value = dto.courses.mapNotNull { c ->
            runCatching { c.toDomain() }
                .onFailure { Log.w(TAG, "Dropping malformed course ${c.id}: ${it.message}") }
                .getOrNull()
        }.sortedWith(compareBy({ it.dayOfWeek.value }, { it.startTime }))
        dto.term?.let { _term.value = it.toDomain() }
    }

    private fun readCache(): ScheduleDto? = runCatching {
        if (!cacheFile.exists()) null
        else json.decodeFromString<ScheduleDto>(cacheFile.readText())
    }.getOrElse {
        Log.w(TAG, "Schedule cache unreadable, falling back to asset", it)
        null
    }

    private fun readAsset(): ScheduleDto? = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { r ->
            json.decodeFromString<ScheduleDto>(r.readText())
        }
    }.getOrElse {
        Log.w(TAG, "Bundled schedule unreadable", it)
        null
    }

    private fun persist(dto: ScheduleDto) {
        val payload = json.encodeToString(ScheduleDto.serializer(), dto)
        tmpFile.writeText(payload)
        if (!tmpFile.renameTo(cacheFile)) {
            cacheFile.delete()
            if (!tmpFile.renameTo(cacheFile)) {
                cacheFile.writeText(payload)
                tmpFile.delete()
            }
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────
    // Synchronous and cheap, because the tile and complication services call
    // them from their own request callbacks and must not suspend.

    fun byDay(day: DayOfWeek): List<Course> =
        _courses.value.filter { it.dayOfWeek == day }.sortedBy { it.startTime }

    /** The lecture running right now, if any. */
    fun current(day: DayOfWeek, at: LocalTime): Course? =
        byDay(day).firstOrNull { !at.isBefore(it.startTime) && at.isBefore(it.endTime) }

    /** The next lecture that has not started yet, today only. */
    fun upcoming(day: DayOfWeek, at: LocalTime): Course? =
        byDay(day).firstOrNull { it.startTime.isAfter(at) }

    private companion object {
        const val CACHE_NAME = "schedule.json"
        const val ASSET_NAME = "courses.json"
        const val TAG = "ScheduleRepository"

        /**
         * Used only until Igor supplies the real term. The start date is the
         * Monday of the current week so week arithmetic stays sane on a fresh
         * install; it is not a guess at the real semester start, and the UI
         * flags an unconfigured term rather than pretending otherwise.
         */
        val DEFAULT_TERM = Term(
            startDate = LocalDate.now().with(DayOfWeek.MONDAY).toString(),
            totalWeeks = 14,
            requiredRate = 0.70,
        )
    }
}
