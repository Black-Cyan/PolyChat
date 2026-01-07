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
 * 用于与 OpenAl 兼容的聊天完成 API 进行交互的服务。
 *
 * <p>此服务使用 OpenAl API 格式提供流式和非流式聊天完成功能。
 * 所有方法都是线程安全的，可以从多个线程并发调用。
 *
 * <p>用法示例：
 * <pre>
 * OpenAIService service = new OpenAIService("https://api.openai.com", "your-api-key", "gpt-4");
 * // 在此处使用您自己的应用程序记录器，而不是 OpenAIService 的内部记录器
 * Logger logger = LoggerFactory.getLogger(YourApplication.class);
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
     * 创建一个新的 OpenAl 服务客户端。
     *
     * @param baseUrl API 端点的基本 URL（例如，"https://api.openai.com"）
     * @param apiKey 用于身份验证的 API 密钥
     * @param modelName 要使用的模型名称（例如，"gpt-4"）
     * @throws IllegalArgumentException 如果任何参数为 null 或为空
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
     * 表示具有角色和内容的聊天消息。
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
     * 用于流式聊天完成的回调接口。
     * 所有方法都在后台线程（OkHttp 的线程池）上调用。
     */
    public interface StreamCallback {
        /**
         * 收到响应块时调用。
         * 这在后台线程上调用。
         *
         * @param content 收到的内容块
         */
        void onChunk(String content);
        
        /**
         * 当流式响应完成时调用。
         * 这在后台线程上调用。
         */
        void onComplete();
        
        /**
         * 当流式传输期间发生错误时调用。
         * 这在后台线程上调用。
         *
         * @param e 发生的异常
         */
        void onError(Exception e);
    }

    /**
     * 发送带有流式支持的聊天完成请求
     * @param messages 对话历史记录
     * @param callback 用于处理流式响应的回调
     * @throws IllegalArgumentException 如果 messages 为 null 或为空
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
     * 发送非流式聊天完成请求
     * @param messages 对话历史记录
     * @return 完整的响应内容
     * @throws IOException 如果请求失败
     * @throws IllegalArgumentException 如果 messages 为 null 或为空
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
     * 关闭 HTTP 客户端并释放资源
     */
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
