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
        val currentTitle = (webView.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }) ?: latestTitle
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
