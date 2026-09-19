package com.tamanna.enterprise.settings

import android.content.Context
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SettingsStorage {
    private const val PREFS = "tamanna_enterprise_settings"
    private const val KEY_SHOP_NAME = "shop_name"
    private const val DEFAULT_SHOP_NAME = "Tamanna Enterprise"

    fun getShopName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME)
            ?: DEFAULT_SHOP_NAME

    fun saveShopName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHOP_NAME, name.trim().ifBlank { DEFAULT_SHOP_NAME })
            .apply()
    }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingsScreen(
                initialShopName = SettingsStorage.getShopName(this),
                onSave = { name ->
                    SettingsStorage.saveShopName(this, name)
                    finish()
                },
                onCancel = { finish() }
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    initialShopName: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var shopName by remember { mutableStateOf(initialShopName) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text("সেটিংস", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                Text("দোকানের তথ্য", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("দোকানের নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("বাতিল")
                    }

                    Button(
                        onClick = { onSave(shopName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("সংরক্ষণ")
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    "সংরক্ষণ করলে দোকানের নাম ড্যাশবোর্ডে দেখাবে।",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
