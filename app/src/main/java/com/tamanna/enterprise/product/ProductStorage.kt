package com.tamanna.enterprise.product

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Product(
    val code: String,
    val name: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val stockQuantity: Int,
    val createdAt: Long = System.currentTimeMillis()
)

object ProductStorage {
    private const val PREFS = "tamanna_enterprise_products"
    private const val KEY_PRODUCTS = "products"
    private const val KEY_NEXT_CODE = "next_product_code"
    private const val FIRST_PRODUCT_NUMBER = 228622

    fun getProducts(context: Context): List<Product> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_PRODUCTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Product(
                    item.getString("code"),
                    item.getString("name"),
                    item.getDouble("purchasePrice"),
                    item.getDouble("salePrice"),
                    item.getInt("stockQuantity"),
                    item.optLong("createdAt", i.toLong())
                ))
            }
        }
    }

    fun nextProductCode(context: Context): String {
        val prefs = BusinessStorage.prefs(context, PREFS)
        var next = prefs.getInt(KEY_NEXT_CODE, -1)

        if (next < FIRST_PRODUCT_NUMBER) {
            val maxExisting = getProducts(context).mapNotNull { product ->
                Regex("^P-(\\d+)$", RegexOption.IGNORE_CASE)
                    .matchEntire(product.code.trim())
                    ?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
            }.maxOrNull() ?: 0
            next = maxOf(FIRST_PRODUCT_NUMBER, maxExisting + 1)
        }

        prefs.edit().putInt(KEY_NEXT_CODE, next + 1).apply()
        return "P-" + next.toString().padStart(6, '0')
    }

    fun addProduct(context: Context, product: Product): Boolean {
        val normalizedCode = product.code.trim().uppercase()
        val codeNumber = Regex("^P-(\\d{6})$")
            .matchEntire(normalizedCode)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?: return false
        if (codeNumber < FIRST_PRODUCT_NUMBER) return false

        val products = getProducts(context).toMutableList()
        if (products.any { it.code.equals(normalizedCode, ignoreCase = true) }) return false

        val prefs = BusinessStorage.prefs(context, PREFS)
        val currentNext = prefs.getInt(KEY_NEXT_CODE, FIRST_PRODUCT_NUMBER)
        if (codeNumber >= currentNext) {
            prefs.edit()
                .putInt(KEY_NEXT_CODE, maxOf(FIRST_PRODUCT_NUMBER, codeNumber + 1))
                .apply()
        }

        products.add(
            product.copy(
                code = normalizedCode,
                createdAt = if (product.createdAt > 0) product.createdAt else System.currentTimeMillis()
            )
        )
        saveProducts(context, products)
        return true
    }

    fun updateProduct(context: Context, product: Product): Boolean {
        val products = getProducts(context)
        var found = false
        val updated = products.map {
            if (it.code.equals(product.code, ignoreCase = true)) {
                found = true
                product.copy(code = it.code, createdAt = it.createdAt)
            } else it
        }
        if (!found) return false
        saveProducts(context, updated)
        return true
    }

    fun updateStock(context: Context, code: String, newStock: Int): Boolean {
        val products = getProducts(context)
        var found = false
        val updated = products.map {
            if (it.code.equals(code, ignoreCase = true)) {
                found = true
                it.copy(stockQuantity = newStock)
            } else it
        }
        if (!found) return false
        saveProducts(context, updated)
        return true
    }

    fun deleteProduct(context: Context, code: String): Boolean {
        val products = getProducts(context)
        val updated = products.filterNot { it.code.equals(code, ignoreCase = true) }
        if (updated.size == products.size) return false
        saveProducts(context, updated)
        return true
    }

    private fun saveProducts(context: Context, products: List<Product>) {
        val array = JSONArray()
        products.forEach {
            array.put(JSONObject().apply {
                put("code", it.code)
                put("name", it.name)
                put("purchasePrice", it.purchasePrice)
                put("salePrice", it.salePrice)
                put("stockQuantity", it.stockQuantity)
                put("createdAt", it.createdAt)
            })
        }
        BusinessStorage.prefs(context, PREFS)
            .edit().putString(KEY_PRODUCTS, array.toString()).apply()
    }
}