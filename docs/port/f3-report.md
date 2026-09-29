# F3：Lightning 完整化（A1 高优先级）报告

原版参照：`Mindustry-126.2/core/src/mindustry/entities/effect/Lightning.java`（71-115 为生成逻辑主体）。
本项目落点：`core/src/com/phoenix/game/entities/effect/Lightning.java`（整体重写为原版实体结构）。

## 0. 结构决策：Lightning 恢复为"实体 + 静态工厂"（照抄原版）

原版 Lightning 是 `TimedEntity implements DrawTrait, TimeTrait` 实体：静态 `createLighting`
在**一次同步调用**里跑完整条闪电的生成逻辑（逐段放子弹 / 射线查墙 / 找敌人跳链），实体本身只负责
折线绘制与到期回收。此前本项目是"Effects 数据驱动特效 + 末端一次 Damage.damage"的简化版，
三段核心逻辑全部缺失。

本项目实体化所需设施均已具备（ItemTransfer 先例）：`TimedEntity`/`DrawTrait`/`Pools`、
`Vars.effectGroup` 已接线（`Logic.update()` → `effectGroup.update()`、`Renderer.draw()` →
`effectGroup.draw()`）。因此按原版结构重写：

- `Lightning extends TimedEntity implements DrawTrait`，字段照原版：
  `lifetime=10f`（static final）、`random`、`rect`、`entities`、`hit`、`maxChain=8`、
  `hitRange=30f`、`lastSeed`、`lines`、`color=Pal.lancerLaser`。
- `create()`（原版经 `Call.createLighting` 网络分发；本项目 `@Remote` 必须注释 → 直接转调）
  / `nextSeed()`（原版 `int` 递增；本项目原为 `long`，已改回 `int`）。
- `targetGroup()` 原版返回 `bulletGroup`；本项目该组未接线（子弹仿真以 `Bullet.all` 为准）→
  登记进 `effectGroup`（ItemTransfer 同款偏离，已注释）。
- 池化：`Pools.obtain(Lightning.class)` → `setDead(false)`（本项目 dead 标记不复位的坑，
  ItemTransfer 先例）→ `add()`；`removed()` → `Pools.free(this)`；`reset()` 复位 color/lines。
- 旧的 `Effects.Effect lightning` 定义与 `region()` 已删（仅 createLighting 内部使用，全库无他引）。

## 1. 沿途伤害（每段一发 damageLightning 子弹）

- 原版位置：`Lightning.java:71-72`，循环 `length / 2` 次，每次
  `Bullet.create(Bullets.damageLightning, l, team, x, y, 0f, 1f, 1f, dmg)` —— dmg 以 `Float`
  走子弹 **data** 携带（`Bullet.damage()` 优先读 Float data）。
- 本项目落点：重写后 `Lightning.createLighting` 循环体内同款调用；本项目
  `Bullet.create(BulletType, Entity owner, Team, x, y, angle, velocityScl, lifetimeScl, Object data)`
  签名完全兼容（Lightning 是 Entity、`Bullet.damage()` 已支持 data Float），零适配。
- 子弹类型：`Bullets.damageLightning` 本项目**已存在且与原版逐字段一致**
  （原版 `content/Bullets.java:386-392`：`BulletType(0.0001f, 0f)`、lifetime=Lightning.lifetime、
  hitEffect=hitLancer、despawnEffect=none、status=shocked、statusDuration=10f），直接复用。
- 碰撞链路核对：`Logic.java:126 collideGroups(Units.units, Bullet.all)` 每帧做子弹↔单位碰撞 →
  `Bullet.collision` 扣血 + `applyEffect(shocked)`；pierce=false 命中即回收，与原版一致。

## 2. 链式跳跃（maxChain=8、伤害不递减）

- 原版位置：`Lightning.java:93-114`：
  - 以当前点为中心 `hitRange=30f` 方框收集敌人（`Units.nearbyEnemies`），`hit` IntSet 去重，
    `hit.size < maxChain(8)` 时才收集（集满后只随机走、不再跳人）；
  - `Geometry.findFurthest` 挑最远者跳过去；无目标则 `rotation += random.range(20f)` 随机偏转
    ±20° 并前进 `hitRange/2`；
  - **伤害不递减**：每段子弹都带同一个 dmg（原版如此，照抄）。
- 本项目落点：`createLighting` 第 ③ 段照抄；`Units.nearbyEnemies` 用现有
  `(team, x, y, w, h, cons)` 重载展开 Rect 左下角（`rect.setSize(hitRange).setCenter(x,y)` 后传
  rect.x/y/width/height，语义等价）；`random` 用 libgdx `RandomXS128` 替代 arc Rand（无同步需求），
  `random.range(20f)` 就地展开为 `(nextFloat()*2-1)*20`（arc Rand.range 公式）。
- 依赖补齐：`Geometry.findFurthest` 本项目原先没有 → 已照 arc
  （`Arc/arc-core/src/arc/math/geom/Geometry.java:102`）逐字移植进 `math/geom/Geometry.java`
  （泛型 `T extends Position`，BaseUnit 实现该接口）。

