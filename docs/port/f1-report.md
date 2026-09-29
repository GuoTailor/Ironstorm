# F1 批次修复报告：单位物理/环境交互补齐

对照原版 `mindustry/entities/type/Unit.java`（updateVelocityStatus 等）与
`mindustry/entities/type/BaseUnit.java`（update 判死两处），落到本项目 `BaseUnit`（本项目 Unit 层能力的承载体）。
A1 审计所指的"头注释声称已落地但实际没有"即本批内容。

## 改动文件

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | 主体：新增 drownTime/hitTime/hitDuration/noSpawner，update() 两处判死，updateVelocity() 重写为完整 updateVelocityStatus 结构，damage 系补齐，onDeath 清 drownTime |
| `core/src/com/phoenix/game/entities/type/base/FlyingUnit.java` | 删除 `updateVelocity()` 覆写（与基类飞行分支等价、缺三项共通交互，见 §结构说明） |
| `core/src/com/phoenix/game/world/Block.java` | 新增 `unitOn(Tile, BaseUnit)` 空默认钩子（原版 Block.java:361） |
| `core/src/com/phoenix/game/world/blocks/distribution/Conveyor.java` | 覆写 `unitOn`（原版 Conveyor.java:140-168） |

新增字段/常量/枚举清单：`BaseUnit.hitDuration`（9f）、`BaseUnit.noSpawner`（-65535，原版是 `Pos.get(-1,1)`）、
`BaseUnit.hitTime`、`BaseUnit.drownTime`、`BaseUnit.damagePeriodic(float)`、`BaseUnit.damage(float,boolean)`、
`Block.unitOn`、`Conveyor.unitOn`。
**没有新增枚举**：`EventType.Trigger.drown` 已存在（game/EventType.java:26），直接使用。

---

## 1. 卡墙 kill

- **原版位置**：`BaseUnit.update`（mindustry/entities/type/BaseUnit.java:288-290）；
  配套的"move 前记录 px/py、没动清零该轴速度"在 updateVelocityStatus 地面分支（Unit.java:324-327）。
- **落点**：`BaseUnit.update()`（status.update 之后、AI 之前，同原版位置）；px/py 清零在 `updateVelocity()` 地面分支 move 前后。
- **照抄要点**：`!isFlying() && world.tileWorld(x,y) != null && !(block instanceof BuildBlock) && solid()` → `kill()`。
  占位方块（BuildBlock）不判死，单位要从上面造过去。`Math.abs(px - x) <= 0.0001f` → 该轴速度清零，
  防止速度持续顶着墙。
- **结构差异**：判据完全一致；外层多一道 `Vars.world != null`（本项目防御惯例）。
  move 调用保持虚调用链：`updateVelocity → move(...) → GroundUnit.move（baseRotation 更新）→ SolidTrait.move（碰撞）`。

## 2. 深水溺亡

- **原版位置**：Unit.java:244-260（入口）、281-283（飞行清零）、306-315（累积/衰减/特效）、317-322（判死）、109（onDeath 清零）。
- **落点**：`BaseUnit.updateVelocity()` 地面分支 + 飞行分支 `drownTime = 0f` + `onDeath()` 清零；字段 `drownTime` 新增。
- **照抄要点**：
  - 仅地面单位；飞行分支恒 `drownTime = 0f`。
  - `onLiquid && floor.drownTime > 0` → `drownTime += Time.delta() * 1f / floor.drownTime`，
    偶发（`chance(Time.delta() * 0.05f)`）`Effects.effect(floor.drownUpdateEffect, floor.color, x, y)`。
  - 离开 → `lerpDelta(drownTime, 0f, 0.03f)`，随后 `drownTime = clamp(drownTime)`。
  - `drownTime >= 0.999f` → `damage(health + 1f)`（"当前血量+1"的秒杀式伤害，护甲减免后仍必死：护甲为 0 时全额）。
  - 玩家单位溺亡 → `Events.fire(Trigger.drown)`。
- **结构差异**：原版 `this == player` → 本项目 `Vars.player != null && this == Vars.player.unit()`
  （本项目 Player 不是单位基类）；原版 `!net.client()` → `!Vars.isClient()`。
  `Floor.drownTime`（water 600/deepwater 140/tar 150 等）与 `drownUpdateEffect` 字段均已存在，未动。

