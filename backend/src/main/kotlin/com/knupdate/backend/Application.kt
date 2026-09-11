package com.knupdate.backend

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.request.header
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.netty.EngineMain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    if (persistenceMode() == "postgres") {
        val jdbcUrl = environment.config.propertyOrNull("database.jdbc-url")?.getString()
            ?: error("DATABASE_URL is required when PERSISTENCE_MODE=postgres.")
        DatabaseMigrator(jdbcUrl).migrate()
    }

    val developmentStore = DevelopmentStore(
        otp = developmentOtp(),
        seedUserIds = configuredSeedUserIds()
    )

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

            call.respond(developmentStore.requestOtp(request.phoneNumber))
        }

        post("/api/v1/auth/verify-otp") {
            val request = call.receive<VerifyOtpRequest>()
            val authResponse = if (appEnvironment() == "development") {
                developmentStore.verifyOtp(request)
            } else {
                null
            }
            if (authResponse == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(code = "invalid_otp", message = "The verification code is invalid or expired.")
                )
                return@post
            }

            call.respond(authResponse)
        }

        route("/api/v1") {
            post("/activities") {
                val user = call.devUserOrNull(developmentStore) ?: return@post
                val request = call.receive<CreateActivityRequest>()
                try {
                    call.respond(developmentStore.createActivity(user.id, request))
                } catch (error: ActivityException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error.code, error.message)
                    )
                }
            }

            get("/feed") {
                val user = call.devUserOrNull(developmentStore) ?: return@get
                call.respond(developmentStore.feedFor(user.id))
            }

            get("/me/activities") {
                val user = call.devUserOrNull(developmentStore) ?: return@get
                call.respond(developmentStore.ownActivitiesFor(user.id))
            }

            get("/connections") {
                val user = call.devUserOrNull(developmentStore) ?: return@get
                call.respond(developmentStore.listConnections(user.id))
            }

            post("/connection-codes") {
                val user = call.devUserOrNull(developmentStore) ?: return@post
                call.respond(developmentStore.createConnectionCode(user.id))
            }

            post("/connection-requests") {
                val user = call.devUserOrNull(developmentStore) ?: return@post
                val request = call.receive<CreateConnectionRequest>()
                try {
                    developmentStore.createConnectionRequest(user.id, request.connectionCode)
                    call.respond(HttpStatusCode.Accepted)
                } catch (error: ConnectionException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error.code, error.message)
                    )
                }
            }

            get("/connection-requests/incoming") {
                val user = call.devUserOrNull(developmentStore) ?: return@get
                call.respond(developmentStore.incomingRequests(user.id))
            }

            post("/connection-requests/{id}/accept") {
                val user = call.devUserOrNull(developmentStore) ?: return@post
                val requestId = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_request_id", "A request ID is required.")
                    )
                try {
                    call.respond(developmentStore.acceptRequest(user.id, requestId))
                } catch (error: ConnectionException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error.code, error.message)
                    )
                }
            }

            post("/connection-requests/{id}/decline") {
                val user = call.devUserOrNull(developmentStore) ?: return@post
                val requestId = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_request_id", "A request ID is required.")
                    )
                try {
                    developmentStore.declineRequest(user.id, requestId)
                    call.respond(HttpStatusCode.NoContent)
                } catch (error: ConnectionException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error.code, error.message)
                    )
                }
            }

            delete("/connections/{id}") {
                val user = call.devUserOrNull(developmentStore) ?: return@delete
                val connectionId = call.parameters["id"]
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_connection_id", "A connection ID is required.")
                    )
                try {
                    developmentStore.removeConnection(user.id, connectionId)
                    call.respond(HttpStatusCode.NoContent)
                } catch (error: ConnectionException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(error.code, error.message)
                    )
                }
            }
        }
    }
}

private fun Application.appEnvironment(): String =
    environment.config.propertyOrNull("app.environment")?.getString() ?: "production"

private fun Application.developmentOtp(): String =
    environment.config.propertyOrNull("app.development-otp")?.getString() ?: "9620"

private fun Application.configuredSeedUserIds(): Set<String> =
    environment.config.propertyOrNull("app.seed-user-ids")
        ?.getString()
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()

private fun Application.persistenceMode(): String =
    environment.config.propertyOrNull("database.persistence-mode")?.getString() ?: "memory"

private suspend fun ApplicationCall.devUserOrNull(store: DevelopmentStore): DevUser? {
    val authorization = request.header(HttpHeaders.Authorization)
    val accessToken = authorization
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val user = accessToken?.let(store::userForAccessToken)
    if (user != null) return user

    respond(
        HttpStatusCode.Unauthorized,
        ErrorResponse("unauthorized", "A valid session is required.")
    )
    return null
}

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
