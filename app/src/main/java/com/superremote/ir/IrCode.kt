package com.superremote.ir

import org.json.JSONObject

/** Supported IR protocols. */
enum class Protocol(val label: String, val usesAddress: Boolean, val maxAddress: Int, val maxCommand: Int) {
    NEC("NEC (8-bit address)", true, 0xFF, 0xFF),
    NECEXT("NEC extended (16-bit address)", true, 0xFFFF, 0xFF),
    SAMSUNG32("Samsung32", true, 0xFF, 0xFF),
    SONY12("Sony SIRC 12-bit", true, 0x1F, 0x7F),
    SONY15("Sony SIRC 15-bit", true, 0xFF, 0x7F),
    SONY20("Sony SIRC 20-bit", true, 0x1FFF, 0x7F),
    RC5("Philips RC5", true, 0x1F, 0x7F),
    PRONTO("Pronto hex (paste)", false, 0, 0),
    RAW("Raw timings (freq: µs,µs,…)", false, 0, 0);

    companion object {
        fun parse(s: String?): Protocol = values().firstOrNull { it.name == s } ?: NEC
        /** Protocols that can be scanned by number in the Code Finder. */
        val scannable = listOf(NEC, NECEXT, SAMSUNG32, SONY12, SONY15, SONY20, RC5)
    }
}

/** One IR code: either protocol + address + command, or raw text (Pronto / raw timings). */
data class IrCode(
    val protocol: Protocol,
    val address: Int = 0,
    val command: Int = 0,
    val raw: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("p", protocol.name)
        put("a", address)
        put("c", command)
        if (raw.isNotEmpty()) put("r", raw)
    }

    fun describe(): String = when (protocol) {
        Protocol.PRONTO -> "Pronto: " + raw.take(24) + if (raw.length > 24) "…" else ""
        Protocol.RAW -> "Raw: " + raw.take(24) + if (raw.length > 24) "…" else ""
        else -> "${protocol.name}  addr ${hex(address, protocol.maxAddress)}  cmd ${hex(command, protocol.maxCommand)}"
    }

    companion object {
        fun fromJson(o: JSONObject): IrCode = IrCode(
            Protocol.parse(o.optString("p")),
            o.optInt("a"),
            o.optInt("c"),
            o.optString("r", "")
        )

        fun nec(a: Int, c: Int) = IrCode(Protocol.NEC, a, c)
        fun samsung(a: Int, c: Int) = IrCode(Protocol.SAMSUNG32, a, c)
        fun sony12(a: Int, c: Int) = IrCode(Protocol.SONY12, a, c)
        fun rc5(a: Int, c: Int) = IrCode(Protocol.RC5, a, c)

        fun hex(v: Int, max: Int): String = if (max > 0xFF) "0x%04X".format(v) else "0x%02X".format(v)
    }
}

/** A carrier frequency and an on/off pattern in microseconds, ready for ConsumerIrManager. */
class IrSignal(val frequency: Int, val pattern: IntArray)

object IrEncoder {
    private var rc5Toggle = 0

    fun encode(code: IrCode): IrSignal = when (code.protocol) {
        Protocol.NEC -> nec(code.address and 0xFF, (code.address.inv()) and 0xFF, code.command)
        Protocol.NECEXT -> nec(code.address and 0xFF, (code.address shr 8) and 0xFF, code.command)
        Protocol.SAMSUNG32 -> samsung32(code.address and 0xFF, code.command)
        Protocol.SONY12 -> sony(code.command, code.address and 0x1F, 5)
        Protocol.SONY15 -> sony(code.command, code.address and 0xFF, 8)
        Protocol.SONY20 -> sony(code.command, code.address and 0x1FFF, 13)
        Protocol.RC5 -> rc5(code.address and 0x1F, code.command and 0x7F)
        Protocol.PRONTO -> pronto(code.raw)
        Protocol.RAW -> raw(code.raw)
    }

    // ---- NEC: 9ms mark, 4.5ms space, 32 bits LSB first, 562µs mark, 562/1687µs space ----
    private fun nec(addrLo: Int, addrHi: Int, cmd: Int): IrSignal {
        val p = ArrayList<Int>()
        p += 9000; p += 4500
        for (b in intArrayOf(addrLo, addrHi, cmd and 0xFF, cmd.inv() and 0xFF)) pulseDistanceByte(p, b)
        p += 562
        return IrSignal(38000, p.toIntArray())
    }

