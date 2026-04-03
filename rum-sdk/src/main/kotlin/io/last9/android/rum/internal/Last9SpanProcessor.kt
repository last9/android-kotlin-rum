package io.last9.android.rum.internal

import io.last9.android.rum.session.SessionStore
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Injects session, view, and user attributes on every span.
 * Attaches breadcrumb trail on crash/ANR spans.
 */
internal class Last9SpanProcessor : SpanProcessor {

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        SessionStore.getCurrentSessionId()?.let {
            span.setAttribute(SemanticConventions.SESSION_ID, it)
        }
        SessionStore.getPreviousSessionId()?.let {
            span.setAttribute(SemanticConventions.SESSION_PREVIOUS_ID, it)
        }

        SessionStore.getCurrentViewId()?.let {
            span.setAttribute(SemanticConventions.VIEW_ID, it)
        }
        SessionStore.getCurrentViewName()?.let {
            span.setAttribute(SemanticConventions.VIEW_NAME, it)
        }

        SessionStore.getCurrentUser()?.let { user ->
            user.id?.let { span.setAttribute(SemanticConventions.USER_ID, it) }
            user.name?.let { span.setAttribute(SemanticConventions.USER_NAME, it) }
            user.fullName?.let { span.setAttribute(SemanticConventions.USER_FULL_NAME, it) }
            user.email?.let { span.setAttribute(SemanticConventions.USER_EMAIL, it) }
            user.roles?.takeIf { it.isNotEmpty() }?.let {
                span.setAttribute(SemanticConventions.USER_ROLES, it.joinToString(","))
            }
        }

        // Attach breadcrumbs to crash/ANR spans so you can see what
        // the user was doing leading up to the error.
        if (isCrashOrAnrSpan(span.name)) {
            BreadcrumbStore.toJson()?.let {
                span.setAttribute(BreadcrumbStore.BREADCRUMBS_KEY, it)
            }
        }
    }

    override fun onEnd(span: ReadableSpan) {}
    override fun isStartRequired() = true
    override fun isEndRequired() = false

    private fun isCrashOrAnrSpan(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("crash") || lower.contains("anr") || lower.contains("exception")
    }
}
