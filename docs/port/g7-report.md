# G7 批次报告 — 战役"发射到下一区域"整链（审计 A6-S1）

范围：把「全量移植收尾·第二轮」审计里 **A6-S1（高）** 指出的「战役发射链缺失」补齐：
`Logic.launchZone()` + `checkGameOver` 的发射分支 + HUD 发射 UI。

铁律：流程/顺序/判据/文案键一律照原版；凡与原版不同处均写注释声明；不跑 gradle/git。

> ⚠ 本批次**未触碰**用户并行 WIP 的三处区域：
> `entities/type/Player.java`、`input/InputHandler.java`、`io/SaveIO.java`，
> 以及 `ClientLauncher.java` 的 `//DEBUG-MINE-START` 块。
> 本批次改动的三个文件（`core/Logic.java`、`core/Control.java`、`ui/fragments/HudFragment.java`）与上述 WIP 不重叠。

**改动文件清单**

| 文件 | 说明 |
| --- | --- |
| `core/src/com/phoenix/game/core/Logic.java` | G7-1 + G7-2（新增 `launchZone()` / `cancelLaunchTimer()` / 发射延时字段；改写 `checkGameOver` 的 attack 分支） |
| `core/src/com/phoenix/game/core/Control.java` | `menu()` 里 `Time.clear()` 旁补 `Logic.cancelLaunchTimer()`（1 行） |
| `core/src/com/phoenix/game/ui/fragments/HudFragment.java` | G7-3（`showLaunch` / `inLaunchWave` / `canLaunch` / `showLaunchConfirm` / 发射按钮 / 波次标签发射分支 / `addPlayButton` 的发射分支） |

---

## G7-1 `Logic.launchZone()`（原版 `core/Logic.java:172-200`）

- **原版位置**：`mindustry/core/Logic.java:172-200`（`@Remote(called = Loc.both) launchZone()`）。
- **本项目改动**：`core/src/com/phoenix/game/core/Logic.java:312-384`（新增），配套字段 `:20-33`，
  延时推进 `:170-179`，取消方法 `:386-394`。

### 照抄的部分（逐条）

1. `if(!headless) ui.hudfrag.showLaunch();` → 本项目 `if(Vars.hud != null) Vars.hud.showLaunch();`
   （本项目没有 headless 概念，`Vars.hud` 由 `ClientLauncher:190` 无条件赋值）。
2. 对 `state.teams.playerCores()` 每个核心 `Effects.effect(Fx.launch, tile)`。
   - **传参口径已核对**：原版 `TileEntity.init` 里 `x/y = tile.drawx()/drawy()`（**方块中心**）；
     本项目 `Tile.getX()/getY()`（`Tile.java:435-447`）就是 `drawx()/drawy()`（同样 = 方块中心，
     `drawx() = block().offset() + worldx()`）→ 直接传 `Tile` 等价，**无需换算**。
     （`playerCores()` 里存的都是锚点瓦片，`drawx()` 只有对锚点才等于中心。）
3. `if(world.getZone() != null) world.getZone().setLaunched();` —— 照抄。
   这一步让 `Objectives.Launched`（`game/Objectives.java:79-97`，读 `zone.hasLaunched()`）
   从"永不达成"变为可达成（`Zone.setLaunched()` 写 `Core.settings.put(name+"-launched", true)`）。
4. 延时 30 tick 后的收尾：遍历核心 → 逐物品 `data.addItem(item, amount)` + fire `LaunchItemEvent(item, amount)`
   → `tile.remove()`；然后 `state.launched = true; state.gameOver = true;` → fire `LaunchEvent`
   → fire `GameOverEvent(state.rules.defaultTeam)`。

### 偏离声明（均在代码注释里同步写明）

