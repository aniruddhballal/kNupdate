package com.knupdate.backend

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ApplicationTest {
    @Test
    fun healthEndpointReturnsDevelopmentStatus() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun developmentOtpAuthenticatesAnyPhoneNumber() = testApplication {
        environment {
            config = MapApplicationConfig(
                "app.environment" to "development",
                "app.development-otp" to "9620"
            )
        }
        application { module() }

        val challenge = client.post("/api/v1/auth/request-otp") {
            contentType(ContentType.Application.Json)
            setBody("""{"phoneNumber":"+919876543210"}""")
        }
        val challengeBody = Json.decodeFromString<RequestOtpResponse>(challenge.bodyAsText())
        val verification = client.post("/api/v1/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"challengeId":"${challengeBody.challengeId}","otp":"9620","displayName":"Aniruddh"}"""
            )
        }

        assertEquals(HttpStatusCode.OK, challenge.status)
        assertEquals(HttpStatusCode.OK, verification.status)
        assertTrue(verification.bodyAsText().contains("\"accessToken\":"))
    }

    @Test
    fun developmentOtpIsRejectedOutsideDevelopment() = testApplication {
        environment {
            config = MapApplicationConfig("app.environment" to "production")
        }
        application { module() }

        val response = client.post("/api/v1/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"challengeId":"development-challenge","otp":"9620","displayName":"Aniruddh"}"""
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun twoDistinctUsersCanCreateAndAcceptAConnection() = testApplication {
        environment {
            config = MapApplicationConfig(
                "app.environment" to "development",
                "app.development-otp" to "9620"
            )
        }
        application { module() }

        val alice = client.authenticate("+919876543210", "Alice")
        val bob = client.authenticate("+919876543211", "Bob")
        assertTrue(alice.user.id != bob.user.id)

        val codeResponse = client.post("/api/v1/connection-codes") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        val code = Json.decodeFromString<CreateConnectionCodeResponse>(codeResponse.bodyAsText())

        val requestResponse = client.post("/api/v1/connection-requests") {
            header(HttpHeaders.Authorization, "Bearer ${alice.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"connectionCode":"${code.code}"}""")
        }
        assertEquals(HttpStatusCode.Accepted, requestResponse.status)

        val incomingResponse = client.get("/api/v1/connection-requests/incoming") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        val incoming = Json.decodeFromString<List<IncomingConnectionRequest>>(incomingResponse.bodyAsText())
        assertEquals(1, incoming.size)
        assertEquals("Alice", incoming.single().requesterDisplayName)

        val acceptResponse = client.post("/api/v1/connection-requests/${incoming.single().id}/accept") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, acceptResponse.status)

        val connectionsResponse = client.get("/api/v1/connections") {
            header(HttpHeaders.Authorization, "Bearer ${alice.accessToken}")
        }
        val connections = Json.decodeFromString<List<ConnectionSummary>>(connectionsResponse.bodyAsText())
        assertEquals(listOf(ConnectionSummary(bob.user.id, "Bob")), connections)
    }

    @Test
    fun activityIsStructuredAndVisibleOnlyToAConnectedUser() = testApplication {
        environment {
            config = MapApplicationConfig(
                "app.environment" to "development",
                "app.development-otp" to "9620"
            )
        }
        application { module() }

        val alice = client.authenticate("+919876543220", "Alice")
        val bob = client.authenticate("+919876543221", "Bob")
        val stranger = client.authenticate("+919876543222", "Stranger")

        val codeResponse = client.post("/api/v1/connection-codes") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        val code = Json.decodeFromString<CreateConnectionCodeResponse>(codeResponse.bodyAsText())
        client.post("/api/v1/connection-requests") {
            header(HttpHeaders.Authorization, "Bearer ${alice.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"connectionCode":"${code.code}"}""")
        }
        val incomingResponse = client.get("/api/v1/connection-requests/incoming") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        val incoming = Json.decodeFromString<List<IncomingConnectionRequest>>(incomingResponse.bodyAsText())
        client.post("/api/v1/connection-requests/${incoming.single().id}/accept") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }

        val activityResponse = client.post("/api/v1/activities") {
            header(HttpHeaders.Authorization, "Bearer ${alice.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"activity_type":"run","started_at":"2026-09-11T09:30:00+05:30","ended_at":"2026-09-11T10:00:00+05:30","quantity_type":"distance","quantity_value":5}"""
            )
        }
        assertEquals(HttpStatusCode.OK, activityResponse.status)
        val activity = Json.decodeFromString<ActivityResponse>(activityResponse.bodyAsText())
        assertEquals("Alice completed an activity — Run · 5 km · 9:30 AM–10:00 AM", activity.renderedText)

        val bobFeedResponse = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer ${bob.accessToken}")
        }
        val bobFeed = Json.decodeFromString<List<ActivityResponse>>(bobFeedResponse.bodyAsText())
        assertEquals(listOf(activity.id), bobFeed.map(ActivityResponse::id))

        val strangerFeedResponse = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer ${stranger.accessToken}")
        }
        assertEquals("[]", strangerFeedResponse.bodyAsText())
    }

    private suspend fun io.ktor.client.HttpClient.authenticate(
        phoneNumber: String,
        displayName: String
    ): DevAuthResponse {
        val challengeResponse = post("/api/v1/auth/request-otp") {
            contentType(ContentType.Application.Json)
            setBody("""{"phoneNumber":"$phoneNumber"}""")
        }
        val challenge = Json.decodeFromString<RequestOtpResponse>(challengeResponse.bodyAsText())
        val verificationResponse = post("/api/v1/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"challengeId":"${challenge.challengeId}","otp":"9620","displayName":"$displayName"}"""
            )
        }
        return Json.decodeFromString(verificationResponse.bodyAsText())
    }
}
