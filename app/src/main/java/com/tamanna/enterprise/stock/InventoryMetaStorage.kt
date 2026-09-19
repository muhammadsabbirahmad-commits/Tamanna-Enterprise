package com.tamanna.enterprise.stock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class InventoryMeta(
    val code: String,
    val batch: String = "",
    val expiryDate: String = "",
    val lowStockLimit: Int = 5,
    val damagedQuantity: Int = 0
)

data class StockHistoryEntry(
    val id: Long,
    val date: String,
    val code: String,
    val productName: String,
    val type: String,
    val quantity: Int,
    val before: Int,
    val after: Int,
    val note: String
)

object InventoryMetaStorage {
    private const val PREFS = "tamanna_inventory_meta"
    private const val KEY_META = "meta"
    private const val KEY_HISTORY = "history"

    fun getMeta(context: Context, code: String): InventoryMeta =
        getAllMeta(context).firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: InventoryMeta(code = code)

    fun saveMeta(context: Context, meta: InventoryMeta) {
        val all = getAllMeta(context).filterNot { it.code.equals(meta.code, true) }.toMutableList()
        all.add(meta)
        val array = JSONArray()
        all.forEach { m ->
            array.put(JSONObject().apply {
                put("code", m.code)
                put("batch", m.batch)
                put("expiryDate", m.expiryDate)
                put("lowStockLimit", m.lowStockLimit)
                put("damagedQuantity", m.damagedQuantity)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_META, array.toString()).apply()
    }

    fun getAllMeta(context: Context): List<InventoryMeta> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_META, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(InventoryMeta(
                        code = o.optString("code"),
                        batch = o.optString("batch"),
                        expiryDate = o.optString("expiryDate"),
                        lowStockLimit = o.optInt("lowStockLimit", 5).coerceAtLeast(0),
                        damagedQuantity = o.optInt("damagedQuantity", 0).coerceAtLeast(0)
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addHistory(context: Context, code: String, productName: String, type: String,
                   quantity: Int, before: Int, after: Int, note: String = "") {
        val history = getHistory(context).toMutableList()
        history.add(StockHistoryEntry(
            System.currentTimeMillis(),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            code, productName, type, quantity, before, after, note
        ))
        val array = JSONArray()
        history.takeLast(500).forEach { h ->
            array.put(JSONObject().apply {
                put("id", h.id); put("date", h.date); put("code", h.code)
                put("productName", h.productName); put("type", h.type)
                put("quantity", h.quantity); put("before", h.before); put("after", h.after); put("note", h.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HISTORY, array.toString()).apply()
    }

    fun getHistory(context: Context): List<StockHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(StockHistoryEntry(
                        o.optLong("id"), o.optString("date"), o.optString("code"),
                        o.optString("productName"), o.optString("type"), o.optInt("quantity"),
                        o.optInt("before"), o.optInt("after"), o.optString("note")
                    ))
                }
            }.sortedByDescending { it.id }
        }.getOrDefault(emptyList())
    }
}
