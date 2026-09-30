# G6 批次报告 — 设置项消费接线（6 项）

范围：把「全量移植收尾·第二轮」审计里 A6 批列出的 6 处「设置项/逻辑无消费点」接线落地。
铁律：判据/顺序/字段名照原版；凡偏离必写注释；不跑 gradle/git。

> ⚠ 本批次**未修改**用户并行 WIP 的三处区域：
> `entities/type/Player.java`、`input/InputHandler.java` 的 `canTapPlayer/tryDropItems/transferInventory`（约 227-280 行）、
> `io/SaveIO.java`，以及 `ClientLauncher.java` 的 `//DEBUG-MINE-START` 钩子。
> G6-3 只落在 `InputHandler.updateLine`（666 行起）这一带，与该 WIP 不重叠。

---

## G6-1 effects 开关（A6-B1）

- **原版位置**：`core/Renderer.java:56-91` —— `Effects.setEffectProvider(...)` 的 provider 体内
  `if(Core.settings.getBool("effects")){ ... 生成 EffectEntity / GroundEffectEntity ... }`（第 58 行）。
  即「关特效 = 不生成特效实体」，但 **`Effects.shake` 走的是 `setScreenShakeProvider`，不受该开关影响**。
- **本项目改动**：`core/src/com/phoenix/game/entities/Effects.java:135-145`
  在 6 参核心方法 `effect(Effect, Color, float, float, float, Object)` 开头加
  `if(!Core.settings.getBool("effects", true)) return;`。
- **照抄/偏离**：
  - 本项目**没有 effectProvider**（`Effects` 是自维护 `EffectState` 列表的极简实现；`entities/type/EffectEntity.java` 类注释已声明其"可编译、未接线"）。
    provider 是原版唯一的特效生成入口 → 等价落点就是所有 `effect(...)` 重载汇聚的 6 参方法。
  - **刻意只守"特效生成"**：守卫写在 `Effects.effect` 内、不写进 `Effects.shake`，与原版"只守实体生成"语义一致（注释已声明）。
  - **显式默认值 `true`**（原版写 `getBool("effects")`）：本项目设置默认值由 `SettingsTable.checkPref` 在 **UI 初始化时**登记，
    若在此之前已有特效生成，单参 `getBool` 会拿到 `false` 而静默关特效 → 传 `true` 规避该启动窗口（注释已声明）。
  - 顺带把 `SettingsMenuDialog.java` 里 "消费方不存在" 的过时注释改成 "已接线"。
- **遗留风险**：低。守卫是每帧多次的 `Preferences.getBoolean`（原版同款做法）。若将来把 `EffectEntity` 接线成真正的实体系统，守卫需同步搬到那里。

---

## G6-2 minimap 开关（A6-B2）

- **原版位置**：`ui/fragments/HudFragment.java:256-265`
  `t.visible(() -> Core.settings.getBool("minimap") && !state.rules.tutorial)`（第 257 行），
  且 **坐标显示 label 与 Minimap 在同一张表 `t` 上** → 关小地图时坐标一并隐藏。
- **本项目改动**：`core/src/com/phoenix/game/ui/fragments/HudFragment.java`
  - 新增字段 `private Table minimapTable;`（第 43 行）；
  - 原 `Table minimapTable = new Table();` 改为给字段赋值（第 205 行）；
  - 新增每帧 `MenuFragment.Act`：`minimapTable.setVisible(getBool("minimap", !Vars.mobile) && rules != null && !rules.tutorial)`（第 425-434 行）。
- **照抄/偏离**：
  - **键名确认**：设置项实际写入的键就是 `"minimap"`（`SettingsMenuDialog.java:265` `graphics.checkPref("minimap", !Vars.mobile)`），与原版同名 → 无需换键。
  - 判据照原版 `getBool("minimap") && !rules.tutorial`；因本项目设置默认值登记晚于 UI 构造，**显式传默认 `!Vars.mobile`**（与登记默认值一致，注释已声明）。
  - 原版 `t.visible` 是惰性钩子；本项目 libgdx `Table` 无 `visible(Prov)` → 用既有 `MenuFragment.Act` 每帧刷（与文件内 fps/position/boss 等既有做法一致）。
  - 坐标 label 与小地图同表 → 关小地图时坐标一并隐藏，与原版一致（注释已声明）。
