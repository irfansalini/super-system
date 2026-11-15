package com.superremote.ir

/** A button slot on a remote layout. [repeat] = keeps sending while held. */
class Key(val id: String, val label: String, val repeat: Boolean = false, val color: Int = 0)

object Keys {
    private val list = listOf(
        Key("power", "⏻ Power", color = 0xFFC62828.toInt()),
        Key("input", "Input"), Key("home", "⌂ Home"), Key("settings", "⚙ Settings"),
        Key("1", "1"), Key("2", "2"), Key("3", "3"), Key("4", "4"), Key("5", "5"),
        Key("6", "6"), Key("7", "7"), Key("8", "8"), Key("9", "9"), Key("0", "0"),
        Key("guide", "Guide"), Key("info", "Info"), Key("qmenu", "Q.Menu"),
        Key("up", "▲", true), Key("down", "▼", true), Key("left", "◀", true), Key("right", "▶", true),
        Key("ok", "OK", color = 0xFF1565C0.toInt()),
        Key("back", "↩ Back"), Key("exit", "Exit"),
        Key("vol_up", "VOL +", true), Key("vol_down", "VOL −", true), Key("mute", "🔇 Mute"),
        Key("ch_up", "CH +", true), Key("ch_down", "CH −", true),
        Key("rew", "⏪"), Key("play", "▶"), Key("pause", "⏸"), Key("stop", "⏹"), Key("ff", "⏩"),
        Key("play_pause", "⏯ Play"), Key("prev", "⏮ Prev"), Key("next", "⏭ Next"),
        Key("red", "", color = 0xFFD32F2F.toInt()), Key("green", "", color = 0xFF388E3C.toInt()),
        Key("yellow", "", color = 0xFFFBC02D.toInt()), Key("blue", "", color = 0xFF1976D2.toInt()),
        Key("netflix", "NETFLIX", color = 0xFF8E0000.toInt()), Key("prime", "prime video", color = 0xFF0D47A1.toInt()),
        Key("bt", "Bluetooth"), Key("optical", "Optical"), Key("aux", "AUX"), Key("hdmi", "HDMI ARC"),
        Key("usb", "USB"), Key("bass_up", "BASS +", true), Key("bass_down", "BASS −", true),
        Key("treble_up", "TREBLE +", true), Key("treble_down", "TREBLE −", true),
        Key("eq", "EQ"), Key("eq_music", "Music"), Key("eq_movie", "Movie"), Key("eq_news", "News"),
        Key("surround", "3D / Surround"), Key("dim", "Dimmer"), Key("reset", "Reset")
    )
    val byId = list.associateBy { it.id }
    operator fun get(id: String): Key = byId[id] ?: Key(id, id)

    /** Rows of key ids (null = empty space) for each device type. "NAV" = the D-pad block. */
    fun layout(type: DeviceType): List<List<String?>> = when (type) {
        DeviceType.TV -> listOf(
            listOf("power", "input", "home", "settings"),
            listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
            listOf("guide", "0", "info"),
            listOf("NAV"),
            listOf("back", "qmenu", "exit"),
            listOf("vol_up", "mute", "ch_up"),
            listOf("vol_down", null, "ch_down"),
            listOf("rew", "play", "pause", "stop", "ff"),
            listOf("red", "green", "yellow", "blue"),
            listOf("netflix", "prime")
        )
        DeviceType.SOUNDBAR -> listOf(
            listOf("power", "input", "mute"),
            listOf("vol_down", "vol_up"),
            listOf("bt", "optical", "aux", "hdmi"),
            listOf("usb", "eq", "surround"),
            listOf("prev", "play_pause", "next"),
            listOf("bass_down", "bass_up"),
            listOf("treble_down", "treble_up"),
            listOf("eq_music", "eq_movie", "eq_news"),
            listOf("dim", "reset")
        )
        DeviceType.OTHER -> listOf(
            listOf("power", "input", "mute"),
            listOf("NAV"),
            listOf("back", "home", "settings"),
            listOf("vol_up", null, "ch_up"),
            listOf("vol_down", null, "ch_down"),
            listOf("prev", "play_pause", "next")
        )
    }

    fun allIds(type: DeviceType): List<String> =
        layout(type).flatten().filterNotNull().flatMap { if (it == "NAV") listOf("up", "down", "left", "right", "ok") else listOf(it) }
}

class Preset(val name: String, val brand: String, val type: DeviceType, val unverified: Boolean = false,
             val codes: Map<String, IrCode>)

object Presets {
    private fun necMap(addr: Int, vararg pairs: Pair<String, Int>) = pairs.associate { it.first to IrCode.nec(addr, it.second) }
    private fun samsungMap(addr: Int, vararg pairs: Pair<String, Int>) = pairs.associate { it.first to IrCode.samsung(addr, it.second) }
    private fun sonyMap(addr: Int, vararg pairs: Pair<String, Int>) = pairs.associate { it.first to IrCode.sony12(addr, it.second) }
    private fun rc5Map(addr: Int, vararg pairs: Pair<String, Int>) = pairs.associate { it.first to IrCode.rc5(addr, it.second) }

