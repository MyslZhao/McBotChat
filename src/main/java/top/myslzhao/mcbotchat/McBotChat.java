package top.myslzhao.mcbotchat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import top.myslzhao.mcbotchat.actions.AiChatManagerActions;
import top.myslzhao.mcbotchat.listener.PlayerListener;

import java.io.*;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class McBotChat extends JavaPlugin implements CommandExecutor {
    private boolean hasDataFolder = true;
    private String apiKey;
    private String systemPrompt;
    private AiChatManagerActions aiChatManagerActions;
    private PlayerListener playerListener;

    private void createConfig(File configFile) throws IOException{
        try (var writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#Set your deepseek api-key down here\n");
            writer.write("api_key=sk-pleaseinputyourapikeyhere\n");
            writer.write("#Set your own system prompt (or set to 'null' for using default prompt) here\n");
            writer.write("system_prompt=null\n");
        }
        getLogger().log(Level.WARNING,
                "A new config.cfg file has generated, please set your api-key!");
    }

    /**
     * 重载 api-key
     *
     * @param configFile 配置文件路径
     */
    private void loadApiKey(File configFile) {
        Properties props = new Properties();
        try (var reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            props.load(reader);
            apiKey = props.getProperty("api_key", "");
            if(apiKey == null || apiKey.trim().isEmpty()) {
                getLogger().severe("Api-key is empty! Please check your config.cfg.");
            } else {
                getLogger().info("Api-key settings has loaded.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 重载系统提示词
     *
     * @param configFile 配置文件路径
     */
    private void loadSystemPrompt(File configFile) {
        Properties props = new Properties();
        try (var reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            props.load(reader);
            systemPrompt = props.getProperty("system_prompt", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 重载配置
     */
    private void reloadPlugin() {
        apiKey = null;
        systemPrompt = "";

        if (hasDataFolder) {
            File configFile = new File(getDataFolder(), "config.cfg");
            if (!configFile.exists()) {
                getLogger().warning("Config.cfg wasn't here, please check your config.cfg.");
                return;
            }
            loadApiKey(configFile);
            loadSystemPrompt(configFile);
        }

        AiChatManagerActions newManager = createAiChatManager();

        if (playerListener != null) {
            playerListener.setAiChatManagerActions(newManager);
        }

        this.aiChatManagerActions = newManager;
        aiChatManagerActions.clearHistory();

        getLogger().info("Config reloaded.");
    }

    /**
     * 根据当前的 apiKey 和 systemPrompt 创建 AiChatManagerActions 实例
     */
    private AiChatManagerActions createAiChatManager() {
        if (Objects.equals(systemPrompt, "null")) {
            return new AiChatManagerActions(apiKey);
        } else {
            return new AiChatManagerActions(apiKey, systemPrompt);
        }
    }

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
        apiKey = null;
        systemPrompt = "";

        if (hasDataFolder){
            File configFile = new File(getDataFolder(), "config.cfg");

            if (!configFile.exists()){
                try{
                    createConfig(configFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            loadApiKey(configFile);
            loadSystemPrompt(configFile);
        }

        aiChatManagerActions = createAiChatManager();

        // 注册
        playerListener = new PlayerListener(aiChatManagerActions, this);
        getServer().getPluginManager().registerEvents(playerListener, this);

        // 介绍信息
        getLogger().info(">>> Bot Chat Start <<<");

        // 命令注册
        Objects.requireNonNull(getCommand("mcbotchat")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /mcbotchat reload");
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mcbotchat.reload")) {
                sender.sendMessage("You don't have the access to the command");
                return true;
            }
            reloadPlugin();
            sender.sendMessage("Settings reloaded.");
            return true;
        }

        sender.sendMessage("Unknown command.");
        return false;
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }
}
