/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

import kotlin.math.*

class RecordingCropBar : View {

    private var thumbSize = 24f

    private var trackHeight = 8f

    private var tickHeight = 10f

    private var timelinePadding = 16f

    private var timestampTextSize = 14f

    private var minPosition = 0f

    private var maxPosition = 1000f

    var minSelection = 50f

    var maxSelection = 950f

    private var minSelectionSize: Float = 100f

    private var currentZoom = 1.0f

    private var minZoom = 1.0f

    private var maxZoom = 15.0f

    private var panOffsetX = 0f

    private var touchStartX = 0f

    private var isDraggingLeft = false

    private var isDraggingRight = false

    private var isPanning = false

    var isCursorMode = false

    private var cursorTime: Float = 0f

    private var isDraggingCursor = false

    private var trackInitialized = false

    private var currentZoomAnimator: ValueAnimator? = null

    private var currentPanAnimator: ValueAnimator? = null

    private var isAnimatingFitSelection = false

    private val desiredWidth = 800

    private val desiredHeight = 120

    private val trackPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = resources.getColor(R.color.crop_track_paint)
        strokeWidth = trackHeight
    }

    private val selectedTrackPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = trackHeight * 1.5f
        strokeWidth = trackHeight
    }

    private val thumbDraggingPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f
    }

    private val thumbPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f
    }

    private val thumbBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tickPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        textSize = timestampTextSize * context.resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }

    private val cursorPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        var globalProperties = GlobalProperties(context)
        val darkTheme: GlobalProperties.DarkThemeProperty = globalProperties.getDarkTheme(false)

        val cropBarText = TypedValue()
        val cropBarTrack = TypedValue()
        val cropBarTrackSelected = TypedValue()
        val cropBarThumb = TypedValue()
        val cropBarThumbDragging = TypedValue()
        val cropBarThumbBorder = TypedValue()
        val cropBarTick = TypedValue()
        val cropBarCursor = TypedValue()

        val theme = context.getTheme()
        theme.resolveAttribute(R.attr.cropBarText, cropBarText, true)
        theme.resolveAttribute(R.attr.cropBarTrack, cropBarTrack, true)
        theme.resolveAttribute(R.attr.cropBarTrackSelected, cropBarTrackSelected, true)
        theme.resolveAttribute(R.attr.cropBarThumb, cropBarThumb, true)
        theme.resolveAttribute(R.attr.cropBarThumbDragging, cropBarThumbDragging, true)
        theme.resolveAttribute(R.attr.cropBarThumbBorder, cropBarThumbBorder, true)
        theme.resolveAttribute(R.attr.cropBarTick, cropBarTick, true)
        theme.resolveAttribute(R.attr.cropBarCursor, cropBarCursor, true)

        trackPaint.color = cropBarTrack.data
        selectedTrackPaint.color = cropBarTrackSelected.data
        thumbDraggingPaint.color = cropBarThumbDragging.data
        thumbPaint.color = cropBarThumb.data
        thumbBorderPaint.color = cropBarThumbBorder.data
        tickPaint.color = cropBarTick.data
        textPaint.color = cropBarText.data
        cursorPaint.color = cropBarCursor.data

        thumbSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            thumbSize,
            context.resources.displayMetrics
        )

        trackHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            trackHeight,
            context.resources.displayMetrics
        )

        timelinePadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            timelinePadding,
            context.resources.displayMetrics
        )

        timestampTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            timestampTextSize,
            context.resources.displayMetrics
        )
    }

    private fun getActualThumbWidth(): Float {
        return thumbSize / currentZoom.coerceAtLeast(1e-5f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f

        val leftX = timelineToScreen(minPosition)
        val rightX = timelineToScreen(maxPosition)
        canvas.drawRect(leftX, centerY - trackHeight / 2f, rightX, centerY + trackHeight / 2f, trackPaint)

        val selectionLeftX = timelineToScreen(minSelection)
        val selectionRightX = timelineToScreen(maxSelection)
        canvas.drawRect(selectionLeftX, centerY - trackHeight / 2f, selectionRightX, centerY + trackHeight / 2f, selectedTrackPaint)

        if (!isCursorMode) {
            if (!isDraggingLeft) {
                drawThumb(canvas, timelineToScreen(minSelection), centerY, thumbPaint)
            } else {
                drawThumb(canvas, timelineToScreen(minSelection), centerY, thumbDraggingPaint)
            }

            if (!isDraggingRight) {
                drawThumb(canvas, timelineToScreen(maxSelection), centerY, thumbPaint)
            } else {
                drawThumb(canvas, timelineToScreen(maxSelection), centerY, thumbDraggingPaint)
            }
        } else {
            val cursorScreenX = timelineToScreen(cursorTime)
            canvas.drawLine(cursorScreenX, 0f, cursorScreenX, height.toFloat(), cursorPaint)
        }

        drawTicks(canvas, centerY)
    }

    private fun drawTicks(canvas: Canvas, centerY: Float) {
        val visibleRangeStart = screenToTimeline(0f)
        val visibleRangeEnd = screenToTimeline(width.toFloat())

        if (visibleRangeStart >= visibleRangeEnd) return

        val targetTickSpacingInPixels = 340f
        val zoom = currentZoom.coerceAtLeast(1e-5f)
        val idealTimelineSpacing = targetTickSpacingInPixels / zoom

        val logSpacing = floor(log10(idealTimelineSpacing))
        val base = 10f.pow(logSpacing)
        val mantissa = idealTimelineSpacing / base
        val niceMantissa = when {
            mantissa < 1.5f -> 1f
            mantissa < 3.5f -> 2f
            mantissa < 7.5f -> 5f
            else -> 10f
        }
        var tickSpacing = base * niceMantissa

        val startTick = ceil(visibleRangeStart / tickSpacing) * tickSpacing
        val endTick = floor(visibleRangeEnd / tickSpacing) * tickSpacing

        textPaint.textAlign = Paint.Align.CENTER

        val tickCount = ((endTick - startTick) / tickSpacing).toInt().coerceAtLeast(0)

        for (i in 0..tickCount) {
            val timelineX = startTick + i * tickSpacing

            if (timelineX < minPosition || timelineX > maxPosition) continue

            val screenX = timelineToScreen(timelineX).coerceIn(0f, width.toFloat())

            canvas.drawLine(
                screenX, centerY - tickHeight,
                screenX, centerY + tickHeight,
                tickPaint
            )

            val label = formatTime(timelineX)

            canvas.drawText(
                label,
                screenX,
                centerY + tickHeight + textPaint.textSize + 10f,
                textPaint
            )
        }
    }

    fun enterCursorMode(startCursorAt: Float) {
        cursorTime = startCursorAt.coerceIn(minSelection, maxSelection)
        isCursorMode = true
        fitSelectionToView()
        invalidate()
    }

    fun exitCursorMode() {
        isCursorMode = false
        invalidate()
    }

    fun setCursorPosition(time: Float) {
        if (!isCursorMode) return
        cursorTime = time.coerceIn(minSelection, maxSelection)
        invalidate()
    }

    fun getCursorPosition(): Float {
        return cursorTime
    }

    private fun drawThumb(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        canvas.drawCircle(x, y, thumbSize / 2, paint)
        canvas.drawCircle(x, y, thumbSize / 2, thumbBorderPaint)
    }

    private fun clampPanOffset() {
        minZoom = getMaxZoomToFitTimeline()
        currentZoom = currentZoom.coerceAtLeast(minZoom)

        val clampedVisibleWidth = width / currentZoom.coerceAtLeast(1e-5f)

        val effectiveStart = minPosition - getActualThumbWidth()
        val effectiveEnd = maxPosition + getActualThumbWidth()

        val minPan = effectiveStart
        val maxPan = effectiveEnd - clampedVisibleWidth

        val safeMinPan = minPan.coerceAtMost(maxPan)
        val safeMaxPan = maxPan.coerceAtLeast(minPan)

        panOffsetX = panOffsetX.coerceIn(safeMinPan, safeMaxPan)
    }

    private fun timelineToScreen(timelineX: Float): Float {
        return (timelineX - panOffsetX) * currentZoom
    }

    private fun screenToTimeline(screenX: Float): Float {
        return panOffsetX + screenX / currentZoom
    }

    private fun formatTime(value: Float): String {
        val totalMs = value.toInt()
        val absMs = abs(totalMs)
        val totalSeconds = absMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = absMs % 1000
        return String.format("%d:%02d.%03d", minutes, seconds, millis)
    }

    fun zoomIn() {
        if (!isCursorMode) {
            val targetZoom = (currentZoom * 1.5f).coerceAtMost(maxZoom)
            animateZoomTo(targetZoom)
        }
    }

    fun zoomOut() {
        if (!isCursorMode) {
            val targetZoom = (currentZoom / 1.5f).coerceAtLeast(minZoom)
            animateZoomTo(targetZoom)
        }
    }

    fun setZoom(zoom: Float) {
        if (!isCursorMode) {
            val targetZoom = zoom.coerceAtLeast(minZoom)
            animateZoomTo(targetZoom)
        }
    }

    fun setSelectionRange(minSel: Float, maxSel: Float) {
        val min = minSel.coerceIn(minPosition, maxPosition)
        val max = maxSel.coerceIn(min + minSelectionSize, maxPosition)
        minSelection = min
        maxSelection = max
        invalidate()
    }

    fun getSelectionRange(): Pair<Float, Float> {
        return Pair(minSelection, maxSelection)
    }

    private fun animateZoomTo(targetZoom: Float, duration: Long = 400L) {
        if (targetZoom <= 0f) return

        val screenCenterX = width / 2f
        val timelineAtScreenCenterBefore = screenToTimeline(screenCenterX)

        val startZoom = currentZoom

        val clampedZoom = targetZoom.coerceIn(minZoom, maxZoom)

        currentZoomAnimator?.cancel()
        currentZoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()

            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                val zoomT = startZoom + (clampedZoom - startZoom) * t

                val visibleWidthT = width / zoomT.coerceAtLeast(1e-5f)
                val panT = timelineAtScreenCenterBefore - visibleWidthT / 2f

                val visibleRangeT = width / zoomT.coerceAtLeast(1e-5f)
                val minPanT = minPosition
                val maxPanT = maxPosition - visibleRangeT

                val clampedPanT = if (minPanT <= maxPanT) {
                    panT.coerceIn(minPanT, maxPanT)
                } else {
                    (minPosition + maxPosition) / 2f - visibleRangeT / 2f
                }

                currentZoom = zoomT
                panOffsetX = clampedPanT
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentZoom = clampedZoom
                    val visibleWidthFinal = width / clampedZoom.coerceAtLeast(1e-5f)
                    panOffsetX = timelineAtScreenCenterBefore - visibleWidthFinal / 2f

                    val visibleRangeFinal = width / currentZoom.coerceAtLeast(1e-5f)
                    val minPan = minPosition
                    val maxPan = maxPosition - visibleRangeFinal
                    panOffsetX = if (minPan <= maxPan) {
                        panOffsetX.coerceIn(minPan, maxPan)
                    } else {
                        (minPosition + maxPosition) / 2f - visibleRangeFinal / 2f
                    }

                    touchStartX = timelineToScreen(panOffsetX + width / (2f * currentZoom.coerceAtLeast(1e-5f)))

                    currentZoomAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentZoomAnimator = null
                }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        minZoom = getMaxZoomToFitTimeline()
        currentZoom = minZoom
        initializeTrack()
        clampPanOffset()
    }

    private fun initializeTrack() {
        var zoomFind = getMaxZoomToFitTimeline()
        while (zoomFind < currentZoom) {
            minZoom = zoomFind
            currentZoom = minZoom
            zoomFind = getMaxZoomToFitTimeline()
        }
        currentZoom = minZoom
    }

    private fun getMaxZoomToFitTimeline(): Float {
        if (!trackInitialized) {
            trackInitialized = true
            initializeTrack()
        }

        val totalTimelineWidth = maxPosition - minPosition
        val paddedWidth = totalTimelineWidth + getActualThumbWidth() * 2f

        return if (paddedWidth <= 0f) Float.MAX_VALUE else width / paddedWidth
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount != 1) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val leftX = timelineToScreen(minSelection)
                val rightX = timelineToScreen(maxSelection)

                if (isCursorMode) {
                    val cursorScreenX = timelineToScreen(cursorTime)
                    val threshold = thumbSize * 2f
                    val timelineTouchX = screenToTimeline(event.x)

                    val inSelection = timelineTouchX in (minSelection..maxSelection)

                    if (abs(event.x - cursorScreenX) < threshold || inSelection) {
                        cursorTime = timelineTouchX
                        isDraggingCursor = true
                        touchStartX = event.x
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                val leftThreshold = thumbSize * 2f
                val rightThreshold = thumbSize * 2f

                isDraggingLeft = abs(event.x - leftX) < leftThreshold
                isDraggingRight = abs(event.x - rightX) < rightThreshold

                if (isDraggingLeft && isDraggingRight) {
                    isDraggingRight = false
                }

                isPanning = !isDraggingLeft && !isDraggingRight && abs(event.y - (height / 2f)) < trackHeight * 3

                if (isDraggingLeft || isDraggingRight || isPanning) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }

                touchStartX = event.x
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingCursor) {
                    val deltaScreen = event.x - touchStartX
                    val deltaTimeline = deltaScreen / currentZoom.coerceAtLeast(1e-5f)
                    cursorTime += deltaTimeline
                    cursorTime = cursorTime.coerceIn(minSelection, maxSelection)
                    touchStartX = event.x
                    invalidate()
                    return true
                }

                val currentTimelineX = screenToTimeline(event.x)

                if (!isCursorMode) {
                    if (isDraggingLeft) {
                        val newLeft = currentTimelineX.coerceIn(minPosition, maxPosition - minSelectionSize)
                        minSelection = newLeft.coerceAtMost(maxSelection - minSelectionSize)

                    } else if (isDraggingRight) {
                        val newRight = currentTimelineX.coerceIn(minPosition + minSelectionSize, maxPosition)
                        maxSelection = newRight.coerceAtLeast(minSelection + minSelectionSize)

                    } else if (!isAnimatingFitSelection && isPanning) {
                        val deltaScreen = event.x - touchStartX
                        val deltaTimeline = deltaScreen / currentZoom.coerceAtLeast(1e-5f)

                        panOffsetX -= deltaTimeline

                        clampPanOffset()

                        touchStartX = event.x
                        invalidate()
                    }
                }

                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingCursor) {
                    isDraggingCursor = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }

                isDraggingLeft = false
                isDraggingRight = false
                isPanning = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(event)
    }

    fun fitSelectionToView() {
        if (minPosition >= maxPosition || minSelection >= maxSelection) {
            isAnimatingFitSelection = false
            return
        }

        val clampedMinSelection = minSelection.coerceIn(minPosition, maxPosition)
        val clampedMaxSelection = maxSelection.coerceIn(clampedMinSelection, maxPosition)

        val selectionWidth = clampedMaxSelection - clampedMinSelection
        val selectionCenter = (clampedMinSelection + clampedMaxSelection) / 2f

        currentZoomAnimator?.cancel()
        currentPanAnimator?.cancel()
        currentZoomAnimator = null
        currentPanAnimator = null

        isAnimatingFitSelection = true

        val startZoom = currentZoom
        val startPan = panOffsetX

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()

            addUpdateListener { animation ->
                val thumbHalf = getActualThumbWidth() / 2f
                val visibleRangeNeeded = selectionWidth + 2f * thumbHalf
                val minVisibleRange = visibleRangeNeeded.coerceAtLeast(1e-5f)

                val targetZoom = (width / minVisibleRange).coerceIn(minZoom, maxZoom)
                val visibleRangeAtTarget = width / targetZoom.coerceAtLeast(1e-5f)

                val clampedVisibleRange = visibleRangeAtTarget.coerceAtMost(maxPosition - minPosition).coerceAtLeast(1e-5f)

                val idealPan = selectionCenter - clampedVisibleRange / 2f

                val minPan = minPosition
                val maxPan = maxPosition - clampedVisibleRange
                val finalPan = idealPan.coerceIn(minPan, maxPan)

                val t = (animation.animatedValue as Float)
                val zoomT = startZoom + (targetZoom - startZoom) * t
                val panT = startPan + (finalPan - startPan) * t

                val visibleRangeT = width / zoomT.coerceAtLeast(1e-5f)
                val clampedVisibleRangeT = visibleRangeT.coerceAtMost(maxPosition - minPosition).coerceAtLeast(1e-5f)

                val panMinT = minPosition
                val panMaxT = maxPosition - clampedVisibleRangeT

                val clampedPanT = panT.coerceIn(panMinT, panMaxT)

                currentZoom = zoomT
                panOffsetX = clampedPanT
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val thumbHalf = getActualThumbWidth() / 2f
                    val visibleRangeNeeded = selectionWidth + 2f * thumbHalf
                    val minVisibleRange = visibleRangeNeeded.coerceAtLeast(1e-5f)

                    val targetZoom = (width / minVisibleRange).coerceIn(minZoom, maxZoom)
                    val visibleRangeAtTarget = width / targetZoom.coerceAtLeast(1e-5f)

                    val clampedVisibleRange = visibleRangeAtTarget.coerceAtMost(maxPosition - minPosition).coerceAtLeast(1e-5f)

                    val idealPan = selectionCenter - clampedVisibleRange / 2f

                    val minPan = minPosition
                    val maxPan = maxPosition - clampedVisibleRange
                    val finalPan = idealPan.coerceIn(minPan, maxPan)

                    currentZoom = targetZoom
                    panOffsetX = finalPan
                    minSelection = clampedMinSelection
                    maxSelection = clampedMaxSelection
                    touchStartX = width / 2f
                    isAnimatingFitSelection = false
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    isAnimatingFitSelection = false
                }
            })
            start()
        }
    }

    fun setTimelineRange(
        minPos: Float,
        maxPos: Float
    ) {
        minPosition = minPos
        maxPosition = maxPos

        initializeTrack()
        clampPanOffset()

        touchStartX = timelineToScreen(panOffsetX + width / (2f * currentZoom.coerceAtLeast(1e-5f)))

        invalidate()
    }
}