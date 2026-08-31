package com.videosubtitler.ocr.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videosubtitler.ocr.domain.CueBuilder
import com.videosubtitler.ocr.domain.FrameExtractor
import com.videosubtitler.ocr.domain.InstagramSessionStore
import com.videosubtitler.ocr.domain.LinkVideoResolver
import com.videosubtitler.ocr.domain.OcrEngine
import com.videosubtitler.ocr.domain.SrtWriter
import com.videosubtitler.ocr.domain.TimedText
import com.videosubtitler.ocr.model.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FRAME_INTERVAL_MS = 500L

sealed interface UiState {
    data object Idle : UiState
    data object InstagramConsentRequired : UiState
    data object InstagramLoggingIn : UiState
    data class Fetching(val message: String) : UiState
    data class Processing(val processed: Int, val total: Int) : UiState
    data class Result(val videoUri: Uri, val cues: List<SubtitleCue>) : UiState
    data class Error(val message: String) : UiState
}

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _hasInstagramSession = MutableStateFlow(false)
    val hasInstagramSession: StateFlow<Boolean> = _hasInstagramSession.asStateFlow()

    private val linkResolver = LinkVideoResolver()
    private val instagramSessionStore = InstagramSessionStore(application)
    private var pendingLink: String? = null

    init {
        _hasInstagramSession.value = instagramSessionStore.hasSession()
    }

    fun processVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = UiState.Processing(0, 0)
            try {
                val cues = withContext(Dispatchers.Default) { extractCues(uri) }
                _uiState.value = UiState.Result(uri, cues)
            } catch (t: Throwable) {
                _uiState.value = UiState.Error(t.message ?: "Failed to process video")
            }
        }
    }

    /** Handles a shared piece of text (e.g. a post/reel link from the share sheet or pasted in). */
    fun processSharedText(sharedText: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Fetching("Looking for a video link…")
            val url = linkResolver.extractUrl(sharedText)
            if (url == null) {
                _uiState.value = UiState.Error("No link found in what was shared.")
                return@launch
            }
            if (linkResolver.isInstagramUrl(url) && !instagramSessionStore.hasSession()) {
                pendingLink = url
                _uiState.value = UiState.InstagramConsentRequired
                return@launch
            }
            fetchAndProcess(url)
        }
    }

    fun onInstagramConsentAccepted() {
        _uiState.value = UiState.InstagramLoggingIn
    }

    fun onInstagramConsentDeclined() {
        pendingLink = null
        _uiState.value = UiState.Idle
    }

    fun onInstagramLoggedIn(cookie: String) {
        instagramSessionStore.saveCookie(cookie)
        _hasInstagramSession.value = true
        val url = pendingLink
        pendingLink = null
        if (url != null) {
            viewModelScope.launch { fetchAndProcess(url) }
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun onInstagramLoginCancelled() {
        pendingLink = null
        _uiState.value = UiState.Idle
    }

    fun logOutOfInstagram() {
        instagramSessionStore.clear()
        _hasInstagramSession.value = false
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    private suspend fun fetchAndProcess(url: String) {
        try {
            _uiState.value = UiState.Fetching("Downloading video from the link…")
            val context = getApplication<Application>()
            val cookie = instagramSessionStore.getCookie()
            val videoUri = withContext(Dispatchers.IO) { linkResolver.downloadVideo(context, url, cookie) }
            processVideo(videoUri)
        } catch (t: Throwable) {
            _uiState.value = UiState.Error(t.message ?: "Couldn't fetch that video.")
        }
    }

    private suspend fun extractCues(uri: Uri): List<SubtitleCue> {
        val context = getApplication<Application>()
        val ocrEngine = OcrEngine()
        FrameExtractor(context, uri).use { extractor ->
            val timestamps = extractor.sampleTimestamps(FRAME_INTERVAL_MS)
            if (timestamps.isEmpty()) return emptyList()

            val readings = mutableListOf<TimedText>()
            for ((index, timeMs) in timestamps.withIndex()) {
                _uiState.value = UiState.Processing(index, timestamps.size)
                val bitmap = extractor.frameAt(timeMs) ?: continue
                val text = try {
                    ocrEngine.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
                readings.add(TimedText(timeMs, text))
            }
            ocrEngine.close()
            return CueBuilder.build(readings, FRAME_INTERVAL_MS, extractor.durationMs)
        }
    }

    companion object {
        fun srt(cues: List<SubtitleCue>): String = SrtWriter.toSrt(cues)
        fun transcript(cues: List<SubtitleCue>): String = SrtWriter.toPlainTranscript(cues)
    }
}
