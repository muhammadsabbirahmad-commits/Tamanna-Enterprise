package com.tamanna.enterprise.reports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.ThemeStorage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

data class CalendarDay(
    val date: String,
    val sales: Double,
    val purchase: Double,
    val grossProfit: Double,
    val expense: Double,
    val damage: Double,
    val netProfit: Double,
    val partnerShare: Double,
    val withdrawal: Double,
    val dueCollection: Double
)

@OptIn(ExperimentalMaterial3Api::class)
class FinancialCalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { FinancialCalendarScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FinancialCalendarScreen() {
        val context = this@FinancialCalendarActivity
        val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        var from by remember { mutableStateOf(today) }
        var to by remember { mutableStateOf(today) }
        var refresh by remember { mutableIntStateOf(0) }

        val sales = remember(refresh) { SalesStorage.getSales(context) }
        val purchases = remember(refresh) { PurchaseStorage.getPurchases(context) }
        val expenses = remember(refresh) { ExpenseStorage.getExpenses(context) }
        val damages = remember(refresh) { ExpenseStorage.getDamages(context) }
        val withdrawals = remember(refresh) { ExpenseStorage.getWithdrawals(context) }
        val dueEntries = remember(refresh) { CustomerDueStorage.getEntries(context) }
        val partners = remember(refresh) { PartnerStorage.getPartners(context) }

        val valid = from.length == 10 && to.length == 10 && from <= to
        fun inRange(value: String) = value.substringBefore(" ") in from..to

        val rangeSales = if (valid) sales.filter { inRange(it.date) } else emptyList()
        val rangePurchases = if (valid) purchases.filter { inRange(it.date) } else emptyList()
        val rangeExpenses = if (valid) expenses.filter { inRange(it.date) } else emptyList()
        val rangeDamages = if (valid) damages.filter { inRange(it.date) } else emptyList()
        val rangeWithdrawals = if (valid) withdrawals.filter { inRange(it.date) } else emptyList()
        val rangeDue = if (valid) dueEntries.filter { inRange(it.date) } else emptyList()

        val salesAmount = rangeSales.sumOf { it.quantity * it.salePrice }
        val costOfSales = rangeSales.sumOf { it.quantity * it.purchasePrice }
        val purchaseAmount = rangePurchases.sumOf { it.quantity * it.purchasePrice }
        val grossProfit = salesAmount - costOfSales
        val expenseAmount = rangeExpenses.sumOf { it.amount }
        val damageAmount = rangeDamages.sumOf { it.totalLoss }
        val netProfit = grossProfit - expenseAmount - damageAmount
        val partnerShare = max(0.0, partners.sumOf { PartnerStorage.profitShare(netProfit, it) })
        val withdrawalAmount = rangeWithdrawals.sumOf { it.amount }
        val dueCollection = rangeDue.filter { it.type.equals("PAYMENT", true) }.sumOf { it.amount }

        val days = if (valid) buildDays(
            from, to, rangeSales, rangePurchases, rangeExpenses, rangeDamages,
            rangeWithdrawals, rangeDue, partners
        ) else emptyList()

        Scaffold(
            topBar = { TopAppBar(title = { Text("আর্থিক ক্যালেন্ডার") }) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(from, { from = it }, label = { Text("শুরু") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(to, { to = it }, label = { Text("শেষ") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button({ refresh++ }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("হিসাব আপডেট করুন") }
                }
                item { MetricCard("মোট বিক্রয়", salesAmount) }
                item { MetricCard("মোট ক্রয়", purchaseAmount) }
                item { MetricCard("গ্রস লাভ", grossProfit) }
                item { MetricCard("খরচ", expenseAmount) }
                item { MetricCard("ক্ষতি/ড্যামেজ", damageAmount) }
                item { MetricCard("নিট লাভ", netProfit) }
                item { MetricCard("পার্টনারদের লাভের অংশ", partnerShare) }
                item { MetricCard("পার্টনার উত্তোলন", withdrawalAmount) }
                item { MetricCard("ক্রেতার বাকি আদায়", dueCollection) }
                item { Text("দিনভিত্তিক হিসাব", style = MaterialTheme.typography.titleLarge) }
                if (days.isEmpty()) {
                    item { Text(if (valid) "এই সময়ে কোনো লেনদেন নেই।" else "তারিখ সঠিকভাবে দিন।") }
                } else {
                    items(days) { d ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(d.date, style = MaterialTheme.typography.titleMedium)
                                Text("বিক্রয় ৳ %.2f • ক্রয় ৳ %.2f".format(Locale.US, d.sales, d.purchase))
                                Text("গ্রস লাভ ৳ %.2f • খরচ ৳ %.2f".format(Locale.US, d.grossProfit, d.expense))
                                Text("ড্যামেজ ৳ %.2f • নিট লাভ ৳ %.2f".format(Locale.US, d.damage, d.netProfit))
                                Text("পার্টনার অংশ ৳ %.2f • উত্তোলন ৳ %.2f".format(Locale.US, d.partnerShare, d.withdrawal))
                                Text("বাকি আদায় ৳ %.2f".format(Locale.US, d.dueCollection))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "সূত্র: গ্রস লাভ = বিক্রয় − বিক্রিত পণ্যের ক্রয়মূল্য; নিট লাভ = গ্রস লাভ − খরচ − ড্যামেজ।",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, amount: Double) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text("৳ %.2f".format(Locale.US, amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun buildDays(
    from: String,
    to: String,
    sales: List<com.tamanna.enterprise.sales.Sale>,
    purchases: List<com.tamanna.enterprise.purchase.Purchase>,
    expenses: List<com.tamanna.enterprise.finance.Expense>,
    damages: List<com.tamanna.enterprise.finance.DamageRecord>,
    withdrawals: List<com.tamanna.enterprise.finance.PartnerWithdrawal>,
    dueEntries: List<com.tamanna.enterprise.due.DueEntry>,
    partners: List<com.tamanna.enterprise.partner.Partner>
): List<CalendarDay> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val start = runCatching { fmt.parse(from) }.getOrNull() ?: return emptyList()
    val end = runCatching { fmt.parse(to) }.getOrNull() ?: return emptyList()
    val out = mutableListOf<CalendarDay>()
    val cal = Calendar.getInstance().apply { time = start }
    while (!cal.time.after(end)) {
        val date = fmt.format(cal.time)
        val ds = sales.filter { it.date.startsWith(date) }
        val dp = purchases.filter { it.date.startsWith(date) }
        val de = expenses.filter { it.date.startsWith(date) }
        val dd = damages.filter { it.date.startsWith(date) }
        val dw = withdrawals.filter { it.date.startsWith(date) }
        val due = dueEntries.filter { it.date.startsWith(date) && it.type.equals("PAYMENT", true) }
        val sale = ds.sumOf { it.quantity * it.salePrice }
        val cost = ds.sumOf { it.quantity * it.purchasePrice }
        val gross = sale - cost
        val exp = de.sumOf { it.amount }
        val damage = dd.sumOf { it.totalLoss }
        val net = gross - exp - damage
        val share = max(0.0, partners.sumOf { PartnerStorage.profitShare(net, it) })
        out.add(CalendarDay(date, sale, dp.sumOf { it.quantity * it.purchasePrice }, gross, exp, damage, net, share, dw.sumOf { it.amount }, due.sumOf { it.amount }))
        cal.add(Calendar.DATE, 1)
    }
    return out
}
