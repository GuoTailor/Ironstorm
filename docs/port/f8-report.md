# F8 索敌语义移植报告（A1 审计产出）

原版参照：`Mindustry-126.2/core/src/mindustry/entities/Units.java`（⚠ 参照工程中 Units.java 在 `entities/` 下而非 `ai/` 下）与
`mindustry/entities/type/BaseUnit.java:181-186`（参照工程的 targetClosest 系列在 BaseUnit，而非 BaseUnitComp；126.2 尚未拆 comp）。

## 1. derelict 阵营豁免 — 已完成

原版口径（Units.java 三个索敌入口都有 `if(team == Team.derelict) return null;`）：

| 原版 | 本项目落点 |
| --- | --- |
| `Units.closestTarget` :101 | `entities/Units.java` closestTarget 6 参重载 :119 |
| `Units.closestEnemy` :113 | `entities/Units.java` closestEnemy :130 |
| `Units.findEnemyTile` :84 | 新增 `Units.findEnemyTile` :98 + 自创兜底 `closestEnemyBuilding` :157 |

- `game/Team.java:26` 已有 `derelict` 常量（id=0），无需补。
- `Team.isEnemy`：原版 `Teams.areEnemies` 也是 `team != other`（Teams.java:91-93），本项目语义一致，不改。
- **目标方豁免（补充判定）**：`closestEnemyBuilding` 额外跳过 `tile.getTeam() == Team.derelict` 的建筑（Units.java:166）。
  原因：原版 tile 索敌走 `indexer.findEnemyTile`，目标方为 derelict 的建筑通常不在索引/谓词内；本项目自创兜底是全瓦片遍历，
  不加目标方豁免会让单位主动攻击中立废墟建筑，与"索敌默认跳过 derelict"语义冲突。
  此判定是自创兜底上的补充，非原版逐行照抄。
- 连带效果（符合原版）：derelict 队的炮塔不再开火、derelict 队子弹不再锁定目标（Turret/BulletType.homing 均走 Units 入口）。

## 2. findEnemyTile 的 tilePred 透传 — 已完成

- **BlockIndexer 层**：本项目 `ai/BlockIndexer.java:305 findEnemyTile(Team, x, y, range, Boolf<Tile> pred)` 与原版
  `ai/BlockIndexer.java:190` 同签名，pred 已透传，无需改。
- **Units 层入口（新增）**：原版 Units 有 `findAllyTile`(:78)/`findEnemyTile`(:83) 入口而本项目缺失，照抄补齐
  （`entities/Units.java:90-102`）：derelict 检查 + 转发 `Vars.indexer`，加 `Vars.indexer == null` 防御（项目惯例，headless 未接线时原版会 NPE）。
- **closestTarget 的 tilePred 重载（新增）**：`closestTarget(team, x, y, range, unitPred, tilePred)` 6 参（原版 :100-109），
  tile 分支把 tilePred 透传给 `closestEnemyBuilding`；5 参重载默认 `t -> true`（原版 :96）。
- `closestEnemyBuilding` 签名加 `Boolf<Tile> tilePred` 参数（null 视为全部通过）。旧 4 参签名无外部调用者，直接改签名未留兼容层。
- 谓词选型：建筑谓词用本项目既有 `com.phoenix.game.func.Boolf`（与 BlockIndexer 一致），单位谓词沿用既有 `java.util.function.Predicate`。
- ⚠ 未补 `Units.findDamagedTile`：`Geometry.findClosest` 本项目缺失，`RepairDrone:77` 已就地线性扫实现（有注释声明），
  不在三点范围，保持现状。

## 3. targetClosest 用 max(weapon.range, type.range) — 已完成

原版 `BaseUnit.targetClosest()`(:181-186)：`Units.closestTarget(team, x, y, Math.max(getWeapon().bullet.range(), type.range), u -> type.targetAir || !u.isFlying())`。

本项目改动：

| 落点 | 原状 | 改后 |
| --- | --- | --- |
| `BaseUnit.updateTargeting` :370 | 索敌范围 `type.attackLength`（默认 150f，是原版 FlyingUnit.attack 的**移动距离**口径，非索敌口径） | `closestRange()` = 原版口径 |
| `BaseUnit.targetClosest`（新增 :375） | 方法缺失（GroundUnit 注释已引用它） | 照原版 :181-186 抄（找到才替换 target） |
| `BaseUnit.closestRange`（新增 :388） | — | `max(weapon.bullet.range(), type.range)`；weapon/bullet 为 null 时退回 `max(attackLength, type.range)` |
| `FlyingUnit.flyAttack` :54 | 索敌范围 `max(type.attackLength, 700f)`（自创数字） | `closestRange()`（原版口径） |

- `UnitType.range = 0, attackLength = 150f`（`type/UnitType.java:24`）与原版 `UnitType.java:26` 一致，字段无需补。
- **防御分支说明**：原版单位必有武器，无 null 分支；本项目已有 `range()`(:360-361) 的 null 防御先例，
  `closestRange()` 沿用之（weapon null 时退 attackLength 而不是 0 射程），正常路径严格原版口径。
- **保持现状的赋值结构**：`updateTargeting` 维持"target == null && retarget() 才索敌"（原版 GroundUnit 是 retarget 时无条件
  targetClosest 换目标）；结构差异不在本批次三点内，且改它会改变锁定行为，注明不动。
- `FlyingUnit.flyAttack` 的"追击最近敌方单位（不搞 flag 盘旋索敌）"是**已声明的刻意偏离**（类注释），本批只对齐其索敌范围口径，
  行为结构不动。

## 跳过 / 未动项

| 项 | 原因 |
| --- | --- |
| `BulletType.java:188` homing 索敌 | 与原版 `BulletType.java:165` 逐字一致（含谓词 `!e.isFlying() || collidesAir`），derelict 豁免经 Units 入口自动生效 |
| `Turret.java:280` targetAir 分支用 closestTarget | 原版该分支用 `closestEnemy`（只索单位），本项目多了建筑兜底（对空炮塔会打建筑）。属三点之外的既有差异，改动会变行为，仅记录不改 |
| `closestTarget` 4 参默认谓词 `u -> true` vs 原版 `Unit::isValid` | `closestEnemy` 内已有 `isDead()` 过滤，等效 |
| `RepairDrone.findDamagedTile` 私有实现 | 见上，Geometry.findClosest 缺失，已有注释声明 |
| `BlockIndexer.findEnemyTile` | 已与原版同签名同语义 |

## 文件清单

- `core/src/com/phoenix/game/entities/Units.java`（derelict 豁免 ×3、findEnemyTile/findAllyTile 入口、closestTarget 6 参重载、closestEnemyBuilding 加 tilePred）
- `core/src/com/phoenix/game/entities/type/BaseUnit.java`（targetClosest/closestRange 新增，updateTargeting 换原版口径）
- `core/src/com/phoenix/game/entities/type/base/FlyingUnit.java`（flyAttack 索敌范围换原版口径）

## 不确定点

1. `closestRange()` 的 weapon-null 退路（attackLength）是项目防御口径，原版无此分支——若日后能保证所有单位必有武器，可去掉。
2. `updateTargeting` 的索敌时机结构（`target == null && retarget()`）与原版（retarget 即重索）不同，本批未动，可能属于后续批次。
3. 未编译验证（批次约束禁 gradlew），改动仅静态目检：所有新引用（Boolf/TileEntity/Vars.indexer.findTile/findEnemyTile/Weapon.bullet.range()）均已 grep 确认存在且签名匹配。
