/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginBottom
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

class VideoOverlaySettings : AppCompatActivity() {

    inner class OverlayCallback() {
        fun updateEditCurrentItem(item: VideoOverlay.OverlayItem) {
            updateEditPanel(item)
        }

        fun acquireFocus() {
            buttonsEnterEditMode()
        }

        fun hideEditPanel() {
            this@VideoOverlaySettings.hideEditPanel()
        }

        fun editPanelVisible(): Boolean {
            return editPanel.isVisible
        }

        fun exitDrawMode() {
            this@VideoOverlaySettings.buttonsExitDrawMode()
        }

        fun removeFocus() {
            hideEditPanel()
            if (canvasView.currentChosenItem == null) {
                buttonsExitEditMode()
            }
        }
    }
    private lateinit var logger: Logger
    private var overlayCallback = OverlayCallback()
    private lateinit var overlaySettingsEditor: FrameLayout
    private lateinit var optionsPanel: FrameLayout
    private lateinit var canvasView: VideoOverlay

    private lateinit var inputScale: SeekBar
    private lateinit var inputScaleChangeListener: SeekBar.OnSeekBarChangeListener
    private lateinit var inputOpacity: SeekBar
    private lateinit var inputOpacityChangeListener: SeekBar.OnSeekBarChangeListener
    private lateinit var opacityBarContainer: LinearLayout
    private lateinit var inputRotation: SeekBar
    private lateinit var inputRotationChangeListener: SeekBar.OnSeekBarChangeListener
    private lateinit var inputEditTextSizeIncrease: Button
    private lateinit var inputEditTextSizeIncreaseChangeListener: View.OnClickListener
    private lateinit var inputEditTextSizeDecrease: Button
    private lateinit var inputEditTextSizeDecreaseChangeListener: View.OnClickListener
    private lateinit var rotationBarContainer: LinearLayout
    private lateinit var removeItem: ImageButton
    private lateinit var overlaySettings: LinearLayout
    private lateinit var settingsPanel: View
    private lateinit var settingsPanelHorizontal: View
    private lateinit var editPanel: LinearLayout
    private lateinit var editOptions: LinearLayout
    private lateinit var editNumOptions: LinearLayout
    private lateinit var addButton: ImageButton
    private lateinit var drawingSaveButton: ImageButton
    private lateinit var drawingUndoButton: ImageButton
    private lateinit var editButton: ImageButton
    private lateinit var overlayEditButtons: LinearLayout
    private lateinit var changeColorButton: Button
    private lateinit var changeColorButtonChangeListener: View.OnClickListener
    private lateinit var textEditButton: Button
    private lateinit var textEditButtonChangeListener: View.OnClickListener
    private lateinit var textSizeEditPanel: LinearLayout
    private lateinit var layerEditPanel: LinearLayout
    private lateinit var editorTextSizeEdit: EditText
    private lateinit var editorTextSizeEditChangeListener: TextWatcher
    private lateinit var layerNumberEdit: EditText
    private lateinit var layerNumberEditChangeListener: TextWatcher
    private lateinit var layerNumberIncrease: Button
    private lateinit var layerNumberIncreaseChangeListener: View.OnClickListener
    private lateinit var layerNumberDecrease: Button
    private lateinit var layerNumberDecreaseChangeListener: View.OnClickListener
    private lateinit var colorPickerDialog: View
    private var editPanelPosUpper = false

