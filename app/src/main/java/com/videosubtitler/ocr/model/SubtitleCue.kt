package com.videosubtitler.ocr.model

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
