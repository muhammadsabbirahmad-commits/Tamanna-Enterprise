package com.tamanna.enterprise.purchase

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Supplier(val id: Long, val name: String, val mobile: String, val address: String)

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
    private const val SUPPLIERS_KEY = "suppliers"

    fun getEntries(context: Context): List<SupplierDueEntry> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(SupplierDueEntry(o.getLong("id"), o.getString("date"), o.getString("supplier"),
                    o.getString("type"), o.getDouble("amount"), o.optString("note", "")))
            }
        }.sortedByDescending { it.id }
    }

    fun getSuppliers(context: Context): List<Supplier> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(SUPPLIERS_KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o=a.getJSONObject(i)
                add(Supplier(o.getLong("id"),o.getString("name"),o.optString("mobile",""),o.optString("address","")))
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun addSupplier(context: Context, name: String, mobile: String, address: String): Boolean {
        val n=name.trim(); val m=mobile.trim()
        if(n.isBlank()) return false
        val list=getSuppliers(context).toMutableList()
        if(list.any { it.name.equals(n,true) && it.mobile==m }) return false
        list.add(Supplier(System.currentTimeMillis(),n,m,address.trim()))
        val a=JSONArray()
        list.forEach { a.put(JSONObject().apply { put("id",it.id);put("name",it.name);put("mobile",it.mobile);put("address",it.address) }) }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(SUPPLIERS_KEY,a.toString()).apply()
        return true
    }

    fun addPurchaseDue(context: Context, supplier: String, amount: Double, note: String): Long {
        if (supplier.isBlank() || amount <= 0) return 0L
        val id = add(context, SupplierDueEntry(System.currentTimeMillis(), now(), supplier.trim(), "PURCHASE", amount, note))
        ensureSupplier(context, supplier)
        return id
    }

    fun addPayment(context: Context, supplier: String, amount: Double, note: String) {
        if (supplier.isBlank() || amount <= 0) return
        add(context, SupplierDueEntry(System.currentTimeMillis(), now(), supplier.trim(), "PAYMENT", -amount, note))
        ensureSupplier(context, supplier)
    }

    private fun ensureSupplier(context: Context, name: String) {
        if(name.isBlank() || getSuppliers(context).any { it.name.equals(name.trim(),true) }) return
        addSupplier(context,name,"","")
    }

    fun getBalance(context: Context, supplier: String): Double =
        getEntries(context).filter { it.supplier.equals(supplier, true) }.sumOf { it.amount }.coerceAtLeast(0.0)

    fun getBalances(context: Context): List<Pair<String, Double>> =
        getSuppliers(context).map { it.name to getBalance(context,it.name) }.sortedByDescending { it.second }

    fun removeById(context: Context, id: Long): Boolean {
        if (id <= 0L) return false
        val entries = getEntries(context)
        if (entries.none { it.id == id }) return false
        val remaining = entries.filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach {
            array.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("supplier", it.supplier)
                put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit().putString(KEY, array.toString()).apply()
        return getEntries(context).none { it.id == id }
    }

    private fun add(context: Context, entry: SupplierDueEntry): Long {
        val entries = getEntries(context).toMutableList()
        val usedIds = entries.asSequence().map { it.id }.toHashSet()
        var uniqueId = entry.id
        while (usedIds.contains(uniqueId)) {
            uniqueId++
        }
        val normalizedEntry = entry.copy(id = uniqueId)
        entries.add(normalizedEntry)
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("supplier", it.supplier)
                put("type", it.type); put("amount", it.amount); put("note", it.note)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit().putString(KEY, array.toString()).apply()
        return if (getEntries(context).any { it.id == normalizedEntry.id }) normalizedEntry.id else 0L
    }

    private fun now(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
}
