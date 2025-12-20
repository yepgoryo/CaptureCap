package com.yepgoryo.CaptureCap

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
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
import java.io.File

class SettingsPanel : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private var BPP: Float = 0.25f
    private var appSettings: GlobalProperties? = null
    private var audioFolderPreference: Preference? = null
    private var dialog: AlertDialog? = null
    private var settingsPanel: SettingsFragment? = null
    private var videoFolderPreference: Preference? = null

    private var requestFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@SettingsPanel.requestFolder(result.resultCode, result.data!!.data!!, false)
    }
    private var requestAudioFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@SettingsPanel.requestFolder(result.resultCode, result.data!!.data!!, true)
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
        super.onCreate(bundle)
        val globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties
        val darkTheme: GlobalProperties.DarkThemeProperty = globalProperties.getDarkTheme(true)
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.settings_panel)
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            findViewById<LinearLayout>(R.id.statusbar).setBackgroundColor(getColor(R.color.statusbar_dark))
        }

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
        this.videoFolderPreference = this.settingsPanel!!.findPreference("folderpathpref")
        this.audioFolderPreference = this.settingsPanel!!.findPreference("folderaudiopathpref")
        this.videoFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "None")))
        this.audioFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "None")))
        this.videoFolderPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@SettingsPanel.chooseDir(false)
            true
        }
        this.audioFolderPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@SettingsPanel.chooseDir(true)
            true
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
                Toast.makeText(this@SettingsPanel, R.string.dark_theme_change_notice, Toast.LENGTH_SHORT).show()
                true
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

    fun chooseDir(audio: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (audio) {
            this.requestAudioFolderPermission.launch(intent)
        } else {
            this.requestFolderPermission.launch(intent)
        }
    }

    fun requestFolder(result: Int, uri: Uri, audio: Boolean) {
        if (result != -1) {
            if (audio) {
                if (this.appSettings?.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "NULL").contentEquals("NULL")) {
                    Toast.makeText(this, R.string.error_storage_select_folder, Toast.LENGTH_SHORT).show()
                }
            } else {
                if (this.appSettings?.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "NULL").contentEquals("NULL")) {
                    Toast.makeText(this, R.string.error_storage_select_folder, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (audio) {
                this.appSettings?.setStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, uri.toString())
            } else {
                this.appSettings?.setStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, uri.toString())
            }
            if (audio) {
                this.audioFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "None")))
            } else {
                this.videoFolderPreference?.setSummary(getRealPath(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "None")))
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
}
