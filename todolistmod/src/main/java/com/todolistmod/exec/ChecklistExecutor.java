package com.todolistmod.exec;

import com.todolistmod.chat.ChatRenderer;
import com.todolistmod.model.Checklist;
import com.todolistmod.model.ChecklistAction;
import com.todolistmod.model.ChecklistTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 单份清单的执行引擎：维护当前步骤、步数计数，并按顺序执行动作。
 *
 * <p>动作执行约定：{@code jumpto} 与 {@code end} 会终止当前分支；
 * {@code print}/{@code run} 执行后继续下一个动作。若分支既无 jumpto 也无 end，
 * 则暂停清单并提示玩家。</p>
 */
public class ChecklistExecutor {
    private final Checklist checklist;
    private ChecklistTask currentTask;
    private int stepCount;
    private boolean finished;
    /** 步骤历史栈：每次 jumpto 跳转前压入离开的步骤，back() 弹出回到上一步 */
    private final Deque<ChecklistTask> stepHistory = new ArrayDeque<>();

    public ChecklistExecutor(Checklist checklist) {
        this.checklist = checklist;
    }

    /** 是否还能返回上一步（历史栈非空且清单未结束） */
    public boolean canGoBack() {
        return !finished && !stepHistory.isEmpty();
    }

    public Checklist getChecklist() {
        return checklist;
    }

    public ChecklistTask getCurrentTask() {
        return currentTask;
    }

    public int getStepCount() {
        return stepCount;
    }

    public boolean isFinished() {
        return finished;
    }

    /** 开始执行：定位到首个步骤并渲染 */
    public void start() {
        if (checklist.tasks == null || checklist.tasks.isEmpty()) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.empty"));
            finished = true;
            return;
        }
        currentTask = checklist.tasks.get(0);
        stepCount = 1;
        finished = false;
        stepHistory.clear();
        renderCurrent();
    }

    /** 渲染当前步骤；若为终止步骤（option 为 null）则标记完成 */
    public void renderCurrent() {
        if (currentTask == null) {
            return;
        }
        ChatRenderer.renderTask(this);
        if (currentTask.option == null) {
            finished = true;
            ClickTokens.clearForExecutor(this);
            ChatRenderer.printPlain(Text.translatable("todolist.exec.completed"));
        }
    }

    /**
     * 处理玩家对某步骤的选择。必须在客户端主线程调用。
     *
     * @param taskId 步骤 id（用于校验是否仍是当前步骤）
     * @param choice true 执行 trueDo，false 执行 falseDo
     */
    public void choose(int taskId, boolean choice) {
        if (finished) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.finished"));
            return;
        }
        if (currentTask == null || currentTask.id != taskId) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.option_expired"));
            return;
        }
        List<ChecklistAction> actions = choice ? currentTask.trueDo : currentTask.falseDo;
        if (actions == null) {
            actions = Collections.emptyList();
        }
        for (ChecklistAction a : actions) {
            if (a == null || a.type == null) {
                continue;
            }
            switch (a.type) {
                case "run":
                    runCommand(a.command);
                    break;
                case "print":
                    ChatRenderer.printPlain(a.text);
                    break;
                case "jumpto":
                    // jumpto 终止当前分支：切换步骤后立即返回
                    ClickTokens.clearForExecutor(this);
                    ChecklistTask target = findTask(a.id);
                    if (target == null) {
                        ChatRenderer.printPlain(Text.translatable("todolist.exec.jumpto_not_found", a.id));
                        finished = true;
                        return;
                    }
                    if (checklist.maxSteps > 0 && stepCount >= checklist.maxSteps) {
                        ChatRenderer.printPlain(Text.translatable("todolist.exec.max_steps", checklist.maxSteps));
                        finished = true;
                        return;
                    }
                    stepCount++;
                    stepHistory.addLast(currentTask);
                    currentTask = target;
                    renderCurrent();
                    return;
                case "end":
                    endExecution(a.message);
                    return;
                default:
                    ChatRenderer.printPlain(Text.translatable("todolist.exec.unknown_action", a.type));
                    break;
            }
        }
        // 分支中未出现 jumpto/end：暂停
        ClickTokens.clearForExecutor(this);
        ChatRenderer.printPlain(Text.translatable("todolist.exec.paused"));
    }

    /**
     * 返回上一步。必须在客户端主线程调用。
     *
     * <p>弹出步骤历史栈顶，重新设为当前步骤并渲染。回退不消耗 stepCount / maxSteps 额度。
     * 注意：已执行的 {@code run}（向服务器发送的指令）与 {@code print}（聊天栏输出的文字）
     * 属于副作用，不可撤销；back 仅回到上一步的交互界面供玩家重新选择。</p>
     */
    public void back() {
        if (finished) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.back_finished"));
            return;
        }
        if (stepHistory.isEmpty()) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.back_empty"));
            return;
        }
        ClickTokens.clearForExecutor(this);
        currentTask = stepHistory.removeLast();
        ChatRenderer.printPlain(Text.translatable("todolist.exec.back_done"));
        renderCurrent();
    }

    private void endExecution(String message) {
        ClickTokens.clearForExecutor(this);
        finished = true;
        if (message != null && !message.isEmpty()) {
            ChatRenderer.printPlain(message);
        }
        ChatRenderer.printPlain(Text.translatable("todolist.exec.ended_name",
                checklist.name == null ? "?" : checklist.name));
    }

    private void runCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.not_in_game"));
            return;
        }
        String cmd = command.trim();
        // 去除前导斜杠（支持多个，如 "///time set day"）
        int start = 0;
        while (start < cmd.length() && cmd.charAt(start) == '/') {
            start++;
        }
        cmd = cmd.substring(start);
        ClientPlayNetworkHandler handler = client.player.networkHandler;
        handler.sendChatCommand(cmd);
    }

    private ChecklistTask findTask(Integer id) {
        if (id == null || checklist.tasks == null) {
            return null;
        }
        for (ChecklistTask t : checklist.tasks) {
            if (t.id == id.intValue()) {
                return t;
            }
        }
        return null;
    }
}
