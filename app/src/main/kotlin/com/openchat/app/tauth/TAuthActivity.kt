package com.openchat.app.tauth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * T-Auth Activity - OAuth WebView for T-Auth authentication
 * 
 * This Activity hosts a WebView that loads the T-Auth authorization page.
 * It handles the OAuth flow from authorization to token exchange.
 * 
 * Usage:
 * ```
 * // Launch from your Activity
 * TAuthActivity.launch(this, REQUEST_CODE)
 * 
 * // Handle result
 * override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
 *     if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
 *         val user = data?.getBundleExtra(TAuthActivity.EXTRA_USER)
 *         // User is logged in!
 *     }
 * }
 * ```
 */
class TAuthActivity : ComponentActivity() {

    companion object {
        const val REQUEST_CODE = 1001
        const val EXTRA_USER = "tauth_user"
        const val EXTRA_ACCESS_TOKEN = "tauth_access_token"
        const val EXTRA_REFRESH_TOKEN = "tauth_refresh_token"
        
        private const val EXTRA_TITLE = "tauth_title"
        
        /**
         * Launch T-Auth login flow.
         * 
         * @param activity The calling activity
         * @param requestCode Request code for onActivityResult
         * @param title Optional custom title for the screen
         */
        fun launch(activity: Activity, requestCode: Int = REQUEST_CODE, title: String = "Sign In") {
            val intent = Intent(activity, TAuthActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
            }
            activity.startActivityForResult(intent, requestCode)
        }
        
        /**
         * Launch T-Auth login flow from a Context.
         * Note: This method doesn't provide result callback, use launch(Activity, Int) for that.
         */
        fun launch(context: Context) {
            val intent = Intent(context, TAuthActivity::class.java)
            context.startActivity(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Sign In"
        
        setContent {
            TAuthScreen(
                title = title,
                onBackPressed = { finishWithCancel() },
                onAuthSuccess = { accessToken, refreshToken, user ->
                    finishWithSuccess(accessToken, refreshToken, user)
                },
                onAuthError = { error ->
                    finishWithError(error)
                }
            )
        }
    }

    private fun finishWithSuccess(accessToken: String, refreshToken: String, user: UserInfo) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(EXTRA_REFRESH_TOKEN, refreshToken)
            putExtra(EXTRA_USER, Bundle().apply {
                putString("id", user.id)
                putString("email", user.email)
                putString("name", user.name)
                putString("picture", user.picture)
            })
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun finishWithCancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithError(error: String) {
        val resultIntent = Intent().apply {
            putExtra("error", error)
        }
        setResult(RESULT_CANCELED, resultIntent)
        finish()
    }

    /**
     * Handle OAuth callback URL.
     * Called from WebView when redirect is detected.
     */
    fun handleAuthCallback(
        url: String,
        onAuthSuccess: (String, String, UserInfo) -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        
        when {
            error != null -> {
                onAuthError("Authorization error: $error")
            }
            code != null -> {
                // Exchange code for tokens
                lifecycleScope.launch {
                    try {
                        TAuthClient.getInstance(this@TAuthActivity).exchangeCode(code)
                            .onSuccess { tokenResponse ->
                                // Get user info
                                TAuthClient.getInstance(this@TAuthActivity).getUserInfo()
                                    .onSuccess { userInfo ->
                                        onAuthSuccess(
                                            tokenResponse.accessToken,
                                            tokenResponse.refreshToken,
                                            userInfo
                                        )
                                    }
                                    .onFailure { err ->
                                        onAuthError("Failed to get user info: ${err.message}")
                                    }
                            }
                            .onFailure { err ->
                                onAuthError("Token exchange failed: ${err.message}")
                            }
                    } catch (e: Exception) {
                        onAuthError("Error: ${e.message}")
                    }
                }
            }
            else -> {
                onAuthError("Invalid callback URL")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TAuthScreen(
    title: String,
    onBackPressed: () -> Unit,
    onAuthSuccess: (String, String, UserInfo) -> Unit,
    onAuthError: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TAuthWebView(
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TAuthWebView(
    onAuthSuccess: (String, String, UserInfo) -> Unit,
    onAuthError: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Configure WebView settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "TAuth-Android-SDK/1.0"
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                }
                
                // Clear previous session
                CookieManager.getInstance().removeAllCookies(null)
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        // Check if this is the redirect callback
                        if (url.startsWith("tauth://callback")) {
                            // Handle callback in activity scope
                            (ctx as? TAuthActivity)?.handleAuthCallback(url, onAuthSuccess, onAuthError)
                            return true
                        }
                        
                        return false
                    }
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }
                    
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        if (errorCode != ERROR_HOST_LOOKUP && errorCode != ERROR_CONNECT) {
                            onAuthError(description ?: "WebView error: $errorCode")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        handler?.cancel()
                        onAuthError("SSL error: ${error?.getPrimaryError()}")
                    }
                }
                
                // Load the authorization URL
                try {
                    val authUrl = TAuthClient.getInstance(context).buildAuthUrl()
                    loadUrl(authUrl)
                } catch (e: Exception) {
                    onAuthError("Failed to initialize T-Auth: ${e.message}")
                }
            }
        },
        update = { /* No updates needed */ }
    )
}
