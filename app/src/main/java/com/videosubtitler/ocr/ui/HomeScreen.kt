package com.videosubtitler.ocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onPickVideo: () -> Unit, errorMessage: String? = null) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Extract subtitles from a video's on-screen (burned-in) text — no audio involved.\n\n" +
                    "Pick a video already on your phone, or share one into this app from another app (e.g. after saving it from Instagram/Facebook).",
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
        }
    }
}
