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
 * Best-effort resolver for a shared video post link (Instagram, TikTok, X/Twitter,
 * Facebook, Reddit, Vimeo, ...): fetches the public page and reads a direct video
 * URL out of it, then downloads that video locally so the existing frame/OCR
 * pipeline can run on it.
 *
 * This only works for public, non-login-walled content, relies on each site's page
 * markup staying stable, and is not an official API for any of these platforms —
 * it can stop working at any time if a site changes its page or blocks the request.
 * YouTube is intentionally not supported: it does not expose a direct file URL this
 * way, and reliably extracting one requires reverse-engineering YouTube's player
 * internals (what yt-dlp does), which is far more fragile and a clearer Terms of
 * Service violation than reading public page metadata.
 */
class LinkVideoResolver {

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

    fun isYouTubeUrl(url: String): Boolean {
        val host = Uri.parse(url).host.orEmpty()
        return host.contains("youtube.com") || host.contains("youtu.be")
    }

    suspend fun downloadVideo(context: Context, postUrl: String): Uri = withContext(Dispatchers.IO) {
        if (isYouTubeUrl(postUrl)) {
            error("YouTube links aren't supported. Try Instagram, TikTok, X/Twitter, Facebook, Reddit, or Vimeo instead.")
        }
        val host = Uri.parse(postUrl).host.orEmpty()
        val videoUrl = if (host.contains("instagram.com")) {
            fetchInstagramVideoUrl(postUrl)
        } else {
            fetchOgVideoUrl(postUrl)
        } ?: error("Couldn't find a video on that link. The post may be private, expired, deleted, or not a video.")
        downloadToCache(context, videoUrl)
    }

    /**
     * Instagram's normal post page is a client-rendered shell and doesn't include
     * `og:video` in the raw HTML for Reels. Its embed page (meant for embedding
     * posts on third-party sites, so it stays server-rendered) does include the
     * direct video URL, so try that first before falling back to plain og:video.
     */
    private fun fetchInstagramVideoUrl(postUrl: String): String? {
        val embedUrl = toInstagramEmbedUrl(postUrl)
        if (embedUrl != null) {
            fetchHtml(embedUrl)?.let { html ->
                extractFirst(html, Regex("\"video_url\":\"([^\"]+)\""))
                    ?.let { return unescape(it) }
                extractOgVideo(html)?.let { return it }
            }
        }
        return fetchOgVideoUrl(postUrl)
    }

    private fun toInstagramEmbedUrl(postUrl: String): String? {
        val match = Regex("instagram\\.com/(p|reel|tv)/([A-Za-z0-9_-]+)").find(postUrl) ?: return null
        val (type, shortcode) = match.destructured
        return "https://www.instagram.com/$type/$shortcode/embed/"
    }

    private fun fetchOgVideoUrl(pageUrl: String): String? =
        fetchHtml(pageUrl)?.let { extractOgVideo(it) }

    private fun extractOgVideo(html: String): String? {
        val match = extractFirst(html, Regex("property=\"og:video(?::secure_url)?\"\\s+content=\"([^\"]+)\""))
            ?: extractFirst(html, Regex("content=\"([^\"]+)\"\\s+property=\"og:video(?::secure_url)?\""))
        return match?.let { unescape(it) }
    }

    private fun extractFirst(html: String, regex: Regex): String? =
        regex.find(html)?.groupValues?.get(1)

    private fun unescape(url: String): String =
        Html.fromHtml(url, Html.FROM_HTML_MODE_LEGACY).toString().replace("\\/", "/")

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", browserUserAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
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
