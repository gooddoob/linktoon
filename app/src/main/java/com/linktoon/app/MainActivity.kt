package com.linktoon.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var searchWebView: WebView? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var activeSearchId: String? = null
    private val onlineUrl = BuildConfig.START_URL
    private val offlineAssetUrl = BuildConfig.OFFLINE_URL

    private val viewerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val itemId = result.data?.getStringExtra("ITEM_ID")
            val updatedUrl = result.data?.getStringExtra("UPDATED_URL")
            val updatedTitle = result.data?.getStringExtra("UPDATED_TITLE") ?: ""
            if (!itemId.isNullOrEmpty() && !updatedUrl.isNullOrEmpty()) {
                runOnUiThread {
                    val safeTitle = updatedTitle
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", " ")
                        .replace("\r", "")
                    val safeUrl = updatedUrl
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                    val js = "javascript:if(window.onViewerUrlChanged){window.onViewerUrlChanged('$itemId', '$safeUrl', '$safeTitle');}"
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.mainWebView)
        setupWebView()
        webView.loadUrl(onlineUrl)
    }

    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    // Fall back to offline assets if network is unreachable
                    view?.loadUrl(offlineAssetUrl)
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun openViewer(itemId: String, url: String, title: String) {
                openViewer(itemId, url, title, null)
            }

            @JavascriptInterface
            fun openViewer(itemId: String, url: String, title: String, extractScript: String?) {
                val intent = Intent(this@MainActivity, ViewerActivity::class.java).apply {
                    putExtra("ITEM_ID", itemId)
                    putExtra("TARGET_URL", url)
                    putExtra("TITLE", title)
                    putExtra("EXTRACT_SCRIPT", extractScript)
                }
                viewerLauncher.launch(intent)
            }

            @JavascriptInterface
            fun searchKakaoThumbnail(keyword: String, requestId: String) {
                searchKakaoThumbnail(keyword, requestId, "")
            }

            // categoryUid: 카카오페이지 검색 분류 ("10" 웹툰, "11" 웹소설, "" 전체). 값은 웹(index.html)이 정한다.
            @JavascriptInterface
            fun searchKakaoThumbnail(keyword: String, requestId: String, categoryUid: String) {
                runOnUiThread {
                    startKakaoThumbnailSearch(keyword, requestId, categoryUid)
                }
            }
        }, "AndroidBridge")
    }

    private fun startKakaoThumbnailSearch(keyword: String, requestId: String, categoryUid: String) {
        searchHandler.removeCallbacksAndMessages(null)
        activeSearchId = requestId

        if (searchWebView == null) {
            searchWebView = findViewById<WebView>(R.id.searchWebView).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
            }
        }

        val wv = searchWebView!!
        val searchUrl = "https://page.kakao.com/search/result?keyword=" + Uri.encode(keyword) +
            if (categoryUid.isNotEmpty()) "&categoryUid=" + Uri.encode(categoryUid) else ""
        var resolved = false

        fun finishSearch(thumbUrl: String) {
            if (resolved || activeSearchId != requestId) return
            resolved = true
            searchHandler.removeCallbacksAndMessages(null)
            wv.stopLoading()
            val safeId = requestId.replace("'", "\\'")
            val safeUrl = thumbUrl.replace("\\", "\\\\").replace("'", "\\'")
            val js = "javascript:if(window.onKakaoThumbnailResult){window.onKakaoThumbnailResult('$safeId', '$safeUrl');}"
            webView.evaluateJavascript(js, null)
        }

        fun checkDom(attempt: Int) {
            if (resolved || activeSearchId != requestId) return
            val extractJs = """
                (function() {
                    var imgs = document.querySelectorAll('img');
                    for (var i = 0; i < imgs.length; i++) {
                        var src = imgs[i].currentSrc || imgs[i].getAttribute('src') || imgs[i].getAttribute('data-src') || '';
                        if (!src && imgs[i].getAttribute('srcset')) {
                            src = imgs[i].getAttribute('srcset').split(',')[0].trim().split(' ')[0];
                        }
                        if (src.indexOf('dn-img-page.kakao.com') !== -1 || (src.indexOf('kakaocdn.net/dn/') !== -1 && src.indexOf('pageweb') === -1)) {
                            if (src.indexOf('//') === 0) src = 'https:' + src;
                            return src;
                        }
                    }
                    var bodyText = document.body ? (document.body.innerText || '') : '';
                    if (bodyText.indexOf('검색 결과가 없습니다') !== -1 || bodyText.indexOf('검색결과가 없습니다') !== -1) {
                        return 'NO_RESULT';
                    }
                    return '';
                })();
            """.trimIndent()

            wv.evaluateJavascript(extractJs) { result ->
                if (resolved || activeSearchId != requestId) return@evaluateJavascript
                var cleaned = result?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                if (cleaned.startsWith("//")) {
                    cleaned = "https:$cleaned"
                }
                if (cleaned == "NO_RESULT") {
                    finishSearch("")
                } else if (cleaned.startsWith("http")) {
                    finishSearch(cleaned)
                } else if (attempt < 14) {
                    searchHandler.postDelayed({ checkDom(attempt + 1) }, 500)
                } else {
                    finishSearch("")
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!resolved && activeSearchId == requestId) {
                    searchHandler.postDelayed({ checkDom(0) }, 500)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    finishSearch("")
                }
            }
        }

        wv.loadUrl(searchUrl)
        // Hard timeout after 10 seconds
        searchHandler.postDelayed({ finishSearch("") }, 10000)
    }

    override fun onDestroy() {
        searchHandler.removeCallbacksAndMessages(null)
        searchWebView?.destroy()
        searchWebView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

