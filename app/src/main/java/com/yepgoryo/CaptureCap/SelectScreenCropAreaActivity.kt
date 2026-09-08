package com.yepgoryo.CaptureCap

import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class SelectScreenCropAreaActivity : AppCompatActivity() {

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private lateinit var cropAreaWidget: SelectScreenCropAreaWidget
    private var appSettings: GlobalProperties? = null
    private lateinit var display: Display
    private val TAG = "SelectScreenCropAreaActivity"

    private var indicatorWidth: TextView? = null
    private var indicatorHeight: TextView? = null
    private var indicatorX: TextView? = null
    private var indicatorY: TextView? = null
    private var editWidth: EditText? = null
    private var editHeight: EditText? = null
    private var editX: EditText? = null
    private var editY: EditText? = null
    private var editWidthWatcher: TextWatcher? = null
    private var editHeightWatcher: TextWatcher? = null
    private var editXWatcher: TextWatcher? = null
    private var editYWatcher: TextWatcher? = null

    public override fun onCreate(bundle: Bundle?) {
        val globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties

        this.display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)

        super.onCreate(bundle)
        setContentView(R.layout.screen_recording_crop_area)

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.maincroparea)) { v, insets ->
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

        indicatorWidth = findViewById(R.id.pixelwidth)
        indicatorHeight = findViewById(R.id.pixelheight)
        indicatorX = findViewById(R.id.pixelx)
        indicatorY = findViewById(R.id.pixely)

        editWidth = findViewById(R.id.pixelwidthvalue)
        editHeight = findViewById(R.id.pixelheightvalue)
        editX = findViewById(R.id.pixelxvalue)
        editY = findViewById(R.id.pixelyvalue)

        editWidthWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (editWidth!!.tag != "editing") {
                    try {
                        val newWidthValue = editWidth!!.text.toString().toInt()
                        cropAreaWidget.setCropWidthScreen(newWidthValue)
                    } catch (_: NumberFormatException) {}
                }
            }

        }
        editHeightWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (editHeight!!.tag != "editing") {
                    try {
                        val newHeightValue = editHeight!!.text.toString().toInt()
                        cropAreaWidget.setCropHeightScreen(newHeightValue)
                    } catch (_: NumberFormatException) {}
                }
            }

        }
        editXWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (editX!!.tag != "editing") {
                    try {
                        val newXValue = editX!!.text.toString().toInt()
                        cropAreaWidget.setCropPositionScreenX(newXValue)
                    } catch (_: NumberFormatException) {}
                }
            }

        }
        editYWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (editY!!.tag != "editing") {
                    try {
                        val newYValue = editY!!.text.toString().toInt()
                        cropAreaWidget.setCropPositionScreenY(newYValue)
                    } catch (_: NumberFormatException) {}
                }
            }

        }

        editWidth!!.addTextChangedListener(editWidthWatcher)
        editHeight!!.addTextChangedListener(editHeightWatcher)
        editX!!.addTextChangedListener(editXWatcher)
        editY!!.addTextChangedListener(editYWatcher)

        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val widgetWidth = (screenWidth / 2f).toInt()
        val widgetHeight = (screenHeight / 2f).toInt()

        cropAreaWidget = findViewById(R.id.croparea)

        resetCropConfiguration()

        cropAreaWidget.layoutParams.apply {
            width = widgetWidth
            height = widgetHeight
        }

        setInitialCropValues()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        saveProperties()
        super.onConfigurationChanged(newConfig)

        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val widgetWidth = (screenWidth / 2f).toInt()
        val widgetHeight = (screenHeight / 2f).toInt()

        cropAreaWidget.layoutParams.apply {
            width = widgetWidth
            height = widgetHeight
        }
        resetCropConfiguration()

        setInitialCropValues()
    }

    private fun setInitialCropValues() {
        val cropAreaWidth = cropWidth
        val cropAreaHeight = cropHeight
        val cropAreaX = cropX
        val cropAreaY = cropY

        indicatorWidth!!.setText("${getString(R.string.crop_screen_width)}:")
        editWidth!!.tag = "editing"
        editWidth!!.setText("$cropAreaWidth")
        editWidth!!.tag = ""
        indicatorHeight!!.setText("${getString(R.string.crop_screen_height)}:")
        editHeight!!.tag = "editing"
        editHeight!!.setText("$cropAreaHeight")
        editHeight!!.tag = ""
        indicatorX!!.setText("X:")
        editX!!.tag = "editing"
        editX!!.setText("$cropAreaX")
        editX!!.tag = ""
        indicatorY!!.setText("Y:")
        editY!!.tag = "editing"
        editY!!.setText("$cropAreaY")
        editY!!.tag = ""
    }

    private var cropX: Int = 0
    private var cropY: Int = 0
    private var cropWidth: Int = 0
    private var cropHeight: Int = 0

    private fun resetCropConfiguration() {
        if (screenWidth > screenHeight) {
            Log.d(TAG, "resetConfiguration screen horizontal")
            cropX = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_X_HORIZONTAL,
                cropAreaWidget.getCropPositionScreenX()
            )
            cropY = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_Y_HORIZONTAL,
                cropAreaWidget.getCropPositionScreenY()
            )
            cropWidth = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_HORIZONTAL,
                cropAreaWidget.getCropWidthScreen()
            )
            cropHeight = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_HORIZONTAL,
                cropAreaWidget.getCropHeightScreen()
            )
        } else {
            Log.d(TAG, "resetConfiguration screen vertical")
            cropX = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_X_VERTICAL,
                cropAreaWidget.getCropPositionScreenX()
            )
            cropY = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_Y_VERTICAL,
                cropAreaWidget.getCropPositionScreenY()
            )
            cropWidth = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_VERTICAL,
                cropAreaWidget.getCropWidthScreen()
            )
            cropHeight = appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_VERTICAL,
                cropAreaWidget.getCropHeightScreen()
            )
        }
        Log.d(TAG, "Reset configuration: horizontal: ${screenWidth > screenHeight} cropWidth: $cropWidth, cropHeight: $cropHeight, cropX: $cropX, cropY: $cropY")
        cropAreaWidget.setConfiguration(cropWidth, cropHeight, cropX, cropY, cropAreaWidgetChangeListener)
    }

    private fun saveProperties() {
        if (screenWidth > screenHeight) {
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_X_HORIZONTAL,
                cropAreaWidget.getCropPositionScreenX()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_Y_HORIZONTAL,
                cropAreaWidget.getCropPositionScreenY()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_HORIZONTAL,
                cropAreaWidget.getCropWidthScreen()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_HORIZONTAL,
                cropAreaWidget.getCropHeightScreen()
            )
        } else {
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_X_VERTICAL,
                cropAreaWidget.getCropPositionScreenX()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_Y_VERTICAL,
                cropAreaWidget.getCropPositionScreenY()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_VERTICAL,
                cropAreaWidget.getCropWidthScreen()
            )
            appSettings!!.setIntProperty(
                GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_VERTICAL,
                cropAreaWidget.getCropHeightScreen()
            )
        }
    }

    private var cropAreaWidgetChangeListener: (() -> Unit) = {
        val cropAreaWidth = cropAreaWidget.getCropWidthScreen()
        val cropAreaHeight = cropAreaWidget.getCropHeightScreen()
        val cropAreaX = cropAreaWidget.getCropPositionScreenX()
        val cropAreaY = cropAreaWidget.getCropPositionScreenY()

        indicatorWidth!!.setText("${getString(R.string.crop_screen_width)}:")
        editWidth!!.tag = "editing"
        editWidth!!.setText("$cropAreaWidth")
        editWidth!!.tag = ""
        indicatorHeight!!.setText("${getString(R.string.crop_screen_height)}:")
        editHeight!!.tag = "editing"
        editHeight!!.setText("$cropAreaHeight")
        editHeight!!.tag = ""
        indicatorX!!.setText("X:")
        editX!!.tag = "editing"
        editX!!.setText("$cropAreaX")
        editX!!.tag = ""
        indicatorY!!.setText("Y:")
        editY!!.tag = "editing"
        editY!!.setText("$cropAreaY")
        editY!!.tag = ""

        Log.d(
            TAG,
            "Listener called. Width: $cropAreaWidth Height: $cropAreaHeight X: $cropAreaX Y: $cropAreaY"
        )
    }

    protected override fun onPause() {
        saveProperties()
        super.onPause()
    }

    protected override fun onStop() {
        saveProperties()
        super.onStop()
    }
}
