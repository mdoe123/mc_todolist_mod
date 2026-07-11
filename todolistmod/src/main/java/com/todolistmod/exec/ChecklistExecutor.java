package com.todolistmod.exec;

import com.todolistmod.chat.ChatRenderer;
import com.todolistmod.ModConfig;
import com.todolistmod.model.Checklist;
import com.todolistmod.model.ChecklistAction;
import com.todolistmod.model.ChecklistTask;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    /** 是否处于版本不兼容待确认状态（true 时等待玩家点击继续/取消） */
    private boolean pendingVersionConfirm = false;
    /** 步骤历史栈：每次 jumpto 跳转前压入离开的步骤，back() 弹出回到上一步 */
    private final Deque<ChecklistTask> stepHistory = new ArrayDeque<>();
    /** 自定义变量存储（set 动作写入，表达式/print/run 读取。ConcurrentHashMap 保证线程安全） */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    /** 待确认的高危命令（非 null 表示正在等待玩家确认高危指令） */
    private String pendingDangerousCommand;
    /** 高危确认时保存的剩余动作列表 */
    private List<ChecklistAction> pendingActions;
    /** 高危确认时剩余动作的起始索引 */
    private int pendingActionIndex;

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

    /** 开始执行：定位到首个步骤并渲染。版本不兼容时进入待确认状态 */
    public void start() {
        if (checklist.tasks == null || checklist.tasks.isEmpty()) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.empty"));
            finished = true;
            return;
        }
        // 版本兼容性校验：不兼容时进入待确认状态
        String versionErrKey = checkVersionCompatibility(checklist);
        if (versionErrKey != null) {
            pendingVersionConfirm = true;
            renderVersionWarning(versionErrKey);
            return;
        }
        beginExecution(false);
    }

    /** 实际开始执行：可选输出确认提示，输出 description（若有）后定位首步并渲染 */
    private void beginExecution(boolean showConfirmed) {
        if (showConfirmed) {
            ChatRenderer.printPlain(Text.translatable("todolist.do.version_confirmed",
                    checklist.name == null ? "?" : checklist.name).formatted(Formatting.AQUA));
        }
        // 输出清单介绍（按行输出，暗灰色）
        if (checklist.description != null && !checklist.description.isBlank()) {
            String[] lines = checklist.description.split("\n");
            for (String line : lines) {
                ChatRenderer.printRaw(Text.literal(line).formatted(Formatting.DARK_GRAY));
            }
        }
        // 重新校验 tasks 非空，防止 start() 之后到 beginExecution 之间状态变化
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
     * 快捷确认/否决当前步骤。必须在客户端主线程调用。
     *
     * <p>等价于点击当前步骤的「是/否」按钮。若处于版本确认待决状态，
     * 则等价于点击「继续执行」/「取消」。若当前步骤为终止步骤或已结束则忽略。</p>
     *
     * @param choice true 确认（执行 trueDo / 继续执行），false 否决（执行 falseDo / 取消）
     */
    public void chooseCurrent(boolean choice) {
        if (pendingVersionConfirm) {
            choose(-1, choice);
        } else if (pendingDangerousCommand != null) {
            choose(-2, choice);
        } else if (currentTask != null && currentTask.id != null) {
            choose(currentTask.id, choice);
        }
    }

    /**
     * 处理玩家对某步骤的选择。必须在客户端主线程调用。
     *
     * @param taskId 步骤 id（用于校验是否仍是当前步骤）
     * @param choice true 执行 trueDo，false 执行 falseDo
     */
    public void choose(int taskId, boolean choice) {
        // 版本确认场景：taskId=-1 表示玩家在版本警告界面点击继续/取消
        if (taskId == -1) {
            if (pendingVersionConfirm) {
                if (choice) {
                    confirmVersionAndStart();
                } else {
                    cancelVersionConfirm();
                }
            }
            return;
        }
        // 高危命令确认场景：taskId=-2 表示玩家在高危指令警告界面点击继续/跳过
        if (taskId == -2) {
            if (pendingDangerousCommand != null) {
                String cmd = pendingDangerousCommand;
                List<ChecklistAction> remaining = pendingActions;
                int idx = pendingActionIndex;
                pendingDangerousCommand = null;
                pendingActions = null;
                if (choice) {
                    ChatRenderer.printPlain(Text.translatable("todolist.exec.dangerous_confirmed")
                            .formatted(Formatting.GRAY));
                    runCommand(cmd);
                } else {
                    ChatRenderer.printPlain(Text.translatable("todolist.exec.dangerous_skipped")
                            .formatted(Formatting.YELLOW));
                }
                executeActions(remaining, idx);
            }
            return;
        }
        if (finished) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.finished"));
            return;
        }
        if (currentTask == null || currentTask.id == null || currentTask.id != taskId) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.option_expired"));
            return;
        }
        List<ChecklistAction> actions = choice ? currentTask.trueDo : currentTask.falseDo;
        if (actions == null) {
            actions = Collections.emptyList();
        }
        executeActions(actions, 0);
    }

    /**
     * 执行动作列表，遇到高危命令时暂停并等待玩家确认。
     *
     * @param actions    要执行的动作列表
     * @param startIndex 起始索引（用于高危确认后从断点继续）
     */
    private void executeActions(List<ChecklistAction> actions, int startIndex) {
        for (int i = startIndex; i < actions.size(); i++) {
            ChecklistAction a = actions.get(i);
            if (a == null || a.type == null) {
                continue;
            }
            switch (a.type) {
                case "run":
                    String cmd = ExpressionEvaluator.substitute(a.command, variables);
                    if (isDangerousCommand(cmd)) {
                        pendingDangerousCommand = cmd;
                        pendingActions = actions;
                        pendingActionIndex = i + 1;
                        renderDangerousConfirm(cmd);
                        return;
                    }
                    runCommand(cmd);
                    break;
                case "print":
                    ChatRenderer.printPlain(ExpressionEvaluator.substitute(a.text, variables));
                    break;
                case "set":
                    setVariable(a.var, a.value);
                    break;
                case "if":
                    if (evalCondition(a.cond)) {
                        jumpTo(a.id);
                        return;
                    }
                    break;
                case "jumpto":
                    jumpTo(a.id);
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

    /** 判断命令是否为高危指令（第一个 token 匹配 dangerousCommands 列表） */
    private boolean isDangerousCommand(String command) {
        ModConfig cfg = ModConfig.INSTANCE;
        if (cfg == null || !cfg.dangerousCommandConfirm) return false;
        if (cfg.dangerousCommands == null || cfg.dangerousCommands.isEmpty()) return false;
        if (command == null || command.trim().isEmpty()) return false;
        String cmd = command.trim();
        while (!cmd.isEmpty() && cmd.charAt(0) == '/') cmd = cmd.substring(1);
        int spaceIdx = cmd.indexOf(' ');
        String firstToken = spaceIdx > 0 ? cmd.substring(0, spaceIdx) : cmd;
        for (String dangerous : cfg.dangerousCommands) {
            if (dangerous != null && !dangerous.isEmpty() && dangerous.equalsIgnoreCase(firstToken)) {
                return true;
            }
        }
        return false;
    }

    /** 渲染高危命令确认警告与 [继续][跳过] 按钮 */
    private void renderDangerousConfirm(String command) {
        ChatRenderer.printPlain(Text.translatable("todolist.exec.dangerous_warning", command)
                .formatted(Formatting.RED));
        ClickTokens.clearForExecutor(this);
        PendingChoice confirmPc = new PendingChoice(this, -2, true);
        PendingChoice skipPc = new PendingChoice(this, -2, false);
        String confirmToken = ClickTokens.register(confirmPc);
        String skipToken = ClickTokens.register(skipPc);
        ChatRenderer.printButtons(Text.translatable("todolist.exec.dangerous_confirm"), confirmToken,
                Text.translatable("todolist.exec.dangerous_cancel"), skipToken);
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

    /** 跳转到指定 id 的步骤（jumpto 和 if 共用）。终止当前分支。 */
    private void jumpTo(Integer targetId) {
        ClickTokens.clearForExecutor(this);
        ChecklistTask target = findTask(targetId);
        if (target == null) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.jumpto_not_found", targetId));
            finished = true;
            return;
        }
        if (checklist.maxSteps > 0 && stepCount >= checklist.maxSteps) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.max_steps", checklist.maxSteps));
            finished = true;
            return;
        }
        // 防止 stepCount 溢出为负数导致 maxSteps 保护失效
        if (stepCount < 0 || stepCount == Integer.MAX_VALUE) {
            ChatRenderer.printPlain(Text.translatable("todolist.exec.max_steps", checklist.maxSteps));
            finished = true;
            return;
        }
        stepCount++;
        stepHistory.addLast(currentTask);
        currentTask = target;
        renderCurrent();
    }

    /** 设置自定义变量：对 value 表达式求值后存入 variables */
    private void setVariable(String name, String expr) {
        if (name == null || name.isEmpty()) return;
        Object val = new ExpressionEvaluator(variables).evaluate(expr);
        variables.put(name, val);
    }

    /** 求值条件表达式，返回布尔结果 */
    private boolean evalCondition(String cond) {
        Object result = new ExpressionEvaluator(variables).evaluate(cond);
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof Number) return ((Number) result).doubleValue() != 0;
        return false;
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
            // 跳过未指定 id 的步骤（t.id 为 null），避免默认 0 导致 findTask(0) 歧义
            if (id.equals(t.id)) {
                return t;
            }
        }
        return null;
    }

    // ===== Minecraft 版本兼容性校验 =====

    /** 解析版本字符串为 int[3]，缺失补 0。如 "1.21" → [1,21,0]。解析失败返回 null。 */
    private static int[] parseVersion(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] parts = s.trim().split("\\.");
            int[] v = new int[3];
            for (int i = 0; i < 3; i++) {
                v[i] = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 比较两个 int[3] 版本：a < b 返回 -1，相等 0，a > b 返回 1 */
    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] < b[i]) return -1;
            if (a[i] > b[i]) return 1;
        }
        return 0;
    }

    /** 获取 max 版本的排他上界（不含）。3 段含 max 本身（patch+1）；2 段视为下个 minor；1 段视为下个 major。
     * 解析失败（如含非数字字符）返回 null。 */
    private static int[] getMaxUpperBound(String maxStr) {
        try {
            String[] parts = maxStr.trim().split("\\.");
            int[] v = new int[3];
            for (int i = 0; i < Math.min(3, parts.length); i++) {
                v[i] = Integer.parseInt(parts[i]);
            }
            // 返回排他上界（不含）：current >= upperBound 即视为过高
            if (parts.length >= 3) {
                v[2] = v[2] + 1;
            } else if (parts.length == 2) {
                v[1] = v[1] + 1;
            } else {
                v[0] = v[0] + 1;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 校验当前 MC 版本是否在清单兼容范围内。
     * @return null 表示兼容，否则返回 translatable key（version_too_low / version_too_high）
     */
    public static String checkVersionCompatibility(Checklist checklist) {
        String currentStr = getCurrentMcVersion();
        if (currentStr == null) return null;
        int[] current = parseVersion(currentStr);
        if (current == null) return null; // 当前版本解析失败，跳过校验

        if (checklist.mcVersionMin != null && !checklist.mcVersionMin.isBlank()) {
            int[] min = parseVersion(checklist.mcVersionMin);
            if (min != null && compareVersions(current, min) < 0) {
                return "todolist.do.version_too_low";
            }
        }
        if (checklist.mcVersionMax != null && !checklist.mcVersionMax.isBlank()) {
            int[] upperBound = getMaxUpperBound(checklist.mcVersionMax);
            if (upperBound != null && compareVersions(current, upperBound) >= 0) {
                return "todolist.do.version_too_high";
            }
        }
        return null;
    }

    /** 获取当前 Minecraft 版本的友好字符串；无法获取时返回 null */
    private static String getCurrentMcVersion() {
        var mcOpt = FabricLoader.getInstance().getModContainer("minecraft");
        if (mcOpt.isEmpty()) return null;
        var metadata = mcOpt.get().getMetadata();
        if (metadata == null) return null;
        var version = metadata.getVersion();
        if (version == null) return null;
        return version.getFriendlyString();
    }

    /** 渲染版本不兼容警告与确认按钮 */
    private void renderVersionWarning(String errKey) {
        String minStr = checklist.mcVersionMin == null || checklist.mcVersionMin.isBlank() ? "*" : checklist.mcVersionMin;
        String maxStr = checklist.mcVersionMax == null || checklist.mcVersionMax.isBlank() ? "*" : checklist.mcVersionMax;
        String currentStr = getCurrentMcVersion();
        if (currentStr == null) currentStr = "?";

        ChatRenderer.printPlain(Text.translatable("todolist.do.version_warning",
                checklist.name == null ? "?" : checklist.name, minStr, maxStr, currentStr)
                .formatted(Formatting.YELLOW));

        // 渲染确认按钮：[继续执行] [取消]，用 taskId=-1 标记版本确认场景
        ClickTokens.clearForExecutor(this);
        PendingChoice confirmPc = new PendingChoice(this, -1, true);
        PendingChoice cancelPc = new PendingChoice(this, -1, false);
        String confirmToken = ClickTokens.register(confirmPc);
        String cancelToken = ClickTokens.register(cancelPc);
        ChatRenderer.printButtons(Text.translatable("todolist.do.confirm_continue"), confirmToken,
                Text.translatable("todolist.do.cancel"), cancelToken);
    }

    /** 用户在版本确认界面点击"继续执行"后调用 */
    public void confirmVersionAndStart() {
        pendingVersionConfirm = false;
        ClickTokens.clearForExecutor(this);
        beginExecution(true);
    }

    /** 用户在版本确认界面点击"取消"后调用 */
    public void cancelVersionConfirm() {
        pendingVersionConfirm = false;
        ClickTokens.clearForExecutor(this);
        finished = true;
        ChatRenderer.printPlain(Text.translatable("todolist.do.version_cancelled",
                checklist.name == null ? "?" : checklist.name).formatted(Formatting.GRAY));
    }
}
