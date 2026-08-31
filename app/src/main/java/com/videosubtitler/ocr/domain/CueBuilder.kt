package com.videosubtitler.ocr.domain

import com.videosubtitler.ocr.model.SubtitleCue

/** One OCR reading at a given timestamp; [text] is empty when nothing was recognized. */
data class TimedText(val timeMs: Long, val text: String)

/**
 * Merges consecutive same-text frame readings into single timed subtitle cues,
 * since a burned-in caption is typically re-recognized on every sampled frame
 * while it stays on screen.
 */
object CueBuilder {

    fun build(readings: List<TimedText>, frameIntervalMs: Long, videoDurationMs: Long): List<SubtitleCue> {
        if (readings.isEmpty()) return emptyList()

        val cues = mutableListOf<SubtitleCue>()
        var groupStartMs: Long? = null
        var groupText: String? = null
        var groupNormalized: String = ""

        fun closeGroup(endMs: Long) {
            val start = groupStartMs
            val text = groupText
            if (start != null && !text.isNullOrBlank() && endMs > start) {
                cues.add(SubtitleCue(startMs = start, endMs = endMs, text = text))
            }
            groupStartMs = null
            groupText = null
            groupNormalized = ""
        }

        for (reading in readings) {
            val normalized = normalize(reading.text)
            if (groupStartMs != null && normalized == groupNormalized) {
                continue // same caption still on screen, keep extending the open group
            }
            closeGroup(reading.timeMs)
            if (normalized.isNotEmpty()) {
                groupStartMs = reading.timeMs
                groupText = reading.text
                groupNormalized = normalized
            }
        }
        val naiveEndMs = readings.last().timeMs + frameIntervalMs
        val lastEndMs = if (videoDurationMs > 0) {
            naiveEndMs.coerceAtMost(videoDurationMs).coerceAtLeast(readings.last().timeMs + 1)
        } else {
            naiveEndMs
        }
        closeGroup(lastEndMs)

        return cues
    }

    private fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").lowercase()
}
