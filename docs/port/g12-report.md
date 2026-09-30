# G12 批次报告 —— M8/D5 CoreBlock 重生链（第二轮遗留项）

约定：原版 = `D:\IdeaProjects\Mindustry-126.2`；本项目 = `d:\IdeaProjects\phoenix`。

## 背景

第二轮审计把「单位重生」列为 **M8（中低）** 与 **D5（低）**：

- **M8**：原版 `CoreBlock` 有 `onUnitRespawn(@Remote)` + `update`（respawn 进度/heat）+ `drawLayer`（重生动画）
  + `shouldActiveSound`；本项目这些都没有，重生被简化到 `Player.update()` 里（无核心处动画/进度条、无 `Fx.spawn`）。
- **D5**：原版重生在核心格中心 `set(tile.drawx(), tile.drawy())` + `applyImpulse(0, 8)`；
  本项目 `Control.spawnPlayerUnit()` 把出生点放在 `core.getY() + 32f`（核心上方一格）、无冲量。

当时因「需改用户未提交 WIP 的 `Player.java`」而跳过。本批次完成。

## 原版链路（照抄目标）

```
Player.update() 死亡分支
  → updateRespawning()                      // 选出生点：spawner / lastSpawner / getClosestCore()
      → CoreEntity.updateSpawning(player)   // spawnPlayer = player; progress = 0; player.mech = mech;
          → Player.beginRespawning(core)    // spawner = lastSpawner = core; 位置挪到核心; 再回调一次
CoreBlock.update(tile)  每帧
  → 把 spawnPlayer 钉在核心中心；heat → 1；time += delta；progress += 1/respawnTime * delta
  → progress >= 1 → Call.onUnitRespawn(tile, spawnPlayer)
      → onUnitRespawn: Fx.spawn + progress=0 + player.onRespawn(tile) + applyImpulse(0,8) + spawnPlayer=null
          → Player.onRespawn(tile): 速度清零 / rot=90 / dead=false / spawner=null / respawns-- / 音效 / 落点 / clearItem / heal
CoreBlock.drawLayer(tile)   heat > 0.001 → RespawnBlock.drawRespawn(...)   // 四角光环+进度条+机甲剪影
CoreBlock.shouldActiveSound  spawnPlayer != null
```

## 本项目改动

### 1. `world/blocks/storage/CoreBlock.java`

| 项 | 原版 | 本项目 |
| --- | --- | --- |
| `mech` 字段 | `public Mech mech = Mechs.starter` | 照抄（加载顺序 `Mechs → UnitTypes → Blocks`，与原版一致，构造时已就绪） |
| 重生状态 | `CoreEntity.spawnPlayer/progress/time/heat`（`protected`） | 同名字段，改 **`public`**（沿用本项目 `MechPadEntity.player/progress/heat` 的既有口径，便于探针读取） |
| `SpawnerTrait` | `CoreEntity implements SpawnerTrait` | 照抄；`hasUnit` 因「Player 不是 Unit」改为 `spawnPlayer.unit() == unit` |
| `updateSpawning` | `player.mech = mech` | `player.selectedType = respawnType()`（本项目无机甲装备系统 → 落到单位类型；见下） |
| `update` | `Block.update(Tile)` 方法 | 本项目 `Block.update` 是 **boolean 字段** → 每帧钩子改为 `CoreEntity.update()`（`World.updateTiles` 调用，受 `block.update` 门控） |
| `drawLayer` | `Layer.overlay` 层 | 本项目 `Block` 无 `layer` 字段；但 `Renderer` 在 `block.draw(tile)` 后紧跟调 `drawLayer` → 效果等价 |
| `onUnitRespawn` | `@Remote(called = Loc.server)` RPC | 本项目无 RPC 层 → 由 `CoreEntity.update()` 直接调用；`applyImpulse` 落到 `player.unit()` |
| `shouldActiveSound` | `spawnPlayer != null` | 照抄；⚠ 本项目**无工作音效系统** → 无人调用（同 `LaserTurret.shouldActiveSound` 的降级） |
| 坐标 | `tile.drawx()/drawy()`（中心） | `block.centerX/centerY`（本项目 `worldx()` 是角点） |

新增 `respawnType()`：按 `mech.name` 解析同名 `UnitType`（`Mechs.starter = "dart-ship"` ↔ `UnitTypes.dartShip`，
项目里 `Mechs.dart` 的构造名就是 `"dart-ship"`，故能直接命中；解析不到退回 `UnitTypes.dartShip`）。

### 2. `entities/type/Player.java`

- 新增字段 `spawner` / `lastSpawner`（`SpawnerTrait`）。
- 新增 `updateRespawning()` / `beginRespawning(SpawnerTrait)` / `onRespawn(Tile)`（对应原版 Player.java:825-852 / 104-118）。
- `update()` 死亡分支重写为原版写法（`isBoosting = false` + `respawns > 0 || !limitedRespawns` → `updateRespawning()`），
  活着时 `spawner = null`。
