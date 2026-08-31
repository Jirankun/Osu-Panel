/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Open [url] in a Chrome Custom Tab (in-app browser overlay), with a plain
 * browser fallback. Used by the group-chat WebView and the bio "About" popup.
 */
fun openInCustomTab(context: Context, url: String) {
    val target = normalizeUrl(url) ?: return
    try {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        // May be launched from an app-context holder — never from an Activity.
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, Uri.parse(target))
    } catch (e: Exception) {
        // Fallback: default browser.
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Accepts absolute URLs as-is; turns relative/root-relative ones into https. */
private fun normalizeUrl(url: String): String? {
    val t = url.trim()
    if (t.isEmpty()) return null
    return when {
        t.startsWith("//") -> "https:$t"
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t) -> t
        else -> "https://$t"
    }
}
