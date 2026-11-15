package com.superremote.ir

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

object Ui {
    const val BG = 0xFF111822.toInt()
    const val CARD = 0xFF1E2A3A.toInt()
    const val BTN = 0xFF2B3A4F.toInt()
    const val BTN_EMPTY = 0xFF1A2330.toInt()
    const val TEXT = 0xFFECEFF4.toInt()
    const val DIM = 0xFF8A97A8.toInt()
    const val ACCENT = 0xFF4FC3F7.toInt()
    const val WARN = 0xFFFFB74D.toInt()

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun rounded(ctx: Context, color: Int, radiusDp: Int = 14, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = ctx.dp(radiusDp).toFloat()
            if (strokeColor != 0) setStroke(ctx.dp(1), strokeColor)
        }

    fun background(ctx: Context, color: Int, radiusDp: Int = 14, strokeColor: Int = 0) =
        RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), rounded(ctx, color, radiusDp, strokeColor), null)

    fun text(ctx: Context, s: String, sizeSp: Float = 15f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s; textSize = sizeSp; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    fun button(ctx: Context, label: String, color: Int = BTN, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(TEXT)
            textSize = 15f
            background = background(ctx, color)
            stateListAnimator = null
            compact(this)
            setOnClickListener { onClick() }
        }

    /** Removes the default 88dp minimum width so many buttons fit on one row. */
    fun compact(b: Button) {
        b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
        b.setPadding(b.context.dp(4), 0, b.context.dp(4), 0)
    }

    fun vertical(ctx: Context, padDp: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = ctx.dp(padDp); setPadding(p, p, p, p)
    }

    fun horizontal(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun weighted(ctx: Context, heightDp: Int = 56, marginDp: Int = 4) =
        LinearLayout.LayoutParams(0, ctx.dp(heightDp), 1f).apply {
            val m = ctx.dp(marginDp); setMargins(m, m, m, m)
        }

    fun matchWrap(ctx: Context, marginDp: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            val m = ctx.dp(marginDp); setMargins(m, m, m, m)
        }

    fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    /** Fires [action] on tap and, if [repeat], again every 130 ms while held. */
    @SuppressLint("ClickableViewAccessibility")
    fun attachPress(view: View, repeat: Boolean, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var runnable: Runnable? = null
        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    action()
                    if (repeat) {
                        val r = object : Runnable {
                            override fun run() { action(); handler.postDelayed(this, 130) }
                        }
                        runnable = r
                        handler.postDelayed(r, 450)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    runnable?.let { handler.removeCallbacks(it) }
                    runnable = null
                    if (ev.action == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> true
            }
        }
    }

    fun input(ctx: Context, hint: String, value: String = "", numeric: Boolean = false) = EditText(ctx).apply {
        this.hint = hint
        setText(value)
        setTextColor(TEXT)
        setHintTextColor(DIM)
        inputType = if (numeric) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    fun spinner(ctx: Context, items: List<String>, selected: Int, onSelect: (Int) -> Unit) = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onSelect(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    fun askText(ctx: Context, title: String, initial: String, hint: String = "", onOk: (String) -> Unit) {
        val et = input(ctx, hint, initial)
        val box = vertical(ctx, 20).apply { addView(et) }
        AlertDialog.Builder(ctx).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ -> val t = et.text.toString().trim(); if (t.isNotEmpty()) onOk(t) }
            .setNegativeButton("Cancel", null).show()
    }

    fun confirm(ctx: Context, title: String, msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("No", null).show()
    }

    /** Parses "0x1F", "1F h", or decimal "31". */
    fun parseNumber(s: String): Int? {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return null
        return try {
            when {
                t.startsWith("0x") -> t.substring(2).toInt(16)
                t.endsWith("h") -> t.dropLast(1).trim().toInt(16)
                t.any { it in 'a'..'f' } -> t.toInt(16)
                else -> t.toInt()
            }
        } catch (e: NumberFormatException) { null }
    }

}

/** Dialog to view / type / test an IR code for one button. */
object CodeEditor {
    fun show(ctx: Context, title: String, current: IrCode?, onSave: (IrCode?) -> Unit) {
        val ir = IrTransmitter.get(ctx)
        val protocols = Protocol.values().toList()
        var proto = current?.protocol ?: Protocol.NEC

        val box = Ui.vertical(ctx, 20)
        val addr = Ui.input(ctx, "Address, e.g. 0x04", current?.let { IrCode.hex(it.address, it.protocol.maxAddress) } ?: "", true)
        val cmd = Ui.input(ctx, "Command, e.g. 0x08", current?.let { IrCode.hex(it.command, it.protocol.maxCommand) } ?: "", true)
        val raw = Ui.input(ctx, "Paste code here", current?.raw ?: "", true).apply {
            minLines = 3; gravity = Gravity.TOP
        }
        val help = Ui.text(ctx, "", 13f, Ui.DIM)

        fun refresh() {
            val isRaw = !proto.usesAddress
            addr.visibility = if (isRaw) View.GONE else View.VISIBLE
            cmd.visibility = if (isRaw) View.GONE else View.VISIBLE
            raw.visibility = if (isRaw) View.VISIBLE else View.GONE
            help.text = when (proto) {
                Protocol.PRONTO -> "Pronto hex, e.g. 0000 006D 0022 0002 0157 00AC …  (from remotecentral.com, irdb, etc.)"
                Protocol.RAW -> "Format  frequency: mark,space,mark,…  in µs\ne.g. 38000: 9000,4500,560,560,560,1690 …"
                else -> "Hex (0x..) or decimal. Max address ${IrCode.hex(proto.maxAddress, proto.maxAddress)}, max command ${IrCode.hex(proto.maxCommand, proto.maxCommand)}.\nTip: Flipper Zero / IRDB files list codes as protocol + address + command."
            }
        }

        box.addView(Ui.text(ctx, "Protocol", 13f, Ui.DIM))
        box.addView(Ui.spinner(ctx, protocols.map { it.label }, protocols.indexOf(proto)) { proto = protocols[it]; refresh() })
        box.addView(addr); box.addView(cmd); box.addView(raw); box.addView(help)
        refresh()

        fun build(): IrCode? {
            if (!proto.usesAddress) {
                val t = raw.text.toString().trim()
                if (t.isEmpty()) return null
                val c = IrCode(proto, raw = t)
                return try { IrEncoder.encode(c); c } catch (e: Exception) { Ui.toast(ctx, e.message ?: "Invalid code"); null }
            }
            val a = Ui.parseNumber(addr.text.toString())
            val c = Ui.parseNumber(cmd.text.toString())
            if (a == null || c == null) { Ui.toast(ctx, "Enter address and command"); return null }
            if (a > proto.maxAddress || c > proto.maxCommand || a < 0 || c < 0) { Ui.toast(ctx, "Number too large for ${proto.name}"); return null }
            return IrCode(proto, a, c)
        }

        val dlg = AlertDialog.Builder(ctx).setTitle(title).setView(android.widget.ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Save", null)
            .setNeutralButton("Test", null)
            .setNegativeButton(if (current != null) "Remove code" else "Cancel") { _, _ -> if (current != null) onSave(null) }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                build()?.let { onSave(it); dlg.dismiss() }
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                build()?.let { ir.send(it) { e -> Ui.toast(ctx, e) } }
            }
        }
        dlg.show()
    }
}
