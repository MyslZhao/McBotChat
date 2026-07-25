package top.myslzhao.mcbotchat.listener;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;
import top.myslzhao.mcbotchat.actions.AiChatManagerActions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private AiChatManagerActions aiChatManagerActions;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 5000;

    public PlayerListener(AiChatManagerActions aiChatManagerActions, JavaPlugin plugin) {
        this.aiChatManagerActions = aiChatManagerActions;
        this.plugin = plugin;
    }

    /**
     * 玩家进入游戏时打印提示信息, 用于测试插件正常运行。
     *
     * @param event 玩家加入事件
     */
    @EventHandler
    public void onPlayerEnter(PlayerJoinEvent event){
        Bukkit.getServer().broadcast(Component.text("Ai>hello " + event.getPlayer().getName()));
    }

    /**
     * 玩家打出`>`前缀信息时调用 Ai 聊天。
     *
     * @param event 玩家发送消息事件
     */
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) throws IOException, InterruptedException {
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldown.containsKey(uuid) && now - cooldown.get(uuid) < COOLDOWN_MS) {
            event.getPlayer().sendMessage(Component.text("Frequent requests，please wait " + (COOLDOWN_MS / 1000) + " sec before next request."));
            return;
        }
        cooldown.put(uuid, now);

        Component msgComponent = event.originalMessage();
        String msgRowString = PlainTextComponentSerializer.plainText().serialize(msgComponent);

        if (!msgRowString.startsWith(">")){
            return;
        }

        event.setCancelled(true);

        String msgString = msgRowString.replaceFirst("^>", "");

        Component playerMsg = Component.text(event.getPlayer().getName() + ": " + msgString);
        Bukkit.getServer().broadcast(playerMsg);

        aiChatManagerActions.ask(event.getPlayer().getName(), msgString)
                .thenAcceptAsync(ans -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.getServer().broadcast(
                                Component.text(
                                        "< Ai>" + event.getPlayer().getName() + " > " + ans,
                                        TextColor.color(69, 151, 3)
                                )
                        );
                    });
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String errorMsg = cause.getMessage() != null ? cause.getMessage() : "Unknown Error";

                    TextColor color; // 默认红色
                    if (errorMsg.contains("money")) {
                        color = TextColor.color(255, 250, 71); // 黄色
                    } else {
                        color = TextColor.color(255, 0, 0);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.getServer().broadcast(
                                Component.text("Ai service: " + errorMsg, color)
                        );
                    });
                    Bukkit.getLogger().severe("Request failed: " + errorMsg);
                    return null;
                });

    }

    /**
     * 供 reload 时调用的 setter，替换内部的 AiChatManagerActions 实例
     */
    public void setAiChatManagerActions(AiChatManagerActions aiChatManagerActions) {
        this.aiChatManagerActions = aiChatManagerActions;
    }
}
