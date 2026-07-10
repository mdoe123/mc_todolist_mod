# Chat Todolist（聊天栏任务清单）

一个 Minecraft Fabric **客户端**模组：在聊天栏里运行「流程式」任务清单。
把写好的 `.json` 清单放进游戏目录的 `todolist` 文件夹，用 `/todolist do <名称>` 执行，
玩家可以直接**点击聊天栏里的文字**来确认选项，清单会按 `trueDo` / `falseDo` 自动推进。

## 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.x（1.21 - 1.21.8+） |
| Fabric Loader | ≥ 0.16.14 |
| Fabric API | ≥ 0.100.0（对应 1.21.x 的版本） |
| Java | 21（1.21+ 必须 Java 21） |

> 这是**纯客户端**模组（`environment: client`），不需要装在服务端。
> 但清单里的 `run` 动作会以你的身份向服务器发送指令，因此需要你有对应权限。

## 构建方法

需要本地装有 JDK 21。仓库 `todolistmod/` 子目录下自带 Gradle Wrapper（`gradlew` + `gradle/wrapper/gradle-wrapper.jar`）。

```bash
# Linux / macOS
cd todolistmod && ./gradlew build

# Windows
cd todolistmod && gradlew.bat build
```

构建产物在 `todolistmod/build/libs/`：

- `todolistmod-1.0.0.jar` —— 这就是要放进 `mods` 文件夹的模组文件
- `todolistmod-1.0.0-sources.jar` —— 源码包（可选）

> 首次构建会自动下载 Minecraft、Yarn 映射和依赖，耗时较长，属正常现象。
> 若 `services.gradle.org` 下载 Gradle 本体很慢，可把 `gradle/wrapper/gradle-wrapper.properties`
> 里的 `distributionUrl` 换成国内镜像，例如腾讯云：
> `https\://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip`

## 安装

1. 确保已安装 Fabric Loader 和 Fabric API（选择与你当前 Minecraft 1.21.x 版本对应的 Fabric API）。
2. 把 `todolistmod-1.0.0.jar` 放进 `.minecraft/mods/`。
3. 启动游戏。首次进入世界时，模组会在**游戏根目录**生成 `todolist` 文件夹，并写入一个 `example.json` 示例清单。

## 使用方法

进入游戏（单人或连服务器均可）后，在聊天栏输入：

| 指令 | 作用 |
| --- | --- |
| `/todolist list` | 列出 `todolist` 文件夹里的所有清单（点击名称可直接执行） |
| `/todolist do <清单名>` | 执行某个清单（支持 Tab 补全清单名） |
| `/todolist end <清单名>` | 结束正在执行的清单（支持 Tab 补全清单名） |
| `/todolist back <清单名>` | 返回上一步（支持连续返回多次；支持 Tab 补全清单名）。注意：已执行的 `run`/`print` 副作用不可撤销，仅回到上一步交互界面 |
| `/todolist true [清单名]` | 快捷确认当前步骤的「是」分支。省略清单名时仅当**只有一个**清单在执行才生效；多个清单在执行时需指定清单名（支持 Tab 补全） |
| `/todolist false [清单名]` | 快捷否决当前步骤的「否」分支。省略清单名时仅当**只有一个**清单在执行才生效；多个清单在执行时需指定清单名（支持 Tab 补全） |
| `/todolist is` | 查看当前正在执行的清单及其进度 |
| `/todolist edit` | 在聊天栏输出可点击的编辑器链接（不预选任何清单），点击后用浏览器打开 |
| `/todolist edit <清单名>` | 在聊天栏输出可点击的编辑器链接并预选该清单（支持 Tab 补全清单名）；未找到时仍输出链接并提示 |

> 编辑器内置两种模式，顶部「表单 / 块」按钮切换：**表单模式**为结构化卡片表单 + 右侧实时 JSON 预览；**块模式**为基于 Google Blockly 的可视化积木编辑（拖拽「步骤」与「动作」块搭建流程，自动转换为清单 JSON）。两种模式编辑同一份文件，切换时若有未保存改动会提示。

> `do`、`end`、`back`、`true`、`false`、`edit` 六个子命令的清单名参数均支持 **Tab 补全**：候选来自 `todolist/` 目录下已加载清单的 `name` 字段值，与 `/todolist list` 显示的一致。清单名可能含空格，故参数类型为 `greedyString`，补全候选需按需选择后手动补全剩余部分。

