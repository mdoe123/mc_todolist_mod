package com.todolistmod.model;

import java.util.List;

/**
 * 一份完整的流程式清单。字段名与清单 JSON 结构一一对应，由 Gson 直接反序列化。
 */
public class Checklist {
    /** 清单名称（同时作为指令参数与显示标题） */
    public String name;
    /** 清单介绍（多行文本，聊天栏渲染时自动换行） */
    public String description;
    /** 兼容的 Minecraft 版本下限（含），如 "1.21" 或 "1.21.1"。null 或空表示无下限 */
    public String mcVersionMin;
    /** 兼容的 Minecraft 版本上限（含），如 "1.21.1"。null 或空表示无上限。只写 major.minor 时视为兼容整个 minor 系列 */
    public String mcVersionMax;
    /** 清单类型，目前固定为 "flow" */
    public String type;
    /** 最大执行步数，防止循环清单无限跳转；<=0 表示不限制 */
    public int maxSteps;
    /** 任务步骤列表 */
    public List<ChecklistTask> tasks;
}
