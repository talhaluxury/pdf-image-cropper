package com.pdfimage.cropper.data

import com.pdfimage.cropper.crop.CropRegion
import com.pdfimage.cropper.crop.NRect
import org.json.JSONArray
import org.json.JSONObject

data class SessionData(val crops: List<CropRegion>, val pages: Set<Int>)

object CropJson {
    fun encode(crops: List<CropRegion>, pages: Set<Int>): String {
        val arr = JSONArray()
        crops.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id).put("page", c.page)
                    .put("l", c.rect.left.toDouble()).put("t", c.rect.top.toDouble())
                    .put("r", c.rect.right.toDouble()).put("b", c.rect.bottom.toDouble())
                    .put("order", c.order).put("sel", c.selected)
            )
        }
        val pageArr = JSONArray()
        pages.sorted().forEach { pageArr.put(it) }
        return JSONObject().put("crops", arr).put("pages", pageArr).toString()
    }

    fun decode(json: String?): SessionData {
        if (json.isNullOrBlank()) return SessionData(emptyList(), emptySet())
        return try {
            val o = JSONObject(json)
            val arr = o.getJSONArray("crops")
            val crops = (0 until arr.length()).map {
                val c = arr.getJSONObject(it)
                CropRegion(
                    id = c.getString("id"),
                    page = c.getInt("page"),
                    rect = NRect(
                        c.getDouble("l").toFloat(), c.getDouble("t").toFloat(),
                        c.getDouble("r").toFloat(), c.getDouble("b").toFloat()
                    ),
                    order = c.getLong("order"),
                    selected = c.optBoolean("sel", true)
                )
            }
            val pa = o.getJSONArray("pages")
            SessionData(crops, (0 until pa.length()).map { pa.getInt(it) }.toSet())
        } catch (e: Exception) {
            SessionData(emptyList(), emptySet())
        }
    }
}
