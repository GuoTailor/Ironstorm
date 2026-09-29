# N1 数值对照报告

## 摘要

- **检查**：161 个内容条目 —— 方块 ~130（dump-blksize/blkdata 中有实质数值的条目；地形地板、`part_*`/`build*`/`ore-*`/`spawn` 等程序生成方块不属原版内容对照对象，仅核对注册无异常）、单位 20、状态效果 11
- **不一致**：23 项（状态效果 3 + 方块 19 + 工厂耗电 1）
- **已修**：23 项（全部为 content 定义处数值覆盖，未改任何方法/结构/非 content 文件）
- **已声明跳过**：7 项（注释写明"刻意/降级/自建"的偏离）
- **项目自增**：3 项（原版 Build 126.2 没有的内容）

### 重要方法学说明（本次修复的根因模式）

本项目有一批**方块类构造器自设的默认 health**，原版对应类**没有**这些覆写（已逐一 grep 原版类文件确认），原版运行时全部走 `Block.java:417` 的 `health = size * size * 40` 默认公式。本项目类默认值把公式锁死（health ≠ -1 时公式不生效），导致：

| 本项目类构造器 | 本项目默认 | 原版运行时 |
| --- | --- | --- |
| `UnitFactory.java:61` | 350（不随 size 变） | 2x2=160 / 3x3=360 / 4x4=640 |
| `MechPad.java:67` | 160 | 2x2=160 / 3x3=360 |
| `RepairPoint.java:38` | 160 | 1x1=40 |
| `PowerDiode.java:21` | 260 | 1x1=40 |
| `PowerNode.java:36` | 40 | 1x1=40 / **2x2=160** |
| `PowerVoid.java:26` / `ItemSource.java:30` / `ItemVoid.java:21` | 160 | 1x1=40 |

因任务禁改类文件，修复方式为在 `content/Blocks.java` 定义处**显式覆盖** health（机制已验证可行：`surgeTower.health = 160` 显式赋值即生效）。类构造器里的错误默认值本身未动，属"结构清理"，留给主会话决定是否后续删。

## 修复明细

### content/StatusEffects.java（3 项）

| 内容名 | 字段 | dump/原值 | 原版值 | 动作 |
| --- | --- | --- | --- | --- |
| burning | damage | 0.5f | 0.06f（原版 StatusEffects.java:20） | 已改（A2 结论） |
| wet | speedMultiplier | 0.85f | 0.9f（原版 :44） | 已改（A2 结论） |
| freezing | armorMultiplier | 缺失 | 0.8f（原版 :35） | 已补（A2 审计漏项） |

状态效果 damage 口径说明：原版 `unit.damagePeriodic(damage)` 每帧乘 delta；本项目 `StatusEffect.update` 另有 /60 折算（类注释已声明口径差异）。melting/corroded 已等价，未动；burning 按同一口径照抄 0.06f。

### content/Blocks.java（20 项）

| 内容名 | 字段 | dump 值 | 原版值 | 动作 |
| --- | --- | --- | --- | --- |
| dagger-factory | requirements | copper:60, lead:40 | lead:55, silicon:35（Blocks.java:1724） | 已改 |
| dagger-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| dagger-factory | 耗电 | 0.10（类默认） | 0.5（Blocks.java:1728 consumes.power） | 已补 consumes.power(0.5f) |
| draug-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| spirit-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| phantom-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| wraith-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| crawler-factory | health | 350 | 160（2x2 默认） | 已覆盖 |
| titan-factory | health | 350 | 360（3x3 默认） | 已覆盖 |
| ghoul-factory | health | 350 | 360（3x3 默认） | 已覆盖 |
| fortress-factory | health | 350 | 360（3x3 默认） | 已覆盖 |
| revenant-factory | health | 350 | 640（4x4 默认） | 已覆盖 |
| repair-point | health | 160 | 40（1x1 默认） | 已覆盖 |
| power-node-large | health | 40 | 160（2x2 默认） | 已覆盖 |
| diode | health | 260 | 40（1x1 默认） | 已覆盖 |
| power-void | health | 160 | 40（1x1 默认） | 已覆盖 |
| item-source | health | 160 | 40（1x1 默认） | 已覆盖 |
| item-void | health | 160 | 40（1x1 默认） | 已覆盖 |
| omega-mech-pad | health | 160 | 360（3x3 默认） | 已覆盖 |
| glaive-ship-pad | health | 160 | 360（3x3 默认） | 已覆盖 |

