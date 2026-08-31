# Third-party notices

This project is licensed under the GNU General Public License v3.0 (see
`LICENSE`), because it incorporates code adapted from a GPLv3 project:

## VidSnap

`app/src/main/java/com/videosubtitler/ocr/extractor/InstagramExtractor.kt` is
adapted from `Instagram.java` in [VidSnap](https://github.com/mugames/VidSnap)
(the JSON-extraction logic for pulling a video URL out of an Instagram post
page — the `window._sharedData` / `window.__additionalDataLoaded` parsing).
VidSnap is licensed under the GPLv3.

The WebView login flow in
`app/src/main/java/com/videosubtitler/ocr/ui/InstagramLoginScreen.kt` also
reuses a technique (a legacy-browser user-agent string, to get Instagram's
server-rendered login form instead of its WebView-fingerprinting JS app) from
VidSnap's `LoginFragment.java`.

Everything else in this app was written independently for this project.
