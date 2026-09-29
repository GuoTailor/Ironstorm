# F-LOW 批报告（低优先级合集：A3 审计 + T0 组E 产出）

> 执行：fix-flow（子代理）。日期：2026-09-30。
> 约束：未跑任何 gradlew/编译命令、未做 git 操作（协议禁止）——**全部改动未经编译验证**，合并前请主会话跑 `gradlew :core:compileJava`。
> 共 17 个文件 +758/-70 行。逐条的文件:行号定位见各节。

---

## 一、内容详情面板（A3 低 + ②组E，2 条 TODO 消除）— 已完成

### 现状与依据
- 原版：`mindustry/ui/dialogs/ContentInfoDialog.java`（show → `content.displayInfo(table)`）+ `mindustry/ui/ContentDisplay.java` 的 displayItem:65 / displayLiquid:99 / displayMech:134 / displayUnit:182 + 各 Content 类的 displayInfo 覆写（grep 原版：**Block/Item/Liquid/Mech/UnitType/Zone(空)** 六处；**StatusEffect 没有**——原版 StatusEffect 是 `MappableContent`，连数据库都不进）。
- 本项目：`ContentInfoDialog.show` 只收 Block、`ContentDisplay` 只有 displayBlock、`DatabaseDialog.openInfo` 有"非 Block 就 return"守卫 → 点物品/单位没反应（todo-classified ②组E 两条：UnlockableContent.java:66/69）。

### 改动
| 文件 | 内容 |
| --- | --- |
| `world/meta/I18n.java` | 新增 `format(key, fallback, args...)`：键存在走 `Core.bundle.format`，缺失返回 fallback 原样（项目 bundle 是裁剪版，`Core.bundle.get(key, fallback)` 不生效） |
| `ui/ContentDisplay.java` | 照原版补 `displayItem/displayLiquid/displayUnit/displayMech` + 私有助手（iconOrBlank/addDescription×2/statLine/dividerRow）。统计行走 I18n.format，缺键 fallback 硬编码原版 bundle 原文 |
| `type/Item.java` / `type/UnitType.java` / `type/Mech.java` | 各补 `displayInfo(Table)` 覆写 → 转发 ContentDisplay（对应原版 Item.java:49 / UnitType.java:63 / Mech.java:117） |
| `type/Liquid.java` | **继承链不动**（转换.md:1728 刻意保留 MappableContent），就地补 `localizedName`（`liquid.<name>.name`）/`description`/`icon(Cicon)`（`liquid-<name>` 查找，与 ui/LiquidDisplay 同口径）/`displayInfo` |
| `ui/dialogs/ContentInfoDialog.java` | `show(Block, Stage)` 泛化为 `show(Content, Stage)`：UnlockableContent 走 displayInfo 分发、Liquid 单独分发；**StatusEffect/Zone 显式挡下**（原版就没有它们的详情面板，放行会弹空白框）。既有调用方（PlacementFragment:766 / TechTreeDialog:522 传 Block）不受影响 |
| `ui/dialogs/DatabaseDialog.java` | `openInfo` 解除 Block-only 守卫；数据库网格补列**液体**（原版数据库列液体，本项目此前因 Liquid 非 UnlockableContent 被过滤掉）——`isListable()` 判据 + Liquid 图标/点击分流；SHIFT+复制分支改用 `cName` 局部变量（Content 基类没有 name 字段） |
| `ctype/UnlockableContent.java` | displayInfo 空实现上的 TODO 注释改写为现状说明（②组E 两条 TODO 消除） |

### i18n 键核对（bundle.properties）
- 存在且直接用：`info.title/back/yes/no`、`item.explosiveness/flammability/radioactivity`、`liquid.heatcapacity/temperature/viscosity`、`unit.health/speed`、`mech.weapon/ability/buildspeed/health/itemcapacity/minespeed/minepower`。
- 缺失 → 硬编码原文 fallback：`item.corestorable`（原版 bundle.properties:844 `[lightgray]Storable in Core: {0}`）。
- ⚠ 语义不符（未用）：项目 bundle:387 `zone.requirement.complete` 文本是 "Wave {0} reached: {1} zone requirements met."，与原版 :472 "Requirement for {0} completed:…" **arg 顺序/含义不同**（疑来自其它版本/语境）→ 区域提示没走这个键（见第三节），如需本地化建议先修键。

### 消费点
- DatabaseDialog 条目点击（物品/液体/单位/机甲/方块全部可弹详情）；BlockStats 无现成点击挂点，未加（Block 详情本来就有统计表，符合原版）。

---

