package com.tamanna.enterprise.reports

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ManualCashAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val balance: Double
)

object CashAccountStorage {
    private const val PREFS = "tamanna_manual_cash_accounts"
    private const val KEY_ACCOUNTS = "accounts"

    fun getAccounts(context: Context): List<ManualCashAccount> {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY_ACCOUNTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(ManualCashAccount(item.optString("id"), item.optString("name"), item.optDouble("balance")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccount(context: Context, account: ManualCashAccount): Boolean {
        if (account.name.isBlank() || !account.balance.isFinite() || account.balance < 0.0) return false
        val items = getAccounts(context).toMutableList()
        val index = items.indexOfFirst { it.id == account.id }
        if (index >= 0) items[index] = account else items.add(account)
        return save(context, items)
    }

    fun deleteAccount(context: Context, id: String): Boolean =
        save(context, getAccounts(context).filterNot { it.id == id })

    private fun save(context: Context, items: List<ManualCashAccount>): Boolean = runCatching {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("balance", it.balance)) }
        BusinessStorage.prefs(context, PREFS).edit().putString(KEY_ACCOUNTS, array.toString()).commit()
    }.getOrDefault(false)
}
