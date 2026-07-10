package com.todolistmod.exec;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天栏可点击按钮的令牌仓库。
 *
 * <p>每次渲染一个步骤时，为“是/否”按钮各注册一个一次性令牌；点击按钮触发的
 * {@code /todolist _click <token>} 指令凭令牌取回 {@link PendingChoice} 并消费它，
 * 从而避免重复执行。当某个执行器切换到新步骤或结束时，会清空它名下的全部旧令牌，
 * 使历史聊天中的旧按钮失效。</p>
 */
public class ClickTokens {
    private static final Map<String, PendingChoice> TOKENS = new ConcurrentHashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong();

    /** 注册一个待处理选择，返回令牌字符串 */
    public static String register(PendingChoice pc) {
        String token = Long.toString(COUNTER.incrementAndGet(), 36);
        TOKENS.put(token, pc);
        return token;
    }

    /** 消费并移除令牌；若令牌不存在（已用或已失效）返回 null */
    public static PendingChoice consume(String token) {
        if (token == null) return null;
        return TOKENS.remove(token);
    }

    /** 清空某个执行器名下的全部令牌（步骤切换 / 清单结束时调用） */
    public static void clearForExecutor(ChecklistExecutor executor) {
        Iterator<Map.Entry<String, PendingChoice>> it = TOKENS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().executor == executor) {
                it.remove();
            }
        }
    }
}
