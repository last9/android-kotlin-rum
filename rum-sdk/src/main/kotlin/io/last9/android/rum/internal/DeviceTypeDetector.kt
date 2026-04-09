package io.last9.android.rum.internal

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Detects the device form factor (phone, tablet, tv) using UI mode and screen size.
 */
internal object DeviceTypeDetector {

    fun detect(context: Context): String {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager != null) {
            when (uiModeManager.currentModeType) {
                Configuration.UI_MODE_TYPE_TELEVISION -> return "tv"
                Configuration.UI_MODE_TYPE_WATCH -> return "watch"
                Configuration.UI_MODE_TYPE_CAR -> return "car"
            }
        }

        val screenLayout = context.resources.configuration.screenLayout and
            Configuration.SCREENLAYOUT_SIZE_MASK

        return if (screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE) "tablet" else "phone"
    }
}
