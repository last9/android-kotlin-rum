package io.last9.android.rum.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Collects current network connectivity type, subtype, and carrier name.
 *
 * Requires `ACCESS_NETWORK_STATE` (a normal permission declared in the SDK
 * manifest, granted automatically at install time — no runtime prompt).
 *
 * Results are cached for [CACHE_TTL_MS] to avoid Binder IPC on every span.
 */
internal object NetworkInfoCollector {

    private const val CACHE_TTL_MS = 5_000L
    @Volatile private var cachedInfo: NetworkInfo? = null
    @Volatile private var cachedAt: Long = 0L

    data class NetworkInfo(
        val connectionType: String?,
        val connectionSubtype: String?,
        val carrierName: String?,
    )

    fun collect(context: Context): NetworkInfo {
        val now = System.currentTimeMillis()
        cachedInfo?.let { if (now - cachedAt < CACHE_TTL_MS) return it }

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        var connectionType: String? = null
        var connectionSubtype: String? = null

        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            if (capabilities != null) {
                connectionType = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                    else -> "unknown"
                }
            }
        }

        // Carrier name and cellular subtype
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrierName = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }

        if (connectionType == "cell" && telephonyManager != null) {
            connectionSubtype = cellSubtype(telephonyManager)
        }

        val info = NetworkInfo(connectionType, connectionSubtype, carrierName)
        cachedInfo = info
        cachedAt = now
        return info
    }

    @Suppress("DEPRECATION")
    private fun cellSubtype(tm: TelephonyManager): String? {
        // TelephonyManager.getNetworkType requires READ_PHONE_STATE on API 30+,
        // so we wrap in a try-catch and degrade gracefully.
        return try {
            when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "2g"

                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3g"

                TelephonyManager.NETWORK_TYPE_LTE -> "4g"

                TelephonyManager.NETWORK_TYPE_NR -> "5g"

                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
