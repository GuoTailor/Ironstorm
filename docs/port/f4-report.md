# F4：破坏表现链（A1 审计产出）报告

原版参照：`Mindustry-126.2/core/src/mindustry/world/Block.java`、`entities/type/TileEntity.java`、
`entities/type/Unit.java`、`entities/type/BaseUnit.java`、`entities/Damage.java`、`type/UnitType.java`。
注：原版 126.2（v6）没有 v7 的 `UnitComp`（任务描述里 UnitComp.java:99-101 是 v7 结构），
单位死亡链等价物在 `Unit.onDeath()` + `BaseUnit.onUnitDeath()`（`@Remote` 静态方法）。

## 总览

| 项 | 状态 | 落点 |
| --- | --- | --- |
| 1. Block.onDestroyed（物品爆炸/液体泼洒/电力缓冲爆炸/残骸） | 完成 | `world/Block.java`（新增 onDestroyed + baseExplosiveness） |
| 2. ItemModule 缺口 | 无需修改 | 现有 API 已覆盖本链全部消费点 |
| 3. Block.breakSound | 完成 | `world/Block.java`（字段）+ `entities/type/TileEntity.java`（播放） |
| 4. UnitType.deathSound + 播放 | 完成 | `type/UnitType.java`（字段）+ `entities/type/BaseUnit.java`（播放） |
| 5. 单位死亡携带物品殉爆 | 完成 | `entities/type/BaseUnit.java` onDeath |
| 附：Damage.dynamicExplosion 按原版补全 | 完成 | `entities/Damage.java` |

## 1. Block.onDestroyed（原版 Block.java:604-647）

- 本项目落点：`world/Block.java` 新增 `public void onDestroyed(Tile tile)`（插在 `getFlammability` 之前，
  与原版相邻顺序一致）+ 新增字段 `public float baseExplosiveness = 0f`（原版 Block.java:67）。
- 逐段对照：
  - 物品爆炸力：原版 `for(Item item : content.items())` → 本项目 `Items.all` 下标循环，
    `explosiveness += item.explosiveness * amount; flammability += item.flammability * amount`（照抄）。
  - 液体爆炸力：原版 `liquids.sum(...)` 两行 → 本项目 `LiquidModule.sum` 已存在（F5/F7 批），零适配照抄。
  - 电力缓冲：原版 `consumes.hasPower() && consumes.getPower().buffered` →
    `power += tile.entity.power.status * consumes.getPower().capacity`；本项目无 `Consumers.getPower()`，
    改用既有等价写法 `consumes.get(ConsumeType.power)`（`Block.init()` 同款），`.buffered/.capacity` 字段已存在。
  - 液体泼洒：原版 `liquids.each` + `Time.run(i / 2f, ...)` 延迟 `Puddle.deposit(other, liquid, splash)`
    → 本项目 `LiquidModule.each`、`Puddle.deposit(Tile, Liquid, float)` 均已存在，照抄。
  - 末尾：`Damage.dynamicExplosion(x, y, flammability, explosiveness * 3.5f, power, tilesize * size / 2f, Pal.darkFlame)`
    + `!floor().solid && !floor().isLiquid` 时 `RubbleDecal.create`（照抄）。
- **坐标口径换算（⚠）**：原版 `tile.worldx()/worldy()/drawx()/drawy()` 都是方块中心；本项目它们是
  瓦片角点 → 爆炸中心与贴花落点改用 `centerX(tile)/centerY(tile)`（`Block.drawTeam`/`drawCracks` 同款换算）。
- **lambda 捕获加固**：原版泼洒 lambda 捕获 `tile` 引用、延迟最长 15 个时间单位后才执行；本项目改捕获
  `final int tx/ty = tile.x/tile.y`，防瓦片对象被 `setBlock(air)`/重建改写后拿到过期坐标（行为等价）。
- **arc→libgdx 细节**：`Mathf.range(size / 2)` 原版返回 int，本项目 `Mathf.range(float)` 返回 float →
  显式 `(int)` 取整；`Mathf.clamp(amount / 5, 0, 30)` 走浮点重载（循环条件 int<float 合法），保持原版形状。

## 2. 接线点：TileEntity.kill()（对应原版 TileEntity.onDeath，TileEntity.java:282-292）

