package com.tamanna.enterprise.sales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
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
        if (!SecurityStorage.canWrite(this)) {
            finish()
            return
        }
        scannedCode = intent.getStringExtra(EXTRA_PRODUCT_CODE).orEmpty()
        if (scannedCode.isNotBlank()) scanNonce = 1

        setContent {
            NewSaleScreen(
                scannedCode = scannedCode,
                scanNonce = scanNonce,
                onScan = { launchScanner() },
                onClose = { finish() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSaleScreen(
    scannedCode: String,
    scanNonce: Int,
    onScan: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val products = remember { ProductStorage.getProducts(context) }
    val cart = remember { mutableStateListOf<CartItem>() }

    var currentStep by remember { mutableIntStateOf(1) } // ১ = পণ্য নির্বাচন, ২ = পেমেন্ট

    var search by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var customer by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var message by remember { mutableStateOf("") }

    // বিক্রি সফল হওয়ার পর সব ফিল্ড ক্লিয়ার করে নতুন বিক্রয়ের জন্য প্রস্তুত করার ফাংশন
    fun resetForm() {
        cart.clear()
        search = ""
        quantity = "1"
        customer = ""
        mobile = ""
        discount = "0"
        paid = "0"
        paymentMethod = "Cash"
        currentStep = 1
        message = "✓ বিক্রয় সফলভাবে সম্পন্ন হয়েছে! নতুন বিক্রয় করুন।"
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentStep == 1) "নতুন বিক্রয় • স্টেপ ১ (পণ্য নির্বাচন)"
                        else "নতুন বিক্রয় • স্টেপ ২ (পেমেন্ট)",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    TextButton(onClick = {
                        if (currentStep == 2) {
                            currentStep = 1
                        } else {
                            onClose()
                        }
                    }) {
                        Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentStep == 1) {
                // ==================== STEP 1: PRODUCT SELECTION & CART ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.isNotBlank()) {
                        Text(
                            text = message,
                            color = if (message.startsWith("✓")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it; message = "" },
                            label = { Text("পণ্যের নাম / কোড") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = onScan, modifier = Modifier.height(56.dp)) { Text("স্ক্যান") }
                    }

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit) },
                        label = { Text("পরিমাণ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (matches.isNotEmpty() && search.isNotBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                                items(matches, key = { it.code }) { product ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                                            Text("কোড: " + product.code + " • স্টক: " + product.stockQuantity)
                                            Text("৳ " + String.format(Locale.getDefault(), "%.2f", product.salePrice) + " / ইউনিট")
                                        }
                                        Button(onClick = { addProduct(product) }) { Text("যোগ") }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    Text("কার্ট • " + cart.size + "টি পণ্য", style = MaterialTheme.typography.titleLarge)

                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("কার্টে কোনো পণ্য যোগ করা হয়নি।", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cart, key = { it.product.code }) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "৳ " + String.format(Locale.getDefault(), "%.2f", item.unitPrice * item.quantity),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            "৳ " + String.format(Locale.getDefault(), "%.2f", item.unitPrice) + " × " + item.quantity,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(onClick = {
                                                    val i = cart.indexOfFirst { it.product.code == item.product.code }
                                                    if (i >= 0 && cart[i].quantity > 1) cart[i] = cart[i].copy(quantity = cart[i].quantity - 1)
                                                }) { Text("−") }
                                                Text(
                                                    "${item.quantity}",
                                                    modifier = Modifier.align(Alignment.CenterVertically),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Button(onClick = {
                                                    val i = cart.indexOfFirst { it.product.code == item.product.code }
                                                    if (i >= 0 && cart[i].quantity < item.product.stockQuantity) cart[i] = cart[i].copy(quantity = cart[i].quantity + 1)
                                                }) { Text("+") }
                                            }
                                            Button(
                                                onClick = { cart.removeAll { it.product.code == item.product.code } },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) { Text("বাদ") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (cart.isEmpty()) {
                                message = "কমপক্ষে একটি পণ্য কার্টে যোগ করুন।"
                            } else {
                                message = ""
                                currentStep = 2
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = cart.isNotEmpty()
                    ) {
                        Text("পরবর্তী (পেমেন্ট ও কাস্টমার তথ্য) ➔", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ==================== STEP 2: PAYMENT & CUSTOMER DETAILS ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("কার্ট সামারি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("মোট পণ্য: ${cart.sumOf { it.quantity }}টি  •  আইটেম: ${cart.size}টি")
                            Text("সাবটোটাল: ৳ " + String.format(Locale.getDefault(), "%.2f", subtotal), fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customer, onValueChange = { customer = it; message = "" }, label = { Text("ক্রেতার নাম") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = mobile, onValueChange = { mobile = it.filter(Char::isDigit); message = "" }, label = { Text("মোবাইল") }, modifier = Modifier.weight(1f), singleLine = true)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = discount, onValueChange = { discount = it.filter { c -> c.isDigit() || c == '.' }; message = "" }, label = { Text("ছাড় ৳") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = paid, onValueChange = { paid = it.filter { c -> c.isDigit() || c == '.' }; message = "" }, label = { Text("জমা ৳") }, modifier = Modifier.weight(1f), singleLine = true)
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("সাবটোটাল: ৳ " + String.format(Locale.getDefault(), "%.2f", subtotal))
                            Text("ছাড়: ৳ " + String.format(Locale.getDefault(), "%.2f", discountAmount))
                            HorizontalDivider()
                            Text("মোট: ৳ " + String.format(Locale.getDefault(), "%.2f", total), fontWeight = FontWeight.Bold)
                            Text("জমা: ৳ " + String.format(Locale.getDefault(), "%.2f", paidAmount))
                            Text("বাকি: ৳ " + String.format(Locale.getDefault(), "%.2f", due), color = if (due > 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                            Text(
                                (if (profitLoss >= 0) "সম্ভাব্য লাভ: ৳ " else "সম্ভাব্য ক্ষতি: ৳ ") +
                                    String.format(Locale.getDefault(), "%.2f", kotlin.math.abs(profitLoss)),
                                color = if (profitLoss >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

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

                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("← পণ্য পরিবর্তন")
                        }

                        Button(
                            onClick = {
                                if (!SecurityStorage.canWrite(context)) {
                                    message = "বিক্রয় ও Stock Out করার অনুমতি শুধু অ্যাডমিনের আছে।"
                                    return@Button
                                }
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
                                var saleDueId = 0L

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
                                        saleDueId = CustomerDueStorage.addSaleDue(
                                            context, customer.trim(), mobile.trim(), due, dueNote
                                        )
                                        if (saleDueId <= 0L) {
                                            throw IllegalStateException("ক্রেতার বাকি সংরক্ষণ করা যায়নি")
                                        }
                                    }

                                    latestCart.forEachIndexed { index, item ->
                                        val stockUpdated = ProductStorage.updateStock(
                                            context, item.product.code,
                                            item.product.stockQuantity - item.quantity
                                        )
                                        if (!stockUpdated) {
                                            throw IllegalStateException("স্টক আপডেট করা যায়নি: " + item.product.code)
                                        }
                                        val saleSaved = SalesStorage.addSale(
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
                                        if (!saleSaved) {
                                            throw IllegalStateException("বিক্রয় রেকর্ড সংরক্ষণ করা যায়নি: " + item.product.code)
                                        }
                                    }
                                    val logSaved = ActivityLogStorage.add(
                                        context,
                                        "পণ্য বিক্রয় ও Stock Out",
                                        latestCart.joinToString(" • ") {
                                            it.product.name + " (" + it.product.code + ") x" + it.quantity
                                        } + " • নতুন স্টক: " +
                                            latestCart.joinToString(", ") {
                                                it.product.code + "=" + (it.product.stockQuantity - it.quantity)
                                            }
                                    )
                                    if (!logSaved) {
                                        throw IllegalStateException("বিক্রয়ের Activity Log সংরক্ষণ করা যায়নি")
                                    }
                                } catch (e: Exception) {
                                    latestCart.forEach { item ->
                                        ProductStorage.updateStock(
                                            context, item.product.code,
                                            originalStocks[item.product.code] ?: item.product.stockQuantity
                                        )
                                    }
                                    SalesStorage.removeByTransaction(context, transactionId)
                                    SalesTransactionStorage.removeTransaction(context, transactionId)
                                    if (saleDueId > 0L) {
                                        CustomerDueStorage.removeSaleDueById(context, saleDueId)
                                    }
                                    message = "বিক্রয় সংরক্ষণ ব্যর্থ হয়েছে। কোনো পরিবর্তন রাখা হয়নি।"
                                    return@Button
                                }

                                // বিক্রয় সফলভাবে সম্পন্ন হলে ডাটা রিসেট করে স্টেপ ১-এ ফেরত পাঠানো হবে
                                resetForm()
                            },
                            modifier = Modifier.weight(1.5f).height(50.dp)
                        ) {
                            Text("বিক্রয় সম্পন্ন করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
