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
    val legacy: Boolean = false
) {
    // Explicit wire schema: display names and local metadata never leave the phone.
    fun wireJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("body", body)
        .put("startAt", startAt).put("endAt", endAt ?: JSONObject.NULL).put("timeZone", timeZone)

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
            return ActivityRecord(id, title, body, start, end, zone, direction = "received", status = "received")
        }
    }
}

class ActivityStore internal constructor(context: Context, databaseName: String = "activities.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    private val preferences = context.getSharedPreferences("knupdate_local", Context.MODE_PRIVATE)
    private val mutableRecords = MutableStateFlow<List<ActivityRecord>>(emptyList())
    val records = mutableRecords.asStateFlow()
    private var loaded = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE activities (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
            start_at INTEGER NOT NULL, end_at INTEGER, time_zone TEXT NOT NULL,
            created_at INTEGER NOT NULL, direction TEXT NOT NULL, status TEXT NOT NULL,
            listeners INTEGER, legacy INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX activity_order ON activities(created_at DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun load() {
        if (loaded) return
        migrateLegacy()
        // A killed process cannot know whether a POST reached the relay. Never resend silently.
        writableDatabase.execSQL("UPDATE activities SET status = 'uncertain' WHERE status = 'sending'")
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

    private fun values(record: ActivityRecord) = ContentValues().apply {
        put("id", record.id); put("title", record.title); put("body", record.body)
        put("start_at", record.startAt); put("end_at", record.endAt); put("time_zone", record.timeZone)
        put("created_at", record.createdAt); put("direction", record.direction); put("status", record.status)
        put("listeners", record.listeners); put("legacy", if (record.legacy) 1 else 0)
    }

    private fun refresh() {
        mutableRecords.value = readableDatabase.rawQuery("SELECT * FROM activities ORDER BY created_at DESC, rowid DESC", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(ActivityRecord(
                        id = cursor.getString(0), title = cursor.getString(1), body = cursor.getString(2),
                        startAt = cursor.getLong(3), endAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                        timeZone = cursor.getString(5), createdAt = cursor.getLong(6), direction = cursor.getString(7),
                        status = cursor.getString(8), listeners = if (cursor.isNull(9)) null else cursor.getInt(9), legacy = cursor.getInt(10) == 1
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
                    createdAt = midnight - index, status = "local", legacy = true
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
