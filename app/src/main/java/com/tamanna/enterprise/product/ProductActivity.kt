package com.tamanna.enterprise.product

import android.content.Intent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProductScreen(
                onAddProductClick = {
                    startActivity(Intent(this, AddProductActivity::class.java))
                }
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun ProductScreen(onAddProductClick: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("পণ্য ব্যবস্থাপনা", style = MaterialTheme.typography.headlineMedium)
                Text("Products Management", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAddProductClick, modifier = Modifier.fillMaxWidth()) {
                    Text("নতুন পণ্য যোগ করুন")
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("পণ্যের তালিকা")
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("পণ্য অনুসন্ধান")
                }
            }
        }
    }
}