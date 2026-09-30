# G8 批次报告（全量移植收尾·第二轮）

范围：A4-D2 / A4-D3 / A7-缺失1 / A7-缺失2 / A7-缺失3。
参考：Mindustry Build 126.2（只读，`D:\IdeaProjects\Mindustry-126.2`）。
本批**未编译、未运行**（按铁律，编译/提交由主会话负责）。
未触碰用户并行改动文件：`entities/type/Player.java`、`input/InputHandler.java`、`io/SaveIO.java`、`ClientLauncher.java` 的 `//DEBUG-MINE-START` 块。

---

## G8-1 `Tile.updateOcclusion()` 补 cost 累加（A4-D2）

- **原版位置**：`world/Tile.java:394-440`（`updateOcclusion`）。
- **本项目改动**：
  - `core/src/com/phoenix/game/world/Tile.java:268-325`（方法体重写；新增 `import com.phoenix.game.math.Mathf;`）。
  - 消费点确认：`ai/Pathfinder.java:53`（`PathTile.get(tile.cost, ...)`）与 `:283-286`（扩散时 `path.weights[dx][dy] > cost + other.cost`）确实读 `tile.cost`。此前 `cost` 恒为 1 → 寻路完全不避水/焦油。现已照原版累加。
- **逐行等价换算（均已在方法注释里声明）**：
  - `Geometry.d8`：本项目已存在（`math/geom/Geometry.java:19`，`GridPoint2[]`），直接用 `.x/.y`。
  - `world.tile(...)` → `Vars.world.tile(...)`（返回可空 `Tile`）。
  - `link().synthetic()`：本项目 `Tile` **没有** `synthetic()` 方法 → 用 `link().block().synthetic()`。等价性：`link()` 解析到中心瓦片，`block()` 取该中心方块；原版 `Tile.synthetic()` 就是 `block.update || block.destructible`，而本项目 `Block.synthetic()`（`Block.java:486`）同为 `update || destructible`。
  - `link().solid()` / `link().block.health`：同样经 `link()` 解析后的中心瓦片取用（`center.block().health`）。
  - `Mathf.clamp(health/10f, 0, 20)`：本项目 `Mathf.clamp(float,float,float)`，写法照抄原版文本（首参 float → 命中 float 重载）。
  - 结尾 `if(cost < 0) cost = Byte.MAX_VALUE;` 照抄。
- **刻意偏离（1 处，已注释）**：原版假定 `world` 非空；本项目 `changed()` 可能在世界尚未就绪时被调用 → 方法开头加 `if(Vars.world == null) return;`（保持 `cost = 1`），避免 NPE。
- **遗留风险**：
  - `cost` 是 `byte`，累加是复合赋值（隐式窄化），与原版一致；溢出由结尾保护兜住。
  - `updateOcclusion` 现在每次 `changed()` 做 8 次 `Vars.world.tile()`，与原版同量级，无额外风险。

---

## G8-2 `Build.validPlace` 补 `Units.anyEntities` 早退（A4-D3）

- **原版位置**：`world/Build.java:77-79`。
- **本项目改动**：
  - `core/src/com/phoenix/game/world/Build.java:94-106`（在 `bannedBlocks` 之后、`enemyCoreBuildRadius` 之前插入，位置与原版一致；新增 `import com.phoenix.game.entities.Units;`）。
  - `core/src/com/phoenix/game/world/Block.java:53-60`：新增字段 `public boolean solidifes;`（对应原版 `Block.solidifes`，原版 `Block.java:53`，原项目缺该字段）。
  - `core/src/com/phoenix/game/world/blocks/BuildBlock.java:43-44`：构造器 `solidifes = true;`（原版 `BuildBlock.java:44`）。
  - `core/src/com/phoenix/game/world/blocks/defense/Door.java:46-50`：构造器 `solidifes = true;`（原版 `Door.java:32`），并同步修正类注释（原先写"本项目 Block 无该字段"）。
- **判据**：`(block.solid || block.solidifes) && Units.anyEntities(左, 下, 宽, 高)`。
- **刻意偏离（2 处，已注释）**：
  1. **未用 `Units.anyEntities(tile)`**（任务提示的写法），而是直接按"待放置方块"的矩形调用 4 参重载。原因：本项目 `Units.anyEntities(Tile)`（`Units.java:64-67`）内部取的是 **`tile.block().size`（该格现有方块）**，而 `validPlace` 需要的是 **`block.size`（即将放置的方块）**。对 1×1 实心方块二者等价；但 2×2/3×3 核心压住单位时会按 1×1 判定而放行 → 单位被卡进墙里。故按 `block.centerX/centerY(tile) ∓ block.size*tilesize/2` 构造矩形，语义与原版 `(x*tilesize + type.offset() ∓ type.size*tilesize/2f)` 等价（本项目 `worldx()` 是角点口径，`Block.centerX` 已含 `+tilesize/2`）。
  2. `solidifes` 字段为新增（原项目无）。
