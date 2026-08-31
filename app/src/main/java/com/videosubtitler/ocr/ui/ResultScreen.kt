package com.videosubtitler.ocr.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videosubtitler.ocr.domain.SrtWriter
import com.videosubtitler.ocr.model.SubtitleCue
import java.io.File
import java.io.FileOutputStream

@Composable
fun ResultScreen(videoUri: Uri, cues: List<SubtitleCue>, onStartOver: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip"),
    ) { destinationUri ->
        if (destinationUri != null) {
            writeSrtTo(context, destinationUri, cues)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VideoPlayerWithSubtitles(
                videoUri = videoUri,
                cues = cues,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("subtitles.srt") }) {
                    Text("Export .srt")
                }
                OutlinedButton(onClick = {
                    clipboardManager.setText(AnnotatedString(SrtWriter.toPlainTranscript(cues)))
                }) {
                    Text("Copy transcript")
                }
                OutlinedButton(onClick = onStartOver) {
                    Text("New video")
                }
            }

            if (cues.isEmpty()) {
                Text(
                    text = "No on-screen text was recognized in this video.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(cues) { cue ->
                        Text(
                            text = "${formatShort(cue.startMs)} — ${formatShort(cue.endMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(text = cue.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerWithSubtitles(videoUri: Uri, cues: List<SubtitleCue>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUri)
            if (cues.isNotEmpty()) {
                val srtFile = writeSrtToCache(context, cues)
                val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srtFile))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("en")
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subtitle))
            }
            setMediaItem(mediaItemBuilder.build())
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply { player = exoPlayer }
        },
        modifier = modifier,
    )
}

private fun writeSrtToCache(context: Context, cues: List<SubtitleCue>): File {
    val file = File(context.cacheDir, "subtitles.srt")
    FileOutputStream(file).use { it.write(SrtWriter.toSrt(cues).toByteArray()) }
    return file
}

private fun writeSrtTo(context: Context, uri: Uri, cues: List<SubtitleCue>) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(SrtWriter.toSrt(cues).toByteArray())
    }
}

private fun formatShort(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
