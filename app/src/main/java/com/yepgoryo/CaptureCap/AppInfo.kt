package com.yepgoryo.CaptureCap

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppInfo : AppCompatActivity() {
    private var appSettings: GlobalProperties? = null
    private var contributorsAdapter: ContributorsAdapter? = null
    private var contributorsView: RecyclerView? = null
    private var licenseButton: Button? = null
    private var licenseScroll: NestedScrollView? = null

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.appSettings = GlobalProperties(baseContext)

        val darkTheme: GlobalProperties.DarkThemeProperty = this.appSettings!!.getDarkTheme(true)
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight)
        }

        setContentView(R.layout.about)

        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            findViewById<LinearLayout>(R.id.contributorsboard).setBackgroundResource(R.drawable.contributorspanel_shape_dark)
            findViewById<LinearLayout>(R.id.repoboard).setBackgroundResource(R.drawable.contributorspanel_shape_dark)
            findViewById<TextView>(R.id.repogithublink).setLinkTextColor(getColor(R.color.link_color_dark))
            findViewById<LinearLayout>(R.id.statusbar).setBackgroundColor(getColor(R.color.statusbar_dark))
            findViewById<Button>(R.id.showlicense).setBackground(getDrawable(R.drawable.button_show_license_background_dark))
            findViewById<Button>(R.id.showlicense).setTextColor(getColor(R.color.button_color_dark_background))
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<LinearLayout>(R.id.statusbar).visibility = View.GONE
        }

        this.licenseScroll = findViewById(R.id.mainscroll)
        this.licenseButton = findViewById(R.id.showlicense)

        this.licenseButton?.setOnClickListener {
            this@AppInfo.startActivity(Intent(this@AppInfo, LicenseScreen::class.java))
        }

        this.contributorsView = findViewById(R.id.contributorslist)
        this.contributorsAdapter = object: ContributorsAdapter(this.baseContext) {}
        this.contributorsView?.setAdapter(this.contributorsAdapter)
        this.contributorsView?.post { this@AppInfo.licenseScroll?.scrollTo(0, 0) }

        this.contributorsView?.setLayoutManager(LinearLayoutManager(baseContext))

        (findViewById<TextView>(R.id.appversionnum)).text = packageManager.getPackageInfo(packageName, 0).versionName

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }
        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)
        val statusbarlayoutparams: LinearLayout.LayoutParams = statusbarlayout!!.layoutParams as LinearLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainscroll)) { v, insets ->
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

    public override fun onStart() {
        super.onStart()
    }
}
