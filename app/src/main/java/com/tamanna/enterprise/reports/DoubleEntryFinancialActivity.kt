package com.tamanna.enterprise.reports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.profit.BusinessProfitCalculator
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.purchase.SupplierDueStorage
import com.tamanna.enterprise.sales.SaleReturnStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.sales.SalesTransactionStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
private fun monthStartIso(): String = todayIso().take(8) + "01"

private fun isoDateToUtcMillis(value: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)?.time
}.getOrNull()

private fun utcMillisToIsoDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(value))

class DoubleEntryFinancialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamannaTheme(com.tamanna.enterprise.settings.ThemeStorage.getTheme(this)) {
                DoubleEntryFinancialScreen()
            }
        }
    }
}

private data class LedgerRow(
    val date: String,
    val description: String,
    val debitAccount: String,
    val creditAccount: String,
    val debitAmount: Double,
    val creditAmount: Double,
    val note: String = ""
)

private data class LedgerSummary(
    val rows: List<LedgerRow>,
    val purchaseTotal: Double,
    val stockValue: Double,
    val stockUnits: Int,
    val salesTotal: Double,
    val collectionTotal: Double,
    val receivable: Double,
    val supplierPayable: Double,
    val expenseTotal: Double,
    val periodExpenseTotal: Double,
    val damageTotal: Double,
    val periodDamageTotal: Double,
    val withdrawalTotal: Double,
    val totalCapital: Double,
    val netProfit: Double,
    val cashIn: Double,
    val cashOut: Double,
    val stockWarnings: List<String>,
    val dataWarnings: List<String>,
    val trialBalance: TrialBalanceReport
)

private fun dateKey(value: String): String = value.trim().take(10)

private fun validLedgerDate(value: String): Boolean = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = parser.parse(value) ?: return false
    parser.format(parsed) == value
}.getOrDefault(false)

private fun money(value: Double): String = "৳ " + String.format(Locale.US, "%,.2f", value)




private data class DailyBalanceEntry(
    val id: String, val date: String, val account: String,
    val mode: String, val amount: Double, val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(), val deleted: Boolean = false
)

private object DailyBalanceStorage {
    private const val PREFS = "tamanna_daily_manual_balances"
    private const val KEY = "entries"

    private fun readAll(context: android.content.Context): List<DailyBalanceEntry> = runCatching {
        val raw = BusinessStorage.prefs(context, PREFS).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(DailyBalanceEntry(
                    item.optString("id"), item.optString("date"), item.optString("account"),
                    item.optString("mode"), item.optDouble("amount"), item.optString("note"),
                    item.optLong("updatedAt"), item.optBoolean("deleted", false)
                ))
            }
        }.filter { it.id.isNotBlank() && validLedgerDate(it.date) &&
            it.account in setOf("CASH", "BANK") && it.mode in setOf("SET", "ADD", "SUB") &&
            it.amount.isFinite() && it.amount >= 0.0 }
    }.getOrDefault(emptyList())

    fun get(context: android.content.Context): List<DailyBalanceEntry> =
        readAll(context).filterNot { it.deleted }

    private fun writeAll(context: android.content.Context, entries: List<DailyBalanceEntry>): Boolean = runCatching {
        val array = JSONArray()
        entries.sortedWith(compareBy<DailyBalanceEntry> { it.date }.thenBy { it.updatedAt }).forEach {
            array.put(JSONObject().put("id", it.id).put("date", it.date).put("account", it.account)
                .put("mode", it.mode).put("amount", it.amount).put("note", it.note)
                .put("updatedAt", it.updatedAt).put("deleted", it.deleted))
        }
        BusinessStorage.prefs(context, PREFS).edit().putString(KEY, array.toString()).commit()
    }.getOrDefault(false)

    fun save(context: android.content.Context, entry: DailyBalanceEntry): Boolean =
        writeAll(context, readAll(context).filterNot { it.id == entry.id } + entry)

    fun delete(context: android.content.Context, id: String): Boolean {
        val now = System.currentTimeMillis()
        val entries = readAll(context).map { if (it.id == id) it.copy(updatedAt = now, deleted = true) else it }
        return writeAll(context, entries)
    }

    fun balance(entries: List<DailyBalanceEntry>, account: String, toDate: String,
        openingAmount: Double?, openingDate: String?): Double {
        var total = if (openingDate != null && openingDate <= toDate) openingAmount ?: 0.0 else 0.0
        entries.asSequence().filter { it.account == account && it.date <= toDate &&
            (openingDate == null || it.date >= openingDate) }
            .sortedWith(compareBy<DailyBalanceEntry> { it.date }.thenBy { it.updatedAt }).forEach { entry ->
                total = when (entry.mode) {
                    "SET" -> entry.amount
                    "ADD" -> total + entry.amount
                    "SUB" -> total - entry.amount
                    else -> total
                }
            }
        return total
    }
}

