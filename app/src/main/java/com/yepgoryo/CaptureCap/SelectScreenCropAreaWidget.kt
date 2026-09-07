package com.yepgoryo.CaptureCap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.MotionEvent
import android.view.View

class SelectScreenCropAreaWidget(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var appSettings: GlobalProperties? = null
    private var display: Display? = null

    private val outerRectanglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
    }

    private val innerRectanglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#333333")
    }

    private val resizingHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#555555")
    }

    private val resizingHandlePaintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#777777")
    }

    private val outerLayerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#000000")
    }

    private var cropAreaWidth = width.toFloat()
    private var cropAreaHeight = height.toFloat()

    private var cropAreaWidthMin = width.toFloat() / 2
    private var cropAreaHeightMin = height.toFloat() / 2
    private var innerPaddingWidth = 0f
    private var innerPaddingHeight = 0f

    private var widgetWidth: Int = 0
    private var widgetHeight: Int = 0

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var cropAreaPosX: Float = 0f
    private var cropAreaPosY: Float = 0f

    private var screenWidthRatio: Float = 0f
    private var screenHeightRatio: Float = 0f

    private var newWidth: Float = 0f
    private var newHeight: Float = 0f
    private var newX: Float = 0f
    private var newY: Float = 0f

    private val TAG = "SelectScreenCropAreaWidget"

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val globalProperties = GlobalProperties(context)
        this.appSettings = globalProperties

        val screenCropOuterRectangleColor = TypedValue()
        val screenCropInnerRectangleColor = TypedValue()
        val screenCropResizingHandleColor = TypedValue()
        val screenCropResizingHandleActiveColor = TypedValue()
        val screenCropOuterLayerColor = TypedValue()

        val theme = context.getTheme()
        theme.resolveAttribute(R.attr.screenCropOuterRectangle, screenCropOuterRectangleColor, true)
        theme.resolveAttribute(R.attr.screenCropInnerRectangle, screenCropInnerRectangleColor, true)
        theme.resolveAttribute(R.attr.screenCropResizingHandle, screenCropResizingHandleColor, true)
        theme.resolveAttribute(R.attr.screenCropResizingHandleActive, screenCropResizingHandleActiveColor, true)
        theme.resolveAttribute(R.attr.screenCropOuterLayer, screenCropOuterLayerColor, true)

        outerRectanglePaint.color = screenCropOuterRectangleColor.data
        innerRectanglePaint.color = screenCropInnerRectangleColor.data
        resizingHandlePaint.color = screenCropResizingHandleColor.data
        resizingHandlePaintActive.color = screenCropResizingHandleActiveColor.data
        outerLayerPaint.color = screenCropOuterLayerColor.data

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        this.display = (context.getSystemService("display") as DisplayManager).getDisplay(0)
        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        widgetWidth = w
        widgetHeight = h

        screenWidthRatio = widgetWidth.toFloat() / screenWidth.toFloat()
        screenHeightRatio = widgetHeight.toFloat() / screenHeight.toFloat()

        innerPaddingWidth = w * 0.1f
        innerPaddingHeight = h * 0.1f

        cropAreaWidth = newWidth * screenWidthRatio
        cropAreaHeight = newHeight * screenHeightRatio

        cropAreaPosX = newX * screenWidthRatio
        cropAreaPosY = newY * screenHeightRatio

        cropAreaWidthMin = w / 2f
        cropAreaHeightMin = h / 2f

        checkBoundaries()

        Log.d(TAG, "Size changed. areaWidth: $cropAreaWidth, areaHeight: $cropAreaHeight, x: $cropAreaPosX, y: $cropAreaPosY")
        invalidate()
    }

    fun getCropPositionScreenX() : Int {
        Log.d(TAG, "Converted horizontal: ${screenWidth > screenHeight} cropAreaPosX: $cropAreaPosX ${(cropAreaPosX / screenWidthRatio).toInt()}")
        return (cropAreaPosX / screenWidthRatio).toInt()
    }
    fun getCropPositionScreenY() : Int {
        Log.d(TAG, "Converted horizontal: ${screenWidth > screenHeight} cropAreaPosY: $cropAreaPosY ${(cropAreaPosY / screenHeightRatio).toInt()}")
        return (cropAreaPosY / screenHeightRatio).toInt()
    }
    fun getCropWidthScreen() : Int {
        Log.d(TAG, "Converted horizontal: ${screenWidth > screenHeight} cropAreaWidth: $cropAreaWidth ${(cropAreaWidth / screenWidthRatio).toInt()}")
        return (cropAreaWidth / screenWidthRatio).toInt()
    }
    fun getCropHeightScreen() : Int {
        Log.d(TAG, "Converted horizontal: ${screenWidth > screenHeight} cropAreaHeight: $cropAreaHeight ${(cropAreaHeight / screenHeightRatio).toInt()}")
        return (cropAreaHeight / screenHeightRatio).toInt()
    }

    fun setCropPositionScreenX(newValue: Int) {
        cropAreaPosX = newValue * screenWidthRatio
        checkBoundaries()
        invalidate()
    }
    fun setCropPositionScreenY(newValue: Int) {
        cropAreaPosY = newValue * screenHeightRatio
        checkBoundaries()
        invalidate()
    }
    fun setCropWidthScreen(newValue: Int) {
        cropAreaWidth = newValue * screenWidthRatio
        checkBoundaries()
        invalidate()
    }
    fun setCropHeightScreen(newValue: Int) {
        cropAreaHeight = newValue * screenHeightRatio
        checkBoundaries()
        invalidate()
    }

    private var callBackChanged: (() -> Unit)? = null

    fun setConfiguration(width: Int, height: Int, x: Int, y: Int, cback: (() -> Unit)) {
        newWidth = width.toFloat()
        newHeight = height.toFloat()
        newX = x.toFloat()
        newY = y.toFloat()
        if (callBackChanged == null) {
            callBackChanged = cback
        }
        Log.d(TAG, "Configuration updated. width: $newWidth, height: $newHeight, x: $newX, y: $newY")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.apply {
            save()
            drawCircle(100f, 100f, 100f, outerLayerPaint)
            drawCircle(widgetWidth-100f, 100f, 100f, outerLayerPaint)
            drawCircle(100f, widgetHeight-100f, 100f, outerLayerPaint)
            drawCircle(widgetWidth-100f, widgetHeight-100f, 100f, outerLayerPaint)
            drawRect(RectF(100f, 0f, widgetWidth-100f, widgetHeight.toFloat()), outerLayerPaint)
            drawRect(RectF(0f, 100f, widgetWidth.toFloat(),widgetHeight-100f), outerLayerPaint)
            drawRect(RectF(innerPaddingWidth, innerPaddingHeight, widgetWidth-innerPaddingWidth,widgetHeight-innerPaddingHeight), outerRectanglePaint)
            restore()
            save()
            translate(innerPaddingWidth+((cropAreaPosX+(cropAreaWidth/2))*0.8f), innerPaddingHeight+((cropAreaPosY+(cropAreaHeight/2))*0.8f))
            drawRect(RectF(-(cropAreaWidth/2)*0.8f, (cropAreaHeight/2)*0.8f, (cropAreaWidth/2)*0.8f,-(cropAreaHeight/2)*0.8f), innerRectanglePaint)

            if (resizing && resizingEdge == 0) {
                drawRect(
                    RectF(
                        -(cropAreaWidth / 2) * 0.8f,
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat(),
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat()
                    ), resizingHandlePaintActive
                )
            } else {
                drawRect(
                    RectF(
                        -(cropAreaWidth / 2) * 0.8f,
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat(),
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat()
                    ), resizingHandlePaint
                )
            }
            if (resizing && resizingEdge == 1) {
                drawRect(
                    RectF(
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f),
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat()
                    ), resizingHandlePaintActive
                )
            } else {
                drawRect(
                    RectF(
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f),
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat()
                    ), resizingHandlePaint
                )
            }
            if (resizing && resizingEdge == 2) {
                drawRect(
                    RectF(
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat(),
                        (cropAreaWidth / 2) * 0.8f,
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat()
                    ), resizingHandlePaintActive
                )
            } else {
                drawRect(
                    RectF(
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        -((cropAreaHeight / 2) * 0.8f) + resizingThreshold.toFloat(),
                        (cropAreaWidth / 2) * 0.8f,
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat()
                    ), resizingHandlePaint
                )
            }
            if (resizing && resizingEdge == 3) {
                drawRect(
                    RectF(
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat(),
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f)
                    ), resizingHandlePaintActive
                )
            } else {
                drawRect(
                    RectF(
                        -((cropAreaWidth / 2) * 0.8f) + resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f) - resizingThreshold.toFloat(),
                        ((cropAreaWidth / 2) * 0.8f) - resizingThreshold.toFloat(),
                        ((cropAreaHeight / 2) * 0.8f)
                    ), resizingHandlePaint
                )
            }
            restore()
        }
    }

    fun checkBoundaries() {
        if (cropAreaWidth <= 0f) {
            cropAreaWidth = widgetWidth / 2f
        }

        if (cropAreaHeight <= 0f) {
            cropAreaHeight = widgetHeight / 2f
        }

        if (cropAreaWidth <= cropAreaWidthMin) {
            cropAreaWidth = cropAreaWidthMin
        }

        if (cropAreaWidth >= widgetWidth) {
            cropAreaWidth = widgetWidth.toFloat()
        }

        if (cropAreaHeight <= cropAreaHeightMin) {
            cropAreaHeight = cropAreaHeightMin
        }

        if (cropAreaHeight >= widgetHeight) {
            cropAreaHeight = widgetHeight.toFloat()
        }

        if (cropAreaPosX <= 0f) {
            cropAreaPosX = 0f
        }

        if (cropAreaPosX > widgetWidth.toFloat()-cropAreaWidth) {
            cropAreaPosX = widgetWidth-cropAreaWidth
        }

        if (cropAreaPosY <= 0f) {
            cropAreaPosY = 0f
        }

        if (cropAreaPosY > widgetHeight.toFloat()-cropAreaHeight) {
            cropAreaPosY = widgetHeight-cropAreaHeight
        }
        invalidate()
    }

    private var moving: Boolean = false
    private var resizing: Boolean = false

    private var motionEventX: Float = 0f
    private var motionEventY: Float = 0f

    private var lastWidth: Float = 0f
    private var lastHeight: Float = 0f

    private var lastPosX: Float = 0f
    private var lastPosY: Float = 0f

    private var resizingEdge: Int = 1
    private val resizingThreshold: Int = 50

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        callBackChanged?.invoke()
        if (event!!.action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "Touch detected")
            if (event!!.x in innerPaddingWidth+((cropAreaPosX+resizingThreshold)*0.8f)..innerPaddingWidth+((cropAreaPosX+cropAreaWidth-resizingThreshold)*0.8f) && event!!.y in innerPaddingHeight+((cropAreaPosY+resizingThreshold)*0.8f)..innerPaddingHeight+((cropAreaPosY+cropAreaHeight-resizingThreshold)*0.8f)) {
                moving = true
                motionEventX = event!!.x
                lastPosX = cropAreaPosX
                motionEventY = event!!.y
                lastPosY = cropAreaPosY
            }

            if (event!!.x in innerPaddingWidth+((cropAreaPosX)*0.8f)..innerPaddingWidth+((cropAreaPosX+cropAreaWidth)*0.8f) && event!!.y in innerPaddingHeight+(cropAreaPosY*0.8f)..innerPaddingHeight+((cropAreaPosY+resizingThreshold)*0.8f)) {
                resizing = true
                resizingEdge = 1
                motionEventX = event!!.x
                lastWidth = cropAreaWidth
                motionEventY = event!!.y
                lastHeight = cropAreaHeight
                lastPosX = cropAreaPosX
                lastPosY = cropAreaPosY
            }
            if (event!!.x in innerPaddingWidth+((cropAreaPosX+cropAreaWidth-resizingThreshold)*0.8f)..innerPaddingWidth+((cropAreaPosX+cropAreaWidth)*0.8f) && event!!.y in innerPaddingHeight+(cropAreaPosY*0.8f)..innerPaddingHeight+((cropAreaPosY+cropAreaHeight)*0.8f)) {
                resizing = true
                resizingEdge = 2
                motionEventX = event!!.x
                lastWidth = cropAreaWidth
                motionEventY = event!!.y
                lastHeight = cropAreaHeight
                lastPosX = cropAreaPosX
                lastPosY = cropAreaPosY
            }
            if (event!!.x in innerPaddingWidth+((cropAreaPosX)*0.8f)..innerPaddingWidth+((cropAreaPosX+cropAreaWidth)*0.8f) && event!!.y in innerPaddingHeight+((cropAreaPosY+cropAreaHeight-resizingThreshold)*0.8f)..innerPaddingHeight+((cropAreaPosY+cropAreaHeight)*0.8f)) {
                resizing = true
                resizingEdge = 3
                motionEventX = event!!.x
                lastWidth = cropAreaWidth
                motionEventY = event!!.y
                lastHeight = cropAreaHeight
                lastPosX = cropAreaPosX
                lastPosY = cropAreaPosY
            }
            if (event!!.x in innerPaddingWidth+((cropAreaPosX)*0.8f)..innerPaddingWidth+((cropAreaPosX+resizingThreshold)*0.8f) && event!!.y in innerPaddingHeight+(cropAreaPosY*0.8f)..innerPaddingHeight+((cropAreaPosY+cropAreaHeight)*0.8f)) {
                resizing = true
                resizingEdge = 0
                motionEventX = event!!.x
                lastWidth = cropAreaWidth
                motionEventY = event!!.y
                lastHeight = cropAreaHeight
                lastPosX = cropAreaPosX
                lastPosY = cropAreaPosY
            }
            checkBoundaries()
            invalidate()
            return true
        }
        if (event!!.action == MotionEvent.ACTION_MOVE) {
            if (moving) {
                Log.d(TAG, "Move detected")
                val deltaX = motionEventX - event!!.x
                cropAreaPosX = lastPosX - deltaX
                val deltaY = motionEventY - event!!.y
                cropAreaPosY = lastPosY - deltaY
                Log.d(TAG, "Moving, delta $deltaX $deltaY, posX: ${getCropPositionScreenX()}, posY: ${getCropPositionScreenY()}")
            } else if (resizing) {
                Log.d(TAG, "Resize detected")
                if (resizingEdge == 1) {
                    val deltaY = motionEventY - event!!.y
                    cropAreaHeight = lastHeight + deltaY
                    if (cropAreaHeight > cropAreaHeightMin) {
                        cropAreaPosY = lastPosY - deltaY
                    }
                } else if (resizingEdge == 2) {
                    val deltaX = motionEventX - event!!.x
                    cropAreaWidth = lastWidth - deltaX
                } else if (resizingEdge == 3) {
                    val deltaY = motionEventY - event!!.y
                    cropAreaHeight = lastHeight - deltaY
                } else if (resizingEdge == 0) {
                    val deltaX = motionEventX - event!!.x
                    cropAreaWidth = lastWidth + deltaX
                    if (cropAreaWidth > cropAreaWidthMin) {
                        cropAreaPosX = lastPosX - deltaX
                    }
                }
            }
            checkBoundaries()
            invalidate()
            return true
        }
        if (event!!.action == MotionEvent.ACTION_UP) {
            moving = false
            resizing = false
            resizingEdge = 0
            lastPosX = cropAreaPosX
            lastPosY = cropAreaPosY
            checkBoundaries()
            invalidate()
            return true
        }
        invalidate()
        return super.onTouchEvent(event)
    }
}
