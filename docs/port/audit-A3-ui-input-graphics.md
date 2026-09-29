# A3 审计：ui / input / graphics / core（除 Logic）/ world.meta / io / net

> 审计范围：本项目 `com/phoenix/game/{ui(含 dialogs,fragments,layout), input, graphics, core(除 Logic), world/meta, io, net}` ↔ 原版 `mindustry/{ui, input, graphics, core(除 Logic), world/meta, io, net}`。
> 判据：**行为级**对照；**无注释声明的差异才算问题**。已知豁免：arc→libgdx 结构替换（Table/Cell 便捷方法、FrameBuffer/Shader/Cursor 设施、Json/反射层）、Player 非 Unit、无 Mech 系统、@Remote 注释态、自研网络（RemotePlayer/快照模型）、存档 SaveMeta 白名单、自研 Renderer（FloorRenderer/OverlayRenderer/IndexedRenderer 合并进 `core/Renderer.drawTiles`）。
> 跨包去重：`core/Logic` 由 A1 负责、`world/`+`content/` 由 A2 负责；本报告对 A1/A2 已登记项只做 cross-ref，不重复计数。
> **文件级结论**：ui 17+25+13+4 对、input 6 对、graphics 11 对（缺 Drawf/FloorRenderer/IndexedRenderer/MultiPacker/OverlayRenderer —— Drawf 已挪到 `core/Drawf`、MultiPacker 是 arc 打包设施、其余三者合并进自研 Renderer，均已在类注释声明）、core 12 对、world/meta 全对应、io 7 对、net 14 对 —— **无未声明缺文件**。

## 摘要：缺失 8 / 未声明偏离 2 / 断裂 3（另有 20+ 项"偏离(已声明)"，见文末清单）

最严重的三条：
1. **解锁提示整链断裂**：`GlobalData` 会 fire `UnlockEvent`，但原版的唯一监听方 `Control`（→ `HudFragment.showUnlock`）在本项目**不存在**，`HudFragment` 也完全没有 `showUnlock` —— 战役/区域里解锁新单位/方块时**不弹任何提示**（玩家不知道解锁了什么）。
2. **地图截图导出（P 键）整链缺失**：`Binding.screenshot`(P)、`Renderer.takeMapScreenshot()`、`screenshotDirectory` 全部没有，按 P 无反应；这是原版唯一的"导出整张地图 PNG"入口（战役/编辑器分享地图用）。
3. **建造队列暂停（E 键）缺失**：`Binding.pause_building`、`Player.isBuilding`/`buildWasAutoPaused`、`buildautopause` 设置项在本项目**都没有**（`isBuilding` 是单位侧的建造状态，不是玩家侧的队列开关）—— 玩家无法暂停/恢复自己的建造队列，`buildautopause` 设置项成了纯摆设（无消费者）。

## 明细表（按优先级排序）

