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

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKey
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.MAX_IMAGE_COUNT
import com.google.ai.edge.gallery.data.Model
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  fun initialize(context: Context, model: Model, onDone: (String) -> Unit) {
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKey.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKey.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    Log.d(TAG, "Initializing...")
    Log.d(TAG, "Model path: ${model.getPath(context = context)}")
    Log.d(TAG, "Model name: ${model.name}")
    Log.d(TAG, "Accelerator: $accelerator")
    
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> LlmInference.Backend.CPU
        Accelerator.GPU.label -> LlmInference.Backend.GPU
        else -> LlmInference.Backend.GPU
      }
    Log.d(TAG, "Preferred backend: $preferredBackend")
    
    val optionsBuilder =
      LlmInference.LlmInferenceOptions.builder()
        .setModelPath(model.getPath(context = context))
        .setMaxTokens(maxTokens)
        .setPreferredBackend(preferredBackend)
        .setMaxNumImages(if (model.llmSupportImage) MAX_IMAGE_COUNT else 0)
    val options = optionsBuilder.build()
    Log.d(TAG, "Options built successfully")

    // Create an instance of the LLM Inference task and session.
    try {
      Log.d(TAG, "Creating LlmInference from options...")
      val llmInference = LlmInference.createFromOptions(context, options)
      Log.d(TAG, "LlmInference created successfully")

      Log.d(TAG, "Creating LlmInferenceSession...")
      val session =
        LlmInferenceSession.createFromOptions(
          llmInference,
          LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTopP(topP)
            .setTemperature(temperature)
            .setGraphOptions(
              GraphOptions.builder()
                .setEnableVisionModality(model.llmSupportImage)
                .build()
            )
            .build(),
        )
      Log.d(TAG, "LlmInferenceSession created successfully")
      model.instance = LlmModelInstance(engine = llmInference, session = session)
      Log.d(TAG, "Model instance set successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize model: ${e.message}", e)
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    Log.d(TAG, "Initialization completed successfully")
    onDone("")
  }

  fun resetSession(model: Model) {
    try {
      Log.d(TAG, "Resetting session for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      val session = instance.session
      session.close()

      val inference = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val newSession =
        LlmInferenceSession.createFromOptions(
          inference,
          LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTopP(topP)
            .setTemperature(temperature)
            .setGraphOptions(
              GraphOptions.builder()
                .setEnableVisionModality(model.llmSupportImage)
                .build()
            )
            .build(),
        )
      instance.session = newSession
      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset session", e)
    }
  }

  fun cleanUp(model: Model) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.session.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference session: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
  ) {
    Log.d(TAG, "Starting inference for model '${model.name}' with input: '$input'")
    val instance = model.instance as LlmModelInstance

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    // Start async inference.
    //
    // For a model that supports image modality, we need to add the text query chunk before adding
    // image.
    val session = instance.session
    Log.d(TAG, "Adding query chunk: '$input'")
    if (input.trim().isNotEmpty()) {
      session.addQueryChunk(input)
    }
    for (image in images) {
      Log.d(TAG, "Adding image to session")
      session.addImage(BitmapImageBuilder(image).build())
    }
    for (audioClip in audioClips) {
      // Uncomment when audio is supported.
      // session.addAudio(audioClip)
    }
    Log.d(TAG, "Starting async response generation")
    val unused = session.generateResponseAsync(resultListener)
    Log.d(TAG, "Async response generation started")
  }
}
