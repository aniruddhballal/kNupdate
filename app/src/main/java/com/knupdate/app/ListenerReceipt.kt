package com.knupdate.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object ListenerNames {
    fun valid(value: String): Boolean = value.trim().let { name ->
        name.isNotEmpty() && name.length <= 80 && name.none {
            it.code in 0..31 || it.code in 127..159 || it.code in 0x202a..0x202e || it.code in 0x2066..0x2069
        }
    }

    fun sharedName(context: Context): String? {
        val preferences = context.getSharedPreferences("knupdate_local", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("share_listener_name", false)) return null
        return preferences.getString("display_name", null)?.trim()?.takeIf(::valid)
    }
}

/** Per-update receipt IDs are unrelated to private listener session handles. */
data class ListenerReceipt(val id: String, val name: String?, val confirmedAt: Long? = null) {
    init {
        require(UUID.fromString(id).toString() == id.lowercase())
        require(name == null || (ListenerNames.valid(name) && name == name.trim()))
        require(confirmedAt == null || confirmedAt >= 0)
    }

    fun json(): JSONObject = JSONObject().put("id", id).put("name", name ?: JSONObject.NULL)
        .put("confirmedAt", confirmedAt ?: JSONObject.NULL)

    companion object {
        fun parse(array: JSONArray): List<ListenerReceipt> {
            require(array.length() <= 100)
            val result = (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                val name = if (value.isNull("name")) null else value.get("name").also { require(it is String) } as String
                val confirmedAt = if (value.isNull("confirmedAt")) null else {
                    require(value.get("confirmedAt") is Number)
                    value.getLong("confirmedAt").also { require(it.toDouble() == value.getDouble("confirmedAt")) }
                }
                ListenerReceipt(value.getString("id"), name, confirmedAt)
            }
            require(result.map { it.id }.toSet().size == result.size)
            return result
        }

        fun json(values: List<ListenerReceipt>): String = JSONArray().apply { values.forEach { put(it.json()) } }.toString()
    }
}
