# TODO 全量分类（共 254 条：①11 ②26 ③217 ④0）

> 统计口径：`core/src/**` 内大小写不敏感的 `todo`（大写 TODO 253 条 + 小写 todo 1 条：`net/NetworkIO.java:136`）。
> 定性方法：逐条读上下文 + grep 现状 + 与原版（`D:\IdeaProjects\Mindustry-126.2`）及 arc（`D:\IdeaProjects\Arc`）同款注释比对。
> 日期：2026-09-29。只读分析，未改任何 .java。

---

## ①过时（可直接修）——11 条

TODO 描述的事实已经不成立（功能已做、依赖已补、或历史问题已修复）。修法：改写或删除注释。

### content/Mechs.java（7 条）
- [Mechs.java:29] javadoc"只是本类的 TODO 未同步更新"（弹药说未移植）→ F12b 已把 6 种弹药全部接线（`Bullets.standardMechSmall/lightning/healBullet/missileSwarm/missileJavelin/standardGlaive` 均已在 Bullets 注册并赋给 weapon.bullet），javadoc 描述的历史问题已解决。
- [Mechs.java:89] `//F12b：原版弹药（此前 TODO 说"未移植"是过时信息…）` → 自证过时的修复记录，下一行就是 `bullet = Bullets.standardMechSmall`；注释可简化。
- [Mechs.java:126] 同上（Bullets.lightning）→ 下一行已接线。
- [Mechs.java:178] 同上（Bullets.healBullet）→ 下一行已接线。
- [Mechs.java:236] 同上（Bullets.missileSwarm）→ 下一行已接线。
- [Mechs.java:337] 同上（Bullets.missileJavelin）→ 下一行已接线。
- [Mechs.java:458] 同上（Bullets.standardGlaive）→ 下一行已接线。

### core/ClientLauncher.java 之外（4 条）
- [game/MusicControl.java:56] "UI 系统尚未移植，发射场/编辑器音乐不播" → **UI 已移植**：`Vars.ui.deploy` 存在（core/UI.java:192 `public DeployDialog deploy`）、`Vars.ui.editor` 存在（UI.java:199），可直接接 `isShown()` 等价判定（FloatingDialog 可见性）。grep `Vars.ui.deploy|public DeployDialog` 证。
- [math/geom/Bresenham2.java:9] javadoc"Placement 里也有同名 TODO（lineNoDiagonal）" → lineNoDiagonal 已在 input/Placement.java:70 内联实现（类注释自述"已在本文件内联实现"），该 TODO 引用已过时。
- [world/Block.java:1299] useContent 留空的理由"GlobalData 无 isUnlocked/unlockContent（grep 已证）" → **S4a 批已落地两个方法**（game/GlobalData.java:84/93），全工程 20+ 调用点（TechTreeDialog/ZoneInfoDialog/Control 等）；阻塞已消除，useContent 现在可以按原版接 `data.unlockContent(content)`（战役"生产内容即解锁"仍缺失，见 ②）。
- [io/SaveVersion.java:54] javadoc"version/build/mods/stats/rules 全部 TODO" → 同文件 :79 自证"原注释已过时（grep 已证伪）"；stats/rules 已用 JsonIO 读写（:172/:191）、build 已写 126（:80）。仅 mods 仍恒写 `"[]"`（:174，属 ③ mod 降级）。

---

## ②缺失功能（转功能批次）——26 条

功能确实不存在且（大部分）玩家可感。按功能组归并，便于排批次。

### 组 A：地图/存档缩略图链（8 条，一个功能）
原版 `map.texture` 由 `Maps` 的预览生成队列（`Core.app.post` + AsyncExecutor 生成缩略图写盘）填充；本项目 `Map.texture` 恒 null，UI 全部退化成无图占位。原版参照：`mindustry.maps.Maps#queueNewPreview`、`MapPreviewLoader`（加载侧本项目已有死文件 `io/SavePreviewLoader` 未接线；生成侧已有 `game/Saves` 用的 `io/SavePreview` 只覆盖"存档缩略图"，不覆盖"地图缩略图"）。
- [editor/MapLoadDialog.java:30] `Map.texture` 恒为 null → 地图载入对话框无预览图。
- [maps/Maps.java:169] 预览生成队列未移植 → 地图缩略图不生成。
- [maps/Maps.java:174] `System.out.println("TODO 地图缩略图生成未移植，跳过")` → 同上（运行期可见的桩）。
- [maps/MapPreviewLoader.java:24] 类注释"一处降级（TODO）" → 缩略图加载器未接线。
- [maps/MapPreviewLoader.java:49] `Maps.queueNewPreview` 是降级桩 → 同组根因。
- [ui/dialogs/MapsDialog.java:62] "缩略图（整条地图预览链未移植）" → 自定义游戏地图列表无预览图。
- [ui/dialogs/CustomGameDialog.java:38] 同上（`map.texture` 恒 null）→ 同一表现。
- [ui/dialogs/MapPlayDialog.java:41] 同上 → 地图详情无预览图。

