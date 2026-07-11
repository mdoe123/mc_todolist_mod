package com.todolistmod.exec;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天栏可点击按钮的令牌仓库。
 *
 * <p>每次渲染一个步骤时，为"是/否"按钮各注册一个一次性令牌；点击按钮触发的
 * {@code /todolist _click <token>} 指令凭令牌取回 {@link PendingChoice} 并消费它，
 * 从而避免重复执行。当某个执行器切换到新步骤或结束时，会清空它名下的全部旧令牌，
 * 使历史聊天中的旧按钮失效。</p>
 *
 * <p>令牌使用 {@link SecureRandom} 生成 128 位随机数，不可预测。</p>
 */
public class ClickTokens {
    private static final Map<String, PendingChoice> TOKENS = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();
    /** 令牌最大存活时间（毫秒），超时自动清理。默认 30 分钟。 */
    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L;
    /** 上次清理时间 */
    private static long lastCleanupTime = 0;

    /** 注册一个待处理选择，返回令牌字符串（128 位随机 hex） */
    public static String register(PendingChoice pc) {
        // 每次注册时检查是否需要清理过期令牌
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > TOKEN_TTL_MS) {
            cleanupExpired(now);
        }
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
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

    /** 清空全部令牌（世界卸载 / 模组关闭时调用，防止内存泄漏） */
    public static void clearAll() {
        TOKENS.clear();
        lastCleanupTime = System.currentTimeMillis();
    }

    /** 清理过期的令牌（基于执行器的 finished 标志） */
    private static void cleanupExpired(long now) {
        lastCleanupTime = now;
        Iterator<Map.Entry<String, PendingChoice>> it = TOKENS.entrySet().iterator();
        while (it.hasNext()) {
            PendingChoice pc = it.next().getValue();
            if (pc != null && pc.executor != null && pc.executor.isFinished()) {
                it.remove();
            }
        }
    }
}
