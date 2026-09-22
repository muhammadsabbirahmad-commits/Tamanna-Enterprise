package com.tamanna.enterprise.due

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
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

data class Customer(
    val id: Long,
    val name: String,
    val mobile: String
)

object CustomerDueStorage {
    private const val PREFS = "tamanna_customer_due"
    private const val KEY = "entries"
    private const val CUSTOMERS_KEY = "customers"

    fun getEntries(context: Context): List<DueEntry> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(DueEntry(o.getLong("id"), o.getString("date"), o.getString("customer"),
                    o.optString("mobile", ""), o.getString("type"), o.getDouble("amount"), o.optString("note", "")))
            }
        }.sortedByDescending { it.id }
    }

    fun getCustomers(context: Context): List<Customer> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(CUSTOMERS_KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(Customer(o.getLong("id"), o.getString("name"), o.optString("mobile", "")))
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun addCustomer(context: Context, name: String, mobile: String): Boolean {
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        if (cleanName.isBlank()) return false
        val customers = getCustomers(context).toMutableList()
        if (customers.any { it.name.equals(cleanName, true) && it.mobile == cleanMobile }) return false
        customers.add(Customer(nextUniqueCustomerId(customers), cleanName, cleanMobile))
        saveCustomers(context, customers)
        return true
    }

    fun addSaleDue(context: Context, customer: String, mobile: String, amount: Double, note: String): Long {
        if (customer.isBlank() || amount <= 0) return 0L
        val entries = getEntries(context).toMutableList()
        val id = nextUniqueEntryId(entries)
        entries.add(DueEntry(id, now(), customer.trim(), mobile.trim(), "SALE", amount, note))
        saveEntries(context, entries)
        ensureCustomer(context, customer, mobile)
        return id
    }

    fun addPayment(context: Context, customer: String, mobile: String, amount: Double, note: String) {
        if (customer.isBlank() || amount <= 0) return
        val entries = getEntries(context).toMutableList()
        entries.add(DueEntry(nextUniqueEntryId(entries), now(), customer.trim(), mobile.trim(), "PAYMENT", amount, note))
        saveEntries(context, entries)
        ensureCustomer(context, customer, mobile)
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

    private fun ensureCustomer(context: Context, name: String, mobile: String) {
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        if (cleanName.isBlank()) return
        if (getCustomers(context).any { it.name.equals(cleanName, true) && it.mobile == cleanMobile }) return
        addCustomer(context, cleanName, cleanMobile)
    }

    private fun nextUniqueEntryId(entries: List<DueEntry>): Long {
        val usedIds = entries.asSequence().map { it.id }.toHashSet()
        var id = System.currentTimeMillis()
        while (usedIds.contains(id)) id++
        return id
    }

    private fun nextUniqueCustomerId(customers: List<Customer>): Long {
        val usedIds = customers.asSequence().map { it.id }.toHashSet()
        var id = System.currentTimeMillis()
        while (usedIds.contains(id)) id++
        return id
    }

    private fun saveCustomers(context: Context, customers: List<Customer>) {
        val a = JSONArray()
        customers.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("mobile", it.mobile)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit()
            .putString(CUSTOMERS_KEY, a.toString()).apply()
    }

    private fun saveEntries(context: Context, entries: List<DueEntry>) {
        val a = JSONArray()
        entries.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("customer", it.customer)
                put("mobile", it.mobile); put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        BusinessStorage.prefs(context, PREFS)
            .edit().putString(KEY, a.toString()).apply()
    }

    private fun now(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

    fun removePaymentById(context: Context, id: Long): Boolean {
        if (id <= 0L) return false
        val entries = getEntries(context)
        if (entries.none { it.id == id && it.type == "PAYMENT" }) return false
        saveEntries(context, entries.filterNot { it.id == id && it.type == "PAYMENT" })
        return getEntries(context).none { it.id == id && it.type == "PAYMENT" }
    }

    fun removeSaleDueById(context: Context, id: Long): Boolean {
        if (id <= 0L) return false
        val entries = getEntries(context)
        if (entries.none { it.id == id && it.type == "SALE" }) return false
        saveEntries(context, entries.filterNot { it.id == id && it.type == "SALE" })
        return true
    }
}
