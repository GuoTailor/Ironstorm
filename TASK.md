# TASK.md — phoenix 移植任务

## 当前：寻路系统移植（Mindstry-126.2 → libgdx 版）

参照 `D:\IdeaProjects\Mindustry-126.2\core\src\mindustry\ai\Pathfinder.java` 移植流场寻路。
替换 arc 库为 libgdx；线程模型=后台 daemon 线程；PathTarget 目标=占位（边界/随机），待建筑系统接入。

- [x] 1. Geometry.d4/d8 方向数组（`math/geom/Geometry`）
- [x] 2. PathTile struct + `Vars.pathfinder` 字段（`ai` 包）
- [x] 3. Pathfinder.java 核心移植（PathData/流场扩散/后台线程/getTargetTile/占位 targeter，WorldLoad/Reset 事件挂接）
- [x] 4. BaseUnit 接线寻路（moveTo 直线改流场）
- [x] 5. 编译 + 桌面运行验证

## 2026-09-14：最小核心建筑环

- [x] 6. CoreBlock + CoreEntity（建筑实体生命周期：entity 创建/血量/摧毁）
- [x] 7. Control.play 放置双方核心；PathTarget.enemyCores 目标改真实核心查询
- [x] 8. 建筑摧毁/放置后刷新寻路网格（updateTile + 重扩散 + 重查目标）
- [x] 9. 编译 + 桌面运行验证（核可被子弹摧毁，摧毁后寻路网格刷新）

## 2026-09-14：真实多格核心 + 胜负判定

- [x] 10. Block.linked / BlockPart：3x3 多格链接，卫星瓦片转发到主瓦片
- [x] 11. Tile.link()/block()/setBlock/remove 多格占用；Renderer 跳过卫星瓦片
- [x] 12. Logic.checkGameOver：任一队伍核心全毁 → 胜负 → 回菜单（清理单位/子弹）
- [x] 13. 编译 + 桌面运行验证

## 2026-09-14：HUD + 建造栏最小闭环

- [x] 14. InputHandler.buildBlock + DesktopInput.touchDown 放置（坐标换算→tile.setBlock）
- [x] 15. HudFragment：顶部双方核心状态 + 底部建造栏（墙体/核心按钮）
- [x] 16. ClientLauncher：战役渲染同 stage 绘制 HUD；输入接 InputMultiplexer(stage, Input)
- [x] 17. 编译 + 桌面运行验证（点击按钮可放墙/核，刷新寻路）

## 2026-09-14：物品/生产最小闭环

- [x] 18. Item + Items + ItemStack + ItemModule
- [x] 19. Teams 共享库存与生产方块
- [x] 20. HUD/Blocks/Control 接线
- [x] 21. 编译运行验证

## 2026-09-14：传送带 / 仓储

- [x] 22. Block 物品接口（hasItems/acceptItem/handleItem/offloadNear/offloadDir/tryDump）
- [x] 23. TileEntity.items 本地库存 + Tile.front()
- [x] 24. Conveyor + StorageBlock 方块
- [x] 25. Drill/GenericCrafter/CoreBlock 改接本地库存
- [x] 26. 放置朝向 + HUD 按钮
- [x] 27. 编译运行验证

## 2026-09-14：玩家单位控制

参照 `mindustry/entities/type/Player.java`、`mindustry/input/DesktopInput.java` 移植玩家操作。
原版 Player 自身就是一个 Unit（带整套 Mech 装备系统），这里简化为“Player 持有一个被控制的单位引用”。

- [x] 28. `entities/type/Player`（位置/瞄准点 pointerX,Y/射击/加速状态/死亡重生）+ `Vars.player`
- [x] 29. `BaseUnit.isPlayer`：玩家单位不跑 AI 状态机，改由输入驱动
- [x] 30. `InputHandler`：movement 向量、鼠标世界坐标、updateMovement/updateShooting、Shift 加速倍率
- [x] 31. `DesktopInput`：存活时 WASD 操控单位（死亡/暂停时才平移相机）、左键射击（建造模式优先放置方块）
- [x] 32. `Control.spawnPlayerUnit`（核心旁出生 + 相机对准）+ 死亡 5 秒后重生；Logic 更新玩家、Renderer 相机 lerpDelta 跟随
- [x] 33. `HudFragment`：操作提示 + 玩家血量/重生倒计时
- [x] 34. 编译 + 桌面运行验证（出生/移动/射击/受伤死亡/重生计时 300tick 均正常）

