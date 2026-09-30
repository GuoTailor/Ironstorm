# A7 审计：net / io / game / maps / editor / core（逻辑与元系统）

> 审计范围：本项目 `com/phoenix/game/{net, io, game, maps, editor, core(逻辑与元系统)}` ↔ 原版 `mindustry/{net, io, game, maps, editor, core(Control/Logic/ContentLoader/Vars/GameState)}`。
> 判据：**行为级**对照；原版有、本项目无（或行为不同）、**且无注释声明刻意偏离**才报。
> 已读前置：`2026-09-29-full-port-completion.md` 批次登记表（F1~F18/T1/R1/V1）、`2026-09-30-round2-gap-audit.md`、`audit-A1/A2/A3-*.md`、`todo-classified.md`、`f-low-report.md`、`r1-report.md`。A3 已登记的项（UnlockEvent/screenshot/pause_building/pick/rotateBlock/drawConfigure/buildUI/isDroppingItem/diagonal_placement/Administration subnetBans-strict-Config/canShoot/ContentInfoDialog/MapIO.writeMap/ContentLoader.loadColors/votekick）**不重复计数**。
> 已知刻意偏离（未报）：存档自有 PHNX 格式（SaveIO v7 / SaveVersion 死兼容层）、Rules 走 SaveMeta.RulesMeta 手工白名单、程序化地图、命令状态不入档、`rules.infiniteResources`→`rules.sandbox` 等价、`playerLimit`/白名单自有设计、Administration socketInput 三项留空、BeControl 降级、音频系统未启用（`Musics/Sounds.load` 注释态）、`Rules.copy()`（MapEditorDialog 已声明降级）、`Maps.writeWaves/readWaves`（WaveInfoDialog 已声明 TODO）、`Maps.ShuffleMode`（Maps 类注释已声明）、`Map.name()`（Map 类注释已声明不移植）、`ContentLoader.createModContent/loadColors/logContent/dispose`（类注释已声明）。
> 未逐字段展开（自研/已声明豁免）：`net/Packets.java`、`net/PacketSerializer.java`、`net/PhoenixNetProvider.java`、`io/SaveIO.java`、`io/SaveVersion.java`、`io/SaveMeta.java`。

## 摘要：缺失 3 / 偏离 6 / 断裂 5

最严重的三条：
1. **`Rules.waveSpacing` 默认值是原版的一半**（3600 vs 7200 tick）→ 战役/自定义图每波间隔 60s（原版 120s），玩法节奏整体快一倍（`game/Rules.java:14` vs 原版 `Rules.java:56`）。**中**
2. **`GlobalData.addItem` 不更新 `state.stats.itemsDelivered`**（原版 `GlobalData.java:92`）→ 区域结算框的"交付资源清单"恒为空、`Stats.calculateRank` 恒算 0 分（消费方齐全、生产方缺失）→ 评级永远是最低档。**中**
3. **`Rules.dropZoneRadius` 默认值偏小**（200 vs 原版 300）→ 开波"清场"冲击波/伤害半径小 1/3，玩家可更靠近出生点建塔而不被清（`game/Rules.java:26` vs 原版 `Rules.java:52`）。**中**

---

## 一、缺失（原版有、本项目完全没有）

