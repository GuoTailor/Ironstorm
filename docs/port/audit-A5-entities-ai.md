# A5 审计：实体 / 战斗 / AI（第二轮静默缺口普查）

> 审计范围：本项目 `com/phoenix/game/{entities,ai,ctype}` ↔ 原版 `mindustry/{entities,ai,ctype}`。
> 判据：原版**有** + 本项目**没有/行为不同** + **无注释声明刻意偏离** 三者同时满足才报。
> 已读前置：`2026-09-29-full-port-completion.md` 批次登记表、`2026-09-30-round2-gap-audit.md`、`audit-A1/A2`、
> `f1/f3/f5f7/f6/f8/f9/f16-report.md`、`n1-report.md`、`todo-classified.md`。A1/A2 已登记项一律 cross-ref、不重报。
> 已知刻意偏离（不报）：collideGroups 实参对调、acceptItem 默认语义、坐标/rotation/d4 口径、
> Player 非 Unit、无 Mech 系统、CommandAI 为 v7/v8 替代 v6 onCommand、afterRead 不入档、TileEntity 休眠优化。
> 文件级结论：entities 75 对一一对应（`entities/units/UnitCommand.java` 挪到 `ai/`；`ai/{ControlPathfinder,PathTile,TaskQueue,UnitGroup,UnitStance,UnitCommand,types/CommandAI}` 为 v7+ 自研扩展）；
> ai/ctype 无缺文件。**以下为方法/字段级对照新发现，均未在 A1/A2 及后续 F 批中登记。**

## 摘要：缺失 2 / 偏离 6 / 断裂 4

最严重的三条：
1. **状态速度倍率整链断裂（高）**：`Statuses.getSpeedMultiplier()`（本项目 `entities/units/Statuses.java:112`）全工程**零消费者**；原版唯一消费点是 `Unit.updateVelocityStatus`（`Unit.java:256` 的 `velocity.limit(maxVelocity()).scl(1f + (status.getSpeedMultiplier()-1f)*Time.delta())`），本项目 `BaseUnit.updateVelocity`（`BaseUnit.java:1118-1216`）只做 `limit(maxVelocity())`。→ freezing(0.6)/wet(0.9)/tarred(0.6)/melting(0.8)/boss(1.1)/overdrive(1.15) 的速度倍率**全部无效**，冰冻/沾油不减速。
2. **单位碰撞伤害用错接口（中）**：原版 `DestructibleEntity.collision`（`DestructibleEntity.java:16-25`）是 `onHit(other); damage(other.damage()); if(死了) other.killed(this);`；本项目 `BaseUnit.collision`（`BaseUnit.java:962-970`）改写为 `damage(other.getShieldDamage())`，即 `max(damage, splashDamage)` → 对 `splashDamage > damage` 的子弹（Flak 15、炮弹等）**直击伤害被放大**，且**不调 onHit**（丢 `Trigger.phaseDeflectHit`）、**不调 killed**。
3. **单位伤害倍率整链断裂（中）**：`BaseUnit.getDamageMultipler()`（`BaseUnit.java:301-309`）全工程零消费者；原版由 `Bullet.damageMultiplier()`（`Bullet.java:124-130`）消费，本项目 `Bullet.damageMultiplier()`（`Bullet.java:160-166`）只对玩家返回加成、其余恒 1f → `rules.unitDamageMultiplier` 自定义规则与 status `damageMultiplier`（boss×3/overdrive×1.4）对 AI 单位子弹**完全无效**。

## 一、缺失（原版有、本项目完全没有、无声明）

| 原版位置 | 项 | 本项目现状 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- |
| `entities/type/base/FlyingUnit.java:135,190-201` | `wobble()`：飞行单位每帧叠加 sin/cos 微幅漂移（`x += sin(Time+id*999,25,0.05)*delta` 等），是飞行单位的悬浮抖动表现 | `FlyingUnit` 类注释只声明了 `updateRotation/drawWeapons/attack/onCommand` 未移植，**未声明 wobble**；全工程 grep `wobble` = 0 | 低 | 照原版补 `protected void wobble()`，在飞行单位 update 末尾调用（本项目 `FlyingUnit` 继承 `GroundUnit`，注意只在飞行分支调，避免影响地面单位） |
| `entities/type/BaseUnit.java:90-98` | `onHit(SolidTrait)` 覆写：被 `DeflectorEntity` 所属子弹命中且本方为玩家队时，`Core.app.post` 里若已死则 `Events.fire(Trigger.phaseDeflectHit)` | 本项目 `HealthTrait.onHit`（`traits/HealthTrait.java:25`）是空默认；`DestructibleEntity.collision`（`DestructibleEntity.java:27`）会调 `onHit`，但 `BaseUnit extends SolidEntity`（不走 DestructibleEntity）且其 `collision`（`BaseUnit.java:962-970`）**不调 onHit** → 单位版 onHit 彻底缺失 | 低 | 在 `BaseUnit.collision` 里补 `onHit(other)`；`BaseUnit` 覆写 `onHit` 实现 deflect 命中事件（`Trigger.phaseDeflectHit` 已存在） |

