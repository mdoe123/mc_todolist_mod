package com.todolistmod.editor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.todolistmod.store.ChecklistStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 本地 HTTP 编辑器服务器：在 127.0.0.1 上以系统分配的随机端口提供清单编辑器页面与 REST API。
 *
 * <p>所有端点仅读写游戏根目录下 {@code todolist} 文件夹内的 .json 文件，
 * 路径解析会拒绝目录穿越（含 {@code ..} 的路径段）与绝对路径。</p>
 *
 * <p>使用 JDK 自带的 {@link com.sun.net.httpserver.HttpServer}，默认执行器同步执行。</p>
 */
public final class ChecklistEditorServer {
    private static HttpServer server;
    private static int port;
    /** 一次性启动密钥：所有 /api/* 请求必须携带此密钥，防 CSRF 与 DNS Rebinding */
    private static String secretToken;

    /** 新建清单时的默认模板（与 example.json 结构一致的最小空清单）。 */
    private static final String DEFAULT_TEMPLATE =
            "{\"name\":\"新清单\",\"type\":\"flow\",\"maxSteps\":30,\"tasks\":["
                    + "{\"id\":1,\"desc\":\"步骤描述\",\"option\":{\"trueText\":\"是\",\"falseText\":\"否\"},"
                    + "\"trueDo\":[{\"type\":\"end\",\"message\":\"结束\"}],"
                    + "\"falseDo\":[{\"type\":\"end\",\"message\":\"结束\"}]}]}";

    private ChecklistEditorServer() {}

    /** 若服务器未运行则启动，返回监听端口号；启动失败返回 -1。 */
    public static synchronized int ensureStarted() {
        if (server != null) {
            return port;
        }
        try {
            // 生成 32 字节随机密钥（64 字符 hex），用于防 CSRF
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            secretToken = java.util.HexFormat.of().formatHex(bytes);
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", new EditorHandler());
            s.setExecutor(null);
            s.start();
            server = s;
            port = s.getAddress().getPort();
            ChecklistStore.LOGGER.info("[ChatTodolist] 编辑器服务器已启动: {}", baseUrl());
            return port;
        } catch (IOException e) {
            ChecklistStore.LOGGER.error("[ChatTodolist] 启动编辑器服务器失败", e);
            return -1;
        }
    }

    /** 返回服务器基址，形如 "http://127.0.0.1:<port>"。 */
    public static synchronized String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** 获取当前启动密钥（用于构造编辑器 URL）。 */
    public static synchronized String getSecretToken() {
        return secretToken;
    }

