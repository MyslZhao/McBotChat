package top.myslzhao.mcbotchat.listener;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;
import top.myslzhao.mcbotchat.actions.ChatWithAiActions;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import top.myslzhao.mcbotchat.utils.ApiKeyStatus;

import java.io.IOException;

public class PlayerListener implements Listener {
    private final ChatWithAiActions chatWithAiActions;
    private final JavaPlugin plugin;

    public PlayerListener(ChatWithAiActions chatWithAiActions, JavaPlugin plugin) {
        this.chatWithAiActions = chatWithAiActions;
        this.plugin = plugin;
    }

    /**
     * 玩家进入游戏时打印提示信息。
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
    public void onPlayerChat(AsyncChatEvent event){

        Component msgComponent = event.originalMessage();
        String msgRowString = PlainTextComponentSerializer.plainText().serialize(msgComponent);

        if (!msgRowString.startsWith(">")){
            return;
        }

        event.setCancelled(true);

        String msgString = msgRowString.replaceFirst("^>", "");

        Component playerMsg = Component.text(event.getPlayer().getName() + ": " + msgString);
        Bukkit.getServer().broadcast(playerMsg);

        ApiKeyStatus status = chatWithAiActions.getIsValid();
        switch(status){

            case SUCCESS -> {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try{
                        String ans = chatWithAiActions.ask(msgString);

                        Bukkit.getScheduler().runTask(plugin, () ->{

                            Bukkit.getServer().broadcast(
                                    Component.text(
                                            "Ai>" + event.getPlayer().getName() + ":" + ans,
                                            TextColor.color(69, 151, 3)
                                    )
                            );
                        });
                    } catch (InterruptedException | IOException e) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            Bukkit.getServer().broadcast(
                                    Component.text(
                                            "Request timeout, please contact with server admins.",
                                            TextColor.color(255, 0, 0)
                                    )
                            );
                            Bukkit.getLogger().severe("Request timeout, Please check server's connection between deepseek.com .");
                        });
                    }
                });
            }

            case UNAUTHORIZED -> {
                Bukkit.getServer().broadcast(
                        Component.text(
                                "Api-key invalid, please contact with server admins.",
                                TextColor.color(255, 0, 0)
                        )
                );
                Bukkit.getLogger().severe("Api-key invalid, please check your config.cfg settings.");
            }

            case INSUFFICIENT_BALANCE -> {
                Bukkit.getServer().broadcast(
                        Component.text(
                                "Run out of money!(",
                                TextColor.color(255, 250, 71)
                        )
                );
                Bukkit.getLogger().severe("Api-key token insufficient, please pay for more tokens.");
            }

            case NETWORK_ERROR -> {
                Bukkit.getServer().broadcast(
                        Component.text(
                                "Request failed, please contact with server admins.",
                                TextColor.color(255, 0, 0)
                        )
                );
                Bukkit.getLogger().severe("Unreachable, please check server's connection between deepseek.com .");
            }

            case OTHER_ERROR -> {
                Bukkit.getServer().broadcast(
                        Component.text(
                                "Unknown errors, please contact with server admins.",
                                TextColor.color(255, 0, 0)
                        )
                );
                Bukkit.getLogger().severe("Unknown errors, please feedback a copy of complete logs to developers, thanks.");
            }

        }

    }
}