### 组 B：键位系统（2 条，一个功能）
设置 → 控件 是空壳（只有返回按钮）；键位查询/重绑定/持久化整体缺失。原版参照：`arc.scene.ui.KeybindDialog` + `arc.KeyBinds.setDefaults/save/load`。
- [ui/dialogs/ControlsDialog.java:10] KeybindDialog 无替代层 → 玩家点"控件"进去什么都没有。
- [input/KeyBinds.java:10] setDefaults/save/load 未移植 → 键位不可改、不落盘（当前各处直接读 `Binding.defaultValue`）。

### 组 C：教程 UI 指引（2 条，一个功能）
- [game/Tutorial.java:353] `outline(String)` 整段空实现 → 教程 10+ 处步骤（category-production/mechanical-drill/conveyor/duo/pause/breakmode/waves…）都不画高亮框与指向箭头，新玩家看不到"该点哪里"。原版参照：`TutorialStage.outline`（`Core.scene.findVisible` + `Lines.rect` + 呼吸箭头）。
- [game/Tutorial.java:347] `toggled(String)` 恒 false → 仅被 outline 内部消费（Tutorial.java:358），随组 B 一并补。

### 组 D：bloom 后处理（2 条，一个功能）
- [ui/dialogs/SettingsMenuDialog.java:44] `renderer.toggleBloom` 无对应物 → 设置里 bloom 复选框只存值、画面不变。原版参照：`Renderer.bloom`（arc 扩展库 Bloom）。
- [ui/dialogs/SettingsMenuDialog.java:289] 同上（`checkPref("bloom", ...)` 回调只存值）。

### 组 E：内容详情面板（2 条，一个功能）
- [ctype/UnlockableContent.java:66] displayInfo 仅 Block 覆写 → 物品/液体/单位/状态的详情页空白。原版参照：`Item/UnitType/Liquid/StatusEffect.displayInfo`。
- [ctype/UnlockableContent.java:69] 同上方法体（空实现本体）。

### 组 F：贴图资源缺口（3 条，加资源即修）
- [content/Blocks.java:1866] 图集无 message 贴图 → MessageBlock 渲染成 1x1 纯白方块（blank 兜底）。修法：`sprites_out/` 加 `message.png` 后重打图集（见构建工具链约定：新贴图必须同时拷进 sprites_out）。
- [content/Blocks.java:1875] 图集无 illuminator/illuminator-top → 照明灯整块不可见（draw 已加 null 守卫防 NPE）。
- [world/blocks/distribution/ArmoredConveyor.java:11] 图集无 armored-conveyor → 装甲传送带借用普通 conveyor 贴图（外观不可分辨）。

### 组 G：RTS 命令 AI 补全（3 条）
- [ai/Pathfinder.java:306] `PathTarget.rallyPoints` targeter 为空 → **集结链断裂**：RallyPoint/CommandCenter 方块已在（带 `BlockFlag.rally`），GroundUnit 的 rally 状态调 `moveToCore(PathTarget.rallyPoints)` 却永远拿不到目标（BaseUnit:324 的 `getClosest(BlockFlag.rally)` 是另一条能用的路）。原版参照：`Pathfinder.rallyPoints` 扫 `BlockFlag.rally` 建筑并写流场。
- [ai/types/CommandAI.java:34] `hit()` 受击反击 / `afterRead()` 读档恢复命令 / boost 起降未移植 → 命令中的单位被打不还手、读档后命令丢失。原版参照：`CommandAI.hit/afterRead`。
- [ai/types/CommandAI.java:35] blockingUnit 避让未移植（`Units.nearbyCheck` 不存在，grep 证）→ 多单位同目标时在目标点堆叠。原版参照：`CommandAI` 的 `blockingUnit` 分支。