## 2026-09-14：敌方波次 + 炮塔（战斗闭环）

参照 `mindustry/ai/WaveSpawner.java`、`game/SpawnGroup.java`、`game/DefaultWaves.java`、`world/blocks/defense/turrets/Turret.java` 移植。
出生点沿用原版机制（地图上的 spawn overlay），由 `World.createMap` 在地图四角生成；炮塔的敌我判定依赖子弹队伍字段。

- [x] 35. 子弹队伍修正：`Bullet` 队伍字段（默认继承发射者）+ 友方单位/建筑穿透（避免误伤自家）
- [x] 36. `Time` 延迟任务队列（`run/clear`，顺带修复武器 `shotDelay` 连发此前完全失效的问题）
- [x] 37. 波次数据：`SpawnGroup`（数量随波次增长）+ `DefaultWaves`（5 组编成）；`Rules.waves/waveTimer/waveSpacing/spawns`、`GameState.wave/wavetime/enemies`
- [x] 38. `WaveSpawner`：扫描 spawn overlay 出生点、`spawnEnemies`、最近出生点查询；`Blocks.spawn`（OverlayFloor）+ `World.placeSpawnPoints`
- [x] 39. `Logic`：波次计时/`runWave`/敌军统计；`Control` 开局宽限（`waveSpacing * 2`）；`BaseUnit.getClosestSpawner` 接真实出生点
- [x] 40. 炮塔 `Turret` + `duo` 方块（消耗铜弹药）：索敌/转向/提前量瞄准/多发开火
- [x] 41. HUD：波次倒计时 + 敌军数 + "下一波"按钮 + 炮塔按钮；顶部/底部改为两张 fillParent 表（原来是单表混用 top/bottom 对齐）
- [x] 42. 编译 + 运行验证（4 个出生点、开波后敌军增长、炮塔索敌开火并消耗弹药、炮口友军满血不误伤）

## 2026-09-15：建造系统（材料 / 拆除 / 墙体可破坏）

参照 `mindustry/world/Build.java`、`world/blocks/defense/Wall.java`、`ui/fragments/PlacementFragment` 移植最小版。
此前建筑是免费且无限放、物品经济没有出口、墙体不可破坏；这一步把三者一起补上。

- [x] 43. `Block.requirements`（建造材料）+ `Rules.loadout`（开局材料）+ `Build`（放置校验/扣料/拆除返还/成本文本）
- [x] 44. `Wall` 方块：墙体带实体承载血量、可被子弹摧毁（`Block.hasEntity()` 改为 `update || destructible`）
- [x] 45. 子弹瓦片规则修正：只有实心方块阻挡子弹，只有敌方建筑扣血（友方墙挡子弹但不掉血）
- [x] 46. 修复 `World.toTile` 半格偏移（四舍五入 → 向下取整）：此前 1x1 墙体根本打不中、放置位置也偏半格
- [x] 47. 输入：可连续放置（不再放一次就退出）、拆除模式、右键取消建造（对应 `Binding.deselect`）
- [x] 48. HUD：建造按钮显示材料需求（不足变红）+ 拆除按钮（激活时高亮）+ 提示更新
- [x] 49. 编译 + 运行验证（放墙扣 6 铜 200→194、拆除返还 194→197、无材料放置失败、多格重叠放置失败、敌方子弹打墙 60→42、友方子弹打墙不掉血）

### 已知限制（后续可补）

- 建造没有进度（放置即建成）、也没有建造距离限制；材料直接从队伍共享库存扣/返（原版走核心与建造单位）
- 墙体等方块没有摧毁特效/掉落
- 炮塔为单弹药类型、无液冷、无 `DoubleTurret` 双管交替
- 波次只有地面单位（无飞行单位类型），故 `flySpawns` 分支暂时用不上
- 出生点固定在四角，尚未支持地图存档里的 spawn 定义
- `Tile.getX()/getY()`（= `drawx/drawy`）在多格建筑上返回"锚点瓦片左下角 + offset"，与几何中心差半格；
  需要精确中心时用 `Block.centerX/centerY(tile)`
