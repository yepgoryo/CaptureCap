package com.yepgoryo.CaptureCap

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.preference.ListPreference

class ResolutionList(context: Context, attributeSet: AttributeSet) : ListPreference(context, attributeSet) {

    private var resolutionsList: ArrayList<String> = ArrayList()

    private fun getResolutions(context: Context) {
        var height: Int
        var width: Int
        val display: Display = (context.getSystemService("display") as DisplayManager).getDisplay(0)
        val displayMetrics = DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        val rotation: Int = display.rotation
        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
            height = displayMetrics.heightPixels
            width = displayMetrics.widthPixels
        } else {
            height = displayMetrics.widthPixels
            width = displayMetrics.heightPixels
        }
        if (width <= height) {
            height = width
        }
        if (height == 2160) {
            this.resolutionsList.add("2160p")
        }
        if (height >= 1080) {
            this.resolutionsList.add("1080p")
        }
        if (height >= 720) {
            this.resolutionsList.add("720p")
        }
        if (height >= 480) {
            this.resolutionsList.add("480p")
        }
        if (height >= 360) {
            this.resolutionsList.add("360p")
        }
    }

    init {
        var resolutionNameAuto: String = context.resources.getString(R.string.resolution_option_auto)
        var resolutionValueAuto: String = context.resources.getString(R.string.resolution_option_auto_value)
        getResolutions(context)
        var resolutionNames: Array<String> = Array(this.resolutionsList.size+1) { _ -> "" }
        resolutionNames[0] = resolutionNameAuto
        var resolutionValues: Array<String> = Array(this.resolutionsList.size+1) { _ -> "" }
        resolutionValues[0] = resolutionValueAuto

        var i = 0
        while (i < this.resolutionsList.size) {
            resolutionNames[i+1] = this.resolutionsList.get(i)
            i += 1
        }

        var i2 = 0
        while (i2 < this.resolutionsList.size) {
            resolutionValues[i2+1] = this.resolutionsList.get(i2)
            i2 += 1
        }

        entries = resolutionNames
        entryValues = resolutionValues
        setDefaultValue(resolutionValueAuto)
    }
}
