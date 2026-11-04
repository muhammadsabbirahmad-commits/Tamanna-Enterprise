package com.tamanna.enterprise.sales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ReturnRow(val sale: Sale, val returned: Int, val remaining: Int)

class SaleReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityStorage.canWrite(this)) {
            finish()
            return
        }
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID).orEmpty()
        setContent { SaleReturnScreen(transactionId, { finish() }) }
    }
    companion object { const val EXTRA_TRANSACTION_ID = "transaction_id" }
}

@androidx.compose.runtime.Composable
private fun SaleReturnScreen(transactionId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val tx = remember(transactionId) { SalesTransactionStorage.getTransaction(context, transactionId) }
    val originalLines = remember(transactionId) { SalesStorage.getSales(context).filter { it.transactionId == transactionId } }
    val returned = remember(transactionId) { SaleReturnStorage.getReturnedQuantities(context, transactionId) }
    val rows = remember(originalLines, returned) {
        // One row per product code, even if legacy invoices contain duplicate sale lines.
        originalLines.groupBy { it.productCode.trim().lowercase(Locale.ROOT) }.map { (code, salesForCode) ->
            val totalSold = salesForCode.sumOf { it.quantity.coerceAtLeast(0) }
            val totalReturned = returned[code] ?: returned.entries
                .firstOrNull { it.key.equals(code, ignoreCase = true) }?.value ?: 0
            val weightedPrice = if (totalSold > 0) {
                salesForCode.sumOf { it.quantity.coerceAtLeast(0) * it.salePrice } / totalSold
            } else 0.0
            val combinedSale = salesForCode.first().copy(
                quantity = totalSold,
                salePrice = weightedPrice
            )
            ReturnRow(combinedSale, totalReturned, (totalSold - totalReturned).coerceAtLeast(0))
        }
    }
    val quantities = remember(rows) { mutableStateListOf<Int>().also { list -> rows.forEach { list.add(0) } } }
    var refundMethod by remember { mutableStateOf("Cash") }
    var replacementCode by remember { mutableStateOf("") }
    var replacementQuantity by remember { mutableStateOf("1") }
    var replacementPrice by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val products = remember { ProductStorage.getProducts(context) }
    val replacementProduct = products.firstOrNull {
        it.code.equals(replacementCode.trim(), true) ||
            (replacementCode.isNotBlank() && it.name.contains(replacementCode.trim(), true))
    }
    val replacementQty = replacementQuantity.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val replacementUnitPrice = replacementPrice.toDoubleOrNull() ?: replacementProduct?.salePrice ?: 0.0

    val grossReturn = rows.indices.sumOf { quantities[it] * rows[it].sale.salePrice }
    val discountRate = if ((tx?.subtotal ?: 0.0) > 0.0) (tx!!.discount / tx.subtotal).coerceIn(0.0, 1.0) else 0.0
    val returnAmount = grossReturn * (1.0 - discountRate)
    val previousDueReduction = SaleReturnStorage.getDueReduction(context, transactionId)
    val remainingOriginalDue = ((tx?.due ?: 0.0) - previousDueReduction).coerceAtLeast(0.0)
    val dueReduction = returnAmount.coerceAtMost(remainingOriginalDue)
    val refundAmount = (returnAmount - dueReduction).coerceAtLeast(0.0)
    val exchangeTotal = replacementUnitPrice * replacementQty
    val exchangeDifference = exchangeTotal - returnAmount

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("বিক্রয় রিটার্ন", style = MaterialTheme.typography.headlineSmall)
            if (tx == null || rows.isEmpty()) {
                Text("এই ইনভয়েসের বিক্রয় রেকর্ড পাওয়া যায়নি।")
                Button(onClick = onDone) { Text("বন্ধ") }
            } else {
                Text("ইনভয়েস: $transactionId")
                Text("ক্রেতা: " + tx.customer)
                Text("মূল বাকি: ৳ " + money(tx.due) + " • রিটার্নের পর সমন্বয়যোগ্য বাকি: ৳ " + money(remainingOriginalDue))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.sale.productCode }) { row ->
                        val index = rows.indexOf(row)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(row.sale.productName, style = MaterialTheme.typography.titleMedium)
                                Text("কোড: ${row.sale.productCode}")
                                Text("বিক্রি: ${row.sale.quantity} • আগে রিটার্ন: ${row.returned} • ফেরতযোগ্য: ${row.remaining}")
                                OutlinedTextField(
                                    value = quantities[index].toString(),
                                    onValueChange = { value -> quantities[index] = (value.toIntOrNull() ?: 0).coerceIn(0, row.remaining) },
                                    label = { Text("ফেরত পরিমাণ") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Text("ইউনিট মূল্য: ৳ " + money(row.sale.salePrice))
                            }
                        }
                    }
                }

                Text("রিটার্নের সমন্বয়যোগ্য মোট: ৳ " + money(returnAmount))
                Text("বাকি থেকে কমবে: ৳ " + money(dueReduction))
                Text("ফেরত দেওয়ার টাকা: ৳ " + money(refundAmount))
                androidx.compose.material3.HorizontalDivider()
                Text("বিকল্প পণ্য দিয়ে এক্সচেঞ্জ (ঐচ্ছিক)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = replacementCode,
                    onValueChange = { replacementCode = it; replacementPrice = "" },
                    label = { Text("নতুন পণ্যের নাম বা কোড") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (replacementCode.isNotBlank()) {
                    if (replacementProduct == null) {
                        Text("এই নামে/কোডে কোনো পণ্য পাওয়া যায়নি।")
                    } else {
                        Text(replacementProduct.name + " • " + replacementProduct.code + " • স্টক: " + replacementProduct.stockQuantity)
                        OutlinedTextField(
                            value = replacementQuantity,
                            onValueChange = { replacementQuantity = it.filter(Char::isDigit) },
                            label = { Text("নতুন পণ্যের পরিমাণ") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = replacementPrice.ifBlank { replacementProduct.salePrice.toString() },
                            onValueChange = { replacementPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("নতুন পণ্যের ইউনিট মূল্য") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (exchangeDifference > 0.0) "ক্রেতার কাছ থেকে অতিরিক্ত পাবেন: ৳ " + money(exchangeDifference)
                            else if (exchangeDifference < 0.0) "ক্রেতাকে ফেরত দিতে হবে: ৳ " + money(-exchangeDifference)
                            else "দামের কোনো পার্থক্য নেই।"
                        )
                    }
                }
                Text("ফেরত দেওয়ার মাধ্যম", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "bKash", "Bank").forEach { method ->
                        FilterChip(selected = refundMethod == method, onClick = { refundMethod = method }, label = { Text(method) })
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDone, Modifier.weight(1f)) { Text("বাতিল") }
                    Button(
                        onClick = {
                            if (!SecurityStorage.canWrite(context)) {
                                message = "রিটার্ন সংরক্ষণের অনুমতি নেই."
                                return@Button
                            }
                            if (quantities.sum() <= 0) {
                                message = "কমপক্ষে একটি পণ্যের ফেরত পরিমাণ দিন."
                                return@Button
                            }
                            if (replacementCode.isNotBlank() && (
                                    replacementProduct == null || replacementQty <= 0 ||
                                        replacementUnitPrice < 0.0 ||
                                        replacementQty > (replacementProduct?.stockQuantity ?: 0)
                                )
                            ) {
                                message = "বিকল্প পণ্যের কোড, পরিমাণ, মূল্য ও বর্তমান স্টক যাচাই করুন।"
                                return@Button
                            }
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            val returnId = "RT-" + SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(Date())
                            val lines = rows.indices.mapNotNull { i ->
                                val q = quantities[i]
                                if (q <= 0) null else SaleReturnLine(returnId, transactionId, rows[i].sale.productCode, rows[i].sale.productName, q, rows[i].sale.salePrice)
                            }
                            // Validate against the latest saved sales and return history before touching stock.
                            // This is the final guard: total returned quantity can never exceed sold quantity.
                            if (!SaleReturnStorage.canAddReturn(context, transactionId, lines)) {
                                message = "রিটার্নের পরিমাণ কেনা পরিমাণের বেশি হতে পারবে না। বিক্রয় ও আগের রিটার্ন আবার যাচাই করুন।"
                                return@Button
                            }
                            // Re-read every affected product and calculate one net stock delta per code.
                            val latestProducts = ProductStorage.getProducts(context)
                            val stockDeltas = mutableMapOf<String, Int>()
                            lines.forEach { line ->
                                val key = line.productCode.trim().lowercase(Locale.ROOT)
                                stockDeltas[key] = (stockDeltas[key] ?: 0) + line.quantity
                            }
                            if (replacementProduct != null && replacementQty > 0) {
                                val key = replacementProduct.code.trim().lowercase(Locale.ROOT)
                                stockDeltas[key] = (stockDeltas[key] ?: 0) - replacementQty
                            }
                            val currentProducts = stockDeltas.mapValues { (code, _) ->
                                latestProducts.firstOrNull { it.code.equals(code, true) }
                                    ?: error("পণ্য পাওয়া যায়নি: $code")
                            }
                            val itemStocks = currentProducts.values.associate { it.code to it.stockQuantity }
                            currentProducts.forEach { (code, product) ->
                                val delta = stockDeltas[code] ?: 0
                                if (product.stockQuantity + delta < 0 ||
                                    product.stockQuantity + delta > Int.MAX_VALUE
                                ) error("স্টক অপর্যাপ্ত বা সীমা অতিক্রম করছে: " + product.name)
                            }
                            var paymentId = 0L
                            var extraDueId = 0L
                            var activityLogId = 0L
                            try {
                                currentProducts.forEach { (code, product) ->
                                    val newStock = product.stockQuantity + (stockDeltas[code] ?: 0)
                                    if (!ProductStorage.updateStock(context, product.code, newStock)) {
                                        error("স্টক আপডেট করা যায়নি: " + product.code)
                                    }
                                }
                                val returnSaved = SaleReturnStorage.addReturn(
                                    context,
                                    SaleReturn(returnId, transactionId, now, returnAmount, dueReduction, refundAmount, refundMethod),
                                    lines
                                )
                                if (!returnSaved) error("রিটার্ন রেকর্ড সংরক্ষণ করা যায়নি")
                                if (dueReduction > 0.0 && tx.customer.isNotBlank()) {
                                    paymentId = CustomerDueStorage.addPayment(
                                        context, tx.customer, tx.mobile, dueReduction,
                                        "বিক্রয় রিটার্ন: $returnId ($transactionId)"
                                    )
                                    if (paymentId <= 0L) error("রিটার্নের বাকি সমন্বয় সংরক্ষণ করা যায়নি")
                                }
                                if (replacementProduct != null && replacementQty > 0) {
                                    val exchangeId = "EX-" + SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(Date())
                                    val exchangeSaved = SaleReturnStorage.addExchange(
                                        context,
                                        SaleExchange(
                                            exchangeId, returnId, transactionId, now,
                                            replacementProduct.code, replacementProduct.name,
                                            replacementQty, replacementUnitPrice, returnAmount, exchangeDifference
                                        )
                                    )
                                    if (!exchangeSaved) error("এক্সচেঞ্জ রেকর্ড সংরক্ষণ করা যায়নি")
                                    if (exchangeDifference > 0.0 && tx.customer.isNotBlank()) {
                                        extraDueId = CustomerDueStorage.addSaleDue(
                                            context, tx.customer, tx.mobile, exchangeDifference,
                                            "এক্সচেঞ্জ: $exchangeId ($transactionId)"
                                        )
                                        if (extraDueId <= 0L) error("এক্সচেঞ্জের অতিরিক্ত টাকা সংরক্ষণ করা যায়নি")
                                    }
                                }
                                activityLogId = ActivityLogStorage.addAndGetId(
                                    context, "বিক্রয় রিটার্ন", "$returnId • $transactionId • ৳" + money(returnAmount)
                                )
                                if (activityLogId <= 0L) error("রিটার্নের Activity Log সংরক্ষণ করা যায়নি")
                                onDone()
                            } catch (e: Exception) {
                                if (activityLogId > 0L) ActivityLogStorage.removeById(context, activityLogId)
                                if (paymentId > 0L) CustomerDueStorage.removePaymentById(context, paymentId)
                                if (extraDueId > 0L) CustomerDueStorage.removeSaleDueById(context, extraDueId)
                                SaleReturnStorage.getExchanges(context)
                                    .filter { it.returnId == returnId }
                                    .forEach { SaleReturnStorage.removeExchange(context, it.exchangeId) }
                                itemStocks.forEach { (code, stock) -> ProductStorage.updateStock(context, code, stock) }
                                SaleReturnStorage.removeReturn(context, returnId)
                                message = "রিটার্ন সংরক্ষণ ব্যর্থ হয়েছে। কোনো পরিবর্তন রাখা হয়নি."
                            }
                        },
                        Modifier.weight(1f)
                    ) { Text("রিটার্ন সম্পন্ন") }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun money(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
