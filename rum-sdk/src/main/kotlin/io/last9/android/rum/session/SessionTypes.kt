package io.last9.android.rum.session

internal enum class SessionState { ACTIVE, ENDED, EXPIRED }

internal data class SessionInfo(
    val id: String,
    val previousId: String?,
    val startedAt: Long,
    val lastActivityAt: Long,
    val state: SessionState,
)

internal data class PersistedSession(
    val id: String,
    val previousId: String?,
    val startedAt: Long,
    val lastActivityAt: Long,
)

/**
 * User identity information injected on all spans.
 * Set via [io.last9.android.rum.Last9RumInstance.identify].
 */
data class UserInfo(
    val id: String? = null,
    val name: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val roles: List<String>? = null,
    val ipLocation: String? = null,
    val cityName: String? = null,
    /** Arbitrary key-value pairs attached as span attributes under the `user.*` namespace. */
    val customAttributes: Map<String, String>? = null,
)
