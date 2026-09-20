package com.tamanna.enterprise.security

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamanna.enterprise.dashboard.TamannaTheme
import com.tamanna.enterprise.settings.ThemeStorage

@OptIn(ExperimentalMaterial3Api::class)
class ActivityLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = SecurityStorage.getCurrentUser(this)
        if (current?.role != SecurityStorage.ROLE_ADMIN) {
            finish()
            return
        }
        setContent {
            TamannaTheme(ThemeStorage.getTheme(this)) {
                Scaffold(topBar = { TopAppBar(title = { Text("অ্যাক্টিভিটি লগ") }) }) { padding ->
                    val logs = remember { ActivityLogStorage.get(this@ActivityLogActivity) }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(entry.action, style = MaterialTheme.typography.titleMedium)
                                    Text(entry.date + " • " + entry.username, style = MaterialTheme.typography.bodySmall)
                                    if (entry.details.isNotBlank()) Text(entry.details)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
