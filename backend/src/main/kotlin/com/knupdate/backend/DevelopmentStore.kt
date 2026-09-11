package com.knupdate.backend

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DevelopmentStore(
    private val otp: String,
    private val seedUserIds: Set<String>
) {
    private val challenges = ConcurrentHashMap<String, PendingChallenge>()
    private val users = ConcurrentHashMap<String, DevUser>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val connectionCodes = ConcurrentHashMap<String, ConnectionCode>()
    private val requests = ConcurrentHashMap<String, StoredConnectionRequest>()
    private val connections = ConcurrentHashMap.newKeySet<String>()

    fun requestOtp(phoneNumber: String): RequestOtpResponse {
        val challengeId = UUID.randomUUID().toString()
        challenges[challengeId] = PendingChallenge(
            normalizedPhone = normalizePhone(phoneNumber),
            expiresAt = Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS)
        )
        return RequestOtpResponse(challengeId, CHALLENGE_TTL_SECONDS.toInt())
    }

    fun verifyOtp(request: VerifyOtpRequest): DevAuthResponse? {
        val challenge = challenges.remove(request.challengeId) ?: return null
        if (challenge.expiresAt.isBefore(Instant.now()) || request.otp != otp) return null

        val userId = UUID.nameUUIDFromBytes(
            "development:${challenge.normalizedPhone}".toByteArray(StandardCharsets.UTF_8)
        ).toString()
        val user = DevUser(userId, request.displayName)
        users[userId] = user

        val accessToken = "development-${UUID.randomUUID()}"
        sessions[accessToken] = Session(userId, Instant.now().plusSeconds(SESSION_TTL_SECONDS))
        return DevAuthResponse(
            accessToken = accessToken,
            refreshToken = "development-refresh-${UUID.randomUUID()}",
            expiresInSeconds = SESSION_TTL_SECONDS.toInt(),
            user = user
        )
    }

    fun userForAccessToken(accessToken: String): DevUser? {
        val session = sessions[accessToken] ?: return null
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(accessToken)
            return null
        }
        return users[session.userId]
    }

    fun createConnectionCode(ownerId: String): CreateConnectionCodeResponse {
        val code = UUID.randomUUID().toString().replace("-", "").take(CODE_LENGTH).uppercase()
        connectionCodes[hash(code)] = ConnectionCode(
            ownerId = ownerId,
            expiresAt = Instant.now().plusSeconds(CONNECTION_CODE_TTL_SECONDS)
        )
        return CreateConnectionCodeResponse(code, CONNECTION_CODE_TTL_SECONDS.toInt())
    }

    fun createConnectionRequest(requesterId: String, code: String): StoredConnectionRequest {
        val connectionCode = connectionCodes.remove(hash(code))
            ?: throw ConnectionException("invalid_connection_code", "The connection code is invalid or expired.")
        if (connectionCode.expiresAt.isBefore(Instant.now())) {
            throw ConnectionException("invalid_connection_code", "The connection code is invalid or expired.")
        }
        if (connectionCode.ownerId == requesterId) {
            throw ConnectionException("invalid_connection", "You cannot connect with yourself.")
        }

        val pair = canonicalPair(requesterId, connectionCode.ownerId)
        if (connections.contains(pair)) {
            throw ConnectionException("already_connected", "You are already connected.")
        }
        if (requests.values.any {
                it.status == RequestStatus.PENDING &&
                    canonicalPair(it.requesterId, it.targetId) == pair
            }) {
            throw ConnectionException("request_already_pending", "A connection request is already pending.")
        }

        val sharedMutuals = mutualConnectionCount(requesterId, connectionCode.ownerId)
        val bootstrapAllowed = seedUserIds.isEmpty() ||
            requesterId in seedUserIds || connectionCode.ownerId in seedUserIds
        if (!bootstrapAllowed && sharedMutuals < REQUIRED_MUTUALS) {
            throw ConnectionException(
                "insufficient_mutual_connections",
                "At least two existing mutual connections are required."
            )
        }

        val request = StoredConnectionRequest(
            id = UUID.randomUUID().toString(),
            requesterId = requesterId,
            targetId = connectionCode.ownerId,
            createdAt = Instant.now()
        )
        requests[request.id] = request
        return request
    }

    fun incomingRequests(targetId: String): List<IncomingConnectionRequest> =
        requests.values
            .filter { it.targetId == targetId && it.status == RequestStatus.PENDING }
            .sortedByDescending { it.createdAt }
            .map {
                IncomingConnectionRequest(
                    id = it.id,
                    requesterDisplayName = users[it.requesterId]?.displayName ?: "Unknown",
                    sharedMutualCount = mutualConnectionCount(it.requesterId, it.targetId)
                )
            }

    fun acceptRequest(targetId: String, requestId: String): ConnectionSummary {
        val request = pendingRequestForTarget(targetId, requestId)
        request.status = RequestStatus.ACCEPTED
        connections += canonicalPair(request.requesterId, request.targetId)
        return summaryFor(request.targetId, request.requesterId)
    }

    fun declineRequest(targetId: String, requestId: String) {
        pendingRequestForTarget(targetId, requestId).status = RequestStatus.DECLINED
    }

    fun listConnections(userId: String): List<ConnectionSummary> =
        connections
            .mapNotNull { pair ->
                val otherId = otherUser(pair, userId) ?: return@mapNotNull null
                summaryFor(userId, otherId)
            }
            .sortedBy { it.displayName }

    fun removeConnection(userId: String, connectionId: String) {
        val other = users[connectionId]
            ?: throw ConnectionException("connection_not_found", "The connection does not exist.")
        val pair = canonicalPair(userId, other.id)
        if (!connections.remove(pair)) {
            throw ConnectionException("connection_not_found", "The connection does not exist.")
        }
    }

    private fun pendingRequestForTarget(targetId: String, requestId: String): StoredConnectionRequest {
        val request = requests[requestId]
        if (request == null || request.targetId != targetId || request.status != RequestStatus.PENDING) {
            throw ConnectionException("request_not_found", "The connection request does not exist.")
        }
        return request
    }

    private fun summaryFor(viewerId: String, otherId: String): ConnectionSummary {
        val user = users[otherId]
            ?: throw ConnectionException("user_not_found", "The connection user does not exist.")
        return ConnectionSummary(id = user.id, displayName = user.displayName)
    }

    private fun mutualConnectionCount(firstId: String, secondId: String): Int {
        val firstConnections = connections
            .mapNotNull { otherUser(it, firstId) }
            .toSet()
        val secondConnections = connections
            .mapNotNull { otherUser(it, secondId) }
            .toSet()
        return firstConnections.intersect(secondConnections).size
    }

    private fun otherUser(pair: String, userId: String): String? {
        val parts = pair.split(":")
        return when (userId) {
            parts.getOrNull(0) -> parts.getOrNull(1)
            parts.getOrNull(1) -> parts.getOrNull(0)
            else -> null
        }
    }

    private fun canonicalPair(firstId: String, secondId: String): String =
        listOf(firstId, secondId).sorted().joinToString(":")

    private fun normalizePhone(phoneNumber: String): String =
        phoneNumber.trim().filter { it.isDigit() || it == '+' }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

class ConnectionException(val code: String, override val message: String) : RuntimeException(message)

private data class PendingChallenge(val normalizedPhone: String, val expiresAt: Instant)
private data class Session(val userId: String, val expiresAt: Instant)
private data class ConnectionCode(val ownerId: String, val expiresAt: Instant)

data class StoredConnectionRequest(
    val id: String,
    val requesterId: String,
    val targetId: String,
    val createdAt: Instant,
    var status: RequestStatus = RequestStatus.PENDING
)

enum class RequestStatus { PENDING, ACCEPTED, DECLINED }

private const val CHALLENGE_TTL_SECONDS = 300L
private const val SESSION_TTL_SECONDS = 900L
private const val CONNECTION_CODE_TTL_SECONDS = 900L
private const val CODE_LENGTH = 8
private const val REQUIRED_MUTUALS = 2
