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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddPurchaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AddPurchaseScreen { finish() } }
    }
}

@Composable
private fun AddPurchaseScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val products = remember { ProductStorage.getProducts(context) }

    var productCode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var memoNumber by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val selectedProduct = products.firstOrNull {
        it.code.equals(productCode.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("নতুন ক্রয়")

        OutlinedTextField(
            value = productCode,
            onValueChange = { productCode = it; message = "" },
            label = { Text("পণ্য কোড") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (selectedProduct != null) {
            Text("পণ্য: \${selectedProduct.name} | বর্তমান স্টক: \${selectedProduct.stockQuantity}")
        }

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter(Char::isDigit) },
            label = { Text("পরিমাণ") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = purchasePrice,
            onValueChange = { purchasePrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("ক্রয়মূল্য (প্রতি ইউনিট)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = supplier,
            onValueChange = { supplier = it },
            label = { Text("সরবরাহকারী (ঐচ্ছিক)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = memoNumber,
            onValueChange = { memoNumber = it },
            label = { Text("মেমো নম্বর (ঐচ্ছিক)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val qty = quantity.toIntOrNull()
                val price = purchasePrice.toDoubleOrNull()

                when {
                    selectedProduct == null -> message = "সঠিক পণ্য কোড দিন।"
                    qty == null || qty <= 0 -> message = "সঠিক পরিমাণ দিন।"
                    price == null || price < 0 -> message = "সঠিক ক্রয়মূল্য দিন."
                    else -> {
                        ProductStorage.updateProduct(
                            context,
                            selectedProduct.copy(
                                purchasePrice = price,
                                stockQuantity = selectedProduct.stockQuantity + qty
                            )
                        )

                        PurchaseStorage.addPurchase(
                            context,
                            Purchase(
                                id = System.currentTimeMillis(),
                                date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                                productCode = selectedProduct.code,
                                productName = selectedProduct.name,
                                quantity = qty,
                                purchasePrice = price,
                                supplier = supplier.trim(),
                                memoNumber = memoNumber.trim()
                            )
                        )
                        onSaved()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ক্রয় সংরক্ষণ করুন")
        }

        if (message.isNotBlank()) Text(message)
    }
}
