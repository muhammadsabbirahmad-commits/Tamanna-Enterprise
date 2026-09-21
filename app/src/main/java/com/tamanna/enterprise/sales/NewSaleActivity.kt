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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CartItem(
    val product: Product,
    val quantity: Int,
    val unitPrice: Double
)

class NewSaleActivity : ComponentActivity() {
    private var scannedCode by mutableStateOf("")
    private var scanNonce by mutableIntStateOf(0)

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let { code ->
            scannedCode = code
            scanNonce++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannedCode = intent.getStringExtra(EXTRA_PRODUCT_CODE).orEmpty()
        if (scannedCode.isNotBlank()) scanNonce = 1

        setContent {
            NewSaleScreen(
                scannedCode = scannedCode,
                scanNonce = scanNonce,
                onScan = { launchScanner() },
                onSaved = { finish() }
            )
        }
    }

    private fun launchScanner() {
        scanner.launch(ScanOptions().apply {
            setPrompt("পণ্যের বারকোড / QR কোড স্ক্যান করুন")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        })
    }

    companion object {
        const val EXTRA_PRODUCT_CODE = "product_code"
    }
}

@Composable
private fun NewSaleScreen(
    scannedCode: String,
    scanNonce: Int,
    onScan: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val products = remember { ProductStorage.getProducts(context) }
    val cart = remember { mutableStateListOf<CartItem>() }

    var search by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var customer by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var message by remember { mutableStateOf("") }

    val matches = products.filter {
        search.isBlank() ||
            it.name.contains(search.trim(), true) ||
            it.code.contains(search.trim(), true)
    }.take(6)

    fun addProduct(product: Product) {
        val qty = quantity.toIntOrNull() ?: 0
        if (qty <= 0) {
            message = "সঠিক পরিমাণ দিন।"
            return
        }
        val index = cart.indexOfFirst { it.product.code.equals(product.code, true) }
        val oldQty = if (index >= 0) cart[index].quantity else 0
        if (oldQty + qty > product.stockQuantity) {
            message = "পর্যাপ্ত স্টক নেই। বর্তমান স্টক: " + product.stockQuantity
            return
        }
        if (index >= 0) {
            cart[index] = cart[index].copy(quantity = oldQty + qty)
        } else {
            cart.add(CartItem(product, qty, product.salePrice))
        }
        search = ""
        quantity = "1"
        message = product.name + " কার্টে যোগ হয়েছে।"
    }

    LaunchedEffect(scanNonce) {
        if (scanNonce <= 0 || scannedCode.isBlank()) return@LaunchedEffect
        val currentProducts = ProductStorage.getProducts(context)
        val rawCode = scannedCode.trim()
        val normalizedCode = rawCode
            .substringAfterLast("/")
            .substringBefore("?")
            .trim()
        val product = currentProducts.firstOrNull {
            it.code.equals(rawCode, ignoreCase = true) ||
                it.code.equals(normalizedCode, ignoreCase = true)
        }
        if (product == null) {
            message = "এই কোডের কোনো পণ্য পাওয়া যায়নি: $rawCode"
        } else if (product.stockQuantity <= 0) {
            message = product.name + " এর স্টক শেষ।"
        } else {
            addProduct(product)
        }
    }

    val subtotal = cart.sumOf { it.quantity * it.unitPrice }
    val discountAmount = (discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val total = subtotal - discountAmount
    val paidAmount = (paid.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)
    val due = total - paidAmount
    val totalCost = cart.sumOf { it.quantity * it.product.purchasePrice }
    val profitLoss = total - totalCost

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("নতুন বিক্রয় • POS", style = MaterialTheme.typography.headlineSmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; message = "" },
                    label = { Text("পণ্যের নাম / কোড") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = onScan) { Text("স্ক্যান") }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter(Char::isDigit) },
                label = { Text("পরিমাণ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (matches.isNotEmpty() && search.isNotBlank()) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(matches, key = { it.code }) { product ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    Text("কোড: " + product.code + " • স্টক: " + product.stockQuantity)
                                    Text("৳ " + String.format(Locale.getDefault(), "%.2f", product.salePrice) + " / ইউনিট")
                                }
                                Button(onClick = { addProduct(product) }) { Text("যোগ") }
                            }
                        }
                    }
                }
            }

            Text("কার্ট • " + cart.size + "টি পণ্য", style = MaterialTheme.typography.titleLarge)

            if (cart.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cart, key = { it.product.code }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                                Text("৳ " + String.format(Locale.getDefault(), "%.2f", item.unitPrice) + " × " + item.quantity +
                                    " = ৳ " + String.format(Locale.getDefault(), "%.2f", item.unitPrice * item.quantity))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = {
                                        val i = cart.indexOfFirst { it.product.code == item.product.code }
                                        if (i >= 0 && cart[i].quantity > 1) cart[i] = cart[i].copy(quantity = cart[i].quantity - 1)
                                    }) { Text("−") }
                                    Button(onClick = {
                                        val i = cart.indexOfFirst { it.product.code == item.product.code }
                                        if (i >= 0 && cart[i].quantity < item.product.stockQuantity) cart[i] = cart[i].copy(quantity = cart[i].quantity + 1)
                                    }) { Text("+") }
                                    Button(onClick = { cart.removeAll { it.product.code == item.product.code } }) { Text("বাদ") }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = customer, onValueChange = { customer = it }, label = { Text("ক্রেতার নাম") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter(Char::isDigit) }, label = { Text("মোবাইল") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = discount, onValueChange = { discount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("ছাড় ৳") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = paid, onValueChange = { paid = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("জমা ৳") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Text("সাবটোটাল ৳ " + String.format(Locale.getDefault(), "%.2f", subtotal) +
                "  •  ছাড় ৳ " + String.format(Locale.getDefault(), "%.2f", discountAmount))
            Text("মোট ৳ " + String.format(Locale.getDefault(), "%.2f", total) +
                "  •  জমা ৳ " + String.format(Locale.getDefault(), "%.2f", paidAmount) +
                "  •  বাকি ৳ " + String.format(Locale.getDefault(), "%.2f", due))
            Text(
                (if (profitLoss >= 0) "সম্ভাব্য লাভ ৳ " else "সম্ভাব্য ক্ষতি ৳ ") +
                    String.format(Locale.getDefault(), "%.2f", kotlin.math.abs(profitLoss))
            )

            Text("পেমেন্ট মাধ্যম", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cash", "bKash", "Bank", "Due").forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method) }
                    )
                }
            }

            Button(
                onClick = {
                    if (cart.isEmpty()) {
                        message = "কমপক্ষে একটি পণ্য কার্টে যোগ করুন।"
                        return@Button
                    }
                    if (due > 0.0 && customer.trim().isBlank()) {
                        message = "বাকি বিক্রয়ের জন্য ক্রেতার নাম দিন।"
                        return@Button
                    }
                    val latestProducts = ProductStorage.getProducts(context)
                    val latestByCode = latestProducts.associateBy { it.code.lowercase(Locale.ROOT) }
                    val latestCart = cart.mapNotNull { item ->
                        latestByCode[item.product.code.lowercase(Locale.ROOT)]?.let { latest ->
                            item.copy(product = latest)
                        }
                    }
                    if (latestCart.size != cart.size) {
                        message = "কার্টের একটি বা একাধিক পণ্য আর পাওয়া যাচ্ছে না। আবার পণ্য নির্বাচন করুন।"
                        return@Button
                    }
                    val stockProblem = latestCart.firstOrNull { it.quantity > it.product.stockQuantity }
                    if (stockProblem != null) {
                        message = stockProblem.product.name + " এর বর্তমান স্টক " +
                            stockProblem.product.stockQuantity + "। আবার চেষ্টা করুন।"
                        return@Button
                    }

                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val transactionId = "TX-" + SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(Date())
                    val customerText = customer.trim() + if (mobile.isNotBlank()) " • " + mobile.trim() else ""
                    val dueNote = "বিক্রয়: " + latestCart.joinToString(", ") { it.product.name + " x" + it.quantity }
                    val originalStocks = latestCart.associate { it.product.code to it.product.stockQuantity }

                    try {
                        SalesTransactionStorage.addTransaction(
                            context,
                            SaleTransaction(
                                transactionId = transactionId,
                                date = now,
                                customer = customer.trim(),
                                mobile = mobile.trim(),
                                subtotal = subtotal,
                                discount = discountAmount,
                                total = total,
                                paid = paidAmount,
                                due = due,
                                paymentMethod = paymentMethod
                            )
                        )

                        if (due > 0.0) {
                            CustomerDueStorage.addSaleDue(
                                context, customer.trim(), mobile.trim(), due, dueNote
                            )
                        }

                        latestCart.forEachIndexed { index, item ->
                            ProductStorage.updateStock(
                                context, item.product.code,
                                item.product.stockQuantity - item.quantity
                            )
                            SalesStorage.addSale(
                                context,
                                Sale(
                                    id = System.currentTimeMillis() + index,
                                    transactionId = transactionId,
                                    date = now,
                                    productCode = item.product.code,
                                    productName = item.product.name,
                                    quantity = item.quantity,
                                    salePrice = item.unitPrice,
                                    purchasePrice = item.product.purchasePrice,
                                    customer = customerText
                                )
                            )
                        }
                        ActivityLogStorage.add(
                            context,
                            "পণ্য বিক্রয় ও Stock Out",
                            latestCart.joinToString(" • ") {
                                it.product.name + " (" + it.product.code + ") x" + it.quantity
                            } + " • নতুন স্টক: " +
                                latestCart.joinToString(", ") {
                                    it.product.code + "=" + (it.product.stockQuantity - it.quantity)
                                }
                        )
                    } catch (e: Exception) {
                        cart.forEach { item ->
                            ProductStorage.updateStock(
                                context, item.product.code,
                                originalStocks[item.product.code] ?: item.product.stockQuantity
                            )
                        }
                        SalesStorage.removeByTransaction(context, transactionId)
                        SalesTransactionStorage.removeTransaction(context, transactionId)
                        if (due > 0.0) {
                            CustomerDueStorage.removeSaleDue(
                                context, customer.trim(), mobile.trim(), due, dueNote
                            )
                        }
                        message = "বিক্রয় সংরক্ষণ ব্যর্থ হয়েছে। কোনো পরিবর্তন রাখা হয়নি।"
                        return@Button
                    }

                    InvoicePdfUtil.shareInvoice(
                        context = context,
                        transactionId = transactionId,
                        cart = latestCart.map { InvoiceLine(it.product.name, it.quantity, it.unitPrice) },
                        customer = customer.trim(),
                        mobile = mobile.trim(),
                        subtotal = subtotal,
                        discount = discountAmount,
                        total = total,
                        paid = paidAmount,
                        due = due,
                        paymentMethod = paymentMethod
                    )
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("সাবমিট করে বিক্রয় সম্পন্ন করুন") }

            if (message.isNotBlank()) Text(message)
        }
    }
}
