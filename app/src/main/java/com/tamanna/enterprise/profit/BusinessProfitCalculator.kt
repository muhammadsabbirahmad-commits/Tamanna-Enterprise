package com.tamanna.enterprise.profit

import android.content.Context
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.sales.SaleReturnStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.sales.SalesTransactionStorage

/**
 * Shared source-of-truth calculation used by profit, partner and financial summaries.
 * Sales discounts are allocated proportionally to invoice lines. Returns reverse the
 * discounted sale revenue and restore the original sold-line cost on the return date.
 */
data class DailyBusinessProfit(
    val date: String,
    val grossSales: Double,
    val salesDiscount: Double,
    val costOfGoodsSold: Double,
    val returnRevenue: Double,
    val returnCost: Double,
    val expenses: Double,
    val damageLoss: Double
) {
    val netProfit: Double
        get() = grossSales - salesDiscount - costOfGoodsSold - returnRevenue + returnCost - expenses - damageLoss
}

data class BusinessProfitReport(
    val days: List<DailyBusinessProfit>,
    val warnings: List<String>,
    /** Daily partner-distributable result after carrying forward and recovering unpaid operating expenses. */
    val partnerDistributionByDay: Map<String, Double> = emptyMap(),
    /** Remaining operating expenses after cumulative trading-profit recovery through the report end date. */
    val unrecoveredOperatingExpenses: Double = 0.0
) {
    val grossSales: Double get() = days.sumOf { it.grossSales }
    val salesDiscount: Double get() = days.sumOf { it.salesDiscount }
    val costOfGoodsSold: Double get() = days.sumOf { it.costOfGoodsSold }
    val returnRevenue: Double get() = days.sumOf { it.returnRevenue }
    val returnCost: Double get() = days.sumOf { it.returnCost }
    val expenses: Double get() = days.sumOf { it.expenses }
    val damageLoss: Double get() = days.sumOf { it.damageLoss }
    val netProfit: Double get() = days.sumOf { it.netProfit }
}

internal data class ExpenseRecoveryResult(
    val distributableByDay: Map<String, Double>,
    val unrecoveredOperatingExpenses: Double
)

/** Damage is a direct loss; only positive trading profit after damage recovers operating expenses. */
internal fun calculateExpenseRecovery(days: List<DailyBusinessProfit>): ExpenseRecoveryResult {
    var unpaid = 0.0
    val distributable = linkedMapOf<String, Double>()
    days.sortedBy { it.date }.forEach { result ->
        unpaid += result.expenses.coerceAtLeast(0.0)
        val tradingAfterDamage = result.grossSales - result.salesDiscount -
            result.costOfGoodsSold - result.returnRevenue + result.returnCost - result.damageLoss
        val recovered = minOf(tradingAfterDamage.coerceAtLeast(0.0), unpaid)
        unpaid = (unpaid - recovered).coerceAtLeast(0.0)
        distributable[result.date] = tradingAfterDamage - recovered
    }
    return ExpenseRecoveryResult(distributable, unpaid)
}

object BusinessProfitCalculator {
    private fun day(value: String): String = value.trim().take(10)