## 二、偏离（两边都有、行为/数值不同、无声明）

| 原版位置 | 项 | 本项目位置 | 证据 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| `entities/type/DestructibleEntity.java:16-25` | 单位受击伤害 = `((DamageTrait)other).damage()`，并先 `onHit`、死后 `killed` | `entities/type/BaseUnit.java:962-970` | 写成 `damage(((DamageTrait)other).getShieldDamage())`（`Bullet.getShieldDamage()` = `max(damage(), type.splashDamage)`，`Bullet.java:179`）→ 直击伤害取"直伤与溅射的较大者"；且漏 `onHit`/`killed`。无注释声明 | 中 | 改回 `damage(((DamageTrait)other).damage())`，并补 `onHit(other)` 与 `if(!wasDead && isDead()) other.killed(this)`；`getShieldDamage()` 只该给 ForceProjector 用 |
| `type/StatusEffect.java:47-57` | `update` 调 `unit.damagePeriodic(damage)` = `damage(amount * Time.delta())`（本项目 `Time.delta()`≈1.0 → 每帧 `damage`） | `type/StatusEffect.java:61-67` | 写成 `unit.damage(damage * Time.delta() / 60f)` → 实际每秒 `damage`，比原版**弱 60 倍**；`StatusEffects.java:70` 注释称"数值照抄原版，另做 /60 折算"，但照抄的是原版**每帧**值，折算后口径自相矛盾（burning 0.06：原版 0.06/帧=3.6/s，本项目 0.001/帧=0.06/s）。A2/N1 只审过 burning 的**数值**、未审这条**公式** | 中 | 改为 `unit.damagePeriodic(damage)`（与 `floor.damageTaken` 同一口径），并订正误导性的 /60 注释 |
| `entities/type/Bullet.java:124-130` | `damageMultiplier()` 返回 `((Unit)owner).getDamageMultipler()`（含 status.damageMultiplier × rules.unitDamageMultiplier） | `entities/type/Bullet.java:160-166` | 只对"玩家单位 owner"返回 `Vars.player.damageMultiplier()`，其余恒 `1f` → AI 单位的 `rules.unitDamageMultiplier` 与 status `damageMultiplier`（boss/overdrive）失效。注释只写了玩家加成，未声明丢弃单位倍率 | 中 | 照原版返回 `((BaseUnit)owner).getDamageMultipler()`；玩家分支已含在 `BaseUnit.getDamageMultipler`（`BaseUnit.java:301-309`）内 |
| `entities/bullet/BasicBulletType.java:13-14,37-44` | `bulletWidth=5, bulletHeight=7, bulletShrink=0.5`；back/front 同尺寸，宽恒定、仅高按 `fout()` 收缩 | `entities/bullet/BasicBulletType.java:16-18,55-76` | 改名 `width=5, height=8`（**高 7→8**），新增 `shrinkX=0.2`（原版宽不变，本项目宽也缩）、`shrinkY=0.5`；`drawRegions` 把 back 画成 `w*1.1, h*1.3`（原版 back/front 同尺寸）。纯视觉，未声明 | 低 | 对齐原版：`height=7`、去掉 shrinkX（宽固定）、back/front 同尺寸同 `(1-bulletShrink)+bulletShrink*fout()` 高度 |
| `entities/bullet/MassDriverBolt.java:19-21` | 构造器设 `despawnEffect = Fx.smeltsmoke; hitEffect = Fx.hitBulletBig;` | `entities/bullet/MassDriverBolt.java:22-27` | 构造器只设 `collidesTiles/lifetime/drag`，**漏设 despawnEffect/hitEffect**（项目用默认 `Fx.none`）→ 命中/消失无特效。未声明 | 低 | 补 `despawnEffect = Fx.smeltsmoke; hitEffect = Fx.hitBulletBig;` |
| `entities/type/base/GroundUnit.java:128-165` | `draw()` 中：脚下是液体时腿部按 `floor.color` 染色、本体按 `drownTime*0.4` 渐染 | `entities/type/base/GroundUnit.java:134-174` | 只 `Draw.color(Color.WHITE)`，无液体/溺水着色。未声明 | 低 | 照原版加 `if(floor.isLiquid) Draw.color(Color.WHITE, floor.color, 0.5f)`（腿）与 `drownTime*0.4f`（本体） |

## 三、断裂（本项目已有实现/字段，但无消费点）

