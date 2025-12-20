package com.yepgoryo.CaptureCap

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class GlobalProperties(context: Context) {
    companion object {
        const val PREFERENCES_ALIAS = "CaptureCapPreferences"
    }

    private val propertiesList: SharedPreferences
    private val propertiesListEditor: SharedPreferences.Editor

    enum class AudioChannelsProperty {
        MONO,
        STEREO
    }

    enum class DarkThemeProperty {
        DARK,
        LIGHT,
        AUTOMATIC
    }

    enum class FloatingControlsSizeProperty {
        LARGE,
        NORMAL,
        SMALL,
        TINY,
        LITTLE
    }

    enum class OnShakeProperty {
        DO_NOTHING,
        PAUSE,
        STOP
    }

    enum class PropertiesBoolean {
        CHECK_SOUND_MIC,
        CHECK_SOUND_PLAYBACK,
        RECORD_MODE,
        FLOATING_CONTROLS,
        PANEL_POSITION_VERTICAL_HIDDEN_BIG,
        PANEL_POSITION_VERTICAL_HIDDEN_NORMAL,
        PANEL_POSITION_VERTICAL_HIDDEN_SMALL,
        PANEL_POSITION_VERTICAL_HIDDEN_TINY,
        PANEL_POSITION_VERTICAL_HIDDEN_LITTLE,
        PANEL_POSITION_HORIZONTAL_HIDDEN_BIG,
        PANEL_POSITION_HORIZONTAL_HIDDEN_NORMAL,
        PANEL_POSITION_HORIZONTAL_HIDDEN_SMALL,
        PANEL_POSITION_HORIZONTAL_HIDDEN_TINY,
        PANEL_POSITION_HORIZONTAL_HIDDEN_LITTLE,
        CUSTOM_QUALITY,
        CUSTOM_FPS,
        CUSTOM_BITRATE,
        CUSTOM_SAMPLE_RATE,
        AUD_SOURCE_MEDIA,
        AUD_SOURCE_GAME,
        AUD_SOURCE_UNKNOWN,
        DONT_NOTIFY_ON_FINISH,
        DONT_NOTIFY_ON_ROTATE,
        MINIMIZE_ON_START,
        NO_ROTATE
    }

    enum class PropertiesInt {
        PANEL_POSITION_HORIZONTAL_X_BIG,
        PANEL_POSITION_HORIZONTAL_Y_BIG,
        PANEL_POSITION_HORIZONTAL_X_NORMAL,
        PANEL_POSITION_HORIZONTAL_Y_NORMAL,
        PANEL_POSITION_HORIZONTAL_X_SMALL,
        PANEL_POSITION_HORIZONTAL_Y_SMALL,
        PANEL_POSITION_HORIZONTAL_X_TINY,
        PANEL_POSITION_HORIZONTAL_Y_TINY,
        PANEL_POSITION_HORIZONTAL_X_LITTLE,
        PANEL_POSITION_HORIZONTAL_Y_LITTLE,
        PANEL_POSITION_VERTICAL_X_BIG,
        PANEL_POSITION_VERTICAL_Y_BIG,
        PANEL_POSITION_VERTICAL_X_NORMAL,
        PANEL_POSITION_VERTICAL_Y_NORMAL,
        PANEL_POSITION_VERTICAL_X_SMALL,
        PANEL_POSITION_VERTICAL_Y_SMALL,
        PANEL_POSITION_VERTICAL_X_TINY,
        PANEL_POSITION_VERTICAL_Y_TINY,
        PANEL_POSITION_VERTICAL_X_LITTLE,
        PANEL_POSITION_VERTICAL_Y_LITTLE,
        FLOATING_CONTROLS_OPACITY,
        QUALITY_SCALE
    }

    enum class PropertiesSpecial {
        RESOLUTION_VALUE,
        FLOATING_CONTROLS_SIZE,
        DARK_THEME,
        DARK_THEME_APPLIED,
        AUDIO_CHANNELS,
        ON_SHAKE
    }

    enum class PropertiesString {
        FOLDER_PATH,
        FOLDER_AUDIO_PATH,
        FPS_VALUE,
        BITRATE_VALUE,
        CODEC_VALUE,
        AUDIO_CODEC_VALUE,
        SAMPLE_RATE_VALUE
    }

    enum class ResolutionProperty {
        NATIVE,
        _2160P_,
        _1080P_,
        _720P_,
        _480P_,
        _360P_
    }

    init {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_ALIAS, 0)
        this.propertiesList = sharedPreferences
        this.propertiesListEditor = sharedPreferences.edit()
    }

    private fun convertPropertyName(name: String): String {
        return name.replace("_", "").lowercase(Locale.ROOT)
    }

    private fun convertValueName(name: String): String  {
        val valueName: String = name.replace("_", " ").lowercase(Locale.ROOT)
        return valueName.replaceFirst(valueName.take(1), valueName.take(1).uppercase(Locale.ROOT))
    }

    fun getResolution(): ResolutionProperty {
        val resolution: String = "_" + propertiesList.getString(convertPropertyName(PropertiesSpecial.RESOLUTION_VALUE.toString()), convertValueName(ResolutionProperty.NATIVE.toString()))!!.uppercase(Locale.ROOT) + "_"
        for (i in ResolutionProperty.entries) {
            if (resolution.contentEquals(i.toString())) {
                return i
            }
        }
        return ResolutionProperty.NATIVE
    }

    fun setResolution(resolutionProperty: ResolutionProperty) {
        this.propertiesListEditor.putString(convertPropertyName(PropertiesSpecial.RESOLUTION_VALUE.toString()), convertPropertyName(resolutionProperty.toString().replace("_", "")))
        this.propertiesListEditor.commit()
    }

    fun getFloatingControlsSize(): FloatingControlsSizeProperty {
        val controlsSize: String = propertiesList.getString(convertPropertyName(PropertiesSpecial.FLOATING_CONTROLS_SIZE.toString()), convertPropertyName(FloatingControlsSizeProperty.NORMAL.toString()))!!.replace(" ", "_").uppercase(Locale.ROOT)
        for (i in FloatingControlsSizeProperty.entries) {
            if (controlsSize.contentEquals(i.toString())) {
                return i
            }
        }
        return FloatingControlsSizeProperty.NORMAL
    }

    fun setFloatingControlsSize(floatingControlsSizeProperty: FloatingControlsSizeProperty) {
        this.propertiesListEditor.putString(convertPropertyName(PropertiesSpecial.FLOATING_CONTROLS_SIZE.toString()), convertPropertyName(floatingControlsSizeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getDarkTheme(applied: Boolean): DarkThemeProperty {
        var propertiesSpecial: PropertiesSpecial = PropertiesSpecial.DARK_THEME
        if (applied) {
            propertiesSpecial = PropertiesSpecial.DARK_THEME_APPLIED
        }
        val darkTheme: String = this.propertiesList.getString(convertPropertyName(propertiesSpecial.toString()), convertValueName(DarkThemeProperty.AUTOMATIC.toString()))!!.replace(" ", "_").toUpperCase()
        for (i in DarkThemeProperty.entries) {
            if (darkTheme.contentEquals(i.toString())) {
                return i
            }
        }
        return DarkThemeProperty.AUTOMATIC
    }

    fun setDarkTheme(applied: Boolean, darkThemeProperty: DarkThemeProperty) {
        var darkTheme: PropertiesSpecial = PropertiesSpecial.DARK_THEME
        if (applied) {
            darkTheme = PropertiesSpecial.DARK_THEME_APPLIED
        }
        this.propertiesListEditor.putString(convertPropertyName(darkTheme.toString()), convertPropertyName(darkThemeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getAudioChannels(): AudioChannelsProperty {
        val audioChannels: String = this.propertiesList.getString(convertPropertyName(PropertiesSpecial.AUDIO_CHANNELS.toString()), convertValueName(AudioChannelsProperty.STEREO.toString()))!!.replace(" ", "_").toUpperCase()
        for (i in AudioChannelsProperty.entries) {
            if (audioChannels == i.toString()) {
                return i
            }
        }
        return AudioChannelsProperty.STEREO
    }

    fun setAudioChannels(audioChannelsProperty: AudioChannelsProperty) {
        this.propertiesListEditor.putString(convertPropertyName(PropertiesSpecial.AUDIO_CHANNELS.toString()), convertPropertyName(audioChannelsProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getOnShake(): OnShakeProperty {
        val onShake: String = this.propertiesList.getString(convertPropertyName(PropertiesSpecial.ON_SHAKE.toString()), convertValueName(OnShakeProperty.DO_NOTHING.toString()))!!.replace(" ", "_").toUpperCase()
        for (i in OnShakeProperty.entries) {
            if (onShake == i.toString()) {
                return i
            }
        }
        return OnShakeProperty.DO_NOTHING
    }

    fun setOnShake(onShakeProperty: OnShakeProperty) {
        this.propertiesListEditor.putString(convertPropertyName(PropertiesSpecial.ON_SHAKE.toString()), convertPropertyName(onShakeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getStringProperty(propertiesString: PropertiesString, default: String): String {
        val string: String = this.propertiesList.getString(convertPropertyName(propertiesString.toString()), default) ?: ""
        if (string == "") return default
        return string
    }

    fun setStringProperty(propertiesString: PropertiesString, default: String) {
        this.propertiesListEditor.putString(convertPropertyName(propertiesString.toString()), default)
        this.propertiesListEditor.commit()
    }

    fun getBooleanProperty(propertiesBoolean: PropertiesBoolean, default: Boolean): Boolean {
        return this.propertiesList.getBoolean(convertPropertyName(propertiesBoolean.toString()), default)
    }

    fun setBooleanProperty(propertiesBoolean: PropertiesBoolean, default: Boolean) {
        this.propertiesListEditor.putBoolean(convertPropertyName(propertiesBoolean.toString()), default)
        this.propertiesListEditor.commit()
    }

    fun getIntProperty(propertiesInt: PropertiesInt, default: Int): Int {
        return this.propertiesList.getInt(convertPropertyName(propertiesInt.toString()), default)
    }

    fun setIntProperty(propertiesInt: PropertiesInt, default: Int) {
        this.propertiesListEditor.putInt(convertPropertyName(propertiesInt.toString()), default)
        this.propertiesListEditor.commit()
    }

    fun removeIntProperty(propertiesInt: PropertiesInt) {
        this.propertiesListEditor.remove(convertPropertyName(propertiesInt.toString()))
        this.propertiesListEditor.commit()
    }

    fun removeStringProperty(propertiesString: PropertiesString) {
        this.propertiesListEditor.remove(convertPropertyName(propertiesString.toString()))
        this.propertiesListEditor.commit()
    }

    fun removeBooleanProperty(propertiesBoolean: PropertiesBoolean) {
        this.propertiesListEditor.remove(convertPropertyName(propertiesBoolean.toString()))
        this.propertiesListEditor.commit()
    }

    fun getIntPropertyName(propertiesInt: PropertiesInt): String {
        return convertPropertyName(propertiesInt.toString())
    }

    fun getBooleanPropertyName(propertiesBoolean: PropertiesBoolean): String {
        return convertPropertyName(propertiesBoolean.toString())
    }

    fun getStringPropertyName(propertiesString: PropertiesString): String {
        return convertPropertyName(propertiesString.toString())
    }

    fun getSpecialPropertyName(propertiesSpecial: PropertiesSpecial): String {
        return convertPropertyName(propertiesSpecial.toString())
    }
}
