package app.orchard.tv.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsIconOptionsTest {
    @Test fun positionAndVisibilityAreIndependentAndPersisted() {
        val context = RuntimeEnvironment.getApplication()
        AerialSettings.init(context)
        AerialSettings.chooseSettingsIconPosition(0)
        AerialSettings.chooseSettingsIconVisibility(1)
        assertEquals(0, AerialSettings.settingsIconPosition)
        assertEquals(1, AerialSettings.settingsIconVisibility)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AerialSettings.chooseShowLunarDate(true)
        assertTrue(prefs.getBoolean("show_lunar_date", false))
        AerialSettings.chooseShowLunarDate(false)
        assertFalse(prefs.getBoolean("show_lunar_date", true))
        assertEquals(0, prefs.getInt("settings_icon_position", -1))
        assertEquals(1, prefs.getInt("settings_icon_visibility", -1))
        AerialSettings.chooseSettingsIconPosition(2)
        assertEquals(1, AerialSettings.settingsIconVisibility)
        AerialSettings.chooseSettingsIconVisibility(2)
        assertEquals(2, AerialSettings.settingsIconPosition)
        assertEquals(2, prefs.getInt("settings_icon_visibility", -1))
        AerialSettings.chooseSettingsIconVisibility(0)
        assertEquals(0, prefs.getInt("settings_icon_visibility", -1))
    }
}
