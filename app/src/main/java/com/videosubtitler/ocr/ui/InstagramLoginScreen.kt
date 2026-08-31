package com.videosubtitler.ocr.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val INSTAGRAM_LOGIN_URL = "https://www.instagram.com/accounts/login/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(onLoggedIn: (String) -> Unit, onCancel: () -> Unit) {
    val currentOnLoggedIn = rememberUpdatedState(onLoggedIn)

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
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            val cookie = CookieManager.getInstance().getCookie("https://www.instagram.com")
                            if (cookie != null && cookie.contains("sessionid=")) {
                                currentOnLoggedIn.value(cookie)
                            }
                        }
                    }
                    loadUrl(INSTAGRAM_LOGIN_URL)
                }
            },
        )
    }
}
