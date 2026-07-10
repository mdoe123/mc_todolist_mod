package com.todolistmod.exec;

/**
 * 一次待处理的点击选择：记录对应的执行器、步骤 id 与选择（true=是 / false=否）。
 */
public class PendingChoice {
    public final ChecklistExecutor executor;
    public final int taskId;
    public final boolean choice;

    public PendingChoice(ChecklistExecutor executor, int taskId, boolean choice) {
        this.executor = executor;
        this.taskId = taskId;
        this.choice = choice;
    }
}
