package com.kleinfamily.japan27tv

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Opens YouTube links in the native TV YouTube app instead of the WebView.
 *
 * Which intent formats the Fire TV YouTube app honours isn't documented, so search links use a
 * format picked on the device in the YouTube link test screen (stored in prefs). Video links
 * try the common formats in order.
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

    /** How search links are opened. Ids are stored in prefs, so don't rename them. */
    enum class SearchMode(val label: String) {
        RESULTS_URL("VIEW youtube.com/results?search_query=…"),
        SEARCH_ACTION("SEARCH action with a query extra"),
        TV_HASH_URL("VIEW youtube.com/tv#/results?search_query=…"),
        APP_HOME("Open the YouTube app home, show the search words"),
        TV_SEARCH_HASH("VIEW youtube.com/tv#/search?q=…"),
    }

    private const val PREFS = "japan27tv"
    private const val KEY_SEARCH_MODE = "search_mode"

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

    fun searchMode(ctx: Context): SearchMode {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEARCH_MODE, null)
        return SearchMode.entries.firstOrNull { it.name == saved } ?: SearchMode.RESULTS_URL
    }

    fun setSearchMode(ctx: Context, mode: SearchMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEARCH_MODE, mode.name).apply()
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

    // Never send a youtube.com URL without a package: Fire TV hands those to Silk, which plays logged out.
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

    private fun view(url: String, pkg: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
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

    /**
     * Opens a YouTube link natively. Returns false when no YouTube app took it, so the caller can
     * load it in the WebView instead.
     */
    fun open(activity: Activity, uri: Uri): Boolean {
        val pkg = preferredPackage(activity)
        videoId(uri)?.let { id -> return startFirst(activity, videoIntents(id, pkg)) }

        val query = searchQuery(uri)
        if (query != null) {
            if (pkg == null) return false
            val mode = searchMode(activity)
            val intents = listOfNotNull(
                searchIntent(mode, query, pkg),
                pkg?.let { launchIntent(it) },
                pkg?.let { activity.packageManager.getLaunchIntentForPackage(it) },
            )
            val ok = startFirst(activity, intents)
            if (ok && mode == SearchMode.APP_HOME) {
                Toast.makeText(activity, "Search YouTube for: $query", Toast.LENGTH_LONG).show()
            }
            return ok
        }

        // A channel, playlist or other YouTube page: hand the URL over as-is.
        return pkg != null && startFirst(activity, listOf(view(uri.toString(), pkg)))
    }

    /** Short description of which activity would take an intent, for the link test screen. */
    fun describeTarget(ctx: Context, intent: Intent): String {
        val info = ctx.packageManager.resolveActivity(intent, 0) ?: return "no app accepts this"
        return info.activityInfo.packageName + "/" + info.activityInfo.name.substringAfterLast('.')
    }
}