## 3. 液体地板交互

- **原版位置**：Unit.java:236-242、294-313；damagePeriodic 系 Unit.java:364-376；hitDuration/hitTime Unit.java:33,49。
- **落点**：`BaseUnit.updateVelocity()` 地面分支；`damagePeriodic(float)`/`damage(float,boolean)`/`hitTime`/`hitDuration` 新增到 BaseUnit（HealthTrait 路径）。
- **照抄要点**：
  - 涟漪：`onLiquid && velocity.len() > 0.4f && chance((velocity.len() * floor.speedMultiplier) * 0.06f * Time.delta())`
    → `Effects.effect(floor.walkEffect, floor.color, x, y)`。
  - 状态：`status.handleApply(this, floor.status, floor.statusDuration)`（焦油起火/湿身；`Statuses.handleApply`
    对 `none` 有特判，空气/无状态地板零开销）。
  - 周期伤害：`floor.damageTaken > 0` → `damagePeriodic(floor.damageTaken)`；
    `damagePeriodic = damage(amount * Time.delta(), hitTime <= -20 + hitDuration)`，
    `damage(float,boolean)` 保存/恢复 hitTime —— 持续掉血不闪白，照原版逐行。
  - `damage(float)` 补 `hitTime = hitDuration`（原版 Unit.java:79-84），`update()` 补 `hitTime -= Time.delta()`（原版 BaseUnit.java:280）。
- **结构差异（刻意保留，F1 范围约束）**：原版地面分支把 `floor.speedMultiplier` 乘在 move 上、阻尼里乘
  `floor.dragMultiplier`；本项目 `update()` 里既有"液体上 lerp 减速"块**保留不动**，move/阻尼不重复乘地板系数，
  避免双重减速。另有"站着实心方块不算在液体上"（`tile.block() != Blocks.air` → `onLiquid = false`，Unit.java:289-291）照抄。

## 4. 出界 kill + 出界推回 + dropZone 击退

- **原版位置**：Unit.java:258-260（出界 kill）、262-270（dropZone 击退）、272-278（推回，warpDst=180f）。
- **落点**：`BaseUnit.updateVelocity()`，插在 `velocity.limit(maxVelocity())` 之后、飞行/地面分支之前
  —— 与原版同位，**对所有单位（含飞行）生效**。
- **照抄要点**：
  - 出界 kill：`x < -finalWorldBounds || y < -finalWorldBounds || x >= world.width()*tilesize + finalWorldBounds || y >= ...`
    → `kill()`。`Vars.finalWorldBounds = worldBounds + 500 = 600`（Vars.java:60，已存在未动）。
  - 推回：`x < 0 → velocity.x += -x/180f` 等四条，用 `world.unitWidth()/unitHeight()`（World.java:304-310 已存在）。
  - dropZone：`team != state.rules.waveTeam` 时遍历 `spawner.getGroundSpawns()`（WaveSpawner.java:107 已存在），
    `withinDst(spawn.worldx(), spawn.worldy(), relativeSize)` →
    `velocity.add(Tmp.v1.set(x,y).sub(...).setLength(0.1f + 1f - dst(spawn)/relativeSize).scl(0.45f * Time.delta()))`。
- **结构差异**：
  - 原版 `Tmp.v1.set(this)` 依赖 arc `Vec2.set(Position)` 重载 → 本项目写 `Tmp.v1.set(x, y)`。
  - 半径口径：原版 `getSize()` = hitbox 宽×2，本项目 `getSize()` = `type.hitsize`（= 原版 getSize()/2）
    → `relativeSize = dropZoneRadius + getSize() + 1f` 等价换算（数值一致）。
  - 外层多一道 `Vars.spawner != null` 判空（主菜单等场景防御）。
  - 下标循环取 `getGroundSpawns()`（热路径惯例）。

## 5. spawner 失效 kill

- **原版位置**：BaseUnit.java:294-296。
- **落点**：`BaseUnit.update()`，avoidOthers 之后、updateVelocity 之前（同原版位置）。
- **照抄要点**：`spawner != noSpawner && (world.tile(spawner) == null || !(tile.entity instanceof UnitFactory.UnitFactoryEntity))` → `kill()`。
  顺带把字面量 -65535 提取为 `noSpawner` 常量（对应原版 `Unit.noSpawner`），`setSpawner/getSpawner` 同步引用。
