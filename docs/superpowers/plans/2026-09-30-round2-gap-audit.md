# 第二轮缺口普查（Round 2 Gap Audit）——计划与执行协议

> 上一轮（`2026-09-29-full-port-completion.md`）已完成：A1~A3 审计 → F1~F18 修复 → T1/R1 清理 → V1 终验通过
> （编译/实跑/截图/联机 dart-ship/数值 dump 零差异/dist 打包 44.5MB）。
> 本轮目标：**对当前 HEAD 做一轮全新的静默缺口普查**，找上一轮审计（有限轮次）未覆盖或新引入的缺口，再按批次修复。

## 审计口径（严格执行，避免重复劳动）

**报告条件（三者同时满足才报）**：
1. 原版（`D:\IdeaProjects\Mindustry-126.2`）**有**该功能/字段/方法/行为；
2. 本项目（`D:\IdeaProjects\phoenix`）**没有**（或行为不同）；
3. **且代码里没有注释声明这是刻意偏离**。

**必读前置（不读就报 = 无效报告）**：
- `docs/superpowers/plans/2026-09-29-full-port-completion.md` 的批次登记表（F1~F18/T1/R1/V1 已完成项清单）
- `docs/port/` 下既有报告：`audit-A1/A2/A3-*.md`、`todo-classified.md`、`silent-*`（S 系列）、`f1~f18-report.md`、`t1-report.md`、`r1-report.md`、`f-low-report.md`

**已知刻意偏离（不要报，除非发现"声称偏离但实际可低成本补齐"）**：
- `acceptItem` 默认语义（本项目"有空间就收"）、坐标口径（worldx 角点 vs 原版中心）、rotation 相差 90°、d4 旋向、
  数值折算一律照抄原版、`PowerVoid/MechPad` 等与原版不同是刻意的、液体已不再是排除项。
- 已声明降级：bloom（no-op，真实现步骤清单在 `graphics/Bloom.java` 注释）、SoundLoop（音频未启用）、
  TileEntity 休眠优化（无性能证据）、命令状态不入档（原版即如此）。

**分类（沿用 A1~A3 口径）**：
- **缺失**：原版有、本项目完全没有（类/方法/字段/子系统）。
- **偏离**：两边都有但行为/数值/语义不同（且无声明）。
- **断裂**：本项目已有实现但**没有接线**（无消费点/调用点缺失）。

**严重度**：高（玩家可感/影响玩法正确性）、中（功能可用但表现不对）、低（边角/不可见）。

## 分片（4 个并行 Explore 审计，互不重叠）

| 片 | 范围 | 报告 |
| --- | --- | --- |
| A4 | `world/`（blocks 全子包、consumers、meta）、`content/`、`type/` | `docs/port/audit-A4-world-content.md` |
| A5 | `entities/`（type/units/bullet/effect/traits）、`ai/`、`ctype/` | `docs/port/audit-A5-entities-ai.md` |
| A6 | `ui/`（fragments/dialogs）、`input/`、`graphics/`、`core/` 图形部分（Renderer/Draw/Drawf/Fill/Lines/Fonts） | `docs/port/audit-A6-ui-input-graphics.md` |
| A7 | `net/`、`io/`、`game/`、`maps/`、`editor/`、`core/` 逻辑与元系统（Control/Logic/ContentLoader/Vars/Settings） | `docs/port/audit-A7-net-io-meta.md` |

## 执行协议（与上一轮一致）

1. **主会话**：派发（brief 含全部背景，子代理看不到会话）→ 读摘要 → 抽查 diff → **编译** → **专项实测**（环境变量钩子，用完即删）→ 提交 → 勾选登记表 → 派下一批。
2. **子代理**：只改代码 + 写报告，**禁止运行 gradlew/任何编译命令**，**禁止 git 操作**；详细产物写 `docs/port/<批次>-report.md`；回摘要 ≤200 字。
3. 每批提交信息格式：`port(G<n>): <一句话>`；修 bug 用 `fix(<area>): <一句话>`。
4. 主会话保持上下文干净：子代理的探索过程不进主会话，只有摘要 + 报告落盘。

