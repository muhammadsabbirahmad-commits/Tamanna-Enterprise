package com.tamanna.enterprise.sales

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
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
        paymentMethod: String,
        invoiceNo: String = "TE-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()),
        dateText: String = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
    ) {
        val file = createInvoicePdf(
            context, transactionId, cart, customer, mobile, subtotal, discount,
            total, paid, due, paymentMethod, invoiceNo, dateText
        )
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Tamanna Enterprise Invoice " + invoiceNo)
            putExtra(Intent.EXTRA_TEXT, "Tamanna Enterprise sales invoice")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ইনভয়েস শেয়ার করুন"))
    }

    fun viewInvoice(
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
        paymentMethod: String,
        invoiceNo: String,
        dateText: String
    ) {
        val file = createInvoicePdf(context, transactionId, cart, customer, mobile, subtotal, discount, total, paid, due, paymentMethod, invoiceNo, dateText)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ইনভয়েস দেখুন"))
    }

    fun printInvoice(
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
        paymentMethod: String,
        invoiceNo: String,
        dateText: String
    ) {
        val file = createInvoicePdf(context, transactionId, cart, customer, mobile, subtotal, discount, total, paid, due, paymentMethod, invoiceNo, dateText)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "Tamanna Enterprise " + invoiceNo,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal,
                    callback: LayoutResultCallback,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder(file.name)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build(),
                        true
                    )
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal,
                    callback: WriteResultCallback
                ) {
                    try {
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return
                        }
                        FileInputStream(file).use { input ->
                            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    }
                }
            },
            null
        )
    }

    private fun createInvoicePdf(
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
        paymentMethod: String,
        invoiceNo: String,
        dateText: String
    ): File {
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
        line("Invoice: " + invoiceNo)
        line("Transaction: " + transactionId)
        line("Date: " + dateText)
        if (customer.isNotBlank()) line("Customer: " + customer)
        if (mobile.isNotBlank()) line("Mobile: " + mobile)
        line("---------------------------------------------")
        cart.forEachIndexed { index, item ->
            val amount = item.quantity * item.unitPrice
            line((index + 1).toString() + ". " + item.name.take(34))
            line("   " + item.quantity + " x " + money(item.unitPrice) + " = " + money(amount))
        }
        line("---------------------------------------------")
        line("Subtotal: " + money(subtotal))
        line("Discount: " + money(discount))
        line("Grand Total: " + money(total), 15f)
        line("Paid: " + money(paid))
        line("Due: " + money(due))
        line("Payment: " + paymentMethod)
        line("")
        line("Thank you for shopping with us.")

        document.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = invoiceNo.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "invoice-" + safeName + ".pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun money(value: Double): String =
        "৳" + String.format(Locale.getDefault(), "%.2f", value)
}

data class InvoiceLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)
