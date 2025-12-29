package core.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenAIService {
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final OkHttpClient client;
    private final Gson gson;

    public OpenAIService(String baseUrl, String apiKey, String modelName) {
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

    public static class ChatMessage {
        public String role;
        public String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public interface StreamCallback {
        void onChunk(String content);
        void onComplete();
        void onError(Exception e);
    }

    /**
     * Send a chat completion request with streaming support
     * @param messages The conversation history
     * @param callback The callback for handling streamed responses
     */
    public void chatCompletionStream(List<ChatMessage> messages, StreamCallback callback) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("stream", true);
        
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role);
            msgObj.addProperty("content", msg.content);
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
                                    // Log and skip invalid JSON chunks
                                    System.err.println("Failed to parse SSE chunk: " + data + " - " + e.getMessage());
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
     */
    public String chatCompletion(List<ChatMessage> messages) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("stream", false);
        
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role);
            msgObj.addProperty("content", msg.content);
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
}