> `/todolist true` 与 `/todolist false` 是**快捷确认/否决**命令：等价于点击当前步骤的「是/否」按钮。当只有一个清单在执行时可省略清单名直接执行；若版本不兼容等待确认时，`true` 等价于「继续执行」，`false` 等价于「取消」。

执行清单后，聊天栏会出现类似这样的内容：

```
[我的第一个清单] id 1/3
这是一个交互步骤示例。选择「继续」跳到下一步，选择「结束」直接终止清单。
      [继续]    [结束]
```

点击 `[继续]` 或 `[结束]` 即可确认，清单会按你的选择继续推进。
每个按钮只能点一次，步骤切换后旧按钮自动失效。

## 清单文件格式

清单是放在 `todolist/` 文件夹下的 `.json` 文件，结构如下：

```json
{
  "name": "清单名称（执行时用 /todolist do <这个名称>）",
  "description": "清单介绍（可选，启动时在聊天栏显示）",
  "mcVersionMin": "1.21",
  "mcVersionMax": "1.21.1",
  "type": "flow",
  "maxSteps": 30,
  "tasks": [
    {
      "id": 1,
      "desc": "这一步要显示的问题/描述",
      "option": {
        "trueText": "点击按钮·是的文案",
        "falseText": "点击按钮·否的文案"
      },
      "trueDo": [ ... 点「是」后依次执行的动作 ... ],
      "falseDo": [ ... 点「否」后依次执行的动作 ... ]
    }
  ]
}
```

### 字段说明

- `name`：清单名称，也是指令里用的标识。
- `type`：清单类型，目前只支持 `"flow"`。
- `maxSteps`：最大跳转步数，防止清单写成死循环无限执行。超过则自动终止。设为 `0` 或负数表示不限制（不建议）。
- `description`：（可选）清单介绍，支持多行（`\n` 换行）。清单启动时会先按行输出到聊天栏（暗灰色）。`/todolist list` 列表中仅显示首行（超 50 字符截断）。
- `mcVersionMin`：（可选）兼容的 Minecraft 版本下限（含），如 `"1.21"` 或 `"1.21.1"`。空或不写表示无下限。
- `mcVersionMax`：（可选）兼容的 Minecraft 版本上限（含），如 `"1.21.1"`。空或不写表示无上限。只写 `major.minor`（如 `"1.21"`）时视为兼容整个 minor 系列（所有 `1.21.x`）；只写 `major`（如 `"1"`）时视为兼容整个 major 系列。启动时若当前版本不在范围内，会警告并要求玩家确认后才执行。
- `tasks`：步骤数组。
  - `id`：步骤编号（整数），供 `jumpto` 跳转用。不必连续。
  - `desc`：聊天栏显示的描述文字。
  - `option`：选项配置。
    - 设为 `null` 表示这是**终止步骤**：清单显示完描述即标记完成，不出现按钮。
    - `trueText` / `falseText`：两个按钮的文案。
  - `trueDo` / `falseDo`：点对应按钮后**依次**执行的动作数组。

### 动作类型（Action）

每个动作是一个对象，按 `type` 区分：

| type | 作用 | 额外字段 |
| --- | --- | --- |
| `jumpto` | 跳转到指定 id 的步骤，**并终止当前分支** | `id`：目标步骤 id |
| `print` | 在聊天栏输出一行文字，然后继续下一个动作 | `text`：要输出的文字 |
| `run` | 以你的身份向服务器发送一条指令，然后继续 | `command`：指令，带不带 `/` 都行 |
| `end` | 结束整个清单，**终止当前分支** | `message`：结束时的提示文字（可省略） |

**执行规则**：`print` 和 `run` 执行完会继续当前分支里的下一个动作；
`jumpto` 和 `end` 会立即终止当前分支（跳转或结束）。
如果一个分支里既没有 `jumpto` 也没有 `end`，清单会**暂停**并提示，可用 `/todolist end` 手动结束。

### 完整示例

首次运行时模组会在游戏目录的 `todolist/` 文件夹写入 `example.json` 示例：