### 高优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| ui/fragments/HudFragment.java:440 `showUnlock` + core/Control.java:129 `Events.on(UnlockEvent...)` | 解锁新内容时弹"$unlocked"提示（图标 + 名称，`scheduleToast` 排队） | 断裂 | ui/fragments/HudFragment.java（无 showUnlock）/ core/Control.java（无 UnlockEvent 监听） | `GlobalData.java:106` 会 `Events.fire(new UnlockEvent(content))`，但全工程 grep `showUnlock` = 0；事件被 fire 后**无任何消费者** | 高 |
| core/Control.java:468-470 + core/Renderer.java:409-451 | `Binding.screenshot`(P) → `renderer.takeMapScreenshot()`：把整张地图渲染进 FrameBuffer 并存成 PNG + `$screenshot` 提示 | 缺失 | core/Renderer.java（无 takeMapScreenshot）/ input/DesktopInput.java（无 P 处理） | 全工程 grep `takeMapScreenshot`/`screenshotDirectory` = 0；`Binding.screenshot` 仅定义处出现，无任何 keyTap 消费 | 高 |
| input/DesktopInput.java:346 `Binding.pause_building`(E) + entities/type/Player.java:52 `buildWasAutoPaused` + BuildBlock.java:148 | 暂停/恢复玩家建造队列（`player.isBuilding = !player.isBuilding`），队列清空时自动暂停并记 `buildWasAutoPaused` | 缺失 | input/DesktopInput.java / entities/type/Player.java | 项目 `Player` 无 `isBuilding`/`buildWasAutoPaused` 字段；`Binding.pause_building` 仅定义处出现；`SettingsMenuDialog` 的 `buildautopause` 项无消费者（断裂） | 高 |
| ui/fragments/PlacementFragment.java:93 `Binding.pick`(MOUSE_MIDDLE) | 中键"吸管"：把光标下的方块设为当前待建造方块 | 缺失 | input/DesktopInput.java:293（中键只用于命令模式追加队列） | 非命令模式下中键 `touchDown` 直接 `return false`；`Binding.pick` 仅定义处出现，无消费 | 中高 |
| input/DesktopInput.java:225-227 + input/InputHandler.java:86 `rotateBlock` | R 键 + 滚轮旋转**已放置**的可旋转方块（`Call.rotateBlock`） | 缺失 | input/DesktopInput.java:237-240（R 改成旋转待放置的 buildRotation） | `Binding.rotateplaced` 仅定义处出现；`InputHandler.rotateBlock` 不存在；R 的语义被改成与滚轮等价 | 中高 |

### 中优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| world/Block.java:520 `drawConfigure` + ItemBridge.java:128 / MassDriver.java:186 / PowerNode.java:214 覆写 + graphics/OverlayRenderer.java:84-87 | 配置面板打开时调 `tile.block().drawConfigure(tile)`：画配置连线/范围（桥连接线、驱动器目标线、电力节点连线） | 缺失 | world/Block.java（无 drawConfigure）/ core/Renderer.java / input/InputHandler.java | 全工程 grep `drawConfigure` 仅 1 处注释（MassDriver.java:125），无方法定义、无调用点；`configTile` 只驱动 BlockConfigFragment 面板，不触发方块自身叠加绘制 | 中 |
| input/DesktopInput.java:44-74 `buildUI` | 桌面端建造提示条：`resumebuilding`/`pausebuilding`、`cancelbuilding`、`selectschematic` 三行提示（受设置 `hints` 控制） | 缺失 | input/InputHandler.java:106-107（空 stub）/ input/DesktopInput.java（未覆写） | 项目 `InputHandler.buildUI` 是空实现，DesktopInput 不覆写 → 建造相关键位提示从不显示 | 中 |
| graphics/OverlayRenderer.java:137-155 | 投递物品时的光标预览：物品图标 + accent 圆环 + 目标方块绿色方框（能否接收） | 断裂 | input/InputHandler.java:173-175（`isDroppingItem()` 有定义） | `isDroppingItem()` 除定义外**无任何消费者**；grep `droppingItem` 只出现在赋值/清空，没有绘制 | 中 |
| input/Binding.java:21 + DesktopInput.java:161/188/408 + InputHandler.java:827 | `Binding.diagonal_placement`(Ctrl)：按住时连线走"直线/对角约束"，并让滚轮强制缩放 | 缺失 | input/DesktopInput.java（无 Ctrl 处理）/ input/MobileInput.java:192-194（移动端有 swapdiagonal 按钮） | 桌面端 `Binding.diagonal_placement` 仅定义处出现；`Placement` 的 `normalizeLine` 存在但桌面输入层从不传 diagonal | 中 |
| net/Administration.java:63-134,411-490 | 子网封禁 `subnetBans`/`addSubnetBan`/`isSubnetBanned`、`getStrict`/`allowsCustomClients`、`Config` 枚举（服务端配置项 strict/antiSpam…） | 缺失 | net/Administration.java | 类注释只声明"聊天/动作过滤未实现"，未声明 subnetBan/strict/Config 缺失；原版 `NetServer` 会调 `isSubnetBanned` | 中低 |
| input/InputHandler.java:731 `canShoot` | `block == null && !scene.hasMouse() && !onConfigurable() && !isDroppingItem()` 时才允许开火 | 偏离(未声明) | input/InputHandler.java:1328 `updateShooting` | 项目只用 `unit.getType().canShoot(unit)`（UnitType 侧），没有输入层的 UI/配置/投递守卫；鼠标悬停 UI 或配置面板打开时仍可能开火（影响较小） | 低中 |