除上表外，其余方块全部字段（size/itemCapacity/requirements/health 显式值与 size²×40 默认值，含 `GenericCrafter` 类默认 60、`Unloader` 类默认 70、`MendProjector` 的 `80*size²`、炮塔 `X*size²` 系列）与原版逐一吻合，零不一致。

### 单位（dump-unitdata，0 项修复）

20 个单位的 hitsize/health/武器子弹五元组（damage/speed/lifetime/splash/splashRadius）/免疫状态与原版**全部一致**（含通过 `Bullets.java` 两跳解析的 13 种弹药常量、`BombBulletType(damage, radius)` 参数语义、`BasicBulletType` 默认 lifetime=40、`FlakBulletType` 默认 radius=34、`LiquidBulletType` 默认 lifetime=74、`UnitType` 默认 hitsize=7、`Mech` 默认 hitsize=6）。crawler 的 `bombCrawler`、trident 的内联 `BombBulletType(16f,25f)`、reaper 的匿名子类 `BasicBulletType(7f,42)` 均已逐值核对吻合。

## 已声明跳过（注释写明刻意偏离，不改）

1. **ghoul 子弹 speed=1.5**（原版 0.7）—— `Bullets.java:308-313` 有"⚠ 刻意偏离（主会话已决策）"注释：本项目 FlyingUnit 攻击逻辑简化无提前量，0.7 会打不到移动目标；等攻击逻辑补齐后回退
2. **bombCrawler 保持碰撞开启**（原版 collides=collidesTiles=false）—— `Bullets.java:923` 刻意降级注释
3. **flare 单位整体** —— 原版 Build 126.2 无此单位，`UnitTypes.java` 类注释声明"属本项目自建内容"
4. **metalWall 方块** —— 原版 v126 无 "metal-wall" 方块（全库 grep 无定义），本项目 `wall("metalWall", 120, ...)` 自增（480 = 120×4，机制自洽）
5. **chaosArray/eradicator 的 `boss = true`** —— 本项目 UnitType 自有字段（独立血条/波次提示），与 StatusEffects.boss 互不影响，类注释已声明
6. **javelin/trident/glaive/dart-ship 的 maxVelocity=10/4.4/5.3/10** —— Mech 稳态速度模型 → UnitType maxVelocity 的换算值，逐行注释声明（health/hitsize/武器五元组均照 Mech 原值，已核对一致）
7. **dart-ship 对齐原版 Mechs.dart** —— 任务给定（原版它就是 Mech 不是 UnitType），注释已声明

## 项目自增（原版没有的内容，仅记录）

1. flare（单位）
2. metalWall（方块）
3. dump 中 `part_*`/`build1-9`/`ore-*`/`spawn`/`cliffs` 等程序生成方块（多格卫星瓦片/地图生成物，非内容对照对象）

## 未修（歧义/待议）

1. **core-shard/foundation/nucleus 的 requirements 非空**（copper:120,lead:80 / copper:300,lead:300,silicon:200 / copper:800,lead:800,silicon:500,thorium:300）：原版这三个 requirements 为空且 `debugOnly`（核心不可建造）。本项目自增了建造成本（推测供战役中重建核心）。属机制差异而非数值抄错，改动会影响战役核心建造 → 待主会话确认意图后决定（bv=debugOnly 与原版一致，未动）
2. **UnitFactory 类构造器默认 `powerConsumption = 0.10f`**（`UnitFactory.java:66`）：原版类无此默认。走辅助方法的 8 个工厂都被 `consumes.power(powerUse)` 覆盖不受影响，唯一受影响的 dagger-factory 已在本批修复；类默认值本身未删（禁改类文件），建议后续清理
3. **UnitFactory 类构造器默认 `health = 350` / `MechPad` 160 / `RepairPoint` 160 / `PowerDiode` 260 / `PowerNode` 40 / sandbox 三类 160**：同上，content 处已逐项覆盖，类默认值建议后续删除防止再犯

## 验证建议

- 重新跑 `PHOENIX_BLKSIZE=1 / PHOENIX_BLKDATA=1 / PHOENIX_UNITDATA=1` dump，与本文表格逐行 diff，应只剩"已声明跳过/项目自增"条目
- 修改后工厂/平台类方块需重编 `:core`（无新资源，不需要 texturePacker）
