package com.google.ai.edge.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.ai.edge.gallery.data.getModelByName
import com.google.ai.edge.gallery.data.TASKS
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EdgeAIHTTPServer(private val context: Context) : NanoHTTPD(12345) {
    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method == Method.GET && session.uri == "/health") {
                NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", "OK")
            } else if (session.method == Method.POST && session.uri == "/edgeai") {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                val json = JSONObject(jsonString)
                val prompt = json.getString("prompt")
                val modelName = json.optString("model", null)
                val result = runLlmTextGen(prompt, modelName)
                val responseJson = JSONObject()
                responseJson.put("text", result)
                NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
            } else if (session.method == Method.POST && session.uri == "/edgeai_image") {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                val json = JSONObject(jsonString)
                val prompt = json.getString("prompt")
                val imageBase64 = json.getString("image")
                val modelName = json.optString("model", null)
                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val result = runLlmTextImageGen(prompt, bitmap, modelName)
                val responseJson = JSONObject()
                responseJson.put("text", result)
                NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
            } else {
                NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
    }

    private fun getDefaultLlmModel(): com.google.ai.edge.gallery.data.Model? {
        // Try to get the first LLM model from all tasks
        for (task in TASKS) {
            for (model in task.models) {
                if (model.llmPromptTemplates != null || model.llmSupportImage || model.llmSupportAudio) {
                    return model
                }
            }
        }
        return null
    }

    private fun runLlmTextGen(prompt: String, modelName: String?): String {
        val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
        if (model == null) return "No LLM model available"
        val latch = CountDownLatch(1)
        var resultText = ""
        LlmChatModelHelper.initialize(context, model) { err ->
            if (err.isNotEmpty()) {
                resultText = err
                latch.countDown()
            } else {
                LlmChatModelHelper.runInference(
                    model,
                    prompt,
                    resultListener = { partial, done ->
                        if (done) {
                            resultText = partial
                            latch.countDown()
                        }
                    },
                    cleanUpListener = {},
                    images = listOf()
                )
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return resultText
    }

    private fun runLlmTextImageGen(prompt: String, bitmap: Bitmap, modelName: String?): String {
        val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
        if (model == null) return "No LLM model available"
        val latch = CountDownLatch(1)
        var resultText = ""
        LlmChatModelHelper.initialize(context, model) { err ->
            if (err.isNotEmpty()) {
                resultText = err
                latch.countDown()
            } else {
                LlmChatModelHelper.runInference(
                    model,
                    prompt,
                    resultListener = { partial, done ->
                        if (done) {
                            resultText = partial
                            latch.countDown()
                        }
                    },
                    cleanUpListener = {},
                    images = listOf(bitmap)
                )
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return resultText
    }
} 