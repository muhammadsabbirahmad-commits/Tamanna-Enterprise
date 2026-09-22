package com.tamanna.enterprise.purchase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.ActivityLogStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddPurchaseActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PRODUCT_CODE = "add_purchase_product_code"
        const val EXTRA_QUANTITY = "add_purchase_quantity"
        const val EXTRA_PURCHASE_PRICE = "add_purchase_purchase_price"
        const val EXTRA_MEMO_VERIFIED_DATA = "add_purchase_memo_verified"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecurityStorage.canWrite(this)) {
            finish()
            return
        }

        val initialProductCode = intent.getStringExtra(EXTRA_PRODUCT_CODE).orEmpty()
        val initialQuantity = intent.getIntExtra(EXTRA_QUANTITY, 0)
        val initialPurchasePrice = intent.getDoubleExtra(EXTRA_PURCHASE_PRICE, 0.0)
        val memoVerified = intent.getBooleanExtra(EXTRA_MEMO_VERIFIED_DATA, false)

        setContent {
            AddPurchaseScreen(
                initialProductCode = initialProductCode,
                initialQuantity = initialQuantity,
                initialPurchasePrice = initialPurchasePrice,
                memoVerified = memoVerified
            ) { finish() }
        }
    }
}

@Composable
private fun AddPurchaseScreen(
    initialProductCode: String,
    initialQuantity: Int,
    initialPurchasePrice: Double,
    memoVerified: Boolean,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val products = remember { ProductStorage.getProducts(context) }

    var productCode by remember { mutableStateOf(initialProductCode) }
    var quantity by remember { mutableStateOf(initialQuantity.takeIf { it > 0 }?.toString().orEmpty()) }
    var purchasePrice by remember { mutableStateOf(initialPurchasePrice.takeIf { it > 0 }?.toString().orEmpty()) }
    var supplier by remember { mutableStateOf("") }
    var memoNumber by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(if (memoVerified) "মেমো থেকে পাওয়া পরিমাণ ও ক্রয়মূল্য বসানো হয়েছে—সংরক্ষণের আগে যাচাই করুন।" else "") }

    val selectedProduct = products.firstOrNull { it.code.equals(productCode.trim(), ignoreCase = true) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("নতুন ক্রয়")

        if (memoVerified) {
            Text("⚠️ মেমো OCR যাচাই: তথ্য স্বয়ংক্রিয়ভাবে শনাক্ত হয়েছে। ভুল থাকলে সংরক্ষণের আগে সংশোধন করুন।")
        }

        OutlinedTextField(value = productCode, onValueChange = { productCode = it; message = "" }, label = { Text("পণ্য কোড") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (selectedProduct != null) Text("পণ্য: ${selectedProduct.name} | বর্তমান স্টক: ${selectedProduct.stockQuantity}")
        OutlinedTextField(value = quantity, onValueChange = { quantity = it.filter(Char::isDigit) }, label = { Text("পরিমাণ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("ক্রয়মূল্য (প্রতি ইউনিট)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = supplier, onValueChange = { supplier = it }, label = { Text("সরবরাহকারী (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = memoNumber, onValueChange = { memoNumber = it }, label = { Text("মেমো নম্বর (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = paid, onValueChange = { paid = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("এখন পরিশোধ (৳)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Button(
            onClick = {
                if (!SecurityStorage.canWrite(context)) {
                    message = "পণ্য ক্রয় ও Stock In করার অনুমতি শুধু অ্যাডমিনের আছে।"
                    return@Button
                }
                val qty = quantity.toIntOrNull()
                val price = purchasePrice.toDoubleOrNull()
                val paidAmount = paid.toDoubleOrNull() ?: 0.0
                val totalAmount = (qty ?: 0) * (price ?: 0.0)
                val dueAmount = totalAmount - paidAmount
                val latestProduct = productCode.trim().takeIf { it.isNotBlank() }?.let { code ->
                    ProductStorage.getProducts(context).firstOrNull { it.code.equals(code, ignoreCase = true) }
                }

                when {
                    latestProduct == null -> message = "সঠিক পণ্য কোড দিন।"
                    qty == null || qty <= 0 -> message = "সঠিক পরিমাণ দিন।"
                    price == null || price < 0 -> message = "সঠিক ক্রয়মূল্য দিন।"
                    latestProduct.stockQuantity > Int.MAX_VALUE - qty -> message = "স্টক সীমা অতিক্রম করছে।"
                    paidAmount < 0 || paidAmount > totalAmount -> message = "পরিশোধের পরিমাণ মোট ক্রয়মূল্যের মধ্যে দিন।"
                    dueAmount > 0 && supplier.trim().isBlank() -> message = "বাকি ক্রয়ের জন্য সরবরাহকারীর নাম দিন।"
                    else -> {
                        val updatedProduct = latestProduct.copy(purchasePrice = price, stockQuantity = latestProduct.stockQuantity + qty)
                        val stockUpdated = ProductStorage.updateProduct(context, updatedProduct)
                        if (!stockUpdated) {
                            message = "পণ্যটি আর পাওয়া যাচ্ছে না। আবার চেষ্টা করুন।"
                            return@Button
                        }

                        val purchaseId = System.currentTimeMillis()
                        val purchaseDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        val savedPurchaseId = PurchaseStorage.addPurchase(
                            context,
                            Purchase(
                                id = purchaseId,
                                date = purchaseDate,
                                productCode = latestProduct.code,
                                productName = latestProduct.name,
                                quantity = qty,
                                purchasePrice = price,
                                supplier = supplier.trim(),
                                memoNumber = memoNumber.trim()
                            )
                        )

                        if (savedPurchaseId <= 0L) {
                            ProductStorage.updateStock(context, latestProduct.code, latestProduct.stockQuantity)
                            message = "ক্রয় রেকর্ড সংরক্ষণ করা যায়নি। স্টক আগের অবস্থায় ফিরিয়ে দেওয়া হয়েছে।"
                            return@Button
                        }

                        var supplierDueId = 0L
                        if (dueAmount > 0 && supplier.trim().isNotBlank()) {
                            supplierDueId = SupplierDueStorage.addPurchaseDue(
                                context,
                                supplier.trim(),
                                dueAmount,
                                "ক্রয়: " + latestProduct.name + " x" + qty + if (memoNumber.isBlank()) "" else " • মেমো " + memoNumber.trim()
                            )
                            if (supplierDueId <= 0L) {
                                PurchaseStorage.removeById(context, savedPurchaseId)
                                ProductStorage.updateStock(context, latestProduct.code, latestProduct.stockQuantity)
                                message = "সরবরাহকারীর বাকি রেকর্ড সংরক্ষণ করা যায়নি। ক্রয় ও স্টক rollback করা হয়েছে।"
                                return@Button
                            }
                        }

                        val activityLogId = ActivityLogStorage.addAndGetId(
                            context,
                            "পণ্য ক্রয় ও Stock In",
                            latestProduct.name + " (" + latestProduct.code + ") x" + qty +
                                " • নতুন স্টক: " + updatedProduct.stockQuantity
                        )
                        if (activityLogId <= 0L) {
                            if (supplierDueId > 0L) {
                                SupplierDueStorage.removeById(context, supplierDueId)
                            }
                            PurchaseStorage.removeById(context, savedPurchaseId)
                            ProductStorage.updateStock(context, latestProduct.code, latestProduct.stockQuantity)
                            message = "ক্রয়ের Activity Log সংরক্ষণ করা যায়নি। ক্রয় ও স্টক rollback করা হয়েছে।"
                            return@Button
                        }
                        onSaved()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ক্রয় সংরক্ষণ করুন") }

        if (message.isNotBlank()) Text(message)
    }
}
