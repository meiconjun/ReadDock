package com.comichub.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One persistent browser session for MYCOMIC metadata requests.
 *
 * The site rejects the app's plain HTTP client, so search/detail/page parsing
 * must use the same cookie-enabled WebView session as the reader. This class
 * keeps the WebView off-screen and returns the hydrated document HTML to the
 * source parser. A visible reader WebView can then reuse the same CookieManager
 * when a human challenge needs to be completed.
 */
class MyComicWebSession(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var pending: PendingRequest? = null

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtml(url: String): String = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            val view = ensureWebView()
            pending?.fail(IllegalStateException("MYCOMIC 网页请求仍在进行"))
            pending = PendingRequest(url, continuation)
            view.loadUrl(url)
        }
        continuation.invokeOnCancellation {
            mainHandler.post {
                if (pending?.continuation === continuation) {
                    pending = null
                    webView?.stopLoading()
                }
            }
        }
    }

    /**
     * Return the same browser identity for image requests that was used to
     * obtain the MYCOMIC HTML. The site/CDN commonly rejects a plain
     * HttpURLConnection request without the WebView cookie and referrer.
     */
    fun imageRequestHeaders(url: String, referer: String): Map<String, String> {
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        return buildMap {
            put("User-Agent", WebSettings.getDefaultUserAgent(appContext))
            put("Referer", referer)
            put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            if (cookie.isNotBlank()) put("Cookie", cookie)
        }
    }

    fun destroy() {
        mainHandler.post {
            pending?.fail(IllegalStateException("MYCOMIC 网页会话已关闭"))
            pending = null
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView = webView ?: WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = WebSettings.getDefaultUserAgent(appContext)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Alpine/Livewire hydrates the chapter list shortly after the
                // initial document load. Wait for that DOM before extracting it.
                val request = pending ?: return
                mainHandler.postDelayed({
                    if (pending !== request) return@postDelayed
                    view.evaluateJavascript("document.documentElement.outerHTML") { encoded ->
                        val html = runCatching { JSONTokener(encoded).nextValue() as String }
                            .getOrElse { encoded }
                        if (pending === request) {
                            request.complete(html)
                            pending = null
                        }
                        CookieManager.getInstance().flush()
                    }
                }, DOM_SETTLE_DELAY_MS)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && pending != null) {
                    pending?.fail(IllegalStateException(error.description?.toString() ?: "网页加载失败"))
                    pending = null
                }
            }
        }
        webView = this
    }

    private class PendingRequest(
        val url: String,
        val continuation: CancellableContinuation<String>
    ) {
        fun complete(html: String) {
            if (continuation.isActive) continuation.resume(html)
        }

        fun fail(error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    companion object {
        private const val DOM_SETTLE_DELAY_MS = 900L
    }
}
