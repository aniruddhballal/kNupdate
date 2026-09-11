package com.knupdate.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class ActivityRepository(
    private val client: HttpClient,
    baseUrl: String
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun feed(session: AuthSession): Result<List<ActivityResponse>> = runCatching {
        client.get("$normalizedBaseUrl/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }.requireSuccess().body()
    }

    suspend fun create(
        session: AuthSession,
        request: CreateActivityRequest
    ): Result<ActivityResponse> = runCatching {
        client.post("$normalizedBaseUrl/api/v1/activities") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()
    }
}

private suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (status.isSuccess()) return this

    val error = runCatching { body<ApiError>() }.getOrNull()
    throw IllegalStateException(error?.message ?: "The server request failed.")
}
