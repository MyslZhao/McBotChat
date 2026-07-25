package top.myslzhao.mcbotchat.actions;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.myslzhao.mcbotchat.utils.ApiKeyStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

/**
 * ai聊天行为类，负责验证apiKey，发起请求与解析返回消息。
 *
 * @see AiChatManagerActions
 */
class ChatWithAiActions {
    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;

    private final CompletableFuture<ApiKeyStatus> validationFuture;

    public ChatWithAiActions(String apiKey) {
        this.apiKey = apiKey;
        this.validationFuture = validateKeyAsync();
    }

    /**
     * Future 验证api-key
     */
    public CompletableFuture<ApiKeyStatus> validateKeyAsync() {
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture(ApiKeyStatus.UNAUTHORIZED);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.deepseek.com/v1/models"))
                        .header("Authorization", "Bearer " + apiKey)
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = response.statusCode();
                switch (code) {
                    case 200:
                        return ApiKeyStatus.SUCCESS;
                    case 401:
                        return ApiKeyStatus.UNAUTHORIZED;
                    case 402:
                        return ApiKeyStatus.INSUFFICIENT_BALANCE;
                    default:
                        return ApiKeyStatus.OTHER_ERROR;
                }
            } catch (IOException | InterruptedException e) {
                return ApiKeyStatus.NETWORK_ERROR;
            }
        });
    }

    /**
     * 发送ai请求入口，包括进行验证。
     *
     * @param prompt 用户提示词
     * @return Deepseek返回信息
     * @throws IOException 数据异常
     * @throws InterruptedException 连接中断
     * @deprecated 不兼容历史管理，请使用 {@link #askWithMessages(List)} 发起请求
     */
    @Deprecated
    public CompletableFuture<String> ask(String prompt) throws IOException, InterruptedException {
        return validationFuture.thenCompose(status -> {
            if (status != ApiKeyStatus.SUCCESS) {
                // 根据状态生成对应的错误信息
                String errorMsg = switch (status) {
                    case UNAUTHORIZED -> "Api-key invalid, please contact with server admins.";
                    case INSUFFICIENT_BALANCE -> "Run out of money!( Please inform server admins.";
                    case NETWORK_ERROR -> "Request failed, please contact with server admins.";
                    case OTHER_ERROR -> "Unknown errors, please contact with server admins.";
                    default -> "Unexpected unknown errors!";
                };
                return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doRequest(prompt);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Request failed: " + e.getMessage(), e);
                } catch (Exception e) {
                    throw new RuntimeException("Service abnormal: " + e.getMessage(), e);
                }
            });
        });
    }

    /**
     * 发送ai请求入口，包括进行验证。
     *
     * @param messages 包含上下文的消息
     * @return ai返回消息
     * @see AiChatManagerActions
     */
    public CompletableFuture<String> askWithMessages(List<Map<String, String>> messages)
    {
        return validationFuture.thenCompose(
                status -> {
                    if (status != ApiKeyStatus.SUCCESS) {
                        String errorMsg = switch (status) {
                            case UNAUTHORIZED -> "Api-key invalid, please contact with server admins.";
                            case INSUFFICIENT_BALANCE -> "Run out of money! Please inform server admins.";
                            case NETWORK_ERROR -> "Request failed, please contact with server admins.";
                            case OTHER_ERROR -> "Unknown errors, please contact with server admins.";
                            default -> "Unexpected unknown errors!";
                        };
                        return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
                    }

                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            return doRequestWithMessages(messages);
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException("Request failed: " + e.getMessage(), e);
                        } catch (Exception e) {
                            throw new RuntimeException("Service abnormal: " + e.getMessage(), e);
                        }
                    });

                }
        );
    }

    /**
     * 发起ai请求。
     *
     * @param prompt 提示词
     * @return Deepseek返回消息
     * @throws IOException 数据异常
     * @throws InterruptedException 连接中断
     * @deprecated {@link #ask(String)}方法已弃用，请使用{@link #askWithMessages(List)}
     */
    @Deprecated
    private String doRequest(String prompt) throws IOException, InterruptedException {

        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-v4-flash");

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);
        body.add("messages", messages);

        String json = new Gson().toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String responseBody = response.body();

        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            if (jsonObject.has("error")) {
                String errorMsg = jsonObject.get("error").getAsJsonObject().get("message").getAsString();
                throw new RuntimeException("API error: " + errorMsg);
            }

            String content = jsonObject
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();
            return content;
        } catch (Exception e) {
            throw new RuntimeException("API unstandard reply: " + responseBody, e);
        }
    }

    private String doRequestWithMessages(List<Map<String, String>> messages) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-v4-flash");

        JsonArray msgArray = new JsonArray();
        for (Map<String, String> msg : messages) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", msg.get("role"));
            obj.addProperty("content", msg.get("content"));
            msgArray.add(obj);
        }
        body.add("messages", msgArray);

        String json = new Gson().toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String responseBody = response.body();

        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            if (jsonObject.has("error")) {
                String errorMsg = jsonObject.get("error").getAsJsonObject().get("message").getAsString();
                throw new RuntimeException("API error: " + errorMsg);
            }

            return jsonObject
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();
        } catch (Exception e) {
            throw new RuntimeException("API unstandard reply: " + responseBody, e);
        }
    }
}