- 建造材料数值是近似值（原版按铜/铅/硅/钛/钍等 10 种物品定价，本项目只有铜/铅/硅）

## 2026-09-15：飞行单位 + 直线飞行

- [x] 50. FlyingUnit：飞行单位不走地面碰撞/流场，直线移动
- [x] 51. UnitTypes.flare：新增飞行单位类型
- [x] 52. DefaultWaves：接入飞行编成，激活 WaveSpawner.flySpawns
- [x] 53. 编译运行验证

## 2026-09-15：多槽位存档 + 自动存档

- [x] 54. SaveIO：地图/建筑/库存/单位/波次二进制存读
- [x] 55. Saves：槽位扫描/新建/删除/自动存档计时
- [x] 56. Control + Vars 接线，读档重建世界
- [x] 57. HUD 存档/读档/自动存档控件
- [x] 58. 编译运行验证

## 2026-09-15：战斗/视觉反馈

- [x] 59. BulletType.trail + Basic 高速弹拖尾
- [x] 60. 炮口闪光（duo.shootEffect + flame 火苗）
- [x] 61. 建筑血条（Renderer 受损建筑画条）
- [x] 62. 建筑受击火花（hitTile 命中实体）
- [x] 63. 建造/拆除悬停框
- [x] 64. 编译运行验证

## 2026-09-16：更多地面单位

- [x] 77. 地面单位 grenadier（榴弹）+ tank（重型）
- [x] 78. DefaultWaves 接入（第 9/16 波起）
- [x] 79. 编译运行验证

## 2026-09-15：多格贴图修复（叠加在上面这版代码上）

- [x] 74. 多格贴图被重复绘制：Renderer 过滤卫星瓦片改用 `Tile.isLinked()`
        （原来用 `tile.block().isHidden()`，而 `tile.block()` 对卫星瓦片会转发到中心方块，条件恒不成立）
- [x] 75. 多格贴图"缺上半部分"：地板与方块改为**两遍绘制**（对应原版 `drawFloor`/`drawBlocks` 分离）。
        多格贴图由中心格绘制、会延伸到上方/右侧格子，按"逐格地板+方块"的顺序画会被上面几格的地板盖掉
- [x] 76. 偶数尺寸多格恢复：footprint 用原版写法（锚点 + 0..size-1 + `-(size-1)/2`）、
        `Block.offset()` 补半格（偶数）、新增 `Block.centerX/centerY`，`TileEntity.hitbox` 同步修正
- [x] 77. 重新打上被覆盖的修复：HUD 独立相机（`stageViewport`，否则 HUD 随滚轮缩放放大）、
        `BaseUnit.setSpawner/getSpawner`、`GroundUnit.attack` 卡住改走流场
- [x] 78. 恢复单位工厂：`Blocks.daggerFactory`（2x2、消耗硅 6/单位、`produceTime` 850、`maxSpawn` 4、
        材料 60 铜 + 40 铅）+ HUD「工厂」按钮；上层贴图 `dagger-factory-top` 已接入
- [x] 79. 编译 + 运行验证（放工厂 → 硅 6→0 → 出厂 `dagger` → 出厂点不在实心格 → 位置持续移动 → 阵亡后 alive 归零；
        贴图 = 底座 + 上层 + 生产进度条，2x2 占位与贴图对齐）
- [x] 80. 去掉开局预置部队：`Control.play()` 不再调用 `spawnSquad()`（连同 `spawnSquad/spawn` 方法一起删除）。
        现在开局只有玩家自己那一个被控单位，敌人只从波次来、我方单位靠单位工厂造
- [x] 81. 顺手修好一处编译错误：`MenuFragment` 的「读档」按钮调用了只存在于 `HudFragment` 的私有 `showLoadDialog()`，
        改为两者共用的静态方法 `HudFragment.showLoadDialog(Stage)`

## 2026-09-15：工厂产线打通（铅钻 + 状态条）

排查"放了工厂不出兵"：工厂本身没问题，缺的是**硅**；而硅需要铅，铅在当时没有任何来源。

