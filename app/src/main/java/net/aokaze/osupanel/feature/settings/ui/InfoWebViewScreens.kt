/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.settings.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import net.aokaze.osupanel.R

/**
 * License screen — WebView loads `assets/License_style_v2.html`
 * (license dropdown + canvas laser triangles) with the Torus font
 * injected as base64 from `res/font` (no font file duplication in assets).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoWebViewScreen(
        title = stringResource(R.string.settings_license),
        onBack = onBack,
        assetPath = "License_style_v2.html",
    )
}

/**
 * Contributor screen — WebView loads `assets/contributor.html`
 * (contributor cards from GitHub). Tapping a username (github.com) →
 * opens in the external browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorsScreen(onBack: () -> Unit) {
    InfoWebViewScreen(
        title = stringResource(R.string.settings_contributor),
        onBack = onBack,
        assetPath = "contributor.html",
    )
}

/** Generic WebView for info pages in assets (JS + internet enabled). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoWebViewScreen(
    title: String,
    onBack: () -> Unit,
    assetPath: String,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.profile_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = openExternallyIfGithub(ctx, request.url.toString())
                    }
                    val raw = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
                    loadDataWithBaseURL(
                        "file:///android_asset/",
                        injectTorusFonts(ctx, raw),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * github.com links → external browser (ACTION_VIEW). Other links are left
 * to load inside the WebView.
 */
private fun openExternallyIfGithub(context: Context, url: String): Boolean {
    if (url.contains("github.com")) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        return true
    }
    return false
}

/**
 * Injects the @font-face Torus (base64 from `res/font`, without copying files)
 * into the `<style id="host-fonts">` element of the HTML page.
 */
private fun injectTorusFonts(context: Context, html: String): String {
    val css = buildString {
        append(torusFace(context, R.font.torus_regular, 400))
        append(torusFace(context, R.font.torus_semibold, 600))
        append(torusFace(context, R.font.torus_bold, 700))
    }
    return html.replace(
        "<style id=\"host-fonts\"></style>",
        "<style id=\"host-fonts\">$css</style>",
    )
}

private fun torusFace(context: Context, resId: Int, weight: Int): String {
    val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "@font-face{font-family:'Torus';src:url(data:font/otf;base64,$b64) format('opentype');font-weight:$weight;font-style:normal}\n"
}
