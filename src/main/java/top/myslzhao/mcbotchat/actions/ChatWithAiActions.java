package top.myslzhao.mcbotchat.actions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.myslzhao.mcbotchat.utils.ApiKeyStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChatWithAiActions {
    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;

    private ApiKeyStatus isValid;

    public ChatWithAiActions(String apiKey) {
        this.apiKey = apiKey;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/v1/models"))
                    .header("Authorization", "Bearer " + this.apiKey)
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            switch (statusCode) {
                case 200:
                    isValid = ApiKeyStatus.SUCCESS;
                    break;
                case 401:
                    isValid = ApiKeyStatus.UNAUTHORIZED;
                    break;
                case 402:
                    isValid = ApiKeyStatus.INSUFFICIENT_BALANCE;
                    break;
                default:
                    isValid = ApiKeyStatus.OTHER_ERROR;
            }
        } catch (IOException | InterruptedException e) {
            isValid = ApiKeyStatus.NETWORK_ERROR;
        }
    }

    public String ask(String prompt) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        String json = String.format("""
            {
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": "%s"}]
            }
        """, prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body();
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

        // 检查是否有错误（DeepSeek 返回错误时会有 error 字段）
        if (jsonObject.has("error")) {
            String errorMsg = jsonObject.get("error").getAsJsonObject().get("message").getAsString();
            throw new RuntimeException("API 错误: " + errorMsg);
        }

        // 提取 content
        String content = jsonObject
                .getAsJsonArray("choices")          // 拿到 choices 数组
                .get(0)                             // 取第一个元素
                .getAsJsonObject()                  // 转为对象
                .getAsJsonObject("message")         // 取 message
                .get("content")                     // 取 content
                .getAsString();                     // 转为字符串

        return content; // 直接返回纯文本，上层直接显示就行
    }

    public ApiKeyStatus getIsValid() {
        return isValid;
    }
}
