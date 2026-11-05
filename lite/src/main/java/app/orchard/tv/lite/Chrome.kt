package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.View
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The time as the full app shows it: 24-hour unless the owner chose 12-hour (TCL 65S515D, #15). */
fun timeText(context: Context, millis: Long = System.currentTimeMillis()): String {
    val twelve = runCatching { android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24) }.getOrNull() == "12"
    return SimpleDateFormat(if (twelve) "h:mm" else "H:mm", Locale.getDefault()).format(Date(millis))
}

/** The date as the full app's dateText: the locale's month, day and weekday, the weekday spaced off. */
fun dateText(millis: Long = System.currentTimeMillis()): String {
    val locale = Locale.getDefault()
    val pattern = if (Build.VERSION.SDK_INT >= 18) {
        DateFormat.getBestDateTimePattern(locale, "MMMMdEEEE")
            .replace(Regex("(?<=[^\\s,，'])EEEE"), " EEEE")
            .replace(Regex("EEEE(?=[^\\s,，'])"), "EEEE ")
    } else when (locale.language) {
        "zh" -> "M月d日 EEEE"
        "en" -> "EEEE, MMMM d"
        else -> "EEEE, d MMMM"
    }
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/** The weather beside the clock, once known: its glyph, the temperature, and the rest dimmed. */
class WeatherLine(val glyph: Glyph, val celsius: Int, val place: String?, val detail: String)

/**
 * The full app's clocks (HomeChrome): the small one, the time and the weather in one quiet line
 * level with the status pill; or the large one, as on the lock screen, the date (with the Chinese
 * calendar's in Chinese) over a big time and the weather. Every line is placed by its capitals,
 * as the full app's capBox does, so the gaps are the ones the eye sees.
 */
class ClockView(context: Context) : View(context) {
    var large = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    var weather: WeatherLine? = null
        set(v) { field = v; requestLayout(); invalidate() }
    /** Lines flush right, for the clock on the right (Personalize). */
    var alignEnd = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    private var time = ""
    private var date = ""
    private var capacityText: String? = null

    fun refresh() {
        val now = System.currentTimeMillis()
        time = timeText(context, now)
        capacityText = if (LiteSettings.showDeviceCapacity) runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mem = android.app.ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            val fs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            @Suppress("DEPRECATION") val free = if (Build.VERSION.SDK_INT >= 18) fs.availableBytes else fs.availableBlocks.toLong() * fs.blockSize.toLong()
            context.getString(R.string.home_device_capacity, android.text.format.Formatter.formatShortFileSize(context, mem.availMem), android.text.format.Formatter.formatShortFileSize(context, free))
        }.getOrNull() else null
        val d = dateText(now)
        date = if (Locale.getDefault().language == "zh") Lunar.text(Calendar.getInstance())?.let { "$d  $it" } ?: d else d
        if (alignEnd && !large) requestLayout()
        invalidate()
    }

    /** The lunar part of [date], drawn dimmer. */
    private val lunarStart get() = date.indexOf("  ").takeIf { it > 0 }

    override fun hasOverlappingRendering() = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The small clock beside the pill takes only its own width.
        val w = if (alignEnd && !large) smallWidth().toInt() + Ui.pxi(4f) else Ui.pxi(560f)
        setMeasuredDimension(w, Ui.pxi(if (large) 200f else 34f))
    }

    private fun smallWeatherText(w: WeatherLine) = listOfNotNull(w.place?.takeIf { it.length <= 12 }, "${w.celsius}°").joinToString(" ")

    private fun smallWidth(): Float {
        var x = Ui.width(time, smallPaint, 0.05f)
        val w = weather ?: return x
        x += Ui.px(16f) + Ui.pxi(18f) + Ui.px(6f) + smallWeather.measureText(smallWeatherText(w))
        return x
    }

    // Large clock (ShelfClock): Type.ClockDate, Type.Clock, Type.ClockDetail; shadow 0.28 black, 2 down, 18 px blur at 1080p.
    private val datePaint = Ui.text(19f, 600, display = true).shadow(0x47000000, 9f, 1f)
    private val dateDim = Ui.text(19f, 500, 0xB8FFFFFF.toInt(), display = true).shadow(0x47000000, 9f, 1f)
    private val bigPaint = Ui.text(52f, 400, display = true).shadow(0x47000000, 9f, 1f)
    private val detailPaint = Ui.text(16f, 600).shadow(0x47000000, 9f, 1f)
    private val detailDim = Ui.text(16f, 500, 0xB8FFFFFF.toInt()).shadow(0x47000000, 9f, 1f)
    // Small clock (CornerClock): Type.SmallClock at Title size, 92% white; Type.Headline beside it.
    private val smallPaint = Ui.text(19f, 600, 0xEBFFFFFF.toInt()).shadow(0x80000000.toInt(), 4f, 0f)
    private val smallWeather = Ui.text(15f, 600, 0xEBFFFFFF.toInt()).shadow(0x80000000.toInt(), 4f, 0f)
    private val glyphPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun Paint.shadow(color: Int, blur: Float, dy: Float) = apply { setShadowLayer(Ui.px(blur), 0f, Ui.px(dy), color) }

    override fun onDraw(canvas: Canvas) {
        if (large) drawLarge(canvas) else drawSmall(canvas)
    }

    private fun drawLarge(c: Canvas) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = context.getString(when (hour) {
            in 5..11 -> R.string.home_good_morning
            in 12..17 -> R.string.home_good_afternoon
            in 18..21 -> R.string.home_good_evening
            else -> R.string.home_good_night
        })
        val gPaint = Ui.text(12f, 500, 0xB8FFFFFF.toInt())
        val dPaint = Ui.text(15f, 500, 0xD9FFFFFF.toInt())
        val dateLabel = dateText()
        val timeWidth = Ui.width(time, bigPaint, -0.04f)
        val dateWidth = dPaint.measureText(dateLabel)
        val rowWidth = timeWidth + Ui.px(20f) + dateWidth
        val x0 = if (alignEnd) (width - rowWidth).coerceAtLeast(0f) else 0f
        Ui.draw(c, greeting, x0, Ui.px(14f), gPaint)
        Ui.draw(c, time, x0, Ui.px(65f), bigPaint, -0.04f)
        val lineX = x0 + timeWidth + Ui.px(9f)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt(); strokeWidth = Ui.px(0.6f) }
        c.drawLine(lineX, Ui.px(43f), lineX, Ui.px(65f), linePaint)
        Ui.draw(c, dateLabel, lineX + Ui.px(10f), Ui.px(65f), dPaint)
        weather?.let { w ->
            val g = Ui.pxi(18f)
            c.drawBitmap(Glyphs.image(w.glyph, g, Ui.WHITE), x0, Ui.px(77f), glyphPaint)
            val text = listOfNotNull(w.place, "${w.celsius}°").joinToString(" ")
            Ui.draw(c, text, x0 + g + Ui.px(6f), Ui.px(92f), detailPaint)
        }
        if (LiteSettings.showDeviceCapacity) capacityText?.let {
            Ui.draw(c, it, x0, Ui.px(112f), Ui.text(11f, 500, 0xC2FFFFFF.toInt()))
        }
    }

    private fun drawSmall(c: Canvas) {
        val h = height.toFloat()
        fun baseline(p: Paint) = h / 2 - (p.descent() + p.ascent()) / 2
        Ui.draw(c, time, 0f, baseline(smallPaint), smallPaint, 0.05f)
        val w = weather ?: return
        var x = Ui.width(time, smallPaint, 0.05f) + Ui.px(16f)
        val g = Ui.pxi(18f)
        c.drawBitmap(Glyphs.image(w.glyph, g, 0xEBFFFFFF.toInt()), x, (h - g) / 2f, glyphPaint)
        x += g + Ui.px(6f)
        Ui.draw(c, smallWeatherText(w), x, baseline(smallWeather), smallWeather)
    }
}

