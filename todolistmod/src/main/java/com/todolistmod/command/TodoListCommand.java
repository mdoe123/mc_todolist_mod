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
            src.sendFeedback(Text.translatable("todolist.list.empty")
                    .formatted(Formatting.YELLOW));
            return 0;
        }
        src.sendFeedback(Text.translatable("todolist.list.count", all.size()).formatted(Formatting.GOLD));
        for (Entry e : all.values()) {
            String name = e.checklist.name;
            int n = e.checklist.tasks == null ? 0 : e.checklist.tasks.size();
            MutableText line = Text.translatable("todolist.list.bullet").formatted(Formatting.GRAY);
            line.append(Text.literal(name).setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/todolist do " + name))
                    .withColor(Formatting.AQUA)));
            line.append(Text.translatable("todolist.list.steps", n).formatted(Formatting.DARK_GRAY));
            src.sendFeedback(line);
            // 显示清单介绍（若有）：仅取第一行，过长截断
            if (e.checklist.description != null && !e.checklist.description.isBlank()) {
                String firstLine = e.checklist.description.split("\n")[0];
                if (firstLine.length() > 50) {
                    firstLine = firstLine.substring(0, 50) + "...";
                }
                src.sendFeedback(Text.literal("     ")
                        .append(Text.translatable("todolist.list.description", firstLine)
                                .formatted(Formatting.DARK_GRAY)));
            }
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
            src.sendFeedback(Text.translatable("todolist.is.empty").formatted(Formatting.YELLOW));
            return 0;
        }
        for (ChecklistExecutor exec : RUNNING.values()) {
            Checklist cl = exec.getChecklist();
            ChecklistTask t = exec.getCurrentTask();
            MutableText statusText;
            if (exec.isFinished()) {
                statusText = Text.translatable("todolist.is.status.finished");
            } else {
                int total = cl.tasks == null ? 0 : cl.tasks.size();
                statusText = Text.translatable("todolist.is.status.running",
                        t == null ? "?" : t.id, total, exec.getStepCount(), cl.maxSteps);
            }
            src.sendFeedback(Text.translatable("todolist.is.item", cl.name, statusText).formatted(Formatting.AQUA));
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
            src.sendFeedback(Text.translatable("todolist.do.already_running", name, name)
                    .formatted(Formatting.YELLOW));
            return 0;
        }
        Entry entry = ChecklistStore.find(name);
        if (entry == null) {
            src.sendFeedback(Text.translatable("todolist.do.not_found", name)
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
            src.sendFeedback(Text.translatable("todolist.end.not_running", name).formatted(Formatting.YELLOW));
            return 0;
        }
        ClickTokens.clearForExecutor(exec);
        src.sendFeedback(Text.translatable("todolist.end.ended", name).formatted(Formatting.GREEN));
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
            src.sendFeedback(Text.translatable("todolist.back.not_running", name).formatted(Formatting.YELLOW));
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
        String url = ChecklistEditorServer.baseUrl() + "/?token=" + ChecklistEditorServer.getSecretToken();
        openBrowser(src, url);
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
        String token = ChecklistEditorServer.getSecretToken();
        String url;
        if (entry != null) {
            url = ChecklistEditorServer.baseUrl() + "/?file="
                    + URLEncoder.encode(entry.file.getFileName().toString(), StandardCharsets.UTF_8)
                    + "&token=" + token;
            src.sendFeedback(Text.translatable("todolist.edit.file_path", entry.file.toAbsolutePath())
                    .formatted(Formatting.AQUA));
        } else {
            url = ChecklistEditorServer.baseUrl() + "/?token=" + token;
            src.sendFeedback(Text.translatable("todolist.edit.not_found", name).formatted(Formatting.YELLOW));
        }
        openBrowser(src, url);
        return 1;
    }

    /** 尝试在浏览器打开 URL，失败时提示手动访问 */
    private static void openBrowser(FabricClientCommandSource src, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                src.sendFeedback(Text.translatable("todolist.edit.opened").formatted(Formatting.GREEN));
            } else {
                src.sendFeedback(Text.translatable("todolist.edit.open_failed", url)
                        .formatted(Formatting.YELLOW));
            }
        } catch (Exception e) {
            src.sendFeedback(Text.translatable("todolist.edit.exception", e.getMessage(), url)
                    .formatted(Formatting.YELLOW));
        }
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
