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

- `todolistmod-1.4.6.jar` —— 这就是要放进 `mods` 文件夹的模组文件
- `todolistmod-1.4.6-sources.jar` —— 源码包（可选）

> 首次构建会自动下载 Minecraft、Yarn 映射和依赖，耗时较长，属正常现象。
> 若 `services.gradle.org` 下载 Gradle 本体很慢，可把 `gradle/wrapper/gradle-wrapper.properties`
> 里的 `distributionUrl` 换成国内镜像，例如腾讯云：
> `https\://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip`

## 安装

1. 确保已安装 Fabric Loader 和 Fabric API（选择与你当前 Minecraft 1.21.x 版本对应的 Fabric API）。
2. 把 `todolistmod-1.4.6.jar` 放进 `.minecraft/mods/`。
3. 启动游戏。首次进入世界时，模组会在**游戏根目录**生成 `todolist` 文件夹；若配置项 `generateExample` 为 `true`（默认），还会写入一个 `example.json` 示例清单。

## 配置文件

模组启动时会在 `config/todolistmod.json` 读取/生成配置（JSON 格式，pretty-printed）。文件不存在时自动用默认值创建；存在但字段缺失会自动补全；JSON 格式错误时回退默认配置并打印警告，不会崩溃。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `generateExample` | boolean | `true` | 是否在 `todolist/` 目录首次创建时写入 `example.json` 示例清单 |
| `editorPort` | int | `0` | 编辑器 HTTP 服务器端口，`0` 表示自动分配 |
| `maxStepsLimit` | int | `100` | 清单最大步骤数上限（防死循环） |
| `language` | string | `"system"` | 界面语言：`"system"`（跟随游戏语言）/ `"zh_cn"` / `"en_us"` |
| `dangerousCommandConfirm` | boolean | `true` | 是否对高危指令进行确认。启用后，`run` 动作执行 `dangerousCommands` 列表中的指令前会暂停清单并弹出 [继续][跳过] 按钮 |
| `dangerousCommands` | string[] | `[]` | 高危指令前缀列表（不区分大小写，不含前导 `/`）。如 `["op","deop","stop","give","gamemode","ban","kick","execute","reload"]`。空列表则不拦截任何命令 |

> 配置在 `onInitializeClient` 阶段最先加载（早于 `ChecklistStore.ensureDir()`），因此 `generateExample` 能控制首次示例清单的生成。

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
| `/todolist edit end` | 关闭编辑器后端 HTTP 服务器（释放端口、清理密钥） |

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
| `set` | 设置自定义变量的值（透传动作） | `var`：变量名；`value`：值表达式 |
| `if` | 条件为 true 时跳转到指定 id，false 时继续 | `cond`：条件表达式；`id`：条件为 true 时跳转的目标步骤 id |

**执行规则**：`print`、`run` 和 `set` 执行完会继续当前分支里的下一个动作；
`jumpto` 和 `end` 会立即终止当前分支（跳转或结束）；`if` 在条件为 true 时终止分支并跳转，false 时继续下一个动作。
如果一个分支里既没有 `jumpto` 也没有 `end`（也没遇到条件为 true 的 `if`），清单会**暂停**并提示，可用 `/todolist end` 手动结束。

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

**变量名自动补全**：在 `print` 的 text、`run` 的 command、`set` 的 value、`if` 的 cond 文本框中输入 `${` 时会弹出变量列表，包含 20 个预定义游戏变量（`player.health`、`world.time` 等）以及当前清单 `set` 动作中定义的自定义变量。继续输入可按前缀过滤（如 `${player` 只显示 `player.*` 变量）；↑↓ 键导航、Enter/Tab 选中插入 `${变量名}`；Esc 关闭列表；鼠标点击亦可选择。

### 块模式（`blockly_editor.html`，`?mode=block`）

基于 Google Blockly 的可视化积木编辑器：

