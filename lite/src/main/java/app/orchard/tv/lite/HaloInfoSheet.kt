package app.orchard.tv.lite

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Optional support and attribution, accessible with the remote on Android 4.x. */
class HaloInfoSheet(context: Context, close: () -> Unit, support: Boolean) : Sheet(context, close) {
    init {
        setBackgroundColor(0xFF101827.toInt())
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.pxi(26f), Ui.pxi(18f), Ui.pxi(26f), Ui.pxi(18f))
        }
        addView(content, FrameLayout.LayoutParams(Ui.pxi(840f), LayoutParams.MATCH_PARENT, Gravity.CENTER))
        fun text(id: Int, size: Float, weight: Int = 500, color: Int = Ui.WHITE) =
            context.label(context.getString(id), size, weight, color)
        fun addText(target: LinearLayout, id: Int, size: Float, top: Float = 0f, weight: Int = 500, color: Int = Ui.WHITE) {
            val label = text(id, size, weight, color).apply { setSingleLine(false); ellipsize = null }
            target.addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.pxi(top) })
        }
        if (support) {
            val contentRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val message = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addText(message, R.string.donate_title, 28f, weight = 600)
            addText(message, R.string.donate_subtitle, 18f, top = 12f, weight = 600)
            addText(message, R.string.donate_description, 15f, top = 18f)
            addText(message, R.string.donate_hint, 13f, top = 14f, color = Ui.SECONDARY)
            contentRow.addView(message, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.pxi(24f) })
            fun code(drawable: Int, label: Int) {
                val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val image = ImageView(context).apply {
                    // Keep the original QR content while limiting bitmap memory on older TVs.
                    val options = BitmapFactory.Options().apply { inSampleSize = if (drawable == R.drawable.donate_alipay) 2 else 1 }
                    setImageBitmap(BitmapFactory.decodeResource(resources, drawable, options))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(android.graphics.Color.WHITE)
                    contentDescription = context.getString(label)
                }
                stack.addView(image, LinearLayout.LayoutParams(Ui.pxi(160f), Ui.pxi(160f)))
                stack.addView(text(label, 15f, 600).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.pxi(8f) })
                contentRow.addView(stack, LinearLayout.LayoutParams(Ui.pxi(170f), LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.pxi(12f) })
            }
            code(R.drawable.donate_wechat, R.string.donate_wechat)
            code(R.drawable.donate_alipay, R.string.donate_alipay)
            content.addView(contentRow)
        } else {
            addText(content, R.string.halo_notice_title, 28f, weight = 600)
            addText(content, R.string.halo_notice_body, 17f, top = 24f)
        }
        val closeChip = Chip(context, context.getString(if (support) R.string.donate_close else R.string.halo_notice_close), { false })
            .apply { action = close }
        content.addView(closeChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.pxi(26f) })
        addText(content, if (support) R.string.donate_back else R.string.halo_notice_back, 12f, top = 10f, color = Ui.SECONDARY)
        row(closeChip)
        focusFirst()
    }
}
