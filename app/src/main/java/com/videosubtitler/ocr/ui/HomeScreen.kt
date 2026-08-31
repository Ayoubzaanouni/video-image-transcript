package com.videosubtitler.ocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onPickVideo: () -> Unit,
    onSubmitLink: (String) -> Unit,
    hasInstagramSession: Boolean = false,
    onLogOutOfInstagram: () -> Unit = {},
    errorMessage: String? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    var linkText by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Extract subtitles from a video's on-screen (burned-in) text — no audio involved.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Button(onClick = onPickVideo, modifier = Modifier.padding(top = 24.dp)) {
                Text("Pick a video from this device")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(
                text = "Or paste a link (Instagram, TikTok, X/Twitter, Facebook, Reddit, Vimeo — public posts only)",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = linkText,
                onValueChange = { linkText = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboardManager.getText()?.text?.let { linkText = it }
                }) {
                    Text("Paste")
                }
                Button(
                    onClick = { onSubmitLink(linkText) },
                    enabled = linkText.isNotBlank(),
                ) {
                    Text("Fetch video")
                }
            }

            if (hasInstagramSession) {
                OutlinedButton(onClick = onLogOutOfInstagram, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Log out of Instagram")
                }
            }
        }
    }
}
