package com.tamanna.enterprise.product

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "নতুন পণ্য যোগ করুন",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Add New Product",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "এখানে পরবর্তীতে Product Code, পণ্যের নাম, ক্রয়মূল্য, বিক্রয়মূল্য ও স্টক যোগ করা হবে।",
                    style = MaterialTheme.typography.bodyLarge
                )

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("পণ্য সংরক্ষণ")
                }
            }
        }
    }
}
