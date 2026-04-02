package io.last9.android.rum.internal

import io.opentelemetry.api.common.AttributeKey

/**
 * Centralized attribute key constants matching the browser SDK's semantic-conventions.ts.
 * Ensures attribute names are identical across platforms.
 */
internal object SemanticConventions {
    // Session
    val SESSION_ID: AttributeKey<String> = AttributeKey.stringKey("session.id")
    val SESSION_PREVIOUS_ID: AttributeKey<String> = AttributeKey.stringKey("session.previous_id")
    val SESSION_TIME_SPENT: AttributeKey<Long> = AttributeKey.longKey("session.time_spent")

    // View
    val VIEW_ID: AttributeKey<String> = AttributeKey.stringKey("view.id")
    val VIEW_NAME: AttributeKey<String> = AttributeKey.stringKey("view.name")
    val VIEW_TIME_SPENT: AttributeKey<Long> = AttributeKey.longKey("view.time_spent")
    val VIEW_ERROR_COUNT: AttributeKey<Long> = AttributeKey.longKey("view.error.count")
    val VIEW_RESOURCE_COUNT: AttributeKey<Long> = AttributeKey.longKey("view.resource.count")

    // User
    val USER_ID: AttributeKey<String> = AttributeKey.stringKey("user.id")
    val USER_NAME: AttributeKey<String> = AttributeKey.stringKey("user.name")
    val USER_FULL_NAME: AttributeKey<String> = AttributeKey.stringKey("user.full_name")
    val USER_EMAIL: AttributeKey<String> = AttributeKey.stringKey("user.email")
    val USER_ROLES: AttributeKey<String> = AttributeKey.stringKey("user.roles")

    // Span names (must match browser SDK exactly)
    const val SESSION_START_NAME = "Session Start"
    const val SESSION_END_NAME = "Session End"
    const val VIEW_SPAN_NAME = "View"
}
