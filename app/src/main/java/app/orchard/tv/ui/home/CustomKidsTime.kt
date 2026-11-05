package app.orchard.tv.ui.home

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import app.orchard.tv.R

internal fun customKidsTime(context: Context, label: String, current: Int, clock: Boolean, save: (Int) -> Unit) {
    if (clock) {
        val value = current.takeIf { it in 1..1439 } ?: 20 * 60
        TimePickerDialog(context, { _, hour, minute ->
            val result = hour * 60 + minute
            if (result > 0) save(result) else Toast.makeText(context, R.string.halo_kids_invalid_minutes, Toast.LENGTH_LONG).show()
        }, value / 60, value % 60, true).apply { setTitle(label); show() }
        return
    }
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText((if (current > 0) current else 30).toString())
        selectAll()
    }
    val dialog = AlertDialog.Builder(context).setTitle(label).setView(input)
        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value == null || value !in 1..1440) Toast.makeText(context, R.string.halo_kids_invalid_minutes, Toast.LENGTH_LONG).show()
            else { save(value); dialog.dismiss() }
        }
    }
    dialog.show()
}
