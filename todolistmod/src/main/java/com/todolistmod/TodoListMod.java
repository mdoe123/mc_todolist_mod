package com.todolistmod;

import com.todolistmod.command.TodoListCommand;
import com.todolistmod.store.ChecklistStore;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 聊天栏任务清单模组（客户端）。
 *
 * <p>启动时在游戏根目录创建 {@code todolist} 文件夹，玩家放入 .json 清单文件后，
 * 即可在聊天栏用 {@code /todolist} 系列指令查看、执行、结束清单，
 * 并通过点击聊天栏文字来确认每个步骤的选项。</p>
 */
public class TodoListMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChatTodolist");

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        ChecklistStore.ensureDir();
        TodoListCommand.register();
        LOGGER.info("[ChatTodolist] 已初始化。把 .json 清单放到 <游戏目录>/todolist/ ，然后用 /todolist list 查看。");
    }
}
