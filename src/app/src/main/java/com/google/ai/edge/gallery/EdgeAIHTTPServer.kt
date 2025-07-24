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
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.util.Log
import java.net.URL
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface
import java.net.Inet4Address
import java.security.MessageDigest

class EdgeAIHTTPServer(private val context: Context) : NanoHTTPD("0.0.0.0", 12345) {
    
    private fun downloadImageFromUrl(imageUrl: String): Bitmap? {
        return try {
            Log.d("EdgeAIHTTPServer", "Downloading image from: $imageUrl")
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000
            val inputStream: InputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            Log.d("EdgeAIHTTPServer", "Successfully downloaded image: ${bitmap?.width}x${bitmap?.height}")
            bitmap
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to download image from $imageUrl: ${e.message}")
            null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d("EdgeAI", "Serving....")
        try {
            // Handle CORS preflight
            if (session.method == Method.OPTIONS) {
                val response = NanoHTTPD.newFixedLengthResponse("")
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                response.addHeader("Access-Control-Max-Age", "3600")
                return response
            }
            // Serve images from /images/{filename}
            if (session.method == Method.GET && session.uri.startsWith("/images/")) {
                val filename = session.uri.removePrefix("/images/")
                val file = File(context.filesDir, filename)
                if (file.exists()) {
                    val fis = file.inputStream()
                    val response = NanoHTTPD.newChunkedResponse(Status.OK, "image/png", fis)
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                } else {
                    val response = NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Image not found")
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                }
            }
            if (session.method == Method.POST && session.uri == "/edgeai") {
                Log.d("EdgeAIHTTPServer", "/edgeai route called")
                val body = HashMap<String, String>()
                session.parseBody(body)
                
                // Try to get JSON from different possible sources
                var jsonString = body["postData"] ?: ""
                if (jsonString.isEmpty()) {
                    // Try to get from raw body
                    jsonString = body[""] ?: ""
                }
                if (jsonString.isEmpty()) {
                    // Try to get from any available key
                    jsonString = body.values.firstOrNull() ?: ""
                }
                
                Log.d("EdgeAIHTTPServer", "Request body: $jsonString")
                Log.d("EdgeAIHTTPServer", "Available body keys: ${body.keys}")
                
                if (jsonString.isEmpty()) {
                    Log.e("EdgeAIHTTPServer", "No JSON data found in request")
                    return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "text/plain", "No JSON data found")
                }
                
                val json = JSONObject(jsonString)
                val prompt = json.getString("prompt")
                val modelName = json.optString("model", null)
                val result = runLlmTextGen(prompt, modelName)
                val responseJson = JSONObject()
                responseJson.put("text", result)
                Log.d("EdgeAIHTTPServer", "Response: ${responseJson.toString()}")
                val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                return response

            } else if (session.method == Method.POST && session.uri == "/edgeai_chat") {
                Log.d("EdgeAIHTTPServer", "/edgeai_chat route called")
                val body = HashMap<String, String>()
                session.parseBody(body)
                
                var jsonString = body["postData"] ?: ""
                if (jsonString.isEmpty()) {
                    jsonString = body[""] ?: ""
                }
                if (jsonString.isEmpty()) {
                    jsonString = body.values.firstOrNull() ?: ""
                }
                
                Log.d("EdgeAIHTTPServer", "Chat request body: $jsonString")
                
                if (jsonString.isEmpty()) {
                    Log.e("EdgeAIHTTPServer", "No JSON data found in chat request")
                    return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "text/plain", "No JSON data found")
                }
                
                val json = JSONObject(jsonString)
                val messages = json.getJSONArray("messages")
                val modelName = json.optString("model", null)
                
                // Parse the last user message to extract text and images
                val lastUserMessage = messages.getJSONObject(messages.length() - 1)
                val content = lastUserMessage.getJSONArray("content")
                
                var textPrompt = ""
                val images = mutableListOf<Bitmap>()
                
                for (i in 0 until content.length()) {
                    val contentItem = content.getJSONObject(i)
                    val type = contentItem.getString("type")
                    
                    when (type) {
                        "text" -> {
                            textPrompt += contentItem.getString("text")
                        }
                        "image" -> {
                            val imageUrl = contentItem.getString("url")
                            // For now, we'll skip image URLs and focus on text
                            // TODO: Implement image URL downloading and processing
                            Log.d("EdgeAIHTTPServer", "Image URL found: $imageUrl (skipping for now)")
                        }
                    }
                }
                
                Log.d("EdgeAIHTTPServer", "Extracted text prompt: '$textPrompt'")
                Log.d("EdgeAIHTTPServer", "Found ${images.size} images")
                
                val result = runLlmTextGen(textPrompt, modelName)
                
                // Create response in the same format as the request
                val responseMessages = JSONArray()
                val assistantMessage = JSONObject()
                assistantMessage.put("role", "assistant")
                val assistantContent = JSONArray()
                val textContent = JSONObject()
                textContent.put("type", "text")
                textContent.put("text", result)
                assistantContent.put(textContent)
                assistantMessage.put("content", assistantContent)
                responseMessages.put(assistantMessage)
                
                val responseJson = JSONObject()
                responseJson.put("messages", responseMessages)
                
                Log.d("EdgeAIHTTPServer", "Chat response: ${responseJson.toString()}")
                val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                return response
            } else if (session.method == Method.POST && session.uri == "/edgeai_image") {
                Log.d("EdgeAIHTTPServer", "/edgeai_image route called")
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                Log.d("EdgeAIHTTPServer", "Request body: $jsonString")
                val json = JSONObject(jsonString)
                // Use a fixed default prompt regardless of what is sent
                val prompt = "Extract all key information about the person in the picture, but only if they exist: gender, face_shape, hair_color, hair_length, hair_style, eye_color, skin_tone, height, build, top_clothing, bottom_clothing, accessories, distinctive_features, age_range. Return the result as a JSON object with these fields. Do not include fields that are not visible or cannot be determined. Do not add any extra text."
                val modelName = json.optString("model", null)
                val imageBase64 = json.getString("image")
                // Log the first and last 100 characters of the base64 data for debugging
                val b64Preview = if (imageBase64.length > 200) {
                    imageBase64.take(100) + "..." + imageBase64.takeLast(100)
                } else {
                    imageBase64
                }
                Log.d("EdgeAIHTTPServer", "Received base64 image data (preview): $b64Preview (length: ${imageBase64.length})")
                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                // Log SHA-256 hash of the image bytes
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(imageBytes).joinToString("") { "%02x".format(it) }
                Log.d("EdgeAIHTTPServer", "SHA-256 of decoded image bytes: $hash")
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                Log.d("EdgeAIHTTPServer", "Decoded image dimensions: ${bitmap.width}x${bitmap.height}")
                // Resize bitmap to 256x256 for Gemma 3n
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                // Save bitmap to file
                val filename = "img_${System.currentTimeMillis()}.png"
                val file = File(context.filesDir, filename)
                val outputStream = FileOutputStream(file)
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
                
                // Debug: Check file size and dimensions
                Log.d("EdgeAIHTTPServer", "Saved image file size: ${file.length()} bytes")
                Log.d("EdgeAIHTTPServer", "Resized bitmap dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
                
                // Generate URL
                val imageUrl = "http://${getDeviceIpAddress()}:12345/images/$filename"
                Log.d("EdgeAIHTTPServer", "Generated image URL: $imageUrl")
                
                // Verify the file exists and is accessible
                if (file.exists()) {
                    Log.d("EdgeAIHTTPServer", "Image file exists and is accessible: ${file.absolutePath}")
                    Log.d("EdgeAIHTTPServer", "File size: ${file.length()} bytes")
                } else {
                    Log.e("EdgeAIHTTPServer", "Image file does not exist: ${file.absolutePath}")
                }
           
                // Allow temperature and top_p to be set via request
                val temperature = json.optDouble("temperature", Double.NaN)
                val topP = json.optDouble("top_p", Double.NaN)
                val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
                if (model == null) {
                    val responseJson = JSONObject()
                    responseJson.put("text", "No LLM model available")
                    val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                    return response
                }
                // Override config for this request if values provided
                val mutableConfig = model.configValues.toMutableMap()
                if (!temperature.isNaN()) mutableConfig["Temperature"] = temperature.toFloat()
                if (!topP.isNaN()) mutableConfig["TopP"] = topP.toFloat()
                model.configValues = mutableConfig
                // Step 1: Freeform description
                val freeformPrompt = "Analyze the person in this image and describe their visible appearance. Focus only on what is clearly visible. Describe: clothing, age range, hair color/style, eye color, skin tone, build, and any distinctive features. Avoid guessing gender, race, or other uncertain attributes. If something is unclear or not visible, respond with \"unknown\". url:$imageUrl"
                val freeformPromptOld = "Describe the person in the picture with as much detail as possible. url:$imageUrl"
                val freeformResult = runLlmTextGen(freeformPrompt, modelName)
                Log.d("EdgeAIHTTPServer", "Freeform description result: $freeformResult")

                // Step 2: Structured extraction
                val extractionPrompt = """Given the following description, extract all key information and return as a JSON object with these fields: gender, face_shape, hair_color, hair_length, hair_style, eye_color, skin_tone, height, build, top_clothing, bottom_clothing, accessories, distinctive_features, age_range. IMPORTANT: Only include fields that have clear, specific values. Do NOT include any fields that are "Not discernible", "avoided", "null", or similar uncertain values in the final JSON.\n\nDescription:\n$freeformResult"""
                val structuredResult = runLlmTextGen(extractionPrompt, modelName)
                Log.d("EdgeAIHTTPServer", "Structured extraction result: $structuredResult")

                val responseJson = JSONObject()
                responseJson.put("text", structuredResult)
                val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                return response
            } else if (session.method == Method.POST && session.uri == "/edgeai_image_url") {
                Log.d("EdgeAIHTTPServer", "/edgeai_image_url route called")
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                Log.d("EdgeAIHTTPServer", "Request body: $jsonString")
                val json = JSONObject(jsonString)
                val prompt = json.optString("prompt", "What is shown in this image?")
                val modelName = json.optString("model", null)
                val imageUrl = json.getString("imageUrl")
                // Use the working prompt format with URL
                val workingPrompt = "$prompt url:$imageUrl"
                Log.d("EdgeAIHTTPServer", "Using URL prompt format: '$workingPrompt'")
                val result = runLlmTextGen(workingPrompt, modelName)
                val responseJson = JSONObject()
                responseJson.put("text", result)
                val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                return response
            } else {
                Log.d("EdgeAIHTTPServer", "Unknown route: ${session.uri}")
                val response = NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
                response.addHeader("Access-Control-Allow-Origin", "*")
                response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                return response
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EdgeAIHTTPServer", "Error: ${e.message}")
            val response = NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
            return response
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
        Log.d("EdgeAIHTTPServer", "runLlmTextGen called with prompt: '$prompt'")
        val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
        if (model == null) return "No LLM model available"
        
        // Clean up any existing model instance to ensure fresh start
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up existing model instance")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model: ${e.message}")
        }
        
        val latch = CountDownLatch(1)
        var resultText = ""
        LlmChatModelHelper.initialize(context, model) { err ->
            if (err.isNotEmpty()) {
                resultText = err
                latch.countDown()
            } else {
                try {
                    LlmChatModelHelper.runInference(
                        model,
                        prompt,
                        resultListener = { partial, done ->
                            Log.d("EdgeAIHTTPServer", "ResultListener called: partial='$partial', done=$done")
                            resultText += partial // Accumulate the streaming response
                            if (done) {
                                Log.d("EdgeAIHTTPServer", "Inference completed with result: '$resultText'")
                                latch.countDown()
                            }
                        },
                        cleanUpListener = {
                            Log.d("EdgeAIHTTPServer", "CleanUpListener called")
                        },
                        images = listOf()
                    )
                } catch (e: Exception) {
                    Log.e("EdgeAIHTTPServer", "Error during inference: ${e.message}", e)
                    resultText = "Error during inference: ${e.message}"
                    latch.countDown()
                }
            }
        }
        latch.await(900, TimeUnit.SECONDS)
        
        // Clean up after inference to prevent memory leaks
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up model after inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model after inference: ${e.message}")
        }
        
        return resultText
    }

    private fun runLlmTextImageGen(prompt: String, bitmap: Bitmap, modelName: String?): String {
        val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
        if (model == null) return "No LLM model available"
        
        // Clean up any existing model instance to ensure fresh start
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up existing model instance for image inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model for image inference: ${e.message}")
        }
        
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
                        resultText = partial // Accumulate the complete response so far
                        if (done) {
                            latch.countDown()
                        }
                    },
                    cleanUpListener = {},
                    images = listOf(bitmap)
                )
            }
        }
        latch.await(900, TimeUnit.SECONDS)
        
        // Clean up after inference to prevent memory leaks
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up model after image inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model after image inference: ${e.message}")
        }
        
        return resultText
    }

    // Helper to get device's local IP address
    private fun getDeviceIpAddress(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
        return "127.0.0.1"
    }
} 