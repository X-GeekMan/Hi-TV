package app.orchard.tv.lite

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class FlipClockView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val digitTypeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    private val textTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private var current = "0000"
    private var previous = current
    private var initialized = false
    private var info = ""
    private var progress = 1f
    private var animator: ValueAnimator? = null

    fun setContents(time: String, newInfo: String) {
        if (!initialized) {
            current = time
            previous = time
            initialized = true
        } else if (time != current) {
            animator?.cancel()
            previous = current
            current = time
            progress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 620L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (info == newInfo) return
        info = newInfo
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Match the original ClockView's 83% width and centered vertical rhythm.
        val cardSize = minOf(w * 0.22f, h * 0.42f, w * 0.90f / 2.14f)
        val infoSize = (cardSize * 0.070f).coerceIn(18f, h * 0.038f)
        val infoGap = cardSize * 0.13f
        val infoHeight = infoSize * 1.30f
        val startY = (h - cardSize - infoGap - infoHeight) * 0.5f
        val clockWidth = w * 0.83f
        val smallGap = w * 0.008f
        val middleGap = w * 0.052f
        val digitWidth = (clockWidth - middleGap - 2f * smallGap) / 4f
        val digitCenterY = startY + cardSize * 0.485f
        val digitHalfHeight = cardSize * 1.17f * 0.45f
        val top = (digitCenterY - digitHalfHeight).coerceAtLeast(0f)
        val bottom = digitCenterY + digitHalfHeight
        var x = (w - clockWidth) * 0.5f
        for (index in 0..3) {
            drawDigit(canvas, RectF(x, top, x + digitWidth, bottom), previous[index], current[index])
            x += digitWidth + if (index == 1) middleGap else smallGap
        }

        paint.typeface = textTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(158, 158, 158)
        paint.textSize = infoSize
        while (paint.measureText(info) > w * 0.86f && paint.textSize > 16f) paint.textSize -= 1f
        val infoCenterY = startY + cardSize + infoGap + infoHeight * 0.5f
        val baseline = infoCenterY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) * 0.5f
        canvas.drawText(info, w * 0.5f, baseline, paint)
    }

    private fun drawDigit(canvas: Canvas, card: RectF, old: Char, next: Char) {
        val hingeY = card.centerY()
        val top = RectF(card.left, card.top, card.right, hingeY)
        val bottom = RectF(card.left, hingeY, card.right, card.bottom)
        if (old == next || progress >= 1f) {
            drawHalf(canvas, card, next, top)
            drawHalf(canvas, card, next, bottom)
        } else if (progress < 0.5f) {
            drawHalf(canvas, card, next, top)
            drawHalf(canvas, card, old, bottom)
            drawHalf(canvas, card, old, top, (1f - progress * 2f).coerceAtLeast(0.02f))
        } else {
            drawHalf(canvas, card, next, top)
            drawHalf(canvas, card, old, bottom)
            drawHalf(canvas, card, next, bottom, ((progress - 0.5f) * 2f).coerceAtLeast(0.02f))
        }
        paint.color = Color.rgb(9, 9, 9)
        val seam = maxOf(3f, card.height() * 0.010f)
        canvas.drawRect(card.left, hingeY - seam * 0.5f, card.right, hingeY + seam * 0.5f, paint)
    }

    private fun drawHalf(canvas: Canvas, card: RectF, digit: Char, clip: RectF, fold: Float = 1f) {
        canvas.save()
        canvas.clipRect(clip)
        if (fold != 1f) canvas.scale(1f, fold, card.centerX(), card.centerY())
        paint.color = Color.rgb(244, 244, 244)
        paint.typeface = digitTypeface
        paint.textAlign = Paint.Align.CENTER
        // Keep Clean Your TV's sans-serif-black digits, slightly smaller within the same flip area.
        paint.textSize = card.height() * 1.20f
        while (paint.measureText(digit.toString()) > card.width() * 0.98f) paint.textSize -= 2f
        val baseline = card.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) * 0.5f
        canvas.drawText(digit.toString(), card.centerX(), baseline, paint)
        canvas.restore()
    }
}
