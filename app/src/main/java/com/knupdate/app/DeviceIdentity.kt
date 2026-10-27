package com.knupdate.app

import android.content.Context
import java.util.UUID

data class DeviceCredentials(val id: String, val token: String)

/** Anonymous app identity. It is not an Android hardware identifier and contains no user details. */
object DeviceIdentity {
    private const val PREFS = "knupdate_local"
    private const val DEVICE_ID = "device_id"
    private const val DEVICE_TOKEN = "device_token"

    fun credentials(context: Context): DeviceCredentials {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = preferences.getString(DEVICE_ID, null)
        var token = preferences.getString(DEVICE_TOKEN, null)
        if (id.isNullOrBlank() || token.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            token = UUID.randomUUID().toString()
            check(preferences.edit().putString(DEVICE_ID, id).putString(DEVICE_TOKEN, token).commit()) {
                "Could not save this device identity."
            }
        }
        return DeviceCredentials(id.lowercase(), token.lowercase())
    }

    fun displayName(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("display_name", null)?.trim()?.takeIf(ListenerNames::valid) ?: "kNupdate device"
}

data class PairedDevice(val id: String, val name: String?, val createdAt: Long?) {
    fun label(): String = name ?: "Unnamed device"
}
