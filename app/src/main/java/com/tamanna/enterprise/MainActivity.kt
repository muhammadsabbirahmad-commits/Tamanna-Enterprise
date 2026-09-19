package com.tamanna.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tamanna.enterprise.dashboard.DashboardActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, DashboardActivity::class.java)
        )

        finish()
    }
}
