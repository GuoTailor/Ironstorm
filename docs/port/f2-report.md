# F2 批次报告：开波表现与清场整链（A1 最严重问题）

对应审计 `audit-A1-entities-ai-game.md` 高优先级表前三行（WaveSpawner 整链）。
修复症状：敌人瞬间凭空出现、出生点不清场（可在出生点堵门建塔）、attack 图无核心增援。

---

## 1. 开波清场（spawnShockwave + Damage 清空投区）

**原版位置**：`mindustry/ai/WaveSpawner.java:78-83`（spawnEnemies 末段）

```java
eachGroundSpawn((spawnX, spawnY, doShockwave) -> {
    if(doShockwave){
        Time.run(20f, () -> Effects.effect(Fx.spawnShockwave, spawnX, spawnY, state.rules.dropZoneRadius));
        Time.run(40f, () -> Damage.damage(state.rules.waveTeam, spawnX, spawnY, state.rules.dropZoneRadius, 99999999f, true));
    }
});
```

**本项目落点**：`ai/WaveSpawner.java` spawnEnemies 末段（逐字照抄，`state.rules` → `Vars.state.rules`）。

**依赖补齐** — `entities/Damage.java` 新增原版口径 6 参重载（原版 Damage.java:180）：

| 新增 | 原版对应 | 说明 |
| --- | --- | --- |
| `damage(Team, x, y, radius, amount, boolean complete)` | Damage.java:180 | 单位：矩形粗筛（`Units.nearbyEnemies`，等价原版 `rect.setSize(radius*2).setCenter`）+ 圆形精筛 + `calculateDamage` 距离衰减 + 击退；`complete=true` 走 `completeDamage` |
| `completeDamage(Team, x, y, radius, amount)`（private） | Damage.java:257 | 遍历 `round(x/tilesize)±trad` 方格内敌方 `tile.entity.handleDamage`（= 原版 `tile.entity.damage`，本项目 TileEntity 无 `damage(float)`，`handleDamage` 即其等价物，含血量归零销毁） |
| `calculateDamage(...)`（private） | Damage.java:269 | falloff 0.4：中心全额、边缘 6 折 |
| `Events.fire(Trigger.exclusionDeath)` | Damage.java:191-193 | 玩家单位被 `9999999f` 级清场伤害波及时广播 |

**关键照抄点**：
- 时序：20 帧后特效、40 帧后伤害（两段独立 `Time.run`）。
- 原版 cons 内 `entity.getTeam() == team` 同队跳过、`entity.dst(x, y) > radius` 精筛、击退公式 `(1 - dst/radius) * 2 / mass`，全部照抄。
- 原版 `complete=false` 分支走 `tileDamage` 传播算法（F6 批大件）——本批**未移植**，留 TODO，当前 `complete=false` 只伤单位不动瓦片。
- `complete=false` 与既有 4 参 `damage(Team, x, y, radius, amount)` **并存不互改**：4 参是早期最小口径（无衰减/击退/建筑摧毁），消费方为 BulletType 溅射与 Lightning，动它会波及现有爆炸表现。

**与原版结构差异**：
- `entity == player` → `entity == Vars.player.unit()`：本项目 `Player` 不是 `BaseUnit` 子类（独立类），"玩家实体"等价物是玩家当前控制的单位。
- `Events.fire(Trigger.exclusionDeath)` 的 fire 保留（126.2 内核同样只有 fire、无内置监听者）。

**"误伤玩家核心"问题（任务要求写清）**：原版**没有任何针对玩家核心的豁免**——`damage(waveTeam, ...)` 打的是 waveTeam 的一切敌人，`completeDamage` 会把半径 `dropZoneRadius`（默认 200 = 25 格）内的玩家建筑（含核心）与单位全部清掉。原版的"安全性"仅来自两点：
1. 只对 `doShockwave=true` 的 spawn overlay 出生点清场（attack 核心增援点 `doShockwave=false` 不清，因为那是敌核旁、且清场伤害只会打到"waveTeam 的敌人"= 玩家，敌核自己毫发无伤）；
2. 正常地图出生点距玩家核心远超 25 格。玩家若故意在出生点旁建核心，原版同样会拆——这正是"防堵门"设计的代价面，配 `exclusionDeath` 事件兜底。

---

