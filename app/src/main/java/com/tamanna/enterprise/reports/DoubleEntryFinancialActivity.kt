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

private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
private fun monthStartIso(): String = todayIso().take(8) + "01"

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
    val totalCapital: Double,
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

private fun buildLedger(context: android.content.Context, fromDate: String, toDate: String): LedgerSummary {
    val inRange: (String) -> Boolean = { dateKey(it) >= fromDate && dateKey(it) <= toDate }
    val purchases = PurchaseStorage.getPurchases(context).filter { inRange(it.date) }
    val sales = SalesStorage.getSales(context).filter { inRange(it.date) }
    val transactions = SalesTransactionStorage.getTransactions(context).filter { inRange(it.date) }
    val expenses = ExpenseStorage.getExpenses(context).filter { inRange(it.date) }
    val damages = ExpenseStorage.getDamages(context).filter { inRange(it.date) }
    val supplierEntries = SupplierDueStorage.getEntries(context).filter { inRange(it.date) }
    val customerEntries = CustomerDueStorage.getEntries(context).filter { inRange(it.date) }
    val periodTransactionIds = transactions.map { it.transactionId }.toSet()
    val returns = SaleReturnStorage.getReturns(context).filter { inRange(it.date) && it.transactionId in periodTransactionIds }
    val returnLines = SaleReturnStorage.getLines(context).filter { line ->
        returns.any { it.returnId == line.returnId }
    }
    val withdrawals = PartnerStorage.getWithdrawals(context).filter { inRange(it.date) }
    val capitalContributions = PartnerStorage.getCapitalContributions(context).filter { inRange(it.date) }

    val rows = mutableListOf<LedgerRow>()
    val warnings = mutableListOf<String>()

    capitalContributions.forEach { contribution ->
        val partnerName = PartnerStorage.getPartners(context).firstOrNull { it.id == contribution.partnerId }?.name ?: "পুরোনো/মুছে ফেলা পার্টনার"
        rows += LedgerRow(contribution.date, "পার্টনার মূলধন জমা • $partnerName", "নগদ/ব্যাংক", "পার্টনার মূলধন", contribution.amount, contribution.note + if (contribution.approximateDate) " • তারিখ আনুমানিক" else "")
        if (contribution.approximateDate) warnings += "$partnerName-এর পুরোনো মূলধনের প্রকৃত জমার তারিখ সংরক্ষিত ছিল না; পার্টনার তৈরির তারিখকে আনুমানিক ধরা হয়েছে।"
    }

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
            if (paid > 0.004) rows += LedgerRow(
                dateKey(purchase.date), "পণ্য ক্রয় • ${purchase.productName} × ${purchase.quantity}",
                "স্টক/ইনভেন্টরি", "নগদ", paid,
                "মেমো: ${purchase.memoNumber.ifBlank { "—" }}; ক্রয়ের মোট ${money(gross)}"
            )
            if (due > 0.004) rows += LedgerRow(
                dateKey(purchase.date), "পণ্য ক্রয় বাকি • ${purchase.productName} × ${purchase.quantity}",
                "স্টক/ইনভেন্টরি", "সরবরাহকারীর দেনা", due,
                "মেমো: ${purchase.memoNumber.ifBlank { "—" }}"
            )
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
        if (paid > 0.004) rows += LedgerRow(
            dateKey(tx.date), "বিক্রয় আদায় • ${tx.customer.ifBlank { "নামহীন ক্রেতা" }}",
            "নগদ", "বিক্রয় আয়", paid, "ইনভয়েস: ${tx.transactionId}"
        )
        if (due > 0.004) rows += LedgerRow(
            dateKey(tx.date), "বিক্রয় বাকি • ${tx.customer.ifBlank { "নামহীন ক্রেতা" }}",
            "ক্রেতার পাওনা", "বিক্রয় আয়", due, "ইনভয়েস: ${tx.transactionId}"
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
    val totalCapital = PartnerStorage.getPartners(context).sumOf { it.shareAmount.coerceAtLeast(0.0) }
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
            stockWarnings += "$code: নির্বাচিত সময়ের ক্রয় দিয়ে বিক্রয়/ক্ষতি পূরণ হচ্ছে না ($quantity ইউনিট)।"
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

    // Cash movement is partial until manually entered opening cash/bank balances are reconciled.
    val purchaseDue = supplierEntries.filter { it.type == "PURCHASE" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val cashIn = transactions.sumOf { it.paid.coerceAtLeast(0.0) } +
        customerEntries.filter { it.type == "PAYMENT" }.sumOf { it.amount.coerceAtLeast(0.0) }
    val cashOut = (purchaseTotal - purchaseDue).coerceAtLeast(0.0) +
        supplierEntries.filter { it.type == "PAYMENT" }.sumOf { -it.amount } +
        expenseTotal + withdrawalTotal

    return LedgerSummary(
        rows.sortedWith(compareBy<LedgerRow> { it.date }.thenBy { it.description }),
        purchaseTotal, stockValue, stockUnits, salesTotal, collectionTotal, receivable,
        supplierPayable, expenseTotal, damageTotal, withdrawalTotal, totalCapital, netProfit,
        cashIn, cashOut, stockWarnings, warnings
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
    var accounts by remember(refresh) { mutableStateOf(CashAccountStorage.getAccounts(context)) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<ManualCashAccount?>(null) }
    val totalManualCash = accounts.sumOf { it.balance.coerceAtLeast(0.0) }
    val validRange = validLedgerDate(fromDate) && validLedgerDate(toDate) && fromDate <= toDate
    val summary = remember(refresh, fromDate, toDate) { if (validRange) buildLedger(context, fromDate, toDate) else buildLedger(context, "9999-12-31", "0001-01-01") }
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
            Text("হিসাবের সময়সীমা নির্বাচন", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromDate, { fromDate = it }, label = { Text("শুরুর তারিখ") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(toDate, { toDate = it }, label = { Text("শেষ তারিখ") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (!validRange) Text("সঠিক তারিখের পরিসর দিন (YYYY-MM-DD)।", color = MaterialTheme.colorScheme.error)
            Text("নির্বাচিত সময়: $fromDate থেকে $toDate", style = MaterialTheme.typography.bodySmall)

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

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ডেবিট–ক্রেডিট সমাধানের পর ফলাফল", style = MaterialTheme.typography.titleMedium)
                    SummaryLine("মোট ডেবিট", totalDebit)
                    SummaryLine("মোট ক্রেডিট", totalCredit)
                    SummaryLine("আপনার যোগ করা ক্যাশ/ব্যাংকে আছে", totalManualCash)
                    SummaryLine(if (summary.netProfit >= 0.0) "অতিরিক্ত লভ্যাংশ / নিট লাভ" else "নিট ক্ষতি", kotlin.math.abs(summary.netProfit))
                    Text("লভ্যাংশটি নির্বাচিত সময়ের বিক্রয়, বিক্রীত পণ্যের ক্রয়মূল্য, খরচ ও ড্যামেজের ভিত্তিতে। এটি এখনো পার্টনারদের মধ্যে বণ্টিত/উত্তোলনযোগ্য চূড়ান্ত ব্যালান্স নয়।", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("ব্যবসার সারসংক্ষেপ", style = MaterialTheme.typography.titleLarge)
            SummaryLine("মোট পণ্য ক্রয়", summary.purchaseTotal)
            SummaryLine("বর্তমান স্টক মূল্য (ক্রয়মূল্যে)", summary.stockValue, "${summary.stockUnits} ইউনিট • নির্বাচিত সময়ের নেট স্টক")
            SummaryLine("মোট বিক্রয় (রিটার্ন বাদে)", summary.salesTotal)
            SummaryLine("বিক্রয় থেকে মোট আদায়", summary.collectionTotal)
            SummaryLine("ক্রেতার কাছে পাওনা", summary.receivable)
            SummaryLine("সরবরাহকারীর কাছে দেনা", summary.supplierPayable)
            SummaryLine("মোট প্রতিষ্ঠানের খরচ", summary.expenseTotal)
            SummaryLine("ড্যামেজ/নষ্টের ক্ষতি", summary.damageTotal)
            SummaryLine("পার্টনারের উত্তোলন", summary.withdrawalTotal)

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

            Text("পার্টনারদের মূলধন", style = MaterialTheme.typography.titleLarge)
            SummaryLine("পার্টনারদের মোট জমাকৃত মূলধন", summary.totalCapital, "বর্তমান মোট মূলধন; নির্বাচিত সময়ে জমা হওয়া মূলধন ডেবিট–ক্রেডিট তালিকায় আলাদাভাবে আছে")

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