- **未拦"放置占位/建造中"路径**：`validPlace` 的 `block` 恒为真实目标方块；`Build.beginPlace`（铺 `BuildBlock` 占位）**不调用** `validPlace`，故占位路径不受影响（已注释）。
- **遗留风险**：`Door` 现在 `solid=false, solidifes=true` → 门口站着单位时无法放门（与原版一致）。若后续新增"可实心但当前不实心"的方块，需记得置 `solidifes`。

---

## G8-3 服务端聊天命令（A7-缺失1）

- **原版位置**：`core/NetServer.java:272-457`（`registerCommands`：/help /t /vote /votekick /sync）+ `core/NetClient.java:160-206`（`sendChatMessage` 命令分发）。
- **本项目改动**（`core/src/com/phoenix/game/core/NetServer.java`）：
  - `init()`（:53-56）：追加 `registerCommands();`。
  - 新增 `ServerCommand` 内部类（:327-341）、`commandList`（:344）、`registerCommands()`（:346-359）。
  - 新增 `handleCommand(RemotePlayer, String)`（:361-429）：实现 `/help`（分页，commandsPerPage=6）、`/t`（队伍频道）、`/sync`（重发世界）。
  - 新增 `sendMessage(rp, msg)` / `sendMessage(rp, name, msg)`（:431-444）、`isInt`（:446-454）、`joinArgs`（:456-464）。
  - `onChat`（:746-764）：改为"长度校验 → 命令优先分发 → 否则普通广播"。
- **刻意偏离（已注释）**：
  1. **不用现有 `mod.CommandHandler`**：它的回调签名是 `(args, Consumer<String> responder)`，**不带发送者** → 无法实现 `/t`（要发给全队）与 `/sync`（要重发世界给发起者）。故自写最小分发（`handleCommand`），并保留一张 `commandList` 供 `/help` 展示（对照原版 `CommandHandler` 命令表）。
  2. `/vote`、`/votekick`：依赖未移植的投票系统（原版 `VoteSession` + `Timer` + `Config.enableVotekick`）→ **保留为未实现**，不注册、不硬造（归入"未知命令"）。
  3. `/t` 显示名：原版为 `"[#" + team.color + "]<T>" + colorizeName(...)`；本项目 `Packets.ChatMessage` 只带纯名字字符串 → 保留 `<T>` 前缀、去掉颜色标记。
  4. `/sync` 冷却：原版有 5 秒冷却（`PlayerInfo.lastSyncTime`）；本项目 `Administration.PlayerInfo` 无该字段 → 略去冷却。
- **额外改动（为让命令可达）**：`ui/fragments/ChatFragment.java:219-228`
  - 原客户端把所有 `/` 开头消息**本地**处理（仅 4 条内置），未知命令直接提示"未知命令"、**不发服务器** → 服务端命令永远收不到。
  - 改为：非内置命令 → 先插件命令，再（已连接时）`Vars.netClient.sendChat("/" + line)` 转发给服务端；未连接时仍本地提示"未知命令"。
  - 本地 `/help` 文案追加了 `/t`、`/sync` 两行（`:190-198`）。
  - 依据：原版客户端 `ChatFragment.sendMessage` 只做 `Call.sendChatMessage(message)`，命令全由服务端 `clientCommands` 分发。
- **遗留风险**：
  - 主机（HOST）自身不在 `players` 里 → 从主机发 `/t` 不会显示给主机自己（与"主机能否看到聊天"这一既有实现限制同源，未扩大范围）。
  - `/help` 列表顺序按 `commandList` 注册顺序（本项目自维护），与原版一致；命令总数 < 6，当前只有一页。

---

## G8-4 玩家名/颜色/长度校验（A7-缺失2）

- **原版位置**：`core/NetServer.java:792`（`fixName`）/`:816`（`checkColor`）/`Vars.java:71/73`（`maxTextLength=150` / `maxNameLength=40`）。
- **本项目改动**（`core/src/com/phoenix/game/core/NetServer.java`）：
  - 新增 `fixName(String)`（:466-492）：去首尾空白 → 剥非法颜色标记 → 按字节截断到 `Vars.maxNameLength`。
  - 新增 `checkColor(String)`（:494-524）。
  - `onConnect`（:647-655）：`p.name = fixName(p.name);` + 空名 `kick(con, "name empty")`（对应原版 `nameEmpty`）。
  - `onChat`（:750-755）：`message.length() > Vars.maxTextLength` → 记日志后丢弃（不广播）。
