package com.videosubtitler.ocr.domain

import com.videosubtitler.ocr.model.SubtitleCue
import java.util.Locale

object SrtWriter {

    fun toSrt(cues: List<SubtitleCue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(formatTimestamp(cue.startMs)).append(" --> ").append(formatTimestamp(cue.endMs)).append('\n')
            append(cue.text).append('\n')
            append('\n')
        }
    }

    fun toPlainTranscript(cues: List<SubtitleCue>): String =
        cues.joinToString(separator = "\n") { it.text }

    private fun formatTimestamp(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
