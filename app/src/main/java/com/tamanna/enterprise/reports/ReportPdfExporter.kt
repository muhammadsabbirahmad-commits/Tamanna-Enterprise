package com.tamanna.enterprise.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.tamanna.enterprise.purchase.Purchase
import com.tamanna.enterprise.sales.Sale
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ReportPdfExporter {
    fun exportAndShare(
        context: Context,
        fromDate: String,
        toDate: String,
        sales: List<Sale>,
        purchases: List<Purchase>,
        currentStockUnits: Int,
        stockValueAtPurchase: Double
    ) {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
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
            canvas.drawText(text.take(95), 32f, y, paint)
            y += 17f
        }

        val salesAmount = sales.sumOf { it.quantity * it.salePrice }
        val costOfSales = sales.sumOf { it.quantity * it.purchasePrice }
        val profit = salesAmount - costOfSales
        val purchaseAmount = purchases.sumOf { it.quantity * it.purchasePrice }

        canvas.drawText("Tamanna Enterprise - Report", 32f, y, titlePaint)
        y += 24f
        line("Period: ${fromDate} to ${toDate}")
        line("Sales: ৳ %.2f | Purchases: ৳ %.2f".format(Locale.getDefault(), salesAmount, purchaseAmount))
        line("Profit: ৳ %.2f | Sold: %d | Purchased: %d".format(
            Locale.getDefault(), profit, sales.sumOf { it.quantity }, purchases.sumOf { it.quantity }
        ))
        line("Current stock: ${currentStockUnits} units | Stock cost: ৳ %.2f".format(
            Locale.getDefault(), stockValueAtPurchase
        ))
        y += 10f
        line("SALES DETAILS", true)
        if (sales.isEmpty()) line("No sales in selected period.")
        sales.forEach {
            line("${it.date} | ${it.productCode} | ${it.productName} | Qty ${it.quantity} | Sale ৳ %.2f | Profit ৳ %.2f".format(
                Locale.getDefault(), it.quantity * it.salePrice, it.quantity * (it.salePrice - it.purchasePrice)
            ))
        }
        y += 10f
        line("PURCHASE DETAILS", true)
        if (purchases.isEmpty()) line("No purchases in selected period.")
        purchases.forEach {
            line("${it.date} | ${it.productCode} | ${it.productName} | Qty ${it.quantity} | Cost ৳ %.2f | Memo ${it.memoNumber}".format(
                Locale.getDefault(), it.quantity * it.purchasePrice
            ))
        }

        pdf.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "tamanna_report_${fromDate}_to_${toDate}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "রিপোর্ট PDF শেয়ার করুন"))
    }
}
