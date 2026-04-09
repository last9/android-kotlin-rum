package io.last9.android.rum.internal

import android.content.Context as AndroidContext
import io.last9.android.rum.session.SessionStore
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Injects session, view, user, and network attributes on every span.
 * Attaches breadcrumb trail on crash/ANR spans.
 */
internal class Last9SpanProcessor(
    private val appContext: AndroidContext? = null,
) : SpanProcessor {

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
            user.ipLocation?.let { span.setAttribute(SemanticConventions.USER_IP_LOCATION, it) }
            user.cityName?.let { span.setAttribute(SemanticConventions.USER_CITY_NAME, it) }
            user.customAttributes?.forEach { (key, value) ->
                span.setAttribute(AttributeKey.stringKey("user.$key"), value)
            }
        }

        // Network connectivity (sampled on each span — can change mid-session)
        appContext?.let { ctx ->
            val networkInfo = NetworkInfoCollector.collect(ctx)
            networkInfo.connectionType?.let {
                span.setAttribute(SemanticConventions.NETWORK_CONNECTION_TYPE, it)
            }
            networkInfo.connectionSubtype?.let {
                span.setAttribute(SemanticConventions.NETWORK_CONNECTION_SUBTYPE, it)
            }
            networkInfo.carrierName?.let {
                span.setAttribute(SemanticConventions.NETWORK_CARRIER_NAME, it)
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
