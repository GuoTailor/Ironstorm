# F13 输入/UX 补齐 — 修复报告

批次：F13（9 个独立修复点，全部照抄 Mindustry Build 126.2 原版）。
验证方式：主会话编译 + 运行（本批不跑 gradle）。

## 1. 解锁提示（断裂 → 已接）

- 原版：`core/Control.java:129` `Events.on(UnlockEvent.class, e -> ui.hudfrag.showUnlock(e.content))` + `ui/fragments/HudFragment.java:440-526`（showUnlock）+ `:396-406`（scheduleToast）。
- 落点：
  - `core/Control.java` 构造器新增 `EventType.UnlockEvent` 监听 → `Gdx.app.postRunnable` 延后一帧调 `Vars.hud.showUnlock(e.content)`（本项目 Events.fire 同步，unlockContent 可能在 Logic.update 中途触发，动 stage 需延后，与 Trigger.newGame 监听器同口径）。
  - `ui/fragments/HudFragment.java` 补 `showUnlock(UnlockableContent)` + `scheduleToast(Runnable)` + 字段 `lastUnlockTable/lastUnlockLayout/lastToast`（本项目原本**没有**任何 toast 设施，按原版补最小设施）。
- 照抄要点：菜单态/教程态不弹；`Sounds.message`；第一条弹「内容图标(xlarge) + $unlocked」toast（顶部滑入 1s → 停 2.5s → 滑出 1s 自删）；连续解锁把图标堆进同一 toast（3 列、8 格容量，满了显示 `Icon.add`）；scheduleToast 的 3.5s 排队公式照抄。
- libgdx 适配：arc 的 `table.update(...)` → `MenuFragment.Act`（菜单态自删）；`container.actions(a,b,c)` → `Actions.parallel(动作, sequence(delay, run))`；`Table(Tex.button)` → `new Table()+setBackground`；margin → pad(Scl)。图标缺区域时跳过（`icon()` 可能返回 null）。

## 2. E 键暂停建造队列

- 原版：`input/DesktopInput.java:346-349`（E 切 `player.isBuilding`、清 `buildWasAutoPaused`）+ `:272-276`（buildautopause 自动暂停）+ `entities/type/Player.java:51-52`（字段）+ `:459-467`（updateMechanics 的 `if(isBuilding) updateBuilding()` / `buildRequest()==null || !isBuilding` 才采矿）。
- 落点：
  - `entities/type/Player.java` 补字段 `isBuilding = true` / `buildWasAutoPaused = false`（⚠ 与 `isBuilding()` 同名不同义，原版即如此，注释已写明）。
  - `entities/type/BaseUnit.java` update()：队列推进按 `buildEnabled = !(isPlayer && Vars.player != null && !Vars.player.isBuilding)` 门控；采矿条件改 `(buildRequest() == null || !buildEnabled)`（暂停时队列里还有活也继续采矿，照原版）。建造机（非 isPlayer）不受影响。
  - `input/DesktopInput.java` update()：buildautopause 分支（读 `Core.settings.getBool("buildautopause", false)`，设置项 SettingsMenuDialog 已有检选框，直接接上消费点）+ E 键切换（带 TextField 焦点守卫）。
- 无资源/键位缺失（`Binding.pause_building`(E) 既有）。

## 3. P 键地图截图

- 原版：`core/Control.java:468-470`（键位消费）+ `core/Renderer.java:409-451`（takeMapScreenshot）。
- 落点：
  - `core/Renderer.java` 新增 `takeMapScreenshot()`：整图按「1 世界单位 = 1 像素」渲进 FrameBuffer → `ScreenUtils.getFrameBufferPixels(flipY=true)` → alpha 填满 → `PixmapIO.writePNG` → `screenshots/screenshot-<millis>.png` → `$screenshot` 提示（`showInfoFade`）；≥65MB 时 `$screenshot.invalid`（UI.showInfo）。
  - `input/DesktopInput.java` update()：P 键消费（守卫照原版 `!mobile` + 无文本焦点，另加 `!hasDialog()`）。
- 关键适配：
  - **FBO 不能嵌套**：外层 `FboUtil.save()/restore()`（S10 设施）保住绑定+视口；draw() 内部的灯光/护盾 FBO 自带 save/restore，不冲突。像素化（Pixelator）不在截图内（它包在 ClientLauncher 层，原版截图同样不经过 pixelator 的外层语义）。
  - **相机口径**：原版 arc 相机 width/height 两轴独立 → libgdx Ortho 相机临时改 `viewportWidth/viewportHeight` + `camerascale=1`（applyScale 得 zoom=1）；相机中心 = (w/2, h/2)（本项目 worldx() 是左下角，原版 +tilesize/2 是它的中心口径，照抄会偏半格）。
  - **数据目录安全**：只 `Gdx.files.local("screenshots")` 新建子目录，不碰 assets 根既有文件。

## 4. 中键吸取方块

