package top.myslzhao.mcbotchat;

import org.bukkit.plugin.java.JavaPlugin;
import top.myslzhao.mcbotchat.actions.AiChatManagerActions;
import top.myslzhao.mcbotchat.actions.ChatWithAiActions;
import top.myslzhao.mcbotchat.listener.PlayerListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;

public final class McBotChat extends JavaPlugin {
    private boolean hasDataFolder = true;
    private AiChatManagerActions aiChatManagerActions;

    @Override
    public void onLoad() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            try{
                if (!dataFolder.mkdirs()){
                    hasDataFolder = false;
                    throw new Exception();
                }
            }catch (Exception e){
                getLogger().log(Level.WARNING,
                        "Fail to create config folder, please check the path or the permission.");
            }
        }
    }

    /**
     * 启用插件，加载配置并打印启用信息。
     */
    @Override
    public void onEnable() {
        String apiKey = null;
        String systemPrompt = "";
        if (hasDataFolder){
            File configFile = new File(getDataFolder(), "config.cfg");

            if (!configFile.exists()){
                try{
                    configFile.createNewFile();

                    FileWriter writer = new FileWriter(configFile);
                    writer.write("#Set your deepseek api-key down here\n");
                    writer.write("api_key=sk-pleaseinputyourapikeyhere\n");
                    writer.write("#Set your own system prompt (or set to 'null' for using default prompt) here");
                    writer.write("system_prompt=null");
                    writer.close();
                    getLogger().log(Level.WARNING,
                            "A new config.cfg file has generated, please set your api-key!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                apiKey = props.getProperty("api_key");
                systemPrompt = props.getProperty("system_prompt");
                if(apiKey == null || apiKey.trim().isEmpty()) {
                    getLogger().severe("Api-key is empty! Please check your config.cfg.");
                } else {
                    getLogger().info("Api-key settings has loaded.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (Objects.equals(systemPrompt, "null")){
            aiChatManagerActions = new AiChatManagerActions(apiKey);
        } else {
            aiChatManagerActions = new AiChatManagerActions(apiKey, systemPrompt);
        }


        // 注册
        getServer().getPluginManager().registerEvents(new PlayerListener(aiChatManagerActions, this), this);

        // 介绍信息
        getLogger().info(">>> Bot Chat Start <<<");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
