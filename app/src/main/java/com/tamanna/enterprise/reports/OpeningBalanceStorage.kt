package com.tamanna.enterprise.reports

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONObject

data class OpeningBalances(
    val date: String,
    val cash: Double,
    val bank: Double,
    val stock: Double,
    val customerReceivable: Double,
    val supplierPayable: Double,
    val otherLiabilities: Double,
    val verified: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalAssets: Double get() = cash + bank + stock + customerReceivable
    val totalLiabilities: Double get() = supplierPayable + otherLiabilities
}

object OpeningBalanceStorage {
    private const val PREFS = "tamanna_opening_balances"
    private const val KEY = "opening_balance_v1"

    fun get(context: Context): OpeningBalances? {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            OpeningBalances(
                date = o.getString("date"),
                cash = o.getDouble("cash"),
                bank = o.getDouble("bank"),
                stock = o.getDouble("stock"),
                customerReceivable = o.getDouble("customerReceivable"),
                supplierPayable = o.getDouble("supplierPayable"),
                otherLiabilities = o.getDouble("otherLiabilities"),
                verified = o.optBoolean("verified", false),
                updatedAt = o.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    fun save(context: Context, value: OpeningBalances): Boolean {
        val amounts = listOf(value.cash, value.bank, value.stock, value.customerReceivable,
            value.supplierPayable, value.otherLiabilities)
        if (!validDate(value.date) || amounts.any { !it.isFinite() || it < 0.0 }) return false
        val json = JSONObject().apply {
            put("date", value.date)
            put("cash", value.cash)
            put("bank", value.bank)
            put("stock", value.stock)
            put("customerReceivable", value.customerReceivable)
            put("supplierPayable", value.supplierPayable)
            put("otherLiabilities", value.otherLiabilities)
            put("verified", value.verified)
            put("updatedAt", System.currentTimeMillis())
        }
        return BusinessStorage.prefs(context, PREFS).edit().putString(KEY, json.toString()).commit()
    }

    private fun validDate(value: String): Boolean = runCatching {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
        f.parse(value)?.let { f.format(it) == value } == true
    }.getOrDefault(false)
}
