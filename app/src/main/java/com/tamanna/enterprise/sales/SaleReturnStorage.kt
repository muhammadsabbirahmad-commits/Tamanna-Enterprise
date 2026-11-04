package com.tamanna.enterprise.sales

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
            .groupBy { it.productCode.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

    fun getReturnedAmount(context: Context, transactionId: String): Double =
        getReturns(context).filter { it.transactionId == transactionId }.sumOf { it.amount }

    fun getDueReduction(context: Context, transactionId: String): Double =
        getReturns(context).filter { it.transactionId == transactionId }.sumOf { it.dueReduction }

    /**
     * Rechecks the original sold quantities against every previously saved return.
     * The check is performed again inside addReturn, not only by the UI, so a
     * stale screen or a modified request cannot return more than was purchased.
     */
    fun canAddReturn(
        context: Context,
        transactionId: String,
        lines: List<SaleReturnLine>,
        replacingReturnId: String? = null
    ): Boolean {
        if (transactionId.isBlank() || lines.isEmpty()) return false
        if (lines.any {
                it.transactionId != transactionId ||
                    it.quantity <= 0 ||
                    it.productCode.isBlank() ||
                    !it.unitPrice.isFinite() ||
                    it.unitPrice < 0.0
            }
        ) return false

        val soldByCode = SalesStorage.getSales(context)
            .filter { it.transactionId == transactionId }
            .groupBy { it.productCode.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, sales) -> sales.sumOf { it.quantity.coerceAtLeast(0) } }
        if (soldByCode.isEmpty()) return false

        val alreadyReturned = getLines(context)
            .filter { it.transactionId == transactionId && it.returnId != replacingReturnId }
            .groupBy { it.productCode.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, returnedLines) -> returnedLines.sumOf { it.quantity } }

        val requested = lines.groupBy { it.productCode.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, requestedLines) -> requestedLines.sumOf { it.quantity } }

        return requested.all { (code, quantity) ->
            val sold = soldByCode[code] ?: return@all false
            val previous = alreadyReturned[code] ?: 0
            previous >= 0 && quantity <= sold - previous
        }
    }

    @Synchronized
    fun addReturn(context: Context, item: SaleReturn, lines: List<SaleReturnLine>): Boolean {
        // Return IDs are append-only; an existing record must never be silently replaced.
        if (getReturns(context).any { it.returnId == item.returnId }) return false
        if (item.returnId.isBlank() || item.transactionId.isBlank() ||
            !item.amount.isFinite() || item.amount < 0.0 ||
            !item.dueReduction.isFinite() || item.dueReduction < 0.0 ||
            !item.refundAmount.isFinite() || item.refundAmount < 0.0 ||
            !canAddReturn(context, item.transactionId, lines)
        ) return false

        val returns = getReturns(context) + item
        val allLines = getLines(context) + lines
        save(context, returns, allLines)
        return getReturns(context).any { it.returnId == item.returnId } &&
            getLines(context).count { it.returnId == item.returnId } == lines.size
    }

    fun removeReturn(context: Context, returnId: String): Boolean {
        save(
            context,
            getReturns(context).filterNot { it.returnId == returnId },
            getLines(context).filterNot { it.returnId == returnId }
        )
        return getReturns(context).none { it.returnId == returnId } &&
            getLines(context).none { it.returnId == returnId }
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