| # | 原版 | 本项目 | 原因 |
| --- | --- | --- | --- |
| 1 | `@Remote(called = Loc.both)` 网络 RPC | 普通 `public static void` | 本项目**没有 `Call` 层**（无 RPC 通道）；单机/主机直接调用。纯客机见 G7-2 |
| 2 | `if(!headless)` | `if(Vars.hud != null)` | 本项目无 headless 概念 |
| 3 | 延时 `Time.runTask(30f, ...)` | 静态字段 `launchTimer` + `launchTimerTask`，在 `update()` **暂停守卫之外**按 `Time.delta()` 递减 | **见下方"关键陷阱"** |
| 4 | 边遍历 `playerCores()` 边 `tile.remove()` | 先 `new Array<>(playerCores())` 拷贝再遍历 | arc 的 `Array` 迭代器无 fail-fast 检查；libgdx `Array` 迭代器在数组被修改时会抛 `GdxRuntimeException`。语义等价（每个核心处理一次） |
| 5 | `GameOverEvent` 由 `Control` 的监听器消费（`Control.java:103-114`：置 `stats.wavesLasted` + `Call.onGameOver` → `ui.restart.show`） | 就地补 `state.stats.wavesLasted = state.wave`，再 `Vars.ui.restart.show(defaultTeam)`（带 null 守卫） | 本项目 `Control` **没有** `GameOverEvent` 监听器（已 grep 确认，全项目仅 `Logic` 自己 fire）。与 `Logic.update` 里既有写法（`:263-269`）保持一致 |

### ⚠ 关键陷阱：`Time.runTask` 在本项目会让发射链卡死（已实测推理确认）

- `Time.runTask` 在 arc 里是 `Timer.schedule(r, delay/60f)`（`Arc/.../Time.java:25-27`）——
  **走 `java.util.Timer`，按真实秒计，与游戏暂停无关**。
- 本项目 `Time.java` **没有** `runTask`，只有 `run(float delay, Runnable)`（`:33-38`）——
  它是 **tick 队列，只在 `Time.update()` 里推进**（`updateRuns`，`:74-89`）。
- 而 `Time.update()` 被 `Logic.update()` 的 `if(!Vars.state.isPaused())` 守卫包住（`Logic.java:50-51`），
  且 `GameState.isPaused()` 在 `gameOver` 时为 true（单机 `netActive == false`）。
- 发射流程会立刻把 `state.gameOver = true`（原版 `Logic.java:167`，本批次照抄）→ 仿真被冻结 →
  **`Time.run(30f, ...)` 的任务永远不执行**：白闪后卡死、核心不消失、结算框不弹。
- 故改用 `launchTimer`/`launchTimerTask`，把递减放在 `update()` 的 `isPaused` 守卫**之外**
  （`Logic.java:170-179`），行为等价（30 tick ≈ 0.5s；原版 `runTask(30f)` 也是 30/60 = 0.5 秒）。
- 由此新增 `Logic.cancelLaunchTimer()`，并在 `Control.menu()` 的 `Time.clear()` 旁调用（`Control.java:885`）——
  否则回菜单后残留的发射任务会被带进下一局（`launchTimer` 不在 `Time.runs` 队列里，`Time.clear()` 清不到）。

---

## G7-2 `checkGameOver` 的发射分支（原版 `core/Logic.java:157-167`）

- **原版**：
  ```java
  if(world.isZone() && alive == state.rules.defaultTeam){
      //in attack maps, a victorious game over is equivalent to a launch
      Call.launchZone();
  }else{
      Events.fire(new GameOverEvent(alive));
  }
  state.gameOver = true;   // ← 两支之后统一置
  ```
- **本项目改动**：`core/src/com/phoenix/game/core/Logic.java:282-309`。
  - `world.isZone() && alive == rules.defaultTeam` → 调 `launchZone()`；
  - 否则走原有普通胜利分支（`stats.wavesLasted` + fire `GameOverEvent(alive)` + `ui.restart.show`）；
  - `Vars.state.gameOver = true;` 移到 if/else **之后**（照原版 `:167`）。
  - 原 `:257-258` 那句"区域发射/存档/解锁链未移植 → 统一按普通胜利弹结算框"的注释与逻辑已删除。

