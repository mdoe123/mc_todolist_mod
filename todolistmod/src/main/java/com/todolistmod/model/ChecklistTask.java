package com.todolistmod.model;

import java.util.List;

/**
 * 清单中的一个步骤。
 */
public class ChecklistTask {
    /** 步骤 id，jumpto 动作通过它定位目标步骤 */
    public int id;
    /** 步骤描述，显示在聊天栏 */
    public String desc;
    /** 选项配置；为 null 表示这是终止步骤（只展示描述，无按钮） */
    public TaskOption option;
    /** 选择“是”时依次执行的动作 */
    public List<ChecklistAction> trueDo;
    /** 选择“否”时依次执行的动作 */
    public List<ChecklistAction> falseDo;
}
