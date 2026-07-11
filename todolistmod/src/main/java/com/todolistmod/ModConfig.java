package com.todolistmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置，读写 config/todolistmod.json。
 */
public class ModConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChatTodolist");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 加载后的配置实例（volatile 保证多线程可见性） */
    public static volatile ModConfig INSTANCE;

    /** 是否在 todolist 目录生成示例清单 */
    public boolean generateExample = true;
    /** 编辑器 HTTP 端口（0=自动分配） */
    public int editorPort = 0;
    /** 清单最大步骤数上限 */
    public int maxStepsLimit = 100;
    /** 界面语言（"system"/"zh_cn"/"en_us"） */
    public String language = "system";
    /** 是否对高危指令进行确认（true=启用，遇到 dangerousCommands 中的指令前缀时暂停并等待玩家确认） */
    public boolean dangerousCommandConfirm = true;
    /** 高危指令前缀列表（不区分大小写，不含前导 /）。空列表则不拦截任何命令 */
    public List<String> dangerousCommands = new ArrayList<>();

    /** 配置文件路径 */
    public static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("todolistmod.json");
    }

    /** 加载配置：文件不存在则创建默认，存在则读取并补全缺失字段，格式错误则回退默认 */
    public static ModConfig load() {
        Path path = getPath();
        ModConfig config;
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, ModConfig.class);
                if (config == null) config = new ModConfig();
                config.fillDefaults();
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[ChatTodolist] 配置文件解析失败，使用默认配置: {}", e.getMessage());
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
            save(config);
        }
        INSTANCE = config;
        return config;
    }

    /** 补全可能缺失的字段（向后兼容旧配置） */
    private void fillDefaults() {
        // Gson 对基本类型 int 不会为 null，但 String 可能为 null
        if (language == null) language = "system";
        if (dangerousCommands == null) dangerousCommands = new ArrayList<>();
        // 校验配置范围，非法值回退默认
        if (editorPort < 0 || editorPort > 65535) editorPort = 0;
        if (maxStepsLimit < 1) maxStepsLimit = 100;
        if (maxStepsLimit > 10000) maxStepsLimit = 10000;
    }

    /** 保存配置到文件 */
    public static void save(ModConfig config) {
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(config));
        } catch (IOException e) {
            LOGGER.warn("[ChatTodolist] 无法写入配置文件: {}", e.getMessage());
        }
    }
}
