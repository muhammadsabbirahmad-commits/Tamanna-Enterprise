package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.tamanna.enterprise.sync.CloudSyncManager
import com.tamanna.enterprise.notifications.NotificationScheduler

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurityStorage.ensureInitialized(this)
        NotificationScheduler.scheduleDaily(this)
        if (FirebaseAuth.getInstance().currentUser != null) {
            CloudSyncManager.start(this)
        }

        val destination = if (SecurityStorage.isLoginEnabled(this) && !SecurityStorage.isLoggedIn(this)) {
            LoginActivity::class.java
        } else {
            DashboardActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }
}