### 组 H：地图滤镜应用链（1 条）
- [io/MapIO.java:44] `World.FilterContext` 未实现（grep 证无该类）→ 导入带生成滤镜的地图时滤镜不生效（`GenerateInput.begin` 无调用点）。原版参照：`World.FilterContext.end()` 的双层循环 apply。

### 组 I：杂项小功能（3 条）
- [ui/dialogs/DatabaseDialog.java:39] `Fonts.getUnicode` 缺 → 数据库界面 SHIFT+点击复制内容字符的彩蛋失效（:142 同）。
- [ui/dialogs/DatabaseDialog.java:142] 同上（`keyDown(SHIFT_LEFT) && getUnicode(...) != 0` 分支被 TODO）。
- [ui/dialogs/SettingsMenuDialog.java:180] `coreselect` 设置无消费者 → 保存蓝图时不按设置决定是否把核心写进蓝图（Schematics.java 的 write/saveChanges/create 均已存在，只差这一句判据）。原版参照：`Schematics.create` 里 `Core.settings.getBool("coreselect")`。

---

## ③刻意偏离（保留）——217 条

注释本质是"降级 / 等价替代 / 原版同款 / 依赖缺失已声明"的长期文档，保留即可（个别可在顺手时去掉 TODO 字样改写为普通注释，但无功能影响）。

### 根目录 / core / ctype / game / graphics / input / math
- [ClientLauncher.java:2061] 调试设施 buildMuzzleTest 内解释"耗电炮塔在该场景供不起电不开火"，非功能缺口。
- [UnitCommand.java:12] 类注释：命令系统与原版的刻意偏离清单（子控制器未移植，全走 defaultBehavior）。
- [UnitCommand.java:35] repair/rebuild/assist/mine/payload 命令依赖未移植子系统，暂只提供 move（声明清单）。
- [UnitStance.java:15] patrol/ram/boost/holdPosition/mineAuto 姿态按已移植子系统裁剪（声明）。
- [ContentLoader.java:141] mod 内容前缀未移植（mod 系统降级的一部分）。
- [Platform.java:89] `Core.input.getTextInput` 软键盘包装未移植（死接口的声明）。
- [Renderer.java:393] Category.upgrade 时给屏幕外 MechPad 画指示箭头降级跳过（无建造分类状态）。
- [Content.java:49] minfo 缺 mod/sourceFile 字段（mod 系统未移植的声明）。
- [MapEditor.java:97] Steam 创意工坊 steamid 省略（无 Steam 集成）。
- [Vars.java:55] `iconsmall` "TODO remove"——原版同款注释（Vars.java:74），照抄保留。
- [Floor.java:132] beginShader/endShader 空壳的说明（与 CacheLayer 同组，水面着色器缺失已声明）。
- [RidgedPerlin.java:11] "this is a mess"——arc 同款注释（Arc/arc-core/.../RidgedPerlin.java:6），照抄保留。
- [Bresenham2.java:9] 之外的说明性 javadoc（见 ①，仅"Placement 里同名 TODO"一句过时）。
- [KeyBinds.java:10] 之外的类型定义说明。
- [DesktopInput.java:501] `!ui.minimapfrag.shown()` 守卫缺——但注释自证"update() 开头遇到全屏小地图已提前 return"，等价覆盖。
- [DesktopInput.java:503] `!scene.hasKeyboard()` 守卫缺——文本输入都在 Dialog 里，已被 hasDialog 覆盖（等价声明）。

