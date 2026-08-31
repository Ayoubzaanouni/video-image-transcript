package com.videosubtitler.ocr.domain

import android.content.Context
import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Best-effort resolver for a shared video post link (Instagram, TikTok, X/Twitter,
 * Facebook, Reddit, Vimeo, ...): fetches the post and reads a direct video URL out
 * of it, then downloads that video locally so the existing frame/OCR pipeline can
 * run on it.
 *
 * For most sites this reads the public `og:video` page metadata. Instagram no
 * longer serves any content in its raw HTML (confirmed by direct testing: both the
 * normal post page and the embed page return an empty client-rendered shell, and
 * even the old unauthenticated private-API endpoints now redirect to a login wall)
 * — so Instagram requires an [instagramCookie] from a real logged-in session,
 * captured via [InstagramSessionStore] after explicit user consent, and uses
 * Instagram's own authenticated media-info endpoint instead of scraping HTML.
 *
 * None of this is an official API for any of these platforms. It relies on each
 * site's markup/endpoints staying stable and can stop working at any time. YouTube
 * is intentionally not supported: it exposes no direct file URL this way at all,
 * and reliably extracting one requires reverse-engineering YouTube's player
 * internals (what yt-dlp does), which is far more fragile and invasive than this.
 */
class LinkVideoResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val instagramAppId = "936619743392459" // Instagram's own public web-client app id

    /** Extracts the first http(s) URL found in shared share-sheet text. */
    fun extractUrl(sharedText: String): String? =
        Regex("https?://\\S+").find(sharedText)?.value

    fun isYouTubeUrl(url: String): Boolean {
        val host = Uri.parse(url).host.orEmpty()
        return host.contains("youtube.com") || host.contains("youtu.be")
    }

    fun isInstagramUrl(url: String): Boolean =
        Uri.parse(url).host.orEmpty().contains("instagram.com")

    suspend fun downloadVideo(context: Context, postUrl: String, instagramCookie: String? = null): Uri =
        withContext(Dispatchers.IO) {
            if (isYouTubeUrl(postUrl)) {
                error("YouTube links aren't supported. Try Instagram, TikTok, X/Twitter, Facebook, Reddit, or Vimeo instead.")
            }
            val videoUrl = if (isInstagramUrl(postUrl)) {
                fetchInstagramVideoUrl(postUrl, instagramCookie)
            } else {
                fetchOgVideoUrl(postUrl, null)
            } ?: error("Couldn't find a video on that link. The post may be private, expired, deleted, or not a video.")
            downloadToCache(context, videoUrl)
        }

    private fun fetchInstagramVideoUrl(postUrl: String, cookie: String?): String? {
        val shortcode = extractInstagramShortcode(postUrl)

        if (cookie != null && shortcode != null) {
            fetchInstagramApiVideoUrl(shortcode, cookie)?.let { return it }
        }

        // Fallbacks below rarely succeed without a session (Instagram serves an
        // empty client-rendered shell to unauthenticated requests) but are cheap
        // to try in case that ever changes for a given post.
        val embedUrl = toInstagramEmbedUrl(postUrl)
        if (embedUrl != null) {
            fetchHtml(embedUrl, cookie)?.let { html ->
                extractFirst(html, Regex("\"video_url\":\"([^\"]+)\""))?.let { return unescape(it) }
                extractOgVideo(html)?.let { return it }
            }
        }
        return fetchOgVideoUrl(postUrl, cookie)
    }

    /** Instagram's authenticated private media-info endpoint; requires a logged-in session cookie. */
    private fun fetchInstagramApiVideoUrl(shortcode: String, cookie: String): String? {
        val request = Request.Builder()
            .url("https://www.instagram.com/api/v1/media/$shortcode/info/")
            .header("User-Agent", browserUserAgent)
            .header("x-ig-app-id", instagramAppId)
            .header("Cookie", cookie)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return runCatching {
                val item = JSONObject(body).getJSONArray("items").getJSONObject(0)
                val versions = item.getJSONArray("video_versions")
                versions.getJSONObject(0).getString("url")
            }.getOrNull()
        }
    }

    private fun extractInstagramShortcode(url: String): String? =
        Regex("instagram\\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

    private fun toInstagramEmbedUrl(postUrl: String): String? {
        val match = Regex("instagram\\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)").find(postUrl) ?: return null
        val (type, shortcode) = match.destructured
        val embedType = if (type == "reels") "reel" else type
        return "https://www.instagram.com/$embedType/$shortcode/embed/"
    }

    private fun fetchOgVideoUrl(pageUrl: String, cookie: String?): String? =
        fetchHtml(pageUrl, cookie)?.let { extractOgVideo(it) }

    private fun extractOgVideo(html: String): String? {
        val match = extractFirst(html, Regex("property=\"og:video(?::secure_url)?\"\\s+content=\"([^\"]+)\""))
            ?: extractFirst(html, Regex("content=\"([^\"]+)\"\\s+property=\"og:video(?::secure_url)?\""))
        return match?.let { unescape(it) }
    }

    private fun extractFirst(html: String, regex: Regex): String? =
        regex.find(html)?.groupValues?.get(1)

    private fun unescape(url: String): String =
        Html.fromHtml(url, Html.FROM_HTML_MODE_LEGACY).toString().replace("\\/", "/")

    private fun fetchHtml(url: String, cookie: String?): String? {
        val builder = Request.Builder().url(url).header("User-Agent", browserUserAgent)
        if (cookie != null) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun downloadToCache(context: Context, videoUrl: String): Uri {
        val request = Request.Builder()
            .url(videoUrl)
            .header("User-Agent", browserUserAgent)
            .header("Referer", "https://www.instagram.com/")
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
