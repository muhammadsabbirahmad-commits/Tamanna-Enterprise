package com.tamanna.enterprise

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.dashboard.DashboardActivity
import com.tamanna.enterprise.security.AccessRequestManager
import com.tamanna.enterprise.security.LoginActivity
import com.tamanna.enterprise.security.SecurityStorage
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private fun hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStorage.ensureInitialized(this)

        val localUser = SecurityStorage.getCurrentUser(this)
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val matchingOnlineSession = localUser != null &&
            firebaseUser != null &&
            localUser.id == firebaseUser.uid

        // If Firebase cannot restore its token while offline, allow only the
        // previously approved Gmail whose UID owns this exact local business cache.
        val cachedOfflineSession = localUser != null &&
            firebaseUser == null &&
            !hasInternet() &&
            localUser.role == SecurityStorage.ROLE_ADMIN &&
            AccessRequestManager.hasCachedApproval(localUser.id, localUser.googleEmail) &&
            BusinessAccountStorage.get(this).ownerUid == localUser.id

        if (matchingOnlineSession || cachedOfflineSession) {
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            // Never erase a saved Gmail identity because of a temporary network failure.
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