## 3. 绝缘墙拦截（insulated）

- 原版位置：`Lightning.java:79-90`：每段线段 `world.raycastEach` 逐格查
  `tile.block().insulated`，命中则把折线末点**钉在墙上**（snap，不是截断消失）并 `break`。
- 本项目落点：照抄；`World.raycastEach/ltile/toTile` 均已存在，签名等价
  （TileRaycaster.accept(int,int) 返回 boolean 停止）。
- 字段现状与补齐：
  - 原版 `world/Block.java:63 public boolean insulated = false;` → 本项目 `world/Block.java`
    原先**无此字段**，已在 `hasShadow` 与 `health` 之间补上（对齐原版"health 之前"的位置）。
  - **原版设 insulated=true 的是塑钢墙与电力二极管，不是 surge wall**（任务提示有误，已按原版核对）：
    `Blocks.java:764/771`（plastaniumWall / plastaniumWallLarge）、
    `PowerDiode.java:23`。surge wall（合金墙）原版**没有** insulated，它只有被击放电反击。
  - 本项目补齐：`Blocks.java` plastaniumWall/plastaniumWallLarge 补 `insulated = true`；
    `PowerDiode.java` 构造器补 `insulated = true`。SurgeWall 不动（与原版一致）。
  - 备注：insulated 的另一消费点是电力连接判定（原版 `PowerNode.insulated(...)`，
    `Block.java:342`/`PowerNode.java:356`），属电网子系统，本批不涉及（字段已就绪）。

## 4. 调用点核对（签名以原版为准：create(team, color, damage, x, y, targetAngle, length)）

| 本项目调用点 | 原版对应 | 核对结果 |
| --- | --- | --- |
| `Bullets.java:817`（lightning 弹 init） | `Bullets.java:620` | length=30 ✓；**修正 1 处偏离**：非玩家分支伤害倍率原版乘 `1f`，本项目误写 `unitDamageMultiplier` → 已改回（`Bullets.java:814` 附近） |
| `Bullets.java:837`（arc 弹 init） | `Bullets.java:641` | length=25、damage、Pal.lancerLaser ✓ |
| `BulletType.java:163`（despawned 闪电散射） | `BulletType.java:145` | 逐字一致（含 nextSeed）✓ |
| `SurgeWall.java:29`（合金墙受击反击） | `SurgeWall.java` | lightningChance=0.05/damage=15/length=17/rot+180°/Pal.surge ✓；seed 由 `Time.nanos()`（long，编译不过新 int 签名）改为 `Lightning.nextSeed()`（原版 Lightning.create 内部即 nextSeed()） |
| `ShockMine.java:73`（地雷放电力弧） | `ShockMine.java:55` | cooldown=80/tileDamage=5/damage=13/length=10/tendrils=6 字段一致 ✓；seed 同上改 `nextSeed()`；**回退"补偿性偏离" `unit.damage(damage)`**（该行注释声明"等 Lightning 补齐后回退"——现在起点伤害子弹会打到踩雷单位，与原版一致） |
| `Mechs.java:144`（alpha 机甲 onLand） | `Mechs.java:89` | 8 道、17f×playerDamageMultiplier、随机 360°、length=14 ✓ |
| `Mechs.java:364` 与 `UnitTypes.java:528`（javelin updateAlt） | `Mechs.java:288-291` | 10f×playerDamageMultiplier、x/y+velocity 偏移、length=14 ✓（两处并存，参数均与原版一致，未合并） |

所有调用点 seed 统一为 `Lightning.nextSeed()`（int 递增），与原版 `Lightning.create → nextSeed()` 语义一致。

## 5. 改动文件列表

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/entities/effect/Lightning.java` | 重写：实体化 + 三段核心逻辑 + draw（折线逐段画 + 折点 Fill.square）；删旧 Effects 特效与末端 Damage.damage |
| `core/src/com/phoenix/game/math/geom/Geometry.java` | 补 `findFurthest`（照抄 arc Geometry.java:102） |
| `core/src/com/phoenix/game/world/Block.java` | 补 `public boolean insulated = false;`（原版 Block.java:63 位置） |
| `core/src/com/phoenix/game/content/Blocks.java` | plastaniumWall/plastaniumWallLarge 补 `insulated = true`（原版 Blocks.java:764/771） |
| `core/src/com/phoenix/game/world/blocks/power/PowerDiode.java` | 补 `insulated = true`（原版 PowerDiode.java:23） |
| `core/src/com/phoenix/game/world/blocks/defense/ShockMine.java` | seed 改 `Lightning.nextSeed()`；回退补偿性 `unit.damage(damage)` |
| `core/src/com/phoenix/game/world/blocks/defense/SurgeWall.java` | seed 改 `Lightning.nextSeed()` |
| `core/src/com/phoenix/game/content/Bullets.java` | lightning 弹 init 非玩家伤害倍率 `unitDamageMultiplier` → `1f`（照原版） |

未跑 gradle（按约定由主会话编译验证）。`Lightning.createLighting` 签名从 `(long seed, ...)` 收窄为
`(int seed, ...)`，全库调用点已全部核对（见上表），无其它引用残留。