## 审计结果汇总（2026-09-30）

| 片 | 报告 | 缺失 | 偏离 | 断裂 | 备注 |
| --- | --- | --- | --- | --- | --- |
| A4 | `docs/port/audit-A4-world-content.md` | 8 | 5 | 3 | 文件级 1:1 |
| A5 | `docs/port/audit-A5-entities-ai.md` | 2 | 6 | 4 | 文件级 1:1 |
| A6 | `docs/port/audit-A6-ui-input-graphics.md` | 3 | 3 | 4 | +1 存疑（S1 已裁决算缺口→G7） |
| A7 | `docs/port/audit-A7-net-io-meta.md` | 3 | 6 | 5 | 文件级 1:1 |
| **合计** | | **16** | **20** | **16** | 52 项 |

## 批次登记表

| 批次 | 主题 | 内容（来源项） | 状态 |
| --- | --- | --- | --- |
| G1 | 物流与库存记账 | Conveyor 卸载不扣 items（A4-D1，**高·复制漏洞**）+ Conveyor acceptStack/handleStack 覆写（A4-M3）+ Drill/GenericCrafter/UnitFactory shouldConsume（A4-M4，满仓不停料）+ Turret 基类 updateShooting 乘 baseReloadSpeed（A4-B2）+ Block.getReplacement/Conveyor·Conduit 覆写（A6-B3 前半） | ☑（提交 0183242）；**实测：handleStack items=3/len=3 一致 → 3 个物品送达 container、带子库存归零**（修复前会残留 3 个幽灵计数）；交付后修 GenericCrafter 缺 Tile import；UnitFactory 用 aliveCount() 替代原版 spawned 字段（避免升存档版本，O(N) 可忽略，已批准） |
| G2 | 状态与伤害链 | getSpeedMultiplier 接线（A5-1，**高**）+ BaseUnit.collision 用 damage() 并补 onHit/killed（A5-2）+ getDamageMultipler 接线/Bullet.damageMultiplier（A5-3）+ StatusEffect.update 的 /60 复核修正（A5-偏离）+ onHit(deflect)（A5-M2）+ countsAsEnemy 消费（A5-断裂） | ☑（提交见下）；**实测：无状态 \|v\|=9.5594 → freezing \|v\|=7.6874 → 清除后精确恢复 9.5594；比值 0.8041745 与 `1+(0.6−1)×delta(0.489564)=0.8041744` 逐位吻合**；遗留：onHit(deflect) 因 DeflectorWall 简化（无 resetOwner）当前不触发，记入 G9；玩家版 getDamageMultipler 多乘 unitDamageMultipler（Player 非 Unit 架构差异，已注释声明） |
| G3 | 事件派发补齐 | ClientLoadEvent（A7-B1）+ Trigger.update（A7-B3）+ StateChangeEvent（A7-B4）+ PlayEvent/DisposeEvent（A7-B5）+ CoreItemDeliverEvent（A4-B1，教程卡住）+ MusicControl/LoopControl 实例化（A7-B2） | ☑（提交见下）；**实测（PHOENIX_G3TEST）：clientLoad=1 / play=1 / trigger=200（=帧数 200）/ stateDelta=2 / coreItemDelta=1 / musicAmbient=4（reload 跑过）/ disposeFired=1 全部符合预期**；顺带修 `EventType` 的 `State` import 误写（`ai.fsm.State`→`GameState.State`，不修则 StateChangeEvent 签名不兼容）；刻意偏离：music 放 Vars（原版在 Control）、PlayEvent 用 clearUnits 区分新局/读档、Trigger.update 仅游玩中派发（本项目 Logic.update 只在 isPlaying 调） |
| G4 | 规则与数值 | waveSpacing 7200（A7-D1）+ dropZoneRadius 300（A7-D2）+ addItem itemsDelivered（A7-D3）+ SpawnGroup items（A7-D4）+ GameState.isPaused gameOver 分支（A7-D5，结算不冻结）+ Team 占位颜色（A7-D6）+ Rules 默认值复核（A7-D7） | ☑（提交见下）；**实测（PHOENIX_G4TEST）：gameOver 时 isPaused=true 且 30 帧位移=0（冻结）→ 清 gameOver 后 30 帧位移=52.6（恢复）；waveSpacing=7200/dropZoneRadius=300/waves=true；team6=0.96,0.23,0.22、team255=0.917,0.07,0.065（非白、互异）；itemsDelivered copper 0→1**；交付后主会话修编译错：libgdx 无静态 `Color.HSVtoRGB` → 改 `new Color().fromHsv(...)`；G4-7 结论：`Rules.waves` 改回原版 false，`Control.play()/playZone()` 各显式补 `waves=true`（playMap 已套 Gamemode）；`loadout` 保留空数组（Items 晚于 `new Rules()` 初始化，已声明偏离+playMap 等价兜底） |
| G5 | 图形与特效 | 武器 shootEffect/smokeEffect（A4-M1）+ FlyingUnit.wobble/drawEngine（A5-M1/断裂）+ BasicBulletType 尺寸、MassDriverBolt 特效、GroundUnit 液体着色（A5-偏离×3）+ Battery 电量 draw、RepairPoint 旋转炮塔、MendProjector efficiency（A4-M2/M7/D4）+ shieldBuffer dispose（A6-B4） | ☑（提交见下）；**实测截图：电池大（3×3）色块+顶盖可见、维修点炮塔按 45° 旋转、dart-ship 引擎尾焰可见**；补 `battery-top`/`battery-large-top` 贴图并重打图集（区域名已校验）；**交付后主会话修 1 个逻辑 bug：`Battery.draw` 漏 `super.draw(tile)`**（本项目 `Block.draw` 才是本体绘制点，不调 super 会丢电池底图；NuclearReactor/LightBlock 均调）→ 已修；Fill 渲染经 size=100 极端测试确认正常（小电池色块被顶盖贴图遮挡属美术预期） |
| G6 | 设置项消费接线 | effects 开关（A6-B1）+ minimap 开关（A6-B2）+ blockreplace 应用（A6-B3 后半）+ 右键拆除模式（A6-D2）+ 跳波 canSkipWave 门控（A6-D3）+ 死亡相机跟随核心（A6-D1） | ☑（提交 863fdb1）；**实测 effects 开关与 minimap 显隐（中心区域亮度 134.6 vs 135.9，跨运行整图对比因随机地图无效，改用区域亮度 + 并排裁剪）**；⚠ 工作区含用户未提交并行改动，提交时用 hunk 级选择性暂存避开 |
| G7 | 战役发射链（裁决算缺口） | Logic.launchZone + state.launched + zone.setLaunched + HUD 发射按钮/倒计时 + Objectives.Launched（A6-S1） | ☑（提交 d4be6de）；**实测整链通过**；发现并规避 `Time.run` 在 gameOver 后不推进的坑（tick 队列被 isPaused 守卫包住） |
| G8 | 世界/寻路/网络 | Tile.updateOcclusion cost（A4-D2，寻路不避水）+ validPlace Units.anyEntities（A4-D3）+ 服务端聊天命令（A7-缺失1）+ 玩家名/颜色/长度校验（A7-缺失2）+ 游戏结束删区域槽（A7-缺失3）+ CoreBlock 重生链（A4-M8/D5，低） | ☑（提交 5c9add6）；**实测 cost 累加与实心块压单位放置判定**；M8/D5（CoreBlock 重生链，需改用户 WIP 的 Player.java）跳过 |
| G9 | UI 小项与低优先 | SHIFT 复制图标（A6-M1）+ 聊天滚轮（A6-M2）+ setHudText（A6-M3）+ ItemModule/ItemStack 死方法（A4-M5/M6）+ UnitCreateEvent（A4-B3）+ DefaultWaves 复核（A7-D8） | ☑（提交 41e5b94）；**实测 DeflectorWall 归属改换与 DefaultWaves 24 组**；M3/M5/M6 复核后跳过（已在别处实现） |
| G10 | `Fill.square` 语义系统性错配（G5 期间新发现） | 本项目 `Fill.square(x,y,size)` 第三参是**边长**，而 arc 的 `Fill.square(x,y,radius)` 是**半径**（arc 内部 `rect(x,y,radius*2,radius*2)`）→ 凡从 arc 原样搬来、传半径的调用点（`content/Fx.java` 多处、`Lightning.java` 等）实际只画出**一半尺寸**。G5 的 Battery 已按半径语义正确换算（`tilesize*size-2`）。**需逐调用点审计**（哪些是照抄 arc 的半径、哪些是本项目自写的边长），再决定「改 Fill 语义 + 全量换算」还是「逐点 ×2」 | ☑（提交见下）；**方案：改 `Fill.square` 为 arc 半径语义（`rect(x,y,radius*2,radius*2)`）+ 换算自写调用点**；审计结果：照抄 arc 的调用点 19 处（`Fx.java` 18 + `Lightning.java` 1，语义自动归位）、自写点仅 `Fx.unitDebris` 1 处（尺寸 /2）、`Battery` 的旧补偿 `tilesize*size-2` 改回原式 `tilesize*size/2-1`（渲染结果不变）；**实测（PHOENIX_G10TEST）：洋红参照方块半径 4 实测 47px（真实≈48），ppu=camerascale 硬上限 6 → 1 格 48px，证明 radius 4 画出 8 单位=1 格；旧的边长语义只会是 24px（需 ppu=11.75>6，不可能）** |
| G11 | `Lines.square` 4 参形式 √2 语义差（G10 期间新发现） | 本项目 `Lines.square(x,y,rad,rot)` 把 rad 当**半边长**，而 arc 的 4 参版本是 `poly(x,y,4,rad,rot-45)`（**外接圆半径**）→ 同一 rad 本项目大 √2 倍。⚠ 但 arc 的 **3 参**版本 `square(x,y,rad)=rect(x-rad,y-rad,rad*2,rad*2)` 恰恰是**半边长**，故 3 参调用点（`Renderer`/`Block`/`ItemBridge`/`Fx` 多处）**不受影响、必须保持现状**。仅 4 参受影响：`Drawf.square`（2 处，已有文档化偏离说明）+ `Fx.attackCommand`（1 处，本项目自写）| ☑（提交见下）；**方案：拆开两个重载——3 参独立走 `rect(x-rad,y-rad,2rad,2rad)`（半边长，与 arc 3 参逐字等价）、4 参去掉 `×1.4142135f`（外接圆半径，与 arc `poly(...,rot-45)` 逐字等价）**；审计：全量 17 处调用中 4 参仅 3 处（`Drawf` 2 + `Fx.attackCommand` 1），14 处 3 参不受影响；**实测（PHOENIX_G11TEST）：同 radius=8 下 3 参外框 102px、4 参 74px，减描边 6px 后 96 vs 68 = 16 vs 11.31 世界单位，比值 1.412≈√2，两框均轴对齐** |
| G-V | 终验 | 编译 + autoplay 实跑 + 关键专项实测（G1 复制漏洞 / G2 状态减速 / G4 波次间隔 / G7 发射链）+ dist 打包 | ☐ |

