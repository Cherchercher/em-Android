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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var server: EdgeAIHTTPServer? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("EdgeAI", "onCreate called")
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
        webView!!.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }
}
