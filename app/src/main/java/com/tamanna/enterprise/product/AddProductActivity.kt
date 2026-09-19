package com.tamanna.enterprise.product

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class AddProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AddProductScreen() }
    }
}

@androidx.compose.runtime.Composable
fun AddProductScreen() {
    val context = LocalContext.current
    var productCode by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var stockQuantity by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("নতুন পণ্য যোগ করুন", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(productCode, { productCode = it }, label = { Text("Product Code") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(productName, { productName = it }, label = { Text("পণ্যের নাম") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(purchasePrice, { purchasePrice = it }, label = { Text("ক্রয়মূল্য") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(salePrice, { salePrice = it }, label = { Text("বিক্রয়মূল্য") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stockQuantity, { stockQuantity = it }, label = { Text("স্টকের পরিমাণ") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        val purchase = purchasePrice.toDoubleOrNull()
                        val sale = salePrice.toDoubleOrNull()
                        val stock = stockQuantity.toIntOrNull()
                        when {
                            productCode.isBlank() || productName.isBlank() ->
                                Toast.makeText(context, "Product Code ও পণ্যের নাম দিন", Toast.LENGTH_SHORT).show()
                            purchase == null || purchase < 0 ->
                                Toast.makeText(context, "সঠিক ক্রয়মূল্য দিন", Toast.LENGTH_SHORT).show()
                            sale == null || sale < 0 ->
                                Toast.makeText(context, "সঠিক বিক্রয়মূল্য দিন", Toast.LENGTH_SHORT).show()
                            stock == null || stock < 0 ->
                                Toast.makeText(context, "সঠিক স্টকের পরিমাণ দিন", Toast.LENGTH_SHORT).show()
                            else -> {
                                val saved = ProductStorage.addProduct(
                                    context,
                                    Product(productCode.trim(), productName.trim(), purchase, sale, stock)
                                )
                                if (saved) {
                                    Toast.makeText(context, "পণ্য সফলভাবে সংরক্ষণ হয়েছে", Toast.LENGTH_SHORT).show()
                                    (context as? ComponentActivity)?.finish()
                                } else {
                                    Toast.makeText(context, "এই Product Code আগে থেকেই আছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("পণ্য সংরক্ষণ")
                }
            }
        }
    }
}