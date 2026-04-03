package io.last9.android.rum.internal

import io.opentelemetry.api.common.AttributeKey

/**
 * Thread-safe ring buffer of recent user actions.
 *
 * When a crash or ANR span is created, [Last9SpanProcessor] attaches
 * the breadcrumb trail as a JSON attribute so you can see what the
 * user was doing leading up to the error.
 */
internal object BreadcrumbStore {

    val BREADCRUMBS_KEY: AttributeKey<String> = AttributeKey.stringKey("last9.breadcrumbs")

    private const val MAX_SIZE = 20

    data class Breadcrumb(
        val timestamp: Long,
        val type: String,
        val message: String,
    )

    private val buffer = ArrayDeque<Breadcrumb>(MAX_SIZE)
    private val lock = Any()

    fun add(type: String, message: String) {
        val crumb = Breadcrumb(System.currentTimeMillis(), type, message)
        synchronized(lock) {
            if (buffer.size >= MAX_SIZE) {
                buffer.removeFirst()
            }
            buffer.addLast(crumb)
        }
    }

    /**
     * Returns breadcrumbs as a JSON array string for attaching to crash spans.
     * Returns null if no breadcrumbs.
     */
    fun toJson(): String? {
        val snapshot = synchronized(lock) {
            if (buffer.isEmpty()) return null
            buffer.toList()
        }
        return buildString {
            append('[')
            snapshot.forEachIndexed { i, crumb ->
                if (i > 0) append(',')
                append("{\"ts\":")
                append(crumb.timestamp)
                append(",\"type\":\"")
                append(crumb.type.replace("\"", "\\\""))
                append("\",\"msg\":\"")
                append(crumb.message.replace("\"", "\\\"").take(200))
                append("\"}")
            }
            append(']')
        }
    }

    internal fun reset() {
        synchronized(lock) { buffer.clear() }
    }
}
