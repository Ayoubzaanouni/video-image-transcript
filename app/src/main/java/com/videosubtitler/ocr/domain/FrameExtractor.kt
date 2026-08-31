package com.videosubtitler.ocr.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Samples frames from a video file at a fixed interval using MediaMetadataRetriever.
 * One frame is decoded at a time and must be recycled by the caller once processed,
 * to bound peak memory use on long videos.
 */
class FrameExtractor(context: Context, uri: Uri) : AutoCloseable {

    private val retriever = MediaMetadataRetriever().apply {
        setDataSource(context, uri)
    }

    val durationMs: Long = retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull() ?: 0L

    fun frameAt(timeMs: Long): Bitmap? =
        retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)

    /** Timestamps to sample, from 0 to duration, spaced [intervalMs] apart. */
    fun sampleTimestamps(intervalMs: Long): List<Long> {
        if (durationMs <= 0L) return emptyList()
        val timestamps = mutableListOf<Long>()
        var t = 0L
        while (t < durationMs) {
            timestamps.add(t)
            t += intervalMs
        }
        return timestamps
    }

    override fun close() {
        retriever.release()
    }
}
