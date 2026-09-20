package com.tamanna.enterprise.sales

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfUtil {
    fun shareInvoice(
        context: Context,
        transactionId: String,
        cart: List<InvoiceLine>,
        customer: String,
        mobile: String,
        subtotal: Double,
        discount: Double,
        total: Double,
        paid: Double,
        due: Double,
        paymentMethod: String
    ) {
        val invoiceNo = "TE-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val date = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        var y = 45f

        fun line(text: String, size: Float = 12f) {
            paint.textSize = size
            canvas.drawText(text, 40f, y, paint)
            y += size + 10f
        }

        line("TAMANNA ENTERPRISE", 22f)
        line("Sales Invoice", 16f)
        line("Invoice: $invoiceNo")
        line("Transaction: $transactionId")
        line("Date: $date")
        if (customer.isNotBlank()) line("Customer: $customer")
        if (mobile.isNotBlank()) line("Mobile: $mobile")
        line("---------------------------------------------")
        cart.forEachIndexed { index, item ->
            val amount = item.quantity * item.unitPrice
            line("${index + 1}. ${item.name.take(34)}")
            line("   ${item.quantity} x ${money(item.unitPrice)} = ${money(amount)}")
        }
        line("---------------------------------------------")
        line("Subtotal: ${money(subtotal)}")
        line("Discount: ${money(discount)}")
        line("Grand Total: ${money(total)}", 15f)
        line("Paid: ${money(paid)}")
        line("Due: ${money(due)}")
        line("Payment: $paymentMethod")
        line("")
        line("Thank you for shopping with us.")

        document.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "invoice-$invoiceNo.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Tamanna Enterprise Invoice $invoiceNo")
            putExtra(Intent.EXTRA_TEXT, "Tamanna Enterprise sales invoice")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ইনভয়েস শেয়ার করুন"))
    }

    private fun money(value: Double): String =
        "৳" + String.format(Locale.getDefault(), "%.2f", value)
}

data class InvoiceLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)