## 二、Administration 子网封禁/strict/Config（A3 中低）— 已完成

依据：原版 `mindustry/net/Administration.java`（subnetBans :63-79、Config :411-489、getStrict :125、allowsCustomClients :129）+ 原版 NetServer.java:81/109/174/515 消费。A3 审计指认：项目类注释只声明了聊天/动作过滤缺失，未声明这三块。

| 文件 | 内容 |
| --- | --- |
| `net/Administration.java` | ① `subnetBans` 字段 + `getSubnetBans/addSubnetBan/removeSubnetBan/isSubnetBanned`（前缀 startsWith 匹配，替代 arc 的 `contains(ip::startsWith)`）；持久化沿用本项目 admins/ 目录文件方案（新文件 `subnets.txt`，与 banned.txt 同构）。② 新增 `Config` 枚举全部 19 项（键名/默认值照抄原版；`Vars.port`/`Vars.headless` 项目均有），bool/num/string/get/set 走项目 `Core.settings`（set = put + save()，项目 Settings 无 putSave）。**差异**：socketInput 三项的 changed 回调原版 fire `Trigger.socketConfigChanged`（本地 TCP 控制台未移植）→ 留空。③ `getStrict()/allowsCustomClients()` 接 Config。 |
| `core/NetServer.java:424-429` | `onConnect` 封禁校验补 `admins.isSubnetBanned(con.address)`（对应原版 NetServer.java:109）——真实消费点接线 |

保留偏离（已有注释/既有设计）：项目 `playerLimit` 是运行期字段且已被 discovery/isServerFull 消费，不改成原版的 settings 版 `getPlayerLimit/setPlayerLimit`；白名单继续走字段 + 文件（`Config.whitelist` 项已立好但未接，`isWhitelistEnabled()` 未改）；ChatFilter/ActionFilter（antiSpam）仍缺（原声明保留）；Config 无 UI/命令行入口（留 TODO，原版消费方是 headless 控制台）。

---

## 三、EventType 缺失事件类（组③若干"事件未移植"阻塞解除）— 已完成

对照原版 EventType.java（400 行）逐类核对。**新增 22 个类/常量**（`game/EventType.java`）：

- Trigger 补 `socketConfigChanged`、`update`（原版 :32-33）。
- 类：`LaunchItemEvent`、`CommandIssueEvent`、`PlayerChatEvent`、`ZoneRequireCompleteEvent`、`ZoneConfigureCompleteEvent`、`ClientCreateEvent`、`ServerLoadEvent`、`ContentReloadEvent`、`TapEvent`、`TapConfigEvent`、`GameOverEvent`、`BlockBuildBeginEvent`、`BuildSelectEvent`、`UnitCreateEvent`、`MechChangeEvent`、`PlayerJoin`、`PlayerConnect`、`PlayerLeave`、`PlayerBanEvent`、`PlayerUnbanEvent`、`PlayerIpBanEvent`、`PlayerIpUnbanEvent`。每个类注释标了原版行号、原版派发点、本项目是否接线。

**接线（有现成消费点/阻塞 TODO 的三组）**：
1. **BuildSelectEvent**（原版 BuilderTrait.java:84 fire → BuilderDrone.java:115 消费）：
   - `entities/traits/BuilderTrait.java:113-120`：updateBuilding 里占位方块初次 `initialized` 时 `Gdx.app.postRunnable` fire（B 类注释转正）。
   - `entities/type/base/BuilderDrone.java` 构造器：恢复"对方块开始反方向建造时停手"联动（`unitGroup.all()` → 既有映射 `Units.units`；`getTeam()` 闭包语义照原版）。类 javadoc 对应条目同步改写。
2. **ZoneRequireCompleteEvent / ZoneConfigureCompleteEvent**（Zone.java:152 TODO 的"阻塞原因"即缺这两个类）：
   - `type/Zone.updateObjectives` 按原版 Zone.java:113-127 实现（incomplete 扫描 → closure → 逐个 fire；`content.zones()` → `Vars.content.getBy(ContentType.zone)`，⚠ 返回的是 libgdx Array、与项目 struct.Array 不同，用 FQN 接）。
   - `core/Control.java`：补两个监听器（对应原版 Control.java:153-163）弹提示。两处等价降级已注明：原版 `ui.hudfrag.showToast` → 项目 `UI.showInfoFade`；bundle 的 `zone.requirement.complete` 键语义不符、`zone.config.unlocked` 缺 → 硬编码原版英文原文。
