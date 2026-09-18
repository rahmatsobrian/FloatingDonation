package siroha.floating.donation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import siroha.floating.donation.model.OverlayStatus
import siroha.floating.donation.util.Logger
import siroha.floating.donation.util.UrlValidator

class OverlayWebView(context: Context) {

    val webView: WebView = WebView(context)
    var statusCallback: ((OverlayStatus) -> Unit)? = null
    private var currentUrl: String = ""
    private var isDestroyed = false

    init {
        setupWebView(context)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(context: Context) {
        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(false)
                savePassword = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            // Enable cookies
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!isDestroyed) {
                        statusCallback?.invoke(OverlayStatus.LOADING)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!isDestroyed) {
                        // Make background transparent
                        view?.evaluateJavascript(
                            "document.body.style.backgroundColor='transparent';",
                            null
                        )
                        // Terapkan skala setelah layout pass selesai, supaya
                        // view.width sudah valid (bukan 0)
                        view?.post {
                            if (!isDestroyed) updateScaleToFit(view.width)
                        }
                        statusCallback?.invoke(OverlayStatus.CONNECTED)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && !isDestroyed) {
                        val errorMsg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            error?.description?.toString() ?: "Unknown error"
                        } else {
                            "Unknown error"
                        }
                        Logger.webViewError(errorMsg)
                        statusCallback?.invoke(OverlayStatus.ERROR)
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // Do NOT bypass SSL errors - cancel the request
                    Logger.webViewError("SSL Error: ${error?.primaryError}")
                    handler?.cancel()
                    if (!isDestroyed) {
                        statusCallback?.invoke(OverlayStatus.ERROR)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Only allow HTTP/HTTPS
                    if (!url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        Logger.w("Blocked non-HTTP URL: ${url.take(50)}")
                        return true // Block the navigation
                    }
                    return false // Allow normal HTTP/HTTPS navigation
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Allow audio/video playback
            }

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    fun loadUrl(url: String) {
        if (isDestroyed) return
        if (!UrlValidator.isValid(url)) {
            Logger.webViewError("Invalid URL: $url")
            statusCallback?.invoke(OverlayStatus.ERROR)
            return
        }
        currentUrl = UrlValidator.sanitize(url)
        Logger.webViewLoad(currentUrl)
        statusCallback?.invoke(OverlayStatus.LOADING)
        webView.loadUrl(currentUrl)
    }

    fun reload() {
        if (isDestroyed) return
        if (currentUrl.isNotEmpty()) {
            webView.reload()
        }
    }

    /**
     * Android WebView yang di-attach langsung ke WindowManager (bukan di dalam
     * Activity, seperti overlay ini) punya quirk: CSS "device-width" dihitung
     * dari lebar LAYAR FISIK penuh, bukan dari lebar WebView itu sendiri.
     * Akibatnya saat overlay di-resize lebih kecil dari layar, halaman tetap
     * di-render selebar layar penuh dan yang keliatan cuma sepotong (ke-crop),
     * bukan ikut mengecil proporsional.
     *
     * Fix: hitung rasio lebar container terhadap lebar layar, lalu paksa
     * lewat setInitialScale() supaya konten ikut menyesuaikan ukuran overlay.
     * Dipanggil ulang setiap kali lebar overlay berubah (resize manual,
     * dialog Customize, reset posisi, atau rotasi layar).
     */
    fun updateScaleToFit(containerWidthPx: Int) {
        if (isDestroyed || containerWidthPx <= 0) return
        val screenWidthPx = webView.context.resources.displayMetrics.widthPixels
        if (screenWidthPx <= 0) return
        val scalePercent = (containerWidthPx.toFloat() / screenWidthPx.toFloat() * 100f)
            .toInt()
            .coerceIn(10, 100)
        webView.setInitialScale(scalePercent)
    }

    fun setTransparentBackground() {
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    fun applySettings(
        javaScriptEnabled: Boolean = true,
        mediaPlaybackEnabled: Boolean = true,
        cacheEnabled: Boolean = true,
        cookiesEnabled: Boolean = true
    ) {
        if (isDestroyed) return
        webView.settings.apply {
            this.javaScriptEnabled = javaScriptEnabled
            this.mediaPlaybackRequiresUserGesture = !mediaPlaybackEnabled
            this.cacheMode = if (cacheEnabled) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
    }

    fun setOpacity(opacity: Float) {
        webView.alpha = opacity.coerceIn(0f, 1f)
    }

    fun destroy() {
        isDestroyed = true
        statusCallback = null
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Logger.e("Error destroying WebView", e)
        }
    }

    fun clearCache() {
        if (!isDestroyed) {
            webView.clearCache(true)
            webView.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
        }
    }
}