- 原版：`ui/fragments/PlacementFragment.java:93-109`（gridUpdate 的 `Binding.pick`(MOUSE_MIDDLE) 分支：光标下方块 / 建造队列占位方块 → `input.block`，切分类，须 `isVisible() && unlocked`）。
- 落点：
  - `ui/fragments/PlacementFragment.java` 新增 `pickBlock(float worldX, float worldY)`（含队列占位方块的 bounds 命中判定、`unlockedCur()` 解锁判定、切 `currentCategory` + rebuildGrid/rebuildCategories）；HudFragment 补 `placement()` 访问器。
  - `input/DesktopInput.java` touchDown：非命令模式 MIDDLE 分支（原先直接落到 `return false`）调 pickBlock。
- 小偏离：原版是 gridUpdate 每帧 keyDown 轮询；本项目输入层事件式 → 改为**中键按下时**调用一次（语义等价）。

## 5. R 键旋转已放置方块

- 原版：`input/DesktopInput.java:225-227`（`keyDown(rotateplaced)` + 滚轮方向 → `Call.rotateBlock`）+ `input/InputHandler.java:86-98`（Call.rotateBlock 本体：`tile.rotation(mod(rotation+sign,4))` + `updateProximity()` + `noSleep()`）。
- **读原版结论**：桌面端"待放置朝向"只有滚轮（update :188），R **不是**朝向键；R = rotateplaced（按住 + 滚轮转已放置方块），二者经 `!keyDown(rotateplaced)`（zoom 条件 :161）与 `!isPlacing()`（:225）共存，无冲突。
- 落点（`input/DesktopInput.java`）：
  - 滚轮分支三分：`!isPlacing() && keyDown(R)` → `rotatePlacedAt(方向)`（不缩放）；放置可旋转方块且**未按 Ctrl** → 转待放置朝向（补原版 :188 的 `!keyDown(diagonal_placement)` 守卫）；否则缩放。
  - `rotatePlacedAt()`：`Call.rotateBlock` 等价本地直调（Remote 注释态）；守卫 `interactable && !isPlacing && block().rotate`（+ link() 解析多格、方向取 `Mathf.mod(rotation + sign(±1), 4)`）；`noSleep()` 省略（本项目 TileEntity 无休眠机制）。
  - **删除**原"R 键切换待放置朝向"的照抄偏差（原版无此绑定）。
- 已知限制：联机客户端旋转只在本地表现（原版走 @Remote 同步；本项目无对应包类型，与既有 net 层颗粒度一致）。

## 6. drawConfigure 配置叠加

- 原版：`world/Block.java:520-525`（默认 accent 描边方框）+ `ItemBridge.java:128-149` / `MassDriver.java:186-207` / `PowerNode.java:214-240`（三个覆写）+ `graphics/OverlayRenderer.java:84-87`（渲染调用）。
- 落点：
  - `world/Block.java` 补 `drawConfigure(Tile)` 默认实现（centerX/centerY 口径）。
  - `ItemBridge`：本体方框 + 四向 range 内可连桥格描边（已连 `Pal.place`、可连未连 `Pal.breakInvalid`+脉动；d4 全向遍历，旋向差异无影响）。
  - `MassDriver`：本体脉动圆 + waitingShooters 圆/箭头 + 对端圆/箭头 + range 虚线圆。
  - `PowerNode`：本体呼吸圆 + laserRange 圈 + 范围内已连节点 `Drawf.square(Pal.place)`（linked 判据用 `NodeEntity.links`，等价替换原版 `linkValid/link`）。
  - `core/Renderer.java` draw()：`configTile != null` 时、`input.drawTop()` 之前调用（原版顺序）。

## 7. buildUI 建造提示条

- 原版：`input/DesktopInput.java:44-74` buildUI 第一张 fill 表（三行提示，受 `hints` 设置控制，随 `player.isBuilding()` 淡入淡出）。
- 落点：
  - `input/InputHandler.java` 的空 stub 保持基类签名；`DesktopInput` 覆写 `buildUI(Table)`：黑底三行（`pausebuilding/resumebuilding` 动态首行、`cancelbuilding`、`selectschematic`），`MenuFragment.Act` 每帧刷 alpha/显隐/文本，低透明度禁触摸。
  - `ui/fragments/HudFragment.java` build()：桌面端新建 bottom 对齐的 fill-parent 容器传入 `input().buildUI(...)`（原版往 uiGroup 塞 fill 表的等价分工；移动端 MobileInput.buildUI 路径不变）。
- **bundle 补键**（原先缺失，会显示 `???key???`）：`assets/i18n/bundle.properties` 与 `bundle_zh_CN.properties` 在 `screenshot.invalid` 后各补 4 键：`cancelbuilding / selectschematic / pausebuilding / resumebuilding`（文案逐字抄原版，含 `[accent]`/`[scarlet]` 标记）。
- 降级：原版 visible 的 `selectRequests.isEmpty()`（桌面框选蓝图期间隐藏）本项目桌面端无该状态 → 省略；第二张 fill 表（蓝图翻转提示+保存按钮）未移植（任务范围外）。