### 低优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| ui/dialogs/ContentInfoDialog.java:15 `show(UnlockableContent)` + ui/ContentDisplay.java:65/99/134/182 `displayItem/displayLiquid/displayUnit/displayMech` | 物品/液体/单位/机甲的详情面板 | 缺失(已声明) | ui/ContentDisplay.java（只有 displayBlock）/ ui/dialogs/DatabaseDialog.java:180-190 | `UnlockableContent.java:69` 明确 TODO"各内容类型自己的详情面板尚未移植"；`DatabaseDialog.openInfo` 保留"非 Block 就 return"守卫 → 数据库里点物品/单位**没有反应** | 低 |
| world/Block.java:481 `getCursor` + DesktopInput.java:207-228 光标切换 | 光标随方块/状态切换（hand/drill/unload） | 缺失(已声明) | core/UI.java:83,143 | UI 类注释声明"无 drillCursor/unloadCursor（libgdx 无 Core.graphics.newCursor 式设施）" | 低 |
| ui/fragments/PlacementFragment.java:169-179 + blockSelect 方向键 | `category_prev/next`(,/.) 切分类、`block_select_left/right/up/down`(方向键) 选方块 | 缺失(已声明) | ui/fragments/PlacementFragment.java:894-935 | 注释 TODO"方向键部分见 updateBlockSelect() 的 TODO"；`Binding.category_*`/`block_select_方向` 全 0 引用（数字键已实现） | 低 |
| graphics/Shaders.java:12-20 | `shadow`/`blockbuild`/`menu`/`water`/`tar` 着色器 | 缺失(已声明) | graphics/Shaders.java:14-15 | 类注释声明"其中 light/build/shield 已补；其余待补"；`CacheLayer` 也声明 water/tar 未移植（动画水面缺失，地板按区域直绘） | 低 |
| core/Renderer.java:176-203 `setupBloom`/`toggleBloom`/`init`/`resume` | 泛光后处理管线 | 缺失(已声明) | ui/dialogs/SettingsMenuDialog.java:44-45,288-292 | 设置页注释 TODO"本项目 core/Renderer 没有 bloom 后处理管线 → bloom 复选框只存值（画面不变）"，且启动时强制 `bloom=false` | 低 |
| core/ContentLoader.java:134-147 `loadColors` | 读 `sprites/block_colors.png` 给方块着色（小地图/预览颜色） | 缺失(已声明) | core/ContentLoader.java:37 | 类注释声明未移植；本项目改用 `world/Block.java:202-220` 从图标中心像素取色（等价） | 低 |
| io/MapIO.java:52 `writeMap` / :68,138 `generatePreview` | 写地图文件 / 生成地图缩略图 | 缺失(已声明) | io/MapIO.java / editor/MapEditorDialog.java:100-101,1043+ / maps/Maps.java:40 | MapEditorDialog 注释声明"saveMap 自实现"；Maps/MapPreviewLoader/MapsDialog 均声明"没有地图缩略图生成"→ 地图列表无预览图 | 低 |
| ui/Cicon.java:15-16 `all`/`scaled`；graphics/Pal.java `plastanium` | Cicon 数组 / 颜色常量 | 缺失 | ui/Cicon.java / graphics/Pal.java | `Cicon.all` 原仅用于 `UnlockableContent` 的 cicons 数组（本项目图标机制不同）；`Pal.plastanium` 原仅用于 PowerNode 绝缘子（未移植） | 低 |
| ui/dialogs/SettingsMenuDialog.java:211-222,244-250,306 `autotarget`/`keyboard`/`touchscreen`/`playerlimit`/`publichost`/`landscape` | 移动端/主机端专属设置项 | 缺失(已声明) | ui/dialogs/SettingsMenuDialog.java:49,160 | 注释 TODO"mobile 专属项（autotarget/keyboard + control.setInput）"；`autotarget` 的原消费点 `Player.java:722` 也未接 | 低 |
| ui/fragments/PlayerListFragment.java:141-145 votekick | 玩家投票踢人（`/votekick` 命令 + 确认框） | 缺失(已声明) | ui/fragments/PlayerListFragment.java:46 | 注释 TODO"votekick …服务端没有该命令处理" | 低 |
| core/UI.java:385 `showExceptions(String, String...)` | 多段错误列表弹窗 | 缺失(无影响) | core/UI.java:87 | 原版该方法自身**无调用点**（死代码）→ 无行为影响 | 低 |
| graphics/BlockRenderer.java:286 `drawLayer2` + RepairPoint.java:88 | 第二叠层绘制（RepairPoint 画到目标的激光） | 缺失(已声明) | world/blocks/units/RepairPoint.java:22 | 注释声明"未移植：激光连线绘制（原版 drawLayer2 …）"；仅 RepairPoint 一处覆写 | 低 |
| graphics/MinimapRenderer.java / core/World.java:308,337,344 `isDarkened` | 世界暗化层 | 缺失 | world/Tile.java / core/World.java | 与 A2 高优先级项同因，cross-ref A2（`isDarkened`/`addDarkness` 全无） | 低 |

