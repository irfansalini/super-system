package com.superremote.ir

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.ScrollView
import com.superremote.ir.Ui.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val root = Ui.vertical(this, 16)
        val ir = IrTransmitter.get(this)

        val status = Ui.text(this,
            if (ir.hasEmitter) "✅ IR blaster ready  (${ir.frequencyInfo()})"
            else "⚠ No IR blaster found on this phone. Buttons will not work.",
            13f, if (ir.hasEmitter) Ui.ACCENT else Ui.WARN)
        root.addView(status, Ui.matchWrap(this, 4))
        root.addView(Ui.text(this, "Point the top of your phone at the device.", 13f, Ui.DIM), Ui.matchWrap(this, 4))

        listBox = Ui.vertical(this)
        root.addView(listBox, Ui.matchWrap(this))

        val row = Ui.horizontal(this)
        row.addView(Ui.button(this, "＋ Add device / brand", 0xFF0277BD.toInt()) { addDevice() }, Ui.weighted(this, 56))
        row.addView(Ui.button(this, "🔍 Code Finder") {
            startActivity(Intent(this, CodeFinderActivity::class.java))
        }, Ui.weighted(this, 56))
        root.addView(row, Ui.matchWrap(this))

        root.addView(Ui.text(this,
            "Tip: tap a device to open its remote. Long-press it to rename, share or delete. " +
            "Inside a remote use ⋮ → Edit mode to change any button's code.", 12f, Ui.DIM), Ui.matchWrap(this, 8))

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        listBox.removeAllViews()
        for (d in DeviceStore.all(this)) {
            val card = Ui.vertical(this, 16).apply { background = Ui.background(context, Ui.CARD, 18) }
            val icon = when (d.type) { DeviceType.TV -> "📺"; DeviceType.SOUNDBAR -> "🔊"; DeviceType.OTHER -> "🎛" }
            card.addView(Ui.text(this, "$icon  ${d.name}", 19f, bold = true))
            val count = d.codes.size + d.custom.count { it.code != null }
            card.addView(Ui.text(this, "${d.type.label} · $count buttons set" +
                    if (d.unverified) " · codes unverified" else "", 13f, Ui.DIM))
            card.isClickable = true
            card.setOnClickListener {
                startActivity(Intent(this, RemoteActivity::class.java).putExtra("id", d.id))
            }
            card.setOnLongClickListener { deviceMenu(d); true }
            listBox.addView(card, Ui.matchWrap(this, 6))
        }
        if (DeviceStore.all(this).isEmpty()) {
            listBox.addView(Ui.text(this, "No devices yet. Tap “Add device”.", 15f, Ui.DIM).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(30))
            }, Ui.matchWrap(this))
        }
    }

    private fun addDevice() {
        val names = Presets.all.map { it.name } + listOf("📋 Import from clipboard (shared code)")
        AlertDialog.Builder(this).setTitle("Choose brand / type")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == Presets.all.size) { importClipboard(); return@setItems }
                val p = Presets.all[which]
                Ui.askText(this, "Name this remote", p.name.substringBefore(" (")) { name ->
                    DeviceStore.all(this).add(Presets.create(p, name))
                    DeviceStore.save(this)
                    refresh()
                }
            }.show()
    }

    private fun importClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) { Ui.toast(this, "Clipboard is empty"); return }
        try {
            val start = text.indexOfFirst { it == '{' || it == '[' }
            val json = text.substring(start)
            val list = if (json.startsWith("[")) JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                       else listOf(JSONObject(json))
            for (o in list) DeviceStore.all(this).add(Device.fromJson(o, newId = true))
            DeviceStore.save(this)
            refresh()
            Ui.toast(this, "Imported ${list.size} device(s)")
        } catch (e: Exception) {
            Ui.toast(this, "Clipboard does not contain a Super Remote device")
        }
    }

    private fun deviceMenu(d: Device) {
        val opts = arrayOf("Rename", "Share codes (to back up or send)", "Duplicate", "Move to top", "Delete")
        AlertDialog.Builder(this).setTitle(d.name).setItems(opts) { _, w ->
            val all = DeviceStore.all(this)
            when (w) {
                0 -> Ui.askText(this, "Rename", d.name) { d.name = it; DeviceStore.save(this); refresh() }
                1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Super Remote device:\n" + d.toJson().toString())
                }, "Share ${d.name}"))
                2 -> { all.add(Device.fromJson(d.toJson(), newId = true).also { it.name = d.name + " copy" }); DeviceStore.save(this); refresh() }
                3 -> { all.remove(d); all.add(0, d); DeviceStore.save(this); refresh() }
                4 -> Ui.confirm(this, "Delete ${d.name}?", "Its saved codes will be lost.") {
                    all.remove(d); DeviceStore.save(this); refresh()
                }
            }
        }.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Backup all devices to clipboard")
        menu.add(0, 2, 0, "About / help")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                val arr = JSONArray(); DeviceStore.all(this).forEach { arr.put(it.toJson()) }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Super Remote backup", arr.toString()))
                Ui.toast(this, "Copied. Save it in a note; restore with Add device → Import.")
            }
            2 -> AlertDialog.Builder(this).setTitle("Super Remote").setMessage(HELP).setPositiveButton("OK", null).show()
        }
        return true
    }

    companion object {
        const val HELP = """Universal IR remote for phones with an IR blaster.

• LG TV: works out of the box (LG NEC codes).
• Vinnfier HyperBar 3000: Vinnfier does not publish its codes, so the built-in ones are a best guess. If a button does nothing, open Code Finder, let it scan while you watch the soundbar, and save the code that makes it react.
• Tip: if the soundbar is connected by HDMI ARC, turn on SIMPLINK (HDMI-CEC) on the LG TV – the TV remote volume then controls the soundbar.
• Add another brand: ＋ Add device. Presets exist for Samsung, Sony and Philips; any other brand can be added blank and filled with codes (protocol + address + command, Pronto hex, or raw timings).
• Hold VOL/CH/arrow buttons to repeat."""
    }
}
