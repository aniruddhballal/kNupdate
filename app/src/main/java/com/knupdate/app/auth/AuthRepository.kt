package com.knupdate.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AuthRepository(
    private val client: HttpClient,
    baseUrl: String
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun requestOtp(phoneNumber: String): Result<RequestOtpResponse> =
        runCatching {
            val response = client.post("$normalizedBaseUrl/api/v1/auth/request-otp") {
                contentType(ContentType.Application.Json)
                setBody(RequestOtpRequest(phoneNumber))
            }
            response.requireSuccess().body()
        }

    suspend fun verifyOtp(
        challengeId: String,
        otp: String,
        displayName: String
    ): Result<AuthSession> = runCatching {
        val response = client.post("$normalizedBaseUrl/api/v1/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(VerifyOtpRequest(challengeId, otp, displayName))
        }
        response.requireSuccess().body()
    }
}

private suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (status.isSuccess()) return this

    val error = runCatching { body<ApiError>() }.getOrNull()
    throw IllegalStateException(error?.message ?: "The server request failed.")
}
