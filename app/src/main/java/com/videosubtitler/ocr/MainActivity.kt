package com.videosubtitler.ocr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videosubtitler.ocr.ui.HomeScreen
import com.videosubtitler.ocr.ui.ProcessingScreen
import com.videosubtitler.ocr.ui.ResultScreen
import com.videosubtitler.ocr.viewmodel.TranscriptionViewModel
import com.videosubtitler.ocr.viewmodel.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: TranscriptionViewModel by viewModels()

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.processVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val current = state) {
                is UiState.Idle -> HomeScreen(onPickVideo = { pickVideoLauncher.launch(arrayOf("video/*")) })
                is UiState.Processing -> ProcessingScreen(processed = current.processed, total = current.total)
                is UiState.Result -> ResultScreen(
                    videoUri = current.videoUri,
                    cues = current.cues,
                    onStartOver = { viewModel.reset() },
                )
                is UiState.Error -> HomeScreen(
                    onPickVideo = { pickVideoLauncher.launch(arrayOf("video/*")) },
                    errorMessage = current.message,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("video/") == true) {
            val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (sharedUri != null) {
                viewModel.processVideo(sharedUri)
            }
        }
    }
}
