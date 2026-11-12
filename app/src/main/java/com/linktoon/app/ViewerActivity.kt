package com.linktoon.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton

    private var itemId: String = ""
    private var initialUrl: String = ""
    private var latestUrl: String = ""
    private var latestTitle: String = ""
    private var latestH2Text: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        itemId = intent.getStringExtra("ITEM_ID") ?: ""
        initialUrl = intent.getStringExtra("TARGET_URL") ?: ""
        val title = intent.getStringExtra("TITLE") ?: "웹툰 뷰어"
        latestUrl = initialUrl

        tvTitle = findViewById(R.id.tvViewerTitle)
        tvTitle.text = title

        progressBar = findViewById(R.id.viewerProgressBar)
        btnBack = findViewById(R.id.btnViewerBack)
        btnBack.setOnClickListener {
            finishWithResult()
        }

        webView = findViewById(R.id.viewerWebView)
        setupViewerWebView()

        if (initialUrl.isNotEmpty()) {
            webView.loadUrl(initialUrl)
        }
    }

    private fun setupViewerWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString.replace("; wv", "") // 모바일 크롬 브라우저와 동일한 브라우저 엔진으로 인식
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank() && !title.startsWith("http") && title != "about:blank") {
                    latestTitle = title
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    if (isValidContentUrl(it)) {
                        latestUrl = it
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    if (isValidContentUrl(it)) {
                        latestUrl = it
                    }
                }
                view?.title?.let {
                    if (it.isNotBlank() && !it.startsWith("http") && it != "about:blank") {
                        latestTitle = it
                    }
                }

                // 지정된 h2 스타일 태그 또는 회차 포함 태그의 텍스트 자동 추출
                val js = """
                    (function() {
                        var h2s = document.getElementsByTagName('h2');
                        for (var i = 0; i < h2s.length; i++) {
                            var st = (h2s[i].getAttribute('style') || '').toLowerCase().replace(/\s+/g, '');
                            if (st.indexOf('padding-left:60px') !== -1 || (st.indexOf('float:left') !== -1 && st.indexOf('font-size:15px') !== -1)) {
                                return (h2s[i].innerText || h2s[i].textContent || '').trim();
                            }
                        }
                        for (var i = 0; i < h2s.length; i++) {
                            var txt = (h2s[i].innerText || h2s[i].textContent || '').trim();
                            if (/\d+\s*화/.test(txt)) {
                                return txt;
                            }
                        }
                        return '';
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js) { res ->
                    if (!res.isNullOrBlank() && res != "null" && res != "\"\"") {
                        val parsed = res.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\n", " ").trim()
                        if (parsed.isNotBlank()) {
                            latestH2Text = parsed
                        }
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (isValidContentUrl(url)) {
                        latestUrl = url
                    }
                    return false
                }
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    true
                }
            }
        }
    }

    private fun isValidContentUrl(url: String): Boolean {
        // about:blank 나 리다이렉트 중간 주소 제외
        return url.startsWith("http") && !url.contains("about:blank")
    }

    private fun finishWithResult() {
        val currentTitle = if (latestH2Text.isNotBlank()) {
            latestH2Text
        } else {
            (webView.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }) ?: latestTitle
        }
        val data = Intent().apply {
            putExtra("ITEM_ID", itemId)
            putExtra("UPDATED_URL", latestUrl)
            putExtra("UPDATED_TITLE", currentTitle)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finishWithResult()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