### game / entities
- [Objectives.java:12] 裸 `//TODO`——原版同款（Objectives.java:11）。
- [Objectives.java:29] 裸 `//TODO`——原版同款（Objectives.java:28）。
- [LoopControl.java:25] `sound.calcFalloff` 缺（libgdx Sound 无该方法）→ 不衰减（等价降级声明）。
- [LoopControl.java:53] `sound.calcPan` 缺 → 无声像（等价降级声明）。
- [SoundLoop.java:29] `calcVolume/calcPan` 缺 → 同组（音量/声像空间化未做，已声明）。
- [MusicControl.java:56] 之外的降级描述（菜单态只播 Musics.menu 的现状说明；TODO 字样本身过时归 ①）。
- [Tutorial.java:96] `!Core.scene.hasDialog()` 判定缺 → 直接 stage.draw()（少一个"有对话框不画教程浮层"的判定，等价降级声明）。
- [BaseUnit.java:1014] 单位 write 退化为空实现——自证"全工程无调用方、是死代码链"，等移植单位同步时补齐。
- [BaseUnit.java:1024] read 同上。
- [Player.java:81] isTyping 恒 false（无 NetServer.chatting 包字段，降级声明）。
- [BaseDrone.java:57] `FlyingUnit.onCommand` 钩子缺 → 等价实现在别处（声明）。
- [BaseDrone.java:63] `FlyingUnit.updateRotation` 钩子缺 → 同上。
- [BuilderDrone.java:34] 类注释：刻意偏离/未移植依赖总述（方法内均有标注）。
- [BuilderDrone.java:155] `BuildSelectEvent`"他人开始建造时停止本地建造"联动未移植（事件系统声明）。
- [HoverUnit.java:23] `FlyingUnit.drawWeapons` 无钩子（B 类，当前无调用点）。
- [HoverUnit.java:34] `FlyingUnit.attack(float)` 无钩子（B 类）。
- [HoverUnit.java:39] `FlyingUnit.updateRotation` 无钩子（B 类）。
- [MinerDrone.java:52] `getClosestCore` 返回核心瓦片（材料走队伍共享库存，等价声明）。
- [MinerDrone.java:87] `getSpawner()` 返回 int 坐标而非实体（等价声明）。

### content
- [Blocks.java:223] Floor 缺 updateEffect/cacheLayer 字段 → 原版对应配置降级为注释（已声明；cacheLayer 相关见 CacheLayer 空壳）。
- [Blocks.java:413] Renderer 画 overlay 走 Floor.variant + batch.draw，不调 DoubleOverlayFloor.draw() → pebbles 影子层不画（绘制路径偏离声明）。
- [Blocks.java:737] cryofluidmixer 无裸名贴图 → textureName 指向 -bottom 兜底（资源缺口的既有降级方案）。
- [Blocks.java:1043] 炮塔 activeSound/activeSoundVolume 缺依赖，归 B 类保留（工作音效未接线声明）。
- [Blocks.java:1382] `activeSound = Sounds.beam` 降级注释（同组）。
- [Blocks.java:1811] MechPad 换装走 UnitType 等价路径（F12），"等机甲系统移植后接线"是未来增强声明。
- [Mechs.java:33] javadoc：shootHeat 无对应物，依赖它的覆写整段禁用（对应 :242，声明）。
- [Mechs.java:37] javadoc：Shaders 缺 build 成员 → omega 装甲绘制禁用（声明）。
- [Mechs.java:62] timerAbility=4 借用单位计时器（Player 无计时器系统的等价方案，已工作）。
- [Mechs.java:96] `player.healBy` → `player.unit().healBy`（Player 是持有者的等价实现，下一行即实现）。
- [Mechs.java:134] `Player.timer.get` → 单位计时器（等价实现）。
- [Mechs.java:139] `Effects.effect(Fx.landShock, player)` → 传坐标（Position 语义等价）。
- [Mechs.java:189] 同 :134（tau 治疗计时器）。
- [Mechs.java:194] `unit.health` 字段 → `health()` 方法（API 命名差异声明）。
- [Mechs.java:203] `Fx.healWave` 传坐标（等价）。
- [Mechs.java:242] shootHeat 依赖的三段覆写整段注释保留（该类钩子无调用方——真实路径在 UnitTypes，见类 javadoc"钩子的实际调用方"）。
- [Mechs.java:388] `player.velocity()` → `player.unit().velocity()`（等价）。
- [Mechs.java:420] `bulletWidth/bulletHeight` → `width/height`（改名声明）。
- [Mechs.java:426] `bullet.shootSound` → 挂到 Weapon（:428 已实现，声明）。
- [Mechs.java:434] 同 :388（trident canShoot）。