| # | 原版位置(file:line) | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| 1 | `net/NetServer.java:272-458`（`registerCommands()`：`/help`、`/t`、`/sync`、`/vote`、`/votekick`）+ `NetClient.java:160-190` 分发 | 服务端聊天命令系统（`CommandHandler clientCommands`，玩家发的 `/xxx` 由服务端解析执行） | `core/NetServer.java:537-541` 的 `onChat` 只 `broadcastChat`，无任何命令解析；仅 `ui/fragments/ChatFragment.java:189-224` 有 4 条**客户端本地**命令（`/help /ping /disconnect /reconnect`） | 中低 | 在 NetServer 挂 `CommandHandler("/")` 并按原版注册 `/t`（队伍频道）、`/sync`、`/vote`、`/votekick`、服务端 `/help`；`onChat` 先走命令分发。注意 `votekick` 已在 PlayerListFragment 声明，其余 4 条未声明 |
| 2 | `net/NetServer.java:792 fixName` / `:816 checkColor` / `:161 maxTextLength` 校验 | 玩家名清洗（去非法字符/截断/去重）与颜色合法性校验、聊天长度校验 | `core/NetServer.java:446` 直接 `player.name = p.name`；`onChat` 不校验长度 | 低 | 按原版补 `fixName`/`checkColor`，`onConnect` 与 `onChat` 调用（联机安全/体验） |
| 3 | `core/Control.java:103-114`（`Events.on(GameOverEvent...)`） | 游戏结束时删除**区域存档槽**（`saves.getZoneSlot().delete()`，非教程局） | `core/Control.java` 无 `GameOverEvent` 监听器；`Saves.getZoneSlot()`/`delete()` 已存在但无调用点 → 打完/打输的区域存档槽不会被清，可被"继续战役"反复载入已结束的区域 | 低 | 在 Control 构造器补 `GameOverEvent` 监听器照原版删槽（`rules.zone != null && !net.client() && !tutorial`） |

---

## 二、偏离（两边都有、行为/数值不同，且无声明）

| # | 原版位置(file:line) | 项 | 本项目 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| 1 | `game/Rules.java:56` `waveSpacing = 60*60*2`（7200） | 基础波次间隔 | `game/Rules.java:14` `= 60f*60f`（3600）→ 战役/自定义图每波 60s（原版 120s）。注释写"默认 60 秒"，未声明是刻意偏离 | 中 | 改回 `60 * 60 * 2`；`io/SaveMeta.java:51` 的 `RulesMeta.waveSpacing` 默认同步改（只影响新档默认值） |
| 2 | `game/Rules.java:52` `dropZoneRadius = 300f` | 出生点清场半径 | `game/Rules.java:26` `= 200f`。消费点 `ai/WaveSpawner.java:126-127`（冲击波特效 + 99999999 伤害清场）→ 半径小 1/3 | 中 | 改回 `300f`；`SaveMeta.java:53` 同步 |
| 3 | `game/GlobalData.java:84-97`（`:92` `state.stats.itemsDelivered.getAndIncrement(...)`） | 全局入库时累加"已交付物品"统计 | `game/GlobalData.java:41-49` 的 `addItem` 只改 `items[]`/解锁，**不写** `state.stats.itemsDelivered` | 中 | 在 `addItem` 里补 `Vars.state.stats.itemsDelivered.getAndIncrement(item, 0, amount)`（配合溢出钳制）。消费方已全（`GameOverDialog.java:127`、`Stats.java:70`） |
| 4 | `game/SpawnGroup.java:60-72`（`:67-69` `if(items != null) unit.addItem(...)`） | 波次单位携带物品（自爆/燃烧弹编成） | `game/SpawnGroup.java:56-66` 的 `createUnit` **不施加 `items`**；`items` 字段存在但无消费者。当前内置波次未设 `items`，故暂无可见影响，但自定义/区域波次设了就失效 | 中低 | `createUnit` 里补 `if(items != null) unit.addItem(items.item, items.amount)` |
| 5 | `core/GameState.java:41-43` `isPaused() = (is(paused) && !net.active()) || (gameOver && !net.active())` + `Logic.java:218` `if(!state.isPaused()){...}` 守卫 | 游戏结束后冻结仿真 | `core/GameState.java:67-69` `isPaused()` 只判 `state==paused`；`core/Logic.java:34` 的 `update` 无 isPaused 守卫；`ClientLauncher.java:572` 只在 `state.isPlaying()` 时调 `Logic.update()`，而 `gameOver` 不改 state → **游戏结束后单位仍移动/开火、玩家仍可操作**（`GameOverDialog` 未置 paused） | 中 | `isPaused()` 补 `gameOver` 分支（或 `GameOverDialog.show` 置 `State.paused`），使结算期间冻结仿真与输入（输入层已多处依赖 `isPaused()`） |
| 6 | `game/Team.java:32-39`（占位阵营 6..255 用 `Color.HSVtoRGB(360*random,...)`） | 额外阵营颜色 | `game/Team.java:33-38` 占位阵营一律 `Color.WHITE.cpy()` → 编辑器/队伍索引里 6 号以后队伍颜色全白、不可区分 | 低 | 照原版用随机 HSV（或至少不同色相）初始化占位阵营颜色 |
| 7 | `game/Rules.java:22` `waves`（默认 false）、`:80` `loadout = Array.with(ItemStack.with(Items.copper,100))` | 规则默认值 | `game/Rules.java:10` `waves = true`、`:18` `loadout = {}`（空）。战役 `Control.play()` 用 `new Rules()` 依赖 `waves=true`，与 `Gamemode.survival` 显式置位语义不同；`loadout` 空由 `playMap` 兜底 | 低 | 与 #1/#2 一并复核默认值（`waves` 建议照原版 false，由 Gamemode 置位；`loadout` 默认给 copper×100） |
| 8 | `game/DefaultWaves.java:12-187`（20 组，含 items/overdrive/shielded、revenant/lich） | 默认波次编成 | `game/DefaultWaves.java` 仅 10 组、数值与起始波次均不同，且**未用** `items`/`revenant`/`lich` | 低（**已声明**"最小实现"） | 属已声明偏离；若 `UnitTypes.revenant/lich` 已存在，可低成本补齐（建议复核后决定） |