private fun buildLedger(context: android.content.Context, fromDate: String, toDate: String): LedgerSummary {
    val inRange: (String) -> Boolean = { dateKey(it) >= fromDate && dateKey(it) <= toDate }
    val purchases = PurchaseStorage.getPurchases(context).filter { inRange(it.date) }
    val allSales = SalesStorage.getSales(context)
    val sales = allSales.filter { inRange(it.date) }
    val transactions = SalesTransactionStorage.getTransactions(context).filter { inRange(it.date) }
    val allExpenses = ExpenseStorage.getExpenses(context)
    val expenses = allExpenses.filter { inRange(it.date) }
    val allDamages = ExpenseStorage.getDamages(context)
    val damages = allDamages.filter { inRange(it.date) }
    val supplierEntries = SupplierDueStorage.getEntries(context).filter { inRange(it.date) }
    val customerEntries = CustomerDueStorage.getEntries(context).filter { inRange(it.date) }
    // A return belongs to the period in which the return happened, even if the original sale was older.
    val returns = SaleReturnStorage.getReturns(context).filter { inRange(it.date) }
    val returnLines = SaleReturnStorage.getLines(context).filter { line ->
        returns.any { it.returnId == line.returnId }
    }
    val exchanges = SaleReturnStorage.getExchanges(context).filter { inRange(it.date) }
    val withdrawals = PartnerStorage.getWithdrawals(context).filter { inRange(it.date) }
    val allCapitalContributions = PartnerStorage.getCapitalContributions(context)
    val capitalContributions = allCapitalContributions.filter { inRange(it.date) }
    val rows = mutableListOf<LedgerRow>()
    val warnings = mutableListOf<String>()
    val sharedProfitReport = BusinessProfitCalculator.calculate(context, fromDate, toDate)
    warnings += sharedProfitReport.warnings

    // Use exactly the same weighted historical purchase cost as the shared profit calculator.
    fun historicalReturnCost(line: com.tamanna.enterprise.sales.SaleReturnLine): Double {
        val originals = allSales.filter {
            it.transactionId == line.transactionId &&
                it.productCode.trim().equals(line.productCode.trim(), ignoreCase = true)
        }
        if (originals.isEmpty()) return 0.0
        val originalUnits = originals.sumOf { it.quantity.coerceAtLeast(0) }
        if (originalUnits <= 0) return 0.0
        val weightedUnitCost = originals.sumOf {
            it.quantity.coerceAtLeast(0) * it.purchasePrice.coerceAtLeast(0.0)
        } / originalUnits
        return line.quantity.coerceAtLeast(0) * weightedUnitCost
    }

    // Opening capital and assets are reconciled in a separate verified opening-balance
    // panel. Never manufacture an asset counterpart from historic partner capital.
    val openingBalance = OpeningBalanceStorage.get(context)
    if (openingBalance == null) {
        warnings += "প্রারম্ভিক ব্যালান্স সংরক্ষণ করা হয়নি; পুরোনো মূলধনের বিপরীতে কোনো নগদ/স্টক/পাওনা অনুমান করে যোগ করা হয়নি।"
    } else if (openingBalance.date != fromDate) {
        warnings += "প্রারম্ভিক ব্যালান্সের তারিখ ${openingBalance.date}; নির্বাচিত হিসাবের শুরুর তারিখ ${fromDate}। উদ্বোধনী এন্ট্রি দেখতে রিপোর্টের শুরুর তারিখ একই করুন।"
    } else if (!openingBalance.verified) {
        warnings += "প্রারম্ভিক ব্যালান্স সংরক্ষিত আছে, কিন্তু যাচাইকৃত হিসেবে নিশ্চিত করা হয়নি; তাই ট্রায়াল ব্যালান্সে পোস্ট করা হয়নি।"
    } else {
        // Post only the actual opening balances entered by the user. Each opening asset
        // is an independent debit and each liability/equity is an independent credit.
        // If the accounting equation does not balance, the trial balance stays unbalanced;
        // no suspense/plug account is generated to conceal the difference.
        fun openingDebit(account: String, label: String, amount: Double) {
            if (amount > 0.004) rows += LedgerRow(fromDate, "প্রারম্ভিক $label", account, "", amount, 0.0)
        }
        fun openingCredit(account: String, label: String, amount: Double) {
            if (amount > 0.004) rows += LedgerRow(fromDate, "প্রারম্ভিক $label", "", account, 0.0, amount)
        }
        openingDebit("নগদ", "নগদ", openingBalance.cash)
        openingDebit("ব্যাংক", "ব্যাংক", openingBalance.bank)
        openingDebit("স্টক/ইনভেন্টরি", "স্টক", openingBalance.stock)
        openingDebit("ক্রেতার পাওনা", "ক্রেতার পাওনা", openingBalance.customerReceivable)
        openingCredit("সরবরাহকারীর দেনা", "সরবরাহকারীর দেনা", openingBalance.supplierPayable)
        openingCredit("অন্যান্য দেনা", "অন্যান্য দেনা", openingBalance.otherLiabilities)
        val openingCapital = allCapitalContributions
            .filter { dateKey(it.date) <= openingBalance.date }
            .sumOf { it.amount }
        if (openingCapital >= 0.0) openingCredit("পার্টনার মূলধন", "পার্টনার মূলধন", openingCapital)
        else openingDebit("পার্টনার মূলধন", "পার্টনার মূলধনের ঘাটতি", -openingCapital)
        if (allCapitalContributions.any { dateKey(it.date) <= openingBalance.date && it.approximateDate }) {
            warnings += "প্রারম্ভিক মূলধনের কিছু পুরোনো জমার তারিখ আনুমানিক; পার্টনারভিত্তিক মূলধন যাচাই করুন।"
        }
    }

    capitalContributions.forEach { contribution ->
        val partnerName = PartnerStorage.getPartners(context).firstOrNull { it.id == contribution.partnerId }?.name ?: "পুরোনো/মুছে ফেলা পার্টনার"
        val retainedProfit = contribution.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ")
        val capitalReturned = contribution.amount < -0.004
        val amount = kotlin.math.abs(contribution.amount)
        if (amount <= 0.004) return@forEach
        val description = when {
            retainedProfit -> "অবণ্টিত মুনাফা মূলধনে স্থানান্তর • $partnerName"
            capitalReturned -> "পার্টনার মূলধন ফেরত • $partnerName"
            else -> "পার্টনার মূলধন জমা • $partnerName"
        }
        rows += LedgerRow(contribution.date, description, if (retainedProfit) "অবণ্টিত মুনাফা" else if (capitalReturned) "পার্টনার মূলধন" else "নগদ/ব্যাংক", if (capitalReturned) "নগদ/ব্যাংক" else "পার্টনার মূলধন", amount, amount, contribution.note + if (contribution.approximateDate) " • তারিখ আনুমানিক" else "")
        if (contribution.approximateDate) warnings += "$partnerName-এর পুরোনো মূলধনের প্রকৃত জমার তারিখ সংরক্ষিত ছিল না; পার্টনার তৈরির তারিখকে আনুমানিক ধরা হয়েছে।"
    }

    // Each purchase is reconciled against its own invoice settlement.
    // New records use a stable purchaseId and stored paid/due amounts. Legacy
    // records are matched only by an exact unique supplier/date/memo description;
    // ambiguous historical entries are deliberately left unreconciled.
    val purchaseDueEntries = supplierEntries.filter { it.type == "PURCHASE" }
    val consumedLegacyDueIds = mutableSetOf<Long>()
    purchases.forEach { purchase ->
        val gross = (purchase.quantity.coerceAtLeast(0) * purchase.purchasePrice.coerceAtLeast(0.0))
        if (gross <= 0.004) return@forEach

        val linkedEntries = purchaseDueEntries.filter { it.purchaseId == purchase.id }
        val expectedLegacyNote = "ক্রয়: ${purchase.productName} x${purchase.quantity}" +
            if (purchase.memoNumber.isBlank()) "" else " • মেমো ${purchase.memoNumber.trim()}"
        val legacyMatches = if (!purchase.settlementRecorded) {
            purchaseDueEntries.filter {
                it.purchaseId <= 0L && it.id !in consumedLegacyDueIds &&
                    it.supplier.trim().equals(purchase.supplier.trim(), true) &&
                    dateKey(it.date) == dateKey(purchase.date) &&
                    it.note.trim() == expectedLegacyNote
            }
        } else emptyList()
        val duplicateLegacyPurchaseCount = if (!purchase.settlementRecorded) purchases.count {
            !it.settlementRecorded &&
                it.supplier.trim().equals(purchase.supplier.trim(), true) &&
                dateKey(it.date) == dateKey(purchase.date) &&
                it.productName == purchase.productName &&
                it.quantity == purchase.quantity &&
                it.memoNumber.trim() == purchase.memoNumber.trim()
        } else 0

        val dueFromSource: Double? = when {
            purchase.settlementRecorded -> purchase.dueAmount
            linkedEntries.size == 1 -> linkedEntries.single().amount
            linkedEntries.size > 1 -> {
                warnings += "ক্রয় ইনভয়েস ${purchase.id}: একই ইনভয়েসে একাধিক বাকি রেকর্ড আছে; যাচাই প্রয়োজন।"
                null
            }
            legacyMatches.size == 1 && duplicateLegacyPurchaseCount == 1 -> {
                consumedLegacyDueIds += legacyMatches.single().id
                legacyMatches.single().amount
            }
            legacyMatches.isNotEmpty() -> {
                warnings += "পুরোনো ক্রয় ${purchase.memoNumber.ifBlank { purchase.id.toString() }}: একই মেমো/পণ্য/তারিখের একাধিক বাকি রেকর্ড মেলায় ইনভয়েসভিত্তিক ভাগ নির্ধারণ করা যায়নি।"
                null
            }
            else -> 0.0
        }

        if (purchase.settlementRecorded && linkedEntries.size == 1 &&
            kotlin.math.abs(linkedEntries.single().amount - purchase.dueAmount) > 0.01) {
            warnings += "ইনভয়েস ${purchase.memoNumber.ifBlank { purchase.id.toString() }}: ক্রয় রেকর্ডের বাকি ${money(purchase.dueAmount)} এবং সরবরাহকারী রেকর্ডের বাকি ${money(linkedEntries.single().amount)} মেলেনি।"
        }

        if (dueFromSource == null) {
            // Do not invent a cash/payable split for ambiguous legacy records.
            rows += LedgerRow(dateKey(purchase.date), "পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity} • নিষ্পত্তি যাচাই বাকি",
                "স্টক/ইনভেন্টরি", "", gross, 0.0,
                "মেমো: ${purchase.memoNumber.ifBlank { "—" }}; মোট ${money(gross)}; নগদ/বাকি ভাগ পুরোনো রেকর্ড থেকে নিশ্চিত করা যায়নি।")
            return@forEach
        }

        val due = dueFromSource.coerceIn(0.0, gross)
        if (kotlin.math.abs(dueFromSource - due) > 0.01) {
            warnings += "ইনভয়েস ${purchase.memoNumber.ifBlank { purchase.id.toString() }}: বাকি মোট ক্রয়মূল্যের বেশি; প্রকৃত অঙ্ক যাচাই করুন।"
        }
        val paid = gross - due
        if (purchase.settlementRecorded && kotlin.math.abs(purchase.paidAmount + purchase.dueAmount - gross) > 0.01) {
            warnings += "ইনভয়েস ${purchase.memoNumber.ifBlank { purchase.id.toString() }}: জমা ও বাকি মিলে মোট ক্রয়মূল্য হচ্ছে না।"
        }
        val invoiceNote = "মেমো: ${purchase.memoNumber.ifBlank { "—" }} • ইনভয়েস ID: ${purchase.id}"
        if (paid > 0.004) rows += LedgerRow(dateKey(purchase.date), "পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity}",
            "স্টক/ইনভেন্টরি", "নগদ", paid, paid, "$invoiceNote; ক্রয়ের মোট ${money(gross)}")
        if (due > 0.004) rows += LedgerRow(dateKey(purchase.date), "পণ্য ক্রয় বাকি • ${purchase.productName} × ${purchase.quantity}",
            "স্টক/ইনভেন্টরি", "সরবরাহকারীর দেনা", due, due, invoiceNote)
    }

    // Sales: cash received at checkout plus the outstanding customer receivable.
    transactions.forEach { tx ->
        val gross = tx.total.coerceAtLeast(0.0)
        if (gross <= 0.0) return@forEach
        val paid = tx.paid.coerceIn(0.0, gross)
        val due = gross - paid
        if (kotlin.math.abs((tx.paid + tx.due) - gross) > 0.02) {
            warnings += "বিক্রয় ${tx.transactionId}: মোট, জমা ও বাকি টাকার মধ্যে অমিল আছে।"
        }
        if (paid > 0.004) rows += LedgerRow(dateKey(tx.date), "বিক্রয় আদায় • ${tx.customer.ifBlank { "নামহীন ক্রেতা" }}", "নগদ", "বিক্রয় আয়", paid, paid, "ইনভয়েস: ${tx.transactionId}")
        if (due > 0.004) rows += LedgerRow(dateKey(tx.date), "বিক্রয় বাকি • ${tx.customer.ifBlank { "নামহীন ক্রেতা" }}", "ক্রেতার পাওনা", "বিক্রয় আয়", due, due, "ইনভয়েস: ${tx.transactionId}")
    }
    val transactionIds = transactions.map { it.transactionId }.toSet()
    sales.filter { it.transactionId !in transactionIds }.groupBy { it.transactionId }.forEach { (id, items) ->
        warnings += "বিক্রয় লাইন $id আছে, কিন্তু বিক্রয়ের মূল পেমেন্ট রেকর্ড নেই; তাই ডেবিট-ক্রেডিটে যোগ করা হয়নি।"
    }

    // Cost of goods sold, separately from revenue.
    // Record the full cost at sale time; returned goods reverse COGS on the actual return date below.
    sales.forEach { sale ->
        val cost = sale.quantity.coerceAtLeast(0) * sale.purchasePrice.coerceAtLeast(0.0)
        if (cost > 0.0) rows += LedgerRow(dateKey(sale.date), "বিক্রিত পণ্যের ক্রয়মূল্য • ${sale.productName} × ${sale.quantity}", "বিক্রিত পণ্যের খরচ", "স্টক/ইনভেন্টরি", cost, cost)
    }

    returns.forEach { ret ->
        val refunded = ret.refundAmount.coerceAtLeast(0.0)
        val dueReduction = ret.dueReduction.coerceAtLeast(0.0)
        val originalLines = returnLines.filter { it.returnId == ret.returnId }
        val expectedReturnAmount = originalLines.sumOf { line ->
            val originals = allSales.filter {
                it.transactionId == line.transactionId &&
                    it.productCode.trim().equals(line.productCode.trim(), ignoreCase = true)
            }
            val units = originals.sumOf { it.quantity.coerceAtLeast(0) }
            val unitSalePrice = if (units > 0) originals.sumOf {
                it.quantity.coerceAtLeast(0) * it.salePrice.coerceAtLeast(0.0)
            } / units else 0.0
            val tx = SalesTransactionStorage.getTransaction(context, line.transactionId)
            val discountRate = if (tx != null && tx.subtotal > 0.0)
                (tx.discount / tx.subtotal).coerceIn(0.0, 1.0) else 0.0
            line.quantity.coerceAtLeast(0) * unitSalePrice * (1.0 - discountRate)
        }
        if (originalLines.isEmpty()) {
            warnings += "রিটার্ন ${ret.returnId}: রিটার্নের পণ্য-লাইন পাওয়া যায়নি; বিক্রয় ফেরত ও স্টক/COGS মিলিয়ে দেখুন।"
        } else if (kotlin.math.abs(ret.amount - expectedReturnAmount) > 0.02) {
            warnings += "রিটার্ন ${ret.returnId}: সংরক্ষিত ফেরত মূল্য ${money(ret.amount)}; মূল বিক্রয়-লাইন ও ডিসকাউন্ট অনুযায়ী হওয়ার কথা ${money(expectedReturnAmount)}।"
        }
        if (refunded > 0.0) rows += LedgerRow(dateKey(ret.date), "বিক্রয় ফেরত • নগদ ফেরত", "বিক্রয় ফেরত", "নগদ", refunded, refunded, "রিটার্ন: ${ret.returnId}")
        if (dueReduction > 0.0) rows += LedgerRow(dateKey(ret.date), "বিক্রয় ফেরত • ক্রেতার বাকি কমেছে", "বিক্রয় ফেরত", "ক্রেতার পাওনা", dueReduction, dueReduction, "রিটার্ন: ${ret.returnId}")
        if (kotlin.math.abs(ret.amount - refunded - dueReduction) > 0.02) {
            warnings += "রিটার্ন ${ret.returnId}: ফেরতের মোট টাকা ও নগদ ফেরত/বাকি সমন্বয়ের অঙ্ক মেলেনি।"
        }
        returnLines.filter { it.returnId == ret.returnId }.forEach { line ->
            val originalExists = allSales.any {
                it.transactionId == line.transactionId &&
                    it.productCode.trim().equals(line.productCode.trim(), ignoreCase = true)
            }
            val costValue = historicalReturnCost(line)
            if (!originalExists) {
                warnings += "রিটার্ন ${ret.returnId}: ${line.productCode} পণ্যের মূল বিক্রয়-লাইন পাওয়া যায়নি; ফেরত পণ্যের ক্রয়মূল্য যাচাই প্রয়োজন।"
            }
            if (costValue > 0.0) rows += LedgerRow(dateKey(ret.date), "ফেরত পণ্য স্টকে • ${line.productName} × ${line.quantity}", "স্টক/ইনভেন্টরি", "বিক্রিত পণ্যের খরচ", costValue, costValue)
        }
    }

    // Actual customer collections only. A return's dueReduction is already
    // posted above as Sales Returns Dr / Customer Receivable Cr; its matching
    // PAYMENT row exists only to update the customer sub-ledger and must not
    // be posted again as a cash collection.
    customerEntries.filter {
        it.type == "PAYMENT" && it.amount > 0.0 &&
            !it.note.trim().startsWith("বিক্রয় রিটার্ন:")
    }.forEach { entry ->
        rows += LedgerRow(dateKey(entry.date), "ক্রেতার বাকি আদায় • ${entry.customer}", "নগদ", "ক্রেতার পাওনা", entry.amount, entry.amount, entry.note)
    }

    exchanges.forEach { exchange ->
        warnings += "এক্সচেঞ্জ ${exchange.exchangeId}: বিকল্প পণ্যের বিক্রয়মূল্য ও COGS এখনো স্বতন্ত্র জার্নাল এন্ট্রি নয়; এই সময়ের লাভ/ট্রায়াল ব্যালান্সে এক্সচেঞ্জ আলাদাভাবে যাচাই করুন।"
    }

    expenses.forEach { expense ->
        if (expense.amount > 0.0) rows += LedgerRow(dateKey(expense.date), "প্রতিষ্ঠানের খরচ • ${expense.category}", "প্রতিষ্ঠানের খরচ", "নগদ", expense.amount, expense.amount, expense.note)
    }
    damages.forEach { damage ->
        if (damage.totalLoss > 0.0) rows += LedgerRow(dateKey(damage.date), "ড্যামেজ/নষ্ট • ${damage.productName}", "ড্যামেজ ক্ষতি", "স্টক/ইনভেন্টরি", damage.totalLoss, damage.totalLoss, damage.reason)
    }

    supplierEntries.filter { it.type == "PAYMENT" && it.amount < 0.0 }.forEach { entry ->
        rows += LedgerRow(dateKey(entry.date), "সরবরাহকারীকে পরিশোধ • ${entry.supplier}", "সরবরাহকারীর দেনা", "নগদ", -entry.amount, -entry.amount, entry.note)
    }
    withdrawals.forEach { withdrawal ->
        val partner = PartnerStorage.getPartners(context).firstOrNull { it.id == withdrawal.partnerId }?.name ?: "পার্টনার"
        rows += LedgerRow(dateKey(withdrawal.date), "পার্টনার লভ্যাংশ উত্তোলন • $partner", "লভ্যাংশ উত্তোলন", "নগদ", withdrawal.amount, withdrawal.amount, withdrawal.note)
    }

    val purchaseTotal = purchases.sumOf { it.quantity * it.purchasePrice }
    val salesTotal = transactions.sumOf { it.total.coerceAtLeast(0.0) } - returns.sumOf { it.amount.coerceAtLeast(0.0) }
    // Return due-reductions are mirrored in the customer sub-ledger as PAYMENT
    // records, but they are not cash collections. Count only actual receipts.
    val actualCustomerCollections = customerEntries.filter {
        it.type == "PAYMENT" && it.amount > 0.0 &&
            !it.note.trim().startsWith("বিক্রয় রিটার্ন:")
    }
    val collectionTotal = transactions.sumOf { it.paid.coerceAtLeast(0.0) } +
        actualCustomerCollections.sumOf { it.amount }
    val receivable = customerEntries.sumOf { if (it.type == "SALE") it.amount else -it.amount }.coerceAtLeast(0.0)
    val supplierPayable = supplierEntries.sumOf { it.amount }.coerceAtLeast(0.0)
    val expenseTotal = allExpenses.sumOf { it.amount.coerceAtLeast(0.0) }
    val periodExpenseTotal = expenses.sumOf { it.amount.coerceAtLeast(0.0) }
    val damageTotal = allDamages.sumOf { it.totalLoss.coerceAtLeast(0.0) }
    val periodDamageTotal = damages.sumOf { it.totalLoss.coerceAtLeast(0.0) }
    val withdrawalTotal = withdrawals.sumOf { it.amount.coerceAtLeast(0.0) }
    val totalCapital = PartnerStorage.getPartners(context).sumOf { it.shareAmount.coerceAtLeast(0.0) }
    val grossCogs = sales.sumOf { sale -> sale.quantity.coerceAtLeast(0) * sale.purchasePrice.coerceAtLeast(0.0) }
    val returnedCost = returnLines.sumOf { line -> historicalReturnCost(line) }
    val netCogs = grossCogs - returnedCost

    // Reconcile each journal category against its independent source records.
    fun checkSource(label: String, source: Double, journal: Double) {
        if (kotlin.math.abs(source - journal) > 0.01) {
            warnings += "$label: মূল রেকর্ড ${money(source)}, ডেবিট-ক্রেডিট জার্নাল ${money(journal)}; অমিল যাচাই করুন।"
        }
    }
    checkSource("প্রতিষ্ঠানের খরচ", periodExpenseTotal,
        rows.filter { it.description.startsWith("প্রতিষ্ঠানের খরচ •") }.sumOf { it.debitAmount })
    checkSource("ড্যামেজ ক্ষতি", periodDamageTotal,
        rows.filter { it.description.startsWith("ড্যামেজ/নষ্ট •") }.sumOf { it.debitAmount })
    checkSource("পণ্য ক্রয়", purchaseTotal,
        rows.filter { it.description.startsWith("পণ্য ক্রয়") }.sumOf { it.debitAmount })
    checkSource("বিক্রয় ইনভয়েস", transactions.sumOf { it.total.coerceAtLeast(0.0) },
        rows.filter { it.creditAccount == "বিক্রয় আয়" }.sumOf { it.creditAmount })
    checkSource("বিক্রয় ফেরত", returns.sumOf { it.amount.coerceAtLeast(0.0) },
        rows.filter { it.debitAccount == "বিক্রয় ফেরত" }.sumOf { it.debitAmount })
    checkSource("বিক্রিত পণ্যের ক্রয়মূল্য", grossCogs,
        rows.filter { it.description.startsWith("বিক্রিত পণ্যের ক্রয়মূল্য •") }.sumOf { it.debitAmount })
    checkSource("ফেরত পণ্যের ক্রয়মূল্য", returnedCost,
        rows.filter { it.description.startsWith("ফেরত পণ্য স্টকে •") }.sumOf { it.debitAmount })
    checkSource("সরবরাহকারীর কাছে ক্রয়-বাকি", supplierEntries.filter { it.type == "PURCHASE" }.sumOf { it.amount.coerceAtLeast(0.0) },
        rows.filter { it.description.startsWith("পণ্য ক্রয় বাকি •") }.sumOf { it.debitAmount })
    checkSource("সরবরাহকারীকে পরিশোধ", supplierEntries.filter { it.type == "PAYMENT" }.sumOf { -it.amount.coerceAtMost(0.0) },
        rows.filter { it.description.startsWith("সরবরাহকারীকে পরিশোধ •") }.sumOf { it.debitAmount })
    checkSource("ক্রেতার বাকি আদায়", actualCustomerCollections.sumOf { it.amount },
        rows.filter { it.description.startsWith("ক্রেতার বাকি আদায় •") }.sumOf { it.debitAmount })
    val partnerCapitalAudit = DoubleEntryAccounting.auditPartnerCapital(buildList {
        capitalContributions.forEach { contribution ->
            val kind = when {
                contribution.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") -> PartnerCapitalKind.PROFIT_CAPITALIZATION
                contribution.amount < -0.004 -> PartnerCapitalKind.CAPITAL_RETURN
                else -> PartnerCapitalKind.CONTRIBUTION
            }
            add(PartnerCapitalMovement(kind, kotlin.math.abs(contribution.amount)))
        }
        withdrawals.forEach { add(PartnerCapitalMovement(PartnerCapitalKind.DIVIDEND_WITHDRAWAL, it.amount)) }
    })
    checkSource("পার্টনারের মূলধন জমা", partnerCapitalAudit.contribution,
        rows.filter { it.description.startsWith("পার্টনার মূলধন জমা •") }.sumOf { it.creditAmount })
    checkSource("পার্টনারের মূলধন ফেরত", partnerCapitalAudit.capitalReturn,
        rows.filter { it.description.startsWith("পার্টনার মূলধন ফেরত •") }.sumOf { it.debitAmount })
    checkSource("লভ্যাংশ উত্তোলন", partnerCapitalAudit.dividendWithdrawal,
        rows.filter { it.description.startsWith("পার্টনার লভ্যাংশ উত্তোলন •") }.sumOf { it.debitAmount })
    checkSource("লভ্যাংশ মূলধনে যোগ", partnerCapitalAudit.profitCapitalization,
        rows.filter { it.description.startsWith("অবণ্টিত মুনাফা মূলধনে স্থানান্তর •") }.sumOf { it.creditAmount })
    val externalCapitalIn = capitalContributions.filter {
        it.amount > 0.004 && !it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ")
    }.sumOf { it.amount }
    val capitalizedProfit = capitalContributions.filter {
        it.amount > 0.004 && it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ")
    }.sumOf { it.amount }
    val capitalReturned = capitalContributions.filter { it.amount < -0.004 }.sumOf { -it.amount }
    checkSource("পার্টনারের নগদ মূলধন জমা", externalCapitalIn,
        rows.filter { it.description.startsWith("পার্টনার মূলধন জমা •") }.sumOf { it.creditAmount })
    checkSource("অবণ্টিত মুনাফা মূলধনে স্থানান্তর", capitalizedProfit,
        rows.filter { it.description.startsWith("অবণ্টিত মুনাফা মূলধনে স্থানান্তর •") }.sumOf { it.creditAmount })
    checkSource("পার্টনারের মূলধন ফেরত", capitalReturned,
        rows.filter { it.description.startsWith("পার্টনার মূলধন ফেরত •") }.sumOf { it.debitAmount })

    // Verify each invoice's payment and receivable split independently of the balanced journal totals.
    transactions.forEach { tx ->
        val invoiceRows = rows.filter { it.note.contains("ইনভয়েস: ${tx.transactionId}") }
        checkSource("ইনভয়েস ${tx.transactionId} জমা", tx.paid.coerceAtLeast(0.0),
            invoiceRows.filter { it.debitAccount == "নগদ" }.sumOf { it.debitAmount })
        checkSource("ইনভয়েস ${tx.transactionId} বাকি", tx.due.coerceAtLeast(0.0),
            invoiceRows.filter { it.debitAccount == "ক্রেতার পাওনা" }.sumOf { it.debitAmount })
    }

    // Profit, partner distribution and the profit screen all use the same calculation engine.
    val netProfit = sharedProfitReport.netProfit

    // Current stock is read from the live product inventory, not reconstructed from only the selected period.
    // Historical stock snapshots are not stored by the app, so this value is explicitly labelled as current.
    val stockWarnings = mutableListOf<String>()
    val currentProducts = ProductStorage.getProducts(context)
    val stockValue = currentProducts.sumOf { it.stockQuantity.coerceAtLeast(0) * it.purchasePrice.coerceAtLeast(0.0) }
    val stockUnits = currentProducts.sumOf { it.stockQuantity.coerceAtLeast(0) }

    // Cash movement is partial until manually entered opening cash/bank balances are reconciled.
    val purchaseDue = supplierEntries.filter { it.type == "PURCHASE" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val cashIn = transactions.sumOf { it.paid.coerceAtLeast(0.0) } +
        actualCustomerCollections.sumOf { it.amount } +
        externalCapitalIn
    val cashOut = (purchaseTotal - purchaseDue).coerceAtLeast(0.0) +
        supplierEntries.filter { it.type == "PAYMENT" }.sumOf { -it.amount.coerceAtMost(0.0) } +
        periodExpenseTotal + withdrawalTotal +
        returns.sumOf { it.refundAmount.coerceAtLeast(0.0) } + capitalReturned

    // Build one opening journal from all opening account balances, then
    // audit every period source row independently as its own journal entry.
    val openingRows = rows.filter { it.description.startsWith("প্রারম্ভিক ") }
    val openingLines = openingRows.flatMap { row ->
        buildList {
            if (row.debitAccount.isNotBlank() && row.debitAmount > 0.004) add(JournalLine(row.debitAccount, debit = row.debitAmount))
            if (row.creditAccount.isNotBlank() && row.creditAmount > 0.004) add(JournalLine(row.creditAccount, credit = row.creditAmount))
        }
    }
    val openingJournal = if (openingLines.isEmpty()) emptyList() else listOf(
        JournalEntry("OPENING-$fromDate", fromDate, openingLines, "Verified opening balances")
    )
    val periodJournal = rows.filterNot { it.description.startsWith("প্রারম্ভিক ") }.mapIndexed { index, row ->
        val lines = buildList {
            if (row.debitAccount.isNotBlank() && row.debitAmount > 0.004) add(JournalLine(row.debitAccount, debit = row.debitAmount))
            if (row.creditAccount.isNotBlank() && row.creditAmount > 0.004) add(JournalLine(row.creditAccount, credit = row.creditAmount))
        }
        JournalEntry("PERIOD-$fromDate-$toDate-$index", row.date, lines, row.description + " " + row.note)
    }.filter { it.lines.isNotEmpty() }
    val auditedTrialBalance = DoubleEntryAccounting.trialBalance(openingJournal, periodJournal)
    warnings += auditedTrialBalance.errors
    return LedgerSummary(
        rows.sortedWith(compareBy<LedgerRow> { it.date }.thenBy { it.description }),
        purchaseTotal, stockValue, stockUnits, salesTotal, collectionTotal, receivable,
        supplierPayable, expenseTotal, periodExpenseTotal, damageTotal, periodDamageTotal,
        withdrawalTotal, totalCapital, netProfit, cashIn, cashOut, stockWarnings, warnings,
        auditedTrialBalance
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoubleEntryFinancialScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var selectedSide by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf(monthStartIso()) }
    var toDate by remember { mutableStateOf(todayIso()) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var accounts by remember(refresh) { mutableStateOf(CashAccountStorage.getAccounts(context)) }
    var openingBalances by remember(refresh) { mutableStateOf(OpeningBalanceStorage.get(context)) }
    var dailyBalanceEntries by remember(refresh) { mutableStateOf(DailyBalanceStorage.get(context)) }
    var showDailyBalanceDialog by remember { mutableStateOf(false) }
    var editingDailyBalanceEntry by remember { mutableStateOf<DailyBalanceEntry?>(null) }
    var dailyBalanceAccount by remember { mutableStateOf("CASH") }
    var showOpeningBalanceDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<ManualCashAccount?>(null) }
    var showCapitalizationDialog by remember { mutableStateOf(false) }
    var capitalizationMessage by remember { mutableStateOf("") }
    val totalManualCash = accounts.sumOf { it.balance.coerceAtLeast(0.0) }
    val validRange = validLedgerDate(fromDate) && validLedgerDate(toDate) && fromDate <= toDate
    val summary = remember(refresh, fromDate, toDate) { if (validRange) buildLedger(context, fromDate, toDate) else buildLedger(context, "9999-12-31", "0001-01-01") }
    val capitalContributionsForPeriod = remember(refresh, fromDate, toDate) {
        PartnerStorage.getCapitalContributions(context).filter { dateKey(it.date) >= fromDate && dateKey(it.date) <= toDate }
    }
    val alreadyCapitalizedAmount = PartnerStorage.getCapitalizedProfitAmount(context, fromDate, toDate)
    val remainingDividend = summary.netProfit - summary.withdrawalTotal - alreadyCapitalizedAmount
    val alreadyCapitalized = PartnerStorage.isProfitPeriodCapitalized(context, fromDate, toDate)

    // The debit detail shows four management figures. Recoverable business
    // expenses are offset by cumulative trading profit (sales less discounts,
    // COGS and returns), not by gross sales revenue and not by net profit after
    // expenses (which would subtract the same expenses twice).
    val cumulativeProfitReport = remember(refresh, toDate) {
        BusinessProfitCalculator.calculate(context, null, toDate)
    }
    val cumulativeTradingProfit = cumulativeProfitReport.days.sumOf {
        it.netProfit + it.expenses + it.damageLoss
    }
    val cumulativeBusinessExpenses = cumulativeProfitReport.expenses + cumulativeProfitReport.damageLoss
    val unrecoveredBusinessExpenses = (cumulativeBusinessExpenses - cumulativeTradingProfit).coerceAtLeast(0.0)

    // Evening cash and bank figures are manually recorded by date, separately from estimated cash flow.
    val cashInHand = DailyBalanceStorage.balance(dailyBalanceEntries, "CASH", toDate,
        openingBalances?.takeIf { it.verified }?.cash, openingBalances?.takeIf { it.verified }?.date)
    val bankBalance = DailyBalanceStorage.balance(dailyBalanceEntries, "BANK", toDate,
        openingBalances?.takeIf { it.verified }?.bank, openingBalances?.takeIf { it.verified }?.date)
    val debitRows = summary.rows.filter { it.debitAccount.isNotBlank() && it.debitAmount > 0.0 }
    val creditRows = summary.rows.filter { it.creditAccount.isNotBlank() && it.creditAmount > 0.0 }
    // The credit card and its detail both represent partner capital investments.
    // Keep the full journal credit total separate for trial-balance reconciliation.
    val partnerInvestmentRows = creditRows.filter { it.description.startsWith("পার্টনার মূলধন জমা •") }
    val partnerInvestmentTotal = partnerInvestmentRows.sumOf { it.creditAmount }
    // Independent totals: a missing/zero side is not silently copied from the other side.
    val totalDebit = debitRows.sumOf { it.debitAmount }
    val totalCredit = creditRows.sumOf { it.creditAmount }
    val debitSummaryTotal = summary.stockValue + unrecoveredBusinessExpenses + cashInHand + bankBalance
    val ledgerDifference = kotlin.math.abs(totalDebit - totalCredit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("পূর্ণাঙ্গ হিসাব") },
                navigationIcon = {
                    TextButton(onClick = { (context as? ComponentActivity)?.finish() }) { Text("ফিরে যান") }
                },
                actions = { TextButton(onClick = { refresh++ }) { Text("রিফ্রেশ") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("হিসাবের সময়সীমা নির্বাচন", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fromDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("শুরুর তারিখ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        TextButton(onClick = { showFromDatePicker = true }) { Text("ক্যালেন্ডার") }
                    }
                )
                OutlinedTextField(
                    value = toDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("শেষ তারিখ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        TextButton(onClick = { showToDatePicker = true }) { Text("ক্যালেন্ডার") }
                    }
                )
            }
            if (!validRange) Text("সঠিক তারিখের পরিসর দিন (YYYY-MM-DD)।", color = MaterialTheme.colorScheme.error)
            Text("নির্বাচিত সময়: $fromDate থেকে $toDate", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerTotalCard("মোট ডেবিট", debitSummaryTotal, "বিস্তারিত") { selectedSide = "ডেবিট" }
                LedgerTotalCard("মোট ক্রেডিট", partnerInvestmentTotal, "বিস্তারিত") { selectedSide = "ক্রেডিট" }
                LedgerDividendCard(remainingDividend)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("ডেবিট–ক্রেডিট যাচাই", style = MaterialTheme.typography.titleMedium)
                    Text(if (ledgerDifference < 0.01) "জার্নাল এন্ট্রির দুই পাশ সমান: ${money(totalDebit)}"
                    else "ডেবিট-ক্রেডিটে অমিল: ${money(ledgerDifference)}")
                    Text("ডেবিট ও ক্রেডিট আলাদা অঙ্ক থেকে যোগ করা হয়। প্রারম্ভিক ব্যালান্সের অমিল থাকলে তা সমন্বয় করে লুকানো হয় না; মূল রেকর্ডের সতর্কতাও যাচাই করুন।", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ডেবিট–ক্রেডিট সমাধানের পর ফলাফল", style = MaterialTheme.typography.titleMedium)
                    SummaryLine("মোট ডেবিট", totalDebit)
                    SummaryLine("মোট ক্রেডিট", totalCredit)
                    SummaryLine("আপনার কাছে আছে (যোগ করা ক্যাশ/ব্যাংক)", totalManualCash)
                    SummaryLine("নির্বাচিত সময়ের নিট লাভ", summary.netProfit)
                    SummaryLine(if (remainingDividend >= 0.0) "অবণ্টিত লভ্যাংশ" else "লভ্যাংশের ঘাটতি", kotlin.math.abs(remainingDividend))
                    Text("অবণ্টিত লভ্যাংশ = নির্বাচিত সময়ের নিট মুনাফা − নগদ লভ্যাংশ উত্তোলন − ইতিমধ্যে মূলধনে স্থানান্তর। মূলধনে স্থানান্তর করলে ক্যাশ বাড়ে না।", style = MaterialTheme.typography.bodySmall)
                    if (alreadyCapitalized) {
                        Text("এই তারিখের হিসাবের লভ্যাংশ ইতিমধ্যে মূলধনে যোগ করা হয়েছে।", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(onClick = { showCapitalizationDialog = true }, enabled = validRange && remainingDividend > 0.004, modifier = Modifier.fillMaxWidth()) {
                            Text("অবশিষ্ট লভ্যাংশ পার্টনারদের মূলধনে যোগ করুন")
                        }
                    }
                    if (capitalizationMessage.isNotBlank()) Text(capitalizationMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Trial Balance — Opening / Period / Closing", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("প্রারম্ভিক ডেবিট: ${money(summary.trialBalance.openingDebit)}")
                    Text("প্রারম্ভিক ক্রেডিট: ${money(summary.trialBalance.openingCredit)}")
                    Text("সময়ের ডেবিট: ${money(summary.trialBalance.periodDebit)}")
                    Text("সময়ের ক্রেডিট: ${money(summary.trialBalance.periodCredit)}")
                    Text("সমাপনী ডেবিট: ${money(summary.trialBalance.closingDebit)}")
                    Text("সমাপনী ক্রেডিট: ${money(summary.trialBalance.closingCredit)}")
                    Text(if (summary.trialBalance.allEntriesBalanced && summary.trialBalance.closingBalanced)
                        "সব জার্নাল এন্ট্রি ও সমাপনী Trial Balance মিলেছে"
                    else "জার্নাল/Trial Balance-এ অমিল আছে — নিচের সতর্কবার্তা যাচাই করুন",
                        color = if (summary.trialBalance.allEntriesBalanced && summary.trialBalance.closingBalanced)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    summary.trialBalance.accounts.forEach { account ->
                        Text("${account.account}: Opening Dr ${money(account.openingDebit)} / Cr ${money(account.openingCredit)} • Period Dr ${money(account.periodDebit)} / Cr ${money(account.periodCredit)} • Closing Dr ${money(account.closingDebit)} / Cr ${money(account.closingCredit)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("ব্যবসার সারসংক্ষেপ", style = MaterialTheme.typography.titleLarge)
            SummaryLine("মোট পণ্য ক্রয়", summary.purchaseTotal)
            SummaryLine("অ্যাপে বর্তমানে থাকা স্টকের মূল্য (ক্রয়মূল্যে)", summary.stockValue, "${summary.stockUnits} ইউনিট • বর্তমান স্টক; পুরোনো তারিখের স্টক-স্ন্যাপশট নেই")
            SummaryLine("মোট বিক্রয় (রিটার্ন বাদে)", summary.salesTotal)
            SummaryLine("বিক্রয় থেকে মোট আদায়", summary.collectionTotal)
            SummaryLine("ক্রেতার কাছে পাওনা", summary.receivable)
            SummaryLine("সরবরাহকারীর কাছে দেনা", summary.supplierPayable)
            SummaryLine("মোট প্রতিষ্ঠানের খরচ (শুরু থেকে)", summary.expenseTotal)
            SummaryLine("নির্বাচিত সময়ের প্রতিষ্ঠানের খরচ", summary.periodExpenseTotal)
            SummaryLine("মোট ড্যামেজ/নষ্টের ক্ষতি (শুরু থেকে)", summary.damageTotal)
            SummaryLine("নির্বাচিত সময়ের ড্যামেজ ক্ষতি", summary.periodDamageTotal)
            SummaryLine("পার্টনারের লভ্যাংশ উত্তোলন", summary.withdrawalTotal)
            SummaryLine("পার্টনারের মূলধন জমা", capitalContributionsForPeriod.filter { it.amount > 0.004 && !it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") }.sumOf { it.amount })
            SummaryLine("পার্টনারের মূলধন ফেরত", capitalContributionsForPeriod.filter { it.amount < -0.004 }.sumOf { -it.amount })
            SummaryLine("লভ্যাংশ থেকে মূলধনে যোগ", capitalContributionsForPeriod.filter { it.amount > 0.004 && it.note.startsWith("অবণ্টিত লভ্যাংশ মূলধনে যোগ") }.sumOf { it.amount })

            Text("নগদ প্রবাহ", style = MaterialTheme.typography.titleLarge)
            SummaryLine("নথিভুক্ত নগদ এসেছে", summary.cashIn)
            SummaryLine("নথিভুক্ত নগদ গেছে", summary.cashOut)
            SummaryLine("নথিভুক্ত নিট নগদ", summary.cashIn - summary.cashOut)
            Text("এটি নথিভুক্ত লেনদেনের নগদ প্রবাহ। প্রারম্ভিক ক্যাশ/ব্যাংক ব্যালেন্স যোগ না করা পর্যন্ত হাতে থাকা প্রকৃত ক্যাশ নিশ্চিত হবে না।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Text("ক্যাশ ও ব্যাংক হিসাব", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("আপনার যোগ করা ক্যাশ/ব্যাংক অ্যাকাউন্ট", style = MaterialTheme.typography.titleMedium)
                    if (accounts.isEmpty()) Text("এখনও কোনো অতিরিক্ত ক্যাশ বা ব্যাংক হিসাব যোগ করা হয়নি।", style = MaterialTheme.typography.bodySmall)
                    accounts.forEach { account ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                                Text(money(account.balance), style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { editingAccount = account; showAccountDialog = true }) { Text("সম্পাদনা") }
                            TextButton(onClick = {
                                CashAccountStorage.deleteAccount(context, account.id)
                                accounts = CashAccountStorage.getAccounts(context)
                                refresh++
                            }) { Text("বাদ") }
                        }
                        HorizontalDivider()
                    }
                    OutlinedButton(onClick = { editingAccount = null; showAccountDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ নতুন ক্যাশ/ব্যাংক হিসাব যোগ করুন")
                    }
                    SummaryLine("আপনার যোগ করা হিসাবগুলোর মোট ব্যালেন্স", totalManualCash)
                    Text("এই ব্যালেন্সগুলো অ্যাপের ভেতরে হাতে যোগ ও সম্পাদনা করা হয়। একই টাকা যেন অ্যাপের অন্য ক্যাশ ব্যালেন্সে আবার যোগ না হয়, তা খেয়াল রাখবেন।", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("প্রারম্ভিক মূলধন ও সম্পদ যাচাই", style = MaterialTheme.typography.titleLarge)
            val openingSnapshot = openingBalances
            val openingCapital = openingSnapshot?.let { opening ->
                PartnerStorage.getCapitalContributions(context)
                    .filter { dateKey(it.date) <= opening.date }
                    .sumOf { it.amount }
            } ?: 0.0
            val openingDifference = openingSnapshot?.let {
                it.totalAssets - it.totalLiabilities - openingCapital
            } ?: 0.0
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (openingSnapshot == null) "প্রারম্ভিক ব্যালান্স: অসম্পূর্ণ"
                        else if (openingSnapshot.verified && openingSnapshot.date == fromDate && kotlin.math.abs(openingDifference) < 0.01) "প্রারম্ভিক ব্যালান্স: মিলেছে"
                        else "প্রারম্ভিক ব্যালান্স: অসম্পূর্ণ",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (openingSnapshot?.let { it.verified && it.date == fromDate && kotlin.math.abs(openingDifference) < 0.01 } == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (openingSnapshot == null) {
                        Text("শুরুর দিনের প্রকৃত নগদ, ব্যাংক, স্টক, ক্রেতার পাওনা ও দেনা এখনো লেখা হয়নি।", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("হিসাবের তারিখ: ${openingSnapshot.date}", style = MaterialTheme.typography.bodySmall)
                        SummaryLine("প্রারম্ভিক নগদ", openingSnapshot.cash)
                        SummaryLine("প্রারম্ভিক ব্যাংক", openingSnapshot.bank)
                        SummaryLine("প্রারম্ভিক স্টক (ক্রয়মূল্যে)", openingSnapshot.stock)
                        SummaryLine("প্রারম্ভিক ক্রেতার পাওনা", openingSnapshot.customerReceivable)
                        SummaryLine("প্রারম্ভিক সরবরাহকারী দেনা", openingSnapshot.supplierPayable)
                        SummaryLine("অন্যান্য প্রারম্ভিক দেনা", openingSnapshot.otherLiabilities)
                        SummaryLine("প্রারম্ভিক পার্টনার মূলধন (রেকর্ড অনুযায়ী)", openingCapital)
                        SummaryLine("সম্পদ − দেনা − মূলধন", openingDifference)
                        if (!openingSnapshot.verified) Text("এই অঙ্কগুলো এখনো ব্যবহারকারী দ্বারা যাচাইকৃত হিসেবে চিহ্নিত হয়নি।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (kotlin.math.abs(openingDifference) >= 0.01) Text("অমিল ${money(kotlin.math.abs(openingDifference))}। এটি কোনো কাল্পনিক সম্পদ বা Suspense Account-এ বসানো হয়নি; প্রকৃত উদ্বোধনী হিসাব মিলিয়ে নিন।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (openingSnapshot.date != fromDate) Text("এই রিপোর্টের শুরুর তারিখের সঙ্গে প্রারম্ভিক ব্যালান্সের তারিখ মিলছে না।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { showOpeningBalanceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (openingSnapshot == null) "প্রারম্ভিক ব্যালান্স লিখুন" else "প্রারম্ভিক ব্যালান্স সম্পাদনা করুন")
                    }
                }
            }

            Text("পার্টনারদের মূলধন", style = MaterialTheme.typography.titleLarge)
            SummaryLine("পার্টনারদের বর্তমান মোট মূলধন", summary.totalCapital, "এটি বর্তমান মূলধন ব্যালান্স; নির্বাচিত সময়ের আগের জমা প্রারম্ভিক ব্যালান্স এবং সময়ের ভেতরের জমা/ফেরত লেনদেন হিসেবে ক্রেডিট/ডেবিট তালিকায় আছে")

            if (summary.stockWarnings.isNotEmpty()) {
                Text("স্টক যাচাইয়ের সতর্কতা", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                summary.stockWarnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (summary.dataWarnings.isNotEmpty()) {
                Text("তথ্য যাচাইয়ের সতর্কতা", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                summary.dataWarnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFromDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDateToUtcMillis(fromDate) ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { fromDate = utcMillisToIsoDate(it) }
                    showFromDatePicker = false
                }) { Text("নির্বাচন") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("বাতিল") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("হিসাবের শুরুর তারিখ") })
        }
    }

    if (showToDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDateToUtcMillis(toDate) ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { toDate = utcMillisToIsoDate(it) }
                    showToDatePicker = false
                }) { Text("নির্বাচন") }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("বাতিল") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("হিসাবের শেষ তারিখ") })
        }
    }


    if (showDailyBalanceDialog) {
        val editing = editingDailyBalanceEntry
        var date by remember(editing) { mutableStateOf(editing?.date ?: toDate) }
        var amount by remember(editing) { mutableStateOf(editing?.amount?.toString() ?: "") }
        var mode by remember(editing) { mutableStateOf(editing?.mode ?: "SET") }
        var note by remember(editing) { mutableStateOf(editing?.note.orEmpty()) }
        var error by remember { mutableStateOf("") }
        val accountEntries = dailyBalanceEntries.filter { it.account == dailyBalanceAccount }
            .sortedWith(compareByDescending<DailyBalanceEntry> { it.date }.thenByDescending { it.id })
        AlertDialog(
            onDismissRequest = { showDailyBalanceDialog = false; editingDailyBalanceEntry = null },
            title = { Text(if (dailyBalanceAccount == "CASH") "হাতে নগদের হিসাব" else "ব্যাংক ব্যালান্সের হিসাব") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("SET = ওই দিনের সন্ধ্যার প্রকৃত ব্যালান্স। যোগ/বিয়োগ = আগের ব্যালান্সের সঙ্গে সমন্বয়।", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(date, { date = it; error = "" }, label = { Text("তারিখ (YYYY-MM-DD)") }, singleLine = true)
                    Text("এন্ট্রির ধরন", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == "SET", onClick = { mode = "SET" }); Text("ব্যালান্স বসান") }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == "ADD", onClick = { mode = "ADD" }); Text("＋ যোগ") }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = mode == "SUB", onClick = { mode = "SUB" }); Text("− বিয়োগ") }
                    OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }; error = "" },
                        label = { Text(if (mode == "SET") "সন্ধ্যার মোট ব্যালান্স (৳)" else "টাকার পরিমাণ (৳)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("বিবরণ (ঐচ্ছিক)") }, singleLine = true)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (accountEntries.isNotEmpty()) {
                        HorizontalDivider()
                        Text("আগের এন্ট্রি", style = MaterialTheme.typography.titleSmall)
                        accountEntries.forEach { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.date + " • " + when(item.mode) { "SET" -> "ব্যালান্স"; "ADD" -> "যোগ"; else -> "বিয়োগ" },
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(money(item.amount) + if (item.note.isBlank()) "" else " • " + item.note, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    editingDailyBalanceEntry = item
                                    date = item.date; amount = item.amount.toString(); mode = item.mode; note = item.note
                                }) { Text("এডিট") }
                                TextButton(onClick = {
                                    if (DailyBalanceStorage.delete(context, item.id)) {
                                        dailyBalanceEntries = DailyBalanceStorage.get(context); refresh++
                                    }
                                }) { Text("বাদ") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val value = amount.trim().toDoubleOrNull()
                    if (!validLedgerDate(date)) error = "সঠিক তারিখ লিখুন (YYYY-MM-DD)।"
                    else if (value == null || !value.isFinite() || value < 0.0) error = "শূন্য বা তার বেশি সঠিক টাকার অঙ্ক লিখুন।"
                    else {
                        val entry = DailyBalanceEntry(editing?.id ?: java.util.UUID.randomUUID().toString(),
                            date, dailyBalanceAccount, mode, value, note.trim(), System.currentTimeMillis())
                        if (DailyBalanceStorage.save(context, entry)) {
                            dailyBalanceEntries = DailyBalanceStorage.get(context)
                            editingDailyBalanceEntry = null; showDailyBalanceDialog = false; refresh++
                        } else error = "এন্ট্রি সংরক্ষণ করা যায়নি।"
                    }
                }) { Text(if (editing == null) "সংরক্ষণ" else "আপডেট") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (editingDailyBalanceEntry != null) editingDailyBalanceEntry = null else showDailyBalanceDialog = false
                }) { Text(if (editingDailyBalanceEntry != null) "নতুন এন্ট্রি" else "বন্ধ") }
            }
        )
    }

    if (showOpeningBalanceDialog) {
        val saved = openingBalances
        var date by remember(saved) { mutableStateOf(saved?.date ?: fromDate) }
        var cash by remember(saved) { mutableStateOf(saved?.cash?.toString() ?: "0") }
        var bank by remember(saved) { mutableStateOf(saved?.bank?.toString() ?: "0") }
        var stock by remember(saved) { mutableStateOf(saved?.stock?.toString() ?: "0") }
        var receivable by remember(saved) { mutableStateOf(saved?.customerReceivable?.toString() ?: "0") }
        var supplierDue by remember(saved) { mutableStateOf(saved?.supplierPayable?.toString() ?: "0") }
        var otherLiabilities by remember(saved) { mutableStateOf(saved?.otherLiabilities?.toString() ?: "0") }
        var verified by remember(saved) { mutableStateOf(saved?.verified ?: false) }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOpeningBalanceDialog = false },
            title = { Text("প্রকৃত প্রারম্ভিক ব্যালান্স") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ব্যবসার শুরুর দিনের বাস্তব অঙ্ক লিখুন। বর্তমান ব্যালান্স বা অনুমান এখানে দেবেন না।", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(date, { date = it; error = "" }, label = { Text("প্রারম্ভিক তারিখ (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(cash, { cash = it; error = "" }, label = { Text("হাতে নগদ (৳)") }, singleLine = true)
                    OutlinedTextField(bank, { bank = it; error = "" }, label = { Text("ব্যাংক ব্যালান্স (৳)") }, singleLine = true)
                    OutlinedTextField(stock, { stock = it; error = "" }, label = { Text("স্টকের ক্রয়মূল্য (৳)") }, singleLine = true)
                    OutlinedTextField(receivable, { receivable = it; error = "" }, label = { Text("ক্রেতার কাছে পাওনা (৳)") }, singleLine = true)
                    OutlinedTextField(supplierDue, { supplierDue = it; error = "" }, label = { Text("সরবরাহকারীর কাছে দেনা (৳)") }, singleLine = true)
                    OutlinedTextField(otherLiabilities, { otherLiabilities = it; error = "" }, label = { Text("অন্যান্য দেনা (৳)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = verified, onCheckedChange = { verified = it })
                        Text("আমি বাস্তব হিসাব দেখে অঙ্কগুলো যাচাই করেছি")
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    fun amount(value: String) = value.trim().toDoubleOrNull()
                    val values = listOf(amount(cash), amount(bank), amount(stock), amount(receivable), amount(supplierDue), amount(otherLiabilities))
                    if (!validLedgerDate(date)) error = "সঠিক প্রারম্ভিক তারিখ লিখুন।"
                    else if (values.any { it == null || !it.isFinite() || it < 0.0 }) error = "সব ঘরে শূন্য বা তার বেশি সঠিক অঙ্ক লিখুন।"
                    else {
                        val item = OpeningBalances(date, values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!, values[5]!!, verified)
                        if (OpeningBalanceStorage.save(context, item)) {
                            openingBalances = item
                            showOpeningBalanceDialog = false
                            refresh++
                        } else error = "প্রারম্ভিক ব্যালান্স সংরক্ষণ করা যায়নি।"
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showOpeningBalanceDialog = false }) { Text("বাতিল") } }
        )
    }

    if (showAccountDialog) {
        var name by remember(editingAccount) { mutableStateOf(editingAccount?.name.orEmpty()) }
        var balance by remember(editingAccount) { mutableStateOf(editingAccount?.balance?.toString().orEmpty()) }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(if (editingAccount == null) "নতুন ক্যাশ/ব্যাংক হিসাব" else "হিসাব সম্পাদনা") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; error = "" }, label = { Text("হিসাবের নাম") }, singleLine = true)
                    OutlinedTextField(balance, { balance = it.filter { ch -> ch.isDigit() || ch == '.' }; error = "" }, label = { Text("বর্তমান ব্যালেন্স (৳)") }, singleLine = true)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = balance.toDoubleOrNull()
                    if (name.isBlank()) error = "হিসাবের নাম লিখুন।"
                    else if (amount == null || !amount.isFinite() || amount < 0.0) error = "সঠিক ব্যালেন্স লিখুন।"
                    else {
                        val account = ManualCashAccount(editingAccount?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), amount)
                        if (CashAccountStorage.saveAccount(context, account)) {
                            accounts = CashAccountStorage.getAccounts(context)
                            refresh++
                            showAccountDialog = false
                        } else error = "হিসাব সংরক্ষণ করা যায়নি।"
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showAccountDialog = false }) { Text("বাতিল") } }
        )
    }


    if (showCapitalizationDialog) {
        AlertDialog(
            onDismissRequest = { showCapitalizationDialog = false },
            title = { Text("লভ্যাংশ মূলধনে যোগ করবেন?") },
            text = {
                Text("নির্বাচিত সময়ের অবশিষ্ট লভ্যাংশ ${money(remainingDividend)} পার্টনারদের সংরক্ষিত লাভের শতাংশ অনুযায়ী ভাগ হয়ে প্রত্যেকের মূলধনে যোগ হবে। ক্যাশ/ব্যাংকের ব্যালেন্স পরিবর্তন হবে না। একই সময়ের হিসাব দ্বিতীয়বার যোগ করা যাবে না।")
            },
            confirmButton = {
                TextButton(onClick = {
                    val success = PartnerStorage.capitalizeRemainingProfit(context, fromDate, toDate, remainingDividend)
                    capitalizationMessage = if (success) "লভ্যাংশ শতাংশ অনুযায়ী পার্টনারদের মূলধনে যোগ হয়েছে।" else "লভ্যাংশ যোগ করা যায়নি। পার্টনারদের লাভের শতাংশ মোট ১০০% কি না এবং এই সময়ের হিসাব আগে যোগ হয়েছে কি না যাচাই করুন।"
                    showCapitalizationDialog = false
                    refresh++
                }) { Text("হ্যাঁ, মূলধনে যোগ করুন") }
            },
            dismissButton = { TextButton(onClick = { showCapitalizationDialog = false }) { Text("বাতিল") } }
        )
    }

    selectedSide?.let { side ->
        val rows = if (side == "ডেবিট") debitRows else partnerInvestmentRows
        AlertDialog(
            onDismissRequest = { selectedSide = null },
            title = { Text(if (side == "ক্রেডিট") "পার্টনারদের বিনিয়োগ" else "ডেবিটের সারসংক্ষেপ") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    if (side == "ডেবিট") {
                        SummaryLine("১. মোট স্টক", summary.stockValue, "বর্তমান স্টকের মোট ক্রয়মূল্য")
                        SummaryLine("২. মোট ব্যয়", unrecoveredBusinessExpenses, "দৈনিক প্রকৃত লভ্যাংশ দিয়ে সমন্বয়ের পর অবশিষ্ট ব্যয়")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { SummaryLine("৩. হাতে নগদ", cashInHand, "তারিখভিত্তিক হাতে লেখা ব্যালান্স") }
                            TextButton(onClick = { dailyBalanceAccount = "CASH"; editingDailyBalanceEntry = null; showDailyBalanceDialog = true }) { Text("＋ / −") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { SummaryLine("৪. ব্যাংকে জমা", bankBalance, "তারিখভিত্তিক ব্যাংক ব্যালান্স") }
                            TextButton(onClick = { dailyBalanceAccount = "BANK"; editingDailyBalanceEntry = null; showDailyBalanceDialog = true }) { Text("＋ / −") }
                        }
                        SummaryLine("ডেবিট সারসংক্ষেপের মোট", debitSummaryTotal)
                        Text("ব্যয় পুরোপুরি সমন্বয় হয়ে গেলে পরবর্তী দৈনিক লভ্যাংশ আর ব্যয় কমাবে না; সেটি অবশিষ্ট লভ্যাংশ হিসেবে থাকবে।", style = MaterialTheme.typography.bodySmall)
                    } else {
                        SummaryLine("পার্টনারদের মোট বিনিয়োগ", summary.totalCapital)
                        Text("নিচে শুধু পার্টনারদের মূলধন জমার এন্ট্রি দেখানো হয়েছে। পণ্য বিক্রি বা স্টকের এন্ট্রি এখানে দেখানো হবে না।", style = MaterialTheme.typography.bodySmall)
                        Text("মোট এন্ট্রি: ${rows.size}টি")
                        rows.forEach { row ->
                            HorizontalDivider()
                            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                Text(row.description, style = MaterialTheme.typography.bodyMedium)
                                Text(row.date + " • " + money(row.creditAmount), style = MaterialTheme.typography.bodySmall)
                                Text("ক্রেডিট: ${row.creditAccount}", style = MaterialTheme.typography.bodySmall)
                                if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedSide = null }) { Text("বন্ধ") } }
        )
    }
}

@Composable
private fun RowScope.LedgerTotalCard(title: String, amount: Double, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(money(amount), style = MaterialTheme.typography.titleSmall)
            Text(action, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RowScope.LedgerDividendCard(amount: Double) {
    val status = when {
        amount > 0.004 -> "লভ্যাংশ"
        amount < -0.004 -> "ঘাটতি"
        else -> "হিসাব সমান"
    }
    val container = when {
        amount > 0.004 -> MaterialTheme.colorScheme.primaryContainer
        amount < -0.004 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("লভ্যাংশ / ঘাটতি", style = MaterialTheme.typography.labelLarge)
            Text(money(kotlin.math.abs(amount)), style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: Double, detail: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Text(money(amount), style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}
