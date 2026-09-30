# G2 批次报告：状态与伤害链（第二轮审计 A5 产出，6 项）

> 范围：`docs/port/audit-A5-entities-ai.md` 的摘要 #1/#2/#3 + 一、缺失 onHit + 三、断裂 countsAsEnemy。
> 参照 `D:\IdeaProjects\Mindustry-126.2`（只读）。**未运行任何编译/git 命令**，仅静态核对（签名/import/覆写/消费点）。

## 完成情况：6/6 全部完成

| # | 项 | 严重度 | 状态 | 文件 |
| --- | --- | --- | --- | --- |
| 1 | 状态速度倍率整链断裂（`getSpeedMultiplier` 零消费） | 高 | ✅ | `entities/type/BaseUnit.java` |
| 2 | 单位碰撞伤害用错接口（`getShieldDamage` + 漏 onHit/killed） | 中 | ✅ | `entities/type/BaseUnit.java` |
| 3 | 单位伤害倍率整链断裂（`getDamageMultipler` 零消费） | 中 | ✅ | `entities/type/Bullet.java` |
| 4 | `StatusEffect.update` 的 /60 折算错误（DoT 弱 60 倍） | 中 | ✅ | `type/StatusEffect.java`、`content/StatusEffects.java` |
| 5 | `onHit(deflect)` 缺失 | 低 | ✅ | `entities/type/BaseUnit.java` |
| 6 | `countsAsEnemy` 无消费者（敌人数含无人机） | 低 | ✅ | `entities/type/BaseUnit.java`、`base/BaseDrone.java`、`core/Logic.java` |

---

## 1. 状态速度倍率（原版 Unit.java:256）

`BaseUnit.updateVelocity()`（原 :1122）的 `velocity.limit(maxVelocity());` 改为：

```java
velocity.limit(maxVelocity()).scl(1f + (status.getSpeedMultiplier() - 1f) * Time.delta());
```

- 位置与顺序照原版：在 `limit` 之后、出界判定/空投区击退/液体 move 之前。
- 调用顺序已核对：`update()` 里 `status.update(this)`（:617/:621）在 `updateVelocity()`（:662）**之前**，故取到的是本帧状态。
- ⚠ 本项目 `Time.delta()` 是方法（≈1.0/帧），不是 1/60。
- 生效项：freezing 0.6 / wet 0.9 / tarred 0.6 / melting 0.8 / boss 1.1 / overdrive 1.15。
- 热路径无每帧分配（纯标量运算）。

## 2. 碰撞伤害接口 + onHit/killed（原版 DestructibleEntity.java:16-25）

`BaseUnit.collision()` 的伤害分支由

```java
if(other instanceof DamageTrait){ damage(((DamageTrait)other).getShieldDamage()); }
```

改为照原版：

```java
if(other instanceof DamageTrait){
    boolean wasDead = isDead();
    onHit(other);
    damage(((DamageTrait)other).damage());
    if(!wasDead && isDead()){ ((DamageTrait)other).killed(this); }
}
```

- 直击伤害改回 `damage()`（不再是 `max(damage, splashDamage)`）→ 修 Flak 等 `splashDamage > damage` 子弹直击被放大的问题。`getShieldDamage()` 保留给 ForceProjector。
- 补 `onHit` / `killed` 两个回调（原版顺序）。
- **未动** G1/F16 批加在同一方法里的 `commandAI.hit(...)` 受击反击钩子与 F1 批物理。

## 3. 单位伤害倍率（原版 Bullet.java:124-130）

`Bullet.damageMultiplier()` 由"仅玩家 owner 返回 `Vars.player.damageMultiplier()`、其余 1f"改为：

```java
if(owner instanceof BaseUnit){ return ((BaseUnit)owner).getDamageMultipler(); }
return 1f;
```

- `BaseUnit.getDamageMultipler()`（:303-310）已含：`status.getDamageMultiplier() × rules.unitDamageMultiplier`，玩家再 `× Vars.player.damageMultiplier()`。故玩家加成**未丢失**，也未重复乘 `playerDamageMultiplier`。
- 默认规则（`unitDamageMultiplier=playerDamageMultiplier=1`）下玩家子弹结果与改动前一致（无回归）；AI 单位现在正确吃到 `status.damageMultiplier`（boss×3 / overdrive×1.4）与自定义 `rules.unitDamageMultiplier`。

## 4. DoT 口径（原版 StatusEffect.java:47-57）

`StatusEffect.update()` 由 `unit.damage(damage * Time.delta() / 60f)` 改为 `unit.damagePeriodic(damage)`。

