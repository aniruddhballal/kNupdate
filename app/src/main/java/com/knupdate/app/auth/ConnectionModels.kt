package com.knupdate.app.auth

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionSummary(val id: String, val displayName: String)

@Serializable
data class CreateConnectionCodeResponse(val code: String, val expiresInSeconds: Int)

@Serializable
data class CreateConnectionRequest(val connectionCode: String)

@Serializable
data class IncomingConnectionRequest(
    val id: String,
    val requesterDisplayName: String,
    val sharedMutualCount: Int
)
