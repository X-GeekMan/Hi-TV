package app.orchard.tv.system

import android.os.Build
import android.view.Window
import app.orchard.tv.settings.HomeRefreshRate
import kotlin.math.abs

/** Requests a refresh mode for Aerial's window only; Android may still choose or retain another mode. */
object DisplayRefreshRate {
    fun available(window: Window): Set<HomeRefreshRate> {
        val display = window.decorView.display ?: return emptySet()
        if (Build.VERSION.SDK_INT < 23) return emptySet()
        val current = if (Build.VERSION.SDK_INT >= 23) display.mode else null
        val rates = if (Build.VERSION.SDK_INT >= 23) {
            display.supportedModes.asSequence()
                .filter { current == null || it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .map { it.refreshRate }
                .toList()
        } else listOf(display.refreshRate)
        return HomeRefreshRate.entries.filter { choice ->
            choice.hz != null && rates.any { abs(it - choice.hz) <= 1.5f }
        }.toSet()
    }

    /** Returns a short diagnostic description of the request applied to this window. */
    fun apply(window: Window, choice: HomeRefreshRate, allowAutomaticSwitch: Boolean): String {
        val display = window.decorView.display ?: return "no display"
        if (Build.VERSION.SDK_INT < 23) return "display mode selection unavailable"

        val current = display.mode
        val matchingModes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val target = when (choice) {
            HomeRefreshRate.Auto -> if (Build.VERSION.SDK_INT >= 31 && allowAutomaticSwitch) {
                val seamless = current.alternativeRefreshRates
                matchingModes
                    .filter { mode ->
                        mode.refreshRate <= 120.5f && seamless.any { abs(it - mode.refreshRate) < 0.5f }
                    }
                    .maxByOrNull { it.refreshRate }
            } else null
            else -> matchingModes.firstOrNull { abs(it.refreshRate - (choice.hz ?: 0)) <= 1.5f }
        }

        // Zero returns the choice to the system policy (the correct Auto behavior on older Android,
        // where the framework cannot tell us which switches would blank the screen).
        val preferredId = target?.modeId ?: 0
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != preferredId) {
            attributes.preferredDisplayModeId = preferredId
            window.attributes = attributes
        }
        return when {
            choice == HomeRefreshRate.Auto && target != null -> "Auto -> ${target.refreshRate}Hz seamless"
            choice == HomeRefreshRate.Auto -> "Auto -> system policy"
            target != null -> "${choice.hz}Hz -> ${target.refreshRate}Hz (mode ${target.modeId})"
            else -> "${choice.hz}Hz unavailable at ${current.physicalWidth}x${current.physicalHeight}"
        }
    }
}