- [x] 82. 新增**铅钻** `Blocks.leadDrill`（贴图 `pneumatic-drill`，采铅，60 tick/个，耗电 0.08）+ HUD「铅钻」按钮
        （材料 20 铜 + 10 铅）——至此链路完整：铜钻/铅钻 → 硅炉 → 硅 → 工厂 → 单位
- [x] 83. 修正 `copperDrill` 贴图名：`copper-drill` 在图集里不存在（渲染成空白块），改用 `mechanical-drill`
- [x] 84. 工厂生产状态可视化：进度条**常驻显示**并按状态着色——红底=缺材料（缺硅）、灰满条=在场单位已满（4）、
        亮色条=正在生产；缺料时不再毫无反馈
- [x] 85. 全链路运行验证（铜钻+铅钻→硅炉→工厂）：
        `f=120 硅炉[铜=1 铅=1 硅=0 电=100%] 工厂[硅=0 进度=0%]`
        `f=480 工厂[硅=4 进度=0%]`、`f=720 工厂[硅=6 进度=40%]`、`f=840 工厂[硅=2 在场=1] 我方单位 1→2`
        （扣 6 硅出厂 1 个 dagger）

## 2026-09-15：传送带贴图 + 放置朝向预览

- [x] 86. `Block.draw(Tile)`（对应原版 Block.draw(Tile)）：方块本体绘制从 Renderer 硬编码挪进方块自身，
        带朝向/动画的方块可以覆盖；新增 `Block.rotate`（是否可旋转）与 `Block.outputsItems()`（拼接判断用）
- [x] 87. 传送带贴图：图集里只有 `conveyor-拼接组-帧`（0~4 组 × 4 帧），没有名为 `conveyor` 的单图，
        原先走 `loadRegions` 会退回 `blank` 白块 → 改为按 `名称-组-帧` 加载，
        绘制时 `rotation` 旋转（贴图基准朝向为"右"，角度 = (1-rotation)*90）+ 按 `Time.time` 走帧 + 堵塞停帧
- [x] 88. 传送带贴图自动拼接：移植 `Autotiler.buildBlending` + `Conveyor.transformCase`
        （左右/后方拼接 → 拼接组 0~4 + 镜像），四邻取 `Geometry.d4[mod(rotation - direction, 4)]`，非朝向方块（钻头/硅炉/核心）视为任意方向可拼接
- [x] 89. 建造预览（对应原版 DesktopInput.drawTop）：半透明方块贴图（按朝向旋转）+ 黄色朝向箭头，
        绿色=可放、红色=位置非法/材料不足；`HudFragment` 顶部同时显示"放置朝向：右（R 键切换）"
- [x] 90. 顺手修好 `Blocks.storage` 贴图名（图集里是 `container`，原来渲染成白块）
- [x] 91. 抓图验证：4 种朝向的带子分别为 0/2=竖向、1/3=横向（与 rotation 一致），自发光白块消失，预览箭头方向正确

## 2026-09-15：拖拽连放（传送带自动转弯）

参照 `mindustry/input/InputHandler.iterateLine` + `DesktopInput` 的连线放置：
按下左键进入连线、拖动刷新路径、**松手才整体建造**（对应原版 lineRequests + flushRequests）。

- [x] 92. `InputHandler`：`beginLine/updateLine/flushLine/clearLine` + `relativeTo`（对应原版 `Tile.relativeTo`）
        与 `buildPath`（直线 / L 形两种路径，对应原版 `Placement.normalizeLine`）；
        每一格朝向指向**路径下一格**、最后一格用"起点→终点"方向（对应原版 `baseRotation`）
- [x] 93. 传送带默认走 **L 形路径**（先长轴后短轴），拖出拐角时带子自动转向下一格；握住 **Ctrl 强制直线**
        （原版用 `Binding.diagonal_placement` 做相反的切换）；长度上限 48 步
- [x] 94. `DesktopInput`：左键按下 `beginLine`、每帧按鼠标刷新连线、松手 `flushLine`；
        右键/切换方块取消连线
