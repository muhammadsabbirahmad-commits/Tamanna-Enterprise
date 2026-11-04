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
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.purchase.SupplierDueStorage
import com.tamanna.enterprise.sales.SaleReturnStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.sales.SalesTransactionStorage
import java.text.SimpleDateFormat
import java.util.Locale

private const val LEDGER_START_DATE = "2026-10-20"

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
    val amount: Double,
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
    val damageTotal: Double,
    val withdrawalTotal: Double,
    val netProfit: Double,
    val cashIn: Double,
    val cashOut: Double,
    val stockWarnings: List<String>,
    val dataWarnings: List<String>
)

private fun dateKey(value: String): String = value.trim().take(10)

private fun validLedgerDate(value: String): Boolean = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val parsed = parser.parse(value) ?: return false
    parser.format(parsed) == value
}.getOrDefault(false)

private fun money(value: Double): String = "৳ " + String.format(Locale.US, "%,.2f", value)

private fun buildLedger(context: android.content.Context): LedgerSummary {
    val purchases = PurchaseStorage.getPurchases(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val sales = SalesStorage.getSales(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val transactions = SalesTransactionStorage.getTransactions(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val expenses = ExpenseStorage.getExpenses(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val damages = ExpenseStorage.getDamages(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val supplierEntries = SupplierDueStorage.getEntries(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val customerEntries = CustomerDueStorage.getEntries(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val returns = SaleReturnStorage.getReturns(context).filter { dateKey(it.date) >= LEDGER_START_DATE }
    val returnLines = SaleReturnStorage.getLines(context).filter { line ->
        returns.any { it.returnId == line.returnId }
    }
    val withdrawals = PartnerStorage.getWithdrawals(context).filter { dateKey(it.date) >= LEDGER_START_DATE }

    val rows = mutableListOf<LedgerRow>()
    val warnings = mutableListOf<String>()

    // Allocate supplier credit notes proportionally to purchases made for that supplier on the same day.
    val purchaseGroups = purchases.groupBy { dateKey(it.date) + "|" + it.supplier.trim().lowercase(Locale.ROOT) }
    purchaseGroups.forEach { (groupKey, group) ->
        val split = groupKey.split("|", limit = 2)
        val dueForGroup = supplierEntries.filter {
            it.type == "PURCHASE" && dateKey(it.date) == split[0] &&
                it.supplier.trim().equals(group.first().supplier.trim(), true)
        }.sumOf { it.amount.coerceAtLeast(0.0) }.coerceAtMost(group.sumOf { it.quantity * it.purchasePrice })
        val groupTotal = group.sumOf { it.quantity * it.purchasePrice }
        var allocatedDue = 0.0
        group.forEachIndexed { index, purchase ->
            val gross = (purchase.quantity * purchase.purchasePrice).coerceAtLeast(0.0)
            val due = if (index == group.lastIndex) (dueForGroup - allocatedDue).coerceAtLeast(0.0)
                else if (groupTotal > 0.0) (dueForGroup * gross / groupTotal).coerceAtMost(gross) else 0.0
            allocatedDue += due
            val paid = (gross - due).coerceAtLeast(0.0)
            if (gross > 0.0) {
                rows += LedgerRow(
                    dateKey(purchase.date), "পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity}",
                    "স্টক/ইনভেন্টরি", if (due > 0.004 && paid > 0.004) "নগদ ও সরবরাহকারী বাকি"
                    else if (due > 0.004) "সরবরাহকারী বাকি" else "নগদ",
                    gross, "মেমো: ${purchase.memoNumber.ifBlank { "—" }}; পরিশোধিত আনুমানিক ${money(paid)}, বাকি ${money(due)}"
                )
            }
        }
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
        rows += LedgerRow(
            dateKey(tx.date), "বিক্রয় • ${tx.customer.ifBlank { "নামহীন ক্রেতা" }}",
            if (due > 0.004 && paid > 0.004) "নগদ ও ক্রেতার পাওনা" else if (due > 0.004) "ক্রেতার পাওনা" else "নগদ",
            "বিক্রয় আয়", gross, "ইনভয়েস: ${tx.transactionId}; জমা ${money(paid)}, বাকি ${money(due)}"
        )
    }
    val transactionIds = transactions.map { it.transactionId }.toSet()
    sales.filter { it.transactionId !in transactionIds }.groupBy { it.transactionId }.forEach { (id, items) ->
        warnings += "বিক্রয় লাইন $id আছে, কিন্তু বিক্রয়ের মূল পেমেন্ট রেকর্ড নেই; তাই ডেবিট-ক্রেডিটে যোগ করা হয়নি।"
    }

    // Cost of goods sold, separately from revenue.
    sales.forEach { sale ->
        val returnedQty = returnLines.filter {
            it.transactionId == sale.transactionId && it.productCode.equals(sale.productCode, true)
        }.sumOf { it.quantity }
        val netQty = (sale.quantity - returnedQty).coerceAtLeast(0)
        val cost = netQty * sale.purchasePrice
        if (cost > 0.0) rows += LedgerRow(
            dateKey(sale.date), "বিক্রিত পণ্যের ক্রয়মূল্য • ${sale.productName} × $netQty",
            "বিক্রিত পণ্যের খরচ", "স্টক/ইনভেন্টরি", cost
        )
    }

    returns.forEach { ret ->
        val refunded = ret.refundAmount.coerceAtLeast(0.0)
        val dueReduction = ret.dueReduction.coerceAtLeast(0.0)
        if (refunded > 0.0) rows += LedgerRow(
            dateKey(ret.date), "বিক্রয় ফেরত • নগদ ফেরত", "বিক্রয় ফেরত", "নগদ", refunded,
            "রিটার্ন: ${ret.returnId}"
        )
        if (dueReduction > 0.0) rows += LedgerRow(
            dateKey(ret.date), "বিক্রয় ফেরত • ক্রেতার বাকি কমেছে", "বিক্রয় ফেরত", "ক্রেতার পাওনা", dueReduction,
            "রিটার্ন: ${ret.returnId}"
        )
        if (kotlin.math.abs(ret.amount - refunded - dueReduction) > 0.02) {
            warnings += "রিটার্ন ${ret.returnId}: ফেরতের মোট টাকা ও নগদ ফেরত/বাকি সমন্বয়ের অঙ্ক মেলেনি।"
        }
        returnLines.filter { it.returnId == ret.returnId }.forEach { line ->
            val originalCost = sales.firstOrNull {
                it.transactionId == line.transactionId && it.productCode.equals(line.productCode, true)
            }?.purchasePrice ?: 0.0
            val costValue = line.quantity * originalCost
            if (costValue > 0.0) rows += LedgerRow(
                dateKey(ret.date), "ফেরত পণ্য স্টকে • ${line.productName} × ${line.quantity}",
                "স্টক/ইনভেন্টরি", "বিক্রিত পণ্যের খরচ", costValue
            )
        }
    }

    // Customer payments against previously recorded receivables.
    customerEntries.filter { it.type == "PAYMENT" && it.amount > 0.0 }.forEach { entry ->
        rows += LedgerRow(
            dateKey(entry.date), "ক্রেতার বাকি আদায় • ${entry.customer}",
            "নগদ", "ক্রেতার পাওনা", entry.amount, entry.note
        )
    }

    expenses.forEach { expense ->
        if (expense.amount > 0.0) rows += LedgerRow(
            dateKey(expense.date), "প্রতিষ্ঠানের খরচ • ${expense.category}",
            "প্রতিষ্ঠানের খরচ", "নগদ", expense.amount, expense.note
        )
    }
    damages.forEach { damage ->
        if (damage.totalLoss > 0.0) rows += LedgerRow(
            dateKey(damage.date), "ড্যামেজ/নষ্ট • ${damage.productName}",
            "ড্যামেজ ক্ষতি", "স্টক/ইনভেন্টরি", damage.totalLoss, damage.reason
        )
    }

    supplierEntries.filter { it.type == "PAYMENT" && it.amount < 0.0 }.forEach { entry ->
        rows += LedgerRow(
            dateKey(entry.date), "সরবরাহকারীকে পরিশোধ • ${entry.supplier}",
            "সরবরাহকারীর দেনা", "নগদ", -entry.amount, entry.note
        )
    }
    withdrawals.forEach { withdrawal ->
        val partner = PartnerStorage.getPartners(context).firstOrNull { it.id == withdrawal.partnerId }?.name ?: "পার্টনার"
        rows += LedgerRow(
            dateKey(withdrawal.date), "পার্টনার উত্তোলন • $partner",
            "পার্টনার উত্তোলন", "নগদ", withdrawal.amount, withdrawal.note
        )
    }

    val purchaseTotal = purchases.sumOf { it.quantity * it.purchasePrice }
    val salesTotal = transactions.sumOf { it.total.coerceAtLeast(0.0) } - returns.sumOf { it.amount.coerceAtLeast(0.0) }
    val collectionTotal = transactions.sumOf { it.paid.coerceAtLeast(0.0) } +
        customerEntries.filter { it.type == "PAYMENT" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val receivable = customerEntries.sumOf { if (it.type == "SALE") it.amount else -it.amount }.coerceAtLeast(0.0)
    val supplierPayable = supplierEntries.sumOf { it.amount }.coerceAtLeast(0.0)
    val expenseTotal = expenses.sumOf { it.amount.coerceAtLeast(0.0) }
    val damageTotal = damages.sumOf { it.totalLoss.coerceAtLeast(0.0) }
    val withdrawalTotal = withdrawals.sumOf { it.amount.coerceAtLeast(0.0) }
    val cogs = sales.sumOf { sale ->
        val returned = returnLines.filter { it.transactionId == sale.transactionId && it.productCode.equals(sale.productCode, true) }.sumOf { it.quantity }
        (sale.quantity - returned).coerceAtLeast(0) * sale.purchasePrice
    }
    val returnedCost = returnLines.sumOf { line ->
        line.quantity * (sales.firstOrNull { it.transactionId == line.transactionId && it.productCode.equals(line.productCode, true) }?.purchasePrice ?: 0.0)
    }
    val netProfit = salesTotal - cogs - expenseTotal - damageTotal

    // Reconstruct only stock movements dated on/after the requested start date.
    val stockWarnings = mutableListOf<String>()
    val stockByCode = mutableMapOf<String, Int>()
    val costByCode = mutableMapOf<String, Double>()
    purchases.forEach {
        stockByCode[it.productCode] = (stockByCode[it.productCode] ?: 0) + it.quantity
        costByCode[it.productCode] = (costByCode[it.productCode] ?: 0.0) + it.quantity * it.purchasePrice
    }
    sales.forEach {
        stockByCode[it.productCode] = (stockByCode[it.productCode] ?: 0) - it.quantity
    }
    returnLines.forEach {
        stockByCode[it.productCode] = (stockByCode[it.productCode] ?: 0) + it.quantity
    }
    damages.forEach {
        stockByCode[it.productCode] = (stockByCode[it.productCode] ?: 0) - it.quantity
    }
    val currentProducts = ProductStorage.getProducts(context).associateBy { it.code.lowercase(Locale.ROOT) }
    val stockValue = stockByCode.entries.sumOf { (code, quantity) ->
        if (quantity < 0) {
            stockWarnings += "$code: ২০ অক্টোবরের পরের ক্রয় দিয়ে বিক্রয়/ক্ষতি পূরণ হচ্ছে না ($quantity ইউনিট)।"
            0.0
        } else {
            val unitCost = currentProducts[code.lowercase(Locale.ROOT)]?.purchasePrice
                ?: run {
                    val units = purchases.filter { it.productCode.equals(code, true) }.sumOf { it.quantity }
                    if (units > 0) costByCode[code]?.div(units) ?: 0.0 else 0.0
                }
            quantity * unitCost
        }
    }
    val stockUnits = stockByCode.values.sumOf { it.coerceAtLeast(0) }

    // Cash movement is a partial ledger only: dated partner-capital deposits and opening cash are not stored.
    val purchaseDue = supplierEntries.filter { it.type == "PURCHASE" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val cashIn = transactions.sumOf { it.paid.coerceAtLeast(0.0) } +
        customerEntries.filter { it.type == "PAYMENT" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val cashOut = (purchaseTotal - purchaseDue).coerceAtLeast(0.0) +
        supplierEntries.filter { it.type == "PAYMENT" }.sumOf { -it.amount } +
        expenseTotal + withdrawalTotal

    return LedgerSummary(
        rows.sortedWith(compareBy<LedgerRow> { it.date }.thenBy { it.description }),
        purchaseTotal, stockValue, stockUnits, salesTotal, collectionTotal, receivable,
        supplierPayable, expenseTotal, damageTotal, withdrawalTotal, netProfit,
        cashIn, cashOut, stockWarnings, warnings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoubleEntryFinancialScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var selectedSide by remember { mutableStateOf<String?>(null) }
    val summary = remember(refresh) { buildLedger(context) }
    val totalDebit = summary.rows.sumOf { it.amount }
    val totalCredit = summary.rows.sumOf { it.amount }
    val debitRows = summary.rows
    val creditRows = summary.rows

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
            Text("হিসাবের সময়কাল: ২০ অক্টোবর ২০২৬ থেকে", style = MaterialTheme.typography.titleMedium)
            Text("২০ অক্টোবরের আগের কোনো স্টক বা লেনদেন এই রিপোর্টে যোগ হয়নি।", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LedgerTotalCard("মোট ডেবিট", totalDebit, "বিস্তারিত দেখুন") { selectedSide = "ডেবিট" }
                LedgerTotalCard("মোট ক্রেডিট", totalCredit, "বিস্তারিত দেখুন") { selectedSide = "ক্রেডিট" }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("ডেবিট–ক্রেডিট যাচাই", style = MaterialTheme.typography.titleMedium)
                    Text(if (kotlin.math.abs(totalDebit - totalCredit) < 0.01) "জার্নাল এন্ট্রিগুলো সমান: ${money(totalDebit)}"
                    else "অমিল: ${money(kotlin.math.abs(totalDebit - totalCredit))}")
                    Text("প্রতিটি সংরক্ষিত এন্ট্রিতে সমপরিমাণ ডেবিট ও ক্রেডিট দেখানো হয়।", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("ব্যবসার সারসংক্ষেপ", style = MaterialTheme.typography.titleLarge)
            SummaryLine("মোট পণ্য ক্রয়", summary.purchaseTotal)
            SummaryLine("বর্তমান স্টক মূল্য (ক্রয়মূল্যে)", summary.stockValue, "${summary.stockUnits} ইউনিট • ২০ অক্টোবরের পরের নেট স্টক")
            SummaryLine("মোট বিক্রয় (রিটার্ন বাদে)", summary.salesTotal)
            SummaryLine("বিক্রয় থেকে মোট আদায়", summary.collectionTotal)
            SummaryLine("ক্রেতার কাছে পাওনা", summary.receivable)
            SummaryLine("সরবরাহকারীর কাছে দেনা", summary.supplierPayable)
            SummaryLine("মোট প্রতিষ্ঠানের খরচ", summary.expenseTotal)
            SummaryLine("ড্যামেজ/নষ্টের ক্ষতি", summary.damageTotal)
            SummaryLine("পার্টনারের উত্তোলন", summary.withdrawalTotal)
            SummaryLine("নিট লাভ/ক্ষতি", summary.netProfit)

            Text("নগদ প্রবাহ", style = MaterialTheme.typography.titleLarge)
            SummaryLine("নথিভুক্ত নগদ এসেছে", summary.cashIn)
            SummaryLine("নথিভুক্ত নগদ গেছে", summary.cashOut)
            SummaryLine("নথিভুক্ত নিট নগদ", summary.cashIn - summary.cashOut)
            Text("এটি হাতে থাকা প্রকৃত নগদ নয়। ২০ অক্টোবরের আগের নগদ ও তারিখসহ পার্টনার মূলধন জমার রেকর্ড না থাকায় প্রকৃত ক্যাশ ব্যালেন্স নিশ্চিত করা যাচ্ছে না।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Text("মূলধন", style = MaterialTheme.typography.titleLarge)
            Text("পার্টনারের বর্তমান মূলধনের তথ্য আছে, কিন্তু মূলধন জমার তারিখ সংরক্ষিত নেই। তাই ২০ অক্টোবরের আগের মূলধন ভুল করে এই সময়ের হিসাবে যোগ করা হয়নি।", style = MaterialTheme.typography.bodySmall)

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

    selectedSide?.let { side ->
        val rows = if (side == "ডেবিট") debitRows else creditRows
        AlertDialog(
            onDismissRequest = { selectedSide = null },
            title = { Text("$side লেনদেনের বিস্তারিত") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text("মোট এন্ট্রি: ${rows.size}টি")
                    rows.forEach { row ->
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Text(row.description, style = MaterialTheme.typography.bodyMedium)
                            Text(row.date + " • " + money(row.amount), style = MaterialTheme.typography.bodySmall)
                            Text(if (side == "ডেবিট") "ডেবিট: ${row.debitAccount}" else "ক্রেডিট: ${row.creditAccount}", style = MaterialTheme.typography.bodySmall)
                            if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodySmall)
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
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(money(amount), style = MaterialTheme.typography.titleLarge)
            Text(action, style = MaterialTheme.typography.labelSmall)
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