3. **GameOverEvent**（原版 Logic.java:147/165/198 fire → Control.java:103 消费）：`core/Logic.checkGameOver` 两个分支按原版 fire；原版消费逻辑（结算统计/弹结算框）本项目本就内联等价，事件先立起来供后续挂接。

**类已立、暂不 fire**（原版核心消费者也是 mod 或未移植子系统，硬接=死代码；注释已写明派发点）：ClientCreateEvent（原版 ClientLauncher:36）、ServerLoadEvent（无 headless 循环）、ContentReloadEvent（原版 Mods:452，mod 系统降级；MapRenderer:62/TechTree 的对应 TODO 可自行挂）、TapEvent/TapConfigEvent（原版 @Remote chokepoint，项目 tapped 调用点分散）、BlockBuildBeginEvent、UnitCreateEvent、MechChangeEvent（Player 无机甲装备）、CommandIssueEvent（CommandCenter 已退役）、LaunchItemEvent（发射链未移植）、PlayerChatEvent、PlayerJoin/Connect/Leave（自研网络无对应分发点）、PlayerBanEvent 系四个（ Administration 走文件持久化，未加 fire——原版这些事件核心包内也无监听者）。

**未动**：`WithdrawEvent/DepositEvent/CoreItemDeliverEvent` 项目已有但字段是空壳（原版带 tile/player/item/amount）——项目 Tutorial 监听只记账、字段无人读，扩字段属纯形状改动，保留现状。

---

## 四、小项考据（F-LOW-4）

| 项 | 结论 | 理由 |
| --- | --- | --- |
| Schematics.overwrite（原版 game/Schematics.java:93，消费点 InputHandler.showSchematicSave:261） | **跳过** | 消费链整体缺失：`showSchematicSave` 需要 `ui.showTextInput`（文本输入），项目无此设施（MobileInput.java:44 已声明该降级；桌面蓝图创建也是"固定半径复制"的降级形态）。加方法=死代码 |
| SpawnGroup 序列化（原版 SpawnGroup.java:75-93 write/read；项目 SaveMeta:40 明确"spawns 不入档、开局重建"） | **留 R1/V1** | 触碰存档管线：RulesMeta 白名单加字段要三处同步（RulesMeta/writeMeta/applyMeta）+ SaveVersion:194 读侧改判据 + 存档读回验证。我禁跑 gradlew，存档格式改动无运行验证不能交。且 WaveInfoDialog 会话内编辑可用、仅跨存档丢失，收益有限。**建议做法已写明**：SpawnGroup 实现 JsonSerializable 照抄原版（type 存 name、effect 存 id，避开 JsonIO 无 UnitType/StatusEffect 序列化器的坑） |
| SoundLoop 工作音效（Blocks.java:1043/1382 activeSound 缺依赖） | **跳过** | 根因是 A 类依赖：`Sounds.load()` 在 ClientLauncher.java:134 仍是注释状态（音频系统整体未启用），接了也没有任何可听输出。待音频系统启用后按原版 GenericCrafter/ItemLiquidGenerator 的 updateTile 挂 SoundLoop |
| GlobalData 资源池（Block.useContent → 战役生产解锁） | **无需做（已完成）** | `world/Block.java:1356 useContent` 已按原版接线 `data.unlockContent`（S4a 批落地、后续批接通），GenericCrafter/LiquidConverter/Pump/Separator/SolidPump 均已调用。todo-classified 组I 的 coreselect 同理已在 game/Schematics.java:207 接线（F18） |
| TileEntity 休眠优化 | **跳过** | 性能项，无任何性能问题证据；BlockStorage.java:90/97/111 已声明"本项目无方块休眠机制"的等价偏离。照搬原版 noSleep 体系反而要动 TileEntity 生命周期 |

---

## 五、遗留与风险

1. **未经编译验证**（协议禁跑 gradlew）。风险点自查已做（参数改名 `Content content` 与字段 `Table content` 撞名处已用 `this.content`；libgdx/struct 两套 Array 混用处用 FQN；`objective::equals` 做 Boolf 的方法引用；BuilderDrone 里 `drone.target` 的 protected 访问在同包子类内合法）。请主会话编译把关。
2. **建议主会话实拍验证**（PHOENIX_SHOT 走数据库点开物品/单位详情）：数据库多了一排液体图标、详情面板渲染是否正常（`[lightgray]` 标记解析依赖 Colors.put，已确认 lightgray/lightGray 都注册）。
3. Config 枚举暂无 UI 入口——后续如做服务器设置界面直接消费 `Administration.Config.all`。
4. `zone.requirement.complete` 键文本与原版不一致（疑裁剪来源错位），本项目未消费；如发现其它消费点需注意 arg 顺序。