## 2. 延迟分批出生 + 出生特效

**原版位置**：`WaveSpawner.java:66-73`（地面分支）+ `WaveSpawner.java:144-150`（spawnEffect）

```java
Tmp.v1.rnd(spread);
BaseUnit unit = group.createUnit(state.rules.waveTeam);
unit.set(spawnX + Tmp.v1.x, spawnY + Tmp.v1.y);
Time.run(Math.min(i * 5, 60 * 2), () -> spawnEffect(unit));
//---
private void spawnEffect(BaseUnit unit){
    Effects.effect(Fx.unitSpawn, unit.x, unit.y, 0f, unit);
    Time.run(30f, () -> { unit.add(); Effects.effect(Fx.spawn, unit); });
}
```

**本项目落点**：`ai/WaveSpawner.java` 地面分支 + 新增私有 `spawnEffect(BaseUnit)`，逐字照抄。

**关键照抄点 / 时机差异处理（本批核心难点）**：
- 原版时间线：`create`（**未入组**，不可见不可交互）→ `set` 位置 → 延迟 `min(i*5, 120)` 帧 → `spawnEffect`：unitSpawn 图标落下特效 → 再延迟 30 帧 → `unit.add()`（此刻才入组）+ `Fx.spawn`。
- 本项目 `type.create()` 内部 `init()` 已 `Units.add(this)`（**create 即入组**）——直接照抄会退化成"延迟期间单位已在场景里可被攻击"，与原版语义相反。
- 等价处理：地面单位 create 后**立即 `Units.remove(unit)` 摘出**（等价原版"未 add 的单位"，期间无任何 update/渲染/碰撞），到延迟点 `spawnEffect` → `unit.add()` 重新入组。已写注释说明。
- `BaseUnit` 新增 `add()`（对应原版 `SyncTrait.add` 语义：入组 + `added()` 回调；带 `Units.units.contains(this, true)` 防重入，等价原版 `isAdded()`）。正常创建路径（init 自动入组）不走它，唯一调用方是 spawnEffect。
- `Tmp.v1.rnd(spread)`（arc：随机方向固定长度）→ `Tmp.v1.setToRandomDirection().scl(spread)`（gdx 1.14.2 的 Vector2 确认有 `setToRandomDirection`，已反编译 class 常量池核实）。
- `Effects.effect(Fx.unitSpawn, x, y, 0f, unit)` 需要 5 参重载 —— `entities/Effects.java` 新增 `effect(Effect, x, y, rotation, Object data)`（对应 arc 5 参，转调既有 6 参 color 版本）。
- 飞行单位**保持立即出生**（原版飞行分支无延迟：create → set → add 一步到位；本项目 create 即入组恰好等价）。
- 换图安全：延迟任务挂在本项目 `Time.run` 上，`Time.clear()` 时清空（既有机制，与原版 arc `Time` 一致）；被摘出的单位不在 `Units.units`，换图后无引用可 GC，无泄漏。

**与原版结构差异**：无逻辑差异；仅"create 即入组 → 先摘出再入组"这一层等价转换（见上）。

---

## 3. attack 模式核心增援

**原版位置**：`WaveSpawner.java:88-100`（eachGroundSpawn attack 分支）+ `WaveSpawner.java:102-115`（eachFlyerSpawn attack 分支）

```java
//地面：从敌方核心向玩家核心方向偏出 coreMargin + 核心半径的出生点（不清场）
if(state.rules.attackMode && state.teams.isActive(state.rules.waveTeam) && !state.teams.playerCores().isEmpty()){
    TileEntity firstCore = state.teams.playerCores().first();
    for(TileEntity core : state.rules.waveTeam.cores()){
        Tmp.v1.set(firstCore).sub(core.x, core.y).limit(coreMargin + core.block.size*tilesize);
        cons.accept(core.x + Tmp.v1.x, core.y + Tmp.v1.y, false);
    }
}
//飞行：直接在敌方核心上空
if(state.rules.attackMode && state.teams.isActive(state.rules.waveTeam)){
    for(TileEntity core : state.teams.get(state.rules.waveTeam).cores){ cons.get(core.x, core.y); }
}
```

