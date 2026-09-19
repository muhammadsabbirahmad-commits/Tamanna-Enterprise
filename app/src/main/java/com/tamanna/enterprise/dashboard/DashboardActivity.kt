package com.tamanna.enterprise.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.finance.FinanceActivity
import com.tamanna.enterprise.due.CustomerDueActivity
import com.tamanna.enterprise.partner.PartnerActivity
import com.tamanna.enterprise.product.ProductActivity
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseActivity
import com.tamanna.enterprise.purchase.SupplierDueActivity
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.reports.ReportsActivity
import com.tamanna.enterprise.profit.ProfitActivity
import com.tamanna.enterprise.stock.StockActivity
import com.tamanna.enterprise.stock.StockAlertActivity
import com.tamanna.enterprise.sales.SalesActivity
import com.tamanna.enterprise.sales.SalesScanActivity
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.SettingsActivity
import com.tamanna.enterprise.settings.SettingsStorage
import com.tamanna.enterprise.settings.ThemeStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sales = SalesStorage.getSales(this)
        val purchases = PurchaseStorage.getPurchases(this)
        val products = ProductStorage.getProducts(this)
        val todaySales = sales.filter { it.date.startsWith(today) }.sumOf { it.quantity * it.salePrice }
        val todayPurchases = purchases.filter { it.date.startsWith(today) }.sumOf { it.quantity * it.purchasePrice }
        val todayProfit = sales.filter { it.date.startsWith(today) }.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }
        val totalStock = products.sumOf { it.stockQuantity }

        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                DashboardScreen(
                    todaySales, todayPurchases, todayProfit, totalStock,
                    onProductClick = { startActivity(Intent(this, ProductActivity::class.java)) },
                    onPurchaseClick = { startActivity(Intent(this, PurchaseActivity::class.java)) },
                    onSalesClick = { startActivity(Intent(this, SalesActivity::class.java)) },
                    onStockClick = { startActivity(Intent(this, StockActivity::class.java)) },
                    onStockAlertClick = { startActivity(Intent(this, StockAlertActivity::class.java)) },
                    onReportsClick = { startActivity(Intent(this, ReportsActivity::class.java)) },
                    onProfitClick = { startActivity(Intent(this, ProfitActivity::class.java)) },
                    onScannerClick = { startActivity(Intent(this, SalesScanActivity::class.java)) },
                    onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onPartnerClick = { startActivity(Intent(this, PartnerActivity::class.java)) },
                    onFinanceClick = { startActivity(Intent(this, FinanceActivity::class.java)) },
                    onDueClick = { startActivity(Intent(this, CustomerDueActivity::class.java)) },
                    onSupplierDueClick = { startActivity(Intent(this, SupplierDueActivity::class.java)) },
                    shopName = SettingsStorage.getShopName(this)
                )
            }
        }
    }
}

@Composable
fun TamannaTheme(theme: String, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        "blue" -> lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF6A1B9A))
        "purple" -> lightColorScheme(primary = Color(0xFF6A1B9A), secondary = Color(0xFFAD1457), tertiary = Color(0xFF4527A0))
        "dark" -> darkColorScheme(primary = Color(0xFF66BB6A), secondary = Color(0xFF80CBC4), tertiary = Color(0xFFFFB74D))
        else -> lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFF00897B), tertiary = Color(0xFFF9A825))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun DashboardScreen(
    todaySales: Double,
    todayPurchases: Double,
    todayProfit: Double,
    totalStock: Int,
    onProductClick: () -> Unit,
    onPurchaseClick: () -> Unit,
    onSalesClick: () -> Unit,
    onStockClick: () -> Unit,
    onStockAlertClick: () -> Unit,
    onReportsClick: () -> Unit,
    onProfitClick: () -> Unit,
    onScannerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPartnerClick: () -> Unit,
    onFinanceClick: () -> Unit,
    onDueClick: () -> Unit,
    onSupplierDueClick: () -> Unit,
    shopName: String
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Text("⌂", style = MaterialTheme.typography.titleLarge) },
                    label = { Text("হোম") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSalesClick,
                    icon = { Text("🛒", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("বিক্রয়") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onReportsClick,
                    icon = { Text("📊", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("রিপোর্ট") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { Text("⚙", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("সেটিংস") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScannerClick,
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Text("▦", style = MaterialTheme.typography.headlineSmall)
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(shopName, style = MaterialTheme.typography.headlineMedium)
                Text("Shop Management System", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard("আজকের বিক্রয়", "৳ %.2f".format(todaySales), Modifier.weight(1f))
                    DashboardCard("আজকের ক্রয়", "৳ %.2f".format(todayPurchases), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard("আজকের লাভ", "৳ %.2f".format(todayProfit), Modifier.weight(1f))
                    DashboardCard("মোট স্টক", totalStock.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(24.dp))
                Text("প্রধান মেনু", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                DashboardMenuRow(listOf("📦" to ("পণ্য" to onProductClick), "🛒" to ("ক্রয়" to onPurchaseClick), "🧾" to ("বিক্রয়" to onSalesClick)))
                Spacer(Modifier.height(10.dp))
                DashboardMenuRow(listOf("📦" to ("স্টক" to onStockClick), "⚠" to ("স্টক সতর্কতা" to onStockAlertClick), "📊" to ("রিপোর্ট" to onReportsClick)))
                Spacer(Modifier.height(10.dp))
                DashboardMenuRow(listOf("💰" to ("লাভ" to onProfitClick)))
                Spacer(Modifier.height(10.dp))
                DashboardMenuRow(listOf("🤝" to ("পার্টনার" to onPartnerClick), "💸" to ("খরচ/উত্তোলন" to onFinanceClick), "👤" to ("ক্রেতার বাকি" to onDueClick)))
                Spacer(Modifier.height(10.dp))
                DashboardMenuRow(listOf("🏭" to ("সরবরাহকারীর বাকি" to onSupplierDueClick)))
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun DashboardMenuRow(items: List<Pair<String, Pair<String, () -> Unit>>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (icon, item) ->
            DashboardMenuButton(icon, item.first, item.second, Modifier.weight(1f))
        }
        repeat(3 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun DashboardMenuButton(icon: String, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier) {
    val accent = when {
        title.contains("বিক্রয়") -> MaterialTheme.colorScheme.primaryContainer
        title.contains("ক্রয়") -> MaterialTheme.colorScheme.secondaryContainer
        title.contains("লাভ") -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accent),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