- **遗留风险**：极低。`Vars.state`/`rules` 为 null（菜单）时整表隐藏，符合预期。

---

## G6-3 blockreplace 应用（A6-B3 后半）

- **原版位置**：`input/InputHandler.java:538-555` 的 `updateLine(x1,y1,x2,y2)` 末尾：
  ```java
  if(Core.settings.getBool("blockreplace")){
      lineRequests.each(req -> {
          Block replace = req.block.getReplacement(req, lineRequests);
          if(replace.unlockedCur()){ req.block = replace; }
      });
  }
  ```
  `Block.getReplacement` 默认返回自身；`Conveyor`/`Conduit` 覆写为"十字路口自动换成交叉器"。
  设置项语义（bundle `setting.blockreplace.name = 自动推荐合适的建筑`）确认就是"自动推荐建筑"，非"替换已有方块"。
- **本项目改动**：`core/src/com/phoenix/game/input/InputHandler.java`
  - `LineRequest` 增 `public Block block;` 字段（第 639-644 行）；
  - `updateLine(int,int,int,int)` 生成每格时 `req.block = block`（第 711 行），末尾追加 blockreplace 应用段（第 717-741 行）；
  - 新增两个复用字段 `replaceRequests`（`Array<BuildRequest>`）与 `replaceReqPool`（`BuildRequest[]`，下标对齐 `lines`），避免每帧分配（第 624-629 行）；
  - 消费方改用 `req.block`：`drawTop` 连线预览（第 429-443 行）、`flushLine` 三处发送/入队/即时放置（第 791/802/808 行）。
- **照抄/偏离**：
  - **结构性偏离（必要）**：本项目连线结果类型是 `LineRequest[]`（含 x/y/rotation），不是原版的 `Array<BuildRequest>`。
    为让 `getReplacement` 能按原版读 `req.tile()`/邻居 `o.x/o.y/o.rotation/o.block`，临时构造一份等价的 `BuildRequest` 列表
    （对象池复用，不每帧分配），再把结果写回 `LineRequest.block`（注释已声明）。
  - `unlockedCur()` 判据、`req.block = replace` 的写回语义、`blockreplace` 键（`getBool("blockreplace", true)`，登记默认值 true）均照原版。
  - 消费方 `drawTop` 的朝向箭头条件照原版 `last.block.rotate`（被换成 junction 后 `rotate=false` → 不画箭头）。
- **遗留风险**：中低。`replaceRequests` 只覆盖 `lineSize` 个下标，`clearLine()` 后 `lineSize=0` → 旧值不会被读；
  `LineRequest.block` 在每次 `updateLine` 生成时都会重置为当前 `buildBlock`，无脏值。多人路径（`sendBuild`）也带上替换后的方块，与服务端权威建造一致。

---

## G6-4 右键拆除模式（A6-D2）

- **原版位置**：`input/DesktopInput.java`
  - 第 384-389 行：`keyTap(deselect) && isPlacing()` → `block = null; mode = none;`
  - 第 390-396 行：`keyTap(break_block) && !scene.hasMouse()` → `deleting=false; mode=breaking; selectX/Y=光标格`
  - 第 416-438 行：`keyRelease(break_block) || keyRelease(select)` → 若 `mode==breaking` 调 `removeSelection(selectX,selectY,cursorX,cursorY)`
  - 第 290-292 行：`keyTap(deselect)` → `player.setMineTile(null)`（无条件）
  - （126.2 输入是**轮询式** `update()`，无 `touchDown/touchUp/dragged`；任务书里的"三段"在本项目对应事件式 `touchDown/touchUp` + 轮询 `update`。）
  - 注：原版 `Binding.deselect` 与 `Binding.break_block` **都绑右键**（`Binding.java:15-16`）。
