package com.todolistmod.chat;

import com.todolistmod.exec.ChecklistExecutor;
import com.todolistmod.exec.ClickTokens;
import com.todolistmod.exec.PendingChoice;
import com.todolistmod.model.Checklist;
import com.todolistmod.model.ChecklistTask;
import com.todolistmod.model.TaskOption;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 把清单步骤渲染成聊天栏消息，其中选项按钮为可点击文字。
 */
public class ChatRenderer {

    /** 在聊天栏输出一行普通提示文本（青色） */
    public static void printPlain(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        client.inGameHud.getChatHud().addMessage(
                Text.literal(text == null ? "" : text).formatted(Formatting.AQUA));
    }

    /** 在聊天栏输出一行可翻译文本（青色），用于模组自身的提示文案 */
    public static void printPlain(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null || text == null) {
            return;
        }
        client.inGameHud.getChatHud().addMessage(text.copy().formatted(Formatting.AQUA));
    }

    /** 在聊天栏输出一行文本，保留传入 Text 自身的格式化（不附加默认青色） */
    public static void printRaw(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null || text == null) {
            return;
        }
        client.inGameHud.getChatHud().addMessage(text.copy());
    }

    /** 渲染执行器当前步骤：标题行、描述行，以及（若有）选项按钮行 */
    public static void renderTask(ChecklistExecutor exec) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        Checklist cl = exec.getChecklist();
        ChecklistTask task = exec.getCurrentTask();
        if (task == null) {
            return;
        }

        int total = cl.tasks == null ? 0 : cl.tasks.size();
        String header = String.format("[%s] id %s/%d",
                cl.name == null ? "?" : cl.name, task.id == null ? "?" : task.id, total);
        client.inGameHud.getChatHud().addMessage(
                Text.literal(header).formatted(Formatting.GOLD, Formatting.BOLD));

        client.inGameHud.getChatHud().addMessage(
                Text.literal(task.desc == null ? "" : task.desc).formatted(Formatting.WHITE));

        if (task.option != null && task.id != null) {
            TaskOption opt = task.option;
            String trueLabel = opt.trueText != null ? opt.trueText : "是";
            String falseLabel = opt.falseText != null ? opt.falseText : "否";

            // 切换步骤前先清空本执行器的旧令牌，使旧按钮失效
            ClickTokens.clearForExecutor(exec);
            String trueToken = ClickTokens.register(new PendingChoice(exec, task.id, true));
            String falseToken = ClickTokens.register(new PendingChoice(exec, task.id, false));

            MutableText line = Text.empty();
            line.append(clickable("      [" + trueLabel + "]", Formatting.GREEN,
                    "/todolist _click " + trueToken));
            line.append(Text.literal("    "));
            line.append(clickable("[" + falseLabel + "]", Formatting.RED,
                    "/todolist _click " + falseToken));
            client.inGameHud.getChatHud().addMessage(line);
        }
    }

    /**
     * 在聊天栏输出一对可点击按钮（左绿右红），各绑定一个一次性令牌。
     *
     * @param trueLabel  左侧按钮文案
     * @param trueToken  左侧按钮令牌
     * @param falseLabel 右侧按钮文案
     * @param falseToken 右侧按钮令牌
     */
    public static void printButtons(Text trueLabel, String trueToken, Text falseLabel, String falseToken) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        MutableText line = Text.empty();
        line.append(clickable("      [" + trueLabel.getString() + "]", Formatting.GREEN,
                "/todolist _click " + trueToken));
        line.append(Text.literal("    "));
        line.append(clickable("[" + falseLabel.getString() + "]", Formatting.RED,
                "/todolist _click " + falseToken));
        client.inGameHud.getChatHud().addMessage(line);
    }

    private static MutableText clickable(String text, Formatting color, String command) {
        Style style = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withColor(color);
        return Text.literal(text).setStyle(style);
    }
}
