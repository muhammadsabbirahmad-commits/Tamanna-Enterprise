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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.business.ViewAccessManager
import com.tamanna.enterprise.finance.FinanceActivity
import com.tamanna.enterprise.partner.PartnerActivity
import com.tamanna.enterprise.due.CustomerDueActivity
import com.tamanna.enterprise.product.ProductActivity
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.purchase.PurchaseActivity
import com.tamanna.enterprise.purchase.SupplierDueActivity
import com.tamanna.enterprise.purchase.PurchaseStorage
import com.tamanna.enterprise.reports.ReportsActivity
import com.tamanna.enterprise.reports.FinancialDashboardActivity
import com.tamanna.enterprise.reports.FinancialCalendarActivity
import com.tamanna.enterprise.profit.ProfitActivity
import com.tamanna.enterprise.stock.StockActivity
import com.tamanna.enterprise.stock.StockAlertActivity
import com.tamanna.enterprise.sales.SalesActivity
import com.tamanna.enterprise.sales.SalesScanActivity
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.settings.SettingsActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.settings.SettingsStorage
import com.tamanna.enterprise.settings.ThemeStorage
import com.tamanna.enterprise.search.GlobalSearchActivity
import com.tamanna.enterprise.sync.CloudBackupActivity
import com.tamanna.enterprise.sync.CloudSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        
        val context = this
        val currentBusinessId = BusinessStorage.getActiveBusinessId(context).trim()
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        
        // ড্যাশবোর্ডের ডাটা হিসাব করে UI সেট করার ফাংশন
        fun renderDashboardUI() {
            val loggedIn = SecurityStorage.getCurrentUser(context) != null
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // Data Fetching
            val sales = if (loggedIn) SalesStorage.getSales(context) else emptyList()
            val purchases = if (loggedIn) PurchaseStorage.getPurchases(context) else emptyList()
            val products = if (loggedIn) ProductStorage.getProducts(context) else emptyList()
            
            // Calculations with Flexible Date Format
            val todaySales = sales.filter { 
                it.date.contains(today) || it.date.startsWith(today) 
            }.sumOf { it.quantity * it.salePrice }

            val todayPurchases = purchases.filter { 
                it.date.contains(today) || it.date.startsWith(today) 
            }.sumOf { it.quantity * it.purchasePrice }

            val todayProfit = sales.filter { 
                it.date.contains(today) || it.date.startsWith(today) 
            }.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }

            val totalStock = products.sumOf { it.stockQuantity }

            setContent {
                TamannaTheme(ThemeStorage.getTheme(context)) {
                    DashboardScreen(
                        todaySales = todaySales,
                        todayPurchases = todayPurchases,
                        todayProfit = todayProfit,
                        totalStock = totalStock,
                        loggedIn = loggedIn,
                        onProductClick = { if (loggedIn) startActivity(Intent(context, ProductActivity::class.java)) },
                        onPurchaseClick = { if (loggedIn) startActivity(Intent(context, PurchaseActivity::class.java)) },
                        onSalesClick = { if (loggedIn) startActivity(Intent(context, SalesActivity::class.java)) },
                        onStockClick = { if (loggedIn) startActivity(Intent(context, StockActivity::class.java)) },
                        onStockAlertClick = { if (loggedIn) startActivity(Intent(context, StockAlertActivity::class.java)) },
                        onReportsClick = { if (loggedIn) startActivity(Intent(context, ReportsActivity::class.java)) },
                        onFinancialDashboardClick = { if (loggedIn) startActivity(Intent(context, FinancialDashboardActivity::class.java)) },
                        onFinancialCalendarClick = { if (loggedIn) startActivity(Intent(context, FinancialCalendarActivity::class.java)) },
                        onProfitClick = { if (loggedIn) startActivity(Intent(context, ProfitActivity::class.java)) },
                        onScannerClick = { if (loggedIn) startActivity(Intent(context, SalesScanActivity::class.java)) },
                        onSettingsClick = { startActivity(Intent(context, SettingsActivity::class.java)) },
                        onFinanceClick = { if (loggedIn) startActivity(Intent(context, FinanceActivity::class.java)) },
                        onDueClick = { if (loggedIn) startActivity(Intent(context, CustomerDueActivity::class.java)) },
                        onSupplierDueClick = { if (loggedIn) startActivity(Intent(context, SupplierDueActivity::class.java)) },
                        onPartnerClick = { if (loggedIn) startActivity(Intent(context, PartnerActivity::class.java)) },
                        onGlobalSearchClick = { if (loggedIn) startActivity(Intent(context, GlobalSearchActivity::class.java)) },
                        onBackupClick = { if (loggedIn) startActivity(Intent(context, CloudBackupActivity::class.java)) },
                        shopName = SettingsStorage.getShopName(context)
                    )
                }
            }
        }

        // ১. আগে লোকাল ডাটা দিয়ে দ্রুত UI রেন্ডার করা
        renderDashboardUI()

        // ২. Business ID মিসিং থাকলে Admin বা Viewer হিসেবে আইডি রিকভারি
        if (currentBusinessId.isBlank() && firebaseUser != null) {
            val uid = firebaseUser.uid
            val userEmail = firebaseUser.email.orEmpty()

            FirebaseFirestore.getInstance().collection("businesses")
                .whereEqualTo("ownerUid", uid)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val bId = documents.documents[0].id
                        BusinessStorage.setActiveBusinessId(context, bId)
                        CloudSyncManager.smartSync(context, isAuto = true) { _, isDataSynced ->
                            if (isDataSynced) renderDashboardUI()
                        }
                    } else {
                        // যদি Owner না হয়, তবে Viewer চেক
                        ViewAccessManager.findForEmailAcrossBusinesses(userEmail) { viewEntry ->
                            val bId = viewEntry?.businessId ?: uid
                            BusinessStorage.setActiveBusinessId(context, bId)
                            CloudSyncManager.smartSync(context, isAuto = true) { _, isDataSynced ->
                                if (isDataSynced) renderDashboardUI()
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    BusinessStorage.setActiveBusinessId(context, uid)
                    CloudSyncManager.smartSync(context, isAuto = true) { _, isDataSynced ->
                        if (isDataSynced) renderDashboardUI()
                    }
                }
        } else if (firebaseUser != null) {
            // ৩. ব্যাকগ্রাউন্ডে এককালীন সিঙ্ক সম্পন্ন হলে UI রিফ্রেশ করা
            CloudSyncManager.smartSync(context, isAuto = true) { _, isDataSynced ->
                if (isDataSynced) {
                    renderDashboardUI()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
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
    loggedIn: Boolean,
    onProductClick: () -> Unit,
    onPurchaseClick: () -> Unit,
    onSalesClick: () -> Unit,
    onStockClick: () -> Unit,
    onStockAlertClick: () -> Unit,
    onReportsClick: () -> Unit,
    onFinancialDashboardClick: () -> Unit,
    onProfitClick: () -> Unit,
    onScannerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFinanceClick: () -> Unit,
    onDueClick: () -> Unit,
    onSupplierDueClick: () -> Unit,
    onPartnerClick: () -> Unit,
    onGlobalSearchClick: () -> Unit,
    onBackupClick: () -> Unit,
    onFinancialCalendarClick: () -> Unit,
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
                // Header Section
                Text(shopName, style = MaterialTheme.typography.headlineMedium)
                Text("Shop Management System", style = MaterialTheme.typography.bodyMedium)
                
                if (!loggedIn) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔒 ব্যবসায়িক ডাটা লক করা আছে। সেটিংস → Google/Admin Login থেকে প্রবেশ করুন।",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(18.dp))

                // Dashboard Cards Section
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard("আজকের বিক্রয়", "৳ %.2f".format(todaySales), Color(0xFFE3F2FD), Modifier.weight(1f))
                    DashboardCard("আজকের ক্রয়", "৳ %.2f".format(todayPurchases), Color(0xFFE8F5E9), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard("আজকের লাভ", "৳ %.2f".format(todayProfit), Color(0xFFFFF3E0), Modifier.weight(1f))
                    DashboardCard("মোট স্টক", totalStock.toString(), Color(0xFFF3E5F5), Modifier.weight(1f))
                }

                Spacer(Modifier.height(24.dp))

                // Main Menu Section
                Text("প্রধান মেনু", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                val menuItems = listOf(
                    "📦" to ("পণ্য" to onProductClick),
                    "🛒" to ("ক্রয়" to onPurchaseClick),
                    "🧾" to ("বিক্রয়" to onSalesClick),
                    "📦" to ("স্টক" to onStockClick),
                    "⚠" to ("স্টক সতর্কতা" to onStockAlertClick),
                    "📊" to ("রিপোর্ট" to onReportsClick),
                    "📈" to ("আর্থিক ড্যাশবোর্ড" to onFinancialDashboardClick),
                    "📅" to ("আর্থিক ক্যালেন্ডার" to onFinancialCalendarClick),
                    "🔎" to ("গ্লোবাল সার্চ" to onGlobalSearchClick),
                    "💰" to ("লাভ" to onProfitClick),
                    "💸" to ("খরচ/ড্যামেজ" to onFinanceClick),
                    "👤" to ("ক্রেতার বাকি" to onDueClick),
                    "🏭" to ("সরবরাহকারীর বাকি" to onSupplierDueClick),
                    "👥" to ("পার্টনার" to onPartnerClick),
                    "☁️" to ("ডাটা ব্যাকআপ" to onBackupClick)
                )

                menuItems.chunked(3).forEachIndexed { index, rowItems ->
                    DashboardMenuRow(rowItems, index * 3)
                    if (index < (menuItems.size + 2) / 3 - 1) Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

private val menuColors = listOf(
    Color(0xFFE3F2FD), Color(0xFFFFF3E0), Color(0xFFE8F5E9),
    Color(0xFFF3E5F5), Color(0xFFFFEBEE), Color(0xFFE0F7FA),
    Color(0xFFFFF8E1), Color(0xFFE8EAF6), Color(0xFFF1F8E9),
    Color(0xFFFCE4EC), Color(0xFFEDE7F6), Color(0xFFE0F2F1),
    Color(0xFFFFF3E0), Color(0xFFE1F5FE), Color(0xFFE8F5E9)
)

@Composable
private fun DashboardMenuRow(
    items: List<Pair<String, Pair<String, () -> Unit>>>,
    startIndex: Int
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, (icon, item) ->
            DashboardMenuButton(
                icon = icon,
                title = item.first,
                onClick = item.second,
                background = menuColors[(startIndex + index) % menuColors.size],
                modifier = Modifier.weight(1f)
            )
        }
        
        repeat(3 - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashboardMenuButton(
    icon: String,
    title: String,
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = background,
            contentColor = Color(0xFF263238)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    background: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF263238))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color(0xFF263238))
        }
    }
}