- **本项目改动**：`core/src/com/phoenix/game/input/DesktopInput.java`
  - 新增字段 `private boolean rightBreaking;`（第 30-35 行）；
  - `touchDown` 的右键分支（第 342-364 行）：`isPlacing()` → `clearBuild()`；否则 `setMineTile(null)` + `breaking=true; rightBreaking=true` + 记起点格 + 先拆起点格；
  - `update` 拖拽连拆条件扩为 `isButtonPressed(select) || (rightBreaking && isButtonPressed(break_block))`（第 288-292 行）；
  - `touchUp` 增右键分支：拆到当前光标格 → `rightBreaking=false; breaking=false`（第 446-460 行）。
- **照抄/偏离**：
  - 照原版：右键在**非建造态**进入拆除；`isPlacing()` 时右键仍是取消（deselect）；`setMineTile(null)` 照抄；拆除判据 `!sceneHasMouse()` 照抄。
  - **刻意偏离 1（瞬态 vs 持久）**：原版 `mode=breaking` 是"按下进入、松开退出"的瞬态；本项目 `breaking` 还被 `PlacementFragment` 的"拆除"按钮用作**持久**模式。
    → 用 `rightBreaking` 标记"本次由右键发起"，松开只退出这一次，不影响按钮进入的持久模式（注释已声明）。
  - **刻意偏离 2（连拆形态）**：原版松开时按**矩形** `removeSelection` 拆；本项目复用既有"沿路径逐格拆"（`buildPath` + `breakWorld`），单击即拆一格。
    比矩形选择更贴合本项目既有的拖动拆除实现，避免再引入一套矩形遍历（注释已声明）。
  - **行为变化（有意）**：右键在非建造态不再调用 `clearBuild()`（因此不再顺带清 `configTile`/`droppingItem`/建造队列）—— 这正是任务要求的"`deselect` 只在 `isPlacing()` 时取消"；与原版 deselect 仅清 `mineTile` 一致。
- **遗留风险**：中。① `rightBreaking` 只在 `touchUp` 复位；若右键拖到窗口外松开（libgdx 仍会派发 touchUp）一般无碍，极端情况下 `breaking` 可能残留 → 用户再点一次右键（此时 `isPlacing()` 为真）即 `clearBuild()` 复位。② 未移植原版的矩形框选拆除预览（`drawBreakSelection`），预览仍是既有"单格红块"。

---

## G6-5 跳波 canSkipWave 门控（A6-D3）

- **原版位置**：`ui/fragments/HudFragment.java:673-688`
  ```java
  private boolean canSkipWave(){
      return state.rules.waves && ((net.server() || player.isAdmin) || !net.active())
          && state.enemies == 0 && !spawner.isSpawning() && !state.rules.tutorial;
  }
  // addPlayButton: 按钮 .visible(this::canSkipWave)；
  //   click: net.client() && player.isAdmin → Call.onAdminRequest(player, AdminAction.wave)
  //          else if(inLaunchWave()) → 确认框
  //          else → state.wavetime = 0f
  ```
- **本项目改动**：`core/src/com/phoenix/game/ui/fragments/HudFragment.java`
  - 新增字段 `private Button waveButton;`（第 33-38 行）；"下一波"按钮改为写该字段（第 115-127 行）；
  - 点击分支改为 `if(canSkipWave()) Vars.state.wavetime = 0f;`（第 118-125 行）；
  - 每帧显隐 `waveButton.setVisible(canSkipWave())`（第 368-371 行）；
  - 新增 `private boolean canSkipWave()`（第 468-486 行）。
- **照抄/偏离**：
  - 判据照原版：`rules.waves && (服务端/无网络) && enemies==0 && !spawner.isSpawning() && !rules.tutorial`。
  - **等价映射（注释已声明）**：`net.active()` → `Vars.isClient() || Vars.isServer()`；`net.server()` → `Vars.isServer()`。
  - **刻意偏离（客户端禁用）**：原版客户端管理员走 `Call.onAdminRequest(AdminAction.wave)`。
    本项目**有** `Packets.AdminAction.wave` 枚举值与 `NetClient.sendAdminRequest`，但服务端 `NetServer.onAdminRequest` **没有 wave 分支**，
    且其鉴权会 `reject 目标 == 自己`（`other == rp` 直接拒绝）→ 客户端路径会静默失效。
    按任务书 fallback「本项目没有可用 Call/AdminRequest 时退化为客户端禁用」→ `Vars.isClient()` 时恒 false（注释已声明，并写明恢复条件）。
  - 未接 `inLaunchWave()`（本项目无发射流程，`state.launched` 恒 false）→ 略，注释已声明。
