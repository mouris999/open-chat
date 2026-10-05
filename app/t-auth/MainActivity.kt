package com.yourapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tauth.TAuthActivity
import com.tauth.TAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_T_AUTH = 1001
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── Initialize T-Auth SDK once ────────────────────────────────────────
        TAuthClient.getInstance(this).init(
            serverUrl   = "https://t-auth.com",       // your server URL
            clientId    = "tauth_test_client",         // from your T-Auth dashboard
            redirectUri = "tauth://callback"           // must match AndroidManifest intent-filter
        )

        val tAuth = TAuthClient.getInstance(this)

        // ── If already logged in, show user info ──────────────────────────────
        if (tAuth.isLoggedIn()) {
            val user = tAuth.getCachedUser()
            showLoggedIn("Welcome back, ${user?.name ?: user?.email}!")
        }

        // ── Login button ──────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnLoginWithTAuth).setOnClickListener {
            TAuthActivity.launch(this, REQUEST_T_AUTH)
        }

        // ── Logout button ─────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            tAuth.logout()
            showLoggedOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        }

        // ── Refresh token example ─────────────────────────────────────────────
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            scope.launch {
                val ok = tAuth.refreshToken()
                Toast.makeText(this@MainActivity, if (ok) "Token refreshed!" else "Refresh failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Handle the OAuth result ───────────────────────────────────────────────
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_T_AUTH) {
            if (resultCode == Activity.RESULT_OK) {
                val userBundle = data?.getBundleExtra(TAuthActivity.EXTRA_USER)
                val name  = userBundle?.getString("name")
                val email = userBundle?.getString("email")
                showLoggedIn("Welcome, ${name ?: email}!")
                Toast.makeText(this, "Logged in as $email", Toast.LENGTH_LONG).show()
            } else {
                val error = data?.getStringExtra(TAuthActivity.EXTRA_ERROR)
                Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoggedIn(message: String) {
        findViewById<TextView>(R.id.tvStatus).text = message
        findViewById<Button>(R.id.btnLoginWithTAuth).isEnabled = false
        findViewById<Button>(R.id.btnLogout).isEnabled = true
    }

    private fun showLoggedOut() {
        findViewById<TextView>(R.id.tvStatus).text = "Not logged in"
        findViewById<Button>(R.id.btnLoginWithTAuth).isEnabled = true
        findViewById<Button>(R.id.btnLogout).isEnabled = false
    }
}
