/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery

import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var server: EdgeAIHTTPServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the Edge AI HTTP server
        server = EdgeAIHTTPServer(this)
        try {
            server?.start()
            Log.d("EdgeAI", "HTTP server started on port 12345")
        } catch (e: Exception) {
            Log.e("EdgeAI", "Failed to start HTTP server", e)
        }

        // Set up the WebView
        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        WebView.setWebContentsDebuggingEnabled(true)

        webView!!.webViewClient = WebViewClient()
        webView!!.loadUrl("https://emr.nomadichacker.com/")
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
