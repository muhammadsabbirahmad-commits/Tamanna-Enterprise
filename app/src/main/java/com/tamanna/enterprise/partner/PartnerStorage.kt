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

data class PartnerProfitWithdrawal(
    val id: String,
    val partnerId: Long,
    val amount: Double,
    val date: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


data class PartnerCapitalContribution(
    val id: String,
    val partnerId: Long,
    val amount: Double,
    val date: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approximateDate: Boolean = false
)

data class PartnerRateSnapshot(
    val effectiveDate: String,
    val percentages: Map<Long, Double>,
    val updatedAt: Long = 0L
)

object PartnerStorage {
    private const val PREFS = "tamanna_partner_storage"
    private const val KEY_PARTNERS = "partners"
    private const val KEY_DELETED_PARTNERS = "deleted_partner_ids"
    private const val KEY_RATE_HISTORY = "partner_profit_rate_history"
    private const val KEY_WITHDRAWALS = "partner_profit_withdrawals"
    private const val KEY_CAPITAL_CONTRIBUTIONS = "partner_capital_contributions"

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
        getCapitalContributions(context) // Migrate existing partners before adding this new one.
        if (partner.name.isBlank() || partner.shareAmount < 0 || partner.profitPercentage != 0.0) return false
        val existing = getPartners(context)
        if (existing.any { it.id == partner.id }) return false
        clearDeletedId(context, partner.id)
        val updatedPartners = listOf(partner) + existing
        val contributions = getCapitalContributions(context).toMutableList()
        if (partner.shareAmount > 0.0) contributions += PartnerCapitalContribution(
            java.util.UUID.randomUUID().toString(), partner.id, partner.shareAmount,
            todayDate(), "প্রাথমিক মূলধন জমা"
        )
        return saveCapitalAndPartners(context, contributions, updatedPartners)
    }

    fun updatePartner(context: Context, partner: Partner): Boolean {
        getCapitalContributions(context) // Preserve old capital before calculating an increase.
        if (partner.name.isBlank() || partner.shareAmount < 0) return false
        val existing = getPartners(context)
        val previous = existing.firstOrNull { it.id == partner.id } ?: return false
        // Capital reductions need a separately recorded capital-return transaction.
        if (partner.shareAmount + 0.004 < previous.shareAmount) return false
        val updatedPartners = existing.map {
            if (it.id == partner.id) partner.copy(
                profitPercentage = it.profitPercentage,
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        val contributions = getCapitalContributions(context).toMutableList()
        val addedCapital = partner.shareAmount - previous.shareAmount
        if (addedCapital > 0.004) contributions += PartnerCapitalContribution(
            java.util.UUID.randomUUID().toString(), partner.id, addedCapital,
            todayDate(), "পার্টনারের অতিরিক্ত মূলধন"
        )
        return saveCapitalAndPartners(context, contributions, updatedPartners)
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
        existing.add(PartnerRateSnapshot(effectiveDate, percentages.toMap(), now))
        val normalized = existing.sortedBy { it.effectiveDate }
        return runCatching {
            val history = JSONArray()
            normalized.forEach { snapshot ->
                val rates = JSONObject()
                snapshot.percentages.forEach { (id, pct) -> rates.put(id.toString(), pct) }
                history.put(JSONObject().put("effectiveDate", snapshot.effectiveDate).put("percentages", rates).put("updatedAt", snapshot.updatedAt))
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
                    add(PartnerRateSnapshot(item.optString("effectiveDate"), values, item.optLong("updatedAt", 0L)))
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


    fun getCapitalContributions(context: Context): List<PartnerCapitalContribution> {
        val raw = prefs(context).getString(KEY_CAPITAL_CONTRIBUTIONS, null)
        if (raw.isNullOrBlank()) {
            // Existing installations have only a current shareAmount. Preserve it as a
            // clearly marked legacy estimate; never pretend its exact deposit date is known.
            val legacy = getPartners(context).filter { it.shareAmount > 0.0 }.map { partner ->
                PartnerCapitalContribution(
                    id = "legacy-${partner.id}", partnerId = partner.id,
                    amount = partner.shareAmount, date = dateFromTimestamp(partner.id),
                    note = "পুরোনো রেকর্ড: জমার প্রকৃত তারিখ সংরক্ষিত ছিল না",
                    createdAt = partner.id, approximateDate = true
                )
            }
            saveCapitalContributions(context, legacy)
            return legacy
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(PartnerCapitalContribution(
                        id = item.optString("id"), partnerId = item.optLong("partnerId"),
                        amount = item.optDouble("amount"), date = item.optString("date"),
                        note = item.optString("note", ""), createdAt = item.optLong("createdAt"),
                        approximateDate = item.optBoolean("approximateDate", false)
                    ))
                }
            }
        }.getOrDefault(emptyList()).sortedWith(compareBy<PartnerCapitalContribution> { it.date }.thenBy { it.createdAt })
    }

    fun addCapitalContribution(context: Context, partnerId: Long, amount: Double, date: String, note: String = ""): Boolean {
        if (!amount.isFinite() || amount <= 0.0 || !isValidDate(date)) return false
        val partners = getPartners(context)
        if (partners.none { it.id == partnerId }) return false
        val updatedPartners = partners.map { partner ->
            if (partner.id == partnerId) partner.copy(shareAmount = partner.shareAmount + amount, updatedAt = System.currentTimeMillis()) else partner
        }
        val contributions = getCapitalContributions(context) + PartnerCapitalContribution(
            java.util.UUID.randomUUID().toString(), partnerId, amount, date, note.trim()
        )
        return saveCapitalAndPartners(context, contributions, updatedPartners)
    }

    private fun saveCapitalContributions(context: Context, items: List<PartnerCapitalContribution>): Boolean =
        runCatching { prefs(context).edit().putString(KEY_CAPITAL_CONTRIBUTIONS, capitalArray(items).toString()).commit() }.getOrDefault(false)

    private fun saveCapitalAndPartners(
        context: Context,
        contributions: List<PartnerCapitalContribution>,
        partners: List<Partner>
    ): Boolean = runCatching {
        prefs(context).edit()
            .putString(KEY_CAPITAL_CONTRIBUTIONS, capitalArray(contributions).toString())
            .putString(KEY_PARTNERS, partnerArray(partners).toString())
            .commit()
    }.getOrDefault(false)

    private fun capitalArray(items: List<PartnerCapitalContribution>) = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().put("id", item.id).put("partnerId", item.partnerId)
                .put("amount", item.amount).put("date", item.date).put("note", item.note)
                .put("createdAt", item.createdAt).put("updatedAt", item.createdAt).put("approximateDate", item.approximateDate))
        }
    }

    private fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dateFromTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    fun getWithdrawals(context: Context): List<PartnerProfitWithdrawal> {
        val raw = prefs(context).getString(KEY_WITHDRAWALS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(PartnerProfitWithdrawal(
                        id = item.optString("id"),
                        partnerId = item.optLong("partnerId"),
                        amount = item.optDouble("amount"),
                        date = item.optString("date"),
                        note = item.optString("note", ""),
                        createdAt = item.optLong("createdAt")
                    ))
                }
            }
        }.getOrDefault(emptyList()).sortedWith(compareBy<PartnerProfitWithdrawal> { it.date }.thenBy { it.createdAt })
    }

    fun addWithdrawal(context: Context, partnerId: Long, amount: Double, date: String, note: String = ""): Boolean {
        if (getPartners(context).none { it.id == partnerId }) return false
        if (!amount.isFinite() || amount <= 0.0 || !isValidDate(date)) return false
        val updated = getWithdrawals(context) + PartnerProfitWithdrawal(
            java.util.UUID.randomUUID().toString(), partnerId, amount, date, note.trim()
        )
        return runCatching {
            val array = JSONArray()
            updated.forEach { item ->
                array.put(JSONObject()
                    .put("id", item.id).put("partnerId", item.partnerId)
                    .put("amount", item.amount).put("date", item.date)
                    .put("note", item.note).put("createdAt", item.createdAt))
            }
            prefs(context).edit().putString(KEY_WITHDRAWALS, array.toString()).commit()
        }.getOrDefault(false)
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
                array.put(JSONObject().put("effectiveDate", snapshot.effectiveDate).put("percentages", rates).put("updatedAt", snapshot.updatedAt))
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