/**
 * The full app's StatusPill: top right, the network and Control Center's glyph, and the time once
 * Home has gone down into the grid. Up from the top row lands here; it opens Control Center.
 */
class StatusPill(context: Context) : View(context) {
    var selectedSegment = 2
        set(v) { field = v.coerceIn(0, 2); invalidate() }
    var focusedLook = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
            val s = 1f
            if (isHardwareAccelerated) animate().scaleX(s).scaleY(s).setDuration(160).start() else { scaleX = s; scaleY = s }
        }
    var showTime = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    /** The network: Glyph.Wifi (with [level]), Glyph.Ethernet or Glyph.Offline. */
    var network = Glyph.Wifi
        set(v) { if (field != v) { field = v; invalidate() } }
    var level = 1f
        set(v) { if (field != v) { field = v; invalidate() } }
    /** A VPN is up: the full app's VpnBadge after the network glyph (issue #809). */
    var vpn = false
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }

    private val text = Ui.text(15f, 600)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val g = Ui.pxi(18f)
    private val badge = VpnBadge(context, 10f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.pxi(40f), Ui.pxi(40f))
    }

    override fun onDraw(canvas: Canvas) {
        val diameter = Ui.px(34f)
        if (focusedLook) {
            bg.color = Ui.FOCUS
            rect.set((width - diameter) / 2f, (height - diameter) / 2f, (width + diameter) / 2f, (height + diameter) / 2f)
            canvas.drawRoundRect(rect, diameter / 2f, diameter / 2f, bg)
        }
        val glyph = Ui.pxi(19f)
        canvas.drawBitmap(Glyphs.image(Glyph.Gear, glyph, if (focusedLook) Ui.ON_FOCUS else Ui.WHITE),
            (width - glyph) / 2f, (height - glyph) / 2f, bitmapPaint)
    }

}

/** The full app's VpnBadge: "VPN" in a thin rounded frame, [size] its text size. */
class VpnBadge(context: Context, size: Float) {
    private val label = context.getString(R.string.net_vpn)
    private val text = Ui.text(size, 700)
    private val frame = Shapes.Tint()
    val width: Float = Ui.width(label, text, 0.04f) + Ui.px(8f)
    val height: Float = size * 1.2f * Ui.px(1f) + Ui.px(2f)

    /** Drawn from [x], centred on [centerY]; returns its width. */
    fun draw(canvas: Canvas, x: Float, centerY: Float, color: Int): Float {
        frame.draw(canvas, Shapes.band(Math.round(width), Math.round(height), Ui.px(4f), Ui.px(1.2f)), x, centerY - height / 2, color)
        text.color = color
        Ui.draw(canvas, label, x + Ui.px(4f), centerY - (text.descent() + text.ascent()) / 2, text, 0.04f)
        return width
    }
}

/** A [VpnBadge] as a plain view. */
class VpnBadgeView(context: Context, size: Float, private val color: Int) : View(context) {
    private val badge = VpnBadge(context, size)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(badge.width.toInt() + 1, badge.height.toInt() + 1)
    override fun onDraw(canvas: Canvas) { badge.draw(canvas, 0f, height / 2f, color) }
}
