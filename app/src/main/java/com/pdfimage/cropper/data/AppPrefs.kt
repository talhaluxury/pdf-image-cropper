package com.pdfimage.cropper.data

import android.content.Context
import com.pdfimage.cropper.crop.ExportOptions
import com.pdfimage.cropper.crop.ImageFormat
import com.pdfimage.cropper.crop.Resolution
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class RecentPdf(val uri: String, val name: String, val pages: Int, val time: Long)
data class SavedImage(val uri: String, val name: String)

class AppPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("pdf_image_cropper", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        runCatching { enumValueOf<E>(sp.getString(key, def.name) ?: def.name) }.getOrDefault(def)

    var format: ImageFormat
        get() = enumOf("format", ImageFormat.JPG)
        set(v) { sp.edit().putString("format", v.name).apply() }
    var quality: Int
        get() = sp.getInt("quality", 90)
        set(v) { sp.edit().putInt("quality", v).apply() }
    var resolution: Resolution
        get() = enumOf("resolution", Resolution.STANDARD)
        set(v) { sp.edit().putString("resolution", v.name).apply() }
    var theme: ThemeMode
        get() = enumOf("theme", ThemeMode.SYSTEM)
        set(v) { sp.edit().putString("theme", v.name).apply() }
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) { sp.edit().putBoolean("haptics", v).apply() }
    var keepSessions: Boolean
        get() = sp.getBoolean("keep_sessions", true)
        set(v) { sp.edit().putBoolean("keep_sessions", v).apply() }
    var folder: String
        get() = sp.getString("folder", DEFAULT_FOLDER) ?: DEFAULT_FOLDER
        set(v) { sp.edit().putString("folder", v).apply() }
    var totalConverted: Int
        get() = sp.getInt("total_converted", 0)
        set(v) { sp.edit().putInt("total_converted", v).apply() }

    fun defaultOptions() = ExportOptions(format, quality, resolution)

    // ---- recent PDFs ----
    fun recents(): List<RecentPdf> = runCatching {
        val arr = JSONArray(sp.getString("recents", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RecentPdf(o.getString("uri"), o.getString("name"), o.getInt("pages"), o.getLong("time"))
        }
    }.getOrDefault(emptyList())

    fun addRecent(r: RecentPdf) = saveRecents((listOf(r) + recents().filter { it.uri != r.uri }).take(10))
    fun removeRecent(uri: String) = saveRecents(recents().filter { it.uri != uri })

    private fun saveRecents(list: List<RecentPdf>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("pages", it.pages).put("time", it.time))
        }
        sp.edit().putString("recents", arr.toString()).apply()
    }

    // ---- converted images ----
    fun outputs(): List<SavedImage> = runCatching {
        val arr = JSONArray(sp.getString("outputs", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedImage(o.getString("uri"), o.getString("name"))
        }
    }.getOrDefault(emptyList())

    fun addOutputs(items: List<SavedImage>) {
        val merged = (items.reversed() + outputs()).take(60)
        val arr = JSONArray()
        merged.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        sp.edit().putString("outputs", arr.toString()).apply()
    }

    // ---- unfinished crop sessions ----
    fun loadSession(uri: String): String? = sp.getString("session:$uri", null)
    fun saveSession(uri: String, json: String) { sp.edit().putString("session:$uri", json).apply() }
    fun clearSession(uri: String) { sp.edit().remove("session:$uri").apply() }

    companion object {
        const val DEFAULT_FOLDER = "PDF Image Cropper"
        fun sanitizeFolder(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { DEFAULT_FOLDER }
    }
}
