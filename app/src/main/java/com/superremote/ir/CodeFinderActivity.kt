package com.superremote.ir

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.superremote.ir.Ui.dp

/**
 * Brute-force code finder for devices whose remote is lost/broken and whose codes are unknown.
 * Sends code after code while the user watches the device; when it reacts, the user stops,
 * steps back to find the exact code, and saves it to a button.
 */
class CodeFinderActivity : Activity() {
    private lateinit var ir: IrTransmitter
    private val handler = Handler(Looper.getMainLooper())

    private var protocol = Protocol.NEC
    private var address = 0x00
    private var command = 0x00
    private var scanAddress = false   // false: vary command, true: vary address
    private var running = false
    private var delayMs = 700L
    private val history = ArrayList<IrCode>()

    private lateinit var addrField: EditText
    private lateinit var cmdView: TextView
    private lateinit var stateView: TextView
    private lateinit var startBtn: Button
    private lateinit var autoNextAddr: CheckBox
    private lateinit var historyView: TextView

    /** Addresses most often used by soundbars and cheap A/V gear, tried first. */
    private val commonNecAddresses = listOf(
        0x00, 0x80, 0x01, 0x02, 0x40, 0x20, 0x10, 0x08, 0x04, 0x7F, 0xFF, 0xFE, 0x03, 0x06, 0x12, 0x2C, 0x86, 0xEF
    )

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            sendCurrent()
            if (!advance(+1)) { stop("Finished this range. Nothing reacted? Try the next address or another protocol."); return }
            render()
            handler.postDelayed(this, delayMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = IrTransmitter.get(this)
        title = "Code Finder"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start from the target device's existing protocol/address if it has one
        DeviceStore.find(this, intent.getStringExtra("id"))?.codes?.values
            ?.firstOrNull { it.protocol in Protocol.scannable }?.let { protocol = it.protocol; address = it.address }

        val root = Ui.vertical(this, 14)
        root.addView(Ui.text(this,
            "How to use:\n1. Turn the soundbar ON and point the phone at it from about 1 m.\n" +
            "2. Press START and watch the soundbar (display, LED, sound level, power).\n" +
            "3. When it reacts, press IT REACTED. Step back with ◀ and use SEND to find the exact code.\n" +
            "4. Press SAVE to put the code on a button. Once one code works, other buttons usually share the same address – scan only the command.",
            13f, Ui.DIM), Ui.matchWrap(this, 4))

        // protocol
        root.addView(Ui.text(this, "Protocol", 13f, Ui.DIM))
        val protos = Protocol.scannable
        root.addView(Ui.spinner(this, protos.map { it.label }, protos.indexOf(protocol).coerceAtLeast(0)) {
            if (protocol != protos[it]) {
                protocol = protos[it]
                address = address.coerceAtMost(protocol.maxAddress); command = command.coerceAtMost(protocol.maxCommand)
                render()
            }
        })

        // mode
        root.addView(Ui.text(this, "What to scan", 13f, Ui.DIM))
        root.addView(Ui.spinner(this, listOf("Commands (keep address fixed) – recommended", "Addresses (keep command fixed)"), 0) {
            scanAddress = it == 1; render()
        })

        // address
        root.addView(Ui.text(this, "Address", 13f, Ui.DIM))
        val addrRow = Ui.horizontal(this)
        addrRow.addView(Ui.button(this, "−") { setAddress(address - 1) }, Ui.weighted(this, 50))
        addrField = Ui.input(this, "0x00", "", true).apply {
            gravity = Gravity.CENTER; textSize = 20f
            setOnFocusChangeListener { _, f -> if (!f) Ui.parseNumber(text.toString())?.let { setAddress(it) } ?: render() }
        }
        addrRow.addView(addrField, Ui.weighted(this, 56).apply { weight = 1.4f })
        addrRow.addView(Ui.button(this, "＋") { setAddress(address + 1) }, Ui.weighted(this, 50))
        addrRow.addView(Ui.button(this, "Next common") { nextCommonAddress() }, Ui.weighted(this, 50).apply { weight = 1.6f })
        root.addView(addrRow, Ui.matchWrap(this))

        autoNextAddr = CheckBox(this).apply {
            text = "When commands run out, continue with the next common address"
            setTextColor(Ui.TEXT); isChecked = true
        }
        root.addView(autoNextAddr)

        // speed
        root.addView(Ui.text(this, "Speed", 13f, Ui.DIM))
        val speeds = listOf(400L, 700L, 1000L, 1500L, 2500L)
        root.addView(Ui.spinner(this, listOf("Fast (0.4 s)", "Normal (0.7 s)", "Slow (1 s)", "Slower (1.5 s)", "Very slow (2.5 s)"), 1) {
            delayMs = speeds[it]
        })

        // current code
        cmdView = Ui.text(this, "", 30f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(4)) }
        root.addView(cmdView, Ui.matchWrap(this))
        stateView = Ui.text(this, "Ready", 14f, Ui.ACCENT).apply { gravity = Gravity.CENTER }
        root.addView(stateView, Ui.matchWrap(this))

        val stepRow = Ui.horizontal(this)
        stepRow.addView(Ui.button(this, "◀ Prev") { pause(); advance(-1); render() }, Ui.weighted(this, 60))
        stepRow.addView(Ui.button(this, "SEND", 0xFF1565C0.toInt()) { pause(); sendCurrent(); render() }, Ui.weighted(this, 60))
        stepRow.addView(Ui.button(this, "Next ▶") { pause(); advance(+1); render() }, Ui.weighted(this, 60))
        root.addView(stepRow, Ui.matchWrap(this))

