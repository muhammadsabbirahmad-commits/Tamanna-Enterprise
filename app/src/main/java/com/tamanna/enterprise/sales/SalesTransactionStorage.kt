package com.tamanna.enterprise.sales

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SaleTransaction(
    val transactionId: String,
    val date: String,
    val customer: String,
    val mobile: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val paid: Double,
    val due: Double,
    val paymentMethod: String
)

object SalesTransactionStorage {
    private const val PREFS = "tamanna_enterprise_sale_transactions"
    private const val KEY_TRANSACTIONS = "transactions"

    fun getTransactions(context: Context): List<SaleTransaction> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRANSACTIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SaleTransaction(
                        transactionId = item.getString("transactionId"),
                        date = item.getString("date"),
                        customer = item.optString("customer", ""),
                        mobile = item.optString("mobile", ""),
                        subtotal = item.optDouble("subtotal", 0.0),
                        discount = item.optDouble("discount", 0.0),
                        total = item.optDouble("total", 0.0),
                        paid = item.optDouble("paid", 0.0),
                        due = item.optDouble("due", 0.0),
                        paymentMethod = item.optString("paymentMethod", "Cash")
                    )
                )
            }
        }
    }

    fun addTransaction(context: Context, transaction: SaleTransaction) {
        val transactions = getTransactions(context).toMutableList()
        transactions.removeAll { it.transactionId == transaction.transactionId }
        transactions.add(transaction)

        val array = JSONArray()
        transactions.forEach {
            array.put(JSONObject().apply {
                put("transactionId", it.transactionId)
                put("date", it.date)
                put("customer", it.customer)
                put("mobile", it.mobile)
                put("subtotal", it.subtotal)
                put("discount", it.discount)
                put("total", it.total)
                put("paid", it.paid)
                put("due", it.due)
                put("paymentMethod", it.paymentMethod)
            })
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRANSACTIONS, array.toString())
            .apply()
    }

    fun getTransaction(context: Context, transactionId: String): SaleTransaction? =
        getTransactions(context).firstOrNull { it.transactionId == transactionId }

    fun removeTransaction(context: Context, transactionId: String) {
        saveTransactions(context, getTransactions(context).filterNot { it.transactionId == transactionId })
    }

    private fun saveTransactions(context: Context, transactions: List<SaleTransaction>) {
        val array = JSONArray()
        transactions.forEach { tx ->
            array.put(JSONObject().apply {
                put("transactionId", tx.transactionId)
                put("date", tx.date)
                put("customer", tx.customer)
                put("mobile", tx.mobile)
                put("subtotal", tx.subtotal)
                put("discount", tx.discount)
                put("total", tx.total)
                put("paid", tx.paid)
                put("due", tx.due)
                put("paymentMethod", tx.paymentMethod)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
