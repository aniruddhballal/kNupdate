package com.knupdate.app

import android.content.Context

enum class DeliveryMode(val wireValue: String, val label: String) {
    LEAF("leaf", "Paired only"),
    TREE("tree", "All listeners")
}

object DeliveryScope {
    private const val PREFS = "knupdate_local"
    private const val MODE_KEY = "delivery_mode"

    fun get(context: Context): DeliveryMode = when (
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE_KEY, DeliveryMode.LEAF.wireValue)
    ) {
        DeliveryMode.TREE.wireValue -> DeliveryMode.TREE
        else -> DeliveryMode.LEAF
    }

    fun set(context: Context, mode: DeliveryMode) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE_KEY, mode.wireValue).commit()) { "Could not save delivery mode." }
    }
}