---

## 三、断裂（本项目已有实现但没接线/无消费点）

| # | 原版位置(file:line) | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| 1 | `ClientLauncher.java:133` `Events.fire(new ClientLoadEvent())` | `ClientLoadEvent` 派发 | 全工程 **无任何 `new ClientLoadEvent()`**（grep 只命中定义与两个监听器）；消费方 `game/MusicControl.java:37`（`reload()`）、`game/Tutorial.java:91`（`stage.load()`）永不执行 | 中（结构） | 在 ClientLauncher 资源就绪后 fire 一次（`TutorialStage.text()` 有懒加载兜底，但 `MusicControl` 无） |
| 2 | `core/Control.java:52-54`（`music = new MusicControl()`）+ `Control.java:418` `music.update()` / `:419` `loops.update()` | `MusicControl`/`LoopControl` 实例化与每帧驱动 | 全工程 **无 `new MusicControl()` / `new LoopControl()`**，`Vars` 也无 `music`/`loops` 字段 → 音乐整链（含菜单曲 `Musics.menu`、战斗随机曲、暗色曲）从不运行 | 中低 | 在 Control/Vars 里实例化并每帧 update（下游于已声明的"音频未启用"，音频恢复后即需接） |
| 3 | `core/Logic.java:211` `Events.fire(Trigger.update)` | `Trigger.update` 每帧派发 | `core/Logic.java:34` 的 `update` 不 fire；`game/EventType.java:37-40` 的注释已写"update 在原版 Logic.java:211 每帧 fire"但未接 → 枚举存在、注释承诺、实际不派发 | 低 | 在 `Logic.update()` 开头 `Events.fire(EventType.Trigger.update)` |
| 4 | `core/GameState.java:32-35` `set(State)` 内 `Events.fire(new StateChangeEvent(state, astate))` | `StateChangeEvent` 派发 | `core/GameState.java:79-81` 的 `set` 只赋值、不 fire；全工程无 `new StateChangeEvent`（定义与构造器外 0 引用） | 低 | 在 `set` 里 fire（原版消费方是 Control 的 updateRPC/停音，本项目暂无，但形状应保真） |
| 5 | `core/Logic.java:105` `new PlayEvent()` / `core/Renderer.java:159` `new DisposeEvent()` | `PlayEvent` / `DisposeEvent` 派发 | 全工程均无 `new PlayEvent()` / `new DisposeEvent()`（仅定义） | 低 | 按原版派发点补 fire（PlayEvent 在进入 playing 时，DisposeEvent 在 renderer.dispose 时） |

