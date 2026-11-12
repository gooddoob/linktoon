package com.linktoon.app

import android.content.Intent
import android.os.Bundle
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
    private val onlineUrl = "https://gooddoob.github.io/linktoon/"
    private val offlineAssetUrl = "file:///android_asset/index.html"

    private val viewerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val itemId = result.data?.getStringExtra("ITEM_ID")
            val updatedUrl = result.data?.getStringExtra("UPDATED_URL")
            if (!itemId.isNullOrEmpty() && !updatedUrl.isNullOrEmpty()) {
                runOnUiThread {
                    val js = "javascript:if(window.onViewerUrlChanged){window.onViewerUrlChanged('$itemId', '$updatedUrl');}"
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
                val intent = Intent(this@MainActivity, ViewerActivity::class.java).apply {
                    putExtra("ITEM_ID", itemId)
                    putExtra("TARGET_URL", url)
                    putExtra("TITLE", title)
                }
                viewerLauncher.launch(intent)
            }
        }, "AndroidBridge")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

