package com.videosubtitler.ocr.domain

import android.content.Context
import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Best-effort resolver for a shared Instagram post/reel link: fetches the public
 * post page and reads the direct video URL out of its `og:video` meta tag, then
 * downloads that video locally so the existing frame/OCR pipeline can run on it.
 *
 * Only works for public posts (not private accounts, not Stories), relies on
 * Instagram's page markup staying stable, and is not an official API — it can
 * stop working at any time if Instagram changes their page or blocks the request.
 */
class InstagramVideoResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Extracts the first http(s) URL found in shared share-sheet text. */
    fun extractUrl(sharedText: String): String? =
        Regex("https?://\\S+").find(sharedText)?.value

    fun isInstagramUrl(url: String): Boolean =
        Uri.parse(url).host?.contains("instagram.com") == true

    suspend fun downloadVideo(context: Context, postUrl: String): Uri = withContext(Dispatchers.IO) {
        val videoUrl = fetchDirectVideoUrl(postUrl)
            ?: error("Couldn't find a video on that link. The post may be private, expired, or not a video.")
        downloadToCache(context, videoUrl)
    }

    private fun fetchDirectVideoUrl(postUrl: String): String? {
        val request = Request.Builder()
            .url(postUrl)
            .header("User-Agent", browserUserAgent)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            val match = Regex("property=\"og:video(?::secure_url)?\"\\s+content=\"([^\"]+)\"")
                .find(html)
                ?: Regex("content=\"([^\"]+)\"\\s+property=\"og:video(?::secure_url)?\"").find(html)
            return match?.groupValues?.get(1)?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
        }
    }

    private fun downloadToCache(context: Context, videoUrl: String): Uri {
        val request = Request.Builder()
            .url(videoUrl)
            .header("User-Agent", browserUserAgent)
            .build()

        val file = File(context.cacheDir, "shared_video_${System.currentTimeMillis()}.mp4")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Failed to download the video (HTTP ${response.code}).")
            val body = response.body ?: error("Empty video response.")
            file.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
        return Uri.fromFile(file)
    }
}
