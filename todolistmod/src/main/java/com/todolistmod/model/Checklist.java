package com.todolistmod.model;

import java.util.List;

/**
 * 一份完整的流程式清单。字段名与清单 JSON 结构一一对应，由 Gson 直接反序列化。
 */
public class Checklist {
    /** 清单名称（同时作为指令参数与显示标题） */
    public String name;
    /** 清单类型，目前固定为 "flow" */
    public String type;
    /** 最大执行步数，防止循环清单无限跳转；<=0 表示不限制 */
    public int maxSteps;
    /** 任务步骤列表 */
    public List<ChecklistTask> tasks;
}
