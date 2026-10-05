package com.tauth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TAuthActivity — Opens T-Auth login in a WebView
 *
 * Launch it like this from any Activity:
 *
 *   TAuthActivity.launch(this, REQUEST_CODE)
 *
 * Then handle the result in onActivityResult:
 *
 *   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
 *     if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
 *       val user = data?.getParcelableExtra<TAuthUser>(TAuthActivity.EXTRA_USER)
 *     }
 *   }
 */
class TAuthActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER         = "tauth_user"
        const val EXTRA_ERROR        = "tauth_error"
        private const val KEY_URL    = "auth_url"

        fun launch(activity: Activity, requestCode: Int) {
            val tAuth = TAuthClient.getInstance(activity)
            val url   = tAuth.buildAuthUrl()
            val intent = Intent(activity, TAuthActivity::class.java).apply {
                putExtra(KEY_URL, url)
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }

    private lateinit var webView: WebView
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authUrl = intent.getStringExtra(KEY_URL) ?: run {
            finishWithError("No auth URL provided")
            return
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    // Intercept the redirect back to our app (e.g. tauth://callback?code=xxx)
                    if (uri.toString().startsWith(TAuthClient.getInstance(this@TAuthActivity).redirectUri)) {
                        handleCallback(uri)
                        return true
                    }
                    return false
                }
            }

            loadUrl(authUrl)
        }

        setContentView(webView)
        title = "Sign in with T-Auth"
    }

    private fun handleCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            finishWithError(uri.getQueryParameter("error_description") ?: error)
            return
        }

        val code = uri.getQueryParameter("code")
        if (code == null) {
            finishWithError("No code returned from T-Auth")
            return
        }

        // Exchange code for tokens in background
        scope.launch {
            val tAuth  = TAuthClient.getInstance(this@TAuthActivity)
            val result = tAuth.exchangeCode(code)

            when (result) {
                is TAuthResult.Success -> {
                    val intent = Intent().apply {
                        putExtra(EXTRA_USER, result.user?.let {
                            Bundle().apply {
                                putString("id",      it.id)
                                putString("email",   it.email)
                                putString("name",    it.name)
                                putString("picture", it.picture)
                            }
                        })
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
                is TAuthResult.Error -> finishWithError(result.message)
            }
        }
    }

    private fun finishWithError(message: String) {
        val intent = Intent().putExtra(EXTRA_ERROR, message)
        setResult(Activity.RESULT_CANCELED, intent)
        finish()
    }
}
