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
import com.google.ai.edge.gallery.data.Model

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
                Log.d("EdgeAIHTTPServer", "/edgeai_image route called - Multi-step gallery-style processing (text-only structuring)")
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                Log.d("EdgeAIHTTPServer", "Request body: $jsonString")
                val json = JSONObject(jsonString)
                
                // Get parameters
                val customPrompt = json.optString("prompt", "Describe this image in detail.")
                val modelName = json.optString("model", null)
                val imageBase64 = json.getString("image")
                
                // Log base64 data for debugging
                val b64Preview = if (imageBase64.length > 200) {
                    imageBase64.take(100) + "..." + imageBase64.takeLast(100)
                } else {
                    imageBase64
                }
                Log.d("EdgeAIHTTPServer", "Received base64 image data (preview): $b64Preview (length: ${imageBase64.length})")
                
                // Decode base64 to bitmap
                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(imageBytes).joinToString("") { "%02x".format(it) }
                Log.d("EdgeAIHTTPServer", "SHA-256 of decoded image bytes: $hash")
                
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    Log.e("EdgeAIHTTPServer", "Failed to decode bitmap from base64 data")
                    val responseJson = JSONObject()
                    responseJson.put("text", "Error: Failed to decode image")
                    val response = NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "application/json", responseJson.toString())
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                }
                
                Log.d("EdgeAIHTTPServer", "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Get model
                val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
                if (model == null) {
                    val responseJson = JSONObject()
                    responseJson.put("text", "No LLM model available")
                    val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                }
                
                // Log model configuration
                Log.d("EdgeAIHTTPServer", "Using model: ${model.name}")
                Log.d("EdgeAIHTTPServer", "Model supports image: ${model.llmSupportImage}")
                Log.d("EdgeAIHTTPServer", "Model config: ${model.configValues}")
                
                // Allow temperature and top_p to be set via request
                val temperature = json.optDouble("temperature", Double.NaN)
                val topP = json.optDouble("top_p", Double.NaN)
                val topK = json.optInt("topK", -1)
                val maxTokens = json.optInt("maxTokens", -1)
                
                // Override config for this request if values provided
                val mutableConfig = model.configValues.toMutableMap()
                if (!temperature.isNaN()) mutableConfig["Temperature"] = temperature.toFloat()
                if (!topP.isNaN()) mutableConfig["TopP"] = topP.toFloat()
                if (topK != -1) mutableConfig["TopK"] = topK
                if (maxTokens != -1) mutableConfig["MaxTokens"] = maxTokens
                model.configValues = mutableConfig
                
                Log.d("EdgeAIHTTPServer", "Updated model config: ${model.configValues}")
                
                // Step 1: Freeform description using gallery-style inference
                val freeformPrompt = customPrompt
                Log.d("EdgeAIHTTPServer", "Step 1: Freeform description with prompt: '$freeformPrompt'")
                val freeformResult = runGalleryStyleInference(model, freeformPrompt, listOf(bitmap))
                Log.d("EdgeAIHTTPServer", "Step 1 result: '$freeformResult'")
                
                // Step 2: Structured extraction using text-only inference
                val extractionPrompt = """Given the following description, extract all key information and return as a JSON object with these fields: gender, face_shape, hair_color, hair_length, hair_style, eye_color, skin_tone, height, build, top_clothing, bottom_clothing, accessories, distinctive_features, age_range. IMPORTANT: Only include fields that have clear, specific values. Do NOT include any fields that are 'Not discernible', 'avoided', 'null', or similar uncertain values in the final JSON.\n\nDescription:\n$freeformResult"""
                Log.d("EdgeAIHTTPServer", "Step 2: Structured extraction with prompt: '$extractionPrompt'")
                val structuredResult = runGalleryStyleInference(model, extractionPrompt, listOf()) // No image for step 2
                Log.d("EdgeAIHTTPServer", "Step 2 result: '$structuredResult'")
                
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
            } else if (session.method == Method.POST && session.uri == "/edgeai_image_direct") {
                Log.d("EdgeAIHTTPServer", "/edgeai_image_direct route called - Gallery-style processing")
                val body = HashMap<String, String>()
                session.parseBody(body)
                val jsonString = body["postData"] ?: ""
                Log.d("EdgeAIHTTPServer", "Request body: $jsonString")
                val json = JSONObject(jsonString)
                
                // Get parameters like gallery app
                val customPrompt = json.optString("prompt", "Describe this image in detail.")
                val modelName = json.optString("model", null)
                val imageBase64 = json.getString("image")
                
                // Log base64 data for debugging
                val b64Preview = if (imageBase64.length > 200) {
                    imageBase64.take(100) + "..." + imageBase64.takeLast(100)
                } else {
                    imageBase64
                }
                Log.d("EdgeAIHTTPServer", "Received base64 image data (preview): $b64Preview (length: ${imageBase64.length})")
                
                // Decode base64 to bitmap (like gallery app loads from URI)
                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(imageBytes).joinToString("") { "%02x".format(it) }
                Log.d("EdgeAIHTTPServer", "SHA-256 of decoded image bytes: $hash")
                
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    Log.e("EdgeAIHTTPServer", "Failed to decode bitmap from base64 data")
                    val responseJson = JSONObject()
                    responseJson.put("text", "Error: Failed to decode image")
                    val response = NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "application/json", responseJson.toString())
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                }
                
                Log.d("EdgeAIHTTPServer", "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Get model like gallery app
                val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
                if (model == null) {
                    val responseJson = JSONObject()
                    responseJson.put("text", "No LLM model available")
                    val response = NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString())
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    return response
                }
                
                // Log model configuration like gallery app
                Log.d("EdgeAIHTTPServer", "Using model: ${model.name}")
                Log.d("EdgeAIHTTPServer", "Model supports image: ${model.llmSupportImage}")
                Log.d("EdgeAIHTTPServer", "Model config: ${model.configValues}")
                
                // Process like gallery app - direct bitmap inference
                val result = runGalleryStyleInference(model, customPrompt, listOf(bitmap))
                
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
        Log.d("EdgeAIHTTPServer", "runLlmTextImageGen called with prompt: '$prompt'")
        Log.d("EdgeAIHTTPServer", "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
        Log.d("EdgeAIHTTPServer", "Bitmap config: ${bitmap.config}")
        
        val model = if (modelName != null) getModelByName(modelName) else getDefaultLlmModel()
        if (model == null) {
            Log.e("EdgeAIHTTPServer", "No LLM model available")
            return "No LLM model available"
        }
        
        Log.d("EdgeAIHTTPServer", "Using model: ${model.name}")
        Log.d("EdgeAIHTTPServer", "Model supports image: ${model.llmSupportImage}")
        Log.d("EdgeAIHTTPServer", "Model config: ${model.configValues}")
        
        // Clean up any existing model instance to ensure fresh start
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up existing model instance for image inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model for image inference: ${e.message}")
        }
        
        val latch = CountDownLatch(1)
        var resultText = ""
        
        Log.d("EdgeAIHTTPServer", "Initializing model for image inference...")
        LlmChatModelHelper.initialize(context, model) { err ->
            if (err.isNotEmpty()) {
                Log.e("EdgeAIHTTPServer", "Model initialization failed: $err")
                resultText = err
                latch.countDown()
            } else {
                Log.d("EdgeAIHTTPServer", "Model initialized successfully, starting image inference")
                try {
                    LlmChatModelHelper.runInference(
                        model,
                        prompt,
                        resultListener = { partial, done ->
                            Log.d("EdgeAIHTTPServer", "Image inference result listener: partial='$partial', done=$done")
                            resultText += partial // Accumulate the complete response so far
                            if (done) {
                                Log.d("EdgeAIHTTPServer", "Image inference completed with result: '$resultText'")
                                latch.countDown()
                            }
                        },
                        cleanUpListener = {
                            Log.d("EdgeAIHTTPServer", "Image inference cleanUpListener called")
                        },
                        images = listOf(bitmap)
                    )
                } catch (e: Exception) {
                    Log.e("EdgeAIHTTPServer", "Error during image inference: ${e.message}", e)
                    resultText = "Error during inference: ${e.message}"
                    latch.countDown()
                }
            }
        }
        
        Log.d("EdgeAIHTTPServer", "Waiting for image inference to complete...")
        latch.await(900, TimeUnit.SECONDS)
        
        // Clean up after inference to prevent memory leaks
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up model after image inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model after image inference: ${e.message}")
        }
        
        Log.d("EdgeAIHTTPServer", "runLlmTextImageGen returning: '$resultText'")
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

    // Gallery-style inference that mimics exactly how the gallery app processes images
    private fun runGalleryStyleInference(model: Model, input: String, images: List<Bitmap>): String {
        Log.d("EdgeAIHTTPServer", "runGalleryStyleInference called - mimicking gallery app")
        Log.d("EdgeAIHTTPServer", "Input text: '$input'")
        Log.d("EdgeAIHTTPServer", "Number of images: ${images.size}")
        
        // Log each image's properties
        for ((index, image) in images.withIndex()) {
            Log.d("EdgeAIHTTPServer", "Image $index: ${image.width}x${image.height}, config: ${image.config}")
        }
        
        // Clean up any existing model instance to ensure fresh start (like gallery app)
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up existing model instance for gallery-style inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model for gallery-style inference: ${e.message}")
        }
        
        val latch = CountDownLatch(1)
        var resultText = ""
        
        // Initialize model like gallery app
        LlmChatModelHelper.initialize(context, model) { err ->
            if (err.isNotEmpty()) {
                Log.e("EdgeAIHTTPServer", "Model initialization failed: $err")
                resultText = "Model initialization failed: $err"
                latch.countDown()
            } else {
                Log.d("EdgeAIHTTPServer", "Model initialized successfully, starting inference")
                
                // Run inference exactly like gallery app
                try {
                    LlmChatModelHelper.runInference(
                        model = model,
                        input = input,
                        images = images,
                        audioClips = listOf(), // No audio for this endpoint
                        resultListener = { partialResult, done ->
                            Log.d("EdgeAIHTTPServer", "Gallery-style result listener: partial='$partialResult', done=$done")
                            resultText += partialResult // Accumulate the streaming response like gallery app
                            if (done) {
                                Log.d("EdgeAIHTTPServer", "Gallery-style inference completed with result: '$resultText'")
                                latch.countDown()
                            }
                        },
                        cleanUpListener = {
                            Log.d("EdgeAIHTTPServer", "Gallery-style cleanUpListener called")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("EdgeAIHTTPServer", "Error during gallery-style inference: ${e.message}", e)
                    resultText = "Error during inference: ${e.message}"
                    latch.countDown()
                }
            }
        }
        
        // Wait for completion like gallery app
        latch.await(900, TimeUnit.SECONDS)
        
        // Clean up after inference to prevent memory leaks (like gallery app)
        try {
            LlmChatModelHelper.cleanUp(model)
            Log.d("EdgeAIHTTPServer", "Cleaned up model after gallery-style inference")
        } catch (e: Exception) {
            Log.e("EdgeAIHTTPServer", "Failed to clean up model after gallery-style inference: ${e.message}")
        }
        
        return resultText
    }
} 