## 进度日志（每批一行，主会话维护）

- 2026-09-30 第二轮方案建立（上一轮 V1 已通过）；派发 A4~A7 四片并行审计
- 2026-09-30 四片审计完成（合计 缺失16/偏离20/断裂16）；A6-S1 战役发射链裁决为缺口（注释是状态陈述非偏离声明）→ G7；建 G1~G9 + G-V 登记表；派发 G1
- 2026-09-30 G1 完成：复制漏洞修复 + 实测闭环（提交 0183242）；顺带 gitignore 调试产物（3feb0c6，此前误提交 run-out/run-err）；派发 G2
- 2026-09-30 G2 完成：状态速度倍率/碰撞伤害接口/伤害倍率/DoT 去 /60/countsAsEnemy 全部接线（提交 2cbbf48）；实测状态减速逐位吻合；派发 G3
- 2026-09-30 G3 完成：6 个事件派发点 + MusicControl/LoopControl 实例化（提交见下）；实测 7 项计数全部符合预期；顺带修 EventType 的 State import 误写；派发 G4
- 2026-09-30 G4 完成：waveSpacing/dropZoneRadius/itemsDelivered/SpawnGroup items/isPaused gameOver/Team 颜色/Rules 默认值（提交见下）；实测 gameOver 冻结与恢复、数值与统计全部符合；修 libgdx 无 HSVtoRGB 的编译错；派发 G5
- 2026-09-30 G5 完成：武器特效/引擎尾焰/悬浮抖动/子弹尺寸/液体着色/电池电量/维修点旋转/MendProjector efficiency/shieldBuffer dispose（提交见下）；截图实测电池色块+顶盖、维修点 45° 炮塔、引擎尾焰；补 2 张贴图并重打图集；修 Battery 漏 super.draw；新发现 Fill.square 半径/边长错配 → 立 G10；派发 G6
- 2026-09-30 G6 完成：effects/minimap/blockreplace 设置接线 + 右键拆除 + canSkipWave 门控 + 死亡相机跟随核心（提交见下）；实测 effects 开关与 minimap 显隐；⚠ 工作区存在**用户未提交的并行改动**（Player/InputHandler/SaveIO 的背包分裂修复 + ClientLauncher 的 MINEDBG 钩子），提交时用 hunk 级选择性暂存避开；派发 G7
- 2026-09-30 G7 完成：战役发射链（Logic.launchZone + checkGameOver 发射分支 + HUD 发射按钮/确认框/白闪）（提交见下）；实测整链通过；发现并规避 `Time.run` 在 gameOver 后不推进的坑；派发 G8
- 2026-09-30 G8 完成：寻路 cost 累加 + 实心块压单位判定 + 服务端聊天命令 + 玩家名校验 + 游戏结束删区域槽（提交见下，M8/D5 跳过留 G9）；实测 cost 与放置判定；派发 G9
- 2026-09-30 G9 完成：SHIFT 复制图标 + 聊天滚轮 + UnitCreateEvent + DeflectorWall resetOwner + DefaultWaves 补到 24 组（提交见下）；实测归属改换与组数；M3/M5/M6 复核后跳过；**M8/D5 仍待办**；派发 G10
- 2026-09-30 G10 完成：`Fill.square` 改回 arc 半径语义（`rect(x,y,radius*2,radius*2)`），自写点 `Fx.unitDebris` 尺寸 /2，`Battery` 旧补偿 `tilesize*size-2` 改回原式 `tilesize*size/2-1`（渲染不变）（提交 88ceaee）；截图实测洋红参照方块 47px = 8 单位 = 1 格；顺带发现 `Lines.square` **4 参**形式 √2 语义差（3 参不受影响）→ 立 G11
- 2026-09-30 G11 完成：拆分 `Lines.square` 两个重载（3 参独立走半边长 `rect`、4 参归位外接圆半径）+ `Drawf` 注释更新（提交见下）；派子代理实现、主会话审查 diff + 编译 + 截图实测（同 radius=8：3 参 16 单位 vs 4 参 11.31 单位，比值 √2）；进入 G-V 终验