### 联机部分：调查结论（`Vars.isClient()` 时的行为）

已读 `core/NetClient.java`、`core/NetServer.java`、`net/Packets.java`：

1. **纯客机根本不会进入 `checkGameOver`**：`Logic.update()` 末尾的调用是
   `if(!Vars.isClient() && Vars.state.rules.canGameOver) checkGameOver();`（`Logic.java:154`）——
   与 `GameState.isPaused()` 的"暂停时仍允许"设计一致，但胜负判定只在主机/单机跑。
2. **没有任何可用的 RPC/同步包**：`Packets.java` 里全部 25 个包中没有 `gameOver`/`launch` 相关包
   （`WorldState` 只带 `wave/enemies/wavetime`，`NetClient.java:679` 只同步这三个字段）。
   因此本项目**无法把"发射/结算"从主机同步到客机**——这是**已存在的**联机缺口，不是本批次引入的。
3. **本批次的处置**：不新造 RPC（超出 G7 范围，且需要同时改 `Packets`/`NetServer`/`NetClient` 与注册表）。
   代码按原版形状写（`world.isZone() && alive == defaultTeam → launchZone()`），
   并在 `Logic.java:285-288` 注释里声明：**"客户端发射暂不支持，仅主机/单机"**。
   由于第 1 点，`Vars.isClient()` 分支实际不可达，不会产生"客机本地误发射"的行为。

---

## G7-3 HUD 发射 UI（原版 `ui/fragments/HudFragment.java:529-688`）

- **本项目改动**：`core/src/com/phoenix/game/ui/fragments/HudFragment.java`
  - 字段 `launchButton` `:39-46`
  - `addPlayButton` 的发射分支（`waveButton` 点击）`:127-139`
  - 发射按钮创建 `:144-154`
  - 每帧显隐刷新 `:399-402`
  - `inLaunchWave()` `:521-534`、`canLaunch()` `:536-539`、`showLaunchConfirm()` `:541-552`
  - `showLaunch()` `:740-789`
  - 波次标签的发射分支（`waveStatus()`）`:983-1004`

### 3.1 `inLaunchWave()` / `canLaunch()`（原版 `:577-586`）

照抄判据，仅做等价映射：

- 原版：`world.isZone() && world.getZone().metCondition() && !net.client() && state.wave % launchPeriod == 0 && !spawner.isSpawning()`
- 本项目：`metCondition() && wave % launchPeriod == 0` 恰好就是已有的 `Zone.isLaunchWave(int)`（`type/Zone.java:106`）
  → **直接复用它**，避免同一判据写两份；`net.client()` → `Vars.isClient()`。
- `canLaunch() = inLaunchWave() && state.enemies <= 0` —— 逐字照抄。

### 3.2 波次标签的发射分支（原版 `addWaveTable` 的 `labelWrap`，`:630-644`）

- 颜色：`[#<hex>]`，`Tmp.c1.set(Color.WHITE).lerp(enemies > 0 ? WHITE : SCARLET, Mathf.absin(Time.time(), 2f, 1f))`
  —— 照抄（`Mathf.absin` 本项目是三参 `(time, scl, mag)`，`Time.time()` 显式传入）。
  已核对 libgdx `Color.toString()` 输出 **RRGGBBAA**（8 位十六进制，`gdx-1.14.2` 源码 `Color.java:343-349`），
  项目的 `ui/Label` 标记解析器接受 `#rrggbbaa`（`Label.hex → Color.valueOf`）→ 能正确上色。
- 文案键：`launch.unable2` / `launch` / `launch.next` —— **bundle 里都存在**
  （`assets/i18n/bundle.properties:376-380`，`bundle_zh_CN.properties:343-348`），走 `I18n.get/format` + 原版英文兜底
  （不新增/不改 bundle）。已核对颜色名 `scarlet` / `lightgray` / `gold` / `white` 都在 `graphics/Colors` 表里。