- 已 grep 确认 `BaseUnit.damagePeriodic`（:203-205）= `damage(amount * Time.delta(), hitTime <= -20 + hitDuration)`，与原版一致；与 `floor.damageTaken`（:1182 已用 `damagePeriodic`）同口径。
- 语义订正：本项目 `Time.delta()≈1.0/帧` → 每帧扣 `damage`（原版口径）。改动前多除 60 → 弱 60 倍（burning 0.06：0.06/s → 修复为 3.6/s）。
- 订正了两处误导注释：`type/StatusEffect.java` 的字段注释（"每秒伤害"→"每帧伤害"）、`content/StatusEffects.java:70` 的"/60 折算"说明。

## 5. onHit(deflect)（原版 BaseUnit.java:89-98）

`BaseUnit` 新增覆写（照原版）：`entity instanceof Bullet && ((Bullet)entity).getOwner() instanceof DeflectorWall.DeflectorEntity && Vars.player != null && getTeam() != Vars.player.getTeam()` 时，延后一帧若 `isDead()` 则 `Events.fire(EventType.Trigger.phaseDeflectHit)`。

- 第 2 项补的 `onHit(other)` 调用点让它生效。
- 原版走 `Core.app.post`；本项目无 `Core.app.post`，用 `Gdx.app.postRunnable`（已加 null 守卫；`Events.fire` 是同步的）。新增 import：`com.badlogic.gdx.Gdx`、`com.phoenix.game.world.blocks.defense.DeflectorWall`。
- ⚠ **当前实际不触发**：本项目 `DeflectorWall` 有已声明的刻意简化——弹回子弹时不改换归属（原版 `resetOwner`），故 `getOwner() instanceof DeflectorEntity` 恒为 false。本方法照原版结构保留，一旦将来补上 resetOwner 即生效（已在代码注释中写明）。

## 6. countsAsEnemy（原版 Logic.java:215）

- `BaseUnit` 新增 `public boolean countsAsEnemy(){ return true; }`（照原版上移）。
- `BaseDrone.countsAsEnemy()`（返回 false）补 `@Override`。
- `Logic.countEnemies()` 的判据由 `getTeam() == waveTeam` 改为 `getTeam() == waveTeam && unit.countsAsEnemy()`（用局部变量 `unit` 取一次，热路径无分配）。
- 修复：敌方 Builder/Miner/Repair 无人机不再计入 HUD 敌人数，`rules.waitForWaveToEnd` 期间不再被这些无人机卡住开波。

---

## 文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | +速度倍率 scl；collision 改用 `damage()` + onHit/killed；+`onHit` 覆写；+`countsAsEnemy`；+imports(Gdx/DeflectorWall) |
| `core/src/com/phoenix/game/entities/type/Bullet.java` | `damageMultiplier()` 改调 `BaseUnit.getDamageMultipler()`；更新注释 |
| `core/src/com/phoenix/game/type/StatusEffect.java` | `update` 改 `damagePeriodic`；字段注释订正 |
| `core/src/com/phoenix/game/content/StatusEffects.java` | 订正 :70 的 /60 注释 |
| `core/src/com/phoenix/game/entities/type/base/BaseDrone.java` | `countsAsEnemy` 补 `@Override` |
| `core/src/com/phoenix/game/core/Logic.java` | `countEnemies` 追加 `countsAsEnemy()`；+import BaseUnit |

## 不确定 / 待主会话确认

1. **玩家伤害的额外因子（第 3 项）**：原版 `Player.getDamageMultipler()` = `status × rules.playerDamageMultiplier`（不含 `unitDamageMultiplier`）；本项目 `BaseUnit.getDamageMultipler()` 对玩家 = `status × unitDamageMultiplier × Vars.player.damageMultiplier()`（后者已含 `playerDamageMultiplier` 与等级加成）。改动前该实现零消费者（潜伏），现被消费后，**当 `rules.unitDamageMultiplier ≠ 1` 时玩家子弹会多乘该因子**。默认规则下无差异。按审计"修复建议"照抄 `BaseUnit.getDamageMultipler`，未改动其内部结构；若认为需严格对齐原版，应单独调整 `getDamageMultipler`（会同时影响其它潜在消费点，故未擅动）。
2. **第 5 项实际不触发**：如上，因 `DeflectorWall` 刻意简化不改换子弹归属，`phaseDeflectHit` 目前仍不会 fire。若希望真正生效，需要给 `DeflectorWall` 补 `resetOwner` 等价逻辑（超出 G2 范围）。
3. **未验证运行**：按协议禁止跑 gradlew，以上仅静态核对（签名/import/覆写一致性/消费点已 grep）。
