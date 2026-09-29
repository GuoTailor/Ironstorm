# F14 地图缩略图链（T0-②组A）报告

目标：自定义游戏/地图选择界面的地图缩略图从「空白占位（nomap）」变为真实出图。
对照原版：`mindustry/maps/Maps.java`（loadPreviews/queueNewPreview/createNewPreview/saveMap/importMap）、
`mindustry/io/MapIO.java`（generatePreview(Map)/generatePreview(Tile[][])/colorFor）、
`mindustry/maps/MapPreviewLoader.java`。

## 一、链路设计

```
入档点（3 个，地图文件刚落盘）
  ├─ MapEditorDialog.saveMap   → Maps.createNewPreview(map)   （对应原版 Maps.saveMap 收尾）
  ├─ Maps.exportCurrent        → Maps.createNewPreview(map)   （同上）
  └─ Maps.importMap            → Maps.createNewPreview(map)   （对应原版 importMap）
        │
        ▼
MapIO.generatePreview(Map)   —— 离线流式读 .msav（PHNX 格式），不建世界：
  header → 跳过 meta 区 → 解析 content 区（局部方块名字表）→ 解析 map 区
  地板/覆盖层 + 方块/队伍逐格 colorFor 上色；实体数据 = v7「2B 长度前缀子块」整段跳过
        │
        ├─ map.texture = new Texture(pixmap)      （GL 线程，同步）
        └─ map.previewFile().writePNG(pixmap)     （maps/<名>.png，下次启动直载）

Maps.loadPreviews()  —— Vars.init() 里 maps.load() 之后调一次：
  预览 PNG 存在 → new Texture(FileHandle) 同步直载；缺失 → queueNewPreview（post 到主线程下一帧生成）
  headless（Core.graphics == null）整体跳过，服务端不生成不加载。

消费端（4 个对话框，全部已是「texture != null 就画」的写法，本次只补了缺的那半）：
  MapsDialog（previewImage/previewBorder）、CustomGameDialog（previewOf）、
  MapPlayDialog（previewOf）、editor/MapLoadDialog（本次代码改动：nomapRegion() → map.texture 优先）。
```

### SaveIO 格式 v7（本批唯一动存档格式的点）

- **写入**：`writeMap` 里实体数据由「内联裸数据」改为 `[2B 长度][数据]` 子块 —— 与原版
  `SaveVersion.writeMap` 的 `writeChunk(stream, true, ...)` 内嵌子块**同构**。这是离线预览的前提：
  预览读取器不解析任何实体格式，靠长度整段跳过。
- **读取**：v6/v7 双支持（`SaveIO.isSupportedVersion`），v6 实体仍按内联解析，**v6 旧档可读不破坏**；
  写入一律 v7。`readMap` 增加 `formatVersion` 参数分支。
- **网络**：`SaveIO.write/read` 与网络同步共用（`NetworkIO`），双端同一构建，无跨版本风险。
- `readChunk` 由 private 放宽为包内可见（`MapIO` 同包复用）。

### 多格建筑的预览呈现（本项目与原版格式的差异处理）

原版 MSAV 的多格建筑逐格存 wallID，预览回调逐格上色；本项目存档只在**中心格**存方块（卫星格 air）。
预览读取器按 `block.size` + `-(size-1)/2` 锚点（与 `Tile.setBlock` 铺开逻辑一致）把颜色补画到整个
占地区域，并在占地区域每格下方画半透明阴影（原版逐格画阴影的等价效果）。视觉与原版一致。

## 二、TODO 消化（8 条）

| # | 位置 | 处理 |
|---|---|---|
| 1 | `Maps` 类 javadoc「缩略图依赖 MapIO.generatePreview，暂缓」 | 改为已补声明 |
| 2 | `Maps.queueNewPreview` 打日志桩 | 真实化：post → createNewPreview |
| 3 | `MapPreviewLoader` javadoc「尚未注册进 Core.assets」 | 改为**刻意不注册**声明（见偏离 2） |
| 4 | `MapPreviewLoader.loadAsync` 内联 TODO（queueNewPreview 是桩） | 兜底已可达，注释更新 |
| 5 | `MapsDialog` javadoc「TODO 缩略图整条链未移植」 | 改为已接线 |
| 6 | `CustomGameDialog`「缩略图半落实」 | 改为已接线（代码本来就认 texture） |
| 7 | `MapPlayDialog`「预览纹理半落实」 | 同上 |
| 8 | `MapLoadDialog`「缩略图降级 + nomapRegion 硬编码」 | **代码改动**：`map.texture != null` 时 `new BorderImage(map.texture, 2f)` 出真图 |

顺手对齐（非本批 8 条内）：`SavePreviewLoader` javadoc 的注册 TODO 改为与 MapPreviewLoader 同口径的
「刻意不注册」声明；`Map.texture` 字段注释（原「恒为 null」）更新。

