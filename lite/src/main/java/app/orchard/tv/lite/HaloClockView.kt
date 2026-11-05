package app.orchard.tv.lite

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.View
import java.util.Calendar
import java.util.Locale

/** Halo's home header, drawn once per minute in a single lightweight View. */
class HaloClockView(context: Context) : View(context) {
    var large = true
    var sizeMode = 0
        set(value) { field = value.coerceIn(0, 2); requestLayout(); invalidate() }
    var alignEnd = false
        set(value) { field = value; invalidate() }
    var alignCenter = false
        set(value) { field = value; invalidate() }
    var compactStatus = false
        set(value) { field = value; requestLayout(); invalidate() }
    var weather: WeatherLine? = null
        set(value) { field = value; invalidate() }

    private var time = ""
    private var date = ""
    private var greeting = ""
    private var lunar: String? = null
    private var capacity = ""
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val outfit: Typeface? by lazy { runCatching { Typeface.createFromAsset(context.assets, "outfit.ttf") }.getOrNull() }
    private val icon = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scale get() = when (sizeMode) { 1 -> 1.2f; 2 -> 1.4f; else -> 1f }
    private val ink get() = when (LiteSettings.desktopTextColor) {
        1 -> 0xFF252A30.toInt()
        2 -> 0xFF000000.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun refresh() {
        val now = Calendar.getInstance()
        time = String.format(Locale.ROOT, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        date = dateText(now.timeInMillis)
        lunar = Lunar.text(now)
        greeting = context.getString(when (now.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_good_morning
            in 12..16 -> R.string.home_good_afternoon
            in 17..21 -> R.string.home_good_evening
            else -> R.string.home_good_night
        })
        capacity = if (LiteSettings.showDeviceCapacity) runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            val fs = StatFs(Environment.getDataDirectory().path)
            val storage = if (Build.VERSION.SDK_INT >= 18) fs.availableBytes else fs.availableBlocks.toLong() * fs.blockSize
            context.getString(R.string.home_device_capacity,
                Formatter.formatShortFileSize(context, memory.availMem),
                Formatter.formatShortFileSize(context, storage))
        }.getOrDefault("") else ""
        invalidate()
    }

    override fun hasOverlappingRendering() = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(minOf((Ui.screenW - Ui.pxi(112f)).coerceAtLeast(1), Ui.pxi((if (compactStatus) 520f else 450f) * scale)),
            Ui.pxi(if (compactStatus) 40f * scale else ((if (alignEnd || alignCenter) 150f else 126f) + if (LiteSettings.showLunarDate && lunar != null) 22f else 0f) * scale))
    }

    private fun style(size: Float, weight: Int, alpha: Int = 255, light: Boolean = false) {
        text.textSize = Ui.px(size * if (compactStatus) scale else scale)
        text.typeface = if (sizeMode == 2) Typeface.create(outfit ?: Typeface.DEFAULT, Typeface.BOLD) else outfit ?: if (light) Typeface.create("sans-serif-light", Typeface.NORMAL) else Ui.font(weight)
        text.color = (ink and 0x00FFFFFF) or (alpha shl 24)
        if (LiteSettings.desktopTextColor == 0) text.setShadowLayer(Ui.px(6f), 0f, Ui.px(1f), 0x69000000) else text.clearShadowLayer()
    }

    private fun start(width: Float): Float = when {
        alignEnd -> this.width - width - Ui.px(2f)
        alignCenter -> (this.width - width) / 2f
        else -> 0f
    }

    private fun label(canvas: Canvas, value: String, size: Float, weight: Int, y: Float,
                      alpha: Int = 255, light: Boolean = false, x: Float? = null): Float {
        style(size, weight, alpha, light)
        val width = text.measureText(value)
        canvas.drawText(value, x ?: start(width), Ui.px(y * scale), text)
        return width
    }

    override fun onDraw(canvas: Canvas) {
        if (time.isEmpty()) refresh()
        if (compactStatus) {
            drawCompact(canvas)
        } else if (alignEnd || alignCenter) {
            drawStacked(canvas)
        } else {
            drawLeft(canvas)
        }
    }

    private fun drawLeft(c: Canvas) {
        label(c, greeting, 13f, 500, 12f, 200)
        style(54f, 300, light = true)
        val timeWidth = text.measureText(time)
        val timeBaseline = Ui.px(68f * scale)
        c.drawText(time, 0f, timeBaseline, text)
        val lineX = timeWidth + Ui.px(14f * scale)
        rule.color = (ink and 0x00FFFFFF) or 0x88000000.toInt()
        rule.strokeWidth = Ui.px(1f)
        c.drawLine(lineX, timeBaseline - Ui.px(22f * scale), lineX, timeBaseline, rule)
        label(c, date, 16f, 400, 68f, 220, x = lineX + Ui.px(14f * scale))
        val lunarExtra = if (LiteSettings.showLunarDate && lunar != null) 22f else 0f
        if (lunarExtra > 0f) label(c, context.getString(R.string.lunar_date_value, lunar), 14f, 400, 89f, 225)
        drawWeather(c, 95f + lunarExtra)
        if (capacity.isNotEmpty()) label(c, capacity, 12f, 500, 116f + lunarExtra, 210)
    }

    private fun drawStacked(c: Canvas) {
        label(c, greeting, 13f, 500, 12f, 200)
        label(c, time, 54f, 300, 70f, light = true)
        label(c, date, 16f, 400, 94f, 220)
        val lunarExtra = if (LiteSettings.showLunarDate && lunar != null) 22f else 0f
        if (lunarExtra > 0f) label(c, context.getString(R.string.lunar_date_value, lunar), 14f, 400, 116f, 225)
        drawWeather(c, 120f + lunarExtra)
        if (capacity.isNotEmpty()) label(c, capacity, 12f, 500, 142f + lunarExtra, 210)
    }

    private fun drawWeather(c: Canvas, baseline: Float) {
        if (!LiteSettings.showWeather) return
        val w = weather ?: return
        val value = listOfNotNull(w.place?.takeIf(String::isNotBlank), "${w.celsius}°").joinToString("  ")
        style(17f, 400, 235)
        val glyph = Ui.pxi(24f * scale)
        val gap = Ui.px(6f * scale)
        val width = glyph + gap + text.measureText(value)
        val x = start(width)
        c.drawBitmap(Glyphs.image(w.glyph, glyph, ink), x,
            Ui.px((baseline - 18f) * scale), icon)
        c.drawText(value, x + glyph + gap, Ui.px(baseline * scale), text)
    }

    private fun drawCompact(c: Canvas) {
        style(23f, 500, 235)
        var x = 0f
        val y = Ui.px(29f * scale)
        c.drawText(time, x, y, text)
        x += text.measureText(time) + Ui.px(10f * scale)
        style(14f, 400, 210)
        c.drawText(date, x, y, text)
        x += text.measureText(date) + Ui.px(14f * scale)
        if (LiteSettings.showLunarDate) lunar?.let {
            val value = context.getString(R.string.lunar_date_value, it)
            c.drawText(value, x, y, text)
            x += text.measureText(value) + Ui.px(14f * scale)
        }
        if (LiteSettings.showWeather) weather?.let { w ->
            val glyph = Ui.pxi(18f * scale)
            c.drawBitmap(Glyphs.image(w.glyph, glyph, ink), x, Ui.px(11f * scale), icon)
            x += glyph + Ui.px(5f * scale)
            c.drawText(listOfNotNull(w.place, "${w.celsius}°").joinToString(" "), x, y, text)
        }
    }
}
