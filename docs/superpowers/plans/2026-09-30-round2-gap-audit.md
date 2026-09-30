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
| G2 | 状态与伤害链 | getSpeedMultiplier 接线（A5-1，**高**）+ BaseUnit.collision 用 damage() 并补 onHit/killed（A5-2）+ getDamageMultipler 接线/Bullet.damageMultiplier（A5-3）+ StatusEffect.update 的 /60 复核修正（A5-偏离）+ onHit(deflect)（A5-M2）+ countsAsEnemy 消费（A5-断裂） | ☐ |
| G3 | 事件派发补齐 | ClientLoadEvent（A7-B1）+ Trigger.update（A7-B3）+ StateChangeEvent（A7-B4）+ PlayEvent/DisposeEvent（A7-B5）+ CoreItemDeliverEvent（A4-B1，教程卡住）+ MusicControl/LoopControl 实例化（A7-B2） | ☐ |
| G4 | 规则与数值 | waveSpacing 7200（A7-D1）+ dropZoneRadius 300（A7-D2）+ addItem itemsDelivered（A7-D3）+ SpawnGroup items（A7-D4）+ GameState.isPaused gameOver 分支（A7-D5，结算不冻结）+ Team 占位颜色（A7-D6）+ Rules 默认值复核（A7-D7） | ☐ |
| G5 | 图形与特效 | 武器 shootEffect/smokeEffect（A4-M1）+ FlyingUnit.wobble/drawEngine（A5-M1/断裂）+ BasicBulletType 尺寸、MassDriverBolt 特效、GroundUnit 液体着色（A5-偏离×3）+ Battery 电量 draw、RepairPoint 旋转炮塔、MendProjector efficiency（A4-M2/M7/D4）+ shieldBuffer dispose（A6-B4） | ☐ |
| G6 | 设置项消费接线 | effects 开关（A6-B1）+ minimap 开关（A6-B2）+ blockreplace 应用（A6-B3 后半）+ 右键拆除模式（A6-D2）+ 跳波 canSkipWave 门控（A6-D3）+ 死亡相机跟随核心（A6-D1） | ☐ |
| G7 | 战役发射链（裁决算缺口） | Logic.launchZone + state.launched + zone.setLaunched + HUD 发射按钮/倒计时 + Objectives.Launched（A6-S1） | ☐ |
| G8 | 世界/寻路/网络 | Tile.updateOcclusion cost（A4-D2，寻路不避水）+ validPlace Units.anyEntities（A4-D3）+ 服务端聊天命令（A7-缺失1）+ 玩家名/颜色/长度校验（A7-缺失2）+ 游戏结束删区域槽（A7-缺失3）+ CoreBlock 重生链（A4-M8/D5，低） | ☐ |
| G9 | UI 小项与低优先 | SHIFT 复制图标（A6-M1）+ 聊天滚轮（A6-M2）+ setHudText（A6-M3）+ ItemModule/ItemStack 死方法（A4-M5/M6）+ UnitCreateEvent（A4-B3）+ DefaultWaves 复核（A7-D8） | ☐ |
| G-V | 终验 | 编译 + autoplay 实跑 + 关键专项实测（G1 复制漏洞 / G2 状态减速 / G4 波次间隔 / G7 发射链）+ dist 打包 | ☐ |

## 进度日志（每批一行，主会话维护）

- 2026-09-30 第二轮方案建立（上一轮 V1 已通过）；派发 A4~A7 四片并行审计
- 2026-09-30 四片审计完成（合计 缺失16/偏离20/断裂16）；A6-S1 战役发射链裁决为缺口（注释是状态陈述非偏离声明）→ G7；建 G1~G9 + G-V 登记表；派发 G1
- 2026-09-30 G1 完成：复制漏洞修复 + 实测闭环（提交 0183242）；顺带 gitignore 调试产物（3feb0c6，此前误提交 run-out/run-err）；派发 G2
