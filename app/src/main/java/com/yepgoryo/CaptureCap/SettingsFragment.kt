package com.yepgoryo.CaptureCap

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(bundle: Bundle?, key: String?) {
        preferenceManager.setSharedPreferencesName(GlobalProperties.PREFERENCES_ALIAS)
        setPreferencesFromResource(R.xml.settings, key)
        val preferenceFindPreference: Preference = findPreference("floatingcontrols")!!
        val preferenceFindPreference2: Preference = findPreference("floatingcontrolsposition")!!
        val preferenceFindPreference3: Preference = findPreference("floatingcontrolssize")!!
        val preferenceFindPreference4: Preference = findPreference("floatingcontrolsopacity")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val preferenceCategory: PreferenceCategory = findPreference("controlssettings")!!
            preferenceCategory.removePreference(preferenceFindPreference)
            preferenceCategory.removePreference(preferenceFindPreference2)
            preferenceCategory.removePreference(preferenceFindPreference3)
            preferenceCategory.removePreference(preferenceFindPreference4)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val preferenceFindPreference5: Preference = findPreference("codecvalue")!!
            val preferenceFindPreference6: Preference = findPreference("audiocodecvalue")!!
            val preferenceFindPreference7: Preference = findPreference("selectaudiosources")!!
            val preferenceCategory2: PreferenceCategory = findPreference("capturesettings")!!
            preferenceCategory2.removePreference(preferenceFindPreference5)
            preferenceCategory2.removePreference(preferenceFindPreference6)
            preferenceCategory2.removePreference(preferenceFindPreference7)
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key.contentEquals("qualityscale")) {
            val qualityDialogFragmentNewInstance: QualityDialogFragment = QualityDialogFragment.newInstance(preference.key)
            qualityDialogFragmentNewInstance.setTargetFragment(this, 0)
            qualityDialogFragmentNewInstance.setKeyName("qualityscale")
            qualityDialogFragmentNewInstance.show(requireFragmentManager(), null)
            return
        }
        if (preference.key.contentEquals("floatingcontrolsopacity")) {
            val panelOpacityDialogFragmentNewInstance: PanelOpacityDialogFragment = PanelOpacityDialogFragment.newInstance(preference.key)
            panelOpacityDialogFragmentNewInstance.setTargetFragment(this, 0)
            panelOpacityDialogFragmentNewInstance.setKeyName("floatingcontrolsopacity")
            panelOpacityDialogFragmentNewInstance.show(requireFragmentManager(), null)
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }
}
