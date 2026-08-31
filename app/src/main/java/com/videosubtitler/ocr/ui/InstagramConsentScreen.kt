package com.videosubtitler.ocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InstagramConsentScreen(onAccept: () -> Unit, onCancel: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Instagram log-in required",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Instagram no longer allows fetching video links without being logged in. " +
                    "If you continue, you'll log into Instagram in a normal login screen inside this app, " +
                    "and the app will store your session on this device (encrypted) to fetch videos you link.\n\n" +
                    "This goes against Instagram's Terms of Service and its automated-access detection could " +
                    "flag or restrict your account. Only continue if you accept that risk on your own account.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(onClick = onAccept, modifier = Modifier.padding(top = 24.dp)) {
                Text("I understand, log in to Instagram")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
    }
}