    fun calculate(context: Context, fromDate: String? = null, toDate: String? = null): BusinessProfitReport {
        val sales = SalesStorage.getSales(context)
        val transactions = SalesTransactionStorage.getTransactions(context).associateBy { it.transactionId }
        val expenses = ExpenseStorage.getExpenses(context)
        val damages = ExpenseStorage.getDamages(context)
        val returns = SaleReturnStorage.getReturns(context)
        val returnLines = SaleReturnStorage.getLines(context)
        val exchanges = SaleReturnStorage.getExchanges(context)
        val warnings = mutableListOf<String>()
        val datedSales = sales.filter { sale ->
            val key = day(sale.date)
            key.isNotBlank() && (toDate == null || key <= toDate)
        }
        datedSales.filter { it.transactionId !in transactions }.map { it.transactionId }.distinct().forEach { id ->
            warnings += "বিক্রয় $id: ইনভয়েসের ডিসকাউন্ট/পেমেন্ট রেকর্ড নেই; বিক্রয় লাইনের ভিত্তিতে লাভ গণনা করা হয়েছে, যাচাই প্রয়োজন।"
        }
        transactions.values.filter { tx ->
            val key = day(tx.date)
            key.isNotBlank() && (fromDate == null || key >= fromDate) && (toDate == null || key <= toDate) &&
                datedSales.none { it.transactionId == tx.transactionId }
        }.forEach { tx ->
            warnings += "ইনভয়েস ${tx.transactionId}: ইনভয়েসের রেকর্ড আছে কিন্তু বিক্রয়ের পণ্য-লাইন নেই; পণ্যের ক্রয়মূল্য ছাড়া প্রকৃত লাভ নির্ণয় সম্ভব নয়।"
        }
        transactions.values.filter { tx ->
            val key = day(tx.date)
            key.isNotBlank() && (fromDate == null || key >= fromDate) && (toDate == null || key <= toDate)
        }.forEach { tx ->
            val lineSubtotal = datedSales.filter { it.transactionId == tx.transactionId }
                .sumOf { it.quantity.coerceAtLeast(0) * it.salePrice.coerceAtLeast(0.0) }
            if (kotlin.math.abs(lineSubtotal - tx.subtotal.coerceAtLeast(0.0)) > 0.02) {
                warnings += "ইনভয়েস ${tx.transactionId}: পণ্যের লাইনের মোট ${lineSubtotal} টাকা, ইনভয়েস সাবটোটাল ${tx.subtotal} টাকা; ডিসকাউন্ট বণ্টনের আগে যাচাই প্রয়োজন।"
            }
            if (kotlin.math.abs((tx.subtotal - tx.discount) - tx.total) > 0.02) {
                warnings += "ইনভয়েস ${tx.transactionId}: সাবটোটাল, ডিসকাউন্ট ও সর্বমোটের মধ্যে অমিল আছে।"
            }
        }
        val days = linkedMapOf<String, MutableDaily>()
        // Partner allocation is restated against the original sale date when a
        // return is recorded. The user-facing profit report still records the
        // cash/credit return on its actual return date.
        val partnerDays = linkedMapOf<String, MutableDaily>()

        fun bucket(date: String): MutableDaily {
            val key = day(date)
            return days.getOrPut(key) { MutableDaily() }
        }

        fun partnerBucket(date: String): MutableDaily {
            val key = day(date)
            return partnerDays.getOrPut(key) { MutableDaily() }
        }

        sales.forEach { sale ->
            val key = day(sale.date)
            if (key.isBlank() || (toDate != null && key > toDate)) return@forEach
            val gross = (sale.quantity.coerceAtLeast(0) * sale.salePrice.coerceAtLeast(0.0))
            val tx = transactions[sale.transactionId]
            val invoiceSubtotal = tx?.subtotal?.coerceAtLeast(0.0) ?: 0.0
            val discount = if (tx != null && invoiceSubtotal > 0.0) {
                (tx.discount.coerceAtLeast(0.0) * gross / invoiceSubtotal).coerceAtMost(gross)
            } else 0.0
            val item = bucket(key)
            item.grossSales += gross
            item.salesDiscount += discount
            item.cogs += sale.quantity.coerceAtLeast(0) * sale.purchasePrice.coerceAtLeast(0.0)

            val partnerItem = partnerBucket(key)
            partnerItem.grossSales += gross
            partnerItem.salesDiscount += discount
            partnerItem.cogs += sale.quantity.coerceAtLeast(0) * sale.purchasePrice.coerceAtLeast(0.0)
        }

        returns.forEach { ret ->
            val returnDate = day(ret.date)
            if (returnDate.isBlank() || (toDate != null && returnDate > toDate)) return@forEach
            val item = bucket(returnDate)
            // The saved return amount is the authoritative net amount actually refunded/credited.
            item.returnRevenue += ret.amount.coerceAtLeast(0.0)
            val linesForReturn = returnLines.filter { it.returnId == ret.returnId }
            val grossReturnedValue = linesForReturn.sumOf {
                it.quantity.coerceAtLeast(0) * it.unitPrice.coerceAtLeast(0.0)
            }
            linesForReturn.forEach { line ->
                val originals = sales.filter {
                    it.transactionId == line.transactionId &&
                        it.productCode.trim().equals(line.productCode.trim(), ignoreCase = true)
                }
                if (originals.isEmpty()) {
                    warnings += "রিটার্ন ${ret.returnId}: ${line.productCode} পণ্যের মূল বিক্রয়-লাইন পাওয়া যায়নি; লাভের হিসাব যাচাই প্রয়োজন।"
                }
                val originalUnits = originals.sumOf { it.quantity.coerceAtLeast(0) }
                val weightedUnitCost = if (originalUnits > 0) {
                    originals.sumOf { it.quantity.coerceAtLeast(0) * it.purchasePrice.coerceAtLeast(0.0) } / originalUnits
                } else 0.0
                val returnedCost = line.quantity.coerceAtLeast(0) * weightedUnitCost
                item.returnCost += returnedCost

                // Reverse the returned quantity from the original sale day for
                // partner dividends. This removes the original profit instead of
                // leaving it in lifetime profit and showing a separate later loss.
                val originalDate = originals.firstOrNull()?.date?.let(::day).orEmpty()
                if (originalDate.isNotBlank() && grossReturnedValue > 0.0) {
                    val grossLineValue = line.quantity.coerceAtLeast(0) * line.unitPrice.coerceAtLeast(0.0)
                    val netLineReturn = ret.amount.coerceAtLeast(0.0) * grossLineValue / grossReturnedValue
                    val partnerItem = partnerBucket(originalDate)
                    partnerItem.grossSales -= grossLineValue
                    partnerItem.salesDiscount -= (grossLineValue - netLineReturn)
                    partnerItem.cogs -= returnedCost
                }
            }
        }

        exchanges.filter { exchange ->
            val key = day(exchange.date)
            key.isNotBlank() && (fromDate == null || key >= fromDate) && (toDate == null || key <= toDate)
        }.forEach { exchange ->
            warnings += "এক্সচেঞ্জ ${exchange.exchangeId}: বিকল্প পণ্যের বিক্রয়মূল্য ও ক্রয়মূল্য আলাদা সংরক্ষিত নেই; এই এক্সচেঞ্জের প্রকৃত লাভ নিশ্চিতভাবে গণনা করা যায়নি।"
        }

        expenses.forEach { expense ->
            val key = day(expense.date)
            if (key.isNotBlank() && (toDate == null || key <= toDate)) {
                bucket(key).expenses += expense.amount.coerceAtLeast(0.0)
                partnerBucket(key).expenses += expense.amount.coerceAtLeast(0.0)
            }
        }
        damages.forEach { damage ->
            val key = day(damage.date)
            if (key.isNotBlank() && (toDate == null || key <= toDate)) {
                bucket(key).damageLoss += damage.totalLoss.coerceAtLeast(0.0)
                partnerBucket(key).damageLoss += damage.totalLoss.coerceAtLeast(0.0)
            }
        }

        val dailyResults = days.map { (date, item) ->
            DailyBusinessProfit(date, item.grossSales, item.salesDiscount, item.cogs,
                item.returnRevenue, item.returnCost, item.expenses, item.damageLoss)
        }.sortedBy { it.date }

        // Run recovery from the beginning of the available business history through toDate.
        // Only the displayed rows are filtered by fromDate, so changing the calendar
        // start date never resets the opening unpaid-expense balance.
        val partnerDailyResults = partnerDays.map { (date, item) ->
            DailyBusinessProfit(date, item.grossSales, item.salesDiscount, item.cogs,
                item.returnRevenue, item.returnCost, item.expenses, item.damageLoss)
        }.sortedBy { it.date }
        val recovery = calculateExpenseRecovery(partnerDailyResults)
        val visibleDays = dailyResults.filter { fromDate == null || it.date >= fromDate }
        val visibleDistribution = recovery.distributableByDay.filterKeys { fromDate == null || it >= fromDate }

        return BusinessProfitReport(
            days = visibleDays,
            warnings = warnings.distinct(),
            partnerDistributionByDay = visibleDistribution,
            unrecoveredOperatingExpenses = recovery.unrecoveredOperatingExpenses
        )
    }

    private class MutableDaily {
        var grossSales: Double = 0.0
        var salesDiscount: Double = 0.0
        var cogs: Double = 0.0
        var returnRevenue: Double = 0.0
        var returnCost: Double = 0.0
        var expenses: Double = 0.0
        var damageLoss: Double = 0.0
    }
}