- [x] 95. 连线预览：路径上每一格都画半透明 ghost（各自按自身朝向旋转）+ 最后一格的方向箭头（对应原版 drawTop）
- [x] 96. 顺手加了**拖动拆除**：拆除模式下按住左键沿路径连续拆除（快速拖动也不漏格）
- [x] 97. 验证：L 形拖拽路径 `RRRRRRRRRUR`（第 10 格朝向 U=拐角指向下一格）、落格后 row 与路径完全一致；
        截图中预览为半透明带子 + 绿色占地格，松手后整排带子落地且贴图/朝向正确

## 2026-09-16：波次深化 / Boss

- [x] 98. `UnitType.boss` 标记；chaos-array、eradicator 设为 Boss 类型
- [x] 99. `DefaultWaves` 接入 Boss 编成：chaos-array 第 40 波起每 30 波，eradicator 第 70 波起每 50 波
- [x] 100. Renderer 绘制 Boss 专属常驻血条；HUD 标记 Boss 波次
- [x] 101. 编译验证

## 2026-09-16：电网修复 + 工厂耗电接入（完整产线闭环）

完整产线（铜钻/铅钻 → 硅炉 → 工厂 → dagger）三件生产建筑全吃电，此前电网接不出来、生产不了电。

- [x] 102. **根因修复**：`World.rebuildPowerGraphs` 跳过条件误用 `tile.entity.graph`（TileEntity 独有字段，恒 null），
        导致每块带电建筑被建成**独立电网**——太阳能板独享自己的电，钻头/硅炉各自孤网 `0/需求→satisfaction=0→停机`。
        改判 `tile.entity.power.graph`（reflow 实际写入的电网字段），同一四邻域连通域内的带电建筑并入一个 `PowerGraph`，
        太阳能板产的 0.35 电按满足率分给全链路
- [x] 103. 单位工厂接入电网：`UnitFactory` 加 `hasPower + powerConsumption(0.10)`，`update` 读 `power.status`，
        供电不足暂停生产、按满足率减速，完整产线真正"全电依赖"
- [x] 104. 验证：自动播放 40s，`120x120` 地图加载，`at com.phoenix` 异常 0，进程稳定运行至超时被杀（RUNEXIT=124）

## 2026-09-16：Mindustry HUD 完整移植

参照 `Mindustry-126.2/core/src/mindustry/ui/fragments/HudFragment.java`、
`PlacementFragment.java`、`input/DesktopInput.java`，使用 libgdx 重做右侧 HUD。

- [x] 105. 拆分 HudFragment / PlacementFragment，保留现有存档、波次、建造逻辑
- [x] 106. 右侧建造栏：分类、4 列方块网格、材料状态、拆除按钮
- [x] 107. 选中/悬停状态详情：未选中不显示建筑运行信息
- [x] 108. Tab 菜单开关、Esc/右键取消建造，接入输入路由
- [x] 109. 小地图与截图布局对齐（右上角独立 libgdx Actor，绘制地板/方块/玩家标记）
- [x] 110. 编译、运行和截图验证（core/desktop 编译成功，自动播放启动并加载 120x120 地图）

## 2026-09-17：HUD 按 Mindustry-126.2 重做

当前 HUD 与参考实现差异较大，废弃临时右侧布局，按参考 `HudFragment` / `PlacementFragment` 重新移植；只使用 libgdx，不引入 arc。

- [x] 111. 补齐 Category、Block 图标与显示接口，移除字符串猜分类
- [x] 112. 按参考 PlacementFragment 重建右侧详情、4 列滚动网格、分类图标栏、放置控件
- [x] 113. 按参考 HudFragment 重建左上波次/菜单、暂停、保存提示、小地图层级
- [x] 114. 对齐快捷键选择、Tab 菜单开关、悬停详情、材料灰显行为
- [x] 115. 编译、运行和截图逐状态验证

## 2026-09-18：HUD 截图差异修复

- [x] 116. 对照参考实现定位右侧面板三段式布局与悬停详情根因
- [x] 117. 重构 PlacementFragment：详情区、网格区、分类列、底部操作区独立布局
- [x] 118. 修正未选中时详情区收缩、分类按钮尺寸/贴图与面板边缘定位
- [x] 119. 验证选中建造、悬停建筑、Tab隐藏三种状态并截图（含分类切换）