- **⚠ 刻意偏离**：原版 `launch` 与 `launch.next` 各占一行（`labelWrap` 多行）；本项目波次面板是**单行横排**，
  改用 `" · "` 连接（**文案键与判据照原版**，只有排版不同）。

### 3.3 发射入口（原版 `table.setDisabled(() -> !canLaunch())` + `table.clicked(...)`，`:664-670`）

- **⚠ 刻意偏离**：原版把**整张波次面板**做成可点击的发射入口（`setDisabled` 控制可点性）。
  本项目波次面板里还混着"下一波 / 存档 / 另存 / 读档 / 自动存"等按钮，整表可点会互相抢事件
  → 改用**一枚独立按钮** `launchButton` 承载同一入口：`setVisible(canLaunch())`，
  点击 `if(canLaunch()) showLaunchConfirm()`（**判据与点击结果照原版**）。

### 3.4 `showLaunchConfirm()`（原版 `:557-575`）

原版结构（`$launch` 标题 / `$launch.confirm` 正文 `width(500).wrap().pad(4)` 居中 /
按钮 `defaults().size(200,54).pad(2)` / `setFillParent(false)` / `$cancel`+`$ok` /
`keyDown(ESCAPE|BACK)` / 每帧 `if(!inLaunchWave()) dialog.hide()`）与项目的
`UI.showConfirm(title, text, Boolp hide, Runnable confirmed)`（`core/UI.java:649-679`）**逐项一致**
→ 直接复用它，`$ok` 回调改调 `Logic.launchZone()`（原版是 `Call.launchZone()`）。
（`$launch`/`$launch.confirm`/`$cancel`/`$ok` 键均已确认存在：`bundle.properties:184/189/376/380`。）

### 3.5 `showLaunch()`（原版 `:529-540`）

- 全屏白闪 `Image`，`alpha 0 → 1`，`Actions.fadeIn(40f/60f)`，**不自动移除**
  （原版即如此：白闪一直盖着屏幕，30 tick 后弹出的结算框画在它之上，直到回菜单才移除）。
- **libgdx 适配**：
  - 原版 `new Image()` 用 arc 的默认白 drawable；libgdx 的 `Image` 无默认 drawable →
    显式给 `white` 贴图区域（缺失回退 `blank`），与同文件既有的 `showLand()` 同口径。
  - 原版 `image.update(() -> { if(state.is(State.menu)) image.remove(); })` 是 arc 的每帧回调，
    libgdx 没有 → 用 `MenuFragment.Act` 每帧检查菜单态，并把"白闪 + 该 Act"整体（外层 `fillParent` Table）移除。
  - **⚠ 刻意偏离**：额外设 `Touchable.disabled`（原版 `showLaunch` 未设，`showLand` 设了）——
    白闪覆盖全屏，禁触摸才不抢结算框点击。
- 容器取 `group.getStage()`（HUD 所在 stage），回退 `FloatingDialog.scene`。

### 3.6 `addPlayButton` 的发射分支（原版 `:677-688`）

原版点击顺序：`net.client() && player.isAdmin → Call.onAdminRequest(wave)`；
`else if(inLaunchWave()) → ui.showConfirm("$confirm", "$launch.skip.confirm", () -> !canSkipWave(), () -> state.wavetime = 0f)`；
`else state.wavetime = 0f`。

本项目在 `waveButton` 的 `clicked` 里补上 `inLaunchWave()` 分支（`:132-139`），
第 1 条客户端路径仍按 G6 既有注释**禁用**（`NetServer.onAdminRequest` 没有 wave 分支且会拒绝"目标 == 自己"）。
`$confirm` 键已确认存在（`bundle.properties:184`）。

