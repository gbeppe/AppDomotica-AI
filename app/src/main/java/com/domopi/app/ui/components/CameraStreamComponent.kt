package com.domopi.app.ui.components

import android.util.Base64
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraStreamComponent(
    url: String,
    user: String? = null,
    pass: String? = null,
    modifier: Modifier = Modifier
) {
    // Stato per tracciare se abbiamo già avviato il caricamento di questo URL
    val currentUrl = remember { mutableStateOf("") }
    // Stato per il caricamento
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedHttpAuthRequest(
                            view: WebView?,
                            handler: HttpAuthHandler?,
                            host: String?,
                            realm: String?
                        ) {
                            if (user != null && pass != null) {
                                handler?.proceed(user, pass)
                            } else {
                                super.onReceivedHttpAuthRequest(view, handler, host, realm)
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            handler?.proceed()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                if (currentUrl.value != url) {
                    currentUrl.value = url
                    isLoading = true
                    if (user != null && pass != null) {
                        val auth = "$user:$pass"
                        val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                        val headers = HashMap<String, String>()
                        headers["Authorization"] = "Basic $encodedAuth"
                        webView.loadUrl(url, headers)
                    } else {
                        webView.loadUrl(url)
                    }
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