    private fun samsung32(addr: Int, cmd: Int): IrSignal {
        val p = ArrayList<Int>()
        p += 4500; p += 4500
        for (b in intArrayOf(addr, addr, cmd and 0xFF, cmd.inv() and 0xFF)) pulseDistanceByte(p, b)
        p += 562
        return IrSignal(38000, p.toIntArray())
    }

    private fun pulseDistanceByte(p: MutableList<Int>, byte: Int) {
        for (i in 0 until 8) {
            p += 562
            p += if ((byte shr i) and 1 == 1) 1687 else 562
        }
    }

    // ---- Sony SIRC: 40kHz, 2.4ms header, bits LSB first, sent 3 times every 45ms ----
    private fun sony(cmd: Int, addr: Int, addrBits: Int): IrSignal {
        val frame = ArrayList<Int>()
        frame += 2400
        val bits = ArrayList<Int>()
        for (i in 0 until 7) bits += (cmd shr i) and 1
        for (i in 0 until addrBits) bits += (addr shr i) and 1
        for (b in bits) {
            frame += 600            // space
            frame += if (b == 1) 1200 else 600 // mark
        }
        val frameLen = frame.sum()
        val p = ArrayList<Int>()
        repeat(3) { n ->
            p.addAll(frame)
            if (n < 2) p += maxOf(45000 - frameLen, 600)
        }
        return IrSignal(40000, p.toIntArray())
    }

    // ---- Philips RC5: 36kHz Manchester, 889µs half-bits ----
    private fun rc5(addr: Int, cmd: Int): IrSignal {
        rc5Toggle = rc5Toggle xor 1
        val bits = ArrayList<Int>()
        bits += 1
        bits += if (cmd < 64) 1 else 0 // field bit (inverted 7th command bit)
        bits += rc5Toggle
        for (i in 4 downTo 0) bits += (addr shr i) and 1
        for (i in 5 downTo 0) bits += (cmd shr i) and 1
        // levels: true = carrier on. '1' = off then on, '0' = on then off
        val levels = ArrayList<Boolean>()
        for (b in bits) {
            if (b == 1) { levels += false; levels += true } else { levels += true; levels += false }
        }
        val p = ArrayList<Int>()
        var cur: Boolean? = null
        for (l in levels) {
            if (l == cur) p[p.size - 1] = p.last() + 889 else { p += 889; cur = l }
        }
        // pattern must start with a mark: drop the leading space; and end with a mark
        if (!levels.first()) p.removeAt(0)
        if (!levels.last()) p.removeAt(p.size - 1)
        return IrSignal(36000, p.toIntArray())
    }

    // ---- Pronto hex: "0000 FFFF NNNN MMMM pairs..." ----
    fun pronto(text: String): IrSignal {
        val words = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toInt(16) }
        require(words.size >= 6 && words[0] == 0) { "Only learned Pronto codes (starting with 0000) are supported" }
        val freq = (1_000_000.0 / (words[1] * 0.241246)).toInt()
        val once = words[2]
        val rep = words[3]
        val periodUs = 1_000_000.0 / freq
        val body = words.drop(4)
        require(body.size >= (once + rep) * 2) { "Pronto code is too short" }
        val pairs = if (once > 0) body.take(once * 2) + body.drop(once * 2).take(rep * 2) else body.take(rep * 2)
        val p = pairs.map { maxOf(1, (it * periodUs).toInt()) }.toMutableList()
        if (p.size % 2 == 0 && p.size > 1) p.removeAt(p.size - 1) // end on a mark
        return IrSignal(freq, p.toIntArray())
    }

    // ---- Raw: "38000: 9000,4500,560,..." ----
    fun raw(text: String): IrSignal {
        val t = text.trim()
        val idx = t.indexOf(':')
        require(idx > 0) { "Format: frequency: mark,space,mark,…  e.g. 38000: 9000,4500,560" }
        val freq = t.substring(0, idx).trim().toInt()
        val p = t.substring(idx + 1).split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            .map { kotlin.math.abs(it.toInt()) }.toMutableList()
        require(p.isNotEmpty()) { "No timings found" }
        if (p.size % 2 == 0) p.removeAt(p.size - 1)
        return IrSignal(freq, p.toIntArray())
    }
}
