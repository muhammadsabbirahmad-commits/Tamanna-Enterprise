package com.tamanna.enterprise.security

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class UserManagementActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val current = SecurityStorage.getCurrentUser(this)
        if (current?.role != SecurityStorage.ROLE_ADMIN) {
            finish()
            return
        }

        CloudAccessManager.validateCurrentSession(this) { valid ->
            runOnUiThread {
                if (!valid) finish()
            }
        }

        setContent {
            var users by remember { mutableStateOf<List<CloudAccessUser>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var message by remember { mutableStateOf("") }
            var refresh by remember { mutableStateOf(0) }
            var pendingAction by remember { mutableStateOf<Pair<String, CloudAccessUser>?>(null) }

            LaunchedEffect(refresh) {
                loading = true
                CloudAccessManager.listBusinessMembers(this@UserManagementActivity) { result, error ->
                    users = result
                    message = error.orEmpty()
                    loading = false
                }
            }

            val pending = users.filter { !it.approved && !it.blocked && it.role == SecurityStorage.ROLE_PARTNER }
            val approvedAdmins = users.filter { it.approved && it.role == SecurityStorage.ROLE_ADMIN }
            val activePartners = users.filter { it.approved && !it.blocked && it.role == SecurityStorage.ROLE_PARTNER }
            val blockedPartners = users.filter { it.blocked && it.role == SecurityStorage.ROLE_PARTNER }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ইউজার ম্যানেজমেন্ট", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(4.dp))
                                Text("Partner access এখান থেকেই নিয়ন্ত্রণ করুন।", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { refresh++ },
                                enabled = !loading
                            ) {
                                Text("Refresh")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Active: ${activePartners.size}  •  Pending: ${pending.size}  •  Blocked: ${blockedPartners.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))

                        if (loading) {
                            Text("তালিকা লোড হচ্ছে...")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { Text("অনুমোদিত Admin", style = MaterialTheme.typography.titleMedium) }
                                if (approvedAdmins.isEmpty()) {
                                    item { Text("কোনো Admin নেই।", style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    items(approvedAdmins, key = { "admin-" + it.uid }) { user ->
                                        UserRow(user, "🟢 Active", emptyList(), {})
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Active Partner", style = MaterialTheme.typography.titleMedium)
                                }
                                if (activePartners.isEmpty()) {
                                    item { Text("কোনো Active Partner নেই।", style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    items(activePartners, key = { "active-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            statusLabel = "🟢 Active",
                                            actions = listOf("Block", "Remove"),
                                            onAction = { action -> pendingAction = action to user }
                                        )
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Blocked Partner", style = MaterialTheme.typography.titleMedium)
                                }
                                if (blockedPartners.isEmpty()) {
                                    item { Text("কোনো Blocked Partner নেই।", style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    items(blockedPartners, key = { "blocked-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            statusLabel = "🔴 Blocked",
                                            actions = listOf("Unblock", "Remove"),
                                            onAction = { action -> pendingAction = action to user }
                                        )
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Pending Partner", style = MaterialTheme.typography.titleMedium)
                                }
                                if (pending.isEmpty()) {
                                    item { Text("অনুমোদনের অপেক্ষায় কেউ নেই।", style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    items(pending, key = { "pending-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            statusLabel = "🟡 Pending",
                                            actions = listOf("Approve", "Reject"),
                                            onAction = { action -> pendingAction = action to user }
                                        )
                                    }
                                }

                                if (message.isNotBlank()) {
                                    item {
                                        Spacer(Modifier.height(4.dp))
                                        Text(message, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    val action = pendingAction
                    if (action != null) {
                        val (type, user) = action
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { pendingAction = null },
                            title = { Text(type) },
                            text = {
                                Text(
                                    when (type) {
                                        "Block" -> "এই Partner-এর প্রবেশ সাময়িকভাবে বন্ধ করবেন?"
                                        "Unblock" -> "এই Partner-এর প্রবেশ আবার চালু করবেন?"
                                        "Remove" -> "এই Partner-কে তালিকা থেকে সম্পূর্ণভাবে Remove করবেন?"
                                        "Approve" -> "এই Partner-এর আবেদন অনুমোদন করবেন?"
                                        else -> "এই Partner-এর আবেদন প্রত্যাখ্যান করবেন?"
                                    }
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    pendingAction = null
                                    when (type) {
                                        "Block" -> CloudAccessManager.blockPartner(this@UserManagementActivity, user.uid) { ok, msg ->
                                            message = msg
                                            if (ok) refresh++
                                        }
                                        "Unblock" -> CloudAccessManager.unblockPartner(this@UserManagementActivity, user.uid) { ok, msg ->
                                            message = msg
                                            if (ok) refresh++
                                        }
                                        "Remove" -> CloudAccessManager.removePartner(this@UserManagementActivity, user.uid) { ok, msg ->
                                            message = msg
                                            if (ok) refresh++
                                        }
                                        "Approve" -> CloudAccessManager.approvePartner(this@UserManagementActivity, user.uid) { ok, msg ->
                                            message = msg
                                            if (ok) refresh++
                                        }
                                        "Reject" -> CloudAccessManager.removePartner(this@UserManagementActivity, user.uid) { ok, msg ->
                                            message = msg
                                            if (ok) refresh++
                                        }
                                    }
                                }) { Text("হ্যাঁ") }
                            },
                            dismissButton = {
                                Button(onClick = { pendingAction = null }) { Text("না") }
                            }
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun UserRow(
        user: CloudAccessUser,
        statusLabel: String,
        actions: List<String>,
        onAction: (String) -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel, style = MaterialTheme.typography.bodySmall)
                if (actions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        actions.forEach { action ->
                            Button(onClick = { onAction(action) }) {
                                Text(action)
                            }
                        }
                    }
                }
            }
        }
    }
}
