package com.tamanna.enterprise.reports

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.finance.ExpenseStorage
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.purchase.SupplierDueStorage
import com.tamanna.enterprise.sales.SalesStorage
import java.text.SimpleDateFormat
import java.util.*

class ReportsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReportsScreen() }
    }
}

private data class ReportOption(val key: String, val title: String)

@Composable
private fun ReportsScreen() {
    val context = LocalContext.current
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var fromDate by remember { mutableStateOf(today) }
    var toDate by remember { mutableStateOf(today) }
    val options = remember {
        listOf(
            ReportOption("stock", "পণ্য ও বর্তমান স্টক"),
            ReportOption("purchase", "ক্রয় হিসাব"),
            ReportOption("sales", "বিক্রয় হিসাব"),
            ReportOption("profit", "লাভের হিসাব"),
            ReportOption("partners", "পার্টনার বিনিয়োগ ও লাভের অংশ"),
            ReportOption("withdrawal", "পার্টনার উত্তোলন"),
            ReportOption("expense", "ব্যবসার খরচ"),
            ReportOption("damage", "নষ্ট/ড্যামেজ পণ্য"),
            ReportOption("customerDue", "কাস্টমারের বাকি"),
            ReportOption("supplierDue", "সাপ্লায়ারের বাকি")
        )
    }
    val selected = remember { mutableStateMapOf<String, Boolean>().apply { options.forEach { this[it.key] = true } } }
    var message by remember { mutableStateOf("") }

    fun chooseDate(isFrom: Boolean) {
        val current = (if (isFrom) fromDate else toDate).split("-")
        val y = current.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val m = (current.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val d = current.getOrNull(2)?.toIntOrNull() ?: 1
        DatePickerDialog(context, { _, year, month, day ->
            val value = "%04d-%02d-%02d".format(year, month + 1, day)
            if (isFrom) fromDate = value else toDate = value
        }, y, m, d).show()
    }

    val validRange = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(fromDate)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(toDate)
        fromDate <= toDate
    }.getOrDefault(false)

    fun inRange(date: String): Boolean = date.substringBefore(" ") in fromDate..toDate

    val sales = SalesStorage.getSales(context).filter { inRange(it.date) }
    val purchases = PurchaseStorage.getPurchases(context).filter { inRange(it.date) }
    val products = ProductStorage.getProducts(context)
    val expenses = ExpenseStorage.getExpenses(context).filter { inRange(it.date) }
    val withdrawals = ExpenseStorage.getWithdrawals(context).filter { inRange(it.date) }
    val damages = ExpenseStorage.getDamages(context).filter { inRange(it.date) }
    val customerDue = CustomerDueStorage.getEntries(context).filter { inRange(it.date) }
    val supplierDue = SupplierDueStorage.getEntries(context).filter { inRange(it.date) }
    val partners = PartnerStorage.getPartners(context)

    val canExport = validRange && selected.values.any { it }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("PDF রিপোর্ট কাস্টমাইজ", style = MaterialTheme.typography.headlineSmall)
                    Text("তারিখ নির্বাচন করুন এবং যে হিসাব চান শুধু সেগুলোতে টিক দিন।")
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { chooseDate(true) }, Modifier.weight(1f)) { Text("শুরু: " + fromDate) }
                        OutlinedButton(onClick = { chooseDate(false) }, Modifier.weight(1f)) { Text("শেষ: " + toDate) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { options.forEach { selected[it.key] = true } }, Modifier.weight(1f)) { Text("☑ সবগুলো") }
                        OutlinedButton(onClick = { options.forEach { selected[it.key] = false } }, Modifier.weight(1f)) { Text("সব বাদ") }
                    }
                }
                items(options.size) { index ->
                    val option = options[index]
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Checkbox(
                                checked = selected[option.key] == true,
                                onCheckedChange = { selected[option.key] = it }
                            )
                            Text(option.title, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            message = ReportPdfExporter.exportToDownloads(
                                context, fromDate, toDate,
                                selected = selected.filterValues { it }.keys,
                                sales = sales, purchases = purchases, products = products,
                                partners = partners, expenses = expenses, withdrawals = withdrawals,
                                damages = damages, customerDue = customerDue, supplierDue = supplierDue
                            )
                        },
                        enabled = canExport,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📄 PDF তৈরি ও Download") }
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(message)
                    }
                }
            }
        }
    }
}
