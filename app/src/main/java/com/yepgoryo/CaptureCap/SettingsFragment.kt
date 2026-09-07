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
        val preferenceFindPreference18: Preference = findPreference("floatingcontrolsreducetodot")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val preferenceCategory: PreferenceCategory = findPreference("controlssettings")!!
            preferenceCategory.removePreference(preferenceFindPreference)
            preferenceCategory.removePreference(preferenceFindPreference2)
            preferenceCategory.removePreference(preferenceFindPreference3)
            preferenceCategory.removePreference(preferenceFindPreference4)
            preferenceCategory.removePreference(preferenceFindPreference18)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val preferenceFindPreference5: Preference = findPreference("codecvalue")!!
            val preferenceFindPreference6: Preference = findPreference("audiocodecvalue")!!
            val preferenceFindPreference7: Preference = findPreference("selectaudiosources")!!
            val preferenceFindPreference8: Preference = findPreference("drawoverlay")!!
            val preferenceFindPreference9: Preference = findPreference("drawoverlaycontents")!!
            val preferenceFindPreference10: Preference = findPreference("drawoverlayerasehorizontal")!!
            val preferenceFindPreference11: Preference = findPreference("drawoverlayerasevertical")!!
            val preferenceFindPreference12: Preference = findPreference("soundcontrolnotification")!!
            val preferenceFindPreference13: Preference = findPreference("screenorientation")!!
            val preferenceFindPreference14: Preference = findPreference("rotatehardwaresensor")!!
            val preferenceFindPreference15: Preference = findPreference("dontnotifyonrotate")!!
            val preferenceFindPreference16: Preference = findPreference("norotate")!!
            val preferenceFindPreference17: Preference = findPreference("prerecording")!!
            val preferenceFindPreference18: Preference = findPreference("formatvalue")!!
            val preferenceFindPreference19: Preference = findPreference("audioformatvalue")!!
            val preferenceFindPreference20: Preference = findPreference("selectedmicrophone")!!
            val preferenceFindPreference21: Preference = findPreference("audiovolumeoption")!!
            val preferenceFindPreference22: Preference = findPreference("cropscreen")!!
            val preferenceFindPreference23: Preference = findPreference("cropscreencontents")!!
            val preferenceFindPreference24: Preference = findPreference("cropscreensmoothcrop")!!

            val preferenceCategory2: PreferenceCategory = findPreference("capturesettings")!!
            preferenceCategory2.removePreference(preferenceFindPreference5)
            preferenceCategory2.removePreference(preferenceFindPreference6)
            preferenceCategory2.removePreference(preferenceFindPreference7)
            preferenceCategory2.removePreference(preferenceFindPreference8)
            preferenceCategory2.removePreference(preferenceFindPreference9)
            preferenceCategory2.removePreference(preferenceFindPreference10)
            preferenceCategory2.removePreference(preferenceFindPreference11)
            preferenceCategory2.removePreference(preferenceFindPreference12)
            preferenceCategory2.removePreference(preferenceFindPreference13)
            preferenceCategory2.removePreference(preferenceFindPreference14)
            preferenceCategory2.removePreference(preferenceFindPreference15)
            preferenceCategory2.removePreference(preferenceFindPreference16)
            preferenceCategory2.removePreference(preferenceFindPreference17)
            preferenceCategory2.removePreference(preferenceFindPreference18)
            preferenceCategory2.removePreference(preferenceFindPreference19)
            preferenceCategory2.removePreference(preferenceFindPreference20)
            preferenceCategory2.removePreference(preferenceFindPreference21)
            preferenceCategory2.removePreference(preferenceFindPreference22)
            preferenceCategory2.removePreference(preferenceFindPreference23)
            preferenceCategory2.removePreference(preferenceFindPreference24)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val preferenceFindPreference21: PreferenceCategory = findPreference("shizukusettings")!!
            preferenceScreen.removePreference(preferenceFindPreference21)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val preferenceFindPreference14: Preference = findPreference("screenrotation")!!
            val preferenceCategory4: PreferenceCategory = findPreference("capturesettings")!!
            preferenceCategory4.removePreference(preferenceFindPreference14)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val preferenceFindPreference15: Preference = findPreference("enablevibration")!!
            val preferenceCategory3: PreferenceCategory = findPreference("capturesettings")!!
            preferenceCategory3.removePreference(preferenceFindPreference15)
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
        if (preference.key.contentEquals("timerseconds")) {
            val timerSecondsDialogFragmentNewInstance: TimerDialogFragment = TimerDialogFragment.newInstance(preference.key)
            timerSecondsDialogFragmentNewInstance.setTargetFragment(this, 0)
            timerSecondsDialogFragmentNewInstance.setKeyName("timerseconds")
            timerSecondsDialogFragmentNewInstance.show(requireFragmentManager(), null)
            return
        }
        if (preference.key.contentEquals("audiovolumeoption")) {
            val audioVolumeDialogFragmentNewInstance: AudioVolumeDialogFragment = AudioVolumeDialogFragment.newInstance(preference.key)
            audioVolumeDialogFragmentNewInstance.setTargetFragment(this, 0)
            audioVolumeDialogFragmentNewInstance.setKeyName("audiovolumeoption")
            audioVolumeDialogFragmentNewInstance.show(requireFragmentManager(), null)
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }
}
