package com.todolistmod.model;

/**
 * 步骤动作。同一种动作类型只用到对应的字段，其余字段保持 null。
 *
 * <ul>
 *   <li>{@code jumpto} —— 跳转到 id 指定的步骤（终止当前分支）</li>
 *   <li>{@code print} —— 在聊天栏输出 text 文本（支持 ${变量} 插值）</li>
 *   <li>{@code run}   —— 以玩家身份发送 command 指令（可带或不带前导 /，支持 ${变量} 插值）</li>
 *   <li>{@code end}   —— 结束整个清单，并输出 message 提示</li>
 *   <li>{@code set}   —— 设置自定义变量 var 的值为 value 表达式求值结果（透传动作）</li>
 *   <li>{@code if}    —— 当 cond 条件为 true 时跳转到 id（终止分支），false 时继续（透传动作）</li>
 * </ul>
 */
public class ChecklistAction {
    public String type;
    /** jumpto/if 目标步骤 id */
    public Integer id;
    /** print 输出文本 */
    public String text;
    /** run 指令内容 */
    public String command;
    /** end 结束提示 */
    public String message;
    /** set 动作的变量名 */
    public String var;
    /** set 动作的值表达式 */
    public String value;
    /** if 动作的条件表达式 */
    public String cond;
}