    /** LG TVs: NEC, address 0x04. */
    val LG_TV = Preset("LG TV", "LG", DeviceType.TV, codes = necMap(0x04,
        "power" to 0x08, "input" to 0x0B, "home" to 0x7C, "settings" to 0x43,
        "1" to 0x11, "2" to 0x12, "3" to 0x13, "4" to 0x14, "5" to 0x15,
        "6" to 0x16, "7" to 0x17, "8" to 0x18, "9" to 0x19, "0" to 0x10,
        "guide" to 0xAB, "info" to 0xAA, "qmenu" to 0x45,
        "up" to 0x40, "down" to 0x41, "left" to 0x07, "right" to 0x06, "ok" to 0x44,
        "back" to 0x28, "exit" to 0x5B,
        "vol_up" to 0x02, "vol_down" to 0x03, "mute" to 0x09, "ch_up" to 0x00, "ch_down" to 0x01,
        "rew" to 0x8F, "play" to 0xB0, "pause" to 0xBA, "stop" to 0xB1, "ff" to 0x8E,
        "red" to 0x72, "green" to 0x71, "yellow" to 0x63, "blue" to 0x61,
        "netflix" to 0x56, "prime" to 0x5C
    ))

    /**
     * Vinnfier HyperBar 3000: the manufacturer does not publish its IR codes.
     * These are the codes most common in this class of soundbar (NEC address 0x00).
     * Marked unverified: if they do not work, the Code Finder finds the right ones.
     */
    val VINNFIER_HYPERBAR = Preset("Vinnfier HyperBar 3000", "Vinnfier", DeviceType.SOUNDBAR, unverified = true,
        codes = necMap(0x00,
            "power" to 0x45, "input" to 0x46, "mute" to 0x47,
            "prev" to 0x44, "next" to 0x40, "play_pause" to 0x43,
            "vol_down" to 0x07, "vol_up" to 0x15, "eq" to 0x09
        ))

    val SAMSUNG_TV = Preset("Samsung TV", "Samsung", DeviceType.TV, codes = samsungMap(0x07,
        "power" to 0x02, "input" to 0x01, "home" to 0x79, "settings" to 0x1A,
        "1" to 0x04, "2" to 0x05, "3" to 0x06, "4" to 0x08, "5" to 0x09,
        "6" to 0x0A, "7" to 0x0C, "8" to 0x0D, "9" to 0x0E, "0" to 0x11,
        "guide" to 0x4F, "info" to 0x1F, "qmenu" to 0x4B,
        "up" to 0x60, "down" to 0x61, "left" to 0x65, "right" to 0x62, "ok" to 0x68,
        "back" to 0x58, "exit" to 0x2D,
        "vol_up" to 0x07, "vol_down" to 0x0B, "mute" to 0x0F, "ch_up" to 0x12, "ch_down" to 0x10,
        "rew" to 0x45, "play" to 0x47, "pause" to 0x4A, "stop" to 0x46, "ff" to 0x48,
        "red" to 0x6C, "green" to 0x14, "yellow" to 0x15, "blue" to 0x16
    ))

    val SONY_TV = Preset("Sony TV", "Sony", DeviceType.TV, codes = sonyMap(0x01,
        "power" to 21, "input" to 37, "home" to 96,
        "1" to 0, "2" to 1, "3" to 2, "4" to 3, "5" to 4, "6" to 5, "7" to 6, "8" to 7, "9" to 8, "0" to 9,
        "info" to 58, "up" to 116, "down" to 117, "left" to 52, "right" to 51, "ok" to 101,
        "back" to 35, "vol_up" to 18, "vol_down" to 19, "mute" to 20, "ch_up" to 16, "ch_down" to 17
    ))

    val PHILIPS_TV = Preset("Philips TV (RC5)", "Philips", DeviceType.TV, codes = rc5Map(0x00,
        "power" to 12, "mute" to 13, "vol_up" to 16, "vol_down" to 17, "ch_up" to 32, "ch_down" to 33,
        "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6, "7" to 7, "8" to 8, "9" to 9, "0" to 0,
        "input" to 56, "info" to 15, "home" to 48, "up" to 80, "down" to 81, "left" to 85, "right" to 86, "ok" to 87,
        "back" to 10, "red" to 55, "green" to 54, "yellow" to 50, "blue" to 52
    ))

    val GENERIC_SOUNDBAR = Preset("Soundbar (blank – use Code Finder)", "", DeviceType.SOUNDBAR, codes = emptyMap())
    val BLANK_TV = Preset("TV (blank – enter codes yourself)", "", DeviceType.TV, codes = emptyMap())
    val BLANK_OTHER = Preset("Other device (blank)", "", DeviceType.OTHER, codes = emptyMap())

    val all = listOf(LG_TV, VINNFIER_HYPERBAR, SAMSUNG_TV, SONY_TV, PHILIPS_TV, GENERIC_SOUNDBAR, BLANK_TV, BLANK_OTHER)

    fun create(p: Preset, name: String = p.name) = Device(
        name = name, brand = p.brand, type = p.type, codes = p.codes.toMutableMap(), unverified = p.unverified
    )
}
