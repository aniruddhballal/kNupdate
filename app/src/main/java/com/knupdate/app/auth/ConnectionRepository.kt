package com.knupdate.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class ConnectionRepository(
    private val client: HttpClient,
    baseUrl: String
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun listConnections(session: AuthSession): Result<List<ConnectionSummary>> =
        runCatching {
            client.get("$normalizedBaseUrl/api/v1/connections") {
                bearer(session.accessToken)
            }.requireSuccess().body()
        }

    suspend fun createCode(session: AuthSession): Result<CreateConnectionCodeResponse> =
        runCatching {
            client.post("$normalizedBaseUrl/api/v1/connection-codes") {
                bearer(session.accessToken)
            }.requireSuccess().body()
        }

    suspend fun requestConnection(
        session: AuthSession,
        connectionCode: String
    ): Result<Unit> = runCatching {
        client.post("$normalizedBaseUrl/api/v1/connection-requests") {
            bearer(session.accessToken)
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(connectionCode))
        }.requireSuccess()
    }

    suspend fun incomingRequests(
        session: AuthSession
    ): Result<List<IncomingConnectionRequest>> = runCatching {
        client.get("$normalizedBaseUrl/api/v1/connection-requests/incoming") {
            bearer(session.accessToken)
        }.requireSuccess().body()
    }

    suspend fun acceptRequest(session: AuthSession, requestId: String): Result<Unit> =
        runCatching {
            client.post("$normalizedBaseUrl/api/v1/connection-requests/$requestId/accept") {
                bearer(session.accessToken)
            }.requireSuccess()
        }

    suspend fun declineRequest(session: AuthSession, requestId: String): Result<Unit> =
        runCatching {
            client.post("$normalizedBaseUrl/api/v1/connection-requests/$requestId/decline") {
                bearer(session.accessToken)
            }.requireSuccess()
        }

    suspend fun removeConnection(session: AuthSession, connectionId: String): Result<Unit> =
        runCatching {
            client.delete("$normalizedBaseUrl/api/v1/connections/$connectionId") {
                bearer(session.accessToken)
            }.requireSuccess()
        }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}

private suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (status.isSuccess()) return this

    val error = runCatching { body<ApiError>() }.getOrNull()
    throw IllegalStateException(error?.message ?: "The server request failed.")
}
