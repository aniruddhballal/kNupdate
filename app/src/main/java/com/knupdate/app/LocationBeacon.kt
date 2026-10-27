package com.knupdate.app

import android.content.Context

/** App-wide consent for attaching a fresh location snapshot to activity updates. */
internal object LocationBeacon {
    private const val PREFERENCES = "knupdate_local"
    private const val ENABLED_KEY = "location_beacon_enabled"

    /** New installs start opted in, but the UI still shows barred until Android access is ready. */
    fun requested(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(ENABLED_KEY, true)

    fun setRequested(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED_KEY, enabled).apply()
    }

    fun active(context: Context): Boolean = requested(context) &&
        LocationCapture.permitted(context) && LocationCapture.enabled(context)
}