- 工具箱分「步骤」「动作」「变量」「运算」「逻辑」五个分类，前两个默认展开（`expanded="true"`），分类间以分隔条区分，拖拽即可搭建流程
- 步骤块含 `interactive_task`（交互步骤，带 trueDo/falseDo 嵌套槽）与 `terminal_task`（终止步骤）两类
- 动作块含 `jumpto`（蓝）/ `print`（灰）/ `run`（橙）/ `end`（红）/ `set_var`（青）/ `if`（绿）六类，按颜色区分
- 变量分类含 `game_var`（游戏变量，紫色，下拉菜单选 20 个预定义变量）、`variables_get`/`variables_set`（Blockly 内置自定义变量块）
- 运算分类含 `math_number`（数字）、`math_arithmetic`（四则运算 + 幂）—— 用于构建 `set_var` 的值表达式
- 逻辑分类含 `logic_compare`（比较）、`logic_operation`（与/或）、`logic_negate`（非）、`logic_boolean`（真/假）—— 用于构建 `if` 的条件表达式
- `set_var` 块的 value 和 `if` 块的 cond 改为值输入槽，可拖入变量/运算/逻辑块可视化拼装表达式；无连接时显示灰色 `expr_text` 影子块占位
- 加载已有清单时，表达式字符串放入 `expr_text` 影子块显示（不逆向解析为块树），保持完全向后兼容
- 工具栏提供「复制 / 粘贴 / 撤销 / 重做」四个图标按钮（14×14 线条风 SVG 图标）：复制当前选中块到剪贴板、从剪贴板粘贴块到工作区、撤销/重做工作区操作
- 自动双向转换 Blockly 工作区 ↔ 清单 JSON，保存前校验 id 唯一性、jumpto 目标存在性以及 `if` 条件跳转目标存在性
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

- `ModConfig`：最先加载，读写 `config/todolistmod.json`，提供 `generateExample`/`editorPort`/`maxStepsLimit`/`language` 等配置项。
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

## 更新历史

### v1.4.6 — 遗留安全与防御性修复（P2-遗留 + P1-遗留）

- **P2-遗留-2** `ChecklistEditorServer`：缺少 `Referrer-Policy` 响应头。新增 `Referrer-Policy: no-referrer`，纵深防御残余信息通过 Referer 头泄露。
- **P2-遗留-3** `editor.html`：`f.taskCount` 未经 `escapeHtml()` 包裹。虽然后端返回 int 类型安全，但按防御性编程统一包裹。
- **P2-遗留-4** `editor.html` + `blockly_editor.html`：输入字段未设置 `maxlength` 属性。按字段类型添加合理上限（清单名 100、描述 2000、命令 256、版本 20、变量名 64 等），数字字段添加 `max` 属性，防止意外粘贴超大内容。
- **P1-遗留-1** `TodoListCommand`：`runClick` lambda 未重新校验执行器，与 `runChoose`/`runBack` 的 P1-C2 修复不一致。将 `containsValue` 检查移入 lambda，`remove(checklist.name)` 改为 `entrySet().removeIf(e -> e.getValue() == exec)` 按引用条件删除。

### v1.4.5 — 健壮性与并发修复（P1-P + P1-C）

- **P1-P1** `ExpressionEvaluator`：`||` / `&&` 短路求值不正确，右侧操作数总被完整求值。新增 `noEval` 标志，短路时仅消费 token 跳过变量查找。
- **P1-P2** `ExpressionEvaluator`：字符串字面量不支持转义，`\"` 被错误截断。改为逐字符解析，支持 `\"` `\\` `\n` `\t` 转义。
- **P1-P3** `ChecklistSuggestionProvider`：Tab 补全在主线程同步调用 `loadAll()`，缓存失效时可能卡顿。改为 `CompletableFuture.supplyAsync()` 异步执行。
- **P1-P4** `ChecklistEditorServer`：`setExecutor(null)` 单线程串行，慢请求阻塞所有请求。改为 4 线程固定线程池。
- **P1-P5** `GameVariables`：`world.day` 强转 int 理论上可溢出。改为返回 `long`。
- **P1-P6** `ModConfig`：`editorPort` / `maxStepsLimit` 未校验范围。`fillDefaults()` 新增范围校验，非法值回退默认。
- **P1-C1** `TodoListCommand`：`runDo` 中 `containsKey` + `put` 非原子 TOCTOU 竞态。改用 `putIfAbsent` 原子操作。
- **P1-C2** `TodoListCommand`：`runChooseNoName` / `runChoose` / `runBack` 的 lambda 执行前执行器可能被 `end` 移除。lambda 内新增 `RUNNING.get(name) != exec` 重新校验，`remove` 改为 `remove(name, exec)` 条件删除。
- **P1-C3** `ModConfig`：`INSTANCE` 非 volatile，多线程可见性问题。加 `volatile` 修饰。
- **P1-C4** `ChecklistStore`：`cachedEntries` / `lastFileModTime` 非 volatile，无同步。加 `volatile` 修饰，`loadAll()` / `invalidateCache()` 加 `synchronized`。
- **P1-C5** `ChecklistExecutor`：`variables` 使用非线程安全 `HashMap`。改为 `ConcurrentHashMap`。