### world / world.blocks
- [Block.java:299] editorIcon 退化用 icon(Cicon.full)（图集无 -icon-editor 系列，回退已实现）。
- [Block.java:319] editor 变体贴图回退到实景贴图（地图编辑器观感差异已声明）。
- [BlockStorage.java:90] `noSleep()` 去掉（本项目无方块休眠机制，等价声明）。
- [BlockStorage.java:97] 同上。
- [BlockStorage.java:111] 同上。
- [CachedTile.java:13] `preChanged()` 钩子缺 → 覆写 changed() 等价替代（B 类声明）。
- [Autotiler.java:53] `req.worldContext` 固定 true（BuildRequest 未移植该字段，B 类）。
- [RespawnBlock.java:25] 类注释：与原版差异总述（每处有标注）。
- [TreeBlock.java:12] `layer/expanded` 字段缺（B 类：Layer.power 分层绘制未接线）。
- [ExtendingItemBridge.java:6] 弧线桥绘制降级为直线（C 类刻意，视觉差异小）。
- [MessageBlock.java:58] 配置方法原为 @Remote 网络方法（B 类：单机直调等价）。
- [MessageBlock.java:165] `updateTableAlign` 配置面板跟随方块位置的钩子缺（B 类）。
- [MechPad.java:51] 同 Blocks:1811（机甲系统等价路径声明）。
- [Pump.java:49] `layer = Layer.overlay` 缺 Block.layer 字段（声明）。
- [ImpactReactor.java:43] `outputsPower/consumesPower` 字段缺（B 类：电力语义已由 consumes 框架表达）。
- [ItemLiquidGenerator.java:32] 类注释：与原版差异总述。
- [LightBlock.java:20] 类注释：与原版差异总述。
- [NuclearReactor.java:46] 类注释：与原版差异总述。
- [NuclearReactor.java:98] `rebuildable = false` 字段缺（B 类：自动重建机制未移植）。
- [PowerGenerator.java:28] 类注释：与原版差异总述。
- [PowerGenerator.java:52] `sync = true` 字段缺（B 类：无网络消费方）。
- [PowerGenerator.java:53] `baseExplosiveness = 5f` 字段缺（B 类：爆炸威力链未接）。

### ai
- （CommandAI:34/35 归 ②；其余 ai 包条目在 UnitCommand/UnitStance 已列）

### editor
- [MapEditorDialog.java:88] 编辑器试玩整体降级（B 类：缺 Player.reset/set/setDead/dead 与 Vars.logic）。
- [MapEditorDialog.java:92] Vars.platform 不存在 → 文件选择用自研 FileChooser 等价替代。
- [MapEditorDialog.java:100] Maps.saveMap/tryCatchMapError 缺 → 本类自实现 saveMap（等价替代已工作）。
- [MapEditorDialog.java:102] tags["rules"] 不写——JsonIO 对 DefaultWaves 匿名子类"写得出读不回"（实测记录 + 修法方向声明）。
- [MapEditorDialog.java:252] Steam 创意工坊发布分支省略。
- [MapEditorDialog.java:296] beginForceLandscape（移动端横屏）缺 Vars.platform。
- [MapEditorDialog.java:301] logic.reset → 等价 `state.rules = new Rules()`（已实现）。
- [MapEditorDialog.java:307] platform.updateRPC 省略。
- [MapEditorDialog.java:312] updateRPC + endForceLandscape 省略。
- [MapEditorDialog.java:321] lastSavedRules 字段删除（无试玩可恢复）。
- [MapEditorDialog.java:332] playtest 原版方法体注释保留（B 类降级）。
- [MapEditorDialog.java:343] Log.warn("TODO playtest...")（运行时提示，同 :88 声明）。
- [MapEditorDialog.java:357] tags["rules"] 同 :102（方法内重复声明）。
- [MapInfoDialog.java:37] platform.addDialog 软键盘省略。
- [MapInfoDialog.java:120] genfilters write-back：匿名 GenerateFilter 无法 JSON 往返（已知阻塞声明）。
- [MapInfoDialog.java:124] 软键盘省略。
- [MapRenderer.java:62] ContentReloadEvent 未接线（事件系统缺，重取 clear-editor 贴图降级）。
- [MapSaveDialog.java:21] 软键盘省略。
- [MapSaveDialog.java:47] 同上。
- [WaveInfoDialog.java:50] Maps.writeWaves/readWaves 缺（B 类声明：波次复制/导入降级，"重置为默认波次"完整可用）。
- [WaveInfoDialog.java:52] 同上引用。
- [WaveInfoDialog.java:104] `setClipboardText` 缺 → 复制按钮降级（同组）。
- [WaveInfoDialog.java:112] `readWaves` 缺 → 载入按钮降级（同组）。
- [WaveInfoDialog.java:114] Log.warn 桩（同组）。
- [MapLoadDialog.java:30] 之外的说明（texture null 的引用归 ②）。

