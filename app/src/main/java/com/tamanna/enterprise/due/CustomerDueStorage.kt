package com.tamanna.enterprise.due

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DueEntry(
    val id: Long,
    val date: String,
    val customer: String,
    val mobile: String,
    val type: String,
    val amount: Double,
    val note: String
)

object CustomerDueStorage {
    private const val PREFS = "tamanna_customer_due"
    private const val KEY = "entries"

    fun getEntries(context: Context): List<DueEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(DueEntry(o.getLong("id"), o.getString("date"), o.getString("customer"),
                    o.optString("mobile", ""), o.getString("type"), o.getDouble("amount"), o.optString("note", "")))
            }
        }.sortedByDescending { it.id }
    }

    fun addSaleDue(context: Context, customer: String, mobile: String, amount: Double, note: String) {
        if (customer.isBlank() || amount <= 0) return
        add(context, DueEntry(System.currentTimeMillis(), now(), customer.trim(), mobile.trim(), "SALE", amount, note))
    }

    fun addPayment(context: Context, customer: String, mobile: String, amount: Double, note: String) {
        if (customer.isBlank() || amount <= 0) return
        add(context, DueEntry(System.currentTimeMillis(), now(), customer.trim(), mobile.trim(), "PAYMENT", amount, note))
    }

    fun getBalance(context: Context, customer: String, mobile: String = ""): Double =
        getEntries(context).filter { sameCustomer(it, customer, mobile) }
            .sumOf { if (it.type == "SALE") it.amount else -it.amount }.coerceAtLeast(0.0)

    fun getBalances(context: Context): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        getEntries(context).forEach {
            val key = it.customer.trim() + if (it.mobile.isBlank()) "" else " • " + it.mobile.trim()
            result[key] = (result[key] ?: 0.0) + if (it.type == "SALE") it.amount else -it.amount
        }
        return result.filterValues { it > 0.004 }
    }

    private fun sameCustomer(e: DueEntry, customer: String, mobile: String): Boolean =
        e.customer.trim().equals(customer.trim(), true) && (mobile.isBlank() || e.mobile == mobile.trim())

    private fun add(context: Context, entry: DueEntry) {
        val list = getEntries(context).toMutableList()
        list.add(entry)
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("customer", it.customer)
                put("mobile", it.mobile); put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    private fun now(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    fun removeSaleDue(context: Context, customer: String, mobile: String, amount: Double, note: String) {
        val entries = getEntries(context).filterNot {
            it.type == "SALE" &&
                it.customer.trim().equals(customer.trim(), true) &&
                it.mobile == mobile.trim() &&
                kotlin.math.abs(it.amount - amount) < 0.000001 &&
                it.note == note
        }
        saveEntries(context, entries)
    }

    private fun saveEntries(context: Context, entries: List<DueEntry>) {
        val a = JSONArray()
        entries.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("customer", it.customer)
                put("mobile", it.mobile); put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, a.toString()).apply()
    }

}
