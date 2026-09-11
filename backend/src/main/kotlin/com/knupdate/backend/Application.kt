package com.knupdate.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.netty.EngineMain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        })
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", environment = appEnvironment()))
        }

        post("/api/v1/auth/request-otp") {
            val request = call.receive<RequestOtpRequest>()
            if (request.phoneNumber.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(code = "invalid_phone_number", message = "A phone number is required.")
                )
                return@post
            }

            if (appEnvironment() != "development") {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ErrorResponse(
                        code = "otp_provider_not_configured",
                        message = "Production OTP delivery is not configured."
                    )
                )
                return@post
            }

            call.respond(
                RequestOtpResponse(
                    challengeId = DEVELOPMENT_CHALLENGE_ID,
                    expiresInSeconds = 300
                )
            )
        }

        post("/api/v1/auth/verify-otp") {
            val request = call.receive<VerifyOtpRequest>()
            if (appEnvironment() != "development" ||
                request.challengeId != DEVELOPMENT_CHALLENGE_ID ||
                request.otp != developmentOtp()
            ) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(code = "invalid_otp", message = "The verification code is invalid or expired.")
                )
                return@post
            }

            call.respond(
                DevAuthResponse(
                    accessToken = DEVELOPMENT_ACCESS_TOKEN,
                    refreshToken = DEVELOPMENT_REFRESH_TOKEN,
                    expiresInSeconds = 900,
                    user = DevUser(id = DEVELOPMENT_USER_ID, displayName = request.displayName)
                )
            )
        }
    }
}

private fun Application.appEnvironment(): String =
    environment.config.propertyOrNull("app.environment")?.getString() ?: "production"

private fun Application.developmentOtp(): String =
    environment.config.propertyOrNull("app.development-otp")?.getString() ?: "9620"

@Serializable
data class HealthResponse(val status: String, val environment: String)

@Serializable
data class RequestOtpRequest(val phoneNumber: String)

@Serializable
data class RequestOtpResponse(val challengeId: String, val expiresInSeconds: Int)

@Serializable
data class VerifyOtpRequest(val challengeId: String, val otp: String, val displayName: String)

@Serializable
data class DevAuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val user: DevUser
)

@Serializable
data class DevUser(val id: String, val displayName: String)

@Serializable
data class ErrorResponse(val code: String, val message: String)

private const val DEVELOPMENT_CHALLENGE_ID = "development-challenge"
private const val DEVELOPMENT_ACCESS_TOKEN = "development-access-token"
private const val DEVELOPMENT_REFRESH_TOKEN = "development-refresh-token"
private const val DEVELOPMENT_USER_ID = "00000000-0000-0000-0000-000000000001"
