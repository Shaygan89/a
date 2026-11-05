package com.example.mylauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val CHATGPT_URL = "https://chatgpt.com/"

private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@Composable
fun ChatGPTWebView(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { factoryContext ->
                WebView(factoryContext).apply {
                    // Default native WebView background - no custom colors or transparency forced

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        loadsImagesAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true

                        userAgentString = MOBILE_CHROME_UA
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            loadFailed = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            loadFailed = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                loadFailed = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return false
                            val host = uri.host?.lowercase() ?: ""

                            if (host.contains("chatgpt.com") || host.contains("openai.com") || host.contains("auth0.com")) {
                                return false
                            }

                            return try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                true
                            } catch (_: ActivityNotFoundException) {
                                false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                            message?.let {
                                Log.d("MyLauncherChatGPT", "${it.message()} @ ${it.sourceId()}:${it.lineNumber()}")
                            }
                            return true
                        }
                    }

                    webViewInstance = this

                    // Remove 'X-Requested-With' header to prevent Cloudflare from blocking embedded WebView
                    val customHeaders = mapOf("X-Requested-With" to "")
                    loadUrl(CHATGPT_URL, customHeaders)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isLoading && !loadFailed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (loadFailed) {
            ChatGPTLoadError(
                context = context,
                onReload = {
                    loadFailed = false
                    isLoading = true
                    val customHeaders = mapOf("X-Requested-With" to "")
                    webViewInstance?.loadUrl(CHATGPT_URL, customHeaders)
                },
            )
        }
    }
}

@Composable
private fun ChatGPTLoadError(
    context: Context,
    onReload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "CHATGPT COULDN'T LOAD",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Check your connection, then try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                TextButton(onClick = onReload) {
                    Text("RELOAD")
                }
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_URL))
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                    }
                ) {
                    Text("OPEN BROWSER")
                }
            }
        }
    }
}