        startBtn = Ui.button(this, "", 0xFF2E7D32.toInt()) { if (running) pause() else start() }
        root.addView(startBtn, Ui.matchWrap(this, 4).apply { height = dp(64) })

        root.addView(Ui.button(this, "✋ IT REACTED!", 0xFFEF6C00.toInt()) {
            stop("Stopped. The reaction came from one of the last codes below – tap one to resend it.")
        }, Ui.matchWrap(this, 4).apply { height = dp(64) })

        root.addView(Ui.button(this, "💾 SAVE this code to a button", 0xFF6A1B9A.toInt()) { saveDialog() },
            Ui.matchWrap(this, 4).apply { height = dp(56) })

        root.addView(Ui.text(this, "Last codes sent (tap to resend & select):", 13f, Ui.DIM).apply { setPadding(0, dp(12), 0, 0) })
        historyView = Ui.text(this, "", 14f)
        root.addView(historyView)
        historyBox = Ui.vertical(this)
        root.addView(historyBox, Ui.matchWrap(this))

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
        render()
    }

    private lateinit var historyBox: android.widget.LinearLayout

    private fun current() = IrCode(protocol, address, command)

    private fun setAddress(a: Int) {
        address = when {
            a < 0 -> protocol.maxAddress
            a > protocol.maxAddress -> 0
            else -> a
        }
        render()
    }

    private fun nextCommonAddress() {
        if (protocol != Protocol.NEC) { setAddress(address + 1); return }
        val i = commonNecAddresses.indexOf(address)
        setAddress(commonNecAddresses[(i + 1) % commonNecAddresses.size])
        command = 0
        render()
    }

    /** Moves to the next/previous code. Returns false when the scan range is exhausted. */
    private fun advance(step: Int): Boolean {
        if (scanAddress) {
            val n = address + step
            if (n > protocol.maxAddress) return false
            address = n.coerceAtLeast(0)
            return true
        }
        val n = command + step
        if (n > protocol.maxCommand) {
            if (running && autoNextAddr.isChecked) {
                nextCommonAddress()
                command = 0
                return true
            }
            return false
        }
        command = n.coerceAtLeast(0)
        return true
    }

    private fun sendCurrent() {
        val c = current()
        ir.send(c) { e -> stop(e) }
        history.remove(c)
        history.add(0, c)
        if (history.size > 8) history.removeAt(history.size - 1)
    }

    private fun start() {
        if (!ir.hasEmitter) { Ui.toast(this, "This phone has no IR blaster"); return }
        running = true
        stateView.text = "Scanning… watch the device"
        handler.post(tick)
        render()
    }

    private fun pause() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        stateView.text = "Paused"
        render()
    }

    private fun stop(msg: String) {
        running = false
        handler.removeCallbacks(tick)
        stateView.text = msg
        render()
    }

    private fun render() {
        if (!addrField.hasFocus()) addrField.setText(IrCode.hex(address, protocol.maxAddress))
        cmdView.text = "${protocol.name}\naddr ${IrCode.hex(address, protocol.maxAddress)} · cmd ${IrCode.hex(command, protocol.maxCommand)}"
        startBtn.text = if (running) "⏸ PAUSE" else "▶ START SCAN"
        startBtn.background = Ui.background(this, if (running) 0xFF8D6E63.toInt() else 0xFF2E7D32.toInt())
        historyBox.removeAllViews()
        historyView.text = if (history.isEmpty()) "–" else ""
        for (c in history) {
            historyBox.addView(Ui.button(this, c.describe(), Ui.CARD) {
                pause()
                protocol = c.protocol; address = c.address; command = c.command
                sendCurrent(); render()
            }, Ui.matchWrap(this, 2).apply { height = dp(44) })
        }
    }

    private fun saveDialog() {
        pause()
        val code = current()
        val devices = DeviceStore.all(this)
        val names = devices.map { it.name } + "＋ New device…"
        val preselect = devices.indexOfFirst { it.id == intent.getStringExtra("id") }
        AlertDialog.Builder(this).setTitle("Save ${code.describe()} to which remote?")
            .setSingleChoiceItems(names.toTypedArray(), preselect) { dlg, which ->
                dlg.dismiss()
                if (which == devices.size) {
                    Ui.askText(this, "New soundbar name", "My soundbar") { n ->
                        val d = Device(name = n, brand = "", type = DeviceType.SOUNDBAR)
                        devices.add(d); DeviceStore.save(this); chooseKey(d, code)
                    }
                } else chooseKey(devices[which], code)
            }.show()
    }

    private fun chooseKey(d: Device, code: IrCode) {
        val ids = Keys.allIds(d.type)
        val labels = ids.map { id ->
            val k = Keys[id]
            val name = k.label.ifEmpty { id.replaceFirstChar { it.uppercase() } }
            if (d.codes[id] != null) "$name   (replace)" else name
        } + d.custom.map { "• ${it.label}" } + "＋ New custom button…"
        AlertDialog.Builder(this).setTitle("Which button?")
            .setItems(labels.toTypedArray()) { _, w ->
                when {
                    w < ids.size -> d.codes[ids[w]] = code
                    w < ids.size + d.custom.size -> d.custom[w - ids.size].code = code
                    else -> {
                        Ui.askText(this, "Button name", "") { n ->
                            d.custom += CustomButton(n, code); d.unverified = false; DeviceStore.save(this)
                            Ui.toast(this, "Saved to ${d.name} → $n")
                        }
                        return@setItems
                    }
                }
                d.unverified = false
                DeviceStore.save(this)
                Ui.toast(this, "Saved to ${d.name}")
            }.show()
    }

    override fun onPause() {
        super.onPause()
        pause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return true
    }
}
