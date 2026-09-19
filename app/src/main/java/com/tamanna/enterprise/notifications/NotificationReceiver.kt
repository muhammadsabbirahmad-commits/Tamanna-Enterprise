package com.tamanna.enterprise.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tamanna.enterprise.due.CustomerDueStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.sales.SalesStorage
import com.tamanna.enterprise.stock.InventoryMetaStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sales = SalesStorage.getSales(context).filter { it.date.startsWith(today) }
        val todaySales = sales.sumOf { it.quantity * it.salePrice }
        val todayProfit = sales.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }

        val products = ProductStorage.getProducts(context)
        val low = products.count {
            val limit = InventoryMetaStorage.getMeta(context, it.code).lowStockLimit
            it.stockQuantity <= limit
        }

        val receivable = CustomerDueStorage.getBalances(context).values.sum()
        val parts = mutableListOf<String>()
        if (low > 0) parts += "কম স্টক $lowটি"
        if (receivable > 0) parts += "ক্রেতার বাকি ৳%.2f".format(Locale.getDefault(), receivable)
        parts += "আজ বিক্রয় ৳%.2f, লাভ ৳%.2f".format(Locale.getDefault(), todaySales, todayProfit)

        NotificationHelper.show(
            context,
            "Tamanna Enterprise — দৈনিক আপডেট",
            parts.joinToString(" • ")
        )
    }
}