    private var editPanelBottomMargin: Int = 0

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
        private val CAMERA_PERMISSION_REQUEST_CODE = 1002
    }

    fun setEditPanelPos(upper: Boolean) {
        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val overlaySettingslayout: FrameLayout.LayoutParams = overlaySettingsEditor?.layoutParams as FrameLayout.LayoutParams
        if (upper) {
            editPanelPosUpper = true
            overlaySettingslayout.topMargin = statusBarHeight
            overlaySettingslayout.gravity = Gravity.TOP
        } else {
            editPanelPosUpper = false
            overlaySettingslayout.topMargin = 0
            overlaySettingslayout.gravity = Gravity.BOTTOM
        }
        overlaySettingsEditor.setLayoutParams(overlaySettingslayout)
    }

    fun updateEditPanel(item: VideoOverlay.OverlayItem) {
        inputScale.setProgress((((item.scale / item.maxScale)) * 100f).toInt())
        inputOpacity.setProgress(item.opacity)
        inputRotation.setProgress(item.rotation.toInt())
        layerNumberEdit.tag = "set"
        layerNumberEdit.setText(item.layerNumber.toString())
        layerNumberEdit.tag = null
        editorTextSizeEdit.tag = "set"
        editorTextSizeEdit.setText(item.textSize.toString())
        editorTextSizeEdit.tag = null
    }

    fun showEditStrokePanel() {
        inputScale.setProgress((canvasView!!.strokeWidthGlobalRatio * 100f).toInt())
        removeItem.visibility = View.GONE
        textEditButton.visibility = View.GONE
        overlayEditButtons.visibility = View.GONE
        changeColorButton.visibility = View.VISIBLE
        textSizeEditPanel.visibility = View.GONE
        opacityBarContainer.visibility = View.GONE
        rotationBarContainer.visibility = View.GONE
        overlayEditButtons.visibility = View.VISIBLE
        textEditButton.visibility = View.GONE
        layerEditPanel.visibility = View.GONE

        overlaySettings.visibility = View.VISIBLE
        optionsPanel.visibility = View.GONE

        editPanel.visibility = View.VISIBLE

        setEditPanelPos(!checkItemInUpperPart())
    }

    fun checkItemInUpperPart(): Boolean {
        val chosenItem = canvasView!!.currentChosenItem
        if (chosenItem != null) {
            overlaySettingsEditor.measure(0,0)
            val panelHeight = overlaySettingsEditor.measuredHeight + overlaySettingsEditor.marginBottom

            if (chosenItem!!.y <= canvasView!!.displayHeight - panelHeight) {
                return true
            } else {
                return false
            }
        }
        return true
    }

    fun showEditPanel(item: VideoOverlay.OverlayItem) {
        inputScale.setProgress((((item.scale / item.maxScale)) * 100f).toInt())
        inputOpacity.setProgress(item.opacity)
        inputOpacity.visibility = View.VISIBLE
        inputRotation.setProgress(item.rotation.toInt())
        inputRotation.visibility = View.VISIBLE
        layerEditPanel.visibility = View.VISIBLE
        rotationBarContainer.visibility = View.VISIBLE
        if (item.isCamera) {
            textEditButton.visibility = View.GONE
            overlayEditButtons.visibility = View.GONE
            changeColorButton.visibility = View.GONE
            textSizeEditPanel.visibility = View.GONE
            opacityBarContainer.visibility = View.VISIBLE
        } else if (item.bitmap == null) {
            textSizeEditPanel.visibility = View.VISIBLE
            overlayEditButtons.visibility = View.VISIBLE
            textEditButton.visibility = View.VISIBLE
            changeColorButton.visibility = View.VISIBLE
            opacityBarContainer.visibility = View.GONE
        } else {
            textEditButton.visibility = View.GONE
            overlayEditButtons.visibility = View.GONE
            changeColorButton.visibility = View.GONE
            textSizeEditPanel.visibility = View.GONE
            opacityBarContainer.visibility = View.VISIBLE
        }
        layerNumberEdit.tag = "set"
        layerNumberEdit.setText(item.layerNumber.toString())
        layerNumberEdit.tag = null
        editorTextSizeEdit.tag = "set"
        editorTextSizeEdit.setText(item.textSize.toString())
        editorTextSizeEdit.tag = null
        overlaySettings.visibility = View.VISIBLE
        optionsPanel.visibility = View.GONE
        editPanel.visibility = View.VISIBLE

        setEditPanelPos(!checkItemInUpperPart())
    }

    fun hideEditPanel() {
        overlaySettings.visibility = View.GONE
        editPanel.visibility = View.GONE
        optionsPanel.visibility = View.VISIBLE
    }

    fun buttonsEnterDrawMode() {
        setEditPanelPos(false)

        editButton.visibility = View.VISIBLE
        removeItem.visibility = View.GONE
        addButton.visibility = View.GONE

        drawingSaveButton.visibility = View.VISIBLE
        drawingUndoButton.visibility = View.VISIBLE
    }

    fun buttonsExitDrawMode() {
        buttonsExitEditMode()
        drawingSaveButton.visibility = View.GONE
        drawingUndoButton.visibility = View.GONE
    }

    fun buttonsEnterEditMode() {
        editButton.visibility = View.VISIBLE
        removeItem.visibility = View.VISIBLE
        addButton.visibility = View.GONE
    }

    fun buttonsExitEditMode() {
        editButton.visibility = View.GONE
        removeItem.visibility = View.GONE
        addButton.visibility = View.VISIBLE
    }

    private fun reloadPanelIds(panel: View) {
        editPanel = panel.findViewById(R.id.overlay_edit)
        editOptions = panel.findViewById(R.id.overlay_edit_options)
        editNumOptions = panel.findViewById(R.id.edit_text_num_options)
        inputScale = panel.findViewById(R.id.input_scale)
        opacityBarContainer = panel.findViewById(R.id.opacitybar_container)
        inputOpacity = panel.findViewById(R.id.input_opacity)
        rotationBarContainer = panel.findViewById(R.id.rotationbar_container)
        inputRotation = panel.findViewById(R.id.input_rotation)
        overlayEditButtons = panel.findViewById(R.id.overlay_edit_buttons)
        changeColorButton = panel.findViewById(R.id.button_edit_color)
        textEditButton = panel.findViewById(R.id.button_edit_text)
        textSizeEditPanel = panel.findViewById(R.id.text_size_edit_panel)
        layerEditPanel = panel.findViewById(R.id.layer_panel)
        editorTextSizeEdit = panel.findViewById(R.id.input_text_size_edit)
        layerNumberEdit = panel.findViewById(R.id.layer_number)
        layerNumberIncrease = panel.findViewById(R.id.button_layer_number_increase)
        layerNumberDecrease = panel.findViewById(R.id.button_layer_number_decrease)
        inputEditTextSizeDecrease = findViewById(R.id.button_text_size_edit_decrease)
        inputEditTextSizeIncrease = findViewById(R.id.button_text_size_edit_increase)
    }

    private fun clearPanelListeners() {
        editorTextSizeEdit.removeTextChangedListener(editorTextSizeEditChangeListener)
        layerNumberEdit.removeTextChangedListener(layerNumberEditChangeListener)
        layerNumberIncrease.setOnClickListener(null)
        layerNumberDecrease.setOnClickListener(null)
        inputEditTextSizeIncrease.setOnClickListener(null)
        inputEditTextSizeDecrease.setOnClickListener(null)
        changeColorButton.setOnClickListener(null)
        textEditButton.setOnClickListener(null)
        inputScale.setOnSeekBarChangeListener(null)
        inputOpacity.setOnSeekBarChangeListener(null)
        inputRotation.setOnSeekBarChangeListener(null)
    }

    private fun loadPanelListeners() {
        editorTextSizeEdit.addTextChangedListener(editorTextSizeEditChangeListener)
        layerNumberEdit.addTextChangedListener(layerNumberEditChangeListener)
        layerNumberIncrease.setOnClickListener(layerNumberIncreaseChangeListener)
        layerNumberDecrease.setOnClickListener(layerNumberDecreaseChangeListener)
        inputEditTextSizeIncrease.setOnClickListener(inputEditTextSizeIncreaseChangeListener)
        inputEditTextSizeDecrease.setOnClickListener(inputEditTextSizeDecreaseChangeListener)
        changeColorButton.setOnClickListener(changeColorButtonChangeListener)
        textEditButton.setOnClickListener(textEditButtonChangeListener)
        inputScale.setOnSeekBarChangeListener(inputScaleChangeListener)
        inputOpacity.setOnSeekBarChangeListener(inputOpacityChangeListener)
        inputRotation.setOnSeekBarChangeListener(inputRotationChangeListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger = Logger(this, baseContext)
        logger.setExceptionHandler()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_overlay_settings)

        var statusBarHeight = 0
        var resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        settingsPanel = LayoutInflater.from(this).inflate(R.layout.video_overlay_settings_panel, null)
        settingsPanelHorizontal = LayoutInflater.from(this).inflate(R.layout.video_overlay_settings_panel_horizontal, null)

        optionsPanel = findViewById(R.id.button_menu_panel)
        overlaySettingsEditor = findViewById(R.id.overlay_settings_editor)
        canvasView = findViewById(R.id.overlay_canvas)
        canvasView.setOverlayCallback(overlayCallback)
        overlaySettings = findViewById(R.id.overlay_settings)

        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                overlaySettings.addView(settingsPanelHorizontal)
                reloadPanelIds(settingsPanelHorizontal)
            }
            else -> {
                overlaySettings.addView(settingsPanel)
                reloadPanelIds(settingsPanel)
            }
        }

        removeItem = findViewById(R.id.button_remove)
        addButton = findViewById(R.id.button_add)
        drawingUndoButton = findViewById(R.id.button_undo_drawing)
        drawingSaveButton = findViewById(R.id.button_save_drawing)
        editButton = findViewById(R.id.button_edit)

        drawingUndoButton.setOnClickListener {
            canvasView!!.undoDrawing()
        }

        drawingSaveButton.setOnClickListener {
            if (canvasView!!.drawingMode) {
                canvasView!!.finishDrawing()
                buttonsExitDrawMode()
            }
        }

        val overlaySettingslayout: FrameLayout.LayoutParams = overlaySettingsEditor?.layoutParams as FrameLayout.LayoutParams

        val optionslayoutparams: FrameLayout.LayoutParams = optionsPanel?.layoutParams as FrameLayout.LayoutParams

        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")

        val display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)

        if (display.rotation == Surface.ROTATION_270) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = statusBarHeight
            overlaySettingslayout.bottomMargin = 0
            editPanelBottomMargin = 0
            overlaySettingslayout.rightMargin = statusBarHeight
            overlaySettingslayout.leftMargin = resources.getDimensionPixelSize(resourceId)
        } else if (display.rotation == Surface.ROTATION_90) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.bottomMargin = 0
            editPanelBottomMargin = 0
            overlaySettingslayout.rightMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.leftMargin = 0
        } else {
            optionslayoutparams.bottomMargin = resources.getDimensionPixelSize(resourceId)
            optionslayoutparams.rightMargin = 0
            overlaySettingslayout.bottomMargin = resources.getDimensionPixelSize(resourceId)
            editPanelBottomMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.rightMargin = 0
            overlaySettingslayout.leftMargin = 0
        }
        canvasView.updateMetrics()
        optionsPanel?.setLayoutParams(optionslayoutparams)
        overlaySettingsEditor?.setLayoutParams(overlaySettingslayout)

        overlaySettingsEditor.measure(0,0)

        val dialogColorPickerView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        colorPickerDialog = dialogColorPickerView

        val recordPanel: LinearLayout = colorPickerDialog.findViewById(R.id.dialog_color_picker)

        val rotation: Int = display!!.rotation
        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            recordPanel.orientation = LinearLayout.VERTICAL
        }

        val colorPalette = dialogColorPickerView.findViewById<ColorPaletteWidget>(R.id.color_palette)
        val colorPicker = dialogColorPickerView.findViewById<ColorPicker>(R.id.color_picker_view)
        val brightnessSeekBar = dialogColorPickerView.findViewById<ValueSeekBar>(R.id.brightness_seekbar)
        val opacitySeekBar = dialogColorPickerView.findViewById<OpacitySeekBar>(R.id.opacity_seekbar)
        val colorPreviewBox = dialogColorPickerView.findViewById<View>(R.id.preview_box)
        val colorHex = dialogColorPickerView.findViewById<EditText>(R.id.color_hex)

        val changeColorDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        changeColorDialogBuilder
            .setTitle(R.string.overlay_change_color)
            .setPositiveButton(R.string.dialog_ok) { dialog, which ->
                val textColor = colorHex.text.toString().uppercase()
                var editedColor = 0

                try {
                    editedColor = Color.parseColor(textColor)
                } catch (e: Exception) {
                    logger.logError(e)
                    editedColor = colorPicker.getCurrentColor()
                }
                val opacityResult = (opacitySeekBar.getOpacityValue() * 255).toInt()

                if (canvasView!!.drawingMode) {
                    canvasView!!.paintColorGlobal = editedColor
                    canvasView!!.paintOpacityGlobal = opacityResult
                } else {
                    canvasView.updateCurrentTextColor(
                        editedColor,
                        opacityResult
                    )
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setView(dialogColorPickerView)

        colorPicker.onColorChangedListener = { color ->
            val newValue = brightnessSeekBar.getValueValue()
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] = newValue

            brightnessSeekBar.setColorGradient(color)

            val colorWithValue = brightnessSeekBar.getColorWithValue()

            opacitySeekBar.setColorChosen(colorWithValue)
            colorPreviewBox.setBackgroundColor(colorWithValue)
            val colorText = String.format("#%06X", (0xFFFFFF and colorWithValue))
            colorHex.tag = "editing"
            colorHex.setText(colorText)
            colorHex.tag = null
        }

        colorPalette.setOnColorItemClickListener { index, color ->
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] = 1.0f
            val newValueColor = Color.HSVToColor(hsv)

            brightnessSeekBar.setColorChosen(color)

            val colorWithValue = brightnessSeekBar.getColorWithValue()

            colorPicker.setColor(newValueColor)
            opacitySeekBar.setColorChosen(colorWithValue)
            colorPreviewBox.setBackgroundColor(colorWithValue)
            val colorText = String.format("#%06X", (0xFFFFFF and colorWithValue))
            colorHex.tag = "editing"
            colorHex.setText(colorText)
            colorHex.tag = null
        }

        brightnessSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val newValue = progress / seekBar!!.max.toFloat()
                opacitySeekBar.setValueChosen(newValue)
                val hsv = FloatArray(3)
                val textColor = colorHex.text.toString().uppercase()

                try {
                    Color.colorToHSV(
                        Color.parseColor(textColor), hsv
                    )
                } catch (e: Exception) {
                    Color.colorToHSV(
                        colorPicker.getCurrentColor(), hsv
                    )
                }

                hsv[2] = newValue

                val colorWithValue = brightnessSeekBar.getColorWithValue()

                val colorText = String.format("#%06X", (0xFFFFFF and colorWithValue))
                opacitySeekBar.setColorChosen(colorWithValue)
                colorPreviewBox.setBackgroundColor(colorWithValue)

                colorHex.tag = "set"
                colorHex.setText(colorText)
                colorHex.tag = null
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) { }

        })

        opacitySeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val newValue = progress / seekBar!!.max.toFloat()
                colorPreviewBox.background.alpha = (255 * newValue).toInt()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) { }

        })

        colorHex.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(editable: Editable) {}

            override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
                if (colorHex.tag == null) {
                    try {
                        val parsedColor = Color.parseColor(charSequence.toString().uppercase())

                        val hsv = FloatArray(3)
                        Color.colorToHSV(parsedColor, hsv)
                        val newColorValue = hsv[2]
                        hsv[2] = 1.0f
                        val newValueColor = Color.HSVToColor(hsv)

                        brightnessSeekBar.progress = (brightnessSeekBar.max * newColorValue).toInt()
                        brightnessSeekBar.setColorGradient(newValueColor)

                        colorPicker.setColor(newValueColor)
                        opacitySeekBar.setColorChosen(parsedColor)
                        colorPreviewBox.setBackgroundColor(parsedColor)
                    } catch (e: Exception) {}
                }
            }
        })

        editorTextSizeEditChangeListener = object: TextWatcher {
            override fun afterTextChanged(editable: Editable) {}

            override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
                if (editorTextSizeEdit.tag == null) {
                    try {
                        val newParsedSize = Integer.parseInt(charSequence.toString())
                        canvasView.updateCurrentTextSize(newParsedSize)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        layerNumberEditChangeListener = object: TextWatcher {
            override fun afterTextChanged(editable: Editable) {}

            override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
                if (layerNumberEdit.tag == null) {
                    try {
                        val parsedLayer = Integer.parseInt(charSequence.toString())
                        canvasView.updateCurrentItemLayer(parsedLayer)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        layerNumberIncreaseChangeListener = View.OnClickListener {
            try {
                val parsedLayer = Integer.parseInt(layerNumberEdit.text.toString())
                layerNumberEdit.setText((parsedLayer + 1).toString())
                canvasView.updateCurrentItemLayer(parsedLayer + 1)
            } catch (e: Exception) {}
        }

        layerNumberDecreaseChangeListener = View.OnClickListener {
            try {
                val parsedLayer = Integer.parseInt(layerNumberEdit.text.toString())
                layerNumberEdit.setText((parsedLayer - 1).toString())
                canvasView.updateCurrentItemLayer(parsedLayer - 1)
            } catch (e: Exception) {}
        }

        inputEditTextSizeIncreaseChangeListener = View.OnClickListener {
            try {
                val parsedSize = Integer.parseInt(editorTextSizeEdit.text.toString())
                editorTextSizeEdit.setText((parsedSize + 1).toString())
                canvasView.updateCurrentTextSize(parsedSize + 1)
            } catch (e: Exception) {}
        }

        inputEditTextSizeDecreaseChangeListener = View.OnClickListener {
            try {
                val parsedSize = Integer.parseInt(editorTextSizeEdit.text.toString())
                editorTextSizeEdit.setText((parsedSize - 1).toString())
                canvasView.updateCurrentTextSize(parsedSize - 1)
            } catch (e: Exception) {}
        }

        val changeColorDialog: AlertDialog = changeColorDialogBuilder.create()

        changeColorButtonChangeListener = View.OnClickListener {
            if (canvasView!!.drawingMode) {
                val color = canvasView!!.paintColorGlobal
                val colorText = String.format("#%06X", (0xFFFFFF and color))
                colorHex.setText(colorText)
                colorPicker.setColor(color)
                brightnessSeekBar.setColorChosen(color)
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                brightnessSeekBar.progress = (brightnessSeekBar.max * hsv[2]).toInt()
                opacitySeekBar.progress = canvasView.paintOpacityGlobal
            } else {
                if (canvasView.currentChosenItem != null) {
                    val color = canvasView.currentChosenItem!!.textColor
                    val colorText = String.format("#%06X", (0xFFFFFF and color))
                    colorPicker.setColor(color)

                    brightnessSeekBar.setColorChosen(color)
                    val hsv = FloatArray(3)
                    Color.colorToHSV(color, hsv)
                    brightnessSeekBar.progress = (brightnessSeekBar.max * hsv[2]).toInt()
                    opacitySeekBar.progress = canvasView.currentChosenItem!!.opacity
                    opacitySeekBar.setColorChosen(color)
                    colorPreviewBox.setBackgroundColor(color)
                    colorHex.tag = "editing"
                    colorHex.setText(colorText)
                    colorHex.tag = null
                }
            }
            changeColorDialog.show()
        }

        removeItem.setOnClickListener {
            canvasView.removeCurrentItem()
        }

        val dialogTextSettingsView = LayoutInflater.from(this).inflate(R.layout.dialog_text_settings, null)

        val textEdited = dialogTextSettingsView.findViewById<EditText>(R.id.input_text_edit_dialog)
        val textEditedCentered = dialogTextSettingsView.findViewById<CheckBox>(R.id.input_text_edit_center)

        textEditedCentered.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                textEdited.gravity = Gravity.CENTER_HORIZONTAL
            } else {
                textEdited.gravity = Gravity.NO_GRAVITY
            }
        }

        val editTextDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        editTextDialogBuilder
            .setTitle(R.string.overlay_edit_text)
            .setPositiveButton(R.string.dialog_ok) { dialog, which ->
                canvasView.updateCurrentTextLabel(textEdited.text.toString())
                canvasView.updateCurrentTextCentered(textEditedCentered.isChecked)
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setView(dialogTextSettingsView)

        val editTextDialog: AlertDialog = editTextDialogBuilder.create()

        textEditButtonChangeListener = View.OnClickListener {
            if (canvasView.currentChosenItem != null) {
                textEdited.setText(canvasView.currentChosenItem!!.text)
                textEditedCentered.isChecked = canvasView.currentChosenItem!!.textCentered
            }
            editTextDialog.show()
        }

        editButton.setOnClickListener {
            if (canvasView.currentChosenItem != null) {
                showEditPanel(canvasView.currentChosenItem!!)
            } else if (canvasView!!.drawingMode) {
                showEditStrokePanel()
            }
        }

        val dialogTextAddView = LayoutInflater.from(this).inflate(R.layout.dialog_text_add, null)

        val textAdded = dialogTextAddView.findViewById<EditText>(R.id.input_text_add_dialog)

        val textAddedCentered = dialogTextAddView.findViewById<CheckBox>(R.id.input_text_add_center)

        textAddedCentered.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                textAdded.gravity = Gravity.CENTER_HORIZONTAL
            } else {
                textAdded.gravity = Gravity.NO_GRAVITY
            }
        }

        val addNewTextDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        addNewTextDialogBuilder
            .setTitle(R.string.overlay_add_new_text)
            .setPositiveButton(R.string.dialog_ok) { dialog, which ->
                canvasView.addTextLabel(textAdded.text.toString(), textCentered = textAddedCentered.isChecked)
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, which -> }
            .setView(dialogTextAddView)

        val addNewTextDialog: AlertDialog = addNewTextDialogBuilder.create()

        val addNewItemDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        addNewItemDialogBuilder
            .setTitle(this.getString(R.string.overlay_add_new_item))
            .setNegativeButton(this.getString(R.string.dialog_cancel)) { _, _ -> }
            .setItems(arrayOf(
                this.getString(R.string.overlay_add_text),
                this.getString(R.string.overlay_add_image),
                this.getString(R.string.overlay_add_pen_drawing),
                this.getString(R.string.overlay_add_arrow),
                this.getString(R.string.overlay_add_circle),
                this.getString(R.string.overlay_add_rectangle),
                this.getString(R.string.overlay_add_camera_window),
            )
            ) { dialog, which ->
                if (which == 0) {
                    addNewTextDialog.show()
                }
                if (which == 1) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, REQUEST_IMAGE_PICK)
                }
                if (which == 2) {
                    buttonsEnterDrawMode()
                    canvasView!!.drawPenMode()
                }
                if (which == 3) {
                    buttonsEnterDrawMode()
                    canvasView!!.drawShapeMode(VideoOverlay.DrawingShapeType.SHAPE_ARROW)
                }
                if (which == 4) {
                    buttonsEnterDrawMode()
                    canvasView!!.drawShapeMode(VideoOverlay.DrawingShapeType.SHAPE_CIRCLE)
                }
                if (which == 5) {
                    buttonsEnterDrawMode()
                    canvasView!!.drawShapeMode(VideoOverlay.DrawingShapeType.SHAPE_RECTANGLE)
                }
                if (which == 6) {
                    if (!canvasView!!.hasCamera()) {
                        if (!hasCameraPermission()) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.CAMERA),
                                CAMERA_PERMISSION_REQUEST_CODE
                            )
                        } else {
                            if (hasFrontCamera()) {
                                canvasView!!.addCamera()
                            }
                        }
                    } else {
                        Toast.makeText(this, R.string.overlay_camera_added, Toast.LENGTH_LONG).show()
                    }
                }
            }

        val addNewItemDialog: AlertDialog = addNewItemDialogBuilder.create()

        addButton.setOnClickListener {
            addNewItemDialog.show()
        }

        inputScaleChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val progressConverted = progress.toFloat() / 100f
                if (canvasView!!.drawingMode) {
                    canvasView.strokeWidthGlobalRatio = progressConverted
                } else {
                    canvasView.scaleCurrentItem(progressConverted)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        inputOpacityChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                canvasView.updateCurrentItemOpacity(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        inputRotationChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                canvasView.updateCurrentItemRotation(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        loadPanelListeners()
    }

    override fun onPause() {
        canvasView.saveState()
        super.onPause()
    }

    override fun onStop() {
        canvasView.saveState()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        hideEditPanel()
        canvasView.saveState()

        clearPanelListeners()

        super.onConfigurationChanged(newConfig)

        overlaySettings.removeView(settingsPanel)
        overlaySettings.removeView(settingsPanelHorizontal)

        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                overlaySettings.addView(settingsPanelHorizontal)
                reloadPanelIds(settingsPanelHorizontal)
            }
            else -> {
                overlaySettings.addView(settingsPanel)
                reloadPanelIds(settingsPanel)
            }
        }
        loadPanelListeners()

        buttonsExitEditMode()

        var statusBarHeight = 0
        var resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val overlaySettingslayout: FrameLayout.LayoutParams = overlaySettingsEditor?.layoutParams as FrameLayout.LayoutParams

        val optionslayoutparams: FrameLayout.LayoutParams = optionsPanel?.layoutParams as FrameLayout.LayoutParams

        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")

        val display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)

        if (display.rotation == Surface.ROTATION_270) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = statusBarHeight
            overlaySettingslayout.bottomMargin = 0
            editPanelBottomMargin = 0
            overlaySettingslayout.rightMargin = statusBarHeight
            overlaySettingslayout.leftMargin = resources.getDimensionPixelSize(resourceId)
            editNumOptions.orientation = LinearLayout.HORIZONTAL
        } else if (display.rotation == Surface.ROTATION_90) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.bottomMargin = 0
            editPanelBottomMargin = 0
            overlaySettingslayout.rightMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.leftMargin = 0
            editNumOptions.orientation = LinearLayout.HORIZONTAL
        } else {
            optionslayoutparams.bottomMargin = resources.getDimensionPixelSize(resourceId)
            optionslayoutparams.rightMargin = 0
            overlaySettingslayout.bottomMargin = resources.getDimensionPixelSize(resourceId)
            editPanelBottomMargin = resources.getDimensionPixelSize(resourceId)
            overlaySettingslayout.rightMargin = 0
            overlaySettingslayout.leftMargin = 0
            editNumOptions.orientation = LinearLayout.VERTICAL
        }
        canvasView.updateMetrics()
        optionsPanel?.setLayoutParams(optionslayoutparams)
        overlaySettingsEditor?.setLayoutParams(overlaySettingslayout)

        val recordPanel: LinearLayout = colorPickerDialog.findViewById(R.id.dialog_color_picker)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            recordPanel.orientation = LinearLayout.VERTICAL
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            val uri: Uri? = data.data
            if (uri != null) {
                val imagePath = getPathFromUri(uri)

                if (imagePath != null) {
                    canvasView.addImageFromPath(imagePath)
                }
            }
        }
    }

    private fun getPathFromUri(contentUri: Uri): String? {
        var projection = arrayOf(MediaStore.Images.Media.DATA)
        var cursor = contentResolver.query(contentUri, projection, null, null, null)
        if (cursor != null) {
            try {
                cursor.moveToFirst()
                return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            } finally {
                cursor.close()
            }
        }
        return null
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
        } else {
            Toast.makeText(this, R.string.error_camera_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasFrontCamera(): Boolean {
        val cameraManager = getSystemService(CameraManager::class.java) ?: return false

        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return true
            }
        }

        return false
    }
}