- **刻意偏离（已注释）**：
  - `checkColor`：原版分别查 `Colors.get(大写)` 与 `Colors.get(小写)`；本项目 `graphics.Colors.get` 的 `key()` 内部已 `toLowerCase`（大小写不敏感）→ 查一次，行为一致。
  - 超长聊天：原版抛 `ValidateException`（服务端只记一条 debug）；本项目无该异常链路 → 记日志后丢弃（等效"不广播"）。
  - `Vars.maxTextLength` / `Vars.maxNameLength` 本项目已存在（`Vars.java:52/54`），直接复用。
- **遗留风险**：无。

---

## G8-5 游戏结束时删除区域存档槽（A7-缺失3）

- **原版位置**：`core/Control.java:103-114`（`Events.on(GameOverEvent...)`）。
- **本项目改动**：`core/src/com/phoenix/game/core/Control.java:233-248`（构造器末尾**追加**监听器；新增 `import com.phoenix.game.game.Saves;`）。
  - 判据照原版：`rules.zone != null && !Vars.isClient() && !rules.tutorial` → `saves.getZoneSlot().delete()`。
  - 本项目 `SaveSlot` 无 `delete()` → 走 `Vars.saves.delete(slot)`（等价）。
- **刻意偏离（已注释）**：原版该监听器还负责 `stats.wavesLasted` / 震屏 / `Call.onGameOver` / 弹结算框；本项目这几步已内联在 `Logic.checkGameOver` / `Logic.launchZone` → 这里**只补"删区域槽"**，避免重复。事件为同步 fire（原版经 `Core.app.post`）→ 删文件前判空。
- **遗留风险**：
  - `Rules.java:73` 注释"本项目战役生成器尚未接线 → zone 恒为 null"**已过时**：`Control.playZone`（:462）与 `SaveIO`（:533）会赋 `rules.zone` → 该监听器对区域局生效。
  - `launchZone()` 也 fire `GameOverEvent(defaultTeam)` → 成功发射后同样会删区域槽（与原版一致：已发射的区域不该再被"继续战役"载入）。

---

## ⛔ M8/D5（CoreBlock 重生链）——本批**完全未做**

- 原版位置：`world/blocks/storage/CoreBlock.java:42-53,186-232`（`drawLayer` 重生动画 / `update` 的 heat+progress / `shouldActiveSound` / `onUnitRespawn` / `Fx.spawn`）。
- **做到哪一步：0 步**。理由：
  1. 触发链（`SpawnerTrait.updateSpawning` 由玩家死亡后调用、`Player.update` 里的重生计时）在 `entities/type/Player.java` —— **用户正在改该文件，本批禁动**。
  2. 若只做 `CoreBlock` 侧的 `heat/progress/spawnPlayer/drawLayer`，由于**没有任何代码会去设置 `spawnPlayer`**（触发点在 `Player.update`），这些字段与绘制将恒为空/死代码，不产生可验证的行为。
  3. `CoreBlock.CoreEntity` 当前连 `heat/progress/time/spawnPlayer` 字段都没有（grep 全无），要做必须整套搬（含 `RespawnBlock.drawRespawn` 接线、`entity.delta()`、`mech` 字段、`Call.onUnitRespawn` 的本地位替代）——依赖面已超出"纯新增"。
- **结论**：整条重生链留到 G9，与 `Player.java` 的改动一起做。
  （`RespawnBlock.java` 在本项目**已存在**，`Player.java:457-465` 里已有旧的"玩家自计时重生"逻辑 —— 正是 M8/D5 要搬到 `CoreBlock` 的那套。）

---

## 改动文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/world/Tile.java` | G8-1：`updateOcclusion` 重写 + `Mathf` import |
| `core/src/com/phoenix/game/world/Block.java` | G8-2：新增 `solidifes` 字段 |
| `core/src/com/phoenix/game/world/blocks/BuildBlock.java` | G8-2：构造器 `solidifes = true` |
| `core/src/com/phoenix/game/world/blocks/defense/Door.java` | G8-2：构造器 `solidifes = true` + 注释修正 |
| `core/src/com/phoenix/game/world/Build.java` | G8-2：`validPlace` 补 `anyEntities` 早退 + `Units` import |
| `core/src/com/phoenix/game/core/NetServer.java` | G8-3/G8-4：命令分发 + `fixName`/`checkColor` + `onConnect`/`onChat` 校验 |
| `core/src/com/phoenix/game/ui/fragments/ChatFragment.java` | G8-3 配套：未知命令转发服务端 + help 文案 |
| `core/src/com/phoenix/game/core/Control.java` | G8-5：`GameOverEvent` 删区域槽监听器 + `Saves` import |

## 额外发现

1. `Rules.java:73` 关于 `rules.zone` 恒为 null 的注释已过时（G8-5 报告内已记）。
2. `Door.java` 类注释原称"本项目 Block 无 solidifes 字段"，G8-2 补字段后已同步修正。
3. 客户端原本不转发任何 `/` 命令到服务端，是 G8-3 服务端命令此前"无法被触发"的根因（已在 ChatFragment 补齐转发）。
