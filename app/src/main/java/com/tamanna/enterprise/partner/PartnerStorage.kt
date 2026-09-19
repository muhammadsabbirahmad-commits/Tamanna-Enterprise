package com.tamanna.enterprise.partner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class Partner(
    val id: Long,
    val name: String,
    val investment: Double,
    val percentage: Double
)

object PartnerStorage {
    private const val PREFS = "tamanna_enterprise_partners"
    private const val KEY_PARTNERS = "partners"

    fun getPartners(context: Context): List<Partner> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PARTNERS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        Partner(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            investment = o.optDouble("investment"),
                            percentage = o.optDouble("percentage")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun savePartners(context: Context, partners: List<Partner>) {
        val array = JSONArray()
        partners.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("investment", it.investment)
                    put("percentage", it.percentage)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PARTNERS, array.toString()).apply()
    }

    fun addPartner(context: Context, name: String, investment: Double, percentage: Double): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || investment < 0 || percentage < 0) return false
        val partners = getPartners(context).toMutableList()
        partners.add(Partner(System.currentTimeMillis(), cleanName, investment, percentage))
        savePartners(context, partners)
        return true
    }

    fun deletePartner(context: Context, id: Long) {
        savePartners(context, getPartners(context).filterNot { it.id == id })
    }

    fun totalInvestment(context: Context) = getPartners(context).sumOf { it.investment }
    fun totalPercentage(context: Context) = getPartners(context).sumOf { it.percentage }
    fun profitShare(profit: Double, partner: Partner): Double =
        profit * partner.percentage / 100.0
}
