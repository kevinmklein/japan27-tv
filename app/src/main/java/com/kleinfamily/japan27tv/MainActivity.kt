package com.kleinfamily.japan27tv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Full-screen WebView around the Japan27 web app.
 *
 * Contract with the web app (it detects TV mode by either of these):
 *  - User agent ends with " Japan27TV/<version>".
 *  - window.Japan27TV bridge: isTV(), version(), youtubeApp(), openYouTube(url).
 *  - Back button: the shell first calls window.japan27TvBack() if the page defines it. Returning
 *    true means the page handled it (closed a modal, etc.); otherwise the WebView goes back, then exits.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView

    /** In-app video player laid over the itinerary, so Back drops straight back to where we were. */
    private var player: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#1e1b2e"))
        root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // target="_blank" links and window.open come through onCreateWindow below.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = "$userAgentString $UA_MARKER/${BuildConfig.VERSION_NAME}"
        }
        web.addJavascriptInterface(Bridge(), "Japan27TV")

        web.webViewClient = object : WebViewClient() {
            // Only top-level navigations: an embedded YouTube iframe should stay in the page.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && handleExternally(request.url)

            @Deprecated("Needed below API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleExternally(Uri.parse(url))

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showOffline()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // Catch the new window's first URL, then route it like a normal link.
                val catcher = WebView(this@MainActivity)
                catcher.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        openLink(request.url)
                        v.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = catcher
                resultMsg.sendToTarget()
                return true
            }
        }

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(HOME_URL)
        }
        web.requestFocus()
    }

    /** A link the page opened: native YouTube if possible, otherwise load it in the WebView. */
    private fun openLink(uri: Uri) {
        if (!handleExternally(uri)) web.loadUrl(uri.toString())
    }

    /** True when the link was handed to another app and the WebView shouldn't load it. */
    private fun handleExternally(uri: Uri): Boolean {
        if (YouTube.isYouTube(uri)) return openYouTube(uri)
        if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "about" || uri.scheme == "data") return false
        // intent:, market:, tel: and friends. Nothing useful to show in the WebView if they fail.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // ignore
        }
        return true
    }

    /**
     * A YouTube link. Videos play straight away. Search links (the itinerary's ▶️ Videos buttons) are
     * looked up first, because the Fire TV YouTube app ignores search links. Returns false only for
     * other YouTube pages when there's no YouTube app, so the WebView shows them.
     */
    private fun openYouTube(uri: Uri): Boolean {
        YouTube.videoId(uri)?.let { play(it); return true }
        val query = YouTube.searchQuery(uri)
            ?: return YouTube.preferredPackage(this)?.let { YouTube.startFirst(this, listOf(YouTube.view(uri.toString(), it))) } ?: false
        Toast.makeText(this, "Finding a video…", Toast.LENGTH_SHORT).show()
        Thread {
            val id = YouTube.topVideoId(query)
            runOnUiThread {
                when {
                    id != null -> play(id)
                    else -> {
                        Toast.makeText(this, "Couldn't find a video for “$query”", Toast.LENGTH_LONG).show()
                        YouTube.preferredPackage(this)?.let { YouTube.startFirst(this, listOf(YouTube.launchIntent(it))) }
                    }
                }
            }
        }.start()
        return true
    }

    private fun play(id: String) {
        val handedOff = !YouTube.playInApp(this) &&
            YouTube.startFirst(this, YouTube.videoIntents(id, YouTube.preferredPackage(this)))
        if (!handedOff) playInApp(id)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun playInApp(id: String) {
        closePlayer()
        val p = WebView(this)
        p.setBackgroundColor(Color.BLACK)
        p.settings.javaScriptEnabled = true
        p.settings.domStorageEnabled = true
        p.settings.mediaPlaybackRequiresUserGesture = false
        p.webChromeClient = WebChromeClient()
        p.webViewClient = object : WebViewClient() {
            // Keep the player from wandering off (e.g. clicking the YouTube logo).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.isForMainFrame
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.querySelector('iframe').focus()", null)
            }
        }
        // The embed needs a referrer, so the page gets the site's origin as its base URL.
        val html = """
            <!doctype html><html><body style="margin:0;background:#000;overflow:hidden">
            <iframe src="https://www.youtube.com/embed/$id?autoplay=1&rel=0&playsinline=1"
              style="border:0;width:100vw;height:100vh" referrerpolicy="strict-origin-when-cross-origin"
              allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        p.loadDataWithBaseURL(HOME_URL, html, "text/html", "utf-8", null)
        root.addView(p, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        p.requestFocus()
        player = p
    }

    private fun closePlayer() {
        player?.let {
            root.removeView(it)
            it.destroy()
        }
        player = null
        web.requestFocus()
    }

    private fun showOffline() {
        val html = """
            <html><body style="background:#1e1b2e;color:#fdf9f6;font-family:sans-serif;display:flex;
            align-items:center;justify-content:center;height:100vh;margin:0;text-align:center">
            <div><div style="font-size:64px">🗾</div>
            <h1 style="font-size:40px">Can't reach Japan27</h1>
            <p style="font-size:24px;opacity:.8">Check the Fire TV's Wi-Fi, then press OK to try again.</p>
            <button autofocus onclick="location.href='$HOME_URL'" style="font-size:28px;padding:16px 40px;
            border-radius:40px;border:0;background:#d9472b;color:#fff;outline:4px solid #f4a7b9">Try again</button>
            </div></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(HOME_URL, html, "text/html", "utf-8", null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Fine on Fire OS; we don't opt in to predictive back")
    override fun onBackPressed() {
        if (player != null) {
            closePlayer()
            return
        }
        web.evaluateJavascript("(typeof window.japan27TvBack === 'function' && window.japan27TvBack() === true)") { handled ->
            when {
                handled == "true" -> Unit
                web.canGoBack() -> web.goBack()
                else -> finish()
            }
        }
    }

    /** The remote's ☰ button. */
    private fun showMenu() {
        val inApp = YouTube.playInApp(this)
        val videos = if (inApp) "Videos play here in Japan27 · switch to the YouTube app"
        else "Videos play in the YouTube app · switch to playing here"
        val items = arrayOf("Reload", "Go to the start page", videos, "YouTube link test", "Version ${BuildConfig.VERSION_NAME}")
        AlertDialog.Builder(this)
            .setTitle("Japan27 TV")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> web.reload()
                    1 -> web.loadUrl(HOME_URL)
                    2 -> {
                        YouTube.setPlayInApp(this, !inApp)
                        val now = if (inApp) "the YouTube app (signed in)" else "Japan27 (Back returns instantly)"
                        Toast.makeText(this, "Videos now play in $now", Toast.LENGTH_LONG).show()
                    }
                    3 -> startActivity(Intent(this, LinkTestActivity::class.java))
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Exposed to the page as window.Japan27TV. Called on a background thread. */
    inner class Bridge {
        @JavascriptInterface fun isTV(): Boolean = true
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun youtubeApp(): String = YouTube.preferredPackage(this@MainActivity) ?: ""
        @JavascriptInterface fun openYouTube(url: String) {
            runOnUiThread { openLink(Uri.parse(url)) }
        }
    }

    companion object {
        const val HOME_URL = "https://jp27.netlify.app/"
        const val UA_MARKER = "Japan27TV"
    }
}
