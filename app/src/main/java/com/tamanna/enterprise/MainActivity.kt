package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurityStorage.ensureInitialized(this)
        val destination = if (SecurityStorage.isLoginEnabled(this) && !SecurityStorage.isLoggedIn(this)) {
            LoginActivity::class.java
        } else {
            DashboardActivity::class.java
        }

        startActivity(Intent(this, destination))

        finish()
    }
}