- **遗留风险**：低。单机/服务端（房主）跳波照常；客户端跳波**明确禁用**而非静默失效。若要恢复客户端跳波，需补 `NetServer.onAdminRequest` 的 wave 分支 + 放行自身目标。

---

## G6-6 死亡相机跟随核心（A6-D1）

- **原版位置**：`core/Renderer.java:122-135`
  ```java
  if(player.isDead()){
      TileEntity core = player.getClosestCore();
      if(core != null){
          if(player.spawner == null) camera.position.lerpDelta(core.x, core.y, 0.08f);
          else camera.position.lerpDelta(position, 0.08f);
      }
  }else if(control.input instanceof DesktopInput && !state.isPaused()){
      camera.position.lerpDelta(position, 0.08f);
  }
  ```
- **本项目改动**：`core/src/com/phoenix/game/core/Renderer.java:150-171`
  把原"仅存活分支"拆成 `isDead()` / 存活两分支；死亡分支取 `Vars.player.getClosestCore()` 并 lerp 到核心中心。
- **照抄/偏离**：
  - 照原版：死亡 → 跟随最近核心，lerp 系数 `0.08f`。
  - **偏离 1（坐标口径）**：本项目 `Player.getClosestCore()` 返回 `Tile`（非 `TileEntity`），且 `Tile.worldx()/worldy()` 是**左下角** →
    用 `block.centerX(core)/centerY(core)` 取几何中心（原版 `TileEntity.x/y` 即中心）。注释已声明。
  - **偏离 2（spawner 二选一）**：原版死亡分支有 `player.spawner == null` 的二选一；本项目 `Player` **没有** `spawner` 字段（无空投点实体）→ 恒走"跟随核心"。注释已声明。
  - 存活分支保持既有行为（`!isPaused() && DesktopInput`），并补了 `Vars.state != null` 守卫。
  - 本项目用 libgdx `Vector3.lerp(target, alpha)`（原版 `lerpDelta`）——与文件内既有写法一致。
- **遗留风险**：极低。核心被拆后 `getClosestCore()` 可能返回 null → 相机停在原地（原版同）。

---

## 改动文件清单

| 文件 | 项目 |
| --- | --- |
| `core/src/com/phoenix/game/entities/Effects.java` | G6-1 |
| `core/src/com/phoenix/game/ui/fragments/HudFragment.java` | G6-2、G6-5 |
| `core/src/com/phoenix/game/input/InputHandler.java` | G6-3 |
| `core/src/com/phoenix/game/input/DesktopInput.java` | G6-4 |
| `core/src/com/phoenix/game/core/Renderer.java` | G6-6 |
| `core/src/com/phoenix/game/ui/dialogs/SettingsMenuDialog.java` | G6-1/2/3 的过时注释订正（仅注释） |

## 额外发现（供主会话跟进）

1. **`NetServer.onAdminRequest` 缺 `AdminAction.wave` 分支，且拒绝"目标==自己"**：`Packets.AdminAction.wave` 是死枚举值。
   原版该动作只需 `state.wavetime = 0f`（无需目标玩家）。若要恢复客户端跳波，需在服务端补 wave 分支并放行自身目标。
2. **`Settings` 单参 `getBool` 的启动窗口**：设置默认值由 `SettingsTable.checkPref` 在 UI 初始化时登记；
   在此之前读取**默认值为 true** 的设置项（effects/minimap/…）会拿到 false。本批次已对新消费点显式传默认值规避；
   其他既有的 `getBool("bloom")` 一类调用未动（原版同款写法）。
3. **`Player.getClosestCore()` 返回 `Tile` 而非 `TileEntity`**：`Renderer.java:1184` 的旧注释仍写"本项目 Player 没有该方法"，已过时（方法在 `Player.java:334` 存在）→ 可顺手订正（本批次未改，避免超出 G6 范围）。
