package com.kleinfamily.japan27tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * On-device spike: tries each way of handing a link to the YouTube app, so we can see which ones
 * the Fire TV YouTube app actually honours. Opened from the remote's ☰ menu.
 */
class LinkTestActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = YouTube.preferredPackage(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            setBackgroundColor(Color.parseColor("#1e1b2e"))
        }
        col.addView(text("YouTube link test", 30f, bold = true))
        col.addView(text("YouTube apps found: " + YouTube.installedPackages(this).ifEmpty { listOf("none") }.joinToString(), 18f))
        col.addView(text("Press a button. If YouTube opens on the right video or search results, that format works. Press Back to return here.", 18f))
        status = text("", 18f).also { it.setTextColor(Color.parseColor("#f4a7b9")); col.addView(it) }

        col.addView(text("Videos (“$VIDEO_TITLE”)", 22f, bold = true))
        col.addView(button("V1  VIEW https://www.youtube.com/watch?v=… → YouTube app") {
            pkg?.let { tryIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$VIDEO_ID")).setPackage(it)) }
        })
        col.addView(button("V2  VIEW vnd.youtube:… → YouTube app") {
            pkg?.let { tryIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$VIDEO_ID")).setPackage(it)) }
        })
        col.addView(button("V3  VIEW https://www.youtube.com/watch?v=… (let Fire TV choose; may open Silk)") {
            tryIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$VIDEO_ID")))
        })

        col.addView(text("Searches (“$QUERY”)", 22f, bold = true))
        for (mode in YouTube.SearchMode.entries) {
            col.addView(button("${searchCode(mode)}  ${mode.label}") {
                YouTube.searchIntent(mode, QUERY, pkg)?.let { tryIntent(it) } ?: setStatus("No YouTube app installed")
            })
        }

        col.addView(text("Itinerary search links", 22f, bold = true))
        col.addView(text("The Fire TV YouTube app ignores search links, so Japan27 looks up the top result and plays that video.", 18f))
        col.addView(button("Look up and play the top result") {
            setStatus("Looking up “$QUERY”…")
            Thread {
                val id = YouTube.topVideoId(QUERY)
                runOnUiThread {
                    if (id == null) setStatus("Lookup failed (no network, or YouTube changed its page)")
                    else tryIntent(YouTube.view("https://www.youtube.com/watch?v=$id", pkg))
                }
            }.start()
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun tryIntent(intent: Intent) {
        val target = YouTube.describeTarget(this, intent)
        val ok = YouTube.startFirst(this, listOf(intent))
        setStatus(if (ok) "Sent to $target" else "Failed: no app accepted it ($target)")
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun searchCode(mode: YouTube.SearchMode) = "S" + (mode.ordinal + 1)

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun text(s: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(Color.parseColor("#fdf9f6"))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(if (bold) 20 else 6), 0, dp(6))
    }

    /** D-pad friendly button with an obvious focus state. */
    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTextColor(Color.WHITE)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill("#d9472b", "#f4a7b9"))
            addState(intArrayOf(), pill("#27306b", null))
        }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(0, dp(4), dp(12), dp(4)) }
        setPadding(dp(24), dp(10), dp(24), dp(10))
        setOnClickListener { onClick() }
    }

    private fun pill(fill: String, stroke: String?) = GradientDrawable().apply {
        cornerRadius = dp(28).toFloat()
        setColor(Color.parseColor(fill))
        stroke?.let { setStroke(dp(3), Color.parseColor(it)) }
    }

    companion object {
        // "Me at the zoo", the first YouTube video: it won't be taken down.
        const val VIDEO_ID = "jNQXAC9IVRw"
        const val VIDEO_TITLE = "Me at the zoo"
        const val QUERY = "Fushimi Inari hike to the summit"
    }
}
