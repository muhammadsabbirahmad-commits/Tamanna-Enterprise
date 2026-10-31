package com.tamanna.enterprise.partner

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Partner(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val shareAmount: Double,
    val profitPercentage: Double,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

object PartnerStorage {
    private const val PREFS = "tamanna_partner_storage"
    private const val KEY_PARTNERS = "partners"
    private const val KEY_DELETED_PARTNERS = "deleted_partner_ids"

    fun getPartners(context: Context): List<Partner> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY_PARTNERS, "[]") ?: "[]"
        return runCatching {
            val deleted = deletedIds(context)
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val partnerId = obj.getLong("id")
                    if (partnerId in deleted) continue
                    add(
                        Partner(
                            id = partnerId,
                            name = obj.getString("name"),
                            shareAmount = obj.getDouble("shareAmount"),
                            profitPercentage = obj.getDouble("profitPercentage"),
                            note = obj.optString("note", ""),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.id }
    }

    fun addPartner(context: Context, partner: Partner): Boolean {
        if (partner.name.isBlank() || partner.shareAmount < 0 || partner.profitPercentage < 0) return false
        val list = getPartners(context).toMutableList()
        clearDeletedId(context, partner.id)
        list.add(0, partner)
        return savePartners(context, list)
    }

    fun updatePartner(context: Context, partner: Partner): Boolean {
        if (partner.name.isBlank() || partner.shareAmount < 0 || partner.profitPercentage < 0) return false
        val list = getPartners(context)
        clearDeletedId(context, partner.id)
        val updated = list.map { if (it.id == partner.id) partner.copy(updatedAt = System.currentTimeMillis()) else it }
        return savePartners(context, updated)
    }

    fun deletePartner(context: Context, id: Long): Boolean {
        val list = getPartners(context)
        val updated = list.filterNot { it.id == id }
        if (updated.size == list.size) return false
        addDeletedId(context, id)
        return savePartners(context, updated)
    }

    private fun savePartners(context: Context, list: List<Partner>): Boolean {
        val array = JSONArray()
        list.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("shareAmount", item.shareAmount)
                put("profitPercentage", item.profitPercentage)
                put("note", item.note)
                put("updatedAt", item.updatedAt)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit().putString(KEY_PARTNERS, array.toString()).apply()
        return true
    }
    private fun deletedIds(context: Context): Set<Long> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY_DELETED_PARTNERS, "[]") ?: "[]"
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
        BusinessStorage.prefs(context, PREFS).edit()
            .putString(KEY_DELETED_PARTNERS, array.toString())
            .apply()
    }

}
