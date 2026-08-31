package com.videosubtitler.ocr.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val INSTAGRAM_LOGIN_URL = "https://www.instagram.com/accounts/login/"

// Instagram's modern React app fingerprints the browser and refuses to render
// (blank splash screen) inside a WebView even with a normal Chrome UA. Presenting
// as a very old/basic browser makes Instagram fall back to serving its legacy,
// simple, server-rendered login form instead of the JS app — this trick is used
// by several existing open-source downloader apps (see VidSnap's LoginFragment).
private const val LEGACY_BROWSER_UA =
    "Mozilla/5.0 (Linux; U; Android 2.2; en-gb; Build/FRF50) AppleWebKit/533.1 " +
        "(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(onLoggedIn: (String) -> Unit, onCancel: () -> Unit) {
    val currentOnLoggedIn = rememberUpdatedState(onLoggedIn)
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var pageFinishedTick by remember { mutableStateOf(0) }
    var loggedIn by remember { mutableStateOf(false) }

    // Cookies (in particular the session cookie) can be set slightly after the
    // page itself finishes loading, so poll for a few seconds instead of a
    // one-shot check right on page-finish.
    LaunchedEffect(pageFinishedTick) {
        if (pageFinishedTick == 0 || loggedIn) return@LaunchedEffect
        repeat(6) {
            val cookie = CookieManager.getInstance().getCookie("https://www.instagram.com")
            if (cookie != null && cookie.contains("sessionid=")) {
                loggedIn = true
                currentOnLoggedIn.value(cookie)
                return@LaunchedEffect
            }
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Log in to Instagram",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        removeAllCookies(null)
                    }
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = LEGACY_BROWSER_UA
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                loadError = null
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                pageFinishedTick++
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request.isForMainFrame) {
                                    isLoading = false
                                    loadError = "Couldn't load Instagram's login page (${error.description})."
                                }
                            }
                        }
                        loadUrl(INSTAGRAM_LOGIN_URL, mapOf("X-Requested-With" to "com.android.chrome"))
                    }
                },
                update = { webView ->
                    if (reloadTrigger > 0) {
                        webView.loadUrl(INSTAGRAM_LOGIN_URL, mapOf("X-Requested-With" to "com.android.chrome"))
                    }
                },
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            loadError?.let { message ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = message, style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = {
                            loadError = null
                            reloadTrigger++
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
