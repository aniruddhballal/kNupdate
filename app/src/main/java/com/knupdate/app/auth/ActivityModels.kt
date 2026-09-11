package com.knupdate.app.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateActivityRequest(
    @SerialName("activity_type") val activityType: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("quantity_type") val quantityType: String? = null,
    @SerialName("quantity_value") val quantityValue: Double? = null
)

@Serializable
data class ActivityResponse(
    val id: String,
    @SerialName("activity_type") val activityType: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("quantity_type") val quantityType: String? = null,
    @SerialName("quantity_value") val quantityValue: Double? = null,
    @SerialName("quantity_unit") val quantityUnit: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("author_display_name") val authorDisplayName: String,
    @SerialName("rendered_text") val renderedText: String
)
