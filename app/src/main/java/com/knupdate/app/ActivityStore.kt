package com.knupdate.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ActivityRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val startAt: Long,
    val endAt: Long?,
    val timeZone: String = ZoneId.systemDefault().id,
    val createdAt: Long = System.currentTimeMillis(),
    val direction: String = "sent",
    val status: String = "sending",
    val listeners: Int? = null,
    val legacy: Boolean = false,
    val coordinates: Coordinates? = null,
    val recipients: List<ListenerReceipt>? = null,
    val receiptKey: String? = if (direction == "sent") UUID.randomUUID().toString() else null,
    val receiptExpiresAt: Long? = null
) {
    // Activity payloads exclude local recipient logs and private receipt keys.
    fun wireJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("body", body)
        .put("startAt", startAt).put("endAt", endAt ?: JSONObject.NULL).put("timeZone", timeZone)
        .also { json -> coordinates?.let { json.put("coordinates", it.json()) } }

    fun timeText(): String {
        val zone = ZoneId.of(timeZone)
        val start = ActivityTime.text(startAt, zone, includeDate = !legacy)
        return start + (endAt?.let { " - ${ActivityTime.text(it, zone, includeDate = !legacy)}" } ?: "") +
            if (legacy) " (date unavailable)" else " ($timeZone)"
    }

    companion object {
        fun fromWire(json: JSONObject): ActivityRecord {
            val id = json.getString("id")
            require(UUID.fromString(id).toString() == id.lowercase())
            val title = json.getString("title")
            val body = json.getString("body")
            val start = json.getLong("startAt")
            val end = if (json.isNull("endAt")) null else json.getLong("endAt")
            val zone = json.getString("timeZone")
            require(title.isNotBlank() && title.length <= 80 && body.isNotBlank() && body.length <= 280)
            require(start >= 0 && start <= System.currentTimeMillis() + 60_000)
            require(end == null || (end > start && end <= System.currentTimeMillis() + 60_000))
            ZoneId.of(zone)
            val coordinates = if (json.has("coordinates")) Coordinates.fromJson(json.getJSONObject("coordinates")) else null
            require(coordinates == null || (end == null && coordinates.capturedAt >= start &&
                coordinates.capturedAt <= System.currentTimeMillis() + 60_000))
            return ActivityRecord(id, title, body, start, end, zone, direction = "received", status = "received", coordinates = coordinates)
        }
    }
}