- 原版顺序：`Events.fire(BlockDestroyEvent)` → `block.breakSound.at(tile)` → `block.onDestroyed(tile)` →
  `tile.remove()` → `remove()`。
- 本项目 `kill()` 原先只有 事件 → RubbleDecal → tile.remove()；现改为
  事件 → `block.breakSound.play(1f)` → `block.onDestroyed(tile)` → tile.remove()。
- **RubbleDecal 从 kill() 迁入 Block.onDestroyed**：它本来就是原版 onDestroyed 的末尾两行
  （原 kill() 里的注释也自述"对应原版 Block.onDestroyed 末尾"），迁入后单次执行语义不变（kill 的 dead
  守卫保证 onDestroyed 只跑一次），并顺带删掉了"本项目没有 Block.onDestroyed 钩子"这条已过时的注释理由。
- **拆除不误触**：玩家主动拆除走 `Build.deconstruct → tile.remove()`（Build.java:209-232），不经过 kill()，
  与原版一致（拆除只有 breakBlock 特效不爆炸）；已核对全部 5 处 deconstruct 调用点。
- **核反应堆/冲击反应堆不动**：两者 `kill()` 覆写（NuclearReactor.java:236 / ImpactReactor.java:114）均以
  `super.kill()` 收尾 → 会自动获得基础 onDestroyed 表现（库存爆炸+液体+电力+残骸），与原版
  `NuclearReactor.onDestroyed → super.onDestroyed` 的叠加语义一致；仅帧内先后顺序与原版相反
  （原版先基础爆炸后核爆，本项目先核爆后基础爆炸），均为同帧特效/延迟任务，无行为影响。
  未做"迁移到 Block.onDestroyed 覆写"的结构重构——那是已验证过的 F7/T2 产物，改动超出本批职责。

## 3. breakSound（原版 Block.java:120 + TileEntity.java:287）

- 字段：`world/Block.java` 新增 `public Sound breakSound = Sounds.boom;`（`Sounds` = 生成的
  `io.anuke.mindustry.gen.Sounds`，`boom` 已存在——生成清单核对过）。
- 播放：`TileEntity.kill()` 里 `block.breakSound.play(1f)`。原版 `Sound.at(tile)` 按位置衰减；
  本项目音效无位置衰减，沿用项目既定写法 `play(1f)`（NuclearReactor/Door 先例）。
- ⚠ 与 `content/Blocks.java` 里 `shootSound = Sounds.xxx` 的既有做法一致：字段直接持有 Sounds 静态引用。
  当前 `Sounds.load()` 未启用（全 MockSound 空实现，静音），将来启用音频时这里与 shootSound 同批处理。

## 4. deathSound（原版 UnitType.java:43 + BaseUnit.java:63）

- 字段：`type/UnitType.java` 新增 `public Sound deathSound = Sounds.bang;`（默认值照原版）。
- 播放：`BaseUnit.onDeath()` 里 `if(type.deathSound != null) type.deathSound.play(1f);`（原版是
  `BaseUnit.onUnitDeath` 的 `unit.type.deathSound.at(unit)`；位置衰减同上处理）。
- 已知偏差：原版里玩家（Player 实体）死亡只响 `Sounds.bang`（Unit.onDeath），AI 单位响 bang + deathSound
  （两处都是默认 bang，即响两声）；本项目玩家单位也是 BaseUnit，故玩家单位死亡也走 bang + deathSound。
  音频未启用，无可闻影响。

## 5. 单位死亡携带物品殉爆（原版 Unit.java:98-116 onDeath）

`BaseUnit.onDeath()` 补齐（此前是"无物品加成"的简化版）：

- `explosiveness = 2f + item.explosiveness * itemAmount; flammability = item.flammability * itemAmount`
  （原版 Unit.java:99-100，本项目背包是 `item/itemAmount` 两字段，非可变 ItemStack）；
  `item == null` 守卫：本项目未采矿时 `item` 为 null（原版 ItemStack.item 空手时同样为 null，但原版在
  `writeSave` 前兜底 copper，运行期读 null 会 NPE——本项目不照抄该坑）。