**未回退 G6 的既有设施**：`canSkipWave()` 与 `waveButton`/`minimapTable` 字段原样保留（仅在 `clicked` 里加分支）。

---

## 遗留风险

1. **【低·与原版同源】发射后 30 tick 内可重复发射**：`inLaunchWave()` 不随发射改变（`metCondition()` 只看波次/attackMode），
   所以确认框关闭后到核心被移除之间，玩家再点一次发射按钮会**再**执行一遍 `launchZone()`
   → 核心库存被 `data.addItem` 计两次。**原版存在同样问题**（`Call.launchZone()` 可重复触发），
   按"照抄原版"铁律未加去重守卫。
2. **【低】发射延时任务的清理只挂在 `Control.menu()`**：`enterWorld()` 里也有 `Time.clear()`（`Control.java:697`），
   未在那里取消 `launchTimer`。实际不可达（发射序列是终态，30 tick 内无法通过 UI 离开并开新局），
   但若要更保险可在这两处都调 `Logic.cancelLaunchTimer()`。
3. **【中·已存在】客机收不到发射/结算同步**：见 G7-2 调查结论第 2 点——网络层没有 `gameOver`/`launch` 包。
   本批次按"仅主机/单机"声明，未新造 RPC。
4. **【待验证】视觉/交互链路未经实拍**：按批次约定不编译不运行。需要主会话用
   `PHOENIX_AUTOPLAY=1` + 区域图跑到 `conditionWave`（或 attack 图打光敌队核心）验证：
   白闪 → 特效 → 核心消失 → 结算框标题为 `$launch.title`（`GameOverDialog.rebuild` 读 `state.launched`）。
   注意 `PHOENIX_SHOT >= 140`（开局降落动画）与 `inLaunchWave()` 需要 `wave % launchPeriod == 0`。
5. **`Launched` 目标链**：`setLaunched()` 会写设置并 `data.modified()`，`updateObjectives` 里
   `content.getBy(ContentType.zone)` 已可用（`Zone.java:160`）→ 目标可达成。未做端到端验证（需区域解锁链）。

---

## 附：本批次核对过的"疑点"（结论）

| 疑点 | 结论 |
| --- | --- |
| `Time.runTask` 是否存在 | **不存在**。`Time` 只有 tick 队列版 `run(float, Runnable)`（`Time.java:33`）→ 已按批次指引改用等价延后机制并注释 |
| 核心传参口径（Tile vs TileEntity） | 一致：原版 `TileEntity.x/y = tile.drawx()/drawy()`，本项目 `Tile.getX/getY = drawx/drawy()`，都是**方块中心** → 无需换算 |
| `Effects.effect(Effect, Position)` 重载 | 存在（`entities/Effects.java:123`），`Tile implements com.phoenix.game.math.Position` |
| `Zone.setLaunched()` 有无调用者 | 原先无；本批次接入 `Logic.launchZone()` |
| i18n 键 `launch` / `launch.confirm` / `launch.next` / `launch.unable2` / `launch.skip.confirm` / `confirm` / `cancel` / `ok` | **全部存在**（`bundle.properties`）→ 无需中文兜底、未改 bundle |
| `Color.toString()` 能否用于 `[#hex]` 标记 | 可以：libgdx 输出 RRGGBBAA（`Color.java:343-349`），`ui/Label` 的 `hex()` 走 `Color.valueOf` 接受 8 位 |
| 颜色名 `scarlet` / `lightgray` / `gold` | 都在 `graphics/Colors` 表里（`Colors.java:17/18/38/46`） |
| `Vars.hud` / `Vars.ui` 是否可用 | 两者都在 `ClientLauncher.init` 里无条件赋值（`:190` / `:198`）→ 守卫成立 |
| `Control` 是否有 `GameOverEvent` 监听器 | **没有**（全项目仅 `Logic` 自己 fire）→ 结算框改为就地弹出（既有做法） |