    /** 停止服务器（可选调用）。 */
    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            port = 0;
        }
    }

    /**
     * 将 file 查询参数解析为 todolist 目录内的安全路径。
     *
     * <p>拒绝：null/空、含 {@code ..} 的路径段、绝对路径、非 .json 后缀，
     * 以及 normalize 后仍不在 todolist 目录内的路径。校验失败返回 null。</p>
     */
    private static Path resolveSafeFile(String fileParam) {
        if (fileParam == null || fileParam.isEmpty()) {
            return null;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(fileParam, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        if (decoded.isEmpty()) {
            return null;
        }
        Path asPath = Path.of(decoded);
        if (asPath.isAbsolute()) {
            return null;
        }
        for (Path part : asPath) {
            String s = part.toString();
            if (s.equals("..") || s.isEmpty()) {
                return null;
            }
        }
        if (!decoded.endsWith(".json")) {
            return null;
        }
        Path base = ChecklistStore.getDir();
        Path resolved = base.resolve(decoded).normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    /** 从请求查询串中取出指定键的原始值（未解码，由 {@link #resolveSafeFile} 统一解码）。 */
    private static String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = idx < 0 ? pair : pair.substring(0, idx);
            String v = idx < 0 ? "" : pair.substring(idx + 1);
            if (k.equals(key)) {
                return v;
            }
        }
        return null;
    }

    /** 为响应添加安全头：禁止 MIME 嗅探、禁止被嵌入 iframe、限制 CSP 仅允许内联脚本与本地资源 */
    private static void applySecurityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        // 编辑器页面为纯内联脚本 + 本地 /assets/blockly/ 资源，不允许外部源
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
    }

    /** 发送 JSON 响应。 */
    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 发送原始字节响应。 */
    private static void sendBytes(HttpExchange exchange, int code, byte[] data, String contentType) throws IOException {
        applySecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /** 尽力发送响应，吞掉所有异常（用于错误兜底路径）。 */
    private static void safeSend(HttpExchange exchange, int code, String json) {
        try {
            sendJson(exchange, code, json);
        } catch (Exception ignored) {
            // 客户端可能已断开，忽略
        }
    }

    /** 校验 Host 头是否为本机回环地址（含端口），防 DNS Rebinding。 */
    private static boolean isLocalHost(String hostHeader, int serverPort) {
        // 允许的形式：127.0.0.1:<port> / localhost:<port> / [::1]:<port>
        return ("127.0.0.1:" + serverPort).equalsIgnoreCase(hostHeader)
                || ("localhost:" + serverPort).equalsIgnoreCase(hostHeader)
                || ("[::1]:" + serverPort).equalsIgnoreCase(hostHeader);
    }

    /** HTTP 请求处理器：按路径与方法分发到各端点。 */
    private static final class EditorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // 安全校验 1：拒绝非本机 Host 头（防 DNS Rebinding）
                String host = exchange.getRequestHeaders().getFirst("Host");
                if (host == null || !isLocalHost(host, port)) {
                    safeSend(exchange, 403, "{\"error\":\"forbidden host\"}");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();

                // 安全校验 2：所有 /api/* 请求必须携带正确的 secret token（防 CSRF）
                // 使用常量时间比较，防时序侧信道
                if (path.startsWith("/api/")) {
                    String token = getQueryParam(exchange, "token");
                    if (token == null || !java.security.MessageDigest.isEqual(
                            token.getBytes(StandardCharsets.UTF_8),
                            secretToken.getBytes(StandardCharsets.UTF_8))) {
                        safeSend(exchange, 403, "{\"error\":\"forbidden: invalid token\"}");
                        return;
                    }
                }

                if (path.equals("/") && "GET".equals(method)) {
                    handleIndex(exchange);
                } else if (path.equals("/api/list") && "GET".equals(method)) {
                    handleList(exchange);
                } else if (path.equals("/api/load") && "GET".equals(method)) {
                    handleLoad(exchange);
                } else if (path.equals("/api/save") && "POST".equals(method)) {
                    handleSave(exchange);
                } else if (path.equals("/api/new") && "POST".equals(method)) {
                    handleNew(exchange);
                } else if (path.equals("/api/delete")
                        && ("DELETE".equals(method) || "POST".equals(method))) {
                    handleDelete(exchange);
                } else if (path.startsWith("/assets/blockly/") && "GET".equals(method)) {
                    // Blockly 静态资源：从 classpath 读取 assets/todolistmod/blockly/<文件名>
                    handleBlocklyAsset(exchange, path);
                } else {
                    safeSend(exchange, 404, "{\"error\":\"not found\"}");
                }
            } catch (Exception e) {
                ChecklistStore.LOGGER.error("[ChatTodolist] 编辑器请求处理失败", e);
                safeSend(exchange, 500, "{\"error\":\"internal\"}");
            } finally {
                try {
                    exchange.close();
                } catch (Exception ignored) {
                    // 忽略关闭异常
                }
            }
        }

        /** GET / → 按 mode 查询参数选择编辑器页面：mode=block 返回 blockly_editor.html，否则返回 editor.html（向后兼容）。 */
        private void handleIndex(HttpExchange exchange) throws IOException {
            String mode = getQueryParam(exchange, "mode");
            String resource = "block".equals(mode)
                    ? "/assets/todolistmod/blockly_editor.html"
                    : "/assets/todolistmod/editor.html";
            try (InputStream in = ChecklistEditorServer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    safeSend(exchange, 500, "{\"error\":\"editor resource not found\"}");
                    return;
                }
                byte[] data = in.readAllBytes();
                sendBytes(exchange, 200, data, "text/html; charset=utf-8");
            }
        }

        /** GET /assets/blockly/<文件名> → 从 classpath 读取 Blockly 静态资源。仅允许安全文件名。 */
        private void handleBlocklyAsset(HttpExchange exchange, String path) throws IOException {
            // 提取文件名：去掉 "/assets/blockly/" 前缀
            String filename = path.substring("/assets/blockly/".length());
            // 安全校验：文件名必须仅含字母数字点下划线连字符，且不含 ".." 或 "/"
            if (filename.isEmpty()
                    || filename.contains("..")
                    || filename.contains("/")
                    || !filename.matches("^[a-zA-Z0-9._-]+$")) {
                safeSend(exchange, 400, "{\"error\":\"invalid asset name\"}");
                return;
            }
            String resource = "/assets/todolistmod/blockly/" + filename;
            try (InputStream in = ChecklistEditorServer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    safeSend(exchange, 404, "{\"error\":\"asset not found\"}");
                    return;
                }
                byte[] data = in.readAllBytes();
                sendBytes(exchange, 200, data, contentTypeFor(filename));
            }
        }

        /** 按文件名后缀返回 Content-Type。 */
        private static String contentTypeFor(String filename) {
            if (filename.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            } else if (filename.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            return "application/octet-stream";
        }

        /** GET /api/list → 返回全部清单的概要数组。 */
        private void handleList(HttpExchange exchange) throws IOException {
            JsonArray arr = new JsonArray();
            for (Map.Entry<String, ChecklistStore.Entry> e : ChecklistStore.loadAll().entrySet()) {
                ChecklistStore.Entry entry = e.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("file", entry.file.getFileName().toString());
                obj.addProperty("name", entry.checklist.name);
                obj.addProperty("taskCount",
                        entry.checklist.tasks == null ? 0 : entry.checklist.tasks.size());
                arr.add(obj);
            }
            sendJson(exchange, 200, new Gson().toJson(arr));
        }

        /** GET /api/load?file=<filename> → 返回该清单文件原始内容。 */
        private void handleLoad(HttpExchange exchange) throws IOException {
            Path target = resolveSafeFile(getQueryParam(exchange, "file"));
            if (target == null) {
                safeSend(exchange, 400, "{\"error\":\"invalid path\"}");
                return;
            }
            if (!Files.exists(target)) {
                safeSend(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            long size = Files.size(target);
            if (size > 1_048_576) { // 1MB
                safeSend(exchange, 413, "{\"error\":\"file too large\"}");
                return;
            }
            String content = Files.readString(target, StandardCharsets.UTF_8);
            sendBytes(exchange, 200, content.getBytes(StandardCharsets.UTF_8),
                    "application/json; charset=utf-8");
        }

        /** POST /api/save?file=<filename> → 用请求体覆盖写入该清单文件。先校验为合法清单 JSON。 */
        private void handleSave(HttpExchange exchange) throws IOException {
            Path target = resolveSafeFile(getQueryParam(exchange, "file"));
            if (target == null) {
                safeSend(exchange, 400, "{\"error\":\"invalid path\"}");
                return;
            }
            // 防止过大请求体导致 OOM，限制 1MB
            byte[] raw = exchange.getRequestBody().readNBytes(1_048_577); // 1MB + 1
            if (raw.length > 1_048_576) {
                safeSend(exchange, 413, "{\"error\":\"request too large\"}");
                return;
            }
            String body = new String(raw, StandardCharsets.UTF_8);
            // 保存前校验：必须能被 Gson 解析为 Checklist，且含 name 与 tasks 字段，避免写入损坏文件
            try {
                com.todolistmod.model.Checklist parsed =
                        new Gson().fromJson(body, com.todolistmod.model.Checklist.class);
                if (parsed == null || parsed.name == null || parsed.tasks == null) {
                    safeSend(exchange, 400, "{\"error\":\"invalid checklist: name/tasks required\"}");
                    return;
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                safeSend(exchange, 400, "{\"error\":\"invalid json: " + escapeJson(e.getMessage()) + "\"}");
                return;
            }
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, body, StandardCharsets.UTF_8);
                ChecklistStore.invalidateCache();
                safeSend(exchange, 200, "{\"ok\":true}");
            } catch (IOException e) {
                ChecklistStore.LOGGER.error("[ChatTodolist] 保存清单失败: {}", target, e);
                safeSend(exchange, 500, "{\"error\":\"io\"}");
            }
        }

        /** 最小 JSON 字符串转义，用于把异常消息安全嵌入 JSON 响应。 */
        private static String escapeJson(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            return sb.toString();
        }

        /** POST /api/new?file=<filename> → 用默认模板创建新清单文件。 */
        private void handleNew(HttpExchange exchange) throws IOException {
            Path target = resolveSafeFile(getQueryParam(exchange, "file"));
            if (target == null) {
                safeSend(exchange, 400, "{\"error\":\"invalid path\"}");
                return;
            }
            if (Files.exists(target)) {
                safeSend(exchange, 409, "{\"error\":\"exists\"}");
                return;
            }
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, DEFAULT_TEMPLATE, StandardCharsets.UTF_8);
                ChecklistStore.invalidateCache();
                safeSend(exchange, 200, "{\"ok\":true}");
            } catch (IOException e) {
                ChecklistStore.LOGGER.error("[ChatTodolist] 新建清单失败: {}", target, e);
                safeSend(exchange, 500, "{\"error\":\"io\"}");
            }
        }

        /** DELETE /api/delete?file=<filename>（兼容 POST） → 删除该清单文件。 */
        private void handleDelete(HttpExchange exchange) throws IOException {
            Path target = resolveSafeFile(getQueryParam(exchange, "file"));
            if (target == null) {
                safeSend(exchange, 400, "{\"error\":\"invalid path\"}");
                return;
            }
            if (!Files.exists(target)) {
                safeSend(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            try {
                Files.delete(target);
                ChecklistStore.invalidateCache();
                safeSend(exchange, 200, "{\"ok\":true}");
            } catch (IOException e) {
                ChecklistStore.LOGGER.error("[ChatTodolist] 删除清单失败: {}", target, e);
                safeSend(exchange, 500, "{\"error\":\"io\"}");
            }
        }
    }
}
