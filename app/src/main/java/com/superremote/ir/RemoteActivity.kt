package com.superremote.ir

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.superremote.ir.Ui.dp

class RemoteActivity : Activity() {
    private lateinit var device: Device
    private lateinit var ir: IrTransmitter
    private var editMode = false
    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = DeviceStore.find(this, intent.getStringExtra("id"))
        if (d == null) { finish(); return }
        device = d
        ir = IrTransmitter.get(this)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        container = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        setContentView(container)
    }

    override fun onResume() {
        super.onResume()
        if (::device.isInitialized) build()
    }

    private fun build() {
        title = if (editMode) "✏ Editing ${device.name}" else device.name
        container.removeAllViews()
        val root = Ui.vertical(this, 10)

        if (editMode) {
            root.addView(banner("EDIT MODE: tap a button to set / change its code. Use ⋮ → Done editing when finished.", Ui.ACCENT))
        } else if (device.unverified) {
            root.addView(banner("Codes for this model are a best guess. If a button doesn't work: ⋮ → Find codes (Code Finder), or ⋮ → Edit mode. " +
                    "Tap here to hide this message once everything works.", Ui.WARN).apply {
                setOnClickListener { device.unverified = false; DeviceStore.save(context); build() }
            })
        }
        if (!ir.hasEmitter) root.addView(banner("This phone has no IR blaster – codes cannot be sent.", Ui.WARN))

        for (row in Keys.layout(device.type)) {
            if (row == listOf("NAV")) { root.addView(navPad()); continue }
            val line = Ui.horizontal(this)
            for (id in row) {
                if (id == null) line.addView(View(this), Ui.weighted(this))
                else line.addView(keyButton(Keys[id]), Ui.weighted(this, if (row.size >= 5) 52 else 58))
            }
            root.addView(line, Ui.matchWrap(this))
        }

        // custom buttons
        if (device.custom.isNotEmpty() || editMode) {
            root.addView(Ui.text(this, "More buttons", 14f, Ui.DIM).apply { setPadding(dp(6), dp(14), 0, dp(4)) })
            device.custom.chunked(3).forEach { chunk ->
                val line = Ui.horizontal(this)
                chunk.forEach { line.addView(customButton(it), Ui.weighted(this)) }
                repeat(3 - chunk.size) { line.addView(View(this), Ui.weighted(this)) }
                root.addView(line, Ui.matchWrap(this))
            }
            if (editMode) root.addView(Ui.button(this, "＋ Add a custom button", 0xFF0277BD.toInt()) { addCustom() },
                Ui.matchWrap(this, 4))
        }

        container.addView(ScrollView(this).apply { addView(root) })
    }

    private fun banner(msg: String, color: Int) = Ui.text(this, msg, 13f, color).apply {
        background = Ui.rounded(context, Ui.CARD, 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = Ui.matchWrap(context, 4)
    }

    private fun style(b: Button, color: Int, hasCode: Boolean) {
        val base = if (color != 0) color else Ui.BTN
        b.background = if (hasCode || editMode) Ui.background(this, base, 16, if (editMode && !hasCode) Ui.WARN else 0)
                       else Ui.background(this, Ui.BTN_EMPTY, 16)
        b.setTextColor(if (hasCode) Ui.TEXT else Ui.DIM)
    }

    private fun keyButton(k: Key): Button {
        val b = Button(this).apply {
            text = k.label; isAllCaps = false; textSize = if (k.label.length > 8) 12f else 15f
            stateListAnimator = null
        }
        Ui.compact(b)
        style(b, k.color, device.codes[k.id] != null)
        if (editMode) {
            b.setOnClickListener { editKey(k) }
        } else {
            Ui.attachPress(b, k.repeat) {
                val c = device.codes[k.id]
                if (c == null) Ui.toast(this, "No code for “${k.label.ifEmpty { k.id }}” yet. Use ⋮ → Edit mode.")
                else ir.send(c) { e -> Ui.toast(this, e) }
            }
        }
        return b
    }

    private fun customButton(cb: CustomButton): Button {
        val b = Button(this).apply { text = cb.label; isAllCaps = false; textSize = 13f; stateListAnimator = null }
        Ui.compact(b)
        style(b, 0xFF37474F.toInt(), cb.code != null)
        if (editMode) {
            b.setOnClickListener {
                AlertDialog.Builder(this).setTitle(cb.label)
                    .setItems(arrayOf("Set code", "Rename", "Delete")) { _, w ->
                        when (w) {
                            0 -> CodeEditor.show(this, cb.label, cb.code) { cb.code = it; save() }
                            1 -> Ui.askText(this, "Rename", cb.label) { cb.label = it; save() }
                            2 -> { device.custom.remove(cb); save() }
                        }
                    }.show()
            }
        } else {
            Ui.attachPress(b, false) {
                val c = cb.code
                if (c == null) Ui.toast(this, "No code yet") else ir.send(c) { e -> Ui.toast(this, e) }
            }
        }
        return b
    }

    private fun navPad(): LinearLayout {
        val pad = Ui.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val rows = listOf(listOf(null, "up", null), listOf("left", "ok", "right"), listOf(null, "down", null))
        for (r in rows) {
            val line = Ui.horizontal(this).apply { gravity = Gravity.CENTER }
            for (id in r) {
                val lp = LinearLayout.LayoutParams(dp(78), dp(64)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                line.addView(if (id == null) View(this) else keyButton(Keys[id]), lp)
            }
            pad.addView(line)
        }
        pad.layoutParams = Ui.matchWrap(this, 6)
        return pad
    }

    private fun editKey(k: Key) {
        CodeEditor.show(this, "Code for “${k.label.ifEmpty { k.id }}”", device.codes[k.id]) { code ->
            if (code == null) device.codes.remove(k.id) else device.codes[k.id] = code
            save()
        }
    }

    private fun addCustom() {
        Ui.askText(this, "Button name", "", "e.g. Night mode") { name ->
            val cb = CustomButton(name, null)
            device.custom += cb
            save()
            CodeEditor.show(this, name, null) { cb.code = it; save() }
        }
    }

    private fun save() { DeviceStore.save(this); build() }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, if (editMode) "Done editing" else "Edit mode (change codes)")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 2, 0, "Find codes (Code Finder)")
        menu.add(0, 3, 0, "Add custom button")
        menu.add(0, 4, 0, "Change address of all buttons")
        menu.add(0, 5, 0, "Rename")
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(1)?.title = if (editMode) "Done editing" else "Edit mode (change codes)"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            1 -> { editMode = !editMode; invalidateOptionsMenu(); build() }
            2 -> startActivity(Intent(this, CodeFinderActivity::class.java).putExtra("id", device.id))
            3 -> addCustom()
            4 -> changeAllAddresses()
            5 -> Ui.askText(this, "Rename", device.name) { device.name = it; save() }
        }
        return true
    }

    /** Useful when a whole remote uses another address (common with soundbar clones). */
    private fun changeAllAddresses() {
        val first = device.codes.values.firstOrNull { it.protocol.usesAddress }
        if (first == null) { Ui.toast(this, "No numeric codes on this remote yet"); return }
        Ui.askText(this, "New address for all ${first.protocol.name} buttons", IrCode.hex(first.address, first.protocol.maxAddress)) { s ->
            val a = Ui.parseNumber(s)
            if (a == null) { Ui.toast(this, "Not a number"); return@askText }
            for ((k, v) in device.codes.toMap()) if (v.protocol == first.protocol) device.codes[k] = v.copy(address = a)
            device.custom.forEach { cb -> cb.code?.let { if (it.protocol == first.protocol) cb.code = it.copy(address = a) } }
            save()
            Ui.toast(this, "Address changed")
        }
    }
}
