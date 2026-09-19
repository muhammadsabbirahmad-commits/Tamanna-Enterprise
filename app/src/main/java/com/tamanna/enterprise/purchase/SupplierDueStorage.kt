package com.tamanna.enterprise.purchase

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SupplierDueEntry(
    val id: Long,
    val date: String,
    val supplier: String,
    val type: String,
    val amount: Double,
    val note: String
)

object SupplierDueStorage {
    private const val PREFS = "tamanna_supplier_due"
    private const val KEY = "entries"

    fun getEntries(context: Context): List<SupplierDueEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(SupplierDueEntry(o.getLong("id"), o.getString("date"), o.getString("supplier"),
                    o.getString("type"), o.getDouble("amount"), o.optString("note", "")))
            }
        }.sortedByDescending { it.id }
    }

    fun addPurchaseDue(context: Context, supplier: String, amount: Double, note: String) =
        add(context, SupplierDueEntry(System.currentTimeMillis(), now(), supplier, "PURCHASE", amount, note))

    fun addPayment(context: Context, supplier: String, amount: Double, note: String) =
        add(context, SupplierDueEntry(System.currentTimeMillis(), now(), supplier, "PAYMENT", -amount, note))

    fun getBalance(context: Context, supplier: String): Double =
        getEntries(context).filter { it.supplier.equals(supplier, true) }.sumOf { it.amount }.coerceAtLeast(0.0)

    fun getBalances(context: Context): List<Pair<String, Double>> =
        getEntries(context).groupBy { it.supplier.trim() }
            .map { (name, entries) -> name to entries.sumOf { it.amount }.coerceAtLeast(0.0) }
            .filter { it.first.isNotBlank() && it.second > 0.0 }
            .sortedByDescending { it.second }

    private fun add(context: Context, entry: SupplierDueEntry) {
        val entries = getEntries(context).toMutableList()
        entries.add(entry)
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("supplier", it.supplier)
                put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun now(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
}