```json
{
  "name": "我的第一个清单",
  "description": "这是一个通用示例清单，演示交互步骤、终止步骤与四种动作。",
  "mcVersionMin": "1.21",
  "mcVersionMax": "1.21.1",
  "type": "flow",
  "maxSteps": 30,
  "tasks": [
    {
      "id": 1,
      "desc": "这是一个交互步骤示例。选择「继续」跳到下一步，选择「结束」直接终止清单。",
      "option": {
        "trueText": "继续",
        "falseText": "结束"
      },
      "trueDo": [
        {"type": "print", "text": "你选择了继续"},
        {"type": "jumpto", "id": 2}
      ],
      "falseDo": [
        {"type": "end", "message": "你选择了结束，清单已终止。"}
      ]
    },
    {
      "id": 2,
      "desc": "是否执行一条演示指令（将时间设为白天）？",
      "option": {
        "trueText": "执行",
        "falseText": "跳过"
      },
      "trueDo": [
        {"type": "run", "command": "/time set day"},
        {"type": "print", "text": "已将时间设为白天"},
        {"type": "jumpto", "id": 3}
      ],
      "falseDo": [
        {"type": "jumpto", "id": 3}
      ]
    },
    {
      "id": 3,
      "desc": "演示完毕。可用 /todolist edit 在浏览器打开编辑器修改或新建清单。",
      "option": null,
      "trueDo": [],
      "falseDo": []
    }
  ]
}
```

> 清单格式的完整规范见 [`todolistmod/CHECKLIST_FORMAT.md`](todolistmod/CHECKLIST_FORMAT.md)。

## HTML 清单编辑器

执行 `/todolist edit <清单名>` 时，模组会在本地启动一个 HTTP 服务器（仅绑定 `127.0.0.1` 回环地址），
并在聊天栏输出一个**可点击的链接**（`ClickEvent.OPEN_URL`），点击后才会用浏览器打开编辑器页面——不再自动弹出浏览器。
编辑器支持新建、打开、保存、删除清单文件，免去手写 JSON 的麻烦。

编辑器提供两种编辑模式，通过顶部「表单 / 块」切换按钮切换：

### 表单模式（`editor.html`，默认）

结构化表单（name/type/maxSteps/tasks/option/actions）+ 右侧实时 JSON 预览。

### 块模式（`blockly_editor.html`，`?mode=block`）

基于 Google Blockly 的可视化积木编辑器：

- 工具箱分「步骤」「动作」两个分类，拖拽即可搭建流程
- 步骤块含 `interactive_task`（交互步骤，带 trueDo/falseDo 嵌套槽）与 `terminal_task`（终止步骤）两类
- 动作块含 `jumpto`（蓝）/ `print`（灰）/ `run`（橙）/ `end`（红）四类，按颜色区分
- 自动双向转换 Blockly 工作区 ↔ 清单 JSON，保存前校验 id 唯一性与 jumpto 目标存在性
- Blockly 库（`blockly.min.js`，约 758 KB）已打包进 mod 资源，**离线可用**

### 通用行为

- 若指定了清单名且找到对应清单，编辑器会自动预选该清单文件（URL 携带 `?file=<文件名>` 参数）。
- 若指定了清单名但未找到，仍会打开编辑器并提示「未找到清单」。
- 编辑器左侧文件列表会列出 `todolist/` 目录下所有 `.json` 文件。
- 切换模式时若有未保存改动会弹出确认提示。
- 编辑器界面支持**中/英文切换**：工具栏右侧「中/EN」按钮可切换语言，偏好保存在浏览器 `localStorage`，默认跟随浏览器语言。两种模式的所有 UI 文案、按钮提示、Blockly 工具箱分类名与块标签均会跟随切换。

### 安全限制

- HTTP 服务器**仅绑定到 `127.0.0.1`** 回环地址，外部机器无法访问。
- 所有文件读写操作均限制在 `todolist/` 目录内，路径越界（如包含 `..` 或绝对路径）的请求会被拒绝。

### 编辑器 HTTP API

服务器由 `ChecklistEditorServer.ensureStarted()` 懒启动，基址用 `ChecklistEditorServer.baseUrl()` 获取（形如 `http://127.0.0.1:<port>`）。所有 `file` 参数为目标文件名（须以 `.json` 结尾，URL 编码），路径非法统一返回 `400 {"error":"invalid path"}`。