### 断裂汇总（方法/字段存在但调用链没接上）

| 项 | 本项目位置 | 证据 |
| --- | --- | --- |
| `UnlockEvent` 被 fire 但无监听方（`showUnlock` 缺失） | game/GlobalData.java:106 → 无消费者 | 见高优先级表第 1 条 |
| `buildautopause` 设置项有值无消费 | ui/dialogs/SettingsMenuDialog.java:187 | 原版消费点是 DesktopInput.pollInput（建造队列自动暂停），本项目无 `isBuilding` 字段 |
| `isDroppingItem()` 无绘制消费者 | input/InputHandler.java:173 | 原版 OverlayRenderer 画投递预览，本项目无 |
| `ItemSelection`/`LightBlock` 的"配置隐藏"注释指向 `hideConfig` | world/blocks/ItemSelection.java:41 / LightBlock.java:97 | 原版 `frag.config.hideConfig()`，本项目用 `configTile = null` 等价（已声明，不计问题） |
| `Zone.updateWave` 无调用点 | type/Zone.java:140 | cross-ref A1/A2 |

## 偏离(已声明)清单 —— 不计为问题

- **input/Binding.java 整表**：键位表与原版逐条一致；但 `KeyBinds` 只保留类型定义（无"Binding→当前键码"查询表、无键位设置界面/持久化），消费方一律取 `defaultValue`。`ScriptConsoleFragment`/`PlacementFragment` 均有注释声明。
- **input/DesktopInput.java:118-127**：命令模式键从原版 Shift 改 Q（注释声明"phoenix 的 Shift 已被玩家加速占用"）；`Binding.clear_building`(Q) 因此不再清空建造队列（改由右键/Esc 的 `clearBuild()` 承担）。
- **input/DesktopInput.java:39-42,512-525 / MobileInput**：蓝图"框选创建 + Z/X 翻转 + 另存"降级为 PlacementFragment 的"固定半径复制选区 + 内嵌列表"（注释声明"原版是拖拽框选 + 独立蓝图对话框，这里简化为固定范围复制"）；`schematic_select/flip_x/flip_y` 在桌面端无消费（移动端 MobileInput 有 `flipRequests`）。
- **world/blocks/distribution/ItemBridge.java:31,73-88**：桥改为"放置时自动连接上一次放置的同类桥"（原版需手动点选配置），注释声明"手动点选配置待 HUD 任务补"；`configured()` 仍在。
- **input/InputHandler.java:106-110 / MobileInput.java:174,220**：`buildUI`/`buildPlacementUI` 由 HudFragment 反向调用，桌面端空实现（结构差异）。
- **ui/dialogs/ModsDialog.java:26-56**：模组元数据/启用停用/依赖检查/创意工坊/GitHub 导入整段降级（逐条 TODO 声明，缺 `Core.net.httpGet`/`Jar` 等）。
- **ui/dialogs/SchematicsDialog.java:57,400 / SettingsMenuDialog / LoadDialog**：`platform.shareFile`/`publish`/`openWorkshop`/`viewListing` 降级（Steam/iOS 分支，B 类）。
- **ui/dialogs/DeployDialog.java:40-80**：区域树已接线；缺 `getZoneSlot`/背景贴图/`ContentReloadEvent`/`platform.updateRPC`（逐条 B 类声明）。
- **ui/dialogs/DatabaseDialog.java:36-46,72-76,204-217**：`isHidden()` 语义差异（本项目 Block.isHidden = 不绘制，故改走 `buildVisibility.visible()`）、`Icon.lockOpen`→`Icon.locked`、无 HandCursor/Tooltip/悬停高亮、`Fonts.getUnicode` 缺失（逐条声明）。
- **core/ContentLoader.java:37**：mod 内容、`loadColors()`、`logContent`/`dispose` 未移植（声明）。
- **core/Renderer.java:41-43,226+ / graphics/BlockRenderer.java:41-44**：FloorRenderer/OverlayRenderer/IndexedRenderer 不单独移植，合并进自研 `Renderer.drawTiles`（方块请求队列/分缓存层绘制/`drawDestroyed`/`drawCracks`/`drawTeam` 都在 Renderer 内联实现，注释声明）。
- **graphics/CacheLayer.java:9-16,52-63**：`begin/end/beginShader/endShader` 空壳（无 shieldBuffer/FloorRenderer/water-tar shader，声明）。
- **core/UI.java:83,110,143**：无 `drillCursor`/`unloadCursor`/`packer`（libgdx 无运行时光标与图集打包设施，声明）。
- **core/Platform.java**：`updateRPC`/`showFileChooser` 等为无实例默认实现（B 缺依赖，多处声明）。
- **net/ 整体**：自研简化网络（RemotePlayer/快照模型、`Net.reset()`/`Net.showError()` 缺失、`Administration` 无聊天/动作过滤、`BeControl` 全降级），类注释逐条声明。
- **ui/Minimap.java / ui/Bar.java / ui/GridImage / ui/ReqImage / ui/Styles / ui/Label / ui/BorderImage / ui/layout/* / ui/dialogs 大多数**：方法级与行为级对齐（差异均为 arc→libgdx 的 Table/Cell/Image 便捷方法缺失，已在本项目就地等价实现）。
- **ui/fragments/{ChatFragment,MinimapFragment,PlayerListFragment,BlockInventoryFragment,LoadingFragment,OverlayFragment,FadeInFragment,ScriptConsoleFragment}**：功能对齐（聊天发送/历史、小地图 pan/zoom/toggle、玩家列表 ban/kick/admin/trace、库存面板、加载遮罩、脚本控制台均接线）。

## 备注 / 未完全核实的项

- `ui/dialogs/{JoinDialog,HostDialog,MapsDialog,LoadDialog,SaveDialog,CustomGameDialog,MapPlayDialog,ZoneInfoDialog,ColorPicker,PaletteDialog,LoadoutDialog,TraceDialog,AboutDialog,AdminsDialog,BansDialog,DiscordDialog,FileChooser,LanguageDialog,GameOverDialog,FloatingDialog,TechTreeDialog}` 未逐个方法级展开；抽查未见未声明的高优先级缺口，但服务器列表/文件选择/科技树等仍有细节未逐条核对。
- `net/Packets.java`（767 行，自研）、`net/PacketSerializer.java`、`net/PhoenixNetProvider.java`、`net/TypeIO.java` 属自研协议栈，未做逐包与原版 `@Remote` 定义对照（原版依赖 arc 反射 + @Remote，本项目按已知豁免不比对）。
- `io/SaveIO.java`（820 行，自研）、`io/SaveVersion.java`、`io/SaveMeta.java` 的字段级保真未展开（属存档自研豁免；`SaveMeta` 白名单加字段需同步三处，见既有记忆）。
- graphics 的 `BlockRenderer`/`LightRenderer` 与自研 Renderer 的帧序（placement/overlay/turret/power/lights 分层）未逐帧比对原版 `blocks.drawBlocks(Layer.*)` 的完整顺序。
