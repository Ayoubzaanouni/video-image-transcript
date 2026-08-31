/*
 * Instagram post-page JSON extraction, adapted from VidSnap
 * (https://github.com/mugames/VidSnap), file:
 * app/src/main/java/com/mugames/vidsnap/extractor/Instagram.java
 * Copyright (C) the VidSnap authors.
 *
 * This file is part of Video Subtitler.
 *
 * Video Subtitler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Video Subtitler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.videosubtitler.ocr.extractor

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pulls a direct video URL out of an Instagram post/reel page's embedded JSON,
 * trying the two JSON shapes Instagram has used for this over time (same order
 * VidSnap's Instagram.java tries them in):
 *  1. `window._sharedData = {...}` — older GraphQL `shortcode_media` shape.
 *  2. `window.__additionalDataLoaded(..., {...})` — either the same GraphQL shape,
 *     or the newer `items[]` shape (with per-quality `video_versions`).
 */
object InstagramExtractor {

    fun extractVideoUrl(page: String): String? =
        extractFromSharedData(page) ?: extractFromAdditionalDataLoaded(page)

    private fun extractFromSharedData(page: String): String? {
        val json = findJson(page, Regex("window\\._sharedData\\s*=\\s*(\\{.+?\\});")) ?: return null
        return runCatching {
            val postPage = json.optJSONObject("entry_data")?.optJSONArray("PostPage") ?: return@runCatching null
            val zero = postPage.optJSONObject(0) ?: return@runCatching null
            val graphql = zero.optJSONObject("graphql")
            val media = graphql?.optJSONObject("shortcode_media") ?: zero.optJSONObject("media")
            media?.let(::videoUrlFromMedia)
        }.getOrNull()
    }

    private fun extractFromAdditionalDataLoaded(page: String): String? {
        val json = findJson(page, Regex("window\\.__additionalDataLoaded\\s*\\(\\s*[^,]+,\\s*(\\{.+?\\})\\s*\\)\\s*;")) ?: return null
        return runCatching {
            val graphql = json.optJSONObject("graphql")
            if (graphql != null) {
                graphql.optJSONObject("shortcode_media")?.let(::videoUrlFromMedia)
            } else {
                json.optJSONArray("items")?.let(::videoUrlFromItems)
            }
        }.getOrNull()
    }

    /** Old GraphQL `shortcode_media` shape: a direct `video_url`, or the first video in a carousel. */
    private fun videoUrlFromMedia(media: JSONObject): String? {
        media.optStringOrNull("video_url")?.let { return it }

        val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges") ?: return null
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            if (node.optBoolean("is_video", false)) {
                node.optStringOrNull("video_url")?.let { return it }
            }
        }
        return null
    }

    /** Newer `items[]` shape: each item has multiple qualities in `video_versions`. */
    private fun videoUrlFromItems(items: JSONArray): String? {
        for (i in 0 until items.length()) {
            val versions = items.optJSONObject(i)?.optJSONArray("video_versions") ?: continue
            if (versions.length() > 0) {
                versions.optJSONObject(0)?.optStringOrNull("url")?.let { return it }
            }
        }
        return null
    }

    private fun findJson(page: String, regex: Regex): JSONObject? {
        val match = regex.find(page) ?: return null
        return runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