### graphics / io / maps / mod / net / type
- [CacheLayer.java:14] beginShader/endShader 保留为空壳（无离屏缓冲与水面着色器，声明）。
- [CacheLayer.java:52] animatedwater 设置分支省略（同组）。
- [CacheLayer.java:59] endShader 签名与贴回流程（同组）。
- [MinimapRenderer.java:229] beginShader 空壳引用（同组）。
- [JsonIO.java:52] 真正的类名映射表未落地（本项目不读原版存档；跨版本反序列化失败的声明）。
- [JsonIO.java:118] 同上（方法内重复）。
- [SavePreviewLoader.java:25] 未注册进 Core.assets——存档缩略图已由 io/SavePreview（FBO 渲染写 png）等价提供，本类是"备选加载路径"未接线。
- [SaveVersion.java:123] SaveMeta 是自有格式（version/build/mods/stats 字段集不同的刻意选择）。
- [SaveVersion.java:124] SaveMeta 不解析 Map 对象（同上）。
- [SaveVersion.java:174] mods 恒写 "[]"（Mods 无 getModStrings，mod 系统降级一部分）。
- [SaveVersion.java:193] `Vars.defaultWaves` → `new DefaultWaves().get()`（语义一致的等价）。
- [SaveVersion.java:236] `tile.floorID()` → `floor().id`（等价实现）。
- [SaveVersion.java:259] `tile.blockID()` → `blockRaw().id`（等价实现，且注释含"不能用 block()"的关键提醒，务必保留）。
- [Maps.java:37] 类注释：内置地图列表/workshop/mod 地图未移植（Maps 范围声明）。
- [FilterOption.java:48] Icon.block 缺 → Icon.terrain 占位（资源声明）。
- [FilterOption.java:171] 同上。
- [OreMedianFilter.java:63] Block.cacheLayer 缺 → 退化成"是否自然岩壁"（Rock）判定（等价声明）。
- [ContentParser.java:65] 类注释：本类与原版差异总表（mod 解析降级）。
- [ContentParser.java:173] Sound 解析退化（音频系统未启用——Sounds.load() 仍是注释状态，现状属实）。
- [ContentParser.java:285] "generate dynamically"——原版同款（ContentParser.java:185）。
- [ContentParser.java:454] 全新 mod 内容 localizedName 只能是内部名（bundle 注入不可行的声明）。
- [ContentParser.java:528] Jval 归一化缺失（hjson 风格不容忍，声明）。
- [ContentParser.java:538] minfo.sourceFile/mod 省略。
- [ContentParser.java:546] erroredContent 省略。
- [ContentParser.java:554] minfo 参数省略。
- [ContentParser.java:564] 异常因果链/堆栈打印简化。
- [ContentParser.java:582] LoadedMod 补 name 字段的修法方向（改既有文件，暂缓声明）。
- [ModLoadingSound.java:21] 音频系统未启用（Sounds.load() 在 ClientLauncher:133 仍注释——现状属实）。
- [ModLoadingSound.java:23] Sound 解析器回填（同组引用）。
- [Scripts.java:13] 类注释：rhino 未移植、大幅降级总述。
- [Scripts.java:20] 全部方法走降级的清单。
- [Scripts.java:45] runConsole 原版体保留。
- [Scripts.java:64] evaluateString 原版体。
- [Scripts.java:85] run(file) 原版体。
- [Scripts.java:94] Context.exit 空实现。
- [BeControl.java:54] 定时检查更新未接（B 类：GitHub 自动更新对移植版无意义）。
- [BeControl.java:62] httpGet 拉取 release（同组）。
- [BeControl.java:77] 下载进度弹框+重启（同组）。
- [CrashSender.java:25] 类注释：崩溃上报差异总述。
- [CrashSender.java:67] "开发者本机"判定省略（user.name/Version.modifier）。
- [NetworkIO.java:136] "truncating ... wierd encoding errors"——原版同款（NetworkIO.java:102，小写 todo）。
- [Liquid.java:53] isHidden() 由 UnlockableContent 提供改为本类自带（继承结构差异声明）。
- [Zone.java:46] minfo.mod 前缀处理（mod 场景降级）。
- [Zone.java:55] `//TODO autogenerate`——原版同款（Zone.java:23）。
- [Zone.java:152] updateObjectives 只留 closure.run()——事件类型 ZoneRequireCompleteEvent/ZoneConfigureCompleteEvent 未移植，硬接等于死代码（阻塞原因已写明）。
- [Zone.java:241] minfo.mod 分支省略。

