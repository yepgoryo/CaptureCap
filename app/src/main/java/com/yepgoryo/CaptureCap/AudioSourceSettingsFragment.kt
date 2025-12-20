package com.yepgoryo.CaptureCap

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class AudioSourcesSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(bundle: Bundle?, name: String?) {
        getPreferenceManager().setSharedPreferencesName(GlobalProperties.PREFERENCES_ALIAS)
        setPreferencesFromResource(R.xml.settings_audio_sources, name)
    }
}