### v1.4.4 — 健壮性修复（P1-E）

- **P1-E1** `ExpressionEvaluator`：递归下降解析器深度嵌套表达式时抛 `StackOverflowError`（extends `Error`，`catch (Exception)` 无法捕获），新增 `catch (StackOverflowError)` 防止客户端崩溃。
- **P1-E2** `ChecklistExecutor`：`stepCount` 为 `int`，溢出为负数后 `maxSteps` 保护失效导致无限循环。`jumpTo()` 新增溢出检查，达 `Integer.MAX_VALUE` 或负值时终止清单。
- **P1-E3** `ChecklistExecutor`：`beginExecution()` 中 `tasks.get(0)` 未重新校验 `tasks` 是否为空。新增空集合二次校验，防止 `start()` 与 `beginExecution()` 之间状态变化导致越界。
- **P1-E4** `TodoListCommand`：`ensureStarted()` 可能返回 -1（启动失败），未检查直接构造 URL。`runEditNoName` 与 `runEdit` 均新增返回值检查，失败时提示并退出。
- **P1-E5** `ChecklistStore`：从文件系统加载清单无大小限制，超大 JSON 可导致 OOM。`loadAll()` 新增 1MB 单文件大小限制，超限跳过并告警。
- **P1-E6** `ChecklistStore`：缓存失效基于目录 mtime，文件内容修改不一定更新目录 mtime 且精度可能为秒级。改为扫描所有 `.json` 文件的最大 mtime，失效检测更可靠。
- **P1-E7** `ClickTokens`：执行器异常终止未清理时 token 永久残留。新增 TTL（30 分钟）过期清理、`clearAll()` 方法，并在世界卸载时（`ClientPlayConnectionEvents.DISCONNECT`）自动清空全部令牌。
- **P1-E8** `ChecklistTask`：`id` 为 `int` 默认 0，缺少 `id` 的步骤被 Gson 设为 0 后 `findTask(0)` 行为不确定。改为 `Integer`（缺失为 null），`findTask` 跳过 null id，全链路 null 安全。

### v1.4.3 — 安全加固（P0 + P1-S）

- **P0-1** 高危指令确认：`run` 动作执行任意命令无拦截。新增 `dangerousCommandConfirm`（开关）与 `dangerousCommands`（前缀列表）配置项，命中高危命令时暂停并弹出 [继续][跳过] 按钮，支持从断点继续执行剩余动作。
- **P0-2** ClickEvent 名称注入：清单 `name` 直接拼入 `/todolist do <name>` 的 `ClickEvent.RUN_COMMAND`，未转义换行/控制字符。新增 `sanitizeName()` 清洗。
- **P1-S2** CSP `unsafe-inline` 替换：编辑器 HTML 改为每请求生成 128 位随机 nonce 注入 `<script>`/`<style>` 标签，CSP 用 `nonce-<value>` 替代 `unsafe-inline`。
- **P1-S3** Token 存储迁移：编辑器 CSRF token 从 URL 参数迁移到 `sessionStorage`，URL 用 `history.replaceState` 清除，模式切换不携带 token，减小泄露面。

### v1.4.0 — 块编辑器集成变量/运算/逻辑块 + 表编辑器变量名自动补全

- 块编辑器新增「变量」「运算」「逻辑」三个 Blockly 分类，支持可视化拼装表达式。
- 表编辑器在 `print`/`run`/`set`/`if` 文本框中输入 `${` 时弹出变量自动补全列表（20 个预定义游戏变量 + 自定义变量）。
- 新增 `set`（变量赋值）与 `if`（条件跳转）动作类型，支持 `${var}` 表达式插值。

## 许可证

LGPL-3.0。详见 [LICENSE](LICENSE)。
