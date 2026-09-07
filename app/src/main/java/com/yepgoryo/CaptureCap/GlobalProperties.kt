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
    private val privatePropertiesList: SharedPreferences
    private val privatePropertiesListEditor: SharedPreferences.Editor

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
        CHECK_STREAM,
        STREAM_SAVE_TO_FILE,
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
        FLOATING_CONTROLS_REDUCE_TO_DOT,
        CUSTOM_QUALITY,
        CUSTOM_FPS,
        CUSTOM_BITRATE,
        CUSTOM_SAMPLE_RATE,
        AUD_SOURCE_MEDIA,
        AUD_SOURCE_GAME,
        AUD_SOURCE_UNKNOWN,
        DONT_NOTIFY_ON_FINISH,
        DONT_NOTIFY_ON_ROTATE,
        ENABLE_VIBRATION,
        MINIMIZE_ON_START,
        NO_ROTATE,
        ROTATE_HARDWARE_SENSOR,
        ENABLE_TIMER,
        DRAW_OVERLAY,
        SOUND_CONTROL_NOTIFICATION,
        PRE_RECORDING,
        SHIZUKU_ENABLE,
        SHIZUKU_RECORD_PHONECALL,
        SHIZUKU_AUTO_MANAGE,
        CROP_SCREEN,
        SMOOTH_CROP,
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
        QUALITY_SCALE,
        TIMER_SECONDS,
        AUDIO_VOLUME,
        MICROPHONE_VOLUME,
        SHIZUKU_PHONE_CALL_VOLUME,
        CROP_AREA_WIDTH_HORIZONTAL,
        CROP_AREA_HEIGHT_HORIZONTAL,
        CROP_AREA_X_HORIZONTAL,
        CROP_AREA_Y_HORIZONTAL,
        CROP_AREA_WIDTH_VERTICAL,
        CROP_AREA_HEIGHT_VERTICAL,
        CROP_AREA_X_VERTICAL,
        CROP_AREA_Y_VERTICAL,
    }

    enum class PropertiesSpecial {
        RESOLUTION_VALUE,
        FLOATING_CONTROLS_SIZE,
        DARK_THEME,
        DARK_THEME_APPLIED,
        AUDIO_CHANNELS,
        ON_SHAKE,
        SCREEN_ORIENTATION,
        SCREEN_ROTATION,
        SHIZUKU_PHONE_CALL_AUDIO_SOURCE,
    }

    enum class PropertiesString {
        FOLDER_PATH,
        FOLDER_AUDIO_PATH,
        STREAM_URL,
        STREAM_KEY,
        FPS_VALUE,
        BITRATE_VALUE,
        CODEC_VALUE,
        AUDIO_CODEC_VALUE,
        FORMAT_VALUE,
        AUDIO_FORMAT_VALUE,
        SAMPLE_RATE_VALUE,
        AVC_CODEC,
        HEVC_CODEC,
        AAC_CODEC,
        SELECTED_MICROPHONE,
        SHIZUKU_AUTH_KEY,
    }

    enum class ResolutionProperty {
        NATIVE,
        _2160P_,
        _1080P_,
        _720P_,
        _480P_,
        _360P_
    }

    enum class ScreenOrientationProperty {
        FORCE_LANDSCAPE,
        FORCE_PORTRAIT,
        DEFAULT
    }

    enum class ShizukuPhoneCallAudioSource {
        VOICE_CALL,
        VOICE_CALL_UPLINK,
        VOICE_CALL_DOWNLINK,
    }

    enum class ScreenRotationProperty {
        _0_DEGREES_,
        _90_DEGREES_,
        _180_DEGREES_,
        _270_DEGREES_,
        DEFAULT
    }

    init {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_ALIAS, 0)
        this.propertiesList = sharedPreferences
        this.propertiesListEditor = sharedPreferences.edit()
        val privateSharedPreferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_ALIAS, Context.MODE_PRIVATE)
        this.privatePropertiesList = privateSharedPreferences
        this.privatePropertiesListEditor = privateSharedPreferences.edit()
    }

    fun convertFromPropertyName(name: String): String {
        return name.replace("_", "").lowercase(Locale.ROOT)
    }

    fun convertFromValueName(name: String): String  {
        val valueName: String = name.replace("_", " ").lowercase(Locale.ROOT)
        return valueName.replaceFirst(valueName.take(1), valueName.take(1).uppercase(Locale.ROOT))
    }

    fun convertToValueName(name: String): String  {
        return name.replace(" ", "_").uppercase(Locale.ROOT)
    }

    fun getResolution(): ResolutionProperty {
        val resolution: String = "_" + convertToValueName(propertiesList.getString(convertFromPropertyName(PropertiesSpecial.RESOLUTION_VALUE.toString()), convertFromValueName(ResolutionProperty.NATIVE.toString()))!!) + "_"
        for (i in ResolutionProperty.entries) {
            if (resolution.contentEquals(i.toString())) {
                return i
            }
        }
        return ResolutionProperty.NATIVE
    }

    fun setResolution(resolutionProperty: ResolutionProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.RESOLUTION_VALUE.toString()), convertFromValueName(resolutionProperty.toString().replace("_", "")))
        this.propertiesListEditor.commit()
    }

    fun getScreenOrientation(): ScreenOrientationProperty {
        val screenOrientation: String = convertToValueName(propertiesList.getString(convertFromPropertyName(PropertiesSpecial.SCREEN_ORIENTATION.toString()), convertFromValueName(ScreenOrientationProperty.DEFAULT.toString()))!!)
        for (i in ScreenOrientationProperty.entries) {
            if (screenOrientation.contentEquals(i.toString())) {
                return i
            }
        }
        return ScreenOrientationProperty.DEFAULT
    }

    fun setScreenOrientation(screenOrientationProperty: ScreenOrientationProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.SCREEN_ORIENTATION.toString()), convertFromValueName(screenOrientationProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getShizukuPhoneCallSource(): ShizukuPhoneCallAudioSource {
        val audioSource: String = convertToValueName(propertiesList.getString(convertFromPropertyName(PropertiesSpecial.SHIZUKU_PHONE_CALL_AUDIO_SOURCE.toString()), convertFromValueName(ShizukuPhoneCallAudioSource.VOICE_CALL.toString()))!!)
        for (i in ShizukuPhoneCallAudioSource.entries) {
            if (audioSource.contentEquals(i.toString())) {
                return i
            }
        }
        return ShizukuPhoneCallAudioSource.VOICE_CALL
    }

    fun setShizukuPhoneCallSource(shizukuPhoneCallAudioSource: ShizukuPhoneCallAudioSource) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.SHIZUKU_PHONE_CALL_AUDIO_SOURCE.toString()), convertFromValueName(shizukuPhoneCallAudioSource.toString()))
        this.propertiesListEditor.commit()
    }

    fun getScreenRotation(): ScreenRotationProperty {
        val screenRotation: String = "_" + convertToValueName(propertiesList.getString(convertFromPropertyName(PropertiesSpecial.SCREEN_ROTATION.toString()), convertFromValueName(ScreenRotationProperty.DEFAULT.toString()))!!) + "_"
        for (i in ScreenRotationProperty.entries) {
            if (screenRotation.contentEquals(i.toString())) {
                return i
            }
        }
        return ScreenRotationProperty.DEFAULT
    }

    fun setScreenRotation(screenRotationProperty: ScreenRotationProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.SCREEN_ROTATION.toString()), convertFromValueName(screenRotationProperty.toString().removeSurrounding("_")))
        this.propertiesListEditor.commit()
    }

    fun getFloatingControlsSize(): FloatingControlsSizeProperty {
        val controlsSize: String = convertToValueName(propertiesList.getString(convertFromPropertyName(PropertiesSpecial.FLOATING_CONTROLS_SIZE.toString()), convertFromValueName(FloatingControlsSizeProperty.NORMAL.toString()))!!)
        for (i in FloatingControlsSizeProperty.entries) {
            if (controlsSize.contentEquals(i.toString())) {
                return i
            }
        }
        return FloatingControlsSizeProperty.NORMAL
    }

    fun setFloatingControlsSize(floatingControlsSizeProperty: FloatingControlsSizeProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.FLOATING_CONTROLS_SIZE.toString()), convertFromValueName(floatingControlsSizeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getDarkTheme(applied: Boolean): DarkThemeProperty {
        var propertiesSpecial: PropertiesSpecial = PropertiesSpecial.DARK_THEME
        if (applied) {
            propertiesSpecial = PropertiesSpecial.DARK_THEME_APPLIED
        }
        val darkTheme: String = convertToValueName(this.propertiesList.getString(convertFromPropertyName(propertiesSpecial.toString()), convertFromValueName(DarkThemeProperty.AUTOMATIC.toString()))!!)
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
        this.propertiesListEditor.putString(convertFromPropertyName(darkTheme.toString()), convertFromValueName(darkThemeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getAudioChannels(): AudioChannelsProperty {
        val audioChannels: String = convertToValueName(this.propertiesList.getString(convertFromPropertyName(PropertiesSpecial.AUDIO_CHANNELS.toString()), convertFromValueName(AudioChannelsProperty.STEREO.toString()))!!)
        for (i in AudioChannelsProperty.entries) {
            if (audioChannels == i.toString()) {
                return i
            }
        }
        return AudioChannelsProperty.STEREO
    }

    fun setAudioChannels(audioChannelsProperty: AudioChannelsProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.AUDIO_CHANNELS.toString()), convertFromValueName(audioChannelsProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getOnShake(): OnShakeProperty {
        val onShake: String = convertToValueName(this.propertiesList.getString(convertFromPropertyName(PropertiesSpecial.ON_SHAKE.toString()), convertFromValueName(OnShakeProperty.DO_NOTHING.toString()))!!)
        for (i in OnShakeProperty.entries) {
            if (onShake == i.toString()) {
                return i
            }
        }
        return OnShakeProperty.DO_NOTHING
    }

    fun setOnShake(onShakeProperty: OnShakeProperty) {
        this.propertiesListEditor.putString(convertFromPropertyName(PropertiesSpecial.ON_SHAKE.toString()), convertFromValueName(onShakeProperty.toString()))
        this.propertiesListEditor.commit()
    }

    fun getStringProperty(propertiesString: PropertiesString, default: String): String {
        val string: String = this.propertiesList.getString(convertFromPropertyName(propertiesString.toString()), default) ?: ""
        if (string == "") return default
        return string
    }

    fun setStringProperty(propertiesString: PropertiesString, value: String) {
        this.propertiesListEditor.putString(convertFromPropertyName(propertiesString.toString()), value)
        this.propertiesListEditor.commit()
    }

    fun getPrivateStringProperty(propertiesString: PropertiesString, default: String): String {
        val string: String = this.privatePropertiesList.getString(convertFromPropertyName(propertiesString.toString()), default) ?: ""
        if (string == "") return default
        return string
    }

    fun setPrivateStringProperty(propertiesString: PropertiesString, value: String) {
        this.privatePropertiesListEditor.putString(convertFromPropertyName(propertiesString.toString()), value)
        this.privatePropertiesListEditor.commit()
    }

    fun getBooleanProperty(propertiesBoolean: PropertiesBoolean, default: Boolean): Boolean {
        return this.propertiesList.getBoolean(convertFromPropertyName(propertiesBoolean.toString()), default)
    }

    fun setBooleanProperty(propertiesBoolean: PropertiesBoolean, value: Boolean) {
        this.propertiesListEditor.putBoolean(convertFromPropertyName(propertiesBoolean.toString()), value)
        this.propertiesListEditor.commit()
    }

    fun getIntProperty(propertiesInt: PropertiesInt, default: Int): Int {
        return this.propertiesList.getInt(convertFromPropertyName(propertiesInt.toString()), default)
    }

    fun setIntProperty(propertiesInt: PropertiesInt, value: Int) {
        this.propertiesListEditor.putInt(convertFromPropertyName(propertiesInt.toString()), value)
        this.propertiesListEditor.commit()
    }

    fun removeIntProperty(propertiesInt: PropertiesInt) {
        this.propertiesListEditor.remove(convertFromPropertyName(propertiesInt.toString()))
        this.propertiesListEditor.commit()
    }

    fun removeStringProperty(propertiesString: PropertiesString) {
        this.propertiesListEditor.remove(convertFromPropertyName(propertiesString.toString()))
        this.propertiesListEditor.commit()
    }

    fun removeBooleanProperty(propertiesBoolean: PropertiesBoolean) {
        this.propertiesListEditor.remove(convertFromPropertyName(propertiesBoolean.toString()))
        this.propertiesListEditor.commit()
    }

    fun getIntPropertyName(propertiesInt: PropertiesInt): String {
        return convertFromPropertyName(propertiesInt.toString())
    }

    fun getBooleanPropertyName(propertiesBoolean: PropertiesBoolean): String {
        return convertFromPropertyName(propertiesBoolean.toString())
    }

    fun getStringPropertyName(propertiesString: PropertiesString): String {
        return convertFromPropertyName(propertiesString.toString())
    }

    fun getSpecialPropertyName(propertiesSpecial: PropertiesSpecial): String {
        return convertFromPropertyName(propertiesSpecial.toString())
    }
}
