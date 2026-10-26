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
import android.view.WindowManager
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

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#1e1b2e"))
        setContentView(web)

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
        if (YouTube.isYouTube(uri)) return YouTube.open(this, uri)
        if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "about" || uri.scheme == "data") return false
        // intent:, market:, tel: and friends. Nothing useful to show in the WebView if they fail.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // ignore
        }
        return true
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
        val items = arrayOf("Reload", "Go to the start page", "YouTube link test", "Version ${BuildConfig.VERSION_NAME}")
        AlertDialog.Builder(this)
            .setTitle("Japan27 TV")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> web.reload()
                    1 -> web.loadUrl(HOME_URL)
                    2 -> startActivity(Intent(this, LinkTestActivity::class.java))
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
