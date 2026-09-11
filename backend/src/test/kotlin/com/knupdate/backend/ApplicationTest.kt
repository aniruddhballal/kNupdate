package com.knupdate.backend

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val verification = client.post("/api/v1/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"challengeId":"development-challenge","otp":"9620","displayName":"Aniruddh"}"""
            )
        }

        assertEquals(HttpStatusCode.OK, challenge.status)
        assertEquals(HttpStatusCode.OK, verification.status)
        assertTrue(verification.bodyAsText().contains("development-access-token"))
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
}
