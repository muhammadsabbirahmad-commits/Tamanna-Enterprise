package com.tamanna.enterprise.sales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.security.SecurityStorage
import java.util.Locale

class SalesActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        setContent {
            SalesScreen {
                startActivity(Intent(this, NewSaleActivity::class.java))
            }
        }
    }
}

private data class SaleGroup(
    val transactionId: String,
    val lines: List<Sale>,
    val transaction: SaleTransaction?
)

@androidx.compose.runtime.Composable
private fun SalesScreen(onNewSale: () -> Unit) {
    val context = LocalContext.current
    var selectedGroup by remember { mutableStateOf<SaleGroup?>(null) }

    val sales = remember { SalesStorage.getSales(context) }
    val transactions = remember { SalesTransactionStorage.getTransactions(context) }

    val groups = sales.groupBy { it.transactionId }.values
        .map { lines ->
            val id = lines.first().transactionId
            SaleGroup(id, lines.sortedBy { it.id }, transactions.firstOrNull { it.transactionId == id })
        }
        .sortedByDescending { it.lines.maxOfOrNull { line -> line.id } ?: 0L }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("বিক্রয় ইতিহাস", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = onNewSale) { Text("নতুন বিক্রয়") }
                }
                Spacer(Modifier.height(12.dp))

                if (groups.isEmpty()) {
                    Text("এখনো কোনো বিক্রয় রেকর্ড নেই।")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(groups, key = { it.transactionId }) { group ->
                            val tx = group.transaction
                            val returnedAmount = SaleReturnStorage.getReturnedAmount(context, group.transactionId)
                            Card(Modifier.fillMaxWidth().clickable { selectedGroup = group }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("ইনভয়েস: " + group.transactionId, style = MaterialTheme.typography.titleMedium)
                                    Text((tx?.date ?: group.lines.first().date) + "  •  " + group.lines.size + "টি পণ্য")
                                    if (!tx?.customer.isNullOrBlank()) Text("ক্রেতা: " + tx!!.customer)
                                    if (tx != null) {
                                        Text("মোট: ৳ " + money(tx.total) + "  •  বাকি: ৳ " + money(tx.due))
                                    } else {
                                        Text("মোট: ৳ " + money(group.lines.sumOf { it.quantity * it.salePrice }))
                                    }
                                    if (returnedAmount > 0.0) Text("রিটার্ন: ৳ " + money(returnedAmount))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedGroup?.let { group ->
        val tx = group.transaction
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text("বিক্রয় বিস্তারিত") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Transaction ID: " + group.transactionId)
                    Text("তারিখ: " + (tx?.date ?: group.lines.first().date))
                    if (!tx?.customer.isNullOrBlank()) Text("ক্রেতা: " + tx!!.customer)
                    if (!tx?.mobile.isNullOrBlank()) Text("মোবাইল: " + tx!!.mobile)
                    group.lines.forEachIndexed { index, line ->
                        Text((index + 1).toString() + ". " + line.productName + " × " + line.quantity + " = ৳ " + money(line.quantity * line.salePrice))
                    }
                    if (tx != null) {
                        Text("সাবটোটাল: ৳ " + money(tx.subtotal))
                        Text("ছাড়: ৳ " + money(tx.discount))
                        Text("মোট: ৳ " + money(tx.total))
                        Text("জমা: ৳ " + money(tx.paid))
                        Text("বাকি: ৳ " + money(tx.due))
                        Text("পেমেন্ট: " + tx.paymentMethod)
                        val returned = SaleReturnStorage.getReturnedAmount(context, group.transactionId)
                        if (returned > 0.0) Text("এ পর্যন্ত রিটার্ন: ৳ " + money(returned))
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tx != null && SecurityStorage.canWrite(context)) {
                        TextButton(onClick = {
                            selectedGroup = null
                            context.startActivity(
                                Intent(context, SaleReturnActivity::class.java)
                                    .putExtra(SaleReturnActivity.EXTRA_TRANSACTION_ID, group.transactionId)
                            )
                        }) { Text("রিটার্ন") }
                    }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.shareInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("শেয়ার") }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.viewInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("দেখুন") }
                    TextButton(onClick = {
                        if (tx != null) InvoicePdfUtil.printInvoice(
                            context, group.transactionId,
                            group.lines.map { InvoiceLine(it.productName, it.quantity, it.salePrice) },
                            tx.customer, tx.mobile, tx.subtotal, tx.discount, tx.total, tx.paid, tx.due,
                            tx.paymentMethod, group.transactionId, tx.date
                        )
                    }) { Text("প্রিন্ট") }
                    TextButton(onClick = { selectedGroup = null }) { Text("বন্ধ") }
                }
            }
        )
    }
}

private fun money(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
