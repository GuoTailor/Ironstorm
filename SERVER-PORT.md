# phoenix 服务端移植规划（SERVER-PORT.md）

参照工程：`D:\IdeaProjects\Mindustry-126.2\`（只读）。目标：把原版多人服务端移植到
phoenix（libgdx 版，包名 `mindustry.*` → `com.phoenix.game.*`，**不引入 arc 库**）。

本文件独立于 TASK.md，记录服务端移植的设计与分阶段任务。改动先更新本文件，再编码。

## 背景与约束

- phoenix 保留原版类名/玩法骨架，但全部用 libgdx 替代 arc，**玩法定位是新游戏**。
- **协议策略（2026-09-18 修正）**：网络协议完全自有（phoenix 原生包），**不兼容原版 Mindustry 的
  arc.net 线路、ConnectPacket 字节格式、InvokePacket RPC**——无需与原版客户端/服务器互通。
- 原版网络层仅作架构参考（NetProvider 分层、流分块、服务端权威模型）。
- 客户端耦合点（不可进服务端）：Renderer、UI、Core.camera、Core.atlas、输入、本地 `Vars.player`。

## 关键事实（已探明）

- `ServerControl` ≈994 行、29+ 控制台命令，**完全 headless 安全**（无 renderer/UI import；
  Effects provider 在构造里置为 no-op；只读 `Core.graphics` delta/FPS）。
- `NetServer` ≈874 行、`Administration` ≈579 行、`Maps` ≈504 行、`Mods` ≈817 行。
- 存档 = zlib 分块区域序列化，`.msav`（头 MSAV），无 radix tree。
- phoenix 现状：`Logic.update()` 是世界仿真主循环（headless 安全），
  但 `Control.play()` 混入玩家单位/相机/输入/渲染器，不可直接用于服务端。
  `Vars.control = new Control()`（静态）→ `new DesktopInput()`，其构造 headless 安全。

## 分阶段任务

### 阶段 0：基线
- [x] 探明原版网络/服务端/存档/地图/插件架构。
- [x] 探明 phoenix 当前仿真能力与缺口。
- [ ] 本文件（规划与阶段清单）就绪。

### 阶段 1：Headless 服务端骨架
- [x] `server` gradle 模块（依赖 :core，引入 `gdx-backend-headless` + `gdx-platform:natives-desktop`）。
- [x] `ServerLauncher`：headless 入口（HeadlessApplication），初始化 Vars，启动世界。
- [x] `ServerControl`：headless 逻辑驱动 + 控制台线程 + 基础命令（help/version/status/runwave/say/exit）。
- [x] headless 安全的世界启动路径（不生成玩家/相机/输入；Blocks/UnitTypes 预加载、空 atlas + bundle）。
- [x] 验收：`:server:classes` 编译通过；`:server:run` 启动、tick 世界、控制台可输入、可退出。✅
- [ ] 已知遗留：Windows 控制台中文输出乱码（stdout UTF-8 vs 控制台 GBK），阶段 4 统一控制台时处理。

> 阶段 1 踩坑记录：headless 需要 `gdx-platform:natives-desktop`（HeadlessApplication 会加载 gdx64.dll）；
> 内容 `load()` 在加载期就查 `Core.atlas.findRegion` 与 `Core.bundle`，headless 需先给空 atlas + 启动 bundle。

### 阶段 2：网络协议基础
- [x] `net/` 包：Net / NetProvider / NetConnection / Packet / Packets / Registrator / Streamable / PacketSerializer / TypeIO。
- [x] Java TCP 可靠通道 + UDP 通道 + SendMode{tcp,udp}；TCP 帧 = 4 字节长度 + packet 帧，UDP = 单数据报帧。
- [x] 1 字节 packet ID 注册 + 手工 ByteBuffer 编解码 + StreamChunk 分片（每块 ≤512B）。
- [x] 网络线程只做 socket 读写，包进入线程安全入站队列，由主循环 drain 后派发。
- [x] 快照压缩接口（当前 java.util.zip 占位；原版 LZ4 对齐放阶段 6）。
- [x] localhost 往返自测：ConnectPacket client→server→client，编解码/线程投递/监听器闭环通过。
- [ ] 已知遗留：Connect/Disconnect 是内部生命周期包，尚未接入 NetServer；UDP 注册握手尚未实现；不兼容原版 ArcNet 帧（阶段 6 对齐）。

### 阶段 3：服务端连接与玩家同步
- [x] `NetServer`（core）——服务端权威仿真：openServer/host、握手校验、世界下发、玩家单位生成/同步/移除、聊天、踢出。
- [x] `NetClient`（core）——thin client 管线：connect+握手、WorldStream→重建本地世界、SpawnPlayer 生成本地单位、上报输入、EntityState 应用、聊天、被踢处理。
- [x] `NetworkIO`（net）——世界数据 zlib 压缩传输（SaveIO 复用）；`Administration`（net）——uuid→PlayerInfo、IP/uuid 封禁、人数上限。
- [x] phoenix 原生同步包（Packets）：SpawnPlayer / PlayerInput / EntityState / EntityRemove / ChatMessage / Kick（未用 @Remote 注解处理器，规避 io.anuke 包名）。
- [x] ServerControl 集成：启动即监听 `Vars.port`，命令 host/stopserver/say。
- [x] 验收：SaveIO round-trip、同进程 NetServer+NetClient 加入→世界下发→SpawnPlayer 闭环、`server:run` 监听 + say 广播。✅
- [ ] 已知遗留：UDP 注册握手未做；SaveIO 多格核心读档会退化成单格（已修复，见阶段 4）；远端玩家单位在可视化客户端尚不渲染（已修复，见阶段 6a）。
- [ ] 方向修正：原版协议兼容项全部取消（phoenix 为新游戏，自有协议）。

### 阶段 4：地图、存档、服务端控制台
- [x] `Maps`：headless 地图目录扫描、地图列表、按名加载、当前世界导出、下一个文件名。
- [x] `MapIO` 等价路径：当前复用 `SaveIO`，地图文件使用 `.msav`，避免引入 UI/贴图预览。
- [x] `SaveIO`：公开 `read/write` 供网络世界流复用；修复多格核心卫星瓦片序列化（`Tile.blockRaw` + BlockPart 注册）。
- [x] `ServerControl` 命令：host / stopserver / maps / exportmap / load / save / saves / loadsave / delete / say / runwave / status / version / help。
- [x] 地图切换：`nextmap` 命令 + 终局后自动选下一张；切图清理单位/子弹/特效/时间并触发 WorldLoad。
- [x] 验收：clean 环境 save→saves→loadsave→status；exportmap→maps→load→status；`:server:classes` 通过。✅
- [ ] 已知遗留：地图仍复用 Phoenix SaveIO，不兼容原版 MapIO/Map 格式；终局自动轮换需真实联网/战斗场景验证；控制台中文编码。

### 阶段 5：Administration 与插件
- [x] `Administration`：玩家档案（uuid→名字/IP/管理/封禁/踢次）、IP 封禁、白名单、人数上限、按名查询；持久化 `admins/{players.dat,banned.txt,whitelist.txt}`。
- [x] `mod/` 包：`Mod` / `Plugin` / `Mods`（扫描 mods 目录 jar/zip，按 mod.json/plugin.json 的 main 类用独立 URLClassLoader 加载并实例化）。
- [x] `mod/CommandHandler`：命令抽象，插件通过 registerServerCommands 注册，服务端合入控制台。
- [x] ServerControl 命令：kick / players / ban / unban / bans / admin / admins / whitelist / whitelist-add / playerlimit。
- [x] 验收：playerlimit/whitelist/bans/admins/players/help 运行正常；admins 目录持久化生成；`:server:classes` 通过。✅
- [ ] 已知遗留：脚本（js）与动态内容加载后置；插件命令仅服务端控制台（客户端聊天命令未接）；antiSpam 聊天/动作过滤未实现。

### 阶段 6：多人联机（phoenix 自有协议，不兼容原版 Mindustry）
> 方向修正（2026-09-18）：phoenix 定位为新游戏，玩法与原版不同，**不做原版协议兼容**。
> 原计划的「LAN 原版格式发现 / arc.net 线路兼容 / 原版客户端验收」取消；改为 phoenix 自有协议下的完整联机体验。
- [x] **6a：phoenix 客户端 ↔ phoenix 服务器联机**
  - [x] NetClient：握手、WorldStream 重建世界、SpawnPlayer 本地单位、输入上报（UDP）、远端代理单位（isRemoteProxy）、聊天/被踢/加载回调。
  - [x] MenuFragment「加入服务器」对话框 + Dialog hit 区域错位修复（show 后 pack + 手动居中）。
  - [x] ClientLauncher 渲染循环接入 netClient.update()。
  - [x] 双进程人工验证：desktop 客户端连 headless 服务器，握手→世界下发→单位生成→HUD 同步全通。✅
- [x] **6b：完整玩法同步**（自有协议扩展）
  - [x] 新包：BuildRequest / DeconstructRequest / BlockState / BlockRemove / EntitySnapshot / ShootWeapon / WorldState（Registrator 已注册）。
  - [x] NetServer：建造/拆除请求权威处理（校验+扣材料+广播）、单位快照、WorldState 波次同步。
  - [x] NetClient：BlockState/BlockRemove 应用（跳过已一致瓦片）、WorldState 应用、sendBuild/sendDeconstruct。
  - [x] DesktopInput/InputHandler：联机时 placeWorld/breakWorld/flushLine 改发请求（本地不执行，防分叉）。
  - [x] ChatFragment：Enter 呼出输入框、消息列表（8条滚动）、system 消息；ClientLauncher 惰性挂 onChat/onKick。
  - [x] MenuFragment：onKick → control.menu() 回菜单。
  - [x] 双进程人工验证：聊天双端闭环、加入/离开/重复加入、世界下发。✅
  - [x] **架构改造：客户端跑完整仿真（照原版 Mindustry，2026-09-19）**
    - 对照原版源码确认「客户端跑完整仿真」的**准确含义**：
      - `Logic.update()` 里 `unitGroup/bulletGroup/tileGroup/playerGroup.update()` 与 `collideGroups` **无 net 守卫**，
        客户端也跑 → **建筑（炮塔/钻头/传送带）两端都仿真**，各自 `Bullet.create`，炮塔子弹**不广播**
      - `BaseUnit.update()` 在 `net.client()` 时只 `interpolate(); status.update(); return;`
        → **单位 AI 不在客户端跑**（单位仍服务端权威 + 客户端插值）
      - `Weapon.shoot()` 才是开火事件的挂载层（`Weapon.java:160-171`）：服务端广播
        `Call.onGenericShootWeapon`/`onPlayerShootWeapon`，客户端 `shootDirect` 本地生成，射手自己的客户端跳过
      - 服务端专属：`state.enemies` 统计、`runWave()`、`checkGameOver()`（`Logic.java:214/227/262`）
      - 客户端伤害空操作（`Unit.damage()` 里 `if(!net.client())`）；建筑伤害同样只在服务端结算
        （`TileEntity.damage()` 里的 `Call.onTileDamage` 是 `called = Loc.server`，客机上直接 no-op），
        客户端靠服务端广播的 `onTileDamage`（UDP）/ `onTileDestroyed`（TCP）覆盖本地血量与移除
    - phoenix 落地：
      - 新增 `Vars.isClient()` / `Vars.isServer()`（对应原版 `net.client()` / `net.server()`；本机 hosting 时 `isClient()` 为 false）
      - `Logic.update()` 删除客户端早退与 `updateClient()`；`saves` / `countEnemies` / `updateWaves` / `checkGameOver`
        加 `!isClient()` 守卫；`world.updateTiles()` / 单位 / 子弹 / 碰撞两端都跑
        → **炮塔在客户端本地开火，因此不再需要同步子弹**
      - 开火事件从 `Bullet.create` 挪到 `Weapon.shoot()`（新包 `ShootWeapon`）。原先挂在 `Bullet.create` 上是**层级错误**：
        客户端一旦本地跑炮塔，就会「广播 + 本地各生成一次」→ 子弹重复
      - `InputHandler.updateShooting()` 去掉 `!online` 守卫 → 本地玩家本地开火（视觉即时）；
        服务端仍按 PlayerInput 权威开火，其广播在射手自己的客户端被跳过（避免重复）
      - `BaseUnit.damage()` 在 `isClient()` 时空操作（对应原版 `Unit.damage()`），单位血量由服务端快照权威
  - [x] **单位同步：补上敌方单位 + 按字节预算分片**（对照原版 `NetServer.writeEntitySnapshot`）
    - 原版遍历**所有实体组**（不只玩家），按 `maxSnapshotSize = 430` 字节分片批量发；`isSyncing()` 从未被覆写
      （此版本无距离裁剪）。
    - phoenix 原先只同步玩家单位 → **敌人不可见**，而敌人的子弹照常同步，表现为「子弹凭空出现在空地上」
      （实测第 10 波 220 敌军时满屏无源弹幕，HUD 显示敌军 116 但画面一个敌人都没有）。
    - 改为 `EntitySnapshot`（一包多单位、含 team，22 字节/项，`MAX_ENTRIES = 430/22 = 19`），遍历 `Units.units`；
      单位移除用「上帧同步集合 − 本帧集合」差集走 TCP（丢一个包会残留幽灵单位）。`EntityState`/`EntityTeam` 已删除。
    - 客户端防重：单位快照（UDP）可能先于 `SpawnPlayer`（TCP）到达，此时自己的单位已被当成远端代理创建过 ——
      `SpawnPlayer` 处理里先按 id 移除既有代理再建本地单位。
  - [x] **客户端单位插值**：新增 `net/Interpolator`（对应原版 `mindustry.net.Interpolator`），保存 last/target 与
        实测快照间隔，每帧按 alpha（上限 2，允许外推）推进显示位置，消除 12 tick 同步间隔造成的跳帧。
  - [x] 开火：本地玩家本地开火（视觉即时）+ 服务端按 PlayerInput 权威开火；服务端广播 `ShootWeapon` 开火事件，
        射手自己的客户端跳过（对应原版 `onPlayerShootWeapon` 的 `player == Vars.player` 判断）。
  - [x] 实测（2026-09-19）：联机 6 分钟零异常；炮塔在客户端本地开火且子弹无重复；敌人可见、波次与敌兵数与服务端一致；
        单机战役行为不变（`isClient()` 为 false，等价于改造前的完整仿真路径）。✅
  - [x] **建筑血量纠偏：实现 `onTileDamage` / `onTileDestroyed`**（2026-09-19）
    - 对照原版确认语义：`TileEntity.damage()` 里 `Call.onTileDamage(tile, health - block.handleDamage(tile, damage))`
      是 `@Remote(called = Loc.server)` —— 客机上**整段 no-op**（不执行、也不上报），
      所以建筑扣血**只发生在服务端**，客户端血量完全由广播驱动。原版传输：`onTileDamage` 走 UDP，
      `onTileDestroyed` 走 TCP。
    - phoenix 落地：
      - 新包 `TileDamage{x, y, health}`（Registrator 末尾追加，ID 不变动已有项）。
      - `TileEntity.handleDamage()`：`isClient()` 直接 return（不再本地扣血，与 `BaseUnit.damage()` 对称）；
        服务端扣血后广播 `TileDamage`；血量归零则先 `broadcastBlockRemove(tile)` 再 `kill()`
        （原先「被打爆」这条路径**根本没广播移除**，只是因为客户端自己也会把血扣到 0 才没暴露）。
      - `NetClient` 收到 `TileDamage` → `tile.entity.health(health)` 覆盖。
      - `NetServer.broadcastBlockRemove` 由 private 改 public，供 `TileEntity` 复用（不复制逻辑）。
    - **传输选择 TCP（偏离原版 UDP）**：UDP 无序，连续两次命中乱序到达会让客户端停在较高的旧血量；
      UDP 丢包则永久偏高 —— 而 phoenix 没有原版「每 8 秒全量 `BlockSnapshot`」兜底
      （6b 已决定保留增量 `BlockState`）。与 `EntityRemove`/`BlockRemove` 的既有偏离理由一致。
    - 副作用：客机上「自己子弹命中敌方建筑」的血条要等一个往返才动（LAN 下 <1ms）；火花特效仍在本地即时播放。
  - [ ] 待办：建造/拆除显式双端截图验证；单位位置无丢失补偿（UDP 丢包时靠插值外推）。
  - [x] **修复多格建筑受击判定**（2026-09-19，对照原版 `World.ltile`）
    - 原版：`World.ltile(x, y)` = `tile.block().linked(tile)` —— **取瓦片时就解析多格链接**，卫星瓦片一律返回中心瓦片；
      子弹射线回调拿到的 `tile` 因此永远是有实体的那一格。
    - phoenix 的 `World.ltile` 之前只是 `return tile(x, y)`（注释还写着"多格建筑用同一实现"），
      而卫星瓦片（`BlockPart`）没有实体（`entity == null`）→ `Bullet.update()` 的
      `if(tile.entity != null && ...)` 判空失败 → **多格建筑只有锚点那一格吃伤害**。
      核心（3x3，2000 HP）实际只有正中那一格能被击中，即只有沿中心行/列飞来的子弹有效。
      实测症状：旧代码下核心能顶着十几波（第 16 波才被打爆）几乎不掉血。
    - 改法只有一处：`World.ltile` 改为 `tile == null ? null : tile.link()`（`Tile.link()` 等价于原版的
      `block().linked(tile)`；`Block.linked` 默认返回自身，`BlockPart` 覆写为返回中心）。
      `ltile` 的唯一调用方就是 `Bullet.update()`，无其它副作用面。
    - 顺带确认：拆除（`Build.deconstruct` → `canDeconstruct` 用 `tile.block()`，已解析链接）、
      血条绘制（`Renderer` 里 `tile.link() != tile` 跳过卫星）本来就正确，只有子弹这一条路径漏了。
    - **A/B 对照实测**（headless 服务端，控制台 `runwave` 连开 6 波，场上 76 敌军，观察 2.5 分钟）：
      - A 组（临时回退 `ltile`）：建筑实体从 1625 被打到 **剩 2 个**（整个基地夷平），
        但**始终没有触发胜负判定** —— 核心依然活着。✅ 复现 bug
      - B 组（修复版）：同一条件，**2 分钟内** 服务端打出「胜负判定：crux 获胜（核心被全部摧毁）」，地图自动重开。
      - 差异只来自 `World.ltile` 那一行。
    - 附带排查（2026-09-19）：曾怀疑「服务端终局重开地图后客户端相机缩放变了」，**实测证伪** ——
      在 `Renderer.update()` 挂 `PHOENIX_DEBUG_CAM=1` 探针，跨越一次完整的「胜负判定 → 重开地图」，
      48 次采样全部是 `target=4.0 cur=4.0 zoom=0.25 pos=(479,647) vp=1920x1009`，相机没有任何变化。
      当时的异常画面是**截图工具的假象**：`tools/hud_shot.py` 用 `SetForegroundWindow`（IDE 抢占时会被拒绝），
      窗口没真正到前台时 `PrintWindow` 会返回过期/空白的 GL 后备缓冲（已改为 `SwitchToThisWindow`，与 win_click.py 一致）。
    - **修复地图重开不同步客户端**（2026-09-19）：
      - 根因：`rotateMap()` 在无可用地图时直接 `startGame()`，**完全没有通知已连接的客户端** ——
        不重发 `WorldStream`、不重发 `SpawnPlayer`（`sendWorldData(con)` 只有 `onConnect` 会调；
        `afterWorldLoad()` 的注释写着「网络重发」但实际也没有）。
        实测：重开后服务端 `瓦片实体数 1625 / 单位 0`，客户端仍是 `ents=449`（被打烂的旧世界）+
        `myid=1592`（服务端已不存在的孤儿单位）—— 客户端之后收到的所有快照都指向一个它没有的世界。
      - 服务端：把 `onConnect` 里「生成玩家单位 → `sendWorldData` → 发 `SpawnPlayer`」抽成
        `NetServer.respawnPlayer(RemotePlayer)`（`RemotePlayer.unit` 由 final 改为可重指派），
        新增 `resendWorldToAll()` = 清 `syncedUnits` + 对每个在线玩家 `respawnPlayer`；
        在 `startGame()` 与 `afterWorldLoad()` 收尾各调一次（0 名玩家时是 no-op）。
        必须清 `syncedUnits`：否则新世界第一帧会被当成「上一帧同步过、本帧不在集合里」，把所有客户端单位误判为已移除。
      - 客户端：收到 `WorldStream` 时先把客户端侧的世界相关状态清干净（`remoteUnits`、`playerUnitId`、
        `Bullet.all`、`Effects`；`Units.units` 由 `SaveIO.read` 负责），并补发 `WorldLoadEvent`
        重建寻路流场网格（换到不同尺寸的地图时旧网格会越界）。
      - 实测（2026-09-19，headless 服务端 + desktop 客户端，`runwave` ×8 触发终局重开，连续两轮）：
        服务端每轮打出「世界已重发，重建 1 名玩家的单位」；客户端「世界已加载」与「获得玩家单位」各 1 → 2 → 3，
        单位 id 从 1625 换成 8690；两端 0 异常。✅
  - [x] **小地图照搬原版**（2026-09-19，对照 `MinimapRenderer` / `MapIO.colorFor` / `ui.Minimap` / `ui.fragments.MinimapFragment`）
    - 修前的三个症状（截图实测）：整张图布满**纯白色斑块**、只有本地玩家一个白方块、点击小地图无任何反应。
    - 根因：
      1. `Block.color` 声明后**从未赋值**（只有 `Blocks.floor(...)` 给 `floor.color` 赋过值），
         而原版是打包期在 `Block.createIcons()` 里取图标中心像素当小地图色（`color.set(image.getPixel(w/2, h/2))`），
         phoenix 没有打包管线 → 所有建筑都是 `Color.WHITE`。
      2. 只画 `Vars.player.unit()`，不画其他单位。
      3. `MinimapActor` 没挂监听器，且**全屏小地图整个不存在**。
    - phoenix 落地（尽量逐行对齐原版）：
      - `graphics/MinimapRenderer`：`Pixmap`(1px/格) + `Texture`，`WorldLoadEvent` → `reset()+updateAll()`，
        `TileChangeEvent` → `update(tile)`；`getRegion()`（相机为中心的窗口）、`updateUnitArray()`（`Units.nearby`）、
        `drawEntities(x,y,w,h,scaling,withLabels)`、`drawLabel()`、`zoomBy/setZoom/getZoom/getPixmap/dispose`。
      - `io/MapIO.colorFor(floor, wall, ore, team)`：`synthetic()` → 阵营色；否则 `solid ? wall.color : ore == air ? floor.color : ore.color`。
        `Block.synthetic()`（= `update || destructible`）项目里本来就有。
      - `Block.loadMinimapColor(Pixmap)` + `Blocks.loadMinimapColors()`：按需解码图集分页 PNG，取 region 中心像素写入 `Block.color`。
      - `ui/Minimap`（HUD 右上角，原版 `mindustry.ui.Minimap`）：悬停滚轮缩放、点击 `minimapFragment().toggle()`、
        `ClickListener.tapSquareSize = Scl.scl(11f)` 且拖动超容差不算点击。
      - `ui/fragments/MinimapFragment`（全屏）：`baseSize = Scl.scl(5f)`、`size = baseSize*zoom*world.width`、
        黑底 + 整图 + 单位；滚轮 `zoom = clamp(zoom - amountY/10f*zoom, 0.25f, 10f)`、拖拽 `panx += deltaX/zoom`；
        标题/返回按钮用 bundle 的 `minimap` / `back`（原版 `$minimap` / `$back`）。
      - `UnitType.icon(Cicon)` + `BaseUnit.getIconRegion()`：原版 `unit-<name>-<尺寸>` → `unit-<name>-full` →
        `unit-<name>` → `<name>` 依次回退（phoenix 没有打包管线，实际落到本体贴图）。
      - `DesktopInput` 里 M 键开关（对应原版 `Binding.minimap`）；全屏打开时让出输入（见下）。
    - **libgdx 适配（与原版的差异，均已在代码注释标注）**：
      - 单像素上传：原版 `Pixmaps.drawPixel` 直接写纹理，libgdx 的 `Texture` 只有整张上传的 `draw(Pixmap, x, y)`，
        改用脏标记把同一帧的多次瓦片变化合并成一次上传（战斗中一帧可能拆几十个方块）。
      - 单位染色：原版 `Draw.mixcol(team.color, 1f)`（arc），phoenix 的 `Draw` 没有 `mixcol`，用 `batch.setColor` 做乘法染色。
      - 手势：原版 `ElementGestureListener` 的 pinch 缩放没有 libgdx 等价物，只保留滚轮缩放 + 拖拽平移（桌面端本就够用）。
      - 滚轮焦点：libgdx 的 `Stage.scrolled` 只发给 `scrollFocus`（不做 hit 判定），
        所以 `Minimap`/`MinimapFragment` 必须显式 `stage.setScrollFocus(...)`，否则滚轮会漏给 `Vars.control.input` 去缩放世界相机。
      - 全屏地图打开时 `DesktopInput.update()` 直接早退（丢弃累积滚轮量 + 停火）：
        本类是轮询式（`Gdx.input.isButtonPressed`），光靠 Stage 消费事件拦不住，不加会让拖拽平移地图变成建造/开火。
      - 原版 `colorFor` 里按 `block.cacheLayer == CacheLayer.walls` 用朝向压暗，phoenix 没有 CacheLayer，省略。
    - 实测（2026-09-19，desktop 客户端 1920x1009，游戏暂停后逐项验证）：
      - 客户端日志 `[小地图] 已采样 14 个方块颜色`（= `Blocks.all` 里非 Floor 的方块数）。
      - HUD 小地图：地形 + 自然岩壁（derelict 深灰）+ 双方核心（sharded 金 / crux 红）+ 单位图标（按阵营染色）。
      - 点击小地图 / M 键 → 全屏地图打开（标题「小地图」+「返回」按钮）；Esc、M、返回按钮均可关闭。
      - 全屏地图滚轮上滚放大、下滚缩小（0.95×600px 实测吻合），左键拖拽平移跟随鼠标。
      - 输入隔离：在 HUD 小地图上滚轮 3 格后**世界区域像素差异 = 0.00000**；在 HUD 小地图上拖拽平移地图后
        世界画面无变化（没有误建造/误开火）；关闭地图后世界相机与 HUD 小地图内容与打开前**逐像素一致**。✅
      - headless 服务端：`[小地图] 已采样 0 个方块颜色`（空图集天然跳过）、无异常。✅
  - [x] **自然岩壁换成原版的 StaticWall**（2026-09-19，对照 `world/blocks/Rock` / `StaticWall`）
    - 起因：小地图照搬 `MapIO.colorFor` 后，采样出来的图标色**没有生效对象** —— 生成器把
      `metalWall`/`copperWall`（`destructible = true` → `synthetic()` 为真）当自然岩壁，小地图走阵营色
      （未设队伍 = derelict 深灰）。原版的自然岩壁是 `stoneWall`/`rocks` 这类**非 destructible** 方块。
    - phoenix 落地：
      - 新 `world/blocks/Rock`（`breakable = true; alwaysReplace = true`，`destructible` 保持 false；
        `variants` + `draw(tile)` 按坐标确定性挑变体 + `load()` 取 `name1..nameN` + `generateIcons()` 回退到 `name1`）。
      - 新 `world/blocks/StaticWall extends Rock`（`breakable = alwaysReplace = false; solid = true; variants = 2`；
        4 格同种岩壁相邻时画 2x2 的 `-large` 大贴图）。
      - `math/Mathf` 补 arc 的 `randomSeed(long)` / `randomSeed(long,int,int)`（用 `RandomXS128` 实现，序列与原版不同但同样是确定性）。
      - `Blocks` 末尾注册 `rocks / sandrocks / dunerocks / shalerocks / shrubs / dark-metal`（**追加在 `all` 末尾**，
        保证已有方块的存档 ID 不错位）；`World.createMap` 改成「边界用 `darkMetal`、散布岩壁按所在地表挑种类」。
      - `PlacementFragment.buildable` 排除 `Rock`：原版靠 `buildVisibility` + 解锁状态过滤，phoenix 两者都没有，按类型过滤。
      - `Block.loadMinimapColor` 的取样对象从 `region` 改成 `icon(Cicon.full)`（与 `Block.createIcons` 一致）——
        否则岩壁的裸名 `region` 会回退成 `blank`，采到透明色。
    - **arc/libgdx 差异（已离线逐张验证）**：原版写 `split[tile.x % 2][1 - tile.y % 2]`，
      但 libgdx 的 `TextureRegion.split` 是 `[row][col]`（row 0 = 图像顶行），照抄会把四象限摆错。
      用 Python 对全部 7 张 `*-large.png` 做「按映射拼回原图」比对，只有 `split[1 - tile.y % 2][tile.x % 2]` 能复原 → 采用后者。
    - 实测（2026-09-19，desktop 客户端）：`[小地图] 已采样 20 个方块颜色`（14 → 20，新增 6 个自然岩壁）；
      小地图上岩壁按地表呈灰/棕（图标采样色）而非 derelict 深灰；岩壁在世界里是实心、挡子弹、不可拆（`destructible/breakable` 均 false
      → 无实体、`canDeconstruct` 为假）；headless 服务端 0 采样、无异常。✅
  - [x] **修复「胜负判定后回菜单是空白蓝屏」**（2026-09-19）
    - 根因：`Logic.checkGameOver()` → `Vars.control.menu()` 只改状态 + 发 `ResetEvent`，
      **没有任何地方调 `menufrag.show()`**；而 `MenuFragment` 的显隐是手工 `setVisible` 的
      （`ClientLauncher` 只在被踢/断线时才 `menufrag.show()`），于是停在一片清屏色上。
    - 改法：菜单显隐改为**状态驱动** —— `MenuFragment.build` 里挂 `Act(() -> group.setVisible(Vars.state.isMenu()))`，
      对应原版 `UI` 的 `menuGroup.visible(() -> state.is(State.menu))`。`show()/hide()` 保留（下一帧会被状态覆盖，等价）。
    - 实测：`runwave` 打到「胜负判定：crux 获胜」后**主菜单正常出现**（Mindustry logo + 开始游戏/编辑器/设置/关于/退出），
      点「开始游戏 → 战役模式」能正常开新局，小地图随新世界重建。✅
- [x] **6c：联机体验完善**——已完成：
  - [x] LAN 发现（phoenix 自有协议）：`net/Discovery` 组播 227.2.7.7:20151，`PHOENIX1\t名称\t端口\t人数\t上限\t地图`，实测 Python 探测收到回复。✅
  - [x] 加入对话框内嵌 LAN 扫描列表（点击填入地址）。
  - [x] NetClient.reconnect() + 上次连接参数记忆。
  - [x] 服务器控制台 UTF-8 编码修复（build.gradle jvmArgs）。
  - [x] 心跳 / 超时 / 自动重连：`Packets.Ping`/`Pong`（Registrator 已注册），服务端收到 Ping 原样回 Pong；
        NetClient 每帧累加 tickCounter，收包刷新 lastReceiveTick，每 1 秒发 Ping，8 秒无收包 → handleLost →
        每 5 秒自动重连；`getPing()` 暴露 RTT，`autoReconnect` 开关，被踢时关闭自动重连。
  - [x] 客户端聊天命令（ChatFragment）：内置 `/help` `/ping` `/disconnect` `/reconnect`，非内置命令转
        `Mods.eachClass(registerClientCommands)` 插件处理。
  - [x] 回归修复：ClientLauncher.render() 原用 `isConnected()` 守卫 `netClient.update()` → 断线后自动重连
        永不触发，改为无条件调用（chatfrag.update() 仍按连接状态守卫）；挂 onDisconnect → 回菜单。
  - [x] 验收：`:core:compileJava` / `:server:classes` / `:desktop:classes` 编译通过。✅
  - [x] 双进程实测（2026-09-19）：desktop 客户端加入 headless 服务端 → 强杀服务端进程（模拟崩溃，非
        `stopserver`，否则走 Kick 分支会关掉 autoReconnect）→ 客户端 8 秒超时 → 回菜单 → 自动重连重试 →
        重启服务端后自动重连成功、世界重载、菜单自动隐藏、回到游戏。✅
  - [x] 修复重试间隔：`Net.connect()` 改为返回 boolean，连接失败时同步回滚 `connecting`，重试间隔恢复为
        配置的 5 秒（原先失败只打印不回滚，要空等满 `connectTimeoutTicks` 6 秒，实际约 11 秒一轮）；
        `connectTimeoutTicks` 保留为 provider 既不回调也不抛异常时的兜底。
  - [x] 修复断线刷屏：`PhoenixNetProvider.sendClient` 增加 `isClosed()` 判断（TCP + UDP），
        断线到超时判定之间不再刷 `Socket closed`。
  - [x] 修复重连间隔不准：根因是 **`Time.delta` 字段永远停在初始值 `1f`** —— 它只在 `Time.updateGlobal()`
        里赋值，而该方法全工程无人调用（每帧被调用的是 `Time.update()`，只读不写）。于是 `tickCounter` 每帧 +1，
        每秒 tick 数 = 帧率，300 tick 的 `reconnectInterval` 在高帧率下被压缩。改为统一使用 `Time.delta()`
        （`deltaimpl`，取真实帧间隔；游戏里绝大多数代码用的就是这个形式）。
        实测：修复前 30 秒窗口 10 次（3.0 秒/次），修复后 **30 秒窗口 6 次 = 精确 5.0 秒/次**。
        ⚠ 测量方法：必须在**单条命令内**对日志文件做固定窗口取样计数；靠两次人工调用之间的间隔计时会被工具延迟污染。
  - [x] 修复 `Time.delta` 字段失效（根因 + 全部误用点）：对照 arc 源码确认 —— **arc 的 `Time` 根本没有 `delta`
        字段**，`delta` 只是 `update()` 里的局部变量，唯一公开读取方式是 `Time.delta()` 方法
        （`deltaimpl = min(Core.graphics.getDeltaTime()*60f, 3f)`）；`updateGlobal()` 只推进 `globalTime`。
        原版 Mindustry 126.2 中 `Time.delta`（字段形式）出现 **0 次**、`Time.delta()` 出现 **126 次**。
        phoenix 的 `public static float delta` 是移植残留、恒为初始值 `1f`，读它的地方全部失效。
        已删除该字段；`Time.update()` 改用局部 `delta()` 并透传给 `updateRuns()`；5 处误用点改为 `Time.delta()`：
        `Conveyor`(maxmove) / `Drill`(progress) / `GenericCrafter`(progress) / `Saves`(autosave 计时) /
        `BaseUnit.rotate`(转向插值)。原版对应实现是 `TileEntity.delta() = Time.delta() * timeScale`，
        phoenix 实体无 timeScale，故直接用 `Time.delta()`。
        这是**还原原版行为**而非改平衡：修复前传送带/钻头/合成器按 delta=1 推进（高帧率下偏快约 1.5 倍），
        单位转向因 `clamp(rotatespeed*1, 0, 1)` 被钳到 1 而瞬间贴向目标。
  - [ ] 待办：`Control` 未调用 `Time.updateGlobal()`（原版 `Control.java:420` 有调用），故 `globalTime` 恒为 0；
        当前全工程无 `globalTime` 读取方，无实际影响，待有需求时补上。
  - [ ] 待办：`NetClient.inputTimer` 字段声明后从未使用（死代码），可删。
  - [x] 自动化验证钩子：环境变量 `PHOENIX_JOIN=ip[:port]` 跳过菜单直接加入服务器（新增
        `MenuFragment.joinServer`，与加入对话框共用 `connectTo`，其 UI 反馈参数改为可空）。已记入 CODEBUDDY.md。
        使联机链路（加入 → 杀服务端 → 超时 → 自动重连 → 恢复）可以完全自动化验证，无需人工点击。✅
  - [ ] 待办：延迟显示接入 HUD（当前仅 `/ping` 聊天命令）。

## 技术决策

- **传输**：Java NIO 自实现 NetProvider，不引入 Netty/新依赖。
- **线程**：网络线程只读写；主线程改世界；线程安全队列投递。
- **存档**：阶段 1 先最小格式；阶段 4 再对齐原版 `.msav`。
- **代码组织**：`mindustry.server.*` → `com.phoenix.game.server.*`；服务端代码独立 `server` 模块，不进 desktop。
