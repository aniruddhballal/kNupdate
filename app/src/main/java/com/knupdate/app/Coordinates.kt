package com.knupdate.app

import org.json.JSONObject
import java.util.Locale

/** A single fix, kept with the activity so a future map can use the local copy. */
data class Coordinates(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val capturedAt: Long) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(accuracyMeters.isFinite() && accuracyMeters >= 0)
        require(capturedAt >= 0)
    }

    fun json(): JSONObject = JSONObject().put("latitude", latitude).put("longitude", longitude)
        .put("accuracyMeters", accuracyMeters).put("capturedAt", capturedAt)

    fun text(): String = String.format(Locale.US, "%.6f, %.6f (accuracy %.0f m)", latitude, longitude, accuracyMeters)

    companion object {
        fun fromJson(json: JSONObject): Coordinates {
            require(json.keys().asSequence().toSet() == setOf("latitude", "longitude", "accuracyMeters", "capturedAt"))
            for (key in listOf("latitude", "longitude", "accuracyMeters", "capturedAt")) require(json.get(key) is Number)
            val time = json.getDouble("capturedAt")
            require(time.isFinite() && time == json.getLong("capturedAt").toDouble())
            return Coordinates(json.getDouble("latitude"), json.getDouble("longitude"),
                json.getDouble("accuracyMeters"), json.getLong("capturedAt"))
        }
    }
}
