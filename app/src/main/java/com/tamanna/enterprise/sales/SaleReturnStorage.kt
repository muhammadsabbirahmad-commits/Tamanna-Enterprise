package com.tamanna.enterprise.sales

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class SaleReturn(
    val returnId: String,
    val transactionId: String,
    val date: String,
    val amount: Double,
    val dueReduction: Double,
    val refundAmount: Double,
    val refundMethod: String
)

data class SaleReturnLine(
    val returnId: String,
    val transactionId: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double
)

object SaleReturnStorage {
    private const val PREFS = "tamanna_enterprise_sale_returns"
    private const val KEY_RETURNS = "returns"
    private const val KEY_LINES = "lines"

    fun getReturns(context: Context): List<SaleReturn> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_RETURNS, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(
                    SaleReturn(
                        o.getString("returnId"), o.getString("transactionId"),
                        o.getString("date"), o.getDouble("amount"),
                        o.getDouble("dueReduction"), o.getDouble("refundAmount"),
                        o.optString("refundMethod", "Cash")
                    )
                )
            }
        }
    }

    fun getLines(context: Context): List<SaleReturnLine> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_LINES, "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(
                    SaleReturnLine(
                        o.getString("returnId"), o.getString("transactionId"),
                        o.getString("productCode"), o.getString("productName"),
                        o.getInt("quantity"), o.getDouble("unitPrice")
                    )
                )
            }
        }
    }

    fun getReturnedQuantities(context: Context, transactionId: String): Map<String, Int> =
        getLines(context).filter { it.transactionId == transactionId }
            .groupBy { it.productCode }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

    fun getReturnedAmount(context: Context, transactionId: String): Double =
        getReturns(context).filter { it.transactionId == transactionId }.sumOf { it.amount }

    fun getDueReduction(context: Context, transactionId: String): Double =
        getReturns(context).filter { it.transactionId == transactionId }.sumOf { it.dueReduction }

    fun addReturn(context: Context, item: SaleReturn, lines: List<SaleReturnLine>) {
        val returns = getReturns(context).toMutableList()
        returns.removeAll { it.returnId == item.returnId }
        returns.add(item)
        val oldLines = getLines(context).filterNot { it.returnId == item.returnId }
        save(context, returns, oldLines + lines)
    }

    fun removeReturn(context: Context, returnId: String) {
        save(
            context,
            getReturns(context).filterNot { it.returnId == returnId },
            getLines(context).filterNot { it.returnId == returnId }
        )
    }

    private fun save(context: Context, returns: List<SaleReturn>, lines: List<SaleReturnLine>) {
        val ra = JSONArray()
        returns.forEach {
            ra.put(JSONObject().apply {
                put("returnId", it.returnId)
                put("transactionId", it.transactionId)
                put("date", it.date)
                put("amount", it.amount)
                put("dueReduction", it.dueReduction)
                put("refundAmount", it.refundAmount)
                put("refundMethod", it.refundMethod)
            })
        }
        val la = JSONArray()
        lines.forEach {
            la.put(JSONObject().apply {
                put("returnId", it.returnId)
                put("transactionId", it.transactionId)
                put("productCode", it.productCode)
                put("productName", it.productName)
                put("quantity", it.quantity)
                put("unitPrice", it.unitPrice)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit()
            .putString(KEY_RETURNS, ra.toString())
            .putString(KEY_LINES, la.toString())
            .apply()
    }
}