**本项目落点**：`ai/WaveSpawner.java` 新增私有 `eachGroundSpawn(SpawnConsumer)` / `eachFlyerSpawn(Floatc2)`（含私有接口 `SpawnConsumer`，照原版结构；`Floatc2` 用既有 `func/Floatc2`），spawnEnemies 的地面/飞行循环改为走这两个遍历器。

**关键照抄点**：
- 判定链逐字照抄：`attackMode && teams.isActive(waveTeam)`（飞行分支无 playerCores 判定，地面分支有——照抄）。
- `coreMargin = tilesize * 3` 新增为类常量（原版同名同式）。
- 三个判定/取点 API 本项目均已有：`Vars.state.teams.isActive/playerCores()/cores(team)`（返回 `Array<Tile>`，等价原版 TeamData.cores 的 TileEntity 列表）。
- **坐标口径换算**：原版 `core.x/y` 是 TileEntity 世界坐标（= 建筑几何中心）；本项目核心列表给的是锚点 `Tile`，且 `Tile.worldx()` 是格左下角（memory：原版 worldx 本身是中心）——建筑中心一律用 `Block.centerX/centerY(core)`（与 `Renderer.drawDropZones` 同口径）。`Tmp.v1.set(firstCore)` → `Tmp.v1.set(firstX, firstY)`（libgdx Vector2 无 set(Position) 重载，F1 已踩过的坑）。
- **出生点（overlay）坐标保持 `spawn.worldx()/worldy()` 字面照抄**——本项目该值是格左下角、与原版格中心差半格 4px；相对 `spread = 16` 的随机散布与 `dropZoneRadius = 200` 完全不可见，且与 F1 空投区击退、`playerNear()` 同口径。

**与原版结构差异**：仅上述坐标口径换算；逻辑零差异。

**附带修复**：删掉了 `spawnEnemies` 开头的 `groundSpawns.size == 0` 提前返回——attack 图可以没有 spawn overlay，提前返回会连核心增援一起跳过（"attack 图无核心增援"的第二个根因）。保留 `world == null` 守卫。
另：`isSpawning()` 补上原版的 `&& !net.client()`（本项目 `!Vars.isClient()`）。

---

## 4. 顺手核对结论（同文件，未扩大范围）

| 项 | 结论 |
| --- | --- |
| 原版 WaveSpawner 的 `cons/filter/hammer` 字段 | 不存在。`cons` 只是原版私有接口 `SpawnConsumer` 的形参名，无 filter/hammer 字段——无需补 |
| `Logic.runWave → spawner.spawnEnemies()` 入参 | 原版 `Logic.java:136-141`：`spawner.spawnEnemies()` 无参 + `state.wave++` + isLaunchWave 判定 + `WaveEvent`。本项目 `core/Logic.java:170-184` 逐条对应一致，未改 |
| `SpawnGroup.createUnit(team)` | 原版 `type.create(team)` + health + effect；本项目 `type.create()`（无参，`init` 内 `Units.add`）+ `setTeam` + health + effect，行为一致，未改 |
| `Fx.unitSpawn / Fx.spawn / Fx.spawnShockwave` | 三个特效均已按原版移植（spawnShockwave 用 `e.rotation` 传半径），无需改 |
| `dropZoneRadius` 口径 | 与 F1 空投区击退、`playerNear`、存档（SaveMeta）同源同字段，一致 |

## 改动文件清单

| 文件 | 改动 |
| --- | --- |
| `ai/WaveSpawner.java` | 地面分支改延迟分批 + spawnEffect；新增 eachGroundSpawn/eachFlyerSpawn/SpawnConsumer/coreMargin；补开波清场段；删 groundSpawns 空提前返回；isSpawning 补客机判定 |
| `entities/Damage.java` | 新增 6 参 `damage(..., boolean complete)` + 私有 `completeDamage`/`calculateDamage`；import Events/Trigger |
| `entities/type/BaseUnit.java` | 新增 `add()`（入组 + added()，防重入） |
| `entities/Effects.java` | 新增 5 参 `effect(Effect, x, y, rotation, Object)` 重载 |

## 待主会话验证

- `gradlew :core:compileJava`（本批未跑 gradle）。
- 运行期表现：开波后敌人应从出生点分批淡入（unitSpawn 图标特效），出生点 20 帧/40 帧后出现冲击波环并清场；attack 图敌人应从敌核旁刷出。
