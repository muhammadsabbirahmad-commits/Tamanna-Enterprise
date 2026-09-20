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

        setContent {
            var users by remember { mutableStateOf<List<CloudAccessUser>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var message by remember { mutableStateOf("") }
            var refresh by remember { mutableStateOf(0) }

            LaunchedEffect(refresh) {
                loading = true
                CloudAccessManager.listUsers { result, error ->
                    users = result
                    message = error.orEmpty()
                    loading = false
                }
            }

            val pending = users.filter { !it.approved && it.role == SecurityStorage.ROLE_PARTNER }
            val approvedAdmins = users.filter { it.approved && it.role == SecurityStorage.ROLE_ADMIN }
            val approvedPartners = users.filter { it.approved && it.role == SecurityStorage.ROLE_PARTNER }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Text("ইউজার ম্যানেজমেন্ট", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Google account-ই পরিচয়। Gmail connected থাকলেই Admin হওয়া যাবে না।",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(Modifier.height(18.dp))

                        if (loading) {
                            Text("তালিকা লোড হচ্ছে...")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text("অনুমোদিত Admin", style = MaterialTheme.typography.titleLarge)
                                }
                                if (approvedAdmins.isEmpty()) {
                                    item { Text("কোনো অনুমোদিত Admin পাওয়া যায়নি।") }
                                } else {
                                    items(approvedAdmins, key = { "admin-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            actionLabel = null,
                                            onAction = {}
                                        )
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text("অনুমোদিত Partner", style = MaterialTheme.typography.titleLarge)
                                }
                                if (approvedPartners.isEmpty()) {
                                    item { Text("কোনো অনুমোদিত Partner নেই।") }
                                } else {
                                    items(approvedPartners, key = { "partner-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            actionLabel = "অ্যাক্সেস বাতিল",
                                            onAction = {
                                                CloudAccessManager.revokeUser(this@UserManagementActivity, user.uid) { ok, msg ->
                                                    message = msg
                                                    if (ok) refresh++
                                                }
                                            }
                                        )
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Pending Partner", style = MaterialTheme.typography.titleLarge)
                                }
                                if (pending.isEmpty()) {
                                    item { Text("অনুমোদনের অপেক্ষায় কোনো Partner নেই।") }
                                } else {
                                    items(pending, key = { "pending-" + it.uid }) { user ->
                                        UserRow(
                                            user = user,
                                            actionLabel = "অনুমোদন করুন",
                                            secondaryActionLabel = "প্রত্যাখ্যান করুন",
                                            onAction = {
                                                CloudAccessManager.approvePartner(
                                                    this@UserManagementActivity,
                                                    user.uid
                                                ) { ok, msg ->
                                                    message = msg
                                                    if (ok) refresh++
                                                }
                                            },
                                            onSecondaryAction = {
                                                CloudAccessManager.rejectPendingPartner(user.uid) { ok, msg ->
                                                    message = msg
                                                    if (ok) refresh++
                                                }
                                            }
                                        )
                                    }
                                }

                                if (message.isNotBlank()) {
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Text(message, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun UserRow(
        user: CloudAccessUser,
        actionLabel: String?,
        secondaryActionLabel: String? = null,
        onAction: () -> Unit,
        onSecondaryAction: () -> Unit = {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.email, style = MaterialTheme.typography.bodyLarge)
                Text(
                    SecurityStorage.roleLabel(user.role) +
                        if (user.approved) " • অনুমোদিত" else " • অনুমোদনের অপেক্ষায়",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null) {
                Button(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        }
    }
}
