# F10 修复报告（darkness 整链 / Door 手动开合 / 核心可拆）

本批 3 项独立修复，全部照原版补齐。不跑 gradle（主会话编译验证）。

---

## 1. 世界暗化层（darkness）整链

**原版位置**
- `world/Tile.java:123-125`：`isDarkened()` = `block().solid && !block().synthetic() && block().fillsTile`
- `core/World.java:178-180`：`endMapLoad()` 里 `if(!headless){ addDarkness(tiles); }`
- `core/World.java:300-354`：`addDarkness(Tile[][])` —— 自然墙先全标 4，迭代 4 轮向暴露面衰减（四邻有更小值则 -1），结果**写进 tile.rotation**；四邻全为 4 的格子记 5（连墙贴图都可省）
- 渲染消费方：`graphics/BlockRenderer.java:75-94`（黑幕按 `alpha=min((rot+0.5)/4, 1)` 画进 fog 纹理）、`graphics/FloorRenderer.java:185`（walls 层跳过 `isDarkened() && rotation()>=5` 的墙贴图）、`graphics/MinimapRenderer.java:165`（小地图 `mul(1f - rotation/4f)`）

**darkened 数据来源**：不是地图/存档数据带的，而是**每次建图/读档时由 `World.addDarkness` 现算**——按自然墙离暴露面的距离得 0-4 暗度、全包围记 5，复用 `tile.rotation` 字段存储（自然墙无朝向语义）；fog 纹理渲染侧本项目**早已存在**（`graphics/BlockRenderer` 的 fog 缓冲 + `Renderer.drawTiles` 里的 `blocks.drawFog()`），缺的只是数据生成侧，补上后整链自动打通。

**本项目落点**
| 文件 | 改动 |
| --- | --- |
| `world/Tile.java` | 补 `isDarkened()`（照原版，插在 drawy 与 floor 之间） |
| `core/World.java` | 补 `addDarkness()`（照原版逐字；一维索引 `y*width+x`，`Structs.inBounds`→范围判断、`Mathf.num`→三元）；`endMapLoad` 与 `createMap` 各补 `if(!Vars.headless) addDarkness();`（createMap 是本项目自加入口、不经 endMapLoad，普通开局走它） |
| `io/SaveIO.java` | `readMap` 收尾补 `world.addDarkness()` 重算：原版读档最终也走 endMapLoad→addDarkness；且本项目 `Tile.setBlock`/`loadRestore` 的 `rotation % 4` 会破坏存档里的暗度编码（5→1） |
| `core/Renderer.java` | `drawTiles` 方块趟补原版 FloorRenderer.java:185 等价判据 `!(tile.isDarkened() && tile.rotation() >= 5)`（全黑格跳过墙贴图） |
| `graphics/MinimapRenderer.java` | `colorFor` 补暗度压暗（原版 MinimapRenderer.java:165；原判据 `cacheLayer==walls` 本项目无该字段 → 等价用 `isDarkened()`；此前注释声明的省略随数据补齐而还原） |

**照抄要点**：暗度值域 0-5、迭代 4 轮、扩散衰减公式、alpha=(rot+0.5)/4（已在既有 fog 烘焙里）、`!headless` 守卫全部照原版；本项目 `block()` 对卫星格转发中心方块，卫星格 `isDarkened()` 为 false，暗度写入不会污染多格建筑。

---

## 2. Door 手动开合 + 音效 + 寻路刷新

**原版位置**：`world/blocks/defense/Door.java:20-46` —— `consumesTap=true` + `tapped()`（防夹 + 30 帧冷却）→ `onDoorToggle`（@Remote）＝ 写 open + `pathfinder.updateTile` + openfx/closefx + `Sounds.door.at(tile)`。

**本项目落点**：`world/blocks/defense/Door.java`（重写）
- `tapped()` 照原版判据逐字：`Units.anyEntities(tile) && open` → 不能关（防夹）；30 帧冷却（原版走 `TileEntity.timer`，本项目无 Timer 模块 → `DoorEntity.toggleCool` 自计）
- `onDoorToggle`（@Remote 本项目保持注释态）→ 本地 `toggle()`：写 open + `Vars.pathfinder.updateTile(tile)` + openfx/closefx（位置用 `Block.centerX/centerY`，本项目 drawx 是角点口径）+ `Sounds.door.play(1f)`（原版 `at(tile)` 按位置播放，本项目 Sound 是 libgdx `com.badlogic.gdx.audio.Sound` 无 at 重载 → 降级无定位）
- 自动开合保留（此前类注释已声明的简化点），与手动**共用 open 字段**且走同一条 toggle 路径：`DoorEntity.auto` 标记"开来自自动"，手动开关清掉它（手动开门后单位离开不自动关；手动关门后单位走近再自动开）
- `DoorEntity.write/read` 照原版补 `open` 布尔入档（子类追加字段直接读写的项目惯例）
- `entities/Units.java` 补 `anyEntities(Tile)` 重载（照原版 Units.java:49-52；原版 drawx 即中心，本项目换 `centerX/centerY`）

**对应关系说明**：本项目点击层无 `consumesTap` 字段，等价机制是 `tapped` 返回 true 消费点击（DesktopInput.java:341 / MobileInput.java:553 已有调用链）；缺省项：`solidifes`（Block 无该字段）、`getCursor` 手型光标（Block 无该回调），不造接口。

**Sounds.door**：存在 —— annotations 生成的 `io.anuke.mindustry.gen.Sounds.door`（`sounds/door.ogg` 资源在库）；但音频系统未启用（`Sounds.load()` 注释态，运行时是 MockSound 静音），调用路径已接好。

---

## 3. 核心可拆修复

**原版位置**：`world/Build.java:149-152` `validBreak` 含 `tile.block().canBreak(tile)`；`world/Block.java:167-169` `canBreak` 默认 true；`world/blocks/storage/CoreBlock.java:147-149` 覆写 false。

**本项目落点**
| 文件 | 改动 |
| --- | --- |
| `world/Block.java` | 补 `canBreak(Tile)` 默认 true（原版 Block.java:167-169） |
| `world/blocks/storage/CoreBlock.java` | 覆写 `canBreak` 返回 false（原版 CoreBlock.java:147-149） |
| `world/Build.java` | `canDeconstruct` 补 `tile.block().canBreak(tile)`（对齐 validBreak 子句序） |

**拆线路径核对**：本项目拆除判据统一走 `Build.canDeconstruct` —— 玩家（`MobileInput.java:168`）、建造无人机（`BuilderTrait.java:101`）、`BaseUnit.java:827`、`deconstruct()` 入口四处调用同一方法，补一处全覆盖。

---

## 改动文件总表（10 个）

1. `core/src/com/phoenix/game/world/Tile.java`
2. `core/src/com/phoenix/game/core/World.java`
3. `core/src/com/phoenix/game/io/SaveIO.java`
4. `core/src/com/phoenix/game/core/Renderer.java`
5. `core/src/com/phoenix/game/graphics/MinimapRenderer.java`
6. `core/src/com/phoenix/game/world/blocks/defense/Door.java`
7. `core/src/com/phoenix/game/entities/Units.java`
8. `core/src/com/phoenix/game/world/Block.java`
9. `core/src/com/phoenix/game/world/blocks/storage/CoreBlock.java`
10. `core/src/com/phoenix/game/world/Build.java`
