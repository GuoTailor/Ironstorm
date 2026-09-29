# F16 报告：集结与命令 AI（T0-②组G）

日期：2026-09-30　执行：fix-f16
范围：3 条 TODO（rally 流场 / CommandAI.hit / afterRead 存档）+ 前置依赖 Units.nearbyCheck

## 完成情况总览

| # | 条目 | 结论 |
| --- | --- | --- |
| 1 | Pathfinder.rallyPoints 流场 | **已补**（ai/Pathfinder.java） |
| 2 | CommandAI.hit() 受击反击 | **已补**（ai/types/CommandAI.java + entities/type/BaseUnit.java 挂钩） |
| 3 | afterRead() 读档恢复命令 | **不做**（考据：原版根本没有命令状态存档，见下） |
| 4 | Units.nearbyCheck（前置） | **已补**（entities/Units.java，boolean 存在性判定版） |

## 1. Pathfinder.rallyPoints 流场

**改动**：`ai/Pathfinder.java` 的 `PathTarget.rallyPoints` targeter 从空 TODO 补为：

```java
rallyPoints((team, out) -> {
    if(Vars.indexer == null) return;
    for(Tile tile : Vars.indexer.getAllied(team, BlockFlag.rally)){
        if(tile != null) out.add(tile.pos());
    }
});
```

与原版 126.2 `Pathfinder.java:311-315`（`indexer.getAllied(team, BlockFlag.rally)`）逐句对齐。
本项目 `BlockIndexer.getAllied(Team, BlockFlag)` 已存在（flagMap 内层在 newFlagMap() 里全量建好，
不会返回 null；判定只防 `Vars.indexer` 尚未初始化）。

**链路现状（考据）**：
- 消费点 `GroundUnit.rally` / `FlyingUnit.rally`（UnitState 状态机）**早已存在**：
  `moveToCore(PathTarget.rallyPoints)` → `moveTo(PathTarget)` → `pathfinder.getTargetTile(tile, team, rallyPoints)`。
  FlyingUnit 覆写 `moveTo` 走直线（`moveToCore`），与原版 v6 语义一致——rally 流场只服务地面单位。
- **进入 rally 状态的入口（v6 指挥中心全队广播）在本项目已刻意退役**：`CommandCenter.java:33-36`
  注释明确"旧指令下发逻辑已随 RTS 命令系统的引入整体移除（对应 v8 提交 c324f2124b 删除旧 CommandCenter）"，
  本项目 CommandCenter 即 v8 的 LegacyCommandCenter 空壳。v8 中 rally 命令同样不存在
  （v146 `ai/UnitCommand.java` 只有 move/repair/rebuild/assist/mine/boost 六个命令，无 rally/attack/retreat）。
- 因此**不在 CommandAI 里加 rally 移动引导**：v7/v8 的 CommandAI 源码（v136/v146 均已核对）
  没有任何 rally 消费点，登记描述里"CommandAI 里 rally 目标的移动引导"在原版中不存在。
  本批补齐 targeter 后，`UnitState.rally` 一旦被调用（未来接回指挥机制或调试钩子直接 `state.set(rally)`）
  即与原版行为完全一致。

## 2. CommandAI.hit() 受击反击

**参考源**：原版 126.2（v6）无 CommandAI（该类 v7 后期才有，本机参考工程里连 `ai/types/` 目录都没有）。
从 GitHub 拉取 Mindustry v136 / v146 tag 的 `mindustry/ai/types/CommandAI.java` 对齐（v146 语义为准）：

```java
// v146 原文
public void hit(Bullet bullet){
    if(unit.team.isAI() && bullet.owner instanceof Teamc teamc && teamc.team() != unit.team && attackTarget == null &&
        !(teamc instanceof Unit u && !u.checkTarget(unit.type.targetAir, unit.type.targetGround)) && timer.get(timerTarget4, 60f * 10f)){
        commandTarget(teamc, true);
    }
}
```

**移植到 `ai/types/CommandAI.java`**，逐条件映射：

