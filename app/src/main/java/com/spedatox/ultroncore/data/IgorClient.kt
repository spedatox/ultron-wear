package com.spedatox.ultroncore.data

import android.util.Log
import com.spedatox.ultroncore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Igor transport.
 *
 * Deliberately `HttpURLConnection` rather than OkHttp, which is what the phone
 * client uses. This app makes three small REST calls with no streaming, no
 * interceptors and no connection pooling worth the name; OkHttp would add
 * roughly 800 KB of dex and a chunk of class loading to the cold start of a
 * watch app whose entire job is to render a list in under a second. The
 * platform client is enough.
 *
 * Every call is `Dispatchers.IO` and returns a [Result] — the watch is offline
 * often and by design, so a failed call is an ordinary outcome the caller
 * handles, never an exception that reaches the UI.
 *
 * AUTH: Igor's Rule 12 requires `X-API-Key` on every endpoint. The key is
 * injected at build time from `local.properties` (see app/build.gradle.kts), so
 * it is never committed. A build with no key configured degrades to offline-only
 * rather than shipping a placeholder that would 401 on every call.
 */
class IgorClient(
    private val baseUrl: String = BuildConfig.IGOR_BASE_URL,
    private val apiKey: String = BuildConfig.IGOR_API_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** Pull the current schedule + term parameters. */
    suspend fun fetchSchedule(): Result<ScheduleDto> =
        request("GET", "/academic/schedule", body = null) { raw ->
            json.decodeFromString<ScheduleDto>(raw)
        }

    /**
     * Push unsynced answers and pull anything Igor knows that the watch does
     * not. One round trip so a brief window of connectivity settles both
     * directions.
     */
    suspend fun syncAttendance(
        device: String,
        records: List<AttendanceRecord>,
    ): Result<AttendanceSyncResponse> {
        val payload = AttendanceSyncRequest(
            device = device,
            records = records.map(AttendanceSyncDto::from),
        )
        return request("POST", "/academic/attendance", json.encodeToString(payload)) { raw ->
            json.decodeFromString<AttendanceSyncResponse>(raw)
        }
    }

    /** Hand Igor this watch's Firebase Installation ID so it can address the
     *  attendance ask. */
    suspend fun registerDevice(device: String, fid: String, token: String?): Result<Unit> {
        val payload = DeviceRegisterRequest(device = device, fid = fid, token = token)
        return request("POST", "/devices/register", json.encodeToString(payload)) { }
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: String?,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Igor endpoint not configured"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // Short timeouts on purpose: this runs opportunistically in the
                // background. Hanging for 30s on a dead network keeps a radio
                // and a wakelock alive for nothing.
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("X-API-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            body?.let { conn.outputStream.use { os -> os.write(it.toByteArray()) } }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                Log.w(TAG, "$method $path -> $code $err")
                return@withContext Result.failure(IgorHttpException(code, err))
            }
            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            Result.success(parse(raw))
        } catch (e: Exception) {
            Log.w(TAG, "$method $path failed: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private companion object { const val TAG = "IgorClient" }
}

class IgorHttpException(val status: Int, val body: String) :
    Exception("Igor returned $status: ${body.take(200)}")