| 原版位置 | 项 | 本项目位置 | 证据 | 严重度 | 修复建议 |
| --- | --- | --- | --- | --- | --- |
| `entities/type/Unit.java:256` | `velocity.limit(maxVelocity()).scl(1f + (status.getSpeedMultiplier()-1f)*Time.delta())` | `entities/units/Statuses.java:112`（实现存在）；`entities/type/BaseUnit.java:1118-1216`（消费缺失） | 全工程 grep `getSpeedMultiplier` 仅此定义；`BaseUnit.updateVelocity` 只 `velocity.limit(maxVelocity())`。→ 所有状态速度倍率失效（见摘要 #1） | **高** | 在 `updateVelocity` 的 `limit(maxVelocity())` 后追加 `velocity.scl(1f + (status.getSpeedMultiplier() - 1f) * Time.delta())` |
| `entities/type/Bullet.java:124-130` | `Bullet.damageMultiplier()` 消费 `((Unit)owner).getDamageMultipler()` | `entities/type/BaseUnit.java:301-309`（实现存在） | 全工程 grep `getDamageMultipler` 仅此定义（`Unit.java:138` 是注释残留）→ 无人调用（见摘要 #3） | 中 | 由 `Bullet.damageMultiplier()` 改调 `BaseUnit.getDamageMultipler()`（与上一条偏离合并修） |
| `entities/type/base/FlyingUnit.java:172-182` | `drawEngine()` 消费 `type.engineOffset/engineSize` 画引擎尾焰（`Fill.circle` ×2） | `content/UnitTypes.java`（多处设 `engineOffset/engineSize`）；无消费点 | 全工程 grep `engineOffset`/`engineSize`/`drawEngine`：只有 UnitTypes 赋值，**无任何读取/绘制** → 所有飞行单位（flare/wraith/ghoul/飞船/无人机）无引擎火焰。`FlyingUnit` 类注释未列 `drawEngine` | 低 | 补 `FlyingUnit.drawEngine()`（原版 172-182）并在飞行单位绘制链里调用（本项目 `GroundUnit.draw` 是共享绘制点，注意只对 `isFlying()` 生效） |
| `core/Logic.java:215` | `state.enemies = unitGroup.count(b -> b.getTeam()==waveTeam && b.countsAsEnemy())` | `entities/type/base/BaseDrone.java:53`（`countsAsEnemy()` 覆写存在）；`core/Logic.java:143-153`（消费缺失） | 本项目 `Logic.countEnemies()` 只判 `getTeam()==waveTeam`，**不调 countsAsEnemy** → 敌方 Builder/Miner/Repair 无人机被计入敌人数：HUD 敌人数偏高，且 `rules.waitForWaveToEnd` 期间只要敌方无人机存活就**卡住不开下一波** | 低 | `countEnemies` 追加 `&& unit.countsAsEnemy()`；并把 `countsAsEnemy()` 上移到 `BaseUnit`（默认 true，`BaseDrone` 覆写 false），照原版结构 |

## 备注 / 不计为问题

- `GroundUnit.rally`/`retreat` 两个 `UnitState` 在本项目**不可达**（v6 `onCommand` 未移植，命令走 CommandAI）——属已登记的 CommandAI 架构替代（A1 `ai/UnitCommand.java:12-20`），不计。
- `Bullet.killed`/`Bullet.absorb`/`Bullet.createBullet` 未移植：`killed` 原版唯一消费者是 `DestructibleEntity` 的 `DamageTrait.killed`，而 `KillerTrait` 全工程（含原版）无实现者 → 实际等价 no-op；`absorb` 已在 ForceProjector 侧等价实现（A1 已登记）；`createBullet` 是 6 参 `create` 的便捷转发，本项目用 `Bullet.create(...)` 等价。不计。
- `Units.findDamagedTile`：本项目在 `RepairDrone` 内以私有静态等价实现（`RepairDrone.java:77`），不计。
- `Units.nearby(Team,x,y,w,h,cons)` / `nearby(Rect,cons)` / `nearbyEnemies(Team,Rect,cons)`：本项目改用具名/矩形等价重载，消费点（FlakBulletType）语义等价，不计。
- `TileEntity.sleep/noSleep/isSleeping/timeToSleep`、`Fire` 存档、`StatusEffect.reactive`、`Statuses.maxEffects`、`Fire` 音效降级：均已在 A1/A2/R1 声明，不计。
- `Player` 的 `updateMechanics/updateKeyboard/updateShooting/beginRespawning/sendMessage/getInfo` 等：Player 非 Unit 的架构豁免（A1 已声明），不计。
- 文件级：`entities/units/UnitCommand.java` → `ai/UnitCommand.java`；`ai/types/CommandAI` 等 v7+ 自研扩展；均属已声明结构差异。
