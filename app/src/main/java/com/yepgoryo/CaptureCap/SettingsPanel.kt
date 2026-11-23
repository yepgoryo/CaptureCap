package com.yepgoryo.CaptureCap

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import rikka.shizuku.Shizuku

import java.io.File

class SettingsPanel : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private lateinit var logger: Logger
    private var BPP: Float = 0.25f
    private var appSettings: GlobalProperties? = null
    private var audioFolderPreference: Preference? = null
    private var dialog: AlertDialog? = null
    private var settingsPanel: SettingsFragment? = null
    private var videoFolderPreference: Preference? = null
    private var logsFolderPreference: Preference? = null
    private var codecPreference: Preference? = null
    private var audioCodecPreference: Preference? = null
    private var shizukuEnabledPreference: SwitchPreferenceCompat? = null
    private var shizukuAutoManageEnabledPreference: SwitchPreferenceCompat? = null
    private var shizukuAuthKeyPreference: EditTextPreference? = null

    companion object {
        const val ACTION_SETTINGS_PANEL_CONNECT: String = MainActivity.appName + ".SETTINGS_PANEL_CONNECT"
    }

    private var requestLogsPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@SettingsPanel.requestFolder(result.resultCode, result.data!!.data!!, false, true)
    }
    private var requestFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@SettingsPanel.requestFolder(result.resultCode, result.data!!.data!!, false, false)
    }
    private var requestAudioFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@SettingsPanel.requestFolder(result.resultCode, result.data!!.data!!, true, false)
    }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == MainActivity.RecordingPermissionRequest.REQUEST_SHIZUKU.ordinal) {
                Shizuku.removeRequestPermissionResultListener(this)

                val enabledPreference = shizukuEnabledPreference as SwitchPreferenceCompat
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    enabledPreference.isChecked = true
                    if (shizukuAutoManageEnabledPreference!!.isChecked) {
                        startShizukuOnService()
                    } else {
                        connectShizukuOnService()
                    }
                } else {
                    enabledPreference.isChecked = false
                    Toast.makeText(this@SettingsPanel, R.string.error_shizuku_required, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private var settingsPanelBinder: ScreenRecorder.SettingsPanelBinder? = null
    private var mConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            settingsPanelBinder = iBinder as ScreenRecorder.SettingsPanelBinder
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            settingsPanelBinder = null
        }
    }

    override fun onPreferenceStartFragment(preferenceFragmentCompat: PreferenceFragmentCompat, preference: Preference): Boolean {
        val extras: Bundle = preference.getExtras()
        val fragmentInstantiate: Fragment = supportFragmentManager.getFragmentFactory().instantiate(classLoader, preference.fragment!!)
        fragmentInstantiate.setArguments(extras)
        fragmentInstantiate.setTargetFragment(preferenceFragmentCompat, 0)
        supportFragmentManager.beginTransaction().replace(R.id.settings, fragmentInstantiate).addToBackStack(null).commit()
        return true
    }

    override fun onCreate(bundle: Bundle?) {
        logger = Logger(this, baseContext)
        logger.setExceptionHandler()
        val globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties
        super.onCreate(bundle)

        setContentView(R.layout.settings_panel)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<LinearLayout>(R.id.statusbar).visibility = View.GONE
        }

        if (bundle == null) {
            this.settingsPanel = SettingsFragment()
            supportFragmentManager.beginTransaction().replace(R.id.settings, this.settingsPanel!!).commit()
        } else {
            this.settingsPanel = supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment
        }

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)

        val statusbarlayoutparams: LinearLayout.LayoutParams = statusbarlayout.layoutParams as LinearLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top-statusBarHeight,
                right = bars.right,
                bottom = bars.bottom,
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onStart() {
        super.onStart()
        doBindService()
        this.logsFolderPreference = this.settingsPanel!!.findPreference("logpathpref")
        this.videoFolderPreference = this.settingsPanel!!.findPreference("folderpathpref")
        this.audioFolderPreference = this.settingsPanel!!.findPreference("folderaudiopathpref")
        this.logsFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_LOGS_PATH, "None")))
        this.videoFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "None")))
        this.audioFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "None")))
        this.logsFolderPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@SettingsPanel.chooseDir(false, true)
            true
        }
        this.videoFolderPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@SettingsPanel.chooseDir(false, false)
            true
        }
        this.audioFolderPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@SettingsPanel.chooseDir(true, false)
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.codecPreference = this.settingsPanel!!.findPreference("codecvalue")
            this.audioCodecPreference = this.settingsPanel!!.findPreference("audiocodecvalue")

            var codecChangeListener = Preference.OnPreferenceChangeListener { pref, obj ->
                var codecName = obj as String

                if (pref.key == this@SettingsPanel.appSettings!!.getStringPropertyName(
                        GlobalProperties.PropertiesString.AUDIO_CODEC_VALUE
                    )
                ) {
                    when (this@SettingsPanel.appSettings!!.getStringProperty(
                        GlobalProperties.PropertiesString.AUDIO_FORMAT_VALUE,
                        resources.getString(R.string.audio_format_option_auto_value)
                    )) {
                        MediaFormat.MIMETYPE_AUDIO_AAC -> {
                            this@SettingsPanel.appSettings!!.setStringProperty(
                                GlobalProperties.PropertiesString.AAC_CODEC,
                                codecName
                            )
                        }

                        else -> {
                            this@SettingsPanel.appSettings!!.setStringProperty(
                                GlobalProperties.PropertiesString.AAC_CODEC,
                                codecName
                            )
                        }
                    }
                } else {
                    when (this@SettingsPanel.appSettings!!.getStringProperty(
                        GlobalProperties.PropertiesString.FORMAT_VALUE,
                        resources.getString(R.string.format_option_auto_value)
                    )) {
                        MediaFormat.MIMETYPE_VIDEO_AVC -> {
                            this@SettingsPanel.appSettings!!.setStringProperty(
                                GlobalProperties.PropertiesString.AVC_CODEC,
                                codecName
                            )
                        }

                        MediaFormat.MIMETYPE_VIDEO_HEVC -> {
                            this@SettingsPanel.appSettings!!.setStringProperty(
                                GlobalProperties.PropertiesString.HEVC_CODEC,
                                codecName
                            )
                        }

                        else -> {
                            this@SettingsPanel.appSettings!!.setStringProperty(
                                GlobalProperties.PropertiesString.AVC_CODEC,
                                codecName
                            )
                        }
                    }
                }
                true
            }

            this.codecPreference!!.onPreferenceChangeListener = codecChangeListener

            this.audioCodecPreference!!.onPreferenceChangeListener = codecChangeListener
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val floatingControlsPreference: Preference = this.settingsPanel!!.findPreference("floatingcontrols")!!
            val floatingControlsPositionPreference: Preference = this.settingsPanel!!.findPreference("floatingcontrolsposition")!!
            val floatingControlsSizePreference: Preference = this.settingsPanel!!.findPreference("floatingcontrolssize")!!
            val floatingControlsOpacityPreference: Preference = this.settingsPanel!!.findPreference("floatingcontrolsopacity")!!

            floatingControlsPreference.onPreferenceChangeListener = object: Preference.OnPreferenceChangeListener {
                override fun onPreferenceChange(preference: Preference, obj: Any): Boolean {
                    val value: Boolean = (obj as Boolean)
                    if (Settings.canDrawOverlays(this@SettingsPanel.applicationContext)) {
                        return true
                    }
                    this@SettingsPanel.requestOverlayDisplayPermission()
                    return !value
                }
            }
            val onPreferenceClickListener: Preference.OnPreferenceClickListener = object: Preference.OnPreferenceClickListener {
                override fun onPreferenceClick(preference: Preference): Boolean {
                    if (Settings.canDrawOverlays(this@SettingsPanel)) {
                        return false
                    }
                    this@SettingsPanel.requestOverlayDisplayPermission()
                    return true
                }
            }
            floatingControlsPositionPreference.onPreferenceClickListener = onPreferenceClickListener
            floatingControlsSizePreference.onPreferenceClickListener = onPreferenceClickListener
            floatingControlsOpacityPreference.onPreferenceClickListener = onPreferenceClickListener

            val overlayResetHorizontal: Preference = this.settingsPanel!!.findPreference("drawoverlayerasehorizontal")!!
            val overlayResetVertical: Preference = this.settingsPanel!!.findPreference("drawoverlayerasevertical")!!

            val onEraseHorizontalPreferenceClickListener: Preference.OnPreferenceClickListener = object: Preference.OnPreferenceClickListener {
                override fun onPreferenceClick(preference: Preference): Boolean {
                    videoOverlayErase(true)
                    return true
                }
            }
            val onEraseVerticalPreferenceClickListener: Preference.OnPreferenceClickListener = object: Preference.OnPreferenceClickListener {
                override fun onPreferenceClick(preference: Preference): Boolean {
                    videoOverlayErase(false)
                    return true
                }
            }
            overlayResetHorizontal.onPreferenceClickListener = onEraseHorizontalPreferenceClickListener
            overlayResetVertical.onPreferenceClickListener = onEraseVerticalPreferenceClickListener
        }
        val useCustomBitratePreference: Preference = this.settingsPanel!!.findPreference("custombitrate")!!
        val bitratePreference: EditTextPreference = this.settingsPanel!!.findPreference("bitratevalue")!!
        useCustomBitratePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, obj ->
                if ((obj as Boolean)) {
                    bitratePreference.setDefaultValue(this@SettingsPanel.getBitrateDefault())
                    bitratePreference.setText(this@SettingsPanel.getBitrateDefault())
                }
                true
            }
        val useCustomSampleRatePreference: Preference = this.settingsPanel!!.findPreference("customsamplerate")!!
        val sampleRatePreference: EditTextPreference = this.settingsPanel!!.findPreference("sampleratevalue")!!
        useCustomSampleRatePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, obj ->
                if ((obj as Boolean)) {
                    sampleRatePreference.setText("44100")
                }
                true
            }
        this.settingsPanel?.findPreference<ListPreference>("darktheme")?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, obj ->
            val mode = obj as String

            resources.getStringArray(R.array.dark_theme_option_preferences_values)
                .forEachIndexed { index, string ->
                    if (string == mode) {
                        when (index) {
                            0 -> {
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                            }
                            1 -> {
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                            }
                            2 -> {
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                            }
                        }
                    }
                }

            Toast.makeText(
                this@SettingsPanel,
                R.string.dark_theme_change_notice,
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.shizukuEnabledPreference = this.settingsPanel!!.findPreference("shizukuenable")
            this.shizukuAutoManageEnabledPreference =
                this.settingsPanel!!.findPreference("shizukuautomanage")
            this.shizukuAuthKeyPreference = this.settingsPanel!!.findPreference("shizukuauthkey")

            this.shizukuEnabledPreference!!.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference, obj ->
                    val shizukuEnabled = obj as Boolean
                    val shizukuSwitch = preference as SwitchPreferenceCompat
                    if (shizukuEnabled) {
                        if (ShizukuConnectionHelper.shizukuAvailable()) {
                            if (!ShizukuConnectionHelper.hasShizukuPermission(this)) {
                                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                                Shizuku.requestPermission(MainActivity.RecordingPermissionRequest.REQUEST_SHIZUKU.ordinal)
                                shizukuSwitch.isChecked = false
                            } else {
                                connectShizukuOnService()
                            }
                        } else if (shizukuAutoManageEnabledPreference!!.isChecked) {
                            startShizukuOnService()
                        }
                    } else {
                        if (ShizukuConnectionHelper.shizukuAvailable()) {
                            disconnectShizukuOnService()
                            if (shizukuAutoManageEnabledPreference!!.isChecked) {
                                stopShizukuOnService()
                            }
                        }
                    }
                    true
                }

            this.shizukuAutoManageEnabledPreference!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, obj ->
                val shizukuAutoManageEnabled = obj as Boolean

                if (shizukuAutoManageEnabled) {
                    if (shizukuEnabledPreference!!.isChecked) {
                        if (!ShizukuConnectionHelper.shizukuAvailable()) {
                            startShizukuOnService()
                        }
                    }
                }
                true
            }

            this.shizukuAuthKeyPreference!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, obj ->
                val shizukuAuthKey = obj as String

                if (!shizukuAuthKey.isEmpty()) {
                    if (shizukuEnabledPreference!!.isChecked) {
                        if (!ShizukuConnectionHelper.shizukuAvailable()) {
                            startShizukuOnService()
                        }
                    }
                }
                true
            }
        }
    }

    fun startShizukuOnService() {
        val shizukuServiceIntent = Intent(this, ScreenRecorder::class.java)
        shizukuServiceIntent.setAction(ScreenRecorder.ACTION_START_SHIZUKU)
        startService(shizukuServiceIntent)
    }

    fun stopShizukuOnService() {
        val shizukuServiceIntent = Intent(this, ScreenRecorder::class.java)
        shizukuServiceIntent.setAction(ScreenRecorder.ACTION_STOP_SHIZUKU)
        startService(shizukuServiceIntent)
    }

    fun connectShizukuOnService() {
        val shizukuServiceIntent = Intent(this, ScreenRecorder::class.java)
        shizukuServiceIntent.setAction(ScreenRecorder.ACTION_CONNECT_SHIZUKU)
        startService(shizukuServiceIntent)
    }

    fun disconnectShizukuOnService() {
        val shizukuServiceIntent = Intent(this, ScreenRecorder::class.java)
        shizukuServiceIntent.setAction(ScreenRecorder.ACTION_DISCONNECT_SHIZUKU)
        startService(shizukuServiceIntent)
    }

    override fun onStop() {
        super.onStop()
        doUnbindService()
    }

    fun doBindService() {
        val intent = Intent(this, ScreenRecorder::class.java)
        intent.action = ACTION_SETTINGS_PANEL_CONNECT
        bindService(intent, this.mConnection, 1)
    }

    fun doUnbindService() {
        if (this.settingsPanelBinder != null) {
            unbindService(this.mConnection)
        }
    }

    private fun getRealPath(path: String): String {
        val strReplaceFirst: String = Regex("^content://[^/]*/tree/").replaceFirst(path, "")
        if (!path.matches(Regex("^content://com\\.android\\.externalstorage\\.documents/tree/.*"))) {
            return path
        }
        if (strReplaceFirst.startsWith("primary%3A")) {
            return "/storage/emulated/0/" + Uri.decode(strReplaceFirst.replaceFirst("primary%3A", "")) + "/"
        }
        val pathNew: String = "/storage/" + Uri.decode(strReplaceFirst.replaceFirst("%3A", "/")) + "/"
        val uri: Uri = Uri.parse(pathNew)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || File(uri.toString()).isDirectory()) {
            return pathNew
        }
        return "/storage/sdcard" + Uri.decode(Regex(".*\\%3A").replaceFirst(strReplaceFirst, "/")) + "/"
    }

    fun chooseDir(audio: Boolean, log: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (log) {
            this.requestLogsPermission.launch(intent)
        } else {
            if (audio) {
                this.requestAudioFolderPermission.launch(intent)
            } else {
                this.requestFolderPermission.launch(intent)
            }
        }
    }

    fun requestFolder(result: Int, uri: Uri, audio: Boolean, log: Boolean) {
        if (result != -1) {
            if (log) {
                if (this.appSettings?.getStringProperty(
                        GlobalProperties.PropertiesString.FOLDER_LOGS_PATH,
                        "NULL"
                    ).contentEquals("NULL")
                ) {
                    Toast.makeText(
                        this,
                        R.string.error_storage_select_log_folder,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                if (audio) {
                    if (this.appSettings?.getStringProperty(
                            GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH,
                            "NULL"
                        ).contentEquals("NULL")
                    ) {
                        Toast.makeText(
                            this,
                            R.string.error_storage_select_folder,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    if (this.appSettings?.getStringProperty(
                            GlobalProperties.PropertiesString.FOLDER_PATH,
                            "NULL"
                        ).contentEquals("NULL")
                    ) {
                        Toast.makeText(
                            this,
                            R.string.error_storage_select_folder,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (log) {
                this.appSettings?.setStringProperty(
                    GlobalProperties.PropertiesString.FOLDER_LOGS_PATH,
                    uri.toString()
                )
                this.logsFolderPreference?.setSummary(
                    getRealPath(
                        this.appSettings!!.getStringProperty(
                            GlobalProperties.PropertiesString.FOLDER_LOGS_PATH,
                            "None"
                        )
                    )
                )
            } else {
                if (audio) {
                    this.appSettings?.setStringProperty(
                        GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH,
                        uri.toString()
                    )
                } else {
                    this.appSettings?.setStringProperty(
                        GlobalProperties.PropertiesString.FOLDER_PATH,
                        uri.toString()
                    )
                }
                if (audio) {
                    this.audioFolderPreference?.setSummary(
                        getRealPath(
                            this.appSettings!!.getStringProperty(
                                GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH,
                                "None"
                            )
                        )
                    )
                } else {
                    this.videoFolderPreference?.setSummary(
                        getRealPath(
                            this.appSettings!!.getStringProperty(
                                GlobalProperties.PropertiesString.FOLDER_PATH,
                                "None"
                            )
                        )
                    )
                }
            }
        }
    }

    fun getBitrateDefault(): String {
        val widthDensity: Int = ((getResources().configuration.screenWidthDp.toFloat() * getResources().configuration.densityDpi.toFloat()) + 0.5f).toInt()
        val heightDensity: Int = ((getResources().configuration.screenHeightDp.toFloat() * getResources().configuration.densityDpi.toFloat()) + 0.5f).toInt()
        this.appSettings?.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_FPS, false)
        var bitrateValue: Int = (Integer.parseInt(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FPS_VALUE, "30")) * BPP * widthDensity * heightDensity).toInt()
        if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_QUALITY, false)) {
            bitrateValue = (bitrateValue * ((this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.QUALITY_SCALE, 9) + 1) * 0.1f)).toInt()
        }
        return bitrateValue.toString()
    }

    fun requestOverlayDisplayPermission() {
        val noticeDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        noticeDialogBuilder.setCancelable(true)
        noticeDialogBuilder.setTitle(R.string.overlay_notice_title)
        noticeDialogBuilder.setMessage(R.string.overlay_notice_description)
        noticeDialogBuilder.setPositiveButton(R.string.overlay_notice_button) { dialogInterface, i ->
            this@SettingsPanel.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + MainActivity.appName)))
        }
        this.dialog = noticeDialogBuilder.create()
        this.dialog?.show()
    }

    fun videoOverlayErase(isHorizontal: Boolean) {
        val noticeDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        noticeDialogBuilder.setCancelable(true)
        if (isHorizontal) {
            noticeDialogBuilder.setTitle(R.string.draw_overlay_erase_horizontal)
        } else {
            noticeDialogBuilder.setTitle(R.string.draw_overlay_erase_vertical)
        }
        noticeDialogBuilder.setMessage(R.string.draw_overlay_erase_warning)
        noticeDialogBuilder.setPositiveButton(R.string.dialog_ok) { dialogInterface, i ->
            VideoOverlay.destroyItems(baseContext, isHorizontal)
            if (isHorizontal) {
                Toast.makeText(
                    baseContext,
                    R.string.draw_overlay_erase_cleared_horizontal,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    baseContext,
                    R.string.draw_overlay_erase_cleared_vertical,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        noticeDialogBuilder.setNeutralButton(R.string.dialog_cancel) { _, _ -> }
        this.dialog = noticeDialogBuilder.create()
        this.dialog?.show()
    }
}
