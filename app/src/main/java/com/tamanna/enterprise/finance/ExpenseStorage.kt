package com.tamanna.enterprise.finance

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.partner.PartnerStorage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

data class PartnerExpenseAllocation(
    val partnerId: Long,
    val partnerName: String,
    val percentage: Double,
    val amount: Double
)

data class Expense(
    val id: Long,
    val date: String,
    val category: String,
    val amount: Double,
    val note: String,
    val allocations: List<PartnerExpenseAllocation> = emptyList(),
    val allocationEstimated: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class DamageRecord(
    val id: Long,
    val date: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val unitCost: Double,
    val reason: String
) {
    val totalLoss get() = quantity * unitCost
}

object ExpenseStorage {
    private const val P = "tamanna_enterprise_finance"
    private const val E = "expenses"
    private const val D = "damages"

    private fun prefs(c: Context) = BusinessStorage.prefs(c, P)
    private fun array(c: Context, key: String) = JSONArray(prefs(c).getString(key, "[]") ?: "[]")
    private fun save(c: Context, key: String, value: JSONArray): Boolean =
        prefs(c).edit().putString(key, value.toString()).commit()

    private fun moneyCents(value: Double): Long =
        (value * 100.0).let { kotlin.math.round(it).toLong() }

    private fun validDate(value: String): Boolean = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = format.parse(value) ?: return@runCatching false
        format.format(parsed) == value
    }.getOrDefault(false)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Calculates a fixed, per-expense allocation snapshot using the profit-rate
     * history effective on the expense date. Largest-remainder rounding makes
     * the partner amounts add up to the exact expense amount in paisa.
     */
    private fun buildAllocations(
        context: Context,
        date: String,
        amount: Double,
        allowEstimatedLegacyRate: Boolean
    ): Pair<List<PartnerExpenseAllocation>, Boolean>? {
        if (!validDate(date) || !amount.isFinite() || amount <= 0.0) return null
        val partners = PartnerStorage.getPartners(context)
        if (partners.isEmpty()) return null

        val snapshot = PartnerStorage.getRateHistory(context)
            .lastOrNull { it.effectiveDate <= date }
        val rates = snapshot?.percentages
            ?: partners.associate { it.id to it.profitPercentage }
        if (snapshot == null && date < today() && !allowEstimatedLegacyRate) return null
        if (rates.isEmpty() || rates.values.any { !it.isFinite() || it < 0.0 || it > 100.0 }) return null
        if (kotlin.math.abs(rates.values.sum() - 100.0) > 0.0001) return null

        val activeById = partners.associateBy { it.id }
        val orderedRates = rates.toList().sortedBy { it.first }
        val totalCents = moneyCents(amount)
        if (totalCents <= 0L) return null

        val rawShares = orderedRates.map { (id, percentage) ->
            val raw = totalCents.toDouble() * percentage / 100.0
            Triple(id, percentage, raw)
        }
        val baseShares = rawShares.map { floor(it.third).toLong() }
        var remaining = totalCents - baseShares.sum()
        val remainderOrder = rawShares.indices.sortedWith(
            compareByDescending<Int> { rawShares[it].third - baseShares[it] }
                .thenBy { rawShares[it].first }
        )
        val centsByPartner = baseShares.toMutableList()
        var index = 0
        while (remaining > 0L && remainderOrder.isNotEmpty()) {
            centsByPartner[remainderOrder[index % remainderOrder.size]] += 1L
            remaining--
            index++
        }

        val allocations = rawShares.mapIndexed { i, item ->
            val partner = activeById[item.first]
            PartnerExpenseAllocation(
                partnerId = item.first,
                partnerName = partner?.name ?: "পূর্বতন পার্টনার (#${item.first})",
                percentage = item.second,
                amount = centsByPartner[i] / 100.0
            )
        }
        if (allocations.sumOf { moneyCents(it.amount) } != totalCents) return null
        return allocations to (snapshot == null && date < today())
    }

    private fun allocationArray(items: List<PartnerExpenseAllocation>) = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject()
                .put("partnerId", item.partnerId)
                .put("partnerName", item.partnerName)
                .put("percentage", item.percentage)
                .put("amount", item.amount))
        }
    }

    private fun parseAllocations(raw: JSONArray?): List<PartnerExpenseAllocation> {
        if (raw == null) return emptyList()
        return buildList {
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i) ?: continue
                add(PartnerExpenseAllocation(
                    partnerId = item.optLong("partnerId"),
                    partnerName = item.optString("partnerName"),
                    percentage = item.optDouble("percentage", 0.0),
                    amount = item.optDouble("amount", 0.0)
                ))
            }
        }
    }

    private fun expenseJson(x: Expense) = JSONObject().apply {
        put("id", x.id)
        put("date", x.date)
        put("category", x.category)
        put("amount", x.amount)
        put("note", x.note)
        put("allocations", allocationArray(x.allocations))
        put("allocationEstimated", x.allocationEstimated)
        put("updatedAt", x.updatedAt)
    }

    /**
     * Backfills old expenses once. If the old rate history is missing, the
     * current split is used only as an explicitly marked estimate; it is never
     * presented as a confirmed historical rate.
     */
    private fun migrateLegacyAllocations(context: Context): JSONArray {
        val expenses = array(context, E)
        var changed = false
        for (i in 0 until expenses.length()) {
            val item = expenses.optJSONObject(i) ?: continue
            if (item.has("allocations")) continue
            val allocation = buildAllocations(
                context,
                item.optString("date"),
                item.optDouble("amount", 0.0),
                allowEstimatedLegacyRate = true
            )
            if (allocation != null) {
                item.put("allocations", allocationArray(allocation.first))
                item.put("allocationEstimated", allocation.second)
                item.put("updatedAt", item.optLong("updatedAt", item.optLong("id", 0L)))
                changed = true
            }
        }
        if (changed) save(context, E, expenses)
        return expenses
    }

    fun getExpenses(c: Context): List<Expense> {
        val items = migrateLegacyAllocations(c)
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                add(Expense(
                    id = item.optLong("id"),
                    date = item.optString("date"),
                    category = item.optString("category"),
                    amount = item.optDouble("amount"),
                    note = item.optString("note"),
                    allocations = parseAllocations(item.optJSONArray("allocations")),
                    allocationEstimated = item.optBoolean("allocationEstimated", false),
                    updatedAt = item.optLong("updatedAt", item.optLong("id", 0L))
                ))
            }
        }
    }

    /** New expenses are rejected unless the effective partner split is exactly 100%. */
    fun addExpense(c: Context, x: Expense): Boolean {
        if (x.id <= 0L || !x.amount.isFinite() || x.amount <= 0.0 || !validDate(x.date)) return false
        val allocated = buildAllocations(c, x.date, x.amount, allowEstimatedLegacyRate = false) ?: return false
        val items = array(c, E)
        if ((0 until items.length()).any { items.optJSONObject(it)?.optLong("id") == x.id }) return false
        items.put(expenseJson(x.copy(
            allocations = allocated.first,
            allocationEstimated = allocated.second,
            updatedAt = System.currentTimeMillis()
        )))
        return save(c, E, items)
    }

    /** Editing amount/date replaces the allocation snapshot atomically with the expense. */
    fun updateExpense(c: Context, x: Expense): Boolean {
        if (!x.amount.isFinite() || x.amount <= 0.0 || !validDate(x.date)) return false
        val allocated = buildAllocations(c, x.date, x.amount, allowEstimatedLegacyRate = false) ?: return false
        val items = array(c, E)
        var found = false
        for (i in 0 until items.length()) {
            val old = items.optJSONObject(i) ?: continue
            if (old.optLong("id") == x.id) {
                items.put(i, expenseJson(x.copy(
                    allocations = allocated.first,
                    allocationEstimated = allocated.second,
                    updatedAt = System.currentTimeMillis()
                )))
                found = true
                break
            }
        }
        return found && save(c, E, items)
    }

    fun getPartnerExpenseTotal(
        c: Context,
        partnerId: Long,
        fromDate: String? = null,
        toDate: String? = null
    ): Double = getExpenses(c)
        .asSequence()
        .filter { (fromDate == null || it.date >= fromDate) && (toDate == null || it.date <= toDate) }
        .flatMap { it.allocations.asSequence() }
        .filter { it.partnerId == partnerId }
        .sumOf { it.amount }

    fun getUnallocatedExpenseTotal(c: Context): Double = getExpenses(c)
        .filter { expense ->
            expense.allocations.isEmpty() ||
                expense.allocations.sumOf { moneyCents(it.amount) } != moneyCents(expense.amount)
        }
        .sumOf { it.amount }

    fun getEstimatedAllocationExpenseTotal(c: Context): Double = getExpenses(c)
        .filter { it.allocationEstimated }
        .sumOf { it.amount }

    fun getDamages(c: Context) = buildList {
        val items = array(c, D)
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            add(DamageRecord(
                item.optLong("id"), item.optString("date"), item.optString("productCode"),
                item.optString("productName"), item.optInt("quantity"),
                item.optDouble("unitCost"), item.optString("reason")
            ))
        }
    }

    fun addDamage(c: Context, x: DamageRecord) {
        val items = array(c, D)
        items.put(JSONObject().apply {
            put("id", x.id)
            put("date", x.date)
            put("productCode", x.productCode)
            put("productName", x.productName)
            put("quantity", x.quantity)
            put("unitCost", x.unitCost)
            put("reason", x.reason)
        })
        save(c, D, items)
    }
}
