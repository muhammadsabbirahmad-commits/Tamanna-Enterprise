package com.tamanna.enterprise.reports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.SettingsStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)\nclass FinancialDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamannaTheme(com.tamanna.enterprise.settings.ThemeStorage.getTheme(this)) {
                FinancialDashboardScreen()
            }
        }
    }
}

data class DailyFinance(
    val label: String,
    val sales: Double,
    val purchase: Double,
    val profit: Double
)

@Composable
private fun FinancialDashboardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }

    val sales = remember(refresh) { SalesStorage.getSales(context) }
    val purchases = remember(refresh) { PurchaseStorage.getPurchases(context) }
    val expenses = remember(refresh) { ExpenseStorage.getExpenses(context) }
    val damages = remember(refresh) { ExpenseStorage.getDamages(context) }
    val withdrawals = remember(refresh) { ExpenseStorage.getWithdrawals(context) }
    val partners = remember(refresh) { PartnerStorage.getPartners(context) }

    val valid = fromDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        toDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && fromDate <= toDate

    val rs = if (valid) sales.filter { it.date.substringBefore(" ") in fromDate..toDate } else emptyList()
    val rp = if (valid) purchases.filter { it.date.substringBefore(" ") in fromDate..toDate } else emptyList()
    val re = if (valid) expenses.filter { it.date in fromDate..toDate } else emptyList()
    val rd = if (valid) damages.filter { it.date in fromDate..toDate } else emptyList()
    val rw = if (valid) withdrawals.filter { it.date in fromDate..toDate } else emptyList()

    val salesTotal = rs.sumOf { it.quantity * it.salePrice }
    val costOfSales = rs.sumOf { it.quantity * it.purchasePrice }
    val grossProfit = salesTotal - costOfSales
    val expenseTotal = re.sumOf { it.amount }
    val damageTotal = rd.sumOf { it.totalLoss }
    val netProfit = grossProfit - expenseTotal - damageTotal
    val purchaseTotal = rp.sumOf { it.quantity * it.purchasePrice }
    val dueCollection = 0.0
    val receivable = com.tamanna.enterprise.due.CustomerDueStorage.getBalances(context).values.sum()
    val totalPartnerShare = partners.sumOf { PartnerStorage.profitShare(kotlin.math.max(0.0, netProfit), it) }
    val withdrawalTotal = rw.sumOf { it.amount }
    val partnerRemaining = kotlin.math.max(0.0, totalPartnerShare - withdrawalTotal)

    val daily = remember(rs, rp) {
        val start = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(fromDate) }.getOrNull()
        val end = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(toDate) }.getOrNull()
        if (start == null || end == null) emptyList() else {
            val days = ((end.time - start.time) / 86400000L).toInt().coerceIn(0, 29)
            (0..days).map { offset ->
                val cal = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_YEAR, offset) }
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                DailyFinance(
                    SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time),
                    rs.filter { it.date.startsWith(key) }.sumOf { it.quantity * it.salePrice },
                    rp.filter { it.date.startsWith(key) }.sumOf { it.quantity * it.purchasePrice },
                    rs.filter { it.date.startsWith(key) }.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("আর্থিক ড্যাশবোর্ড") }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("তারিখ অনুযায়ী আর্থিক সারসংক্ষেপ", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(fromDate, { fromDate = it }, label = { Text("শুরুর তারিখ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(toDate, { toDate = it }, label = { Text("শেষ তারিখ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({ refresh++ }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("রিপোর্ট আপডেট করুন") }
            if (!valid) Text("তারিখ YYYY-MM-DD ফরম্যাটে দিন এবং শুরু তারিখ শেষ তারিখের আগে রাখুন।")

            MetricCard("মোট বিক্রয়", salesTotal)
            MetricCard("ক্রয়ের মোট মূল্য", purchaseTotal)
            MetricCard("বিক্রিত পণ্যের ক্রয়মূল্য", costOfSales)
            MetricCard("গ্রস লাভ", grossProfit)
            MetricCard("প্রতিষ্ঠানের খরচ", expenseTotal)
            MetricCard("ড্যামেজ/ক্ষতি", damageTotal)
            MetricCard("নিট লাভ", netProfit)
            MetricCard("মোট পার্টনার লাভের অংশ", totalPartnerShare)
            MetricCard("পার্টনার উত্তোলন", withdrawalTotal)
            MetricCard("পার্টনার অবশিষ্ট প্রাপ্য", partnerRemaining)
            MetricCard("মোট ক্রেতা বাকি", receivable)
            MetricCard("বাকি আদায়", dueCollection)

            if (daily.isNotEmpty()) {
                Text("বিক্রয় ও লাভের গ্রাফ", style = MaterialTheme.typography.titleLarge)
                FinanceChart(daily)
            }

            Text("হিসাব: নিট লাভ = গ্রস লাভ − খরচ − ড্যামেজ", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCard(title: String, amount: Double) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("৳ %.2f".format(Locale.US, amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FinanceChart(data: List<DailyFinance>) {
    val maxValue = data.maxOfOrNull { maxOf(it.sales, it.profit, it.purchase) }?.coerceAtLeast(1.0) ?: 1.0
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val left = 16f
                val right = size.width - 16f
                val top = 16f
                val bottom = size.height - 24f
                val width = (right - left) / (data.size.coerceAtLeast(1))
                val scale = (bottom - top) / maxValue.toFloat()

                drawLine(color = androidx.compose.ui.graphics.Color.Gray, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2f)
                drawLine(color = androidx.compose.ui.graphics.Color.Gray, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2f)

                fun point(i: Int, value: Double): Offset {
                    val x = left + width * i + width / 2f
                    val y = bottom - value.toFloat() * scale
                    return Offset(x, y)
                }

                for (i in 1 until data.size) {
                    drawLine(color = androidx.compose.ui.graphics.Color(0xFF2E7D32), start = point(i - 1, data[i - 1].sales), end = point(i, data[i].sales), strokeWidth = 5f)
                    drawLine(color = androidx.compose.ui.graphics.Color(0xFF1565C0), start = point(i - 1, data[i - 1].purchase), end = point(i, data[i].purchase), strokeWidth = 5f)
                    drawLine(color = androidx.compose.ui.graphics.Color(0xFFF9A825), start = point(i - 1, data[i - 1].profit), end = point(i, data[i].profit), strokeWidth = 5f)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("● বিক্রয়")
                Text("● ক্রয়")
                Text("● লাভ")
            }
            if (data.size <= 15) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
