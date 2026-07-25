package top.myslzhao.mcbotchat.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 上下文管理类,负责构造消息，维护历史。
 */
public class AiChatManagerActions {
    private final List<Map<String, String>> history =
            Collections.synchronizedList(new ArrayList<>());
    private final ChatWithAiActions chatWithAiActions;
    private static final int MAX_HISTORY_SIZE = 20;

    private final String systemPrompt;


    public AiChatManagerActions(String apiKey){
        this.chatWithAiActions = new ChatWithAiActions(apiKey);
        this.systemPrompt =
                "你是一个在 Minecraft 服务器中提供帮助的 AI 助手。" +
                        "所有玩家可以与你聊天，你需要记住之前的对话内容。" +
                        "请用中文回答。";
    }

    public AiChatManagerActions(String apiKey, String systemPrompt){
        this.chatWithAiActions = new ChatWithAiActions(apiKey);
        this.systemPrompt = systemPrompt; // 后续加入 config.cfg自行设置
    }

    /**
     * 添加用户请求历史消息
     * @param content 请求消息
     */
    public void addUserMessage(String playerName, String content) {
        String formatted = "[" + playerName + "]" + content;
        history.add(Map.of("role", "user", "content", formatted));
        trimHistory();
    }

    /**
     * 添加 Ai返回历史消息
     * @param content 返回消息
     */
    public void addAssistantMessage(String content) {
        history.add(Map.of("role", "assistant", "content", content));
        trimHistory();
    }

    /**
     * 返回当前的历史消息
     */
    public List<Map<String, String>> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /**
     * 删除超出队列的历史消息
     */
    private void trimHistory() {
        synchronized (history) {
            while (history.size() > MAX_HISTORY_SIZE) {
                history.removeFirst();
            }
        }
    }

    /**
     * 构造发送消息。
     *
     * @param currentPlayerName 玩家名字
     * @param currentMessage 玩家请求消息
     * @return 包含历史消息的消息列表
     */
    public List<Map<String, String>> buildMessage(String currentPlayerName, String currentMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of("role", "system", "content", systemPrompt));

        synchronized (history) {
            messages.addAll(new ArrayList<>(history));
        }

        String formatted = "[" + currentPlayerName + "]" + currentMessage;
        messages.add(Map.of("role", "user", "content", formatted));

        return messages;
    }

    /**
     * Ai 请求入口
     * @param playerName 玩家名称
     * @param message 玩家请求信息
     * @return Ai返回信息
     * @see ChatWithAiActions#askWithMessages(List)
     */
    public CompletableFuture<String> ask(String playerName, String message) {
        addUserMessage(playerName, message);

        List<Map<String, String>> fullMessages = buildMessage(playerName, message);

        return chatWithAiActions.askWithMessages(fullMessages)
                .thenApply(
                        response -> {
                            addAssistantMessage(response);
                            return response;
                        }
                )
                .exceptionally(
                        ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            throw new RuntimeException(cause.getMessage(), cause);
                        }
                );
    }

    /**
     * 清除历史消息
     */
    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }
}
