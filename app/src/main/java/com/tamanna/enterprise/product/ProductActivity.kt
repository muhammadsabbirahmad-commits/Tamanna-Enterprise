package com.tamanna.enterprise.product

import android.os.Bundle
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
import androidx.compose.ui.unit.dp

class AddProductActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AddProductScreen()
        }
    }
}

@androidx.compose.runtime.Composable
fun AddProductScreen() {

    var productCode by remember {
        mutableStateOf("")
    }

    var productName by remember {
        mutableStateOf("")
    }

    var purchasePrice by remember {
        mutableStateOf("")
    }

    var salePrice by remember {
        mutableStateOf("")
    }

    var stockQuantity by remember {
        mutableStateOf("")
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "নতুন পণ্য যোগ করুন",
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = productCode,
                    onValueChange = {
                        productCode = it
                    },
                    label = {
                        Text("Product Code")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = productName,
                    onValueChange = {
                        productName = it
                    },
                    label = {
                        Text("পণ্যের নাম")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purchasePrice,
                    onValueChange = {
                        purchasePrice = it
                    },
                    label = {
                        Text("ক্রয়মূল্য")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = salePrice,
                    onValueChange = {
                        salePrice = it
                    },
                    label = {
                        Text("বিক্রয়মূল্য")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = stockQuantity,
                    onValueChange = {
                        stockQuantity = it
                    },
                    label = {
                        Text("স্টকের পরিমাণ")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        // পরবর্তীতে এখানে পণ্য সংরক্ষণের
                        // database logic যুক্ত হবে
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("পণ্য সংরক্ষণ")
                }
            }
        }
    }
}
