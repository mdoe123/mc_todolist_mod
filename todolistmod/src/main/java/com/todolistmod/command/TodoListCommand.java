package com.todolistmod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.todolistmod.editor.ChecklistEditorServer;
import com.todolistmod.exec.ChecklistExecutor;
import com.todolistmod.exec.ClickTokens;
import com.todolistmod.exec.PendingChoice;
import com.todolistmod.model.Checklist;
import com.todolistmod.model.ChecklistTask;
import com.todolistmod.store.ChecklistStore;
import com.todolistmod.store.ChecklistStore.Entry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册客户端指令 {@code /todolist}，提供 list / edit / do / end / is 子命令，
 * 以及供按钮点击使用的内部子命令 {@code _click}。
 */
public class TodoListCommand {
    /** 正在执行的清单：name -> 执行器 */
    private static final Map<String, ChecklistExecutor> RUNNING = new ConcurrentHashMap<>();

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("todolist")
                    .then(ClientCommandManager.literal("list").executes(TodoListCommand::runList))
                    .then(ClientCommandManager.literal("is").executes(TodoListCommand::runIs))
                    .then(ClientCommandManager.literal("do")
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .suggests(ChecklistSuggestionProvider.INSTANCE)
                                    .executes(TodoListCommand::runDo)))
                    .then(ClientCommandManager.literal("end")
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .suggests(ChecklistSuggestionProvider.INSTANCE)
                                    .executes(TodoListCommand::runEnd)))
                    .then(ClientCommandManager.literal("back")
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .suggests(ChecklistSuggestionProvider.INSTANCE)
                                    .executes(TodoListCommand::runBack)))
                    .then(ClientCommandManager.literal("edit")
                            .executes(TodoListCommand::runEditNoName)
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .suggests(ChecklistSuggestionProvider.INSTANCE)
                                    .executes(TodoListCommand::runEdit)))
                    .then(ClientCommandManager.literal("_click")
                            .then(ClientCommandManager.argument("token", StringArgumentType.string())
                                    .executes(TodoListCommand::runClick)))
            );
        });
    }

    /**
     * 列出所有清单并在聊天栏显示，点击清单名即可执行。
     *
     * @param ctx 命令上下文
     * @return 清单数量
     */
    private static int runList(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        Map<String, Entry> all = ChecklistStore.loadAll();
        if (all.isEmpty()) {
            src.sendFeedback(Text.literal("未找到任何清单。请将 .json 清单文件放入游戏目录下的 todolist 文件夹。")
                    .formatted(Formatting.YELLOW));
            return 0;
        }
        src.sendFeedback(Text.literal("共 " + all.size() + " 个清单（点击名称即可执行）：").formatted(Formatting.GOLD));
        for (Entry e : all.values()) {
            String name = e.checklist.name;
            int n = e.checklist.tasks == null ? 0 : e.checklist.tasks.size();
            MutableText line = Text.literal(" - ").formatted(Formatting.GRAY);
            line.append(Text.literal(name).setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/todolist do " + name))
                    .withColor(Formatting.AQUA)));
            line.append(Text.literal("  (" + n + " 步)").formatted(Formatting.DARK_GRAY));
            src.sendFeedback(line);
        }
        return all.size();
    }

    /**
     * 查询当前正在执行的清单及其进度。
     *
     * @param ctx 命令上下文
     * @return 正在执行的清单数量
     */
    private static int runIs(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        if (RUNNING.isEmpty()) {
            src.sendFeedback(Text.literal("当前没有正在执行的清单。").formatted(Formatting.YELLOW));
            return 0;
        }
        for (ChecklistExecutor exec : RUNNING.values()) {
            Checklist cl = exec.getChecklist();
            ChecklistTask t = exec.getCurrentTask();
            String status;
            if (exec.isFinished()) {
                status = "已结束";
            } else {
                int total = cl.tasks == null ? 0 : cl.tasks.size();
                status = "进行中: 步骤 id " + (t == null ? "?" : t.id) + "/" + total
                        + "，已跳转 " + exec.getStepCount() + "/" + cl.maxSteps;
            }
            src.sendFeedback(Text.literal(" - " + cl.name + "  [" + status + "]").formatted(Formatting.AQUA));
        }
        return RUNNING.size();
    }

    /**
     * 执行指定名称的清单，若该清单已在执行则提示先 end。
     *
     * @param ctx 命令上下文
     * @return 1 表示已开始执行，0 表示未执行（已存在或未找到）
     */
    private static int runDo(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        if (RUNNING.containsKey(name)) {
            src.sendFeedback(Text.literal("清单 [" + name + "] 已在执行中，请先用 /todolist end " + name + " 结束。")
                    .formatted(Formatting.YELLOW));
            return 0;
        }
        Entry entry = ChecklistStore.find(name);
        if (entry == null) {
            src.sendFeedback(Text.literal("未找到清单: " + name + "。使用 /todolist list 查看可用清单。")
                    .formatted(Formatting.RED));
            return 0;
        }
        ChecklistExecutor exec = new ChecklistExecutor(entry.checklist);
        RUNNING.put(name, exec);
        MinecraftClient client = src.getClient();
        client.execute(() -> {
            exec.start();
            if (exec.isFinished()) {
                RUNNING.remove(name);
            }
        });
        return 1;
    }

    /**
     * 强制结束指定名称的清单。
     *
     * @param ctx 命令上下文
     * @return 1 表示已结束，0 表示无正在执行的清单
     */
    private static int runEnd(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        ChecklistExecutor exec = RUNNING.remove(name);
        if (exec == null) {
            src.sendFeedback(Text.literal("没有正在执行的清单: " + name).formatted(Formatting.YELLOW));
            return 0;
        }
        ClickTokens.clearForExecutor(exec);
        src.sendFeedback(Text.literal("已结束清单: " + name).formatted(Formatting.GREEN));
        return 1;
    }

    /**
     * 返回上一步（仅回退交互界面，已执行的副作用不撤销）。
     *
     * @param ctx 命令上下文
     * @return 1 表示已回退，0 表示无正在执行的清单
     */
    private static int runBack(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        ChecklistExecutor exec = RUNNING.get(name);
        if (exec == null) {
            src.sendFeedback(Text.literal("没有正在执行的清单: " + name).formatted(Formatting.YELLOW));
            return 0;
        }
        MinecraftClient client = src.getClient();
        client.execute(() -> {
            exec.back();
            if (exec.isFinished()) {
                RUNNING.remove(name);
            }
        });
        return 1;
    }

    /**
     * /todolist edit 无参版：打开编辑器，不预选任何清单。
     *
     * @param ctx 命令上下文
     * @return 固定返回 1
     */
    private static int runEditNoName(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        ChecklistEditorServer.ensureStarted();
        String url = ChecklistEditorServer.baseUrl() + "/";
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                src.sendFeedback(Text.literal("已在浏览器打开编辑器。").formatted(Formatting.GREEN));
            } else {
                src.sendFeedback(Text.literal("无法自动打开浏览器，请手动访问: " + url)
                        .formatted(Formatting.YELLOW));
            }
        } catch (Exception e) {
            src.sendFeedback(Text.literal("打开浏览器失败: " + e.getMessage() + "，请手动访问: " + url)
                    .formatted(Formatting.YELLOW));
        }
        return 1;
    }

    /**
     * /todolist edit &lt;name&gt;：打开编辑器并预选指定清单。
     *
     * @param ctx 命令上下文
     * @return 固定返回 1
     */
    private static int runEdit(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        Entry entry = ChecklistStore.find(name);
        // 启动编辑器服务器（若未运行）
        ChecklistEditorServer.ensureStarted();
        String url = ChecklistEditorServer.baseUrl() + "/";
        if (entry != null) {
            url += "?file=" + URLEncoder.encode(entry.file.getFileName().toString(), StandardCharsets.UTF_8);
            src.sendFeedback(Text.literal("清单文件路径: " + entry.file.toAbsolutePath())
                    .formatted(Formatting.AQUA));
        } else {
            src.sendFeedback(Text.literal("未找到清单: " + name + "，已打开编辑器").formatted(Formatting.YELLOW));
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                src.sendFeedback(Text.literal("已在浏览器打开编辑器。").formatted(Formatting.GREEN));
            } else {
                src.sendFeedback(Text.literal("无法自动打开浏览器，请手动访问: " + url)
                        .formatted(Formatting.YELLOW));
            }
        } catch (Exception e) {
            src.sendFeedback(Text.literal("打开浏览器失败: " + e.getMessage() + "，请手动访问: " + url)
                    .formatted(Formatting.YELLOW));
        }
        return 1;
    }

    /**
     * 内部指令 /todolist _click &lt;token&gt;：消费令牌并执行对应选择。
     *
     * @param ctx 命令上下文
     * @return 1 表示已执行选择，0 表示令牌无效或清单已结束
     */
    private static int runClick(CommandContext<FabricClientCommandSource> ctx) {
        String token = StringArgumentType.getString(ctx, "token");
        PendingChoice pc = ClickTokens.consume(token);
        if (pc == null) {
            // 令牌已失效或已使用，静默忽略
            return 0;
        }
        final ChecklistExecutor exec = pc.executor;
        if (!RUNNING.containsValue(exec)) {
            return 0;
        }
        final int taskId = pc.taskId;
        final boolean choice = pc.choice;
        MinecraftClient client = ctx.getSource().getClient();
        client.execute(() -> {
            exec.choose(taskId, choice);
            if (exec.isFinished()) {
                RUNNING.remove(exec.getChecklist().name);
            }
        });
        return 1;
    }
}