### ui.dialogs
- [ControlsDialog.java:10] 之外的"外壳"落地说明（键位表本体归 ②）。
- [CustomRulesDialog.java:356] 软键盘省略。
- [CustomRulesDialog.java:392] 软键盘省略。
- [FileChooser.java:45] 软键盘省略。
- [FileChooser.java:116] 同上。
- [HostDialog.java:42] platform.updateLobby 省略（无 Vars.platform）。
- [HostDialog.java:164] 同上（方法内）。
- [JoinDialog.java:37] 类注释：差异总表目录。
- [JoinDialog.java:60] 官方服务器 JSON（httpGet+Jval）未拉取 → 只用 Vars.defaultServers（对移植版刻意为之）。
- [JoinDialog.java:67] Vars.platform 软键盘省略。
- [JoinDialog.java:71] net.reset/beginConnecting/disconnectQuietly/logic.reset 逐处降级（本项目 NetClient.connect 内部自管）。
- [JoinDialog.java:127] 软键盘省略。
- [JoinDialog.java:513] refreshGlobal 占位（defaultServers 恒空，同 :60）。
- [JoinDialog.java:596] disconnectQuietly → disconnect（等价）。
- [JoinDialog.java:602] logic.reset 等三连 → NetClient.connect（等价声明）。
- [JoinDialog.java:608] net.connect 成功回调 → 直接收起对话框（等价声明）。
- [JoinDialog.java:646] httpGet 官方列表（同 :60，方法内重复）。
- [ModsDialog.java:35] requiresReload/ui.loadAnd 降级。
- [ModsDialog.java:40] GitHub 导入依赖的 ui 方法缺失总述。
- [ModsDialog.java:74] "从 GitHub 导入"按钮禁用占位。
- [ModsDialog.java:92] hidden 里的 requiresReload 分支省略。
- [ModsDialog.java:100] reloadrequired 标签省略。
- [MapsDialog.java:40] 类注释：差异总表目录。
- [MapsDialog.java:58] viewListing 省略（无 Vars.platform）。
- [MapsDialog.java:60] mod.meta.displayName 缺 → toString()（等价）。
- [MapsDialog.java:316] viewListing 降级为日志。
- [MapsDialog.java:317] Log.warn 桩（同上）。
- [MapsDialog.java:401] displayName → toString()（方法内）。
- [PausedDialog.java:24] 类注释：差异总表目录。
- [PausedDialog.java:37] inviteFriends 省略。
- [PausedDialog.java:164] 同上（方法内）。
- [PausedDialog.java:165] Log.warn 桩（同上）。
- [SchematicsDialog.java:352] open workshop 不存在（无 Vars.platform）。
- [SchematicsDialog.java:379] publish schematic 同上。
- [SaveDialog.java:219] 存档槽位文案降级（slot.mode() 展示差异，等价声明）。
- [SettingsMenuDialog.java:36] 类注释："仍未接的部分"目录（具体条目见各条）。
- [SettingsMenuDialog.java:38] exportData/importData 降级——数据目录=assets 根，整体打包有删光资源风险（安全声明，按钮只记日志）。
- [SettingsMenuDialog.java:49] mobile 输入切换降级（T14 纠正：InputMultiplexer 启动时一次性捕获，setInput 切换不生效；desktop 不影响）。
- [SettingsMenuDialog.java:56] steam 专属项跳过（Vars.steam 恒 false）。
- [SettingsMenuDialog.java:155] sfxvol 无消费者——**原版同款状态**（原版 SettingsMenuDialog.java:206 也只注册不读取）。
- [SettingsMenuDialog.java:160] 同 :49（方法内）。
- [SettingsMenuDialog.java:190] steam 项（同 :56）。
- [SettingsMenuDialog.java:254] ios 横屏分支省略。
- [SettingsMenuDialog.java:357] exportData 按钮（同 :38）。
- [SettingsMenuDialog.java:362] importData 按钮（同 :38）。

