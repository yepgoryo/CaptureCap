package com.yepgoryo.CaptureCap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class PanelPositionScreen : AppCompatActivity() {
    private var appSettings: GlobalProperties? = null
    private var panelPositionBinder: FloatingControls.PanelPositionBinder? = null

    private var mPanelPositionConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            this@PanelPositionScreen.panelPositionBinder = iBinder as FloatingControls.PanelPositionBinder
            var rotation: Int = (this@PanelPositionScreen.baseContext.getSystemService("display") as DisplayManager).getDisplay(0).rotation
            var rect = Rect()
            this@PanelPositionScreen.window.decorView.getWindowVisibleDisplayFrame(rect)
            rect.width()
            rect.height()
            if (rotation != Surface.ROTATION_90) {
            }
            var intent = Intent(this@PanelPositionScreen, FloatingControls::class.java)
            intent.setAction(FloatingControls.ACTION_POSITION_PANEL)
            this@PanelPositionScreen.startService(intent)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@PanelPositionScreen.panelPositionBinder?.setStop()
        }
    }

    public override fun onCreate(bundle: Bundle?) {
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
        setContentView(R.layout.panel_position)
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            findViewById<LinearLayout>(R.id.statusbar).setBackgroundColor(getColor(R.color.statusbar_dark))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<LinearLayout>(R.id.statusbar).visibility = View.GONE
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainposition)) { v, insets ->
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
        findViewById<TextView>(R.id.mainposition)!!.setOnClickListener { this@PanelPositionScreen.finish() }
    }

    private fun panelDisconnect() {
        val panelPositionBinder: FloatingControls.PanelPositionBinder? = this.panelPositionBinder
        if (panelPositionBinder != null) {
            panelPositionBinder.setStop()
            unbindService(this.mPanelPositionConnection)
        }
    }

    public override fun onStart() {
        super.onStart()
        val intent = Intent(this, FloatingControls::class.java)
        intent.setAction(FloatingControls.ACTION_POSITION_PANEL)
        bindService(intent, this.mPanelPositionConnection, Context.BIND_AUTO_CREATE)
    }

    protected override fun onPause() {
        super.onPause()
        panelDisconnect()
    }
}
