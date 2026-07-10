# 清单格式规范（Checklist JSON Spec）

> 本文档面向**自动化 agent / 人类**制作 `todolist/*.json` 清单文件。
> 清单由 [Chat Todolist 模组](README.md) 在游戏聊天栏里执行，玩家通过点击按钮推进流程。

---

## 1. 文件位置与命名

- 路径：`<游戏根目录>/todolist/<任意名>.json`
- 文件后缀必须是 `.json`
- 首次进入游戏时模组会在该目录写入 `example.json` 示例
- 文件名与清单 `name` 字段**互相独立**：指令用的是 `name` 字段值，不是文件名

---

## 2. 顶层结构

```json
{
  "name": "清单名称（必填，指令用这个定位）",
  "description": "清单介绍（可选，启动时在聊天栏显示）",
  "mcVersionMin": "1.21",
  "mcVersionMax": "1.21.1",
  "type": "flow",
  "maxSteps": 30,
  "tasks": [ ... 步骤数组 ... ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 清单名称，`/todolist do <这个值>` 用它定位 |
| `description` | string | 否 | 清单介绍，支持多行（`\n` 换行）；启动时按行输出到聊天栏（暗灰色）。`/todolist list` 列表中仅显示首行（超 50 字符截断） |
| `mcVersionMin` | string | 否 | 兼容的 Minecraft 版本下限（含），如 `"1.21"` 或 `"1.21.1"`。空或不写表示无下限 |
| `mcVersionMax` | string | 否 | 兼容的 Minecraft 版本上限（含），如 `"1.21.1"`。空或不写表示无上限。只写 `major.minor`（如 `"1.21"`）时视为兼容整个 minor 系列（即所有 `1.21.x`）；只写 `major`（如 `"1"`）时视为兼容整个 major 系列 |
| `type` | string | 是 | 清单类型，目前固定为 `"flow"` |
| `maxSteps` | int | 是 | 最大跳转步数，防死循环；`<=0` 表示不限制（不建议） |
| `tasks` | array | 是 | 步骤数组，元素为 Task 对象（见第 3 节） |

---

## 3. Task（步骤）结构

```json
{
  "id": 1,
  "desc": "这一步在聊天栏显示的描述/问题",
  "option": {
    "trueText": "点「是」按钮的文案",
    "falseText": "点「否」按钮的文案"
  },
  "trueDo":  [ ... 点「是」后依次执行的动作 ... ],
  "falseDo": [ ... 点「否」后依次执行的动作 ... ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | int | 是 | 步骤编号，供 `jumpto` 跳转定位；不必连续，但**必须唯一** |
| `desc` | string | 是 | 聊天栏显示的文字（问题/描述/提示） |
| `option` | object\|null | 是 | 选项配置；`null` 表示**终止步骤**（见 3.1） |
| `trueDo` | array | 是 | 点「是」后**依次**执行的动作数组；无动作写 `[]` |
| `falseDo` | array | 是 | 点「否」后**依次**执行的动作数组；无动作写 `[]` |

### 3.1 终止步骤 vs 交互步骤

- **交互步骤**：`option` 为 `{"trueText":"...","falseText":"..."}`，聊天栏显示两个按钮，玩家点击后走对应分支。
- **终止步骤**：`option` 设为 `null`，聊天栏只显示 `desc`，**不出现按钮**，清单立即标记完成。
  - 终止步骤的 `trueDo`/`falseDo` 通常写 `[]`（不会被触发）。

### 3.2 option 字段

```json
"option": {
  "trueText": "是，已重置",
  "falseText": "否，还没"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `trueText` | string | 是 | 「是」按钮显示文案 |
| `falseText` | string | 是 | 「否」按钮显示文案 |

---

## 4. Action（动作）结构

每个动作是一个 JSON 对象，按 `type` 区分。同一种 type 只用到对应的字段，其余字段**省略不写**（不要写 null）。

| `type` | 作用 | 额外字段 | 是否终止当前分支 |
| --- | --- | --- | --- |
| `jumpto` | 跳转到指定 id 的步骤 | `id`：目标步骤 id（int） | 是，立即跳转 |
| `print` | 聊天栏输出一行文字（支持 `${变量}` 插值） | `text`：要输出的文字（string） | 否，继续下一动作 |
| `run` | 以玩家身份发送指令（支持 `${变量}` 插值） | `command`：指令文本，带不带 `/` 都行（string） | 否，继续下一动作 |
| `end` | 结束整个清单 | `message`：结束提示文字，**可省略**（string） | 是，立即结束 |
| `set` | 设置自定义变量的值（透传动作） | `var`：变量名（string）；`value`：值表达式（string） | 否，继续下一动作 |
| `if` | 条件跳转（透传动作） | `cond`：条件表达式（string）；`id`：条件为 true 时跳转的目标步骤 id（int） | 仅当条件为 true 时终止分支并跳转；false 时继续下一动作 |

### 4.1 各动作示例

```json
{"type": "jumpto", "id": 2}
{"type": "print", "text": "请先重置地图再执行本清单"}
{"type": "print", "text": "当前生命值：${player.health}"}
{"type": "run", "command": "/gamerule keepInventory false"}
{"type": "run", "command": "gamerule doDaylightCycle false"}
{"type": "run", "command": "/tp @s ${player.x} 100 ${player.z}"}
{"type": "end"}
{"type": "end", "message": "清单终止"}
{"type": "set", "var": "count", "value": "1 + 1"}
{"type": "set", "var": "danger", "value": "${player.health} < 10"}
{"type": "if", "cond": "${player.health} > 0", "id": 5}
{"type": "if", "cond": "${world.weather} == \"thunder\"", "id": 3}
```

### 4.2 分支执行规则（重要）

一个分支（`trueDo` 或 `falseDo`）内的动作**按数组顺序依次执行**：

- `print` / `run` / `set` 执行完 → **继续**当前分支的下一个动作
- `jumpto` / `end` 执行完 → **立即终止**当前分支（跳转或结束）
- `if` 动作：条件为 **true** 时立即终止分支并跳转到 `id`；条件为 **false** 时视作透传动作，**继续**当前分支的下一个动作
- 若整个分支执行完都没遇到 `jumpto` 或 `end`（也没遇到条件为 true 的 `if`）→ 清单**暂停**，提示玩家可用 `/todolist end` 手动结束

> 因此每个交互分支通常应以 `jumpto`、`end` 或可能终止的 `if` 收尾。

### 4.3 变量插值与表达式（set / if / print / run）

`print` 的 `text`、`run` 的 `command` 支持 **${变量名}** 形式的插值：执行前将其替换为变量值的字符串形式。变量未定义时替换为 `?`。

`set` 的 `value` 与 `if` 的 `cond` 是**表达式**，由递归下降解析器求值（不是简单插值）。表达式支持：

- **变量引用**：`${name}`（先查自定义变量，再查游戏预定义变量）
- **数字字面量**：`123`、`3.14`、`-5`
- **字符串字面量**：`"hello"`（双引号包围）
- **布尔字面量**：`true`、`false`
- **算术运算**：`+ - * / %`（`+` 两边任一为字符串时做拼接）
- **比较运算**：`== != < > <= >=`（两边都为数字时按数值比较，否则按字符串比较）
- **逻辑运算**：`&& || !`
- **括号**：`()` 改变优先级

表达式求值失败时返回 null（`if` 视为 false，`set` 不写入）。详细语法见 `ExpressionEvaluator.java` 类注释。

#### 预定义游戏变量

以下变量由客户端实时读取（玩家不在游戏内时除 `world.fps` 外均返回 null）：

| 变量名 | 类型 | 说明 |
| --- | --- | --- |
| `player.health` | int | 当前生命值 |
| `player.max_health` | int | 最大生命值 |
| `player.hunger` | int | 饥饿值（0-20） |
| `player.saturation` | int | 饱食度 |
| `player.armor` | int | 护甲值 |
| `player.x` / `player.y` / `player.z` | int | 玩家所在方块坐标 |
| `player.xp_level` | int | 经验等级 |
| `player.gamemode` | string | 游戏模式名称（survival/creative/adventure/spectator） |
| `player.sneaking` | bool | 是否潜行 |
| `player.sprinting` | bool | 是否疾跑 |
| `player.on_ground` | bool | 是否着地 |
| `player.in_water` | bool | 是否在水中 |
| `player.dimension` | string | 维度标识（如 `minecraft:overworld`） |
| `world.time` | int | 当日时间（0-23999） |
| `world.day` | int | 当前总天数 |
| `world.weather` | string | 天气：`thunder` / `rain` / `clear` |
| `world.difficulty` | string | 难度名称（peaceful/easy/normal/hard） |
| `world.fps` | int | 当前帧率 |

> 自定义变量（由 `set` 动作写入）与游戏变量命名空间独立：表达式/插值查找时**先查自定义变量**，找不到再查游戏预定义变量。

---

## 5. 完整示例

### 5.1 顺序流程（含分支判断）

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

### 5.2 最小终止清单

只有一个展示步骤、无任何交互：

```json
{
  "name": "纯提示清单",
  "type": "flow",
  "maxSteps": 1,
  "tasks": [
    {
      "id": 1,
      "desc": "这是一条只展示、无按钮的提示信息。",
      "option": null,
      "trueDo": [],
      "falseDo": []
    }
  ]
}
```

### 5.3 连续执行多指令

一个分支里串多个 `run` + `print`，最后 `jumpto` 收尾：

```json
{
  "id": 5,
  "desc": "一键设置白天和平局",
  "option": { "trueText": "执行", "falseText": "跳过" },
  "trueDo": [
    {"type": "run", "command": "/time set day"},
    {"type": "run", "command": "/difficulty peaceful"},
    {"type": "print", "text": "已设为白天 + 和平"},
    {"type": "jumpto", "id": 6}
  ],
  "falseDo": [
    {"type": "jumpto", "id": 6}
  ]
}
```

### 5.4 循环/回退

`jumpto` 可以回到之前的 id，实现"回到上一步重选"。`maxSteps` 会限制总跳转次数防死循环：

```json
{
  "id": 2,
  "desc": "检查背包是否清空？",
  "option": { "trueText": "已清空，继续", "falseText": "回去再清" },
  "trueDo":  [ {"type": "jumpto", "id": 3} ],
  "falseDo": [ {"type": "jumpto", "id": 1} ]
}
```

---

## 6. 常见模式速查

### 模式 A：二选一 + 失败终止
```json
"falseDo": [
  {"type": "print", "text": "条件不满足"},
  {"type": "end", "message": "流程中止"}
]
```

### 模式 B：执行多指令后跳下一步
```json
"trueDo": [
  {"type": "run", "command": "/give @s diamond 64"},
  {"type": "print", "text": "已发放钻石"},
  {"type": "jumpto", "id": <下一个步骤id>}
]
```

### 模式 C：纯信息展示（终止步骤）
```json
{
  "id": 99,
  "desc": "=== 全部完成 ===",
  "option": null,
  "trueDo": [],
  "falseDo": []
}
```

### 模式 D：空操作直接跳转（两个按钮都跳同一步）
```json
"option": { "trueText": "继续", "falseText": "也继续" },
"trueDo":  [ {"type": "jumpto", "id": 2} ],
"falseDo": [ {"type": "jumpto", "id": 2} ]
```

---

## 7. 制作清单 Checklist（给 agent 用）

生成清单后自检：

- [ ] 顶层有 `name` / `type="flow"` / `maxSteps` / `tasks` 四个字段
- [ ] `name` 非空，建议不含特殊字符（指令参数会用它）
- [ ] `tasks` 数组非空
- [ ] 每个 task 的 `id` 在整份清单内**唯一**
- [ ] 每个 task 有 `desc`（非空字符串）
- [ ] 交互步骤的 `option` 有 `trueText` 与 `falseText`；终止步骤的 `option` 为 `null`
- [ ] 每个 `trueDo`/`falseDo` 数组存在（即使空也写 `[]`）
- [ ] 所有 `jumpto` 的 `id` 目标在 `tasks` 中**存在**（否则运行时报"跳转目标不存在"）
- [ ] 每个交互分支建议以 `jumpto` 或 `end` 收尾，否则清单会暂停
- [ ] 若有循环跳转，`maxSteps` 设合理上限（如 30），防止死循环
- [ ] `run` 的 `command` 是合法的 MC 指令（玩家需有对应权限）
- [ ] JSON 语法合法（无注释、无尾逗号）

---

## 8. 运行时行为补充

- **按钮一次性**：每个「是/否」按钮点击后立即失效；步骤切换后旧按钮也失效，防误点。
- **执行位置**：纯客户端模组，但 `run` 会以**玩家身份**向服务器发指令，需玩家有对应权限。
- **进度查询**：`/todolist is` 可看当前进行中的清单及步骤。
- **手动结束**：`/todolist end <清单名>` 可强制结束卡住的清单。
- **返回上一步**：`/todolist back <清单名>` 返回上一步（支持连续多次）。回退不消耗 `maxSteps` 额度，但已执行的 `run`/`print` 副作用不可撤销，仅回到上一步交互界面供重选。
- **版本兼容校验**：清单启动时若 `mcVersionMin` / `mcVersionMax` 标注的版本范围与当前 MC 版本不匹配，会输出警告并显示「继续执行 / 取消」按钮，**需玩家确认后才执行**（不会自动执行也不会拒绝）。旧清单不带这两个字段时跳过校验。
- **清单介绍**：若 `description` 非空，清单真正开始执行时（含版本确认通过后）会先按行输出介绍文本（暗灰色），再渲染首个步骤。
- **编辑清单**：`/todolist edit [清单名]` 在浏览器打开 HTML 编辑器（见 README.md）。编辑器支持两种模式：
  - **表单模式**（`editor.html`）：以卡片表单逐字段编辑，右侧实时预览 JSON。
  - **块模式**（`blockly_editor.html`）：基于 Blockly 积木块的可视化编辑，拖拽「步骤」与「动作」块搭建流程，自动双向转换为本规范定义的清单 JSON；保存前校验 id 唯一性、jumpto 目标存在性以及 `if` 条件跳转目标存在性。两模式通过顶部「表单 / 块」按钮切换，未保存改动会提示。

---

## 9. 字段速查表（一页纸版）

```
清单 Checklist
├─ name        : string          // 必填，指令定位用
├─ description : string          // 可选，多行介绍（\n 换行），启动时显示
├─ mcVersionMin: string          // 可选，兼容版本下限（含），空=无下限
├─ mcVersionMax: string          // 可选，兼容版本上限（含），空=无上限
├─ type        : "flow"          // 必填，固定值
├─ maxSteps    : int             // 必填，<=0 不限制
└─ tasks[]     : Task[]          // 必填
   ├─ id       : int           // 必填，唯一
   ├─ desc     : string        // 必填
   ├─ option   : {trueText,falseText} | null   // null=终止步骤
   ├─ trueDo[] : Action[]      // 必填
   └─ falseDo[]: Action[]      // 必填

动作 Action（按 type 取字段）
├─ jumpto : {type, id}
├─ print  : {type, text}                  // text 支持 ${变量} 插值
├─ run    : {type, command}               // command 支持 ${变量} 插值
├─ end    : {type, message?}              // message 可省略
├─ set    : {type, var, value}            // value 为表达式，透传动作
└─ if     : {type, cond, id}              // cond 为表达式；true 跳转 id，false 透传

表达式（set.value / if.cond）：支持 ${变量}、数字、"字符串"、true/false、
+ - * / %、== != < > <= >=、&& || !、()；详见 4.3 节
预定义游戏变量：player.health/hunger/x/y/z/gamemode...、world.time/weather/fps...
（完整列表见 4.3 节）
```
