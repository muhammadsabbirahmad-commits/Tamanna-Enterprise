package com.tamanna.enterprise.partner

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class PartnerInvestment(
    val id: Long,
    val partnerId: Long,
    val partnerName: String,
    val amount: Double,
    val date: String,
    val transactionId: String,
    val note: String = ""
)

object PartnerInvestmentStorage {
    private const val PREFS = "tamanna_enterprise_partner_investments"
    private const val KEY = "investments"

    private fun prefs(context: Context) = BusinessStorage.prefs(context, PREFS)

    fun getInvestments(context: Context): List<PartnerInvestment> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(
                        PartnerInvestment(
                            o.optLong("id"),
                            o.optLong("partnerId"),
                            o.optString("partnerName"),
                            o.optDouble("amount", 0.0),
                            o.optString("date"),
                            o.optString("transactionId"),
                            o.optString("note")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addInvestment(context: Context, investment: PartnerInvestment): Boolean {
        if (investment.partnerId <= 0 || investment.amount <= 0 ||
            investment.date.isBlank() || investment.transactionId.isBlank()
        ) return false

        val a = JSONArray(
            prefs(context).getString(KEY, "[]") ?: "[]"
        )
        a.put(JSONObject().apply {
            put("id", investment.id)
            put("partnerId", investment.partnerId)
            put("partnerName", investment.partnerName)
            put("amount", investment.amount)
            put("date", investment.date)
            put("transactionId", investment.transactionId)
            put("note", investment.note)
        })
        prefs(context).edit().putString(KEY, a.toString()).apply()
        return true
    }

    fun totalInvestment(context: Context, partnerId: Long): Double =
        getInvestments(context).filter { it.partnerId == partnerId }.sumOf { it.amount }

    fun totalBusinessInvestment(context: Context): Double =
        getInvestments(context).sumOf { it.amount }

    fun transactionExists(context: Context, transactionId: String): Boolean =
        getInvestments(context).any { it.transactionId.equals(transactionId.trim(), true) }
}
