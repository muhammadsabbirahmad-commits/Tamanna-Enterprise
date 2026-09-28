package com.tamanna.enterprise.reports

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.tamanna.enterprise.due.DueEntry
import com.tamanna.enterprise.finance.DamageRecord
import com.tamanna.enterprise.finance.Expense
import com.tamanna.enterprise.finance.PartnerWithdrawal
import com.tamanna.enterprise.partner.Partner
import com.tamanna.enterprise.partner.PartnerStorage
import com.tamanna.enterprise.product.Product
import com.tamanna.enterprise.purchase.Purchase
import com.tamanna.enterprise.purchase.SupplierDueEntry
import com.tamanna.enterprise.sales.Sale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportPdfExporter {
    fun exportToDownloads(
        context: Context,
        fromDate: String,
        toDate: String,
        selected: Set<String>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        products: List<Product>,
        partners: List<Partner>,
        expenses: List<Expense>,
        withdrawals: List<PartnerWithdrawal>,
        damages: List<DamageRecord>,
        customerDue: List<DueEntry>,
        supplierDue: List<SupplierDueEntry>
    ): String {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        var y = 40f

        fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            y = 40f
        }
        fun line(text: String, bold: Boolean = false) {
            if (y > 810f) newPage()
            paint.isFakeBoldText = bold
            canvas.drawText(text.take(100), 32f, y, paint)
            y += 16f
        }
        fun section(title: String) { y += 8f; line(title, true) }

        canvas.drawText("Tamanna Enterprise", 32f, y, titlePaint)
        y += 24f
        line("Customized PDF Report")
        line("Period: " + fromDate + " to " + toDate)
        line("Selected sections: " + selected.size)
        line("Created: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))

        if ("sales" in selected || "profit" in selected) {
            val salesAmount = sales.sumOf { it.quantity * it.salePrice }
            val cost = sales.sumOf { it.quantity * it.purchasePrice }
            val profit = salesAmount - cost
            section("SALES / PROFIT SUMMARY")
            if ("sales" in selected) line("Total Sales: ৳ %.2f | Units: %d".format(Locale.getDefault(), salesAmount, sales.sumOf { it.quantity }))
            if ("profit" in selected) line("Gross Profit: ৳ %.2f | Cost of Sales: ৳ %.2f".format(Locale.getDefault(), profit, cost))
            if ("sales" in selected) sales.forEach { line(it.date + " | " + it.productCode + " | " + it.productName + " | Qty " + it.quantity + " | ৳ %.2f".format(Locale.getDefault(), it.quantity * it.salePrice)) }
        }
        if ("purchase" in selected) {
            section("PURCHASES")
            line("Total Purchase: ৳ %.2f | Units: %d".format(Locale.getDefault(), purchases.sumOf { it.quantity * it.purchasePrice }, purchases.sumOf { it.quantity }))
            purchases.forEach { line(it.date + " | " + it.productCode + " | " + it.productName + " | Qty " + it.quantity + " | ৳ %.2f".format(Locale.getDefault(), it.quantity * it.purchasePrice)) }
        }
        if ("stock" in selected) {
            section("CURRENT STOCK")
            line("Total Units: " + products.sumOf { it.stockQuantity })
            line("Stock Cost: ৳ %.2f".format(Locale.getDefault(), products.sumOf { it.stockQuantity * it.purchasePrice }))
            products.forEach { line(it.code + " | " + it.name + " | Qty " + it.stockQuantity + " | Cost ৳ %.2f".format(Locale.getDefault(), it.stockQuantity * it.purchasePrice)) }
        }
        if ("partners" in selected) {
            val profit = sales.sumOf { it.quantity * (it.salePrice - it.purchasePrice) }.coerceAtLeast(0.0)
            section("PARTNERS")
            line("Total Investment: ৳ %.2f".format(Locale.getDefault(), partners.sumOf { it.investment }))
            partners.forEach { line(it.name + " | Investment ৳ %.2f | ".format(Locale.getDefault(), it.investment) + it.percentage + "% | Profit Share ৳ %.2f".format(Locale.getDefault(), PartnerStorage.profitShare(profit, it))) }
        }
        if ("withdrawal" in selected) {
            section("PARTNER WITHDRAWALS")
            line("Total Withdrawal: ৳ %.2f".format(Locale.getDefault(), withdrawals.sumOf { it.amount }))
            withdrawals.forEach { line(it.date + " | " + it.partnerName + " | ৳ %.2f | ".format(Locale.getDefault(), it.amount) + it.method) }
        }
        if ("expense" in selected) {
            section("BUSINESS EXPENSES")
            line("Total Expense: ৳ %.2f".format(Locale.getDefault(), expenses.sumOf { it.amount }))
            expenses.forEach { line(it.date + " | " + it.category + " | ৳ %.2f | ".format(Locale.getDefault(), it.amount) + it.note) }
        }
        if ("damage" in selected) {
            section("DAMAGED GOODS")
            line("Total Loss: ৳ %.2f".format(Locale.getDefault(), damages.sumOf { it.totalLoss }))
            damages.forEach { line(it.date + " | " + it.productCode + " | " + it.productName + " | Qty " + it.quantity + " | Loss ৳ %.2f".format(Locale.getDefault(), it.totalLoss)) }
        }
        if ("customerDue" in selected) {
            section("CUSTOMER DUE")
            line("Net Due Entries: ৳ %.2f".format(Locale.getDefault(), customerDue.sumOf { if (it.type == "SALE") it.amount else -it.amount }))
            customerDue.forEach { line(it.date + " | " + it.customer + " | " + it.type + " | ৳ %.2f".format(Locale.getDefault(), it.amount)) }
        }
        if ("supplierDue" in selected) {
            section("SUPPLIER DUE")
            line("Net Supplier Due: ৳ %.2f".format(Locale.getDefault(), supplierDue.sumOf { it.amount }))
            supplierDue.forEach { line(it.date + " | " + it.supplier + " | " + it.type + " | ৳ %.2f".format(Locale.getDefault(), it.amount)) }
        }

        pdf.finishPage(page)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Tamanna_Report_" + fromDate + "_to_" + toDate + ".pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Download folder-এ PDF তৈরি করা যায়নি।")
                try {
                    resolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                        ?: throw IllegalStateException("PDF ফাইল লেখা যায়নি।")
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                FileOutputStream(File(dir, "Tamanna_Report_" + fromDate + "_to_" + toDate + ".pdf")).use { pdf.writeTo(it) }
            }
            pdf.close()
            "✅ PDF সফলভাবে ফোনের Download ফোল্ডারে সেভ হয়েছে।"
        } catch (e: Exception) {
            pdf.close()
            "❌ PDF সেভ করা যায়নি: " + (e.message ?: "অজানা সমস্যা")
        }
    }
}