## 8. 投递物品光标预览

- 原版：`graphics/OverlayRenderer.java:137-155`（isDroppingItem：物品图标 + accent 脉动圆环 + 可收目标方块双层描边）。
- 落点：`core/Renderer.java` 新增 `drawDropItemPreview()`，挂在 `drawOverlay()` 末尾（玩家存活分支内，与原版"dead players don't draw"一致）；物品图标 `player.item.icon(Cicon.medium)`；目标判定 `interactable && acceptStack>0`；描边中心用 centerX/centerY（坐标口径修正）。

## 9. Ctrl 对角放置 + canShoot 守卫

- 原版：`input/InputHandler.java:825-841` iterateLine（`diagonal = keyDown(Binding.diagonal_placement)` → `pathfindLine`（Bresenham 阶梯）否则 `normalizeLine`；PowerNode 反转）+ `input/DesktopInput.java:161/188/408` + `input/InputHandler.java:731-733` canShoot。
- 落点：
  - `input/InputHandler.java` `buildPath()` 重写为原版语义：Ctrl 按住 → `Placement.pathfindLine`（传送带开 conveyorpathfinding 时 A*，否则阶梯直线），否则 `normalizeLine`；PowerNode 反转；定长缓冲超长截断（阶梯线最坏 dx+dy ≈ 2×maxLineLength）。⚠ 传送带不带 Ctrl 时也走 normalizeLine 纯直线（原版同款，替换原先"传送带永远 pathfindLine"的偏差）；拆除拖拽同样受益（Ctrl = 阶梯拆）。
  - `DesktopInput` 滚轮分支补 `!keyDown(diagonal_placement)` 才转待放置朝向（原版 :188）。
  - `InputHandler` 补 `canShoot()`（非建造/拆除 && `!sceneHasMouse()` && `!onConfigurable()` && `!isDroppingItem()`）+ `sceneHasMouse()`（arc `Core.scene.hasMouse()` 等价替代：libgdx `stage.hit()` 对无监听器全屏容器表也命中自身 → 改按命中链上"任一 actor 挂有输入监听器"判定）+ `onConfigurable()` 挂钩（桌面端恒 false，照原版）。
  - `updateShooting()`（InputHandler:~1328 补守卫）：`isShooting && !canShoot()` → 清 isShooting，不再开火（转向/开火走 else 分支结构，未射击时的转身逻辑保留）。

## 改动文件清单

| 文件 | 内容 |
| --- | --- |
| `core/src/com/phoenix/game/entities/type/Player.java` | +isBuilding/buildWasAutoPaused 字段 |
| `core/src/com/phoenix/game/entities/type/BaseUnit.java` | 队列推进/采矿按玩家开关门控 |
| `core/src/com/phoenix/game/input/InputHandler.java` | canShoot/sceneHasMouse/onConfigurable、updateShooting 守卫、buildPath 对角化 |
| `core/src/com/phoenix/game/input/DesktopInput.java` | E 键/buildautopause/P 键截图/R 旋转已放置/中键吸取/buildUI 提示条/bindingKey/rotatePlacedAt |
| `core/src/com/phoenix/game/world/Block.java` | +drawConfigure 默认实现 |
| `core/src/com/phoenix/game/world/blocks/distribution/ItemBridge.java` | +drawConfigure |
| `core/src/com/phoenix/game/world/blocks/distribution/MassDriver.java` | +drawConfigure |
| `core/src/com/phoenix/game/world/blocks/power/PowerNode.java` | +drawConfigure |
| `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java` | +pickBlock（中键吸取） |
| `core/src/com/phoenix/game/ui/fragments/HudFragment.java` | +showUnlock/scheduleToast/字段、+placement()、桌面提示条挂载 |
| `core/src/com/phoenix/game/core/Control.java` | +UnlockEvent 监听 |
| `core/src/com/phoenix/game/core/Renderer.java` | +takeMapScreenshot、drawConfigure 调用点、drawDropItemPreview |
| `assets/i18n/bundle.properties` | +cancelbuilding/selectschematic/pausebuilding/resumebuilding |
| `assets/i18n/bundle_zh_CN.properties` | 同上（中文文案） |

## 键位/资源核对

- 所有键位消费均使用 `input/Binding.java` 既有常量对应的默认键码：`pause_building`(E)、`screenshot`(P)、`rotateplaced`(R)、`pick`(MOUSE_MIDDLE)、`diagonal_placement`(Ctrl)、`clear_building`(Q)、`schematic_select`(F)——与原版逐条一致，无缺失。
- `buildautopause` / `hints` 设置项 SettingsMenuDialog 已有（checkPref），本批只接消费点。
- bundle 补键 4×2（见第 7 条）；`unlocked`/`screenshot`/`screenshot.invalid` 原本就有。
- 主会话需重新编译 `:core`（无新增资源，无需重打图集；无需重编 annotations）。
