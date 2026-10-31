package com.tamanna.enterprise.partner

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Partner(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val shareAmount: Double,
    val profitPercentage: Double,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class PartnerRateSnapshot(
    val effectiveDate: String,
    val percentages: Map<Long, Double>
)

object PartnerStorage {
    private const val PREFS = "tamanna_partner_storage"
    private const val KEY_PARTNERS = "partners"
    private const val KEY_DELETED_PARTNERS = "deleted_partner_ids"
    private const val KEY_RATE_HISTORY = "partner_profit_rate_history"

    private fun prefs(context: Context) = BusinessStorage.prefs(context, PREFS)

    fun getPartners(context: Context): List<Partner> {
        val raw = prefs(context).getString(KEY_PARTNERS, "[]") ?: "[]"
        return runCatching {
            val deleted = deletedIds(context)
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val partnerId = obj.getLong("id")
                    if (partnerId in deleted) continue
                    add(Partner(
                        id = partnerId,
                        name = obj.getString("name"),
                        shareAmount = obj.getDouble("shareAmount"),
                        profitPercentage = obj.getDouble("profitPercentage"),
                        note = obj.optString("note", ""),
                        updatedAt = obj.optLong("updatedAt", 0L)
                    ))
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.id }
    }

    fun addPartner(context: Context, partner: Partner): Boolean {
        if (partner.name.isBlank() || partner.shareAmount < 0 || partner.profitPercentage != 0.0) return false
        val list = getPartners(context).toMutableList()
        if (list.any { it.id == partner.id }) return false
        clearDeletedId(context, partner.id)
        list.add(0, partner)
        return savePartners(context, list)
    }

    fun updatePartner(context: Context, partner: Partner): Boolean {
        if (partner.name.isBlank() || partner.shareAmount < 0) return false
        val list = getPartners(context)
        if (list.none { it.id == partner.id }) return false
        val updated = list.map {
            if (it.id == partner.id) partner.copy(
                profitPercentage = it.profitPercentage,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        return savePartners(context, updated)
    }

    fun saveProfitDistribution(
        context: Context,
        percentages: Map<Long, Double>,
        effectiveDate: String
    ): Boolean {
        val partners = getPartners(context)
        if (partners.isEmpty() || !isValidDate(effectiveDate)) return false
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        // A new rate may start today or later, never rewrite a closed period.
        if (effectiveDate < today) return false
        if (percentages.keys != partners.map { it.id }.toSet()) return false
        if (percentages.values.any { !it.isFinite() || it < 0.0 || it > 100.0 }) return false
        val total = percentages.values.sum()
        if (kotlin.math.abs(total - 100.0) > 0.0001) return false

        val now = System.currentTimeMillis()
        val updatedPartners = partners.map { partner ->
            partner.copy(profitPercentage = percentages[partner.id] ?: 0.0, updatedAt = now)
        }
        val existing = getRateHistory(context).toMutableList()
        if (existing.isEmpty()) {
            // Legacy records had no rate history. Preserve their existing split
            // for all dates before the first explicitly configured change.
            existing.add(PartnerRateSnapshot("0001-01-01", partners.associate { it.id to it.profitPercentage }))
        }
        existing.removeAll { it.effectiveDate == effectiveDate }
        existing.add(PartnerRateSnapshot(effectiveDate, percentages.toMap()))
        val normalized = existing.sortedBy { it.effectiveDate }
        return runCatching {
            val history = JSONArray()
            normalized.forEach { snapshot ->
                val rates = JSONObject()
                snapshot.percentages.forEach { (id, pct) -> rates.put(id.toString(), pct) }
                history.put(JSONObject().put("effectiveDate", snapshot.effectiveDate).put("percentages", rates))
            }
            val partnerArray = partnerArray(updatedPartners)
            prefs(context).edit()
                .putString(KEY_PARTNERS, partnerArray.toString())
                .putString(KEY_RATE_HISTORY, history.toString())
                .commit()
        }.getOrDefault(false)
    }

    fun getRateHistory(context: Context): List<PartnerRateSnapshot> {
        val raw = prefs(context).getString(KEY_RATE_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val rates = item.optJSONObject("percentages") ?: JSONObject()
                    val values = mutableMapOf<Long, Double>()
                    val keys = rates.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        key.toLongOrNull()?.let { values[it] = rates.optDouble(key, 0.0) }
                    }
                    add(PartnerRateSnapshot(item.optString("effectiveDate"), values))
                }
            }.sortedBy { it.effectiveDate }
        }.getOrDefault(emptyList())
    }

    fun getPercentageForDate(context: Context, partnerId: Long, date: String): Double {
        val history = getRateHistory(context)
        val snapshot = history.lastOrNull { it.effectiveDate <= date }
        if (snapshot != null) return snapshot.percentages[partnerId] ?: 0.0
        return getPartners(context).firstOrNull { it.id == partnerId }?.profitPercentage ?: 0.0
    }

    fun deletePartner(context: Context, id: Long, effectiveDate: String): Boolean {
        val partners = getPartners(context)
        val remaining = partners.filterNot { it.id == id }
        if (remaining.size == partners.size || remaining.isEmpty()) return false
        if (kotlin.math.abs(remaining.sumOf { it.profitPercentage } - 100.0) > 0.0001) return false
        val history = getRateHistory(context).toMutableList()
        if (history.isEmpty()) {
            history.add(PartnerRateSnapshot("0001-01-01", partners.associate { it.id to it.profitPercentage }))
        }
        history.removeAll { it.effectiveDate == effectiveDate }
        history.add(PartnerRateSnapshot(effectiveDate, remaining.associate { it.id to it.profitPercentage }))
        val savedHistory = saveRateHistory(context, history)
        if (!savedHistory) return false
        addDeletedId(context, id)
        return savePartners(context, remaining)
    }

    private fun isValidDate(value: String): Boolean = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = format.parse(value) ?: return@runCatching false
        format.format(parsed) == value
    }.getOrDefault(false)

    private fun saveRateHistory(context: Context, history: List<PartnerRateSnapshot>): Boolean =
        runCatching {
            val array = JSONArray()
            history.sortedBy { it.effectiveDate }.forEach { snapshot ->
                val rates = JSONObject()
                snapshot.percentages.forEach { (id, pct) -> rates.put(id.toString(), pct) }
                array.put(JSONObject().put("effectiveDate", snapshot.effectiveDate).put("percentages", rates))
            }
            prefs(context).edit().putString(KEY_RATE_HISTORY, array.toString()).commit()
        }.getOrDefault(false)

    private fun savePartners(context: Context, list: List<Partner>): Boolean =
        runCatching { prefs(context).edit().putString(KEY_PARTNERS, partnerArray(list).toString()).commit() }.getOrDefault(false)

    private fun partnerArray(list: List<Partner>) = JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("shareAmount", item.shareAmount)
                put("profitPercentage", item.profitPercentage)
                put("note", item.note)
                put("updatedAt", item.updatedAt)
            })
        }
    }

    private fun deletedIds(context: Context): Set<Long> {
        val raw = prefs(context).getString(KEY_DELETED_PARTNERS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildSet { for (i in 0 until array.length()) add(array.getLong(i)) }
        }.getOrDefault(emptySet())
    }

    private fun addDeletedId(context: Context, id: Long) {
        val ids = deletedIds(context).toMutableSet()
        ids.add(id)
        saveDeletedIds(context, ids)
    }

    private fun clearDeletedId(context: Context, id: Long) {
        val ids = deletedIds(context).toMutableSet()
        if (ids.remove(id)) saveDeletedIds(context, ids)
    }

    private fun saveDeletedIds(context: Context, ids: Set<Long>) {
        val array = JSONArray()
        ids.sorted().forEach { array.put(it) }
        prefs(context).edit().putString(KEY_DELETED_PARTNERS, array.toString()).apply()
    }
}