| v146 条件 | 本项目实现 |
| --- | --- |
| `unit.team.isAI()` | `!unit.isPlayer`（偏离：Team 无 isAI API；本项目玩家单位在 BaseUnit.update 里本就不跑 CommandAI，个体级判定等价，注释已写明） |
| `bullet.owner instanceof Teamc` | `bullet.getOwner() instanceof TargetTrait`（BaseUnit/TileEntity 都实现 TargetTrait） |
| `teamc.team() != unit.team` | `bullet.getTeam() != unit.getTeam() && unit.getTeam().isEnemy(...)` |
| `attackTarget == null` | 同名判定 |
| `!u.checkTarget(air, ground)` | `attacker.isFlying() && !unit.getType().targetAir` 时跳过（本项目 UnitType 无 targetGround，恒可对地） |
| `timer.get(timerTarget4, 60f*10f)` | 同款（新增 `timerTarget4 = 3`、`hitCooldown = 600f` 常量；Interval(4) 容量足够） |
| `commandTarget(teamc, true)` | `commandTarget(..., true)`（stopAtTarget=true，到达射程即放弃——防御性反击，defaultBehavior 已支持） |

**触发点挂钩**：v146 挂在 `UnitComp.collision(Hitboxc other)`（单位侧收到子弹）。
本项目等价位置是 `BaseUnit.collision(SolidTrait other, float x, float y)`（EntityCollisions 双向调用，
单位侧分支与 v146 同构），在既有 `DamageTrait` 分支前加：

```java
if(other instanceof com.phoenix.game.entities.type.Bullet && commandAI != null){
    commandAI.hit((com.phoenix.game.entities.type.Bullet)other);
}
```

核对了 `DamageTrait.getShieldDamage()` default = `damage()`：单位侧分支本就是子弹对单位
施加伤害的入口，hit() 与伤害同一触发时机，无重复计数（子弹侧 `Bullet.collision` 是另一分支，不冲突）。

## 3. afterRead() 读档恢复命令 —— 不做（考据结论）

- 原版 126.2 全工程 grep `afterRead` **0 命中**。
- v136 与 v146 的 CommandAI 亦无 `write()/read()/afterRead()`——命令状态
  （`targetPos/attackTarget/command/pathId/stopAtTarget`）**在原版任何版本都不入档**；
  AIController 也没有（v146 AIController 无存档方法）。读档后单位 AI 从头开始，是原版行为。
- 本项目 SaveIO 侧无需对称扩展。CommandAI 类头注释已把该 TODO 改为考据结论。

## 4. Units.nearbyCheck（前置依赖）

- 考据：v6（126.2）/v7（v136）/v8（v146）原版 `Units` **均无同名方法**（全源码 grep 确认）；
  登记名应是当时记录偏差。最接近的原版语义是 v146 `Units.anyEntities(x, y, w, h, Boolf<Unit>)`（矩形存在性）。
- 按登记口径实现圆形存在性判定，`entities/Units.java` 新增：

```java
public static boolean nearbyCheck(float x, float y, radius, Predicate<BaseUnit> pred)  // 找到即返回 true
public static boolean nearbyCheck(float x, float y, float radius)                      // 谓词缺省版
```

- 不补带 `Cons` 回调的 `(x,y,radius,cons)` 版本：与既有 `Units.nearby(x, y, radius, cons)`
  完全重复（本工程 `units` 是即时数组，无 EntityGroup 延迟提交问题）。blockingUnit 避让本体
  仍是后续工作（CommandAI 注释已更新为"前置已补"）。

## 文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/ai/Pathfinder.java` | rallyPoints targeter 补实现 |
| `core/src/com/phoenix/game/ai/types/CommandAI.java` | 新增 hit() + timerTarget4/hitCooldown；类头 TODO 注释更新 |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | collision() 挂 Bullet → commandAI.hit() |
| `core/src/com/phoenix/game/entities/Units.java` | 新增 nearbyCheck 两个重载 |

## 验证方式（未执行——本批次禁编译/运行）

静态核对已通过：`BaseEntity.getTeam()`（Bullet/BaseUnit 均继承）、`Team.isEnemy`、
`Position.withinDst`、`Interval.get(int,float)`、`UnitType.targetAir`、`BaseUnit.isFlying/isPlayer`、
`BlockIndexer.getAllied` 均存在且签名匹配；instanceof 模式匹配在本文件已有先例。
建议后续（允许编译时）：`gradlew :core:compileJava` + `PHOENIX_AUTOPLAY=1` 放指挥中心后
`state.set(rally)` 验证流场，或敌我交火看空闲单位是否转火。

## 不确定点

1. `hit()` 的 `!unit.isPlayer` 替代 `team.isAI()` 是个体级 vs 队伍级的近似（多人同队时语义有差），
   本项目无 Team.isAI / playerGroup 队级判定设施，未为此新增子系统。
2. flying 单位对 rally 流场不可达是原版语义（v6 FlyingUnit.moveTo 覆写为直线），非缺陷。
