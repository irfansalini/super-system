package com.superremote.ir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DeviceType(val label: String) {
    TV("TV"), SOUNDBAR("Soundbar / Speaker"), OTHER("Other device");

    companion object {
        fun parse(s: String?) = values().firstOrNull { it.name == s } ?: OTHER
    }
}

class CustomButton(var label: String, var code: IrCode?)

class Device(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var brand: String,
    var type: DeviceType,
    val codes: MutableMap<String, IrCode> = mutableMapOf(),
    val custom: MutableList<CustomButton> = mutableListOf(),
    /** true when the built-in codes are a best guess and should be checked with the Code Finder. */
    var unverified: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("brand", brand); put("type", type.name)
        put("unverified", unverified)
        val c = JSONObject()
        for ((k, v) in codes) c.put(k, v.toJson())
        put("codes", c)
        val arr = JSONArray()
        for (b in custom) arr.put(JSONObject().apply {
            put("label", b.label)
            b.code?.let { put("code", it.toJson()) }
        })
        put("custom", arr)
    }

    companion object {
        fun fromJson(o: JSONObject, newId: Boolean = false): Device {
            val d = Device(
                id = if (newId || !o.has("id")) UUID.randomUUID().toString() else o.getString("id"),
                name = o.optString("name", "Device"),
                brand = o.optString("brand", ""),
                type = DeviceType.parse(o.optString("type")),
                unverified = o.optBoolean("unverified", false)
            )
            o.optJSONObject("codes")?.let { c ->
                for (k in c.keys()) d.codes[k] = IrCode.fromJson(c.getJSONObject(k))
            }
            o.optJSONArray("custom")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    d.custom += CustomButton(b.optString("label", "Button"),
                        b.optJSONObject("code")?.let { IrCode.fromJson(it) })
                }
            }
            return d
        }
    }
}

/** Saves all devices as JSON in SharedPreferences. */
object DeviceStore {
    private const val PREFS = "devices"
    private const val KEY = "list"
    private var cache: MutableList<Device>? = null

    fun all(ctx: Context): MutableList<Device> {
        cache?.let { return it }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY, null)
        val list = mutableListOf<Device>()
        if (text == null) {
            // First launch: set up the user's own devices.
            list += Presets.create(Presets.LG_TV)
            list += Presets.create(Presets.VINNFIER_HYPERBAR)
        } else {
            try {
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) list += Device.fromJson(arr.getJSONObject(i))
            } catch (e: Exception) { /* corrupted: start empty */ }
        }
        cache = list
        if (text == null) save(ctx)
        return list
    }

    fun find(ctx: Context, id: String?): Device? = all(ctx).firstOrNull { it.id == id }

    fun save(ctx: Context) {
        val arr = JSONArray()
        for (d in all(ctx)) arr.put(d.toJson())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
