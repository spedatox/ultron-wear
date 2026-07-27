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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * The attendance ledger on disk.
 *
 * Deliberately a flat JSON file rather than Room. A full semester is ~14 weeks ×
 * ~13 teaching hours ≈ 180 records ≈ 20 KB — small enough to hold in memory and
 * rewrite whole on every change. Room would add a KSP processor, a schema, a
 * migration surface and several hundred KB of dex to this watch's cold start in
 * exchange for indexing a list that never needs an index.
 *
 * Every write is atomic (temp file + rename) and serialised behind a [Mutex].
 * The ledger is the only durable record of whether you can still pass a course;
 * a half-written file after the watch dies mid-save is not an acceptable
 * failure mode.
 */
class AttendanceStore(context: Context) {

    private val dir = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val tmp = File(dir, "$FILE_NAME.tmp")
    private val writeLock = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Keyed by [AttendanceRecord.key] (`slotId@date`) so re-answering an
     *  occurrence overwrites instead of double-counting. */
    private val _records = MutableStateFlow<Map<String, AttendanceRecord>>(emptyMap())
    val records: StateFlow<Map<String, AttendanceRecord>> = _records.asStateFlow()

    @Serializable
    private data class Ledger(val version: Int = 1, val records: List<AttendanceRecord> = emptyList())

    /** Read the ledger into memory. Safe to call more than once. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            _records.value = emptyMap()
            return@withContext
        }
        try {
            val ledger = json.decodeFromString<Ledger>(file.readText())
            _records.value = ledger.records.associateBy { it.key }
        } catch (e: Exception) {
            // A corrupt ledger must not brick the app, but it also must not be
            // silently discarded — that would erase a semester of absences. Keep
            // the bad file for recovery and start clean.
            Log.e(TAG, "Ledger unreadable; quarantining", e)
            runCatching { file.copyTo(File(dir, "$FILE_NAME.corrupt"), overwrite = true) }
            _records.value = emptyMap()
        }
    }

    /**
     * Answer one occurrence. Overwrites any previous answer for the same
     * `(slotId, date)` — correcting yourself is expected, duplicating is not.
     */
    suspend fun record(
        slotId: String,
        courseCode: String,
        date: LocalDate,
        status: AttendanceStatus,
    ): AttendanceRecord {
        val rec = AttendanceRecord(
            slotId = slotId,
            courseCode = courseCode,
            date = date.toString(),
            status = status,
            recordedAt = System.currentTimeMillis(),
            synced = false,
        )
        mutate { it + (rec.key to rec) }
        return rec
    }

    /** Flip the sync flag once Igor has acknowledged these records. */
    suspend fun markSynced(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val set = keys.toHashSet()
        mutate { current ->
            current.mapValues { (k, v) -> if (k in set) v.copy(synced = true) else v }
        }
    }

    /**
     * Merge records that came down from Igor (answered on the phone, or edited
     * server-side). Newest [AttendanceRecord.recordedAt] wins — the same
     * last-write-wins rule both ends apply, so the two converge.
     */
    suspend fun mergeFromServer(incoming: List<AttendanceRecord>) {
        if (incoming.isEmpty()) return
        mutate { current ->
            val merged = current.toMutableMap()
            for (rec in incoming) {
                val existing = merged[rec.key]
                if (existing == null || rec.recordedAt > existing.recordedAt) {
                    merged[rec.key] = rec.copy(synced = true)
                }
            }
            merged
        }
    }

    /** Records Igor has not acknowledged yet — the outbound sync queue. */
    fun pendingSync(): List<AttendanceRecord> = _records.value.values.filterNot { it.synced }

    private suspend fun mutate(
        transform: (Map<String, AttendanceRecord>) -> Map<String, AttendanceRecord>,
    ) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val next = transform(_records.value)
            persist(next)
            _records.value = next
        }
    }

    /** Temp-file + rename. `renameTo` within one filesystem is atomic, so a
     *  reader either sees the whole old ledger or the whole new one. */
    private fun persist(records: Map<String, AttendanceRecord>) {
        val payload = json.encodeToString(Ledger(records = records.values.sortedBy { it.date }))
        tmp.writeText(payload)
        if (!tmp.renameTo(file)) {
            // renameTo can fail if the destination exists on some filesystems.
            file.delete()
            if (!tmp.renameTo(file)) {
                Log.e(TAG, "Atomic rename failed; falling back to direct write")
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "attendance.json"
        const val TAG = "AttendanceStore"
    }
}