> **安全机制**：服务器仅绑定 `127.0.0.1` 回环地址；所有 `/api/*` 请求必须携带 `token` 查询参数（服务器启动时用 `SecureRandom` 生成的 64 字符 hex 密钥，通过编辑器 URL 传递给页面）；Host 头必须为 `127.0.0.1:<port>` / `localhost:<port>` / `[::1]:<port>` 之一，防 CSRF 与 DNS Rebinding。

| 方法 | 路径 | 说明 | 成功响应 |
| --- | --- | --- | --- |
| `GET`  | `/` | 按 `mode` 查询参数返回页面：无 `mode`/`mode=form` → `editor.html`；`mode=block` → `blockly_editor.html` | `text/html` |
| `GET`  | `/assets/blockly/<file>` | 返回 Blockly 静态资源（如 `blockly.min.js`），从 classpath 读取 | 按 `.js`/`.css` 后缀设 Content-Type |
| `GET`  | `/api/list` | 列出全部清单概要 | `[{"file":"example.json","name":"清单名","taskCount":3}]` |
| `GET`  | `/api/load?file=<name>` | 返回该清单原始 JSON 文本 | 文件内容 |
| `POST` | `/api/save?file=<name>` | 用请求体（清单 JSON 文本）覆盖写入；保存前服务端用 Gson 校验合法性 | `{"ok":true}` |
| `POST` | `/api/new?file=<name>` | 用默认模板新建；已存在返回 `409 {"error":"exists"}` | `{"ok":true}` |
| `DELETE` | `/api/delete?file=<name>` | 删除；不存在返回 `404`（兼容 `POST`） | `{"ok":true}` |

## 工作原理简述

- `ChecklistStore`：扫描 `todolist/*.json`，用 Gson 解析，按 `name` 建索引。
- `ChecklistExecutor`：流程引擎，维护当前步骤与步数计数，执行 `jumpto/print/run/end`，并用 `maxSteps` 防死循环。
- `ChatRenderer`：把步骤渲染成聊天栏消息，选项按钮用 `ClickEvent.RUN_COMMAND` 实现可点击。
- `ClickTokens`：为每个按钮生成一次性令牌，点击后立即失效；步骤切换时清空旧令牌，避免误点旧按钮。
- `/todolist _click <token>`：内部指令，按钮点击时触发，消费令牌并执行对应选择。
- `ChecklistEditorServer`：本地 HTTP 服务器，承载 HTML 清单编辑器，仅监听 `127.0.0.1`。
- `ChecklistSuggestionProvider`：为 `do`/`end`/`edit` 子命令的清单名参数提供 Tab 补全候选。

## 多语言支持

模组自身的所有命令反馈与执行提示均使用 `Text.translatable` 可翻译文本，不再硬编码中文。
语言文件位于 `src/main/resources/assets/todolistmod/lang/` 目录下：

| 文件 | 语言 | 说明 |
| --- | --- | --- |
| `en_us.json` | English（美式英语） | 英文翻译，Minecraft 默认语言为 `en_us` 时生效 |
| `zh_cn.json` | 简体中文 | 中文翻译，语言设置为 `zh_cn` 时生效 |

翻译 key 命名约定为 `todolist.<分类>.<用途>`，例如：

- `todolist.list.*`：`/todolist list` 子命令的反馈
- `todolist.is.*`：`/todolist is` 子命令的反馈（含状态文案 `running`/`finished`）
- `todolist.do.*`：`/todolist do` 子命令的反馈
- `todolist.end.*`：`/todolist end` 子命令的反馈
- `todolist.back.*`：`/todolist back` 子命令的反馈
- `todolist.edit.*`：`/todolist edit` 子命令的反馈
- `todolist.exec.*`：`ChecklistExecutor` 执行引擎运行时的提示（如清单为空、已完成、暂停、返回上一步等）

> **注意**：清单 JSON 文件内的用户内容（`print` 动作的 `text`、`end` 动作的 `message`、清单 `name`、步骤 `desc`、按钮 `trueText`/`falseText`）属于用户数据，**不会被翻译**，按原样输出。

## 许可证

GPL-3.0。详见 [LICENSE](LICENSE)。
