package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.CloudAccessManager
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.sync.CloudSyncManager
import com.tamanna.enterprise.notifications.NotificationScheduler

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurityStorage.ensureInitialized(this)
        NotificationScheduler.scheduleDaily(this)

        if (!SecurityStorage.isLoginEnabled(this)) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val localUser = SecurityStorage.getCurrentUser(this)

        if (firebaseUser == null || localUser == null) {
            SecurityStorage.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        CloudAccessManager.validateCurrentSession(this) { valid ->
            if (valid) {
                startActivity(Intent(this, DashboardActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }
    }
}
