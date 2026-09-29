# F5/F7 修复报告（子弹对空语义 + 断裂接线四件）

原版参照：`D:\IdeaProjects\Mindustry-126.2\core\src\mindustry\`（只读）；本项目：`core/src/com/phoenix/game/`。
铁律：无注释声明偏离处一律照抄原版；已声明的偏离保持不动。

---

## F5：子弹对空语义

### F5-1 Bullet.collides

- **原版**：`mindustry/entities/type/Bullet.java:168-170`
  `return type.collides && (other != owner && !(other instanceof DamageTrait)) && !supressCollision && !(other instanceof Unit && ((Unit)other).isFlying() && !type.collidesAir);`
- **本项目改前**（`entities/type/Bullet.java:182-184`）：`type.collides && (other != owner) && !supressCollision && type.collidesAir` —— 对空判定写成了**无条件** `type.collidesAir`，导致对地弹（如 bomb，`collidesAir=false`）打不到任何单位；且缺 `!(other instanceof DamageTrait)`（子弹互不相撞）。
- **改动**：整句照抄原版语义，`Unit`（原版单位基类）对应本项目 `BaseUnit`（本项目无 Unit 基类，Unit.java 是整类注释保留的参考文件，能力落在 BaseUnit）：
  `!(other instanceof BaseUnit && ((BaseUnit)other).isFlying() && !type.collidesAir)`。对地弹重新打得中地面单位，飞行目标仍按 `collidesAir` 判定。
- **性质**：照抄（bug 修复）。

### F5-2 Bullet implements TeamTrait

- **原版**：`Bullet.java:19` 的 implements 列表含 `TeamTrait`（`DamageTrait, Scaled, Poolable, DrawTrait, VelocityTrait, TimeTrait, TeamTrait, AbsorbTrait`）。
- **本项目改前**：`Bullet implements Pool.Poolable, VelocityTrait, DamageTrait` —— 缺 TeamTrait。而 `BaseUnit.collides` 的原版写法用 `other instanceof TeamTrait && getTeam().isEnemy(team)` 判定"单位是否与 DamageTrait 实体碰撞"，没有 TeamTrait 接口就只能靠 `instanceof Bullet` 特判绕行（原改法），且对"非 Bullet 的 DamageTrait"返回 true，与原版（非 TeamTrait 即 false）不等价。
- **改动**：`Bullet` 追加 `implements TeamTrait`（`TeamTrait.getTeam()` 由继承的 `BaseEntity.getTeam()` 满足，零额外代码）。
- **性质**：照抄（对齐原版接口结构）。

### F5-3 BaseUnit.collides

- **原版**：`mindustry/entities/type/Unit.java:87-95`（注意：collides 在原版 **Unit** 基类，不在 BaseUnit；任务描述里的"原版 BaseUnit.collides"实为 Unit.collides）：
  ```java
  if(other instanceof DamageTrait){
      return other instanceof TeamTrait && other.getTeam().isEnemy(team);
  }else{
      return other instanceof Unit && ((Unit)other).isFlying() == isFlying();
  }
  ```
- **本项目改前**（`BaseUnit.java:764-775`）：DamageTrait 分支是 `instanceof Bullet → isEnemy` 特判 + `BaseUnit ? isEnemy : true` 尾分支；后半对"非 Bullet 非 BaseUnit 的 DamageTrait"返回 true（原版为 false），前半偏离原版结构。
- **改动**：整段照抄原版 Unit.collides（`Unit` → `BaseUnit`），删除 Bullet 特判与 `: true` 死分支（本项目 DamageTrait 的实现者目前只有 Bullet，特判本是冗余）。
- **性质**：照抄。

### F5-4 collideGroups 实参顺序（已声明偏离，保持不动）

- **原版**：`mindustry/core/Logic.java:257-258` `collideGroups(bulletGroup, unitGroup)` + `collideGroups(bulletGroup, playerGroup)`，子弹组在**第一个参数**（外层）；单位/玩家分两组各碰一次。
- **本项目**：`core/Logic.java:105` `collideGroups(Units.units, Bullet.all)` —— 单位在外层。**保持不变**，理由：
  1. 原文件注释已声明"单位在外层，避免处理已回收的子弹"。本项目 `BaseEntity.remove()` **同步**执行 `removed()`（`Pools.free` + 字段清空，见 BaseEntity.java:42-52 注释），而 `checkCollide` 按 a→b 顺序回调 collision：若子弹在外层，`Bullet.collision` 里 `remove()` 先回收自身（清空 type），随后 `BaseUnit.collision` 调 `((DamageTrait)other).getShieldDamage()` 会读到已回收子弹（NPE 风险）。
  2. 语义等价：`checkCollide` 本就双向判定（`a.collides(b) && b.collides(a)`）并双向回调，两组一次调用即涵盖"子弹×单位 + 子弹×玩家"（本项目玩家单位也在 `Units.units` 里，`BaseUnit.init()` → `Units.add`，与原版 playerGroup 独立成组是结构差异但覆盖面相同）。
  3. 本项目 `collideGroups` 是 O(n·m) 暴力双循环（无原版的 tree 空间索引），外层谁在先不改变复杂度。
- **改动**：仅把注释扩充为明确说明"原版实参顺序 + 本项目对调的原因"，逻辑不动。
- **性质**：已声明偏离（维持），注释补全。

**调用链自查**（bullet↔unit 双向）：
- 子弹打单位：`bullet.collides(unit)` = `type.collides && unit!=owner && !(unit instanceof DamageTrait)=true && !supress && !(unit.isFlying() && !collidesAir)` ✓（对地弹打地面单位=true，打飞行=false）
- 单位接子弹：`unit.collides(bullet)` = `bullet instanceof DamageTrait && bullet instanceof TeamTrait && isEnemy` ✓（友方子弹穿过友军，与原版一致）

---

## F7：断裂接线四件

### F7-1 runWave 接上 launchWaveMultiplier

- **原版**：`mindustry/core/Logic.java:136-142`：`state.wavetime = world.isZone() && world.getZone().isLaunchWave(state.wave) ? state.rules.waveSpacing * state.rules.launchWaveMultiplier : state.rules.waveSpacing;`（乘在 waveSpacing 上；注意 isLaunchWave 判定的是 **wave++ 之后**的波号，本项目 wave++ 顺序与原版一致）。
- **本项目改前**：`runWave()`（原 159 行）`wavetime = waveSpacing`，`launchWaveMultiplier` 全工程无消费（`Zones.java:62` 已设 3f，groundZero 注释声明"已回填"但没人读）。
- **改动**：照原版三元式接上（`Logic.java:177-179`）。
- **性质**：照抄。

### F7-2 Logic 补 WaveEvent 监听器

- **原版**：`mindustry/core/Logic.java:34-43` 构造器内 `Events.on(WaveEvent.class, ...)`：遍历 `playerGroup` 给每个 Player 重置 `respawns = state.rules.respawns`；`world.isZone()` 时 `world.getZone().updateWave(state.wave)`。
- **本项目改前**：无该监听器；仅 `runWave()` 里给本地玩家重置过 respawns（162-164 行）；`Zone.updateWave`（`type/Zone.java:140`）无调用点。
- **改动**：本项目 `Logic` 是纯静态类、从不实例化（无构造器），在 `Logic` 顶部加**静态初始化块**注册等价监听器（`Logic.java:19-31`）：遍历 `Vars.playerGroup.all()` 重置 respawns（覆盖远端玩家，比原来的只重置本地玩家更贴原版）+ `isZone()` 时 `updateWave`；并把 `runWave()` 里原来的本地重置删掉（避免与监听器重复，结构对齐原版——原版 runWave 里没有 respawns 重置）。
- **性质**：照抄（注册点从构造器降级为静态块，是"Logic 无实例"这一既有结构差异的等价替代，已加注释声明）。
- 前提确认：`World.isZone()/getZone()`（World.java:317/327，getZone 每次现读 `state.rules.zone`）、`Zone.isLaunchWave/updateWave` 均已存在。

### F7-3 护甲减免接入 damage 路径 + StatusEffect.armorMultiplier

- **原版**：`Unit.damage`（`mindustry/entities/type/Unit.java:80-83`）实际扣血走 `calculateDamage(amount)`；`Unit.calculateDamage`（196-198）= `amount * Mathf.clamp(1f - status.getArmorMultiplier() / 100f)`。
- **本项目改前**：`BaseUnit.damage`（163-166）直接 `HealthTrait.super.damage(amount)`，护甲减免无消费者；`Statuses.getArmorMultiplier()`（Statuses.java:91-95，逐效果相乘，与原版 96/116 行一致）早已存在但没人调。
- **改动**：
  - `BaseUnit.damage` 改为 `HealthTrait.super.damage(calculateDamage(amount))`，并新增 `calculateDamage(float)`（BaseUnit.java:172-174），公式照抄原版（`Mathf.clamp` 单参版=clamp 到 [0,1]，本项目 Mathf.java:65 已有）。
  - 原版 `Player.calculateDamage`（Player.java:213-215）多乘 `mech.getExtraArmor(this)`；本项目 Player 是独立壳类（不承载血量，玩家伤害走其驾驶的 BaseUnit），且无机甲附加护甲系统 → 该项无对应结构，不引入（最小实现）。
- **StatusEffect.armorMultiplier 字段**：任务描述称"补字段（默认 0）"，但原版 `type/StatusEffect.java:19` 默认是 **1f**；本项目 `type/StatusEffect.java:32` 已是 `public float armorMultiplier = 1f`，与原版一致，**无需改动**。各状态赋值（原版 StatusEffects.java:35/61/82/90/94 ↔ 本项目 StatusEffects.java:44/78/104/114/119：freezing 0.8 / melting 0.8 / overdrive 0.95 / shielded 3 / boss 3）也已全部在位，包括任务点名的 `freezing.armorMultiplier = 0.8f`（StatusEffects.java:44）。本项实际缺口只有 damage 路径。
- **性质**：照抄（含"字段/数值早已就位、仅缺接线"的结论修正）。

### F7-4 Fire 两处

1. **extinguish 补 fireExtinguish 事件**
   - 原版：`mindustry/entities/effect/Fire.java:72-80`，`fire.time += intensity * Time.delta()` 后若 `fire.time >= fire.lifetime()` 则 `Events.fire(Trigger.fireExtinguish)`。
   - 本项目改前（Fire.java:166-170）：只推进 time，无事件。
   - 改动：照原版结构接上（Fire.java:168-178；Trigger 枚举值 `EventType.java:20` 已存在）。注：全工程（原版与本项目）目前都没有 fireExtinguish 的监听者，原版也只 fire 不 listen——补上即与原版完全一致。
2. **脚下水洼可燃性接入**
   - 原版：`Fire.java:30`（`puddleFlammability` 字段）、126 行 `flammability = baseFlammability + puddleFlammability`、151-157 行在 0.1 概率块开头 `puddleFlammability = Puddle.getPuddle(tile).getFlammability() / 3f`（无水洼清 0）。
   - 本项目改前：类注释 41-42 声称"没有液体水洼（Puddle），跳过"——已过时（Puddle 系统已完整移植，`Puddle.getPuddle`/`getFlammability` 均在）。
   - 改动：加 `puddleFlammability` 字段；`update` 里 `flammability = baseFlammability + puddleFlammability`；0.1 概率块开头照抄水洼读取；删除重算块里原版没有的局部回写 `flammability = baseFlammability;`（对齐原版 132-135 行， puddle 成分不被洗掉）；类注释删掉过时的"没有 Puddle"偏离条目。
   - 生效面：`flammability` 并入后，寿命延长（`lifetime += clamp(flammability/8)`）、蔓延与火球概率（`flammability > 1` 分支）自动带上水洼加成——与原版同一条数据流。
- **性质**：照抄。

---

## 改动文件清单

| 文件 | 内容 |
| --- | --- |
| `core/src/com/phoenix/game/entities/type/Bullet.java` | implements TeamTrait；collides 照原版 |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | collides 照原版 Unit 语义；damage 走 calculateDamage（新增） |
| `core/src/com/phoenix/game/core/Logic.java` | runWave 乘 launchWaveMultiplier；静态块注册 WaveEvent 监听器；碰撞注释补全（逻辑不动） |
| `core/src/com/phoenix/game/entities/effect/Fire.java` | extinguish 补 Trigger.fireExtinguish；puddleFlammability 接入；删过时类注释 |

（`StatusEffect.java`/`StatusEffects.java`/`Zones.java` 经核对均已就位，未改动。）

## 自查发现（供主会话参考）

1. **F5-3 的一个前提**：`BaseUnit.collides` 的单位分支 `other instanceof BaseUnit && isFlying() == isFlying()` 沿用了原改法（原版是 `instanceof Unit`）。本项目 GroundUnit extends BaseUnit，所有单位共用该基类，Player 不参与碰撞（非 SolidTrait）——等价成立。
2. **F7-2 的事件触发链**：静态块随 Logic 类首次加载执行（ClientLauncher 调 `Logic.update()` 时），而 WaveEvent 首次 fire 在 `runWave()`（Logic 已加载）之后，注册时机安全。
3. **F7-3 的范围修正**：任务描述"StatusEffect.armorMultiplier 默认 0"与原版不符（原版默认 1f），以原版为准；字段与 5 处数值赋值本项目早已照原版补齐，本项只缺 damage 接线——已接。
4. **Fire 的一个既有微调被撤**：原重算块里 `flammability = baseFlammability;` 是本项目顺手加的"首帧即正确"，原版没有（首帧按 -1 处理、下一帧才正常）。按照抄原则删除，行为差异仅首帧一帧。
5. 未跑 gradle（按约定主会话编译）。所有改动经过人工签名核对：新增引用 `TeamTrait/Events/Trigger/Mathf.clamp(float)/Puddle.getPuddle/getFlammability/World.isZone/getZone/Zone.isLaunchWave/updateWave/Rules.respawns/Player.respawns` 均已确认存在。