## 三、偏离声明（相对原版）

1. **loadPreviews 直载 PNG，不走 AssetManager/MapPreviewLoader**：本项目 `Core.assets` 没有逐帧
   `update()` 循环（仅启动时 `finishLoading()` 一次），注册异步加载器只会卡死；且 MapPreviewLoader 的
   `getDependencies` 依赖 `"contentcreate"` 资产，本项目 content 是手工加载、无此资产。预览 PNG 很小，
   同步加载开销可忽略。MapPreviewLoader 保留（API 完整性），其失败兜底 `queueNewPreview` 依然可达。
2. **queueNewPreview 不设 previewList/createAllPreviews 排队结构**：原版 post 进集合、由 content
   加载完成回调统一 drain；本项目无该时序锚点，直接 post 单张生成（同样延后到主线程、不阻塞当前帧）。
3. **createNewPreview 同步写 PNG**（原版走 AsyncExecutor 异步）：缩略图至多几百 KB，且本实现要
   先写盘再 `pix.dispose()`，异步反而需要复制像素。
4. **importMap 预览失败不回滚**：原版假设「预览读不了 = 地图坏」→ 删图重抛 IOException。本项目
   v6 旧格式地图**可正常游玩**但离线预览读不了（见下），此时保留地图 + nomap 占位 + 记日志。
5. **不写 `-cache.dat` 侧车**：本项目 spawns/teams 统计存在存档 meta tags 里（既有设计），
   `Maps.load()` 已恢复，无需原版的 writeCache。
6. **v6 旧格式地图无法离线生成预览**（v6 实体数据内联、无长度可跳）→ 永远 nomap，
   在编辑器里重存一次（会写成 v7）即可出图。报告此为已知限制。
7. **编辑器 saveMap/exportCurrent 从文件路径生成**（原版 saveMap 从内存瓦片 `generatePreview(world.getTiles())`
   生成）：文件刚写出、数据与内存瓦片一致，统一走 `createNewPreview` 少一条特例分支。
   `generatePreview(Tile[][])` 重载仍按原版签名移植（当前无调用方，API 完整性）。
8. 预览上色的像素越界守卫（`px/py` 夹取）为本项目新增的防御（原版直接画）。

## 四、改动文件清单

| 文件 | 改动 |
|---|---|
| `io/SaveIO.java` | version 6→7；writeMap 实体长度前缀子块；readMap(formatVersion) v6/v7 分支；read/readMeta 走 isSupportedVersion({6,7})；readChunk 包内可见 |
| `io/MapIO.java` | 新增 `generatePreview(Map)`（离线流式）与 `generatePreview(Tile[][])`（原版重载）；类 javadoc 更新 |
| `maps/Maps.java` | 新增 `loadPreviews()`、`createNewPreview(Map, Cons<Exception>)`；`queueNewPreview` 真实化；`exportCurrent`/`importMap` 接预览；类 javadoc 更新 |
| `maps/Map.java` | `texture` 字段注释更新 |
| `maps/MapPreviewLoader.java` | javadoc（接线状态/兜底可达）+ 内联 TODO 注释更新 |
| `editor/MapEditorDialog.java` | `saveMap` 落盘后 `createNewPreview` |
| `editor/MapLoadDialog.java` | 预览出真图 + javadoc |
| `ui/dialogs/MapsDialog.java` / `CustomGameDialog.java` / `MapPlayDialog.java` | javadoc 更新（消费逻辑本已就绪） |
| `Vars.java` | `init()` 里 `maps.load()` 后接 `maps.loadPreviews()` |
| `io/SavePreviewLoader.java` | javadoc 口径对齐（刻意不注册） |

## 五、不确定的点 / 待主会话验证

- **未编译未运行**（按批次纪律）。重点核对点：① `SaveIO.readMap` 的 v7 有界读取（`readUnsignedShort`
  长度 + `readFully`）与 writeMap 的 `writeShort(bytes.length)` 对应关系；② `MapIO.generatePreview`
  的实体跳过与 `block.hasEntity()` 判定的一致性（未知方块按 air 跳过与 `SaveIO.readMap` 同口径，
  两边会一致地错位/一致地正确）；③ v6 旧档读档路径（`read(in)` 的 v6 分支）未被本次写入路径影响。
- 旧 v6 地图（含首次启动自动导出的默认图，如果它们还是 v6）首次运行会因预览生成失败而保持 nomap，
  编辑器重存后恢复 —— 已在 `MapIO.generatePreview` 的异常信息里说明。
- `Maps.load()` 若未来被再次调用（当前只在 init 调一次），旧列表的预览纹理不会释放（与既有纹理
  生命周期口径一致，未额外处理）。
