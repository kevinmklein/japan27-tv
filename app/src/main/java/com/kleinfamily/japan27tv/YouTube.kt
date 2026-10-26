package com.kleinfamily.japan27tv

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube links: which app takes them, and how search links become a video.
 *
 * Tested on Kevin's Fire TV Stick 4K (AFTMM, Fire OS 6.7; Oct 26, 2026): the Fire TV YouTube app plays
 * VIEW https://www.youtube.com/watch?v=ID (with or without its package set), but ignores vnd.youtube:ID
 * and every search format (it just opens its home screen). So search links are resolved to the top
 * result's video id first.
 */
object YouTube {

    /** TV YouTube apps first, then the phone app (handy when testing on a phone). */
    val PACKAGES = listOf(
        "com.amazon.firetv.youtube",
        "com.google.android.youtube.tv",
        "com.google.android.youtube.googletv",
        "com.google.android.youtube",
    )

    private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com")

    /** Search formats tried in the link test screen. None worked on Fire TV; kept for other devices. */
    enum class SearchMode(val label: String) {
        RESULTS_URL("VIEW youtube.com/results?search_query=…"),
        SEARCH_ACTION("SEARCH action with a query extra"),
        TV_HASH_URL("VIEW youtube.com/tv#/results?search_query=…"),
        APP_HOME("Open the YouTube app home"),
        TV_SEARCH_HASH("VIEW youtube.com/tv#/search?q=…"),
    }

    private const val PREFS = "japan27tv"
    private const val KEY_PLAY_IN_APP = "play_in_app"

    private val topVideoCache = HashMap<String, String>()

    fun isYouTube(uri: Uri): Boolean =
        uri.scheme == "vnd.youtube" || (uri.scheme in setOf("http", "https") && uri.host?.lowercase() in HOSTS)

    fun installedPackages(ctx: Context): List<String> = PACKAGES.filter { pkg ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun preferredPackage(ctx: Context): String? = installedPackages(ctx).firstOrNull()

    /** True: play inside Japan27 (Back returns instantly, not signed in). False: the YouTube app. */
    fun playInApp(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PLAY_IN_APP, false)

    fun setPlayInApp(ctx: Context, inApp: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PLAY_IN_APP, inApp).apply()
    }

    /** Video id from watch?v=, youtu.be/, /shorts/, /embed/, /live/ or vnd.youtube: links. */
    fun videoId(uri: Uri): String? {
        if (uri.scheme == "vnd.youtube") return uri.schemeSpecificPart?.trimStart('/')?.substringBefore('?')
        if (uri.host?.lowercase() == "youtu.be") return uri.pathSegments.firstOrNull()
        uri.getQueryParameter("v")?.let { return it }
        val segs = uri.pathSegments
        if (segs.size >= 2 && segs[0] in setOf("shorts", "embed", "live")) return segs[1]
        return null
    }

    fun searchQuery(uri: Uri): String? =
        if (uri.path?.trimEnd('/') == "/results") uri.getQueryParameter("search_query") ?: uri.getQueryParameter("q") else null

    /**
     * The first regular video in YouTube's search results, or null. Blocking: call off the main thread.
     * Reads the results page's embedded JSON; if YouTube changes it, links fall back to the app's home screen.
     */
    fun topVideoId(query: String): String? {
        synchronized(topVideoCache) { topVideoCache[query]?.let { return it } }
        return try {
            val url = URL("https://www.youtube.com/results?search_query=" + Uri.encode(query))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", DESKTOP_UA)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
                ?.also { id -> synchronized(topVideoCache) { topVideoCache[query] = id } }
        } catch (e: Exception) {
            null
        }
    }

    // Fire TV hands package-less youtube.com URLs to the YouTube app, but other devices may pick a browser.
    fun videoIntents(id: String, pkg: String?): List<Intent> = listOfNotNull(
        pkg?.let { view("https://www.youtube.com/watch?v=$id", it) },
        pkg?.let { view("vnd.youtube:$id", it) },
        view("vnd.youtube:$id", null),
    )

    fun searchIntent(mode: SearchMode, query: String, pkg: String?): Intent? {
        val q = Uri.encode(query)
        return when (mode) {
            SearchMode.RESULTS_URL -> view("https://www.youtube.com/results?search_query=$q", pkg)
            SearchMode.TV_HASH_URL -> view("https://www.youtube.com/tv#/results?search_query=$q", pkg)
            SearchMode.TV_SEARCH_HASH -> view("https://www.youtube.com/tv#/search?q=$q", pkg)
            SearchMode.SEARCH_ACTION -> Intent(Intent.ACTION_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                pkg?.let { setPackage(it) }
            }
            SearchMode.APP_HOME -> pkg?.let { launchIntent(it) }
        }
    }

    /** Fire TV apps register a leanback launcher entry; some have no plain launcher entry. */
    fun launchIntent(pkg: String): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg)

    fun view(url: String, pkg: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        pkg?.let { setPackage(it) }
    }

    /** Starts the first intent that some app accepts. Returns false if none did. */
    fun startFirst(activity: Activity, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // try the next format
            } catch (e: SecurityException) {
                // the target activity isn't exported; try the next format
            }
        }
        return false
    }

    /** Short description of which activity would take an intent, for the link test screen. */
    fun describeTarget(ctx: Context, intent: Intent): String {
        val info = ctx.packageManager.resolveActivity(intent, 0) ?: return "no app accepts this"
        return info.activityInfo.packageName + "/" + info.activityInfo.name.substringAfterLast('.')
    }

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
}
