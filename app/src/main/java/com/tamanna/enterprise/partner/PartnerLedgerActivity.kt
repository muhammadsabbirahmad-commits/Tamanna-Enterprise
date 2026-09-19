package com.tamanna.enterprise.partner

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.core.content.FileProvider
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.finance.PartnerWithdrawal
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.ThemeStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class PartnerLedgerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val partnerId = intent.getLongExtra("partner_id", -1L)
        setContent { TamannaTheme(ThemeStorage.getTheme(this)) { PartnerLedgerScreen(this, partnerId) } }
    }
}

private fun day(value: String) = value.substringBefore(" ")
private fun inRange(value: String, from: String, to: String) = day(value) in from..to

@Composable
private fun PartnerLedgerScreen(activity: ComponentActivity, partnerId: Long) {
    val partner = PartnerStorage.getPartners(activity).firstOrNull { it.id == partnerId }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today) }
    var refresh by remember { mutableIntStateOf(0) }

    if (partner == null) {
        Surface(Modifier.fillMaxSize()) { Text("পার্টনার পাওয়া যায়নি।", Modifier.padding(20.dp)) }
        return
    }

    val sales = remember(refresh) { SalesStorage.getSales(activity) }
    val expenses = remember(refresh) { ExpenseStorage.getExpenses(activity) }
    val damages = remember(refresh) { ExpenseStorage.getDamages(activity) }
    val withdrawals = remember(refresh) { ExpenseStorage.getWithdrawals(activity) }
    val valid = from.length == 10 && to.length == 10 && from <= to
    val rangeSales = if (valid) sales.filter { inRange(it.date, from, to) } else emptyList()
    val salesAmount = rangeSales.sumOf { it.quantity * it.salePrice }
    val costOfSales = rangeSales.sumOf { it.quantity * it.purchasePrice }
    val grossProfit = salesAmount - costOfSales
    val expenseAmount = if (valid) expenses.filter { inRange(it.date, from, to) }.sumOf { it.amount } else 0.0
    val damageAmount = if (valid) damages.filter { inRange(it.date, from, to) }.sumOf { it.totalLoss } else 0.0
    val netProfit = grossProfit - expenseAmount - damageAmount
    val share = max(0.0, PartnerStorage.profitShare(netProfit, partner))
    val periodWithdrawals = if (valid) withdrawals.filter { it.partnerId == partner.id && inRange(it.date, from, to) } else emptyList()
    val withdrawn = periodWithdrawals.sumOf { it.amount }
    val remaining = max(0.0, share - withdrawn)

    Scaffold(topBar = { TopAppBar(title = { Text("পার্টনার লেজার — " + partner.name) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("শুরু") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("শেষ") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Button({ refresh++ }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("লেজার আপডেট করুন") }
            Spacer(Modifier.height(10.dp))
            Text("বিনিয়োগ: ৳ %.2f".format(Locale.US, partner.investment))
            Text("লাভের অংশ: %.2f%%".format(Locale.US, partner.percentage))
            Text("মোট বিক্রয়: ৳ %.2f".format(Locale.US, salesAmount))
            Text("নিট লাভ: ৳ %.2f".format(Locale.US, netProfit))
            Text("পার্টনারের লাভের অংশ: ৳ %.2f".format(Locale.US, share))
            Text("উত্তোলন: ৳ %.2f".format(Locale.US, withdrawn))
            Text("উত্তোলনের পর পাওনা: ৳ %.2f".format(Locale.US, remaining), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { shareStatement(activity, partner, from, to, netProfit, share, withdrawn, remaining, periodWithdrawals) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("স্টেটমেন্ট PDF শেয়ার করুন") }
            Spacer(Modifier.height(10.dp))
            Text("উত্তোলনের ইতিহাস", style = MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(periodWithdrawals) { w ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(w.date + " — ৳ " + "%.2f".format(Locale.US, w.amount) + " — " + w.method)
                            if (w.note.isNotBlank()) Text(w.note)
                        }
                    }
                }
            }
        }
    }
}

private fun shareStatement(
    context: ComponentActivity,
    partner: Partner,
    from: String,
    to: String,
    netProfit: Double,
    share: Double,
    withdrawn: Double,
    remaining: Double,
    withdrawals: List<PartnerWithdrawal>
) {
    val pdf = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val canvas = page.canvas
    var y = 48f
    fun line(s: String) { canvas.drawText(s.take(90), 32f, y, paint); y += 20f }
    canvas.drawText("Tamanna Enterprise - Partner Statement", 32f, y, title)
    y += 30f
    line("Partner: " + partner.name)
    line("Period: " + from + " to " + to)
    line("Investment: ৳ %.2f".format(Locale.US, partner.investment))
    line("Profit share: %.2f%%".format(Locale.US, partner.percentage))
    line("Net profit: ৳ %.2f".format(Locale.US, netProfit))
    line("Partner profit share: ৳ %.2f".format(Locale.US, share))
    line("Withdrawals: ৳ %.2f".format(Locale.US, withdrawn))
    line("Remaining receivable: ৳ %.2f".format(Locale.US, remaining))
    y += 12f
    line("WITHDRAWAL HISTORY")
    withdrawals.forEach { line(it.date + " | ৳ %.2f | ".format(Locale.US, it.amount) + it.method) }
    pdf.finishPage(page)
    val dir = File(context.cacheDir, "partner_statements").apply { mkdirs() }
    val file = File(dir, "partner_" + partner.id + "_" + from + "_to_" + to + ".pdf")
    FileOutputStream(file).use { pdf.writeTo(it) }
    pdf.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "পার্টনার স্টেটমেন্ট শেয়ার করুন"))
}
