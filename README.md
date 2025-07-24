# Google AI Edge Gallery (Android)

An Android application that provides on-device AI inference capabilities through HTTP endpoints.

## Architecture

This application implements a **hybrid architecture** that bridges web technologies with native Android AI capabilities:

### Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web App       │    │   Android        │    │   On-Device     │
│   (PWA)         │◄──►│   WebView        │◄──►│   AI Models     │
│                 │    │                  │    │                 │
│ • React/Vue     │    │ • HTTP Server    │    │ • Gemma LLM     │
│ • Modern UI     │    │ • NanoHTTPD      │    │ • MediaPipe     │
│ • Responsive    │    │ • Bridge Layer   │    │ • TensorFlow    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### How It Works

1. **Web Application Layer**: A Progressive Web App (PWA) provides the user interface
2. **Android WebView**: The PWA runs inside an Android WebView container
3. **HTTP Bridge**: The WebView communicates with native Android code via HTTP endpoints
4. **AI Inference**: Native Android code handles on-device AI model execution
5. **Response Flow**: AI results flow back through the HTTP bridge to the web interface

### Advantages

#### For Development
- **Rapid UI Development**: Use modern web frameworks (React, Vue, Angular) for fast UI iteration
- **Cross-Platform Potential**: Web code can potentially be reused for iOS or web versions
- **Rich Ecosystem**: Access to thousands of npm packages and web development tools
- **Hot Reloading**: Instant UI updates during development without rebuilding APK

#### For User Experience
- **Responsive Design**: Web technologies excel at adaptive layouts across different screen sizes
- **Modern UI/UX**: Easy to implement animations, transitions, and modern design patterns
- **Offline Capability**: PWA features like service workers provide offline functionality
- **Easy Updates**: Web content can be updated without app store approval

#### For AI Integration
- **Native Performance**: AI models run directly on Android hardware with full performance
- **Privacy**: All AI processing happens on-device, no data leaves the device
- **Low Latency**: No network round-trips for AI inference
- **Battery Efficient**: Optimized native code for AI operations

### Disadvantages

#### Technical Challenges
- **Bridge Complexity**: HTTP communication adds latency and potential points of failure
- **Memory Overhead**: WebView consumes additional memory compared to pure native apps
- **Debugging Complexity**: Need to debug both web and native layers
- **Version Synchronization**: Web and native code versions must be kept in sync

#### Performance Considerations
- **WebView Overhead**: WebView adds memory and CPU overhead compared to pure native
- **HTTP Latency**: Each AI request requires HTTP round-trip within the app
- **Large Bundle Size**: WebView + native code creates larger APK size

#### Development Trade-offs
- **Platform Limitations**: Some Android features require native code anyway
- **Testing Complexity**: Need to test both web and native components
- **Deployment Complexity**: Requires both web deployment and Android app store process

### When to Use This Architecture

#### Ideal Use Cases
- **AI-Powered Web Apps**: When you want to add on-device AI to existing web applications
- **Rapid Prototyping**: Quick iteration on AI features with modern web UI
- **Cross-Platform Strategy**: Planning to support multiple platforms eventually
- **Rich UI Requirements**: Complex user interfaces that benefit from web technologies

#### Alternative Approaches
- **Pure Native**: For maximum performance and minimal complexity
- **React Native**: For cross-platform with better native integration
- **Flutter**: For cross-platform with native performance
- **Pure Web**: When AI can be handled server-side

## Features

- On-device LLM inference using Gemma models
- HTTP server for external applications to interact with AI models
- Support for text generation and image analysis
- Real-time streaming responses

## API Endpoints

### Health Check
```
GET /health
```
Returns `OK` if the server is running.

### Text Generation
```
POST /edgeai
```
Generate text responses using the LLM.

**Request:**
```json
{
  "prompt": "Hello, how are you?",
  "model": "gemma3n_e4b_it"
}
```

**Response:**
```json
{
  "text": "I am doing well, thank you for asking! 😊..."
}
```

### Chat Interface
```
POST /edgeai_chat
```
Chat interface that supports conversation history and multiple content types.

