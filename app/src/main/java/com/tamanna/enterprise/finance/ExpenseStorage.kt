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
    val reason: String,
    val allocations: List<PartnerExpenseAllocation> = emptyList(),
    val allocationEstimated: Boolean = false
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
     * Backfills old expenses only when the effective historical rate is known.
     * Missing historical rate data remains unallocated and is surfaced for review.
     */
    private fun isValidAllocation(items: List<PartnerExpenseAllocation>, amount: Double): Boolean {
        if (items.isEmpty() || !amount.isFinite() || amount <= 0.0) return false
        if (items.any {
                it.partnerId <= 0L || !it.percentage.isFinite() || it.percentage < 0.0 ||
                    !it.amount.isFinite() || it.amount < 0.0
            }) return false
        if (items.map { it.partnerId }.distinct().size != items.size) return false
        return kotlin.math.abs(items.sumOf { it.percentage } - 100.0) <= 0.0001 &&
            items.sumOf { moneyCents(it.amount) } == moneyCents(amount)
    }

    private fun migrateLegacyAllocations(context: Context): JSONArray {
        val expenses = array(context, E)
        var changed = false
        for (i in 0 until expenses.length()) {
            val item = expenses.optJSONObject(i) ?: continue
            val amount = item.optDouble("amount", 0.0)
            val existing = parseAllocations(item.optJSONArray("allocations"))
            // Some older entries already contain an empty/incomplete allocations array.
            // Repair those too; only a complete 100% snapshot is considered final.
            if (isValidAllocation(existing, amount)) continue
            val allocation = buildAllocations(
                context,
                item.optString("date"),
                amount,
                allowEstimatedLegacyRate = false
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
        val normalizedAmount = moneyCents(x.amount) / 100.0
        if (normalizedAmount <= 0.0) return false
        val allocated = buildAllocations(c, x.date, normalizedAmount, allowEstimatedLegacyRate = false) ?: return false
        val items = array(c, E)
        if ((0 until items.length()).any { items.optJSONObject(it)?.optLong("id") == x.id }) return false
        items.put(expenseJson(x.copy(
            amount = normalizedAmount,
            allocations = allocated.first,
            allocationEstimated = allocated.second,
            updatedAt = System.currentTimeMillis()
        )))
        return save(c, E, items)
    }

    /** Editing amount/date replaces the allocation snapshot atomically with the expense. */
    fun updateExpense(c: Context, x: Expense): Boolean {
        if (!x.amount.isFinite() || x.amount <= 0.0 || !validDate(x.date)) return false
        val normalizedAmount = moneyCents(x.amount) / 100.0
        if (normalizedAmount <= 0.0) return false
        val items = array(c, E)
        var found = false
        for (i in 0 until items.length()) {
            val old = items.optJSONObject(i) ?: continue
            if (old.optLong("id") == x.id) {
                val oldAllocations = parseAllocations(old.optJSONArray("allocations"))
                val sameFinancialValue = old.optString("date") == x.date &&
                    moneyCents(old.optDouble("amount", 0.0)) == moneyCents(normalizedAmount)
                val allocated = if (sameFinancialValue && isValidAllocation(oldAllocations, normalizedAmount))
                    oldAllocations to old.optBoolean("allocationEstimated", false)
                else buildAllocations(c, x.date, normalizedAmount, allowEstimatedLegacyRate = false) ?: return false
                items.put(i, expenseJson(x.copy(
                    amount = normalizedAmount,
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
    ): Double {
        val ordinary = getExpenses(c).asSequence()
            .filter { (fromDate == null || it.date >= fromDate) && (toDate == null || it.date <= toDate) }
            .flatMap { it.allocations.asSequence() }
            .filter { it.partnerId == partnerId }
            .sumOf { it.amount }
        val damage = getDamages(c).asSequence()
            .filter { (fromDate == null || it.date >= fromDate) && (toDate == null || it.date <= toDate) }
            .flatMap { it.allocations.asSequence() }
            .filter { it.partnerId == partnerId }
            .sumOf { it.amount }
        return ordinary + damage
    }

    private fun isValidDamageAllocation(damage: DamageRecord): Boolean =
        isValidAllocation(damage.allocations, damage.totalLoss)

    fun getUnallocatedExpenseTotal(c: Context): Double {
        val ordinary = getExpenses(c).filterNot { isValidAllocation(it.allocations, it.amount) }.sumOf { it.amount }
        val damage = getDamages(c).filterNot(::isValidDamageAllocation).sumOf { it.totalLoss }
        return ordinary + damage
    }

    fun getEstimatedAllocationExpenseTotal(c: Context): Double {
        val ordinary = getExpenses(c).filter { it.allocationEstimated }.sumOf { it.amount }
        val damage = getDamages(c).filter { it.allocationEstimated }.sumOf { it.totalLoss }
        return ordinary + damage
    }

    private fun damageJson(x: DamageRecord) = JSONObject().apply {
        put("id", x.id)
        put("date", x.date)
        put("productCode", x.productCode)
        put("productName", x.productName)
        put("quantity", x.quantity)
        put("unitCost", x.unitCost)
        put("reason", x.reason)
        put("allocations", allocationArray(x.allocations))
        put("allocationEstimated", x.allocationEstimated)
    }

    private fun migrateLegacyDamageAllocations(context: Context): JSONArray {
        val items = array(context, D)
        var changed = false
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val amount = item.optInt("quantity").coerceAtLeast(0) * item.optDouble("unitCost").coerceAtLeast(0.0)
            val existing = parseAllocations(item.optJSONArray("allocations"))
            if (isValidAllocation(existing, amount)) continue
            val allocation = buildAllocations(
                context, item.optString("date"), amount, allowEstimatedLegacyRate = false
            ) ?: continue
            item.put("allocations", allocationArray(allocation.first))
            item.put("allocationEstimated", allocation.second)
            changed = true
        }
        if (changed) save(context, D, items)
        return items
    }

    fun getDamages(c: Context) = buildList {
        val items = migrateLegacyDamageAllocations(c)
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            add(DamageRecord(
                item.optLong("id"), item.optString("date"), item.optString("productCode"),
                item.optString("productName"), item.optInt("quantity"),
                item.optDouble("unitCost"), item.optString("reason"),
                parseAllocations(item.optJSONArray("allocations")),
                item.optBoolean("allocationEstimated", false)
            ))
        }
    }

    /** Saves each damage loss with the same date-effective 100% partner split. */
    fun addDamage(c: Context, x: DamageRecord): Boolean {
        if (x.id <= 0L || !validDate(x.date) || x.quantity <= 0 ||
            !x.unitCost.isFinite() || x.unitCost <= 0.0) return false
        val allocation = buildAllocations(
            c, x.date, x.totalLoss, allowEstimatedLegacyRate = false
        ) ?: return false
        val items = array(c, D)
        if ((0 until items.length()).any { items.optJSONObject(it)?.optLong("id") == x.id }) return false
        items.put(damageJson(x.copy(
            allocations = allocation.first,
            allocationEstimated = allocation.second
        )))
        return save(c, D, items)
    }
}
