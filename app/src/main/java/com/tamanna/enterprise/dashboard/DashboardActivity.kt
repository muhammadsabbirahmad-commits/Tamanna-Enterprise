package com.tamanna.enterprise.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.product.ProductActivity
import com.tamanna.enterprise.purchase.PurchaseActivity

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DashboardScreen(
                onProductClick = {
                    startActivity(Intent(this, ProductActivity::class.java))
                },
                onPurchaseClick = {
                    startActivity(Intent(this, PurchaseActivity::class.java))
                }
            )
        }
    }
}

@Composable
fun DashboardScreen(
    onProductClick: () -> Unit,
    onPurchaseClick: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Text("Tamanna Enterprise", style = MaterialTheme.typography.headlineMedium)
                Text("Shop Management System", style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardCard("আজকের বিক্রয়", "৳ 0", Modifier.weight(1f))
                    DashboardCard("আজকের ক্রয়", "৳ 0", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardCard("আজকের লাভ", "৳ 0", Modifier.weight(1f))
                    DashboardCard("মোট স্টক", "0", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("প্রধান মেনু", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Button(onClick = onProductClick, modifier = Modifier.fillMaxWidth()) {
                    Text("📦  পণ্য ব্যবস্থাপনা")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = onPurchaseClick, modifier = Modifier.fillMaxWidth()) {
                    Text("🛒  ক্রয় ব্যবস্থাপনা")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("🧾  বিক্রয় ব্যবস্থাপনা")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("📊  স্টক ও রিপোর্ট")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("💰  লাভের হিসাব")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("📷  বারকোড / মেমো স্ক্যান")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("⚙️  সেটিংস")
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