- radius：原版 `getSize() / 2f`；本项目 `BaseUnit.getSize()` 本就返回 `type.hitsize`，等价直用（注释说明）。
- `Sounds.bang.play(1f)`（原版 Unit.java:107）。
- `clearItem()`（原版 `item.amount = 0`，Unit.java:108）。
- `EventType.Trigger.suicideBomb`：`explosiveness > 7f && this == Vars.player.unit()`（原版
  `this == player`，本项目"玩家"等价物是当前控制的单位；Trigger 枚举已存在，此前无 fire 点）。
- 未动的既有部分：`UnitDrops.dropItems`（已移植）、ScorchDecal、Fx.unitDeath/unitDebris（此前批次的
  刻意选择，替换了原版 `Fx.explosion`）、震屏 2f、drownTime 清零、UnitDestroyEvent。

## 附：Damage.dynamicExplosion 按原版补全（Damage.java:33-65 → 本项目 Damage.java:178-215）

这是"电力缓冲爆炸 = 电击视觉"的载体，原简化版（一次性 damageAll + createIncend）缺的分支全部补上：

- **power → 闪电**：`Mathf.clamp(power/20, 0, 6)` 道 `Time.run` 延迟闪电，`Lightning.create(Team.derelict,
  Pal.power, 3, x, y, random(360), branches + range(2))`（`Lightning` F3 批已实体化，签名兼容）。
- **flammability → 火球**：`Mathf.clamp(flammability/4, 0, 30)` 发延迟火球；原版
  `Call.createBullet(Bullets.fireball, ...)` 无网络层 → 本地 `Bullet.create(Bullets.fireball, (Entity)null,
  Team.derelict, ...)`（Fire.java:255 / Puddle.java:172 同款先例）。
- **explosiveness → 多波伤害**：`waves = clamp((int)(explosiveness/4), 0, 30)`，每波
  `damage(null, x, y, clamp(radius + explosiveness, 0, 50) * ((f+1)/waves), explosiveness/2, false)`
  （原版 4 参 `damage(x,y,rad,dmg)` = 6 参 null/false 形态，本项目 6 参重载带衰减+击退，已有）+
  `Fx.blockExplosionSmoke` 随机散布。
- **冲击波阈值**：`explosiveness > 15f` → `Fx.shockwave`；`> 30f` → `Fx.bigShockwave`。
- **震屏与特效**：`shake = min(explosiveness/4 + 3, 9)` → `Effects.shake(shake, shake, x, y)`；
  `Effects.effect(Fx.dynamicExplosion, x, y, radius / 8f)`（本项目该特效的 `e.rotation` = 强度倍数，已对齐）。
- 行为变化（对既有调用方 BaseUnit.onDeath）：空手单位死亡从"固定 12 点范围伤害"变为原版行为
  （explosiveness=2 < 4 → 无伤害波，只有特效/震屏）；携带爆炸物（如爆炸物填满的矿工）死亡才会产生
  波次伤害与冲击波——这就是原版"运炸药单位殉爆"的表现。`Fx.explosion`/`createIncend` 不再由它调用
  （原版如此；`createIncend` 方法保留，另有 Fire/状态系统语义）。

## 6. ItemModule 缺口核对（任务 2，结论：零修改）

本链消费点逐一核对：`items.get(item)`（onDestroyed 物品循环）、`liquids.sum/each`（属 LiquidModule，
均已存在）。原版 `ItemModule` 的 `sum()/has()/hasAll(Seq)/add(ItemStack[])` 在 126.2 的 onDestroyed 链上
**一个都没用到**（它们是 v7 Building 时代的方法）；本项目 `ItemModule` 现有
get/set/add/remove/has/total/clear/take/first 已覆盖。按"只补用到的"原则，零修改。

## 跳过/未含项

- 无跳过项。所有引用的音效（boom/bang）、特效（Fx.dynamicExplosion/shockwave/bigShockwave/
  blockExplosionSmoke/explosion）、Lightning、Bullets.fireball、Puddle、RubbleDecal 均已确认存在。
- 音频系统整体未启用（MockSound 静音）——音效代码路径已就位，出声等待 `Sounds.load()` 解禁。

## 验证限制

- 本批未编译/未运行（协议禁止 gradlew）；建议主会话 `gradlew :core:compileJava` 核对，
  运行期可用 `PHOENIX_AUTOPLAY=1` 打爆一个满库存仓库/电池观察：残骸贴花、水洼、闪电、震屏。