### ui.fragments
- [BlockInventoryFragment.java:85] 原版 @Remote 网络方法 → 单机直调（B 类声明）。
- [BlockInventoryFragment.java:94] 服务端校验分支（同组）。
- [FadeInFragment.java:34] setFillParent 是 arc Element API → 手动等价（已实现）。
- [FadeInFragment.java:43] Fill.crect 左下角 → Fill.rect 中心（换算已实现）。
- [LoadingFragment.java:52] parent.fill → setFillParent+setBackground（等价实现）。
- [LoadingFragment.java:62] Cell.name → Actor.setName（等价实现）。
- [LoadingFragment.java:68] Bar 无参构造 → Supplier 构造（等价实现）。
- [LoadingFragment.java:77] addButton → TextButton（等价实现）。
- [LoadingFragment.java:91] bar.reset → Supplier 每帧读取（等价实现）。
- [LoadingFragment.java:98] 监听器替换 → clearListeners+ClickListener（等价实现）。
- [LoadingFragment.java:110] 泛型按名查找 → findActor（等价实现）。
- [LoadingFragment.java:151] 顺序 actions → Actions.sequence（等价实现）。
- [PlacementFragment.java:863] placement.blockselectkeys 文案降级（keybinds 查询缺失的等价处理）。
- [PlacementFragment.java:894] 数字选择键 javadoc（方向键部分声明）。
- [PlacementFragment.java:895] blockSelect 分类组合键 → 直接数字键（等价降级声明）。
- [PlayerListFragment.java:46] votekick 未接（B 类：服务端无 /votekick 命令处理）。
- [PlayerListFragment.java:302] adminUsid 防冒用缺 → 用 setAdmin(uuid,bool) 等价。
- [PlayerListFragment.java:324] votekick 按钮不显示（同 :46，方法内）。
- [ScriptConsoleFragment.java:78] scene.add → getStage().addActor（等价实现）。
- [ScriptConsoleFragment.java:91] arc visible() 每帧回调 → act()（等价实现）。
- [ScriptConsoleFragment.java:112] Vars.net.active() → isClient()/isServer()（等价实现；Vars.net 确无赋值点，grep 证）。
- [ScriptConsoleFragment.java:175] scene.getStyle → Styles.defaultField（等价实现）。
- [ScriptConsoleFragment.java:184] margin → pad（等价实现）。
- [ScriptConsoleFragment.java:198] Fill.crect 左下角 → 中心换算（等价实现）。
- [ScriptConsoleFragment.java:213] getMarginBottom → getPadBottom（等价实现）。
- [ScriptConsoleFragment.java:222] font.getCache().draw 显式传 batch（等价实现）。
- [ScriptConsoleFragment.java:233] Fill.crect → Fill.rect（等价实现）。
- [ScriptConsoleFragment.java:271] scene.setKeyboardFocus → getStage()（等价实现）。
- [ScriptConsoleFragment.java:284] 同上。
- [ScriptConsoleFragment.java:293] 同上。

---

## ④待议——0 条

本轮全部条目均可凭代码+原版对照定性，无遗留歧义。
唯一"半过时"条目已并入 ①：SaveVersion.java:54（stats/rules/build 已接、mods 仍降级——修注释时按现状改写即可）。

---

## 附：②类建议的批次切法

| 批次 | 内容 | 条数 | 说明 |
| --- | --- | --- | --- |
| 缩略图链 | 组 A | 8 | 把 SavePreview 的 FBO 渲染思路套到 Maps.queueNewPreview；UI 侧 4 个对话框只等 `map.texture` 就绪 |
| 键位系统 | 组 B | 2 | KeybindDialog 替代层 + KeyBinds 存取（读 settings 落盘） |
| 教程指引 | 组 C | 2 | 需要"按 name 找 UI 元素"的登记表（可复用 PlacementFragment 的 actor 命名习惯） |
| rally 流场 | 组 G-1 | 1 | Pathfinder.rallyPoints 扫 BlockFlag.rally（十几行） |
| 命令 AI 补全 | 组 G-2 | 2 | hit() 反击 + afterRead 恢复；blockingUnit 需先补 Units.nearbyCheck |
| 贴图资源 | 组 F | 3 | sprites_out 补 message/illuminator/armored-conveyor 后 texturePacker（记得区域名集合 diff 校验） |
| 小功能 | 组 D/E/H/I | 8 | bloom 管线最重，可最后；coreselect/数据库复制/FilterContext 都是局部改动 |
