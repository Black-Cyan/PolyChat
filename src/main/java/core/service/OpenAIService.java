package core.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with OpenAI-compatible chat completion APIs.
 * 
 * <p>This service provides streaming and non-streaming chat completions using the OpenAI API format.
 * All methods are thread-safe and can be called from multiple threads concurrently.
 * 
 * <p>Example usage:
 * <pre>
 * OpenAIService service = new OpenAIService("https://api.openai.com", "your-api-key", "gpt-4");
 * Logger logger = LoggerFactory.getLogger(YourClass.class);
 * List&lt;ChatMessage&gt; messages = List.of(
 *     new ChatMessage("user", "Hello!")
 * );
 * service.chatCompletionStream(messages, new StreamCallback() {
 *     public void onChunk(String content) { System.out.print(content); }
 *     public void onComplete() { System.out.println("\nDone!"); }
 *     public void onError(Exception e) { logger.error("Stream error", e); }
 * });
 * </pre>
 */
public class OpenAIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIService.class);

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final OkHttpClient client;
    private final Gson gson;

    /**
     * Creates a new OpenAI service client.
     * 
     * @param baseUrl The base URL of the API endpoint (e.g., "https://api.openai.com")
     * @param apiKey The API key for authentication
     * @param modelName The model name to use (e.g., "gpt-4")
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public OpenAIService(String baseUrl, String apiKey, String modelName) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl cannot be null or empty");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey cannot be null or empty");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("modelName cannot be null or empty");
        }
        
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Represents a chat message with a role and content.
     */
    public static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Callback interface for streaming chat completions.
     * All methods are called on a background thread (OkHttp's thread pool).
     */
    public interface StreamCallback {
        /**
         * Called when a chunk of the response is received.
         * This is invoked on a background thread.
         * 
         * @param content The content chunk received
         */
        void onChunk(String content);
        
        /**
         * Called when the streaming response is complete.
         * This is invoked on a background thread.
         */
        void onComplete();
        
        /**
         * Called when an error occurs during streaming.
         * This is invoked on a background thread.
         * 
         * @param e The exception that occurred
         */
        void onError(Exception e);
    }

    /**
     * Send a chat completion request with streaming support
     * @param messages The conversation history
     * @param callback The callback for handling streamed responses
     * @throws IllegalArgumentException if messages is null or empty
     */
    public void chatCompletionStream(List<ChatMessage> messages, StreamCallback callback) {
        if (messages == null || messages.isEmpty()) {
            callback.onError(new IllegalArgumentException("Messages list cannot be null or empty"));
            return;
        }
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("stream", true);
        
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.getRole());
            msgObj.addProperty("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);

        Request request = new Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        gson.toJson(requestBody),
                        MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code() + ": " + response.message()));
                    response.close();
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        callback.onError(new IOException("Empty response body"));
                        return;
                    }

                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(body.byteStream()))) {
                        
                        String line;
                        StringBuilder fullContent = new StringBuilder();
                        
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                
                                try {
                                    JsonObject chunk = gson.fromJson(data, JsonObject.class);
                                    JsonArray choices = chunk.getAsJsonArray("choices");
                                    if (choices != null && choices.size() > 0) {
                                        JsonObject choice = choices.get(0).getAsJsonObject();
                                        JsonObject delta = choice.getAsJsonObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.get("content").getAsString();
                                            fullContent.append(content);
                                            callback.onChunk(content);
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to parse SSE chunk: {}", data, e);
                                }
                            }
                        }
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Send a non-streaming chat completion request
     * @param messages The conversation history
     * @return The complete response content
     * @throws IOException If the request fails
     * @throws IllegalArgumentException if messages is null or empty
     */
    public String chatCompletion(List<ChatMessage> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages list cannot be null or empty");
        }
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("stream", false);
        
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.getRole());
            msgObj.addProperty("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);

        Request request = new Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        gson.toJson(requestBody),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }

            JsonObject responseJson = gson.fromJson(body.string(), JsonObject.class);
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null && message.has("content")) {
                    return message.get("content").getAsString();
                }
            }
            throw new IOException("Invalid response format");
        }
    }
    
    /**
     * Shutdown the HTTP client and release resources
     */
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