- **结构差异**：本项目 `getSpawner()` 返回 int 打包坐标（原版返回 Tile）——判死直接读 `spawner` 字段，与原版一致。
  **不会误杀玩家**：全工程仅 `world/blocks/units/UnitFactory.java:294` 调 `setSpawner`（原版也只有 UnitFactory.java:62 一处），
  玩家出生/核心出生不设 spawner（grep 已核）。

## 6. Block.unitOn 钩子 + Conveyor 单位推动

- **原版位置**：Block.java:361（空默认）、Conveyor.java:140-168（覆写）、Unit.java:226（调用点，地面分支内）。
- **落点**：
  - `world/Block.java`：`unitOn(Tile, BaseUnit)` 空方法（handleStack 之后）。
  - `world/blocks/distribution/Conveyor.java`：覆写 `unitOn`。
  - 调用点：`BaseUnit.updateVelocity()` 地面分支 `tile != null` 时 `tile.block().unitOn(tile, this)`，**仅地面单位**（飞行分支不调，照原版分支位置）。
- **照抄要点**：`clogHeat > 0.5f` 不推；推力 = 带向分量（`speed * tilesize / 2.4f`）+ 中线拉回分量
  （`centerSpeed = 0.1f`、`centerDstScl = 3f`，水平带修纵向、竖直带修横向，距中线 <1 时归零）；
  `entity.len * itemSpace < 0.9f` 才推；`unit.applyImpulse((tx * speed + centerx) * entity.delta(), ...)`。
  `applyImpulse` 走 VelocityTrait default（`velocity += impulse / mass()`，`mass()` = type.mass，与原版同口径）；
  `entity.delta()` 是 TileEntity 的 timeScale 增益（ConveyorEntity.update 内已在用）。
- **结构差异**：
  - `entity.noSleep()` 省略（本项目无方块休眠机制，同 BlockStorage/Conduit 既有先例，注释已声明）。
  - 方向取法：本项目 `Geometry.d4` 与 rotation 同序（0=上/1=右/2=下/3=左），`d4[tile.rotation()]` 即带子前进方向，
    与原版"取带子朝向"语义等价（原版 d4 旋向不同但同为"前进方向"查询）。
  - 参数类型 `Unit` → `BaseUnit`（本项目单位基类）。
  - ⚠ `ShockMine`/`UnitFactory` 的既有"单位站上"等价实现**未动**（任务禁令），钩子注释已声明它们不走此路。

---

## 结构总览（原版 updateVelocityStatus → 本项目 updateVelocity 三段式映射）

| 原版段 | 本项目落点 |
| --- | --- |
| `status.update` / `item.amount clamp` | 不动（已在 `update()` 里，避免双跑） |
| `velocity.limit(maxVelocity())` | 保留（原第一行） |
| 出界 kill / dropZone 击退 / 出界推回 | 【本批新增】插在 limit 之后、飞行/地面分支之前，所有单位生效 |
| isFlying 分支（drownTime=0 + moveBy） | 【本批新增分支】FlyingUnit 原独立覆写删除——它只有"limit→moveBy→阻尼"，缺三项共通交互；删除后行为 = 基类飞行分支，严格等价 + 补齐 |
| 地面分支：unitOn → 液体交互 → 溺亡 → px/py + move | 【本批新增】move 保持虚调用（GroundUnit.move 更新 baseRotation 后进 SolidTrait.move 碰撞移动），move 不乘 speedMultiplier（见 §3） |
| 收尾阻尼 | 保留 `1 - drag()*Time.delta()`，不乘 dragMultiplier（见 §3） |

## 验证与遗留

- 未跑 gradle（按分工由主会话编译验证）。
- 联机一致性：出界 kill/溺亡/卡墙 kill 走 `damage()/kill()`，客户端由 `Vars.isClient()` 拦截本地扣血
  （与既有 `damage(float)` 口径一致），服务器权威结算后快照同步。
- 遗留观察项（不属于本批）：`damagePeriodic` 的 hitTime 阈值照原版 `-20 + hitDuration`；
  若日后补"受击闪白"渲染（原版 drawStats 用 hitTime），字段已就位。
