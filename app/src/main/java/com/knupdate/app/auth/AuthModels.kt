package com.knupdate.app.auth

import kotlinx.serialization.Serializable

@Serializable
data class RequestOtpRequest(val phoneNumber: String)

@Serializable
data class RequestOtpResponse(val challengeId: String, val expiresInSeconds: Int)

@Serializable
data class VerifyOtpRequest(
    val challengeId: String,
    val otp: String,
    val displayName: String
)

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val user: AuthUser
)

@Serializable
data class AuthUser(val id: String, val displayName: String)

@Serializable
data class ApiError(val code: String, val message: String)