class ActivityStore internal constructor(context: Context, databaseName: String = "activities.db") : SQLiteOpenHelper(context, databaseName, null, 3) {
    private val preferences = context.getSharedPreferences("knupdate_local", Context.MODE_PRIVATE)
    private val mutableRecords = MutableStateFlow<List<ActivityRecord>>(emptyList())
    val records = mutableRecords.asStateFlow()
    private var loaded = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE activities (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
            start_at INTEGER NOT NULL, end_at INTEGER, time_zone TEXT NOT NULL,
            created_at INTEGER NOT NULL, direction TEXT NOT NULL, status TEXT NOT NULL,
            listeners INTEGER, legacy INTEGER NOT NULL DEFAULT 0, coordinates TEXT,
            recipients TEXT, receipt_key TEXT, receipt_expires_at INTEGER)""")
        db.execSQL("CREATE INDEX activity_order ON activities(created_at DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE activities ADD COLUMN coordinates TEXT")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE activities ADD COLUMN recipients TEXT")
            db.execSQL("ALTER TABLE activities ADD COLUMN receipt_key TEXT")
            db.execSQL("ALTER TABLE activities ADD COLUMN receipt_expires_at INTEGER")
        }
    }

    @Synchronized fun load() {
        if (loaded) return
        migrateLegacy()
        // A killed process cannot know whether a POST reached the relay. Never resend silently.
        writableDatabase.execSQL("UPDATE activities SET status = 'uncertain' WHERE status = 'sending'")
        // Schedules belong to a user-started foreground service, never a reboot queue.
        writableDatabase.execSQL("UPDATE activities SET status = 'missed' WHERE status IN ('scheduled', 'locating')")
        refresh()
        loaded = true
    }

    @Synchronized fun insert(record: ActivityRecord): Boolean {
        load()
        val inserted = writableDatabase.insertWithOnConflict("activities", null, values(record), SQLiteDatabase.CONFLICT_IGNORE) != -1L
        refresh()
        return inserted
    }

    @Synchronized fun updateStatus(id: String, status: String, listeners: Int? = null) {
        val values = ContentValues().apply {
            put("status", status)
            if (listeners != null) put("listeners", listeners)
        }
        writableDatabase.update("activities", values, "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized fun capture(id: String, coordinates: Coordinates) {
        val values = ContentValues().apply {
            put("coordinates", coordinates.json().toString())
            put("status", "sending")
        }
        writableDatabase.update("activities", values, "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized fun recordAcceptance(id: String, response: JSONObject) {
        val count = response.getInt("listeners")
        require(count in 0..100)
        val recipients = if (response.has("recipients")) ListenerReceipt.parse(response.getJSONArray("recipients")) else null
        require(recipients == null || recipients.size == count)
        val expiry = if (response.isNull("receiptExpiresAt") || recipients == null || count == 0) null else response.getLong("receiptExpiresAt")
        val values = ContentValues().apply {
            put("status", "sent"); put("listeners", count)
            put("recipients", recipients?.let(ListenerReceipt::json)); put("receipt_expires_at", expiry)
            if (expiry == null) putNull("receipt_key")
        }
        writableDatabase.update("activities", values, "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized fun applyReceiptReport(id: String, key: String, response: JSONObject) {
        val record = records.value.firstOrNull { it.id == id && it.receiptKey == key } ?: return
        val original = record.recipients ?: return
        val updated = ListenerReceipt.parse(response.getJSONArray("recipients"))
        require(updated.size == original.size && updated.associate { it.id to it.name } == original.associate { it.id to it.name })
        val byId = updated.associateBy { it.id }
        val merged = original.map { recipient -> recipient.copy(confirmedAt = recipient.confirmedAt ?: byId.getValue(recipient.id).confirmedAt) }
        val values = ContentValues().apply { put("recipients", ListenerReceipt.json(merged)) }
        writableDatabase.update("activities", values, "id = ?", arrayOf(id))
        refresh()
    }

    @Synchronized fun finishReceiptTracking(id: String) {
        val values = ContentValues().apply { putNull("receipt_key") }
        writableDatabase.update("activities", values, "id = ?", arrayOf(id))
        refresh()
    }

    private fun values(record: ActivityRecord) = ContentValues().apply {
        put("id", record.id); put("title", record.title); put("body", record.body)
        put("start_at", record.startAt); put("end_at", record.endAt); put("time_zone", record.timeZone)
        put("created_at", record.createdAt); put("direction", record.direction); put("status", record.status)
        put("listeners", record.listeners); put("legacy", if (record.legacy) 1 else 0)
        put("coordinates", record.coordinates?.json()?.toString())
        put("recipients", record.recipients?.let(ListenerReceipt::json))
        put("receipt_key", record.receiptKey); put("receipt_expires_at", record.receiptExpiresAt)
    }

    private fun refresh() {
        mutableRecords.value = readableDatabase.rawQuery("SELECT * FROM activities ORDER BY created_at DESC, rowid DESC", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(ActivityRecord(
                        id = cursor.getString(0), title = cursor.getString(1), body = cursor.getString(2),
                        startAt = cursor.getLong(3), endAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                        timeZone = cursor.getString(5), createdAt = cursor.getLong(6), direction = cursor.getString(7),
                        status = cursor.getString(8), listeners = if (cursor.isNull(9)) null else cursor.getInt(9), legacy = cursor.getInt(10) == 1,
                        coordinates = if (cursor.isNull(11)) null else Coordinates.fromJson(JSONObject(cursor.getString(11))),
                        recipients = if (cursor.isNull(12)) null else ListenerReceipt.parse(JSONArray(cursor.getString(12))),
                        receiptKey = if (cursor.isNull(13)) null else cursor.getString(13),
                        receiptExpiresAt = if (cursor.isNull(14)) null else cursor.getLong(14)
                    ))
                }
            }
        }
    }

    private fun migrateLegacy() {
        val raw = preferences.getString("updates", null) ?: return
        val old = JSONArray(raw)
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (index in 0 until old.length()) {
                val item = old.getJSONObject(index)
                val record = ActivityRecord(
                    id = "legacy-$index", title = item.getString("title"), body = item.getString("body"),
                    startAt = midnight + item.getInt("start") * 60_000L,
                    endAt = if (item.isNull("end")) null else midnight + item.getInt("end") * 60_000L,
                    createdAt = midnight - index, status = "local", legacy = true, receiptKey = null
                )
                db.insertWithOnConflict("activities", null, values(record), SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        check(preferences.edit().remove("updates").commit()) { "Could not finish local history migration." }
    }

    companion object {
        @Volatile private var instance: ActivityStore? = null
        fun get(context: Context): ActivityStore = instance ?: synchronized(this) {
            instance ?: ActivityStore(context.applicationContext).also { instance = it }
        }
    }
}
