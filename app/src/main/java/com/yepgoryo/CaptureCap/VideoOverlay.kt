/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.Surface
import androidx.constraintlayout.widget.ConstraintLayout

import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.io.File

import com.google.gson.*
import kotlin.math.*

class VideoOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var items = mutableListOf<OverlayItem>()

    private var overlayCallback: VideoOverlaySettings.OverlayCallback? = null

    private var isHorizontal = false

    var newDeltaX = 0.0f
    var newDeltaY = 0.0f

    private var orientationOnStart: Int = 0

    var widthNormal: Int = 0
    var heightNormal: Int = 0

    var displayWidth: Int = 0
    var displayHeight: Int = 0

    private var backColor: Int = 0
    private var strokeColor: Int = 0

    var currentChosenItem: OverlayItem? = null

    var drawingMode: Boolean = false

    val accumulatedPaths = mutableListOf<PathData>()
    private var currentPath: Path? = null
    private var isDrawing = false

    private var drawingPen = false

    var paintColorGlobal = Color.BLACK
    var paintOpacityGlobal: Int = 255
    private var strokeWidthGlobal = 64f
    var strokeWidthGlobalRatio = 0.5f
    private var strokeCapGlobal = Paint.Cap.ROUND
    private var strokeJoinGlobal = Paint.Join.ROUND

    companion object {
        const val FILE_NAME_VERTICAL = "items_saved_vertical.json"
        const val FILE_NAME_HORIZONTAL = "items_saved_horizontal.json"

        const val BITMAP_BEFORE_CAMERA_HORIZONTAL = "bitmap_before_camera_horizontal.b64"
        const val BITMAP_BEFORE_CAMERA_VERTICAL = "bitmap_before_camera_vertical.b64"
        const val BITMAP_AFTER_CAMERA_HORIZONTAL = "bitmap_after_camera_horizontal.b64"
        const val BITMAP_AFTER_CAMERA_VERTICAL = "bitmap_after_camera_vertical.b64"

        val gson = GsonBuilder()
            .registerTypeAdapter(Bitmap::class.java, BitmapTypeAdapter())
            .serializeNulls()
            .create()

        fun getCameraItem(context: Context, isHorizontal: Boolean): CameraItem? {
            try {
                val fileName = if (isHorizontal) FILE_NAME_HORIZONTAL else FILE_NAME_VERTICAL
                val file = File(context.filesDir, fileName)
                if (!file.exists()) {
                    Log.e("VideoOverlay", "Items file doesn't exist")
                    return null
                }

                val jsonString = file.readText()
                for (item in gson.fromJson(jsonString, Array<OverlayItem>::class.java).toMutableList()) {
                    if (item.isCamera) {
                        return CameraItem(
                            item.x,
                            item.y,
                            item.width,
                            item.height,
                            item.scale,
                            item.opacity/255.0f,
                            item.rotation
                        )
                    }
                }
                return null
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("VideoOverlay", "Error reading items file")
                return null
            }
        }

        fun destroyItems(context: Context, isHorizontal: Boolean) {
            try {
                val fileName = if (isHorizontal) FILE_NAME_HORIZONTAL else FILE_NAME_VERTICAL
                val file = File(context.filesDir, fileName)
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class OverlayItem(
        var bitmap: Bitmap?,
        var text: String?,
        var textColor: Int,
        var textSize: Int,
        var textCentered: Boolean,
        var isCamera: Boolean,
        var x: Float,
        var y: Float,
        var width: Int,
        var height: Int,
        var layerNumber: Int = 0,
        var scale: Float = 1f,
        val maxScale: Float = 1f,
        var opacity: Int = 255,
        var rotation: Float = 0f,
    ) {
        private var cachedCorners: Array<FloatArray> = Array(4) { floatArrayOf(0f, 0f) }
        private var lastRotation: Float = 0f
        private var lastScale: Float = 0f
        private var lastX: Float = 0f
        private var lastY: Float = 0f
        private var lastTextSize: Int = 0

        fun containsTouchPoint(touchX: Float, touchY: Float): Boolean {
            val dx = touchX - x
            val dy = touchY - y

            val cos = cos(Math.toRadians(rotation.toDouble())).toFloat()
            val sin = sin(Math.toRadians(-rotation.toDouble())).toFloat()

            val localX = dx * cos - dy * sin
            val localY = dx * sin + dy * cos

            val halfW = (width*scale) / 2f
            val halfH = (height*scale) / 2f

            return (localX >= -halfW && localX <= halfW &&
                    localY >= -halfH && localY <= halfH)
        }

        fun updateTransform() {
            if (lastRotation == rotation &&
                lastX == x && lastY == y &&
                lastScale == scale &&
                lastTextSize == textSize) return

            val cos = cos(Math.toRadians(rotation.toDouble())).toFloat()
            val sin = sin(Math.toRadians(rotation.toDouble())).toFloat()
            val halfW = (width*scale) / 2f
            val halfH = (height*scale) / 2f

            val cornersLocal = arrayOf(
                floatArrayOf(-halfW, -halfH),
                floatArrayOf(halfW, -halfH),
                floatArrayOf(halfW, halfH),
                floatArrayOf(-halfW, halfH)
            )

            val cachedCornersArray = Array(4) { floatArrayOf(0f, 0f) }

            for (i in cornersLocal.indices) {
                val (lx, ly) = cornersLocal[i]
                cachedCornersArray[i][0] = lx * cos - ly * sin + x
                cachedCornersArray[i][1] = lx * sin + ly * cos + y
            }

            cachedCorners = cachedCornersArray

            lastScale = scale
            lastRotation = rotation
            lastX = x
            lastY = y
            lastTextSize = textSize
        }

        fun getScreenBounds(): RectF {
            val bounds = RectF(cachedCorners[0][0], cachedCorners[0][1],
                cachedCorners[0][0], cachedCorners[0][1])
            for (i in 1..3) bounds.union(cachedCorners[i][0], cachedCorners[i][1])
            return bounds
        }

        fun isPartiallyOffScreen(screenWidth: Int, screenHeight: Int): Boolean {
            val bounds = getScreenBounds()
            return (bounds.left < 0 || bounds.right > screenWidth ||
                    bounds.top < 0 || bounds.bottom > screenHeight)
        }

        fun clampToScreen(screenWidth: Int, screenHeight: Int) {
            updateTransform()

            val bounds = getScreenBounds()
            val dx = when {
                bounds.left < 0f -> -bounds.left
                bounds.right > screenWidth -> screenWidth.toFloat() - bounds.right
                else -> 0f
            }
            val dy = when {
                bounds.top < 0f -> -bounds.top
                bounds.bottom > screenHeight -> screenHeight.toFloat() - bounds.bottom
                else -> 0f
            }

            x += dx
            y += dy

            updateTransform()
        }

        fun clampSizeToScreen(screenWidth: Int, screenHeight: Int) {
            updateTransform()

            var bounds = getScreenBounds()

            if (bounds.top < 0f && bounds.bottom > screenHeight) {
                scale -= (bounds.height() - screenHeight) / screenHeight
            }

            if (bounds.left < 0f && bounds.right > screenWidth) {
                scale -= (bounds.width() - screenWidth) / screenWidth
            }

            updateTransform()
        }
    }

    data class CameraItem(
        var x: Float,
        var y: Float,
        var width: Int,
        var height: Int,
        var scale: Float = 1f,
        var opacity: Float = 1f,
        var rotation: Float = 0f,
    )

    fun setOverlayCallback(callback: VideoOverlaySettings.OverlayCallback) {
        overlayCallback = callback
    }

    fun removeCurrentItem() {
        val currentItem = currentChosenItem
        if (currentItem != null) {
            items.remove(currentItem)
            currentChosenItem = null
            overlayCallback?.removeFocus()
            invalidate()
        }
    }

    fun updateCurrentItemLayer(layer: Int) {
        val currentItem = currentChosenItem
        if (currentItem != null) {
            currentItem.layerNumber = layer
            invalidate()
        }
    }

    fun updateCurrentItemOpacity(opacity: Int) {
        val currentItem = currentChosenItem
        if (currentItem != null) {
            currentItem.opacity = opacity
            invalidate()
        }
    }

    fun updateCurrentItemRotation(rotation: Int) {
        val currentItem = currentChosenItem
        if (currentItem != null) {
            currentItem.rotation = rotation.toFloat()
            checkItemsBoundaries()
            invalidate()
        }
    }

    fun scaleCurrentItem(scale: Float) {
        val currentItem = currentChosenItem
        if (currentItem != null && scale > 0.1f) {
            currentItem.scale = (scale * currentItem.maxScale)
            checkItemsBoundaries()
            invalidate()
        }
    }

    data class PathData(
        val path: Path,
        val color: Int,
        val opacity: Int,
        val strokeWidth: Float,
        val strokeCap: Paint.Cap,
        val strokeJoin: Paint.Join
    )

    fun drawPenMode() {
        drawingMode = true
        drawingPen = true
    }

    fun drawShapeMode(shapeType: DrawingShapeType) {
        drawingMode = true
        drawingPen = false
        currentDrawingShapeType = shapeType
    }

    enum class DrawingShapeType {
        SHAPE_ARROW,
        SHAPE_RECTANGLE,
        SHAPE_CIRCLE
    }

    private var currentDrawingShapeType: DrawingShapeType = DrawingShapeType.SHAPE_ARROW

    open class DrawingShape {
        data class Arrow(
            val path: Path,
            val x1: Float,
            val y1: Float,
            val x2: Float,
            val y2: Float,
            val color: Int,
            val opacity: Int,
            val strokeWidth: Float,
            val strokeCap: Paint.Cap,
            val strokeJoin: Paint.Join
        ) : DrawingShape()

        data class Rectangle(
            val rect: RectF,
            val color: Int,
            val opacity: Int,
            val strokeWidth: Float,
            val strokeCap: Paint.Cap,
            val strokeJoin: Paint.Join
        ) : DrawingShape()

        data class Circle(
            val rect: RectF,
            val color: Int,
            val opacity: Int,
            val strokeWidth: Float,
            val strokeCap: Paint.Cap,
            val strokeJoin: Paint.Join
        ) : DrawingShape()
    }

    private var currentShape: DrawingShape? = null

    val accumulatedShapes = mutableListOf<DrawingShape>()

    fun undoDrawing() {
        if (drawingMode) {
            if (drawingPen) {
                if (!accumulatedPaths!!.isEmpty()) {
                    accumulatedPaths?.removeAt(accumulatedPaths!!.lastIndex)
                    invalidate()
                }
            } else {
                if (!accumulatedShapes!!.isEmpty()) {
                    accumulatedShapes?.removeAt(accumulatedShapes!!.lastIndex)
                    invalidate()
                }
            }
        }
    }

    fun finishDrawing() {
        drawingMode = false
        if (accumulatedPaths.isEmpty() && accumulatedShapes.isEmpty()) return

        val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)

        tempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (pathData in accumulatedPaths) {
            val paint = Paint().apply {
                color = pathData.color
                alpha = pathData.opacity
                style = Paint.Style.STROKE
                strokeCap = pathData.strokeCap
                strokeJoin = pathData.strokeJoin
                strokeWidth = pathData.strokeWidth
                isAntiAlias = true
            }
            tempCanvas.drawPath(pathData.path, paint)
        }

        accumulatedPaths.clear()

        for (shapeData in accumulatedShapes) {
            when (shapeData!!) {
                is DrawingShape.Arrow -> {
                    val paint = Paint().apply {
                        color = shapeData.color
                        alpha = shapeData.opacity
                        style = Paint.Style.STROKE
                        strokeCap = shapeData.strokeCap
                        strokeJoin = shapeData.strokeJoin
                        strokeWidth = shapeData.strokeWidth
                        isAntiAlias = true
                    }
                    val shape = shapeData as DrawingShape.Arrow
                    drawArrow(tempCanvas, paint, shape!!.x1, shape!!.y1, shape!!.x2, shape!!.y2, shapeData.strokeWidth)
                }
                is DrawingShape.Rectangle -> {
                    val paint = Paint().apply {
                        color = shapeData.color
                        alpha = shapeData.opacity
                        style = Paint.Style.STROKE
                        strokeCap = shapeData.strokeCap
                        strokeJoin = shapeData.strokeJoin
                        strokeWidth = shapeData.strokeWidth
                        isAntiAlias = true
                    }
                    paint.style = Paint.Style.FILL
                    tempCanvas.drawRect((shapeData as DrawingShape.Rectangle)!!.rect, paint)
                }
                is DrawingShape.Circle -> {
                    val paint = Paint().apply {
                        color = shapeData.color
                        alpha = shapeData.opacity
                        style = Paint.Style.STROKE
                        strokeCap = shapeData.strokeCap
                        strokeJoin = shapeData.strokeJoin
                        strokeWidth = shapeData.strokeWidth
                        isAntiAlias = true
                    }
                    paint.style = Paint.Style.FILL
                    tempCanvas.drawOval((shapeData as DrawingShape.Circle)!!.rect, paint)
                }
            }
        }

        accumulatedShapes.clear()

        val bounds = findTightBounds(tempBitmap)

        if (bounds.isEmpty()) {
            tempBitmap.recycle()
            return
        }

        val trimmedBitmap = Bitmap.createBitmap(
            tempBitmap,
            bounds.left,
            bounds.top,
            bounds.width(),
            bounds.height()
        )

        tempBitmap.recycle()

        val bitmap = trimmedBitmap

        if (bitmap != null) {
            val item = OverlayItem(
                bitmap = bitmap,
                text = null,
                textColor = Color.WHITE,
                textSize = 32,
                textCentered = false,
                isCamera = false,
                x = bounds.left.toFloat()+bitmap.width/2,
                y = bounds.top.toFloat()+bitmap.height/2,
                width = bitmap.width,
                height = bitmap.height
            )
            items.add(item)
            checkItemsBoundaries()
            invalidate()
        }
    }

    private fun findTightBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var top = -1
        var bottom = -1
        var left = -1
        var right = -1

        findTop@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.TRANSPARENT) {
                    top = y
                    break@findTop
                }
            }
        }

        findBottom@ for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                if (pixels[y * width + x] != Color.TRANSPARENT) {
                    bottom = y
                    break@findBottom
                }
            }
        }

        findLeft@ for (x in 0 until width) {
            for (y in top until bottom + 1) {
                if (pixels[y * width + x] != Color.TRANSPARENT) {
                    left = x
                    break@findLeft
                }
            }
        }

        findRight@ for (x in width - 1 downTo 0) {
            for (y in top until bottom + 1) {
                if (pixels[y * width + x] != Color.TRANSPARENT) {
                    right = x
                    break@findRight
                }
            }
        }

        return Rect(left, top, right, bottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val rotation: Int = this.display!!.rotation
        this.orientationOnStart = rotation
        if (orientationOnStart == Surface.ROTATION_270 || orientationOnStart == Surface.ROTATION_90) {
            isHorizontal = true
        } else {
            isHorizontal = false
        }
        currentChosenItem = null
        drawingMode = false
        currentShape = null
        currentPath = null
        accumulatedPaths.clear()
        accumulatedShapes.clear()
        overlayCallback?.exitDrawMode()
        loadState()

        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)

        widthNormal = width
        heightNormal = height
        updateMetrics()

        var bgColor = TypedValue()
        var stColor = TypedValue()

        val theme = context.theme
        theme.resolveAttribute(R.attr.gridBackground, bgColor, true)
        theme.resolveAttribute(R.attr.gridStroke, stColor, true)
        backColor = bgColor.data
        strokeColor = stColor.data
        setBackgroundColor(backColor)
    }

    var currentItemPressed = false

    private var startX = 0f
    private var startY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false
        val x = event.x
        val y = event.y

        if (drawingMode) {
            handled = when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (overlayCallback!!.editPanelVisible()) {
                        overlayCallback?.hideEditPanel()
                    } else {
                        isDrawing = true
                        startX = x
                        startY = y
                        currentPath = Path().apply { moveTo(x, y) }
                        invalidate()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDrawing && currentPath != null) {
                        if (drawingPen) {
                            currentPath?.lineTo(x, y)
                        } else {
                            currentPath = Path().apply {
                                moveTo(startX, startY)
                                lineTo(x, y)
                            }

                            currentShape = when (currentDrawingShapeType) {
                                DrawingShapeType.SHAPE_CIRCLE -> {
                                    DrawingShape.Circle(
                                        RectF(startX, startY, x, y),
                                        paintColorGlobal,
                                        paintOpacityGlobal,
                                        strokeWidthGlobal * strokeWidthGlobalRatio,
                                        strokeCapGlobal,
                                        strokeJoinGlobal
                                    )
                                }
                                DrawingShapeType.SHAPE_ARROW -> {
                                    DrawingShape.Arrow(
                                        currentPath!!,
                                        startX,
                                        startY,
                                        x,
                                        y,
                                        paintColorGlobal,
                                        paintOpacityGlobal,
                                        strokeWidthGlobal * strokeWidthGlobalRatio,
                                        strokeCapGlobal,
                                        strokeJoinGlobal
                                    )
                                }
                                DrawingShapeType.SHAPE_RECTANGLE -> {
                                    DrawingShape.Rectangle(
                                        RectF(startX, startY, x, y),
                                        paintColorGlobal,
                                        paintOpacityGlobal,
                                        strokeWidthGlobal * strokeWidthGlobalRatio,
                                        strokeCapGlobal,
                                        strokeJoinGlobal
                                    )
                                }
                            }
                        }
                        invalidate()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDrawing = false

                    if (!drawingPen) {
                        currentShape?.let { shape ->
                            if (shape is DrawingShape.Arrow) {
                                if (canDrawArrow(shape.x1, shape.y1, shape.x2, shape.y2)) {
                                    accumulatedShapes.add(shape)
                                }
                            } else {
                                accumulatedShapes.add(shape)
                            }
                            currentShape = null
                            invalidate()
                        }
                        true
                    } else {
                        currentPath?.let { path ->
                            accumulatedPaths.add(
                                PathData(
                                    path,
                                    paintColorGlobal,
                                    paintOpacityGlobal,
                                    strokeWidthGlobal * strokeWidthGlobalRatio,
                                    strokeCapGlobal,
                                    strokeJoinGlobal
                                )
                            )
                            currentPath = null
                            invalidate()
                        }
                        true
                    }
                }

                else -> false
            }
        } else {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val sortedItems = items.sortedBy { it.layerNumber }

                    for (i in items.size - 1 downTo 0) {
                        val item = sortedItems[i]
                        if (item.containsTouchPoint(x, y)) {
                            val deltaX = x - item.x
                            val deltaY = y - item.y

                            currentChosenItem = item
                            currentItemPressed = true
                            overlayCallback?.removeFocus()

                            newDeltaX = deltaX
                            newDeltaY = deltaY
                            checkItemsBoundaries()
                            handled = true
                            break
                        }
                    }

                    if (!handled) {
                        currentChosenItem = null
                        overlayCallback?.removeFocus()
                        invalidate()
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val item = currentChosenItem
                    if (item != null) {
                        val deltaX = newDeltaX
                        val deltaY = newDeltaY

                        item.x = x - deltaX
                        item.y = y - deltaY

                        checkItemsBoundaries()
                        invalidate()

                        handled = true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentItemPressed) {
                        currentItemPressed = false
                        if (currentChosenItem != null) {
                            overlayCallback?.acquireFocus()
                        }
                    } else {
                        overlayCallback?.removeFocus()
                    }
                    checkItemsBoundaries()
                }
            }
        }
        return handled || super.onTouchEvent(event)
    }

    fun canDrawArrow(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        strokeWidth: Float = strokeWidthGlobal * strokeWidthGlobalRatio
    ): Boolean {
        val headLength = 5f * strokeWidth

        val dx = endX - startX
        val dy = endY - startY
        val totalLen = hypot(dx.toDouble(), dy.toDouble())

        if (totalLen <= headLength) return false
        return true
    }

    fun drawArrow(
        canvas: Canvas,
        paint: Paint,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        strokeWidth: Float = strokeWidthGlobal * strokeWidthGlobalRatio
    ) {
        val headLength = 5f * strokeWidth

        val dx = endX - startX
        val dy = endY - startY
        val totalLen = hypot(dx.toDouble(), dy.toDouble())

        if (totalLen <= headLength) return

        val angle = atan2(dy, dx)

        val shaftEndX = (startX + (totalLen - headLength) * cos(angle)).toFloat()
        val shaftEndY = (startY + (totalLen - headLength) * sin(angle)).toFloat()

        val perpAngle = angle + Math.PI / 2
        val headHalfWidth = headLength * 0.5f

        val baseX1 = (endX - headLength * cos(angle) + headHalfWidth * cos(perpAngle)).toFloat()
        val baseY1 = (endY - headLength * sin(angle) + headHalfWidth * sin(perpAngle)).toFloat()

        val baseX2 = (endX - headLength * cos(angle) - headHalfWidth * cos(perpAngle)).toFloat()
        val baseY2 = (endY - headLength * sin(angle) - headHalfWidth * sin(perpAngle)).toFloat()

        canvas.apply {
            drawLine(startX, startY, shaftEndX, shaftEndY, paint)

            val path = Path().apply {
                moveTo(shaftEndX, shaftEndY)
                lineTo(baseX1, baseY1)
                lineTo(endX, endY)
                lineTo(baseX2, baseY2)
                close()
            }

            val prevStyle = paint.style
            paint.style = Paint.Style.FILL
            drawPath(path, paint)
            paint.style = prevStyle
        }
    }

    fun checkItemsBoundaries() {
        for (chosenItem in items) {
            chosenItem.updateTransform()

            if (chosenItem.isPartiallyOffScreen(this.displayWidth, this.displayHeight)) {
                chosenItem.clampSizeToScreen(this.displayWidth, this.displayHeight)
                chosenItem.clampToScreen(this.displayWidth, this.displayHeight)
            }

            if (chosenItem.scale > chosenItem.maxScale) {
                chosenItem.scale = chosenItem.maxScale
            }

            if (chosenItem.scale <= 0.1f) {
                chosenItem.scale = 0.1f
            }

            if ((chosenItem.width * chosenItem.scale) > width) {
                chosenItem.scale = width.toFloat() / chosenItem.width.toFloat()
            }
            if ((chosenItem.height * chosenItem.scale) > height) {
                chosenItem.scale = height.toFloat() / chosenItem.height.toFloat()
            }

            if (chosenItem == currentChosenItem) {
                overlayCallback?.updateEditCurrentItem(currentChosenItem!!)
            }
        }
    }

    fun updateMetrics() {
        displayWidth = widthNormal
        displayHeight = heightNormal
    }

    fun addImageFromPath(path: String) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, this)

            var inSampleSize = 1
            while (outWidth / inSampleSize > width || outHeight / inSampleSize > height) {
                inSampleSize *= 2
            }

            this.inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

        val bitmap = BitmapFactory.decodeFile(path, options)

        if (bitmap != null) {
            val item = OverlayItem(
                bitmap = bitmap,
                text = null,
                textColor = Color.WHITE,
                textSize = 32,
                textCentered = false,
                isCamera = false,
                x = (displayWidth / 2).toFloat(),
                y = (displayHeight / 2).toFloat(),
                width = bitmap.width,
                height = bitmap.height
            )
            items.add(item)
            checkItemsBoundaries()
            invalidate()
        }
    }

    fun updateCurrentTextColor(newColor: Int, opacity: Int) {
        val item = currentChosenItem

        if (item != null && item.bitmap == null && !item.isCamera) {
            item.textColor = newColor
            item.opacity = opacity
            invalidate()
        }
    }

    private fun setItemMeasuredTextBounds(paint: Paint, text: String, item: OverlayItem) {
        var widthList: MutableList<Int> = mutableListOf()

        var totalWith = 0
        var totalHeight = 0
        val fm = paint.fontMetrics

        val lineHeight = fm.leading + abs(fm.top) + fm.bottom

        var firstLine = true

        for (line in text.split("\n")) {
            val rect = Rect()
            paint.getTextBounds(line.trim(), 0, line.trim().length, rect)

            widthList.add(rect.width())
            if (firstLine) {
                totalHeight += rect.height()
                firstLine = false
            } else {
                totalHeight += lineHeight.toInt()
            }
        }

        for (width in widthList) {
            totalWith = max(totalWith, width)
        }

        val textPaintText = TextPaint().apply {
            color = item.textColor
            textSize = resources.displayMetrics.density * item.textSize
            textAlign = Paint.Align.LEFT
            alpha = item.opacity
        }

        var alignment = Layout.Alignment.ALIGN_NORMAL
        if (item.textCentered) {
            alignment = Layout.Alignment.ALIGN_CENTER
        }
        val spacingAdd = 0f
        val spacingMultiplier = 1f
        val includePad = false

        val layout = StaticLayout.Builder.obtain(item.text!!,
            0,
            item.text!!.length,
            textPaintText,
            totalWith+(item.textSize / resources.displayMetrics.density).toInt())
                .setAlignment(alignment)
                .setLineSpacing(spacingAdd, spacingMultiplier)
                .setIncludePad(includePad)
                .build()

        item.width = layout.width
        item.height = layout.height
    }

    fun updateCurrentTextCentered(textCentered: Boolean) {
        val item = currentChosenItem

        if (item != null && item.bitmap == null && !item.isCamera) {
            item.textCentered = textCentered
            invalidate()
        }
    }

    fun updateCurrentTextLabel(text: String) {
        val item = currentChosenItem

        if (item != null && item.bitmap == null && !item.isCamera) {
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = resources.displayMetrics.density * item.textSize
                textAlign = Paint.Align.LEFT
            }

            setItemMeasuredTextBounds(paint, text, item)

            var trimmedTextLines: MutableList<String> = mutableListOf()

            for (line in text.split("\n")) {
                trimmedTextLines.add(line.trim())
            }

            item.text = trimmedTextLines.joinToString("\n")
            checkItemsBoundaries()
            invalidate()
        }
    }

    fun updateCurrentTextSize(newTextSize: Int) {
        val item = currentChosenItem

        if (item != null && item.bitmap == null && !item.isCamera) {
            item.textSize = newTextSize
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = resources.displayMetrics.density * item.textSize
                textAlign = Paint.Align.LEFT
            }

            setItemMeasuredTextBounds(paint, item.text!!, item)

            checkItemsBoundaries()
            invalidate()
        }
    }

    fun addTextLabel(text: String, textSizeNew: Int = 32, textUseColor: Int = Color.BLACK, textCentered: Boolean = false) {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = resources.displayMetrics.density * textSizeNew
            textAlign = Paint.Align.LEFT
        }

        val item = OverlayItem(
            bitmap = null,
            text = text,
            textColor = textUseColor,
            textSize = textSizeNew,
            textCentered = textCentered,
            isCamera = false,
            x = (displayWidth / 2).toFloat(),
            y = (displayHeight / 2).toFloat(),
            width = 0,
            height = 0
        )

        setItemMeasuredTextBounds(paint, text, item)
        items.add(item)
        checkItemsBoundaries()
        invalidate()
    }

    fun hasCamera(): Boolean {
        for (item in items) {
            if (item.isCamera) return true
        }
        return false
    }

    fun addCamera() {
        if (!hasCamera()) {
            val cameraWidth = min(width,height)
            val cameraHeight = cameraWidth
            var inScale = 1.0f

            while (cameraWidth*inScale > width*0.9f || cameraHeight*inScale > height*0.9f) {
                inScale -= 0.01f
            }

            val item = OverlayItem(
                bitmap = null,
                text = null,
                textColor = 0,
                textSize = 0,
                textCentered = false,
                isCamera = true,
                scale = inScale,
                maxScale = inScale,
                x = (displayWidth / 2).toFloat(),
                y = (displayHeight / 2).toFloat(),
                width = cameraWidth,
                height = cameraHeight
            )

            items.add(item)
            checkItemsBoundaries()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawGridBackground(canvas)

        val sortedItems = items.sortedBy { it.layerNumber }

        drawOverlay(canvas, sortedItems, true)
    }

    fun genBitmap(drawItems: List<OverlayItem>): Bitmap {
        val output = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawOverlay(canvas, drawItems, false)

        return output
    }

    fun saveBitmaps() {
        val sortedItems = items.sortedBy { it.layerNumber }

        var itemsBeforeCamera: MutableList<OverlayItem> = mutableListOf()
        var itemsAfterCamera: MutableList<OverlayItem> = mutableListOf()
        var reachedCamera = false

        for (item in sortedItems) {
            if (item.isCamera) {
                reachedCamera = true
            } else {
                if (reachedCamera) {
                    itemsAfterCamera.add(item)
                } else {
                    itemsBeforeCamera.add(item)
                }
            }
        }

        var fileNameBeforeCamera = if (isHorizontal) BITMAP_BEFORE_CAMERA_HORIZONTAL else BITMAP_BEFORE_CAMERA_VERTICAL
        BitmapSerializer.saveBitmapToFile(context, fileNameBeforeCamera, genBitmap(itemsBeforeCamera))

        var fileNameAfterCamera = if (isHorizontal) BITMAP_AFTER_CAMERA_HORIZONTAL else BITMAP_AFTER_CAMERA_VERTICAL
        BitmapSerializer.saveBitmapToFile(context, fileNameAfterCamera, genBitmap(itemsAfterCamera))

    }

    private fun drawOverlay(canvas: Canvas, drawItems: List<OverlayItem>, isPreview: Boolean) {
        val selectedItem = currentChosenItem

        val sortedItems = drawItems.sortedBy { it.layerNumber }

        for (item in sortedItems) {
            canvas.save()

            canvas.scale(
                item.scale,
                item.scale,
                item.x,
                item.y
            )

            if (selectedItem == item && isPreview) {
                val boxPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    color = strokeColor
                    strokeWidth = 20.0f / item.scale
                }
                canvas.save()
                canvas.translate(item.x, item.y)
                canvas.rotate(item.rotation)
                drawRotatedRect(canvas, item, boxPaint)
                canvas.restore()
            }


            if (item.isCamera && isPreview) {
                val boxPaint = Paint().apply { style = Paint.Style.FILL; color = strokeColor; alpha = item.opacity }
                canvas.save()
                canvas.translate(item.x, item.y)
                canvas.rotate(item.rotation)
                canvas.drawRect(
                    -item.width / 2.0f, -item.height / 2.0f,
                    item.width / 2.0f,
                    item.height / 2.0f,
                    boxPaint
                )
                var vectorDrawable = context.getDrawable(R.drawable.icon_record_camera_overlay)

                val h = (vectorDrawable!!.getIntrinsicHeight() / item.scale).toInt()
                val w = (vectorDrawable!!.getIntrinsicWidth() / item.scale).toInt()

                vectorDrawable!!.setBounds(0, 0, w, h)

                canvas.save()
                canvas.translate(-(w / 2).toFloat(),  -(h / 2).toFloat())
                vectorDrawable!!.draw(canvas)
                canvas.restore()
                canvas.restore()

            } else if (item.bitmap != null) {
                val bitmapPaint = Paint().apply {
                    color = item.textColor
                    alpha = item.opacity
                }
                canvas.save()
                canvas.translate(item.x, item.y)
                canvas.rotate(item.rotation)
                drawRotatedImage(canvas, item, bitmapPaint)
                canvas.restore()
            } else if (!item.text.isNullOrEmpty()) {
                val textPaintText = TextPaint().apply {
                    color = item.textColor
                    textSize = resources.displayMetrics.density * item.textSize
                    textAlign = Paint.Align.LEFT
                    alpha = item.opacity
                }

                var alignment = Layout.Alignment.ALIGN_NORMAL
                if (item.textCentered) {
                    alignment = Layout.Alignment.ALIGN_CENTER
                }

                val spacingAdd = 0f
                val spacingMultiplier = 1f
                val includePad = false

                val layout = StaticLayout.Builder.obtain(item.text!!, 0, item.text!!.length, textPaintText, item.width)
                    .setAlignment(alignment)
                    .setLineSpacing(spacingAdd, spacingMultiplier)
                    .setIncludePad(includePad)
                    .build()

                canvas.save()
                canvas.translate(item.x,  item.y)
                canvas.rotate(item.rotation)
                canvas.translate(-item.width / 2.0f, -(item.height / 2.0f))
                layout.draw(canvas)
                canvas.restore()
            }
            canvas.restore()
        }

        if (isPreview) {
            if (drawingMode) {
                if (isDrawing) {
                    val paint = Paint().apply {
                        color = paintColorGlobal
                        alpha = paintOpacityGlobal
                        style = Paint.Style.STROKE
                        strokeCap = strokeCapGlobal
                        strokeJoin = strokeJoinGlobal
                        strokeWidth = strokeWidthGlobal * strokeWidthGlobalRatio
                        isAntiAlias = true
                    }
                    if (drawingPen && currentPath != null) {
                        canvas.drawPath(currentPath!!, paint)
                    } else if (currentShape != null) {
                        when (currentShape!!) {
                            is DrawingShape.Arrow -> {
                                val shape = currentShape as DrawingShape.Arrow?
                                drawArrow(
                                    canvas,
                                    paint,
                                    shape!!.x1,
                                    shape!!.y1,
                                    shape!!.x2,
                                    shape!!.y2,
                                    shape!!.strokeWidth
                                )
                            }

                            is DrawingShape.Rectangle -> {
                                paint.style = Paint.Style.FILL
                                canvas.drawRect(
                                    (currentShape as DrawingShape.Rectangle)!!.rect,
                                    paint
                                )
                            }

                            is DrawingShape.Circle -> {
                                paint.style = Paint.Style.FILL
                                canvas.drawOval((currentShape as DrawingShape.Circle)!!.rect, paint)
                            }
                        }
                    }
                }

                for (pathData in accumulatedPaths) {
                    val paint = Paint().apply {
                        color = pathData.color
                        alpha = pathData.opacity
                        style = Paint.Style.STROKE
                        strokeCap = pathData.strokeCap
                        strokeJoin = pathData.strokeJoin
                        strokeWidth = pathData.strokeWidth
                        isAntiAlias = true
                    }
                    canvas.drawPath(pathData.path, paint)
                }

                for (shapeData in accumulatedShapes) {
                    when (shapeData!!) {
                        is DrawingShape.Arrow -> {
                            val paint = Paint().apply {
                                color = shapeData.color
                                alpha = shapeData.opacity
                                style = Paint.Style.STROKE
                                strokeCap = shapeData.strokeCap
                                strokeJoin = shapeData.strokeJoin
                                strokeWidth = shapeData.strokeWidth
                                isAntiAlias = true
                            }
                            val shape = shapeData as DrawingShape.Arrow?
                            if (shape != null) {
                                drawArrow(
                                    canvas,
                                    paint,
                                    shape!!.x1,
                                    shape!!.y1,
                                    shape!!.x2,
                                    shape!!.y2,
                                    shape!!.strokeWidth
                                )
                            }
                        }

                        is DrawingShape.Rectangle -> {
                            val paint = Paint().apply {
                                color = shapeData.color
                                alpha = shapeData.opacity
                                style = Paint.Style.STROKE
                                strokeCap = shapeData.strokeCap
                                strokeJoin = shapeData.strokeJoin
                                strokeWidth = shapeData.strokeWidth
                                isAntiAlias = true
                            }
                            paint.style = Paint.Style.FILL
                            canvas.drawRect((shapeData as DrawingShape.Rectangle)!!.rect, paint)
                        }

                        is DrawingShape.Circle -> {
                            val paint = Paint().apply {
                                color = shapeData.color
                                alpha = shapeData.opacity
                                style = Paint.Style.STROKE
                                strokeCap = shapeData.strokeCap
                                strokeJoin = shapeData.strokeJoin
                                strokeWidth = shapeData.strokeWidth
                                isAntiAlias = true
                            }
                            paint.style = Paint.Style.FILL
                            canvas.drawOval((shapeData as DrawingShape.Circle)!!.rect, paint)
                        }
                    }
                }
            }
        }
    }

    private fun drawRotatedRect(canvas: Canvas, item: OverlayItem, rectPaint: Paint) {
        val halfW = item.width / 2f
        val halfH = item.height / 2f

        canvas.drawRect(-halfW, -halfH, halfW, halfH, rectPaint)
    }

    private fun drawRotatedImage(canvas: Canvas, item: OverlayItem, imagePaint: Paint) {
        val halfW = item.width / 2f
        val halfH = item.height / 2f

        val srcRect = Rect(0, 0, item.bitmap!!.width, item.bitmap!!.height)
        val dstRect = RectF(-halfW, -halfH, halfW, halfH)

        imagePaint.isFilterBitmap = true

        canvas.drawBitmap(item.bitmap!!, srcRect, dstRect, imagePaint)
    }

    private fun drawGridBackground(canvas: Canvas) {
        val paint = Paint().apply {
            color = strokeColor
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val gridSize = 50f
        for (x in 0 until width step gridSize.toInt()) {
            for (y in 0 until height step gridSize.toInt()) {
                if (((x / gridSize + y / gridSize) % 2).toLong() == 0L) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), x + gridSize, y + gridSize, paint)
                }
            }
        }
    }

    class BitmapTypeAdapter : JsonDeserializer<Bitmap?>, JsonSerializer<Bitmap?> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Bitmap? {
            val base64 = json.asString
            return if (base64.isEmpty()) null else BitmapSerializer.base64ToBitmap(base64)
        }

        override fun serialize(
            src: Bitmap?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return if (src == null) JsonPrimitive("") else JsonPrimitive(BitmapSerializer.bitmapToBase64(src))
        }
    }

    object BitmapSerializer {
        fun bitmapToBase64(bitmap: Bitmap, quality: Int = 100): String {
            val baos = ByteArrayOutputStream()
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bitmap.compress(format, quality, baos)
            val byteArray = baos.toByteArray()
            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }

        fun base64ToBitmap(base64String: String): Bitmap? {
            if (base64String.isEmpty()) return null
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        }

        fun saveBitmapToFile(context: Context, fileName: String, bitmap: Bitmap) {
            val file = File(context.filesDir, fileName)
            file.writeText(bitmapToBase64(bitmap))
        }

        fun loadBitmapFromFile(context: Context, fileName: String): Bitmap? {
            try {
                val file = File(context.filesDir, fileName)
                return base64ToBitmap(file.readText())
            } catch (e: Exception) {
                Log.e("VideoOverlay", e.message!!)
                return null
            }
        }
    }

    fun saveItems(context: Context, structures: MutableList<OverlayItem>) {
        try {
            val jsonString = gson.toJson(structures)
            var fileName = if (isHorizontal) FILE_NAME_HORIZONTAL else FILE_NAME_VERTICAL
            val file = File(context.filesDir, fileName)
            file.writeText(jsonString)
            saveBitmaps()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadItems(context: Context): MutableList<OverlayItem> {
        return try {
            var fileName = if (isHorizontal) FILE_NAME_HORIZONTAL else FILE_NAME_VERTICAL
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                Log.e("VideoOverlay", "Items file doesn't exist")
                return mutableListOf()
            }

            val jsonString = file.readText()
            gson.fromJson(jsonString, Array<OverlayItem>::class.java).toMutableList()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("VideoOverlay", "Error reading items file")
            mutableListOf()
        }
    }

    fun saveState() {
        saveItems(context, items)
        Log.d("VideoOverlay", "Saved ${items.size} items")
    }

    fun loadState() {
        items = loadItems(context)
        Log.d("VideoOverlay", "Loaded ${items.size} items")
        invalidate()
    }
}