- **删除 `deadTime` 字段**（原版没有；它是旧版"Player 自己计时"方案的残留，计时已移到核心）→ 同步修
  `unit()` setter 与 `editor/MapEditorDialog` 里的注释。

**刻意偏离（已在代码注释逐条声明）**：

1. 原版 `onRespawn` 复用同一个 Unit 对象；本项目 Player 不是 Unit、死亡时单位已被移除 →
   走 `Control.spawnPlayerUnitAt(...)` 重新创建（与开局出生/机甲平台换装同源，内部已补满血 = 原版 `heal()`）。
2. 原版 `boostHeat = 1f; achievedFlight = true; baseRotation = 90f`：本项目这些是被控单位上的字段且无对应过渡状态机 → 只设 `rotation`。
3. 编辑器分支：原版 `set(地图中心); setDead(false)` → 本项目改为 `spawnPlayerUnitAt(地图中心)`（等价效果）。

### 3. `editor/MapEditorDialog.java`

只改一句注释（`deadTime` 已删除）。

## 实测（PHOENIX_RESPAWNTEST，验完已删）

`PHOENIX_AUTOPLAY=1 PHOENIX_RESPAWNTEST=1 PHOENIX_SHOT=640`，第 160 帧 `unit.damage(99999)` 打死玩家：

```
DBG respawn KILL f=160 coreCenter=484.0,724.0 respawnTime=240.0 respawnsBefore=5 deadNow=true
DBG respawn f=200 dead=true respawns=5 spawner=true core{spawn=true prog=0.115 heat=0.956}
DBG respawn f=400 dead=true respawns=5 spawner=true core{spawn=true prog=0.555 heat=1.000}
DBG respawn f=580 dead=true respawns=5 spawner=true core{spawn=true prog=0.972 heat=1.000}
DBG respawn DONE f=592 pos=483.99716,727.7989 coreCenter=484.0,724.0 dx=-0.00 dy=3.80 vel=0.000,7.660 rot=90.0 respawns=4 type=dart-ship
DBG respawn f=600 dead=false respawns=4 spawner=false core{spawn=false prog=0.000 heat=0.776}
DBG respawn f=640 dead=false respawns=4 spawner=false core{spawn=false prog=0.000 heat=0.184}
```

逐项判定：

| 项 | 结果 |
| --- | --- |
| 核心接管（M8） | `spawner=true`、`core.spawnPlayer=true`、`progress` 从 0 线性推进、`heat → 1.000` ✓ |
| 进度速率 | 每帧 `delta/respawnTime`；`Time.delta()≈0.55`（高刷屏）→ 240 单位约 440 帧走完（与 `entity.delta()` 语义一致） |
| 落点（D5） | `dx = -0.00`（与核心中心**逐位吻合**）、`dy = +3.80`（冲量把单位顶起来）✓ |
| 冲量（D5） | `vel = (0.000, 7.660)` —— `applyImpulse(0,8)` 经一帧阻尼后 8→7.66 ✓ |
| 朝向 | `rot = 90.0` ✓ |
| 次数 | `respawns 5 → 4` ✓ |
| 重生机型 | `type = dart-ship`（核心 mech → `selectedType`）✓ |
| 收尾 | `spawner=false`、`spawn=false`、`progress=0`、`heat` 0.776→0.399→0.184 衰减 ✓ |
| 动画（M8） | 截图可见核心上的四角光环 + 橙色进度条（约 1/3 满）+ 机甲剪影（`RespawnBlock.drawRespawn`）✓ |

## 遗留

- `shouldActiveSound` 无消费者（本项目无工作音效系统）—— 与 `LaserTurret`/`LiquidTurret` 同属 B 类降级。
- `MechPad` 的换装进度动画（原版 `drawLayer → RespawnBlock.drawRespawn`）仍未接线：`RespawnBlock` 已移植、
  `Shaders.build` 已存在，属独立小项，未纳入本批。

## ⚠ 提交树状态说明（与 G12 无关的既有问题）

用 `git worktree` 对提交 `3e1d36b` 做独立编译**失败**，但**根因不在 G12**：

- 已提交的 `core/Renderer.java:573-585` 引用了 `player.carriedItem()` / `player.carriedAmount()`；
- 这两个方法只存在于**用户未提交的** `Player.java`（"背包分裂"重构）；
- 该状态在 G12 之前就已存在（`git grep -n "carriedItem\|carriedAmount" e89a10f -- core/src/` 同样命中 `Renderer.java`）。

也就是说**仓库提交树 HEAD 一直处于"半提交重构"状态、无法单独编译**；能编译的是**工作区**（含用户 WIP）。
本批提交用 hunk 级选择性暂存避开用户 WIP（`Player.java` 的 `itemtime` 行与我的改动相邻，已把该行还原为 HEAD 版本再入索引），
所以提交树里 `Player.java` 仍是 HEAD 的 `itemAmount` 版本 —— 与既有的 `Renderer.java` 引用不匹配属**同一既有问题**，
待用户提交其 WIP（或单独提交 `Player.java`）后即恢复。**工作区（可运行状态）编译绿、重生链实测通过。**
