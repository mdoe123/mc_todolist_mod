package com.todolistmod.store;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.todolistmod.model.Checklist;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 负责在游戏根目录下管理 {@code todolist} 文件夹，并加载其中的 .json 清单文件。
 */
public class ChecklistStore {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChatTodolist");
    private static final Gson GSON = new Gson();
    /** 上次扫描时目录的最后修改时间，用于检测是否需要重新加载 */
    private static FileTime lastDirModTime;
    /** 缓存的全量加载结果 */
    private static Map<String, Entry> cachedEntries;

    /** 首次运行时写入的通用演示清单，演示交互步骤、终止步骤与四种动作（print/run/jumpto/end）。 */
    private static final String EXAMPLE_JSON = """
{
  "name": "我的第一个清单",
  "description": "这是一个通用示例清单，演示交互步骤、终止步骤与四种动作。",
  "mcVersionMin": "1.21",
  "mcVersionMax": "1.21.1",
  "type": "flow",
  "maxSteps": 30,
  "tasks": [
    {
      "id": 1,
      "desc": "这是一个交互步骤示例。选择「继续」跳到下一步，选择「结束」直接终止清单。",
      "option": {
        "trueText": "继续",
        "falseText": "结束"
      },
      "trueDo": [
        {"type": "print", "text": "你选择了继续"},
        {"type": "jumpto", "id": 2}
      ],
      "falseDo": [
        {"type": "end", "message": "你选择了结束，清单已终止。"}
      ]
    },
    {
      "id": 2,
      "desc": "是否执行一条演示指令（将时间设为白天）？",
      "option": {
        "trueText": "执行",
        "falseText": "跳过"
      },
      "trueDo": [
        {"type": "run", "command": "/time set day"},
        {"type": "print", "text": "已将时间设为白天"},
        {"type": "jumpto", "id": 3}
      ],
      "falseDo": [
        {"type": "jumpto", "id": 3}
      ]
    },
    {
      "id": 3,
      "desc": "演示完毕。可用 /todolist edit 在浏览器打开编辑器修改或新建清单。",
      "option": null,
      "trueDo": [],
      "falseDo": []
    }
  ]
}
""";

    /** 游戏根目录下的 todolist 文件夹 */
    public static Path getDir() {
        return FabricLoader.getInstance().getGameDir().resolve("todolist");
    }

    /** 确保目录存在，并在首次创建时写入示例清单 */
    public static void ensureDir() {
        Path dir = getDir();
        try {
            Files.createDirectories(dir);
            Path example = dir.resolve("example.json");
            if (Files.notExists(example)) {
                Files.writeString(example, EXAMPLE_JSON);
                LOGGER.info("[ChatTodolist] 已写入示例清单: {}", example);
            }
        } catch (IOException e) {
            LOGGER.error("[ChatTodolist] 无法创建 todolist 目录", e);
        }
    }

    /** 一份清单与其源文件的绑定 */
    public static class Entry {
        /** 解析后的清单对象 */
        public final Checklist checklist;
        /** 清单源文件路径 */
        public final Path file;

        public Entry(Checklist checklist, Path file) {
            this.checklist = checklist;
            this.file = file;
        }
    }

    /** 重新扫描目录并加载全部 .json 清单，按清单 name 建立索引（重名时保留先出现的）。
     * <p>采用基于目录修改时间的缓存：仅当目录有变更时才重新扫描，避免每次 Tab 补全都触发全量 I/O。</p> */
    public static Map<String, Entry> loadAll() {
        Path dir = getDir();
        if (!Files.isDirectory(dir)) {
            cachedEntries = null;
            lastDirModTime = null;
            return new LinkedHashMap<>();
        }
        try {
            FileTime currentMod = Files.getLastModifiedTime(dir);
            if (cachedEntries != null && currentMod.equals(lastDirModTime)) {
                return cachedEntries;
            }
            lastDirModTime = currentMod;
        } catch (IOException e) {
            // 无法获取修改时间，回退到每次重新扫描
        }
        Map<String, Entry> map = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try (Reader reader = Files.newBufferedReader(p)) {
                    Checklist c = GSON.fromJson(reader, Checklist.class);
                    if (c != null && c.name != null && c.tasks != null) {
                        map.putIfAbsent(c.name, new Entry(c, p));
                    } else {
                        LOGGER.warn("[ChatTodolist] 清单缺少 name 或 tasks，已跳过: {}", p.getFileName());
                    }
                } catch (IOException | JsonSyntaxException e) {
                    LOGGER.error("[ChatTodolist] 解析清单失败: {}", p.getFileName(), e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("[ChatTodolist] 读取 todolist 目录失败", e);
        }
        cachedEntries = map;
        return map;
    }

    /** 按名称查找清单 */
    public static Entry find(String name) {
        return loadAll().get(name);
    }
}
