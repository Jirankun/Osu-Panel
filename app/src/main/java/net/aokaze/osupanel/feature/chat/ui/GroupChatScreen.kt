/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.util.openInCustomTab
import net.aokaze.osupanel.data.local.ChatSettingsStore
import net.aokaze.osupanel.ui.components.OsuSpinner

/**
 * Group chat — full-screen WebView loading https://<group>.chatango.com/?js
 * (official HTML5 mode). All native-browser APIs are enabled (photos, files,
 * camera, geolocation, fullscreen video). Links that leave the group page
 * open in a Chrome Custom Tab. The bottom lifts above the keyboard.
 *
 * IMPORTANT: one WebView per group is kept alive for the whole app session
 * (GroupChatSessions) and only paused while the screen is closed — so closing
 * & reopening the chat does NOT stack new WebViews (each one keeps a socket
 * connection to the room, which duplicated active users and wasted memory).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GroupChatScreen(
    group: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Reuse the session WebView for this group; fresh = never loaded before.
    val webView = remember { GroupChatSessions.get(context, group) }
    val fresh = remember { !GroupChatSessions.wasLoaded(group) }
    var loading by remember { mutableStateOf(fresh) }
    var pageError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        // Persist read access so the WebView can upload large files.
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        GroupChatSessions.pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        GroupChatSessions.clearFileCallback()
    }

    DisposableEffect(Unit) {
        // Hand this screen's hooks to the session WebView, load only once.
        GroupChatSessions.filePickerLauncher = { mime -> filePicker.launch(mime) }
        GroupChatSessions.loadingListener = { loading = it }
        GroupChatSessions.errorListener = { pageError = it }
        webView.onResume()
        if (fresh) webView.loadUrl("https://$group.chatango.com/?js")
        onDispose {
            // Pause (stop JS/socket work) instead of destroying — the page
            // survives so the next open is instant and no instance stacks up.
            GroupChatSessions.filePickerLauncher = null
            GroupChatSessions.loadingListener = null
            GroupChatSessions.errorListener = null
            GroupChatSessions.clearFileCallback()
            GroupChatSessions.hideCustomView()
            webView.onPause()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(group, fontWeight = FontWeight.Bold, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Main web area — lifts above the keyboard (imePadding) so the
            // page's message box is never hidden behind it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )

                if (pageError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            "${stringResource(R.string.chat_group_load_failed)} ($pageError)",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    pageError = null
                                    webView.reload()
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(R.string.chat_retry),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (loading) {
                    OsuSpinner(modifier = Modifier.align(Alignment.Center))
                }
            }

            // Fullscreen video overlay (HTML5 video API) — above everything.
            GroupChatSessions.customView?.let { video ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())
                    IconButton(
                        onClick = { GroupChatSessions.hideCustomView() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.chat_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One WebView per group for the whole session. State that the UI needs
 * (loading, error, file chooser, fullscreen) is exposed as Compose state
 * / listeners so the screen can react even though the WebView outlives it.
 */
private object GroupChatSessions {

    private val views = HashMap<String, WebView>()
    private val loaded = HashSet<String>()

    var pendingFileCallback by mutableStateOf<ValueCallback<Array<Uri>>?>(null)
        private set
    var customView by mutableStateOf<View?>(null)
        private set
    var customViewCallback by mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
        private set
    var filePickerLauncher: ((Array<String>) -> Unit)? = null
    var loadingListener: ((Boolean) -> Unit)? = null
    var errorListener: ((String?) -> Unit)? = null

    fun get(context: Context, group: String): WebView =
        views.getOrPut(group) { createWebView(context.applicationContext, group) }

    fun wasLoaded(group: String): Boolean = group in loaded

    private fun markLoaded(group: String) {
        loaded += group
    }