**Request:**
```json
{
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "What is shown in this image? url:https://ai.google.dev/static/gemma/docs/images/thali-indian-plate.jpg"}
      ]
    }
  ],
  "model": "gemma3n_e4b_it"
}
```

**Response:**
```json
{
  "messages": [
    {
      "role": "assistant",
      "content": [
        {"type": "text", "text": "The image shows a **thali**, which is a traditional Indian meal..."}
      ]
    }
  ]
}
```

### Image Analysis
```
POST /edgeai_image
```
Analyze images using URLs or base64 encoded data.

**Using Image URL (Recommended):**
```json
{
  "imageUrl": "https://ai.google.dev/static/gemma/docs/images/thali-indian-plate.jpg",
  "model": "gemma3n_e4b_it"
}
```

**With Custom Prompt:**
```json
{
  "prompt": "Describe this image in detail",
  "imageUrl": "https://ai.google.dev/static/gemma/docs/images/thali-indian-plate.jpg",
  "model": "gemma3n_e4b_it"
}
```

**Using Base64 Image Data:**
```json
{
  "prompt": "What is shown in this image?",
  "image": "base64_encoded_image_data",
  "model": "gemma3n_e4b_it"
}
```

**Response:**
```json
{
  "text": "The image shows a traditional Indian thali..."
}
```

## Setup

1. Install the APK on your Android device
2. Start the app to launch the HTTP server
3. Use `adb forward tcp:12345 tcp:12345` to forward the port to your development machine
4. Access the API at `http://localhost:12345`

## Model Information

- **Model**: `gemma3n_e4b_it`
- **Type**: Text generation with image URL support
- **Location**: `/data/local/tmp/llm/gemma3n_e4b_it.task`

### Image Processing Capabilities

This model has an **unusual but effective** approach to image processing that differs from industry standards:

#### How It Works
- **URL-based image access**: The model can fetch and analyze images directly from URLs
- **No dedicated image tensors**: Unlike GPT-4V or Claude, it doesn't use separate image processing pipelines
- **Integrated approach**: Image analysis is built into the text generation process
- **Base64 limitations**: Currently doesn't support base64-encoded image data

#### Industry Standard vs This Model

| Feature | Industry Standard (GPT-4V, Claude) | This Model |
|---------|-----------------------------------|------------|
| **Image Input** | Dedicated image tensors + text | URL-based image fetching |
| **Model Architecture** | Explicit multimodal design | Integrated text+image processing |
| **Image Support** | Base64, file uploads, URLs | Primarily URLs |
| **Documentation** | Clear "supports images" labeling | Less explicit about capabilities |
| **Performance** | Optimized for image analysis | Text-first with image capability |

#### Why This Approach Works
- **Training**: Model was trained on web data including image URLs and their descriptions
- **Efficiency**: No need for separate image processing pipelines
- **Privacy**: Images are processed on-device without external services
- **Simplicity**: Single model handles both text and image tasks

#### Limitations
- **URL dependency**: Requires accessible image URLs
- **Base64 support**: Not currently implemented
- **Documentation**: Less clear about image capabilities compared to industry standards
- **Format restrictions**: May not support all image formats

## Usage Examples

### Basic Text Generation
```bash
curl -X POST http://localhost:12345/edgeai \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Hello, how are you?","model":"gemma3n_e4b_it"}'
```

### Image Analysis with URL
```bash
curl -X POST http://localhost:12345/edgeai_image \
  -H "Content-Type: application/json" \
  -d '{"imageUrl":"https://ai.google.dev/static/gemma/docs/images/thali-indian-plate.jpg","model":"gemma3n_e4b_it"}'
```

### Chat Interface
```bash
curl -X POST http://localhost:12345/edgeai_chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":[{"type":"text","text":"What is shown in this image? url:https://ai.google.dev/static/gemma/docs/images/thali-indian-plate.jpg"}]}],"model":"gemma3n_e4b_it"}'
```

## Notes

- The model supports image URLs when included in text prompts
- All endpoints support the `model` parameter (defaults to `gemma3n_e4b_it`)
- The server runs on port 12345 by default
- Use `adb forward` for external access to the device's HTTP server