---

## 四、已核对为"无问题 / 已声明"的项（供交叉复核，不计问题）

- `game/EventType.java`：类定义已与 126.2 逐条对齐（F-LOW 补齐 22 类）；`WithdrawEvent/DepositEvent` 空字段为 f-low 已声明保留项。
- `game/Objectives.java` / `Objective.java`：与原版逐行一致（`Wave` 两边同为 TODO）。
- `game/Gamemode.java`：五模式规则集与 `bestFit` 顺序一致（`infiniteResources`→`sandbox` 已声明）。
- `game/Stats.java`：字段 + `calculateRank` 与原版一致（rank 除法为 int 除法的注释正确）。
- `core/Control.java`：ResetEvent/WaveEvent/BlockBuildEnd/BlockDestroy/UnitDestroy/Unlock/ZoneRequire/ZoneConfigure/Trigger.newGame 监听器齐全；`checkUIScaleChanged`、`playZone/playTutorial/playMap` 均带完整差异声明。
- `core/Logic.java`：WaveEvent 静态监听、brokenBlocks 队列改由 `Control` 两监听器维护（注释已说明）、`checkGameOver` 双分支照原版。
- `core/ContentLoader.java`：`zones()` 缺（原版 `:266`），但 `Zone.updateObjectives` 已用 `content.getBy(ContentType.zone)` 等价（F-LOW）；其余缺项均类注释声明。
- `core/Vars.java`：`screenshotDirectory`/`logic`/`loops`/`becontrol`/`defaultWaves`/`bulletGroup`/`tileGroup` 等缺项分别对应 A3/F13 已声明（截图）或自研结构（静态数组）。
- `game/Saves.java`：`getTotalPlaytime()`/`importSave()`/`getNextSlotFile()`/`resetSave()` 缺，但 `getTotalPlaytime` 已在 `SaveVersion.java:91-93,158-159` 声明降级、`resetSave` 由 `resetCurrent()` 等价（`play()/playMap()` 调用）。
- `maps/Maps.java`、`maps/Map.java`、`maps/filters/*`、`maps/generators/*`：文件级无缺口；`Maps` 缺 `writeWaves/readWaves/ShuffleMode/tryCatchMapError/loadInternalMap/saveMap` 均已有注释声明（WaveInfoDialog / MapsDialog / MapEditorDialog / MapGenerator）。
- `editor/*`：文件级与 `MapEditor`/`MapView`/`MapRenderer` 方法级对齐；`MapRenderer` 的 IndexedRenderer 降级、`MapEditorDialog.saveMap` 缺失等均逐条声明。
- `net/Net.java`：`showError()`/`reset()` 缺（A3 已声明）；`compressSnapshot` 用 zlib 替代 LZ4（类注释声明）；其余结构对齐。
- `net/Administration.java`：ChatFilter/ActionFilter/PlayerAction 缺（A3/f-low 声明）；白名单/playerLimit 为项目自有设计（f-low 声明）。

---

## 五、备注 / 未完全核实

- `net/Packets.java`（767 行自研）、`net/PacketSerializer.java`、`net/PhoenixNetProvider.java`、`net/TypeIO.java`（精简版，60 行 vs 原版 `io/TypeIO.java` 367 行）属自研协议栈，未做逐包与原版 `@Remote` 对照（A3 已声明豁免）。
- `io/SaveIO.java`（871 行自研）、`io/SaveVersion.java`（510 行）、`io/SaveMeta.java` 的字段级保真未展开（存档自研豁免）。
- `editor/MapEditorDialog`、`editor/MapInfoDialog`、`editor/MapGenerateDialog`、`editor/MapResizeDialog`、`editor/MapSaveDialog`、`editor/WaveInfoDialog`、`editor/OperationStack` 未逐方法展开；抽查未见未声明的高优先级缺口。
- `game/Schematics.java` 的 `overwrite`（原版 `:93`）已在 F-LOW 声明跳过；`Schematic` 缺 `compareTo`（原版实现 Comparable）属低价值边角，未计入。