    fun clearFileCallback() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
    }

    fun hideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    private fun createWebView(context: Context, group: String): WebView =
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                // Allow everything on the page (photos, files, mixed resources).
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                // Native-browser APIs: zoom, geolocation, wide viewport, popups.
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setGeolocationEnabled(true)
                setSupportMultipleWindows(true)
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            // Third-party cookies (session/auth for st.chatango.com embeds).
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(ChatangoJsBridge(context), "android")
            webChromeClient = object : WebChromeClient() {
                // "Pilih foto" / "Pilih file" → Android file/document picker.
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    if (filePathCallback == null) return false
                    val launcher = filePickerLauncher
                    if (launcher == null) return false
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = filePathCallback
                    launcher(arrayOf("*/*"))
                    return true
                }

                // Grant camera/mic/etc. requests — "izinkan semuanya".
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    callback?.invoke(origin, true, false)
                }

                // Fullscreen HTML5 video — show the browser-supplied view.
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customView = view
                    customViewCallback = callback
                }

                override fun onHideCustomView() {
                    hideCustomView()
                }
            }
            webViewClient = object : WebViewClient() {
                // Links leaving the group page → Chrome Custom Tab.
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    if (request?.isForMainFrame != true) return false // keep the chat iframe
                    val url = request.url.toString()
                    return if (isGroupScope(group, url)) {
                        false
                    } else {
                        openInCustomTab(context, url)
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    loadingListener?.invoke(true)
                    errorListener?.invoke(null)
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    errorListener?.invoke(description ?: "error $errorCode")
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        errorListener?.invoke("HTTP ${errorResponse?.statusCode}")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingListener?.invoke(false)
                    markLoaded(group)
                    // The embed page keeps the chat iframe at height 0 until the
                    // iframe reports its size (resize_iframe message) — which the
                    // iframe never sends inside a WebView. Force it to fill the
                    // viewport so the HTML5 chat actually shows.
                    view?.evaluateJavascript(FIX_IFRAME_JS, null)
                }
            }
        }
}

/**
 * Forces the chat iframe (st.chatango.com/h5/...) to fill the viewport.
 * The embed page sizes the iframe from a postMessage that never arrives in a
 * WebView, leaving it at height 0 (blank screen). Applied on page load;
 * a MutationObserver + interval catch the iframe even when it is created late.
 */
private const val FIX_IFRAME_JS =
    """(function(){
    function h(){
        return Math.max(window.innerHeight, document.documentElement.clientHeight || 0);
    }
    function fix(){
        var f = document.querySelector('iframe[src*="it.html"]') || document.querySelector('iframe');
        if (f){
            f.style.height = h() + 'px';
            f.style.width = '100%';
            f.style.display = 'block';
            return true;
        }
        return false;
    }
    fix();
    setTimeout(fix, 500);
    setTimeout(fix, 2000);
    var tries = 0;
    var iv = setInterval(function(){ if (fix() || ++tries > 30) clearInterval(iv); }, 500);
    try {
        var mo = new MutationObserver(function(){ fix(); });
        mo.observe(document.documentElement, {childList:true, subtree:true});
    } catch(e) {}
    window.addEventListener('resize', fix);
})();"""

/**
 * True for URLs that belong to the group chat page itself (the group's own
 * domain + the st.chatango.com embed). Everything else leaves to a Custom Tab.
 */
private fun isGroupScope(group: String, url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
    return host.equals("${group.lowercase()}.chatango.com", ignoreCase = true) ||
        host.equals("st.chatango.com", ignoreCase = true)
}

/** JS bridge exposed to the Chatango web page as `android`. */
class ChatangoJsBridge(private val context: Context) {

    @JavascriptInterface
    fun getToken(): String = ChatSettingsStore.getToken(context).orEmpty()

    @JavascriptInterface
    fun getS(): String = Build.SERIAL

    @JavascriptInterface
    fun sendToApp(json: String) {
        // Web → app events (logout, upload, sound, …). Handled in a later step.
    }

    @JavascriptInterface
    fun openSettings() = Unit

    @JavascriptInterface
    fun shareWebView() = Unit

    @JavascriptInterface
    fun log(message: String) = Unit // page-facing API — keep, but no logcat spam
}
