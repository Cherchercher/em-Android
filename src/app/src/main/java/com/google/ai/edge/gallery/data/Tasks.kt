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

package com.google.ai.edge.gallery.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mms
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Config
import com.google.ai.edge.gallery.data.ConfigKey
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Accelerator

/** Type of task. */
enum class TaskType(val label: String, val id: String) {
  LLM_CHAT(label = "AI Chat", id = "llm_chat"),
  LLM_PROMPT_LAB(label = "Prompt Lab", id = "llm_prompt_lab"),
  LLM_ASK_IMAGE(label = "Ask Image", id = "llm_ask_image"),
  LLM_ASK_AUDIO(label = "Audio Scribe", id = "llm_ask_audio"),
  TEST_TASK_1(label = "Test task 1", id = "test_task_1"),
  TEST_TASK_2(label = "Test task 2", id = "test_task_2"),
}

/** Data class for a task listed in home screen. */
data class Task(
  /** Type of the task. */
  val type: TaskType,

  /** Icon to be shown in the task tile. */
  val icon: ImageVector? = null,

  /** Vector resource id for the icon. This precedes the icon if both are set. */
  val iconVectorResourceId: Int? = null,

  /** List of models for the task. */
  val models: MutableList<Model>,

  /** Description of the task. */
  val description: String,

  /** Documentation url for the task. */
  val docUrl: String = "",

  /** Source code url for the model-related functions. */
  val sourceCodeUrl: String = "",

  /** Placeholder text for the name of the agent shown above chat messages. */
  @StringRes val agentNameRes: Int = R.string.chat_generic_agent_name,

  /** Placeholder text for the text input field. */
  @StringRes val textInputPlaceHolderRes: Int = R.string.chat_textinput_placeholder,

  // The following fields are managed by the app. Don't need to set manually.
  var index: Int = -1,
  val updateTrigger: MutableState<Long> = mutableLongStateOf(0),
)

val MODEL_GEMMA3N_E4B_IT = Model(
    name = "gemma3n_e4b_it", // Must match the file and the modelName used in requests
    downloadFileName = "gemma3n_e4b_it.task",
    url = "", // Local model, not downloaded
    sizeInBytes = 0L, // Not used for local
    configs = createLlmChatConfigs(
        defaultMaxToken = 1024,
        defaultTopK = 20, // Reduced for more factual output
        defaultTopP = 0.8f, // Reduced for more conservative sampling
        defaultTemperature = 0.2f, // Very low for maximum factual output
        accelerators = listOf(Accelerator.CPU, Accelerator.GPU)
    ), // Use proper LLM configuration
    llmSupportImage = true, // Enable image support
    imported = true // Mark as imported so getPath uses the correct path
)

val TASK_LLM_CHAT =
  Task(
    type = TaskType.LLM_CHAT,
    icon = Icons.Outlined.Forum,
    models = mutableListOf(),
    description = "Chat with on-device large language models",
    docUrl = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android",
    sourceCodeUrl =
      "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
    textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
  )

val TASK_LLM_PROMPT_LAB =
  Task(
    type = TaskType.LLM_PROMPT_LAB,
    icon = Icons.Outlined.Widgets,
    models = mutableListOf(),
    description = "Single turn use cases with on-device large language models",
    docUrl = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android",
    sourceCodeUrl =
      "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
    textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
  )

val TASK_LLM_ASK_IMAGE =
  Task(
    type = TaskType.LLM_ASK_IMAGE,
    icon = Icons.Outlined.Mms,
    models = mutableListOf(),
    description = "Ask questions about images with on-device large language models",
    docUrl = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android",
    sourceCodeUrl =
      "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
    textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
  )

val TASK_LLM_ASK_AUDIO =
  Task(
    type = TaskType.LLM_ASK_AUDIO,
    icon = Icons.Outlined.Mic,
    models = mutableListOf(),
    // TODO(do not submit)
    description =
      "Instantly transcribe and/or translate audio clips using on-device large language models",
    docUrl = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android",
    sourceCodeUrl =
      "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
    textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
  )

/** All tasks. */
val TASKS: List<Task> =
  listOf(TASK_LLM_ASK_IMAGE, TASK_LLM_ASK_AUDIO, TASK_LLM_PROMPT_LAB, TASK_LLM_CHAT)

fun getModelByName(name: String): Model? {
  for (task in TASKS) {
    for (model in task.models) {
      if (model.name == name) {
        return model
      }
    }
  }
  return null
}

fun processTasks() {
  for ((index, task) in TASKS.withIndex()) {
    task.index = index
    for (model in task.models) {
      model.preProcess()
    }
  }
}

fun useOnlyGemmaModel() {
    TASK_LLM_CHAT.models.clear()
    TASK_LLM_CHAT.models.add(MODEL_GEMMA3N_E4B_IT)
    TASK_LLM_PROMPT_LAB.models.clear()
    TASK_LLM_PROMPT_LAB.models.add(MODEL_GEMMA3N_E4B_IT)
    TASK_LLM_ASK_IMAGE.models.clear()
    TASK_LLM_ASK_IMAGE.models.add(MODEL_GEMMA3N_E4B_IT)
    TASK_LLM_ASK_AUDIO.models.clear()
    TASK_LLM_ASK_AUDIO.models.add(MODEL_GEMMA3N_E4B_IT)
    
    // Ensure the model configuration is processed
    MODEL_GEMMA3N_E4B_IT.preProcess()
}
