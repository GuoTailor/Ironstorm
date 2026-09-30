# G4 批次报告：规则 / 数值默认值（7 项）

> 范围：`docs/port/audit-A7-net-io-meta.md` 第二节「偏离」第 1~7 条（对应审计号 A7-D1~D7）。
> 参照 `D:\IdeaProjects\Mindustry-126.2`（只读）。**未运行任何 gradle / git 命令**（按协议），以下为静态核对。
> 铁律：数值/默认值照抄原版；凡与原版不同处一律写「刻意偏离 + 原因」。

## 完成情况：7/7 全部处理（6 项照抄改回 + G4-7 调查后决策）

| # | 项 | 审计号 | 状态 | 改动文件 |
| --- | --- | --- | --- | --- |
| G4-1 | waveSpacing 3600 → 7200 | A7-D1 | ✅ 照抄 | `game/Rules.java`、`io/SaveMeta.java` |
| G4-2 | dropZoneRadius 200 → 300 | A7-D2 | ✅ 照抄 | `game/Rules.java`、`io/SaveMeta.java` |
| G4-3 | addItem 补 itemsDelivered 统计 | A7-D3 | ✅ 照抄（含 null 守卫） | `game/GlobalData.java` |
| G4-4 | SpawnGroup.createUnit 施加 items | A7-D4 | ✅ 照抄 | `game/SpawnGroup.java` |
| G4-5 | isPaused 补 gameOver + Logic 守卫 | A7-D5 | ✅ 照抄（+ 等价守卫） | `core/GameState.java`、`core/Logic.java` |
| G4-6 | Team 占位阵营随机 HSV | A7-D6 | ✅ 照抄（libgdx 3 参换算） | `game/Team.java` |
| G4-7 | Rules 默认值复核（waves / loadout） | A7-D7 | ⚠ 调查后**部分照抄 + 1 项刻意偏离** | `game/Rules.java`、`io/SaveMeta.java`、`core/Control.java` |

---

## G4-1 waveSpacing 改回 7200（A7-D1，中）

**原版**：`game/Rules.java:56` `public float waveSpacing = 60 * 60 * 2;`（= 7200 tick = 120 秒）。

**本项目改动**
- `game/Rules.java:20`：`60f * 60f` → `60f * 60f * 2f`；行内注释「默认 60 秒」→「默认 120 秒，对应原版 `60 * 60 * 2`」。
- `io/SaveMeta.java:51`：`RulesMeta.waveSpacing` 默认 `60f * 60f` → `60f * 60f * 2f`（只影响新档/缺字段档的默认值，无格式变更；`SaveIO.applyMeta` 是无条件拷贝，正常档以档内值为准）。

**照抄 / 偏离**：完全照抄原版，无偏离。

**消费点核对**（无需改代码）：
- `core/Logic.runWave()`（`Logic.java:187` 附近）：`wavetime = waveSpacing`（或 ×`launchWaveMultiplier`）—— 直接读本字段。
- `core/Control.play()` / `playZone()`：`wavetime = rules.waveSpacing * 2f`（开局宽限，对应原版 `Logic.play` 的 `waveSpacing * 2`）。
- 改后开局宽限从 7200 tick 变为 14400 tick，与原版一致。

**遗留风险**：无。仅改变节奏数值。

---

## G4-2 dropZoneRadius 改回 300（A7-D2，中）

**原版**：`game/Rules.java:52` `public float dropZoneRadius = 300f;`。

**本项目改动**
- `game/Rules.java:40`：`200f` → `300f`，注释补「对应原版 `rules.dropZoneRadius`，原版默认 300f」。
- `io/SaveMeta.java:53`：`RulesMeta.dropZoneRadius` 默认 `200f` → `300f`。

**照抄 / 偏离**：完全照抄原版，无偏离。

**消费点核对**（无需改代码）：`ai/WaveSpawner.java:126-127` —— 开波时对每个地面出生点
`Effects.effect(Fx.spawnShockwave, x, y, dropZoneRadius)` + `Damage.damage(waveTeam, x, y, dropZoneRadius, 99999999f, true)`。
半径由 200（25 格）恢复到 300（37.5 格），语义正确（清空投区更大，与原版一致）。

**遗留风险**：无。`docs/port/f2-report.md` 曾按 `dropZoneRadius=200` 描述"清场半径"，属旧值描述，不影响代码。

---

## G4-3 GlobalData.addItem 补 itemsDelivered 统计（A7-D3，中）

**原版**：`game/GlobalData.java:84-97`：

```java
public void addItem(Item item, int amount){
    if(amount > 0) unlockContent(item);
    amount = Math.max(amount, 0);
    modified = true;
    items.getAndIncrement(item, 0, amount);
    state.stats.itemsDelivered.getAndIncrement(item, 0, amount);   // ← 本项目原先缺这句
    if(items.get(item, 0) < 0) items.put(item, Integer.MAX_VALUE);
    if(state.stats.itemsDelivered.get(item, 0) < 0) state.stats.itemsDelivered.put(item, Integer.MAX_VALUE);
}
```

**本项目改动**：`game/GlobalData.java:42-62` 的 `addItem` 里，在写 `items[]` 之后补：

```java
if(Vars.state != null && Vars.state.stats != null){
    Vars.state.stats.itemsDelivered.getAndIncrement(item, 0, amount);
    if(Vars.state.stats.itemsDelivered.get(item, 0) < 0){
        Vars.state.stats.itemsDelivered.put(item, Integer.MAX_VALUE);
    }
}
```

并新增 `import com.phoenix.game.Vars;`。

**照抄 / 偏离**：
- 语义照抄：`getAndIncrement(item, 0, amount)` + 负数溢出钳制，与原版逐字对应（libgdx `ObjectIntMap` 与 arc 同名同签名，`Tutorial.java:80` 已在用）。
- **等价替代（已写注释）**：本项目 `items` 是 `int[]`（原版是 `ObjectIntMap`），溢出已由 `(int)Math.min((long)items[id] + amount, Integer.MAX_VALUE)` 提前钳死，故 `items` 那条负数钳制**不需要**（照抄反而多余）；只对 `itemsDelivered` 补钳制。
- **刻意新增 null 守卫（已写注释）**：原版 `state` / `state.stats` 恒非空；本项目 `GlobalData.load()` 在启动时会 `addItem(Items.copper, 50)`，且 `Events.fire` 是同步的、菜单态也可能走到这里 → 加 `Vars.state != null && Vars.state.stats != null` 守卫。这是**防御性增强**，不改变正常路径行为。

**消费方核对**（原样已存在，改后即生效）：
- `ui/dialogs/GameOverDialog.java:127-132`：`world.isZone() && !state.stats.itemsDelivered.isEmpty()` → 显示"已交付资源清单"。
- `game/Stats.java:70`：`calculateRank` 里 `itemsDelivered.get(item, 0) / capacity` → 区域评级。此前恒为 0 分，现按实际入库量计。

**遗留风险**：低。注意 `itemsDelivered` 是"**全局**入库（LaunchPad 发射到 GlobalData）"的统计，只在 `GlobalData.addItem` 调用时累加；这与"区域内送达核心"是同一口径（原版亦如此）。

---

## G4-4 SpawnGroup.createUnit 施加 items（A7-D4，中低）

**原版**：`game/SpawnGroup.java:60-72` `createUnit`：

```java
BaseUnit unit = type.create(team);
if(effect != null) unit.applyEffect(effect, 999999f);
if(items != null) unit.addItem(items.item, items.amount);   // ← 本项目原先缺
return unit;
```

**本项目改动**：`game/SpawnGroup.java:65-68`，在 `effect` 之后、`return` 之前补：

```java
//照原版 SpawnGroup.java:67-69：带物品的编成在生成单位时把物品塞进背包
if(items != null){
    unit.addItem(items.item, items.amount);
}
```

**前置确认**：
- `SpawnGroup.items`（`ItemStack`）字段存在（`SpawnGroup.java:31`）。
- `BaseUnit.addItem(Item, int)` 存在（`BaseUnit.java:488`，实现为"换物品重置数量 + 夹容量"，与原版 `Unit.addItem` 语义一致）。

**照抄 / 偏离**：完全照抄原版，无偏离。

**遗留风险**：无。内置 `DefaultWaves` 未设 `items`（原版 126.2 的部分编成有），故当前无可见变化；但自定义/区域波次设了 `items` 后即生效（此前是静默失效）。

---

## G4-5 GameState.isPaused 补 gameOver 分支 + Logic 守卫（A7-D5，中）

**原版**
- `core/GameState.java:41-43`：`return (is(State.paused) && !net.active()) || (gameOver && !net.active());`
- `core/Logic.java:218`：`if(!state.isPaused()){ Time.update(); ... 全部仿真 ... }`（`checkGameOver` 在该守卫**之外**，`state.enemies` 统计也在守卫之外，见原版 `:215`）。

**本项目改动 1**：`core/GameState.java:76-79`：

```java
public boolean isPaused(){
    boolean netActive = Vars.isClient() || Vars.isServer();
    return (state == State.paused && !netActive) || (gameOver && !netActive);
}
```

- **等价替代**：原版 `net.active()` = "网络已激活" → 本项目等价物 `Vars.isClient() || Vars.isServer()`（读 `Vars.java:247/253` 确认：单机两者皆 false，本机 hosting 时 `isServer()=true`）。
- 新增 `import com.phoenix.game.Vars;`。

**本项目改动 2**：`core/Logic.java` 的 `update()` 按原版结构重排：
- 顶部保留 `Events.fire(Trigger.update)`（守卫外，照原版 `:211`）。
- `countEnemies()` 移到守卫**外**（照原版 `:215`，暂停/gameOver 时 HUD 敌人数不僵住）。
- 用 `if(!Vars.state.isPaused()){ ... }` 包住整段仿真（`Time.update()` / `Effects.update()` / 队列 flush / `effectGroup.update()` / playtime / checkSave / saves / `updateWaves` / `world.updateTiles` / Fire·Puddle·Decal / player / units / bullets / 碰撞 / 清理死亡单位）。
- `checkGameOver()` 留在守卫**外**（照原版 `:262-264`）。

**为什么必须补 Logic 守卫（关键）**：本项目 `ClientLauncher.java:590` 只在 `Vars.state.isPlaying()` 时调 `Logic.update()`，而 `gameOver` **不改 state**（仍是 playing）→ 仅补 `isPaused()` 而无人消费 = 无效。补守卫后，结算框弹出期间仿真冻结（单位停移/停火、波次停计），与原版一致。

**守卫内 / 外的判定依据（逐项对照原版 `Logic.update`）**

| 步骤 | 原版位置 | 本项目落点 | 依据 |
| --- | --- | --- | --- |
| `Trigger.update` | 守卫外（`:211`） | 守卫外 | 照原版 |
| `state.enemies` 统计 | 守卫外（`:215`） | `countEnemies()` 守卫外 | 照原版 |
| `Time.update()` | 守卫内（`:219`） | 守卫内 | 照原版 |
| 波次计时 / `runWave` | 守卫内（`:221-229`） | `updateWaves()` 守卫内 | 照原版 |
| effectGroup / 特效 | 守卫内（`:232-233`） | `Effects.update()`、`effectGroup.update()` 守卫内 | 照原版 |
| 单位 / 子弹 / 碰撞 / 玩家 | 守卫内（`:236-259`） | 守卫内 | 照原版 |
| `checkGameOver` | 守卫外（`:262-264`） | 守卫外 | 照原版 |

**改后对既有 `isPaused()` 消费方的影响（已逐个核对）**：`DesktopInput:82/100/214/282/309/342`、`InputHandler:249`、`Renderer:153`、`HudFragment:91/347`、`BlockInventoryFragment:220`、`Tutorial:198/205`、`FloatingDialog:124`。
这些点原本就依赖"暂停时不操作"，`gameOver` 期间一并冻结**正是原版语义**（原版 `isPaused()` 含 gameOver 分支）。gameOver 时结算框 `GameOverDialog` 覆盖全屏，底层 paused 浮层/输入被遮住，无可见副作用。

**照抄 / 偏离**：`isPaused()` 照抄（`net.active()` → 本项目等价物）；`Logic.update` 守卫为**照原版的等价补写**（原版是 `ApplicationListener` 每帧无条件 update，本项目只在 `isPlaying()` 时调用 → 守卫实际只在 gameOver 生效，语义与原版一致）。

**遗留风险**：
- 低。gameOver 期间 `Time.update()` 不再推进 → `Time.delta()` 保持上一帧值（≈1.0），`Time.run` 延迟任务暂停，与原版一致。
- ⚠ 本项目"暂停"（`state=paused`）时 `Logic.update()` 本就不被调用，故守卫对"手动暂停"是冗余但无害的（形状保真）。

---

## G4-6 Team 占位阵营颜色（A7-D6，低）

**原版**：`game/Team.java:32-39`：

```java
static{
    Mathf.random.setSeed(8);
    for(int i = 6; i < all.length; i++){
        new Team(i, "team#" + i, Color.HSVtoRGB(360f * Mathf.random(), 100f * Mathf.random(0.6f, 1f), 100f * Mathf.random(0.8f, 1f), 1f));
    }
    Mathf.random.setSeed(new Rand().nextLong());
}
```

**本项目改动**：`game/Team.java:35-47`：

```java
static{
    //照原版 Team.java:32-39：占位队伍（team#6..team#255）用随机 HSV 上色；seed=8 保证每次启动颜色一致。
    Mathf.random.setSeed(8);
    for(int i = 6; i < all.length; i++){
        new Team(i, "team#" + i, Color.HSVtoRGB(Mathf.random(), Mathf.random(0.6f, 1f), Mathf.random(0.8f, 1f)));
    }
    Mathf.random.setSeed(MathUtils.random.nextLong());
}
```

新增 import：`com.phoenix.game.math.Mathf`、`com.badlogic.gdx.math.MathUtils`。

**照抄 / 偏离（换算说明，已写注释）**：
- **HSVtoRGB 参数换算**：arc 是 **4 参**（h ∈ [0,360), s/v ∈ [0,100], a）；libgdx 是 **3 参**且全为 [0,1]（`Color.HSVtoRGB(h,s,v)` 返回新 Color，alpha 固定 1）。
  原式 `360f*random()/360`、`100f*random(0.6f,1f)/100`、`100f*random(0.8f,1f)/100` → 化简为三个 [0,1] 随机值；**取值顺序 h→s→v 与原版一致**。
- **恢复种子**：原版用 arc 的 `new Rand().nextLong()`；本项目没有 `Rand` → 用共享 `MathUtils.random.nextLong()`（等价"取一个随机 long 重新播种"，已写注释）。
- ⚠ 由于本项目 RNG 是 `RandomXS128`（非 arc 的 `Rand`），生成的**具体颜色值与原版不同**——这是 RNG 实现差异导致的必然结果，不是偏离；契约（随机 HSV、seed=8 可复现）一致。

**遗留风险**：无。`Color.HSVtoRGB` 每次返回新对象，不会共享引用。

---

## G4-7 Rules 默认值复核（A7-D7，低）⚠ 需先调查

**原版**
- `game/Rules.java:22` `public boolean waves;` → 默认 **false**；由 `Gamemode` 置位（`survival: rules.waves = true`，`editor: rules.waves = false`），或由**内置地图的 rules**（区域图经 `MapGenerator.generate → SaveIO.load(map.file)` 载入）提供。
- `game/Rules.java:80` `public Array<ItemStack> loadout = Array.with(ItemStack.with(Items.copper, 100));` → 默认 **copper×100**。

### 调查结论（决策依据）

**1) `waves`：本项目各开局路径是否应用了 Gamemode / 地图 rules？**

| 开局路径 | 是否应用 Gamemode | waves 来源（改前） | 若默认改 false 会怎样 |
| --- | --- | --- | --- |
| `Control.playMap(map, rules)` | ✅ 是（调用方 `MapPlayDialog` 用 `map.applyRules(Gamemode.survival)`，`survival` 置 `waves=true`） | Gamemode | 不受影响 |
| `Control.playZone(zone)` | ❌ 否（照原版只做 `zone.rules.get(...)`） | 改前靠 `Rules.waves=true` 默认 | **11/12 个区域会没波次** |
| `Control.play()`（战役测试图，本项目自有） | ❌ 否 | 改前靠 `Rules.waves=true` 默认 | **没波次** |
| `Control.load(slot)`（读档） | —（规则来自存档 meta，`waves` 已持久化） | 存档 | 不受影响 |
| `MapEditorDialog`（`new Rules()`） | ❌（本项目未套 `Gamemode.editor`） | 改前默认 true（**错误**，编辑器不应有波次） | 变 false（**更贴近原版**） |

**关键发现（原版为什么区域有波次）**：原版区域图用 `MapGenerator`，其 `generate()` 内部 `SaveIO.load(map.file)` 会把**内置地图自带的 rules（含 `waves=true` 与波次编成）**载入 `state.rules`；只有 `desertWastes` 用代码生成器，所以在 `Zones.java` 里显式写了 `r.waves = true`。
**本项目所有区域都换成了程序生成器**（`BasicGenerator` 子类，见 `content/Zones.java` 类注释），**没有"地图 rules"这层兜底** → 只有 `desertWastes` 显式置位，其余 11 个区域此前全靠 `Rules.waves` 默认值撑着。

**决策**：照原版把默认改为 `false`，并在两条"不套 Gamemode 且无地图 rules"的路径上**等价补 `waves = true`**（这正是原版由地图 rules / Gamemode 提供的值）。理由：① 不改会继续把"默认值"当隐式开关用，偏离原版且脆弱；② 补偿点明确、有界（仅 2 处），不会破坏现有玩法。

### 改动

1. `game/Rules.java:16`：`waves = true` → `waves = false`（照原版），注释写明默认 false + 两条补偿路径。
2. `io/SaveMeta.java:46`：`RulesMeta.waves` 默认 `true` → `false`（与新 `Rules` 默认一致；`SaveIO.applyMeta` 是无条件拷贝，正常档以档内值为准，仅影响缺字段档）。
3. `core/Control.play()`（`Control.java:374-376`）：`Vars.state.rules.waves = true;` + 注释（本路径不套 Gamemode、非区域图 → 显式打开）。
4. `core/Control.playZone()`（`Control.java:463-467`）：`Vars.state.rules.waves = true;` + 注释（原版靠地图 rules 提供；本项目换程序生成器后在此等价补上；放在 `zone.rules.get` 之后，与 `desertWastes` 的显式置位一致、无冲突）。

**补偿完整性核对**（全部 `rules.waves` 消费方）：
- `core/Logic.updateWaves()`（`Logic.java:178` 守卫 `!rules.waves`）→ 已补偿。
- `world/Build.java:91`（`!(rules.waves && team == rules.waveTeam)` 敌核禁建豁免）→ 已补偿。
- `ui/fragments/HudFragment.java:110/359/810`（波次 HUD / BOSS 血条显隐）→ 已补偿。
- `game/Teams.java:86`（`active()` 注释提及）→ 无行为依赖。
- `ui/dialogs/CustomRulesDialog.java:260`（用户开关，基准来自 `map.applyRules`）→ 基准已含 `waves=true`。

**未改之处（刻意）**：`Zone.getRules()`（`type/Zone.java:92-104`）返回的 rules 在代码生成器分支仍为 `waves=false`。该方法只被 `ZoneInfoDialog`（显示 attackMode）与 `Stats.calculateRank`（读 attackMode）使用，**不读 waves**，故无功能影响；未改以保持改动克制。

### `loadout`：保留空数组 + 刻意偏离声明（**不改**）

**结论**：`Control.playMap` 的兜底**确实存在且等价** —— `Control.java:628-630`：`if(rules.loadout == null || rules.loadout.length == 0){ rules.loadout = new ItemStack[]{new ItemStack(Items.copper, 100)}; }`（= 原版默认 copper×100）。

**但默认值不能照抄为 `copper×100`**（与任务假设不同，理由如下）：
- 本项目 `Items.copper` 等是在 `Items.load()`（由 `Vars.content.load()` 触发）里才赋值的（`content/Items.java:24`），**原版是字段内联 `new Item(...)`**。
- `Vars.state = new GameState()`（→ 字段 `rules = new Rules()`）发生在 **`Vars` 类静态初始化**时，早于 `Vars.content.load()`（`ClientLauncher.java:130` 调用它本身就会先触发 Vars 类初始化）→ 若把 `Rules.loadout` 写成原版内联默认，会捕获到 `Items.copper == null`，产生含 null item 的默认 loadout（`playMap` 的 `length == 0` 判断随之失效，可能 NPE）。

**决策**：保留 `loadout = {}`，在 `Rules.java:23-32` 写「刻意偏离 + 原因」，并注明等价补偿（`playMap` 兜底 / `play()` 显式赋值 / 区域用 `zone.getStartingItems()`）。

**本项以「不破坏现有玩法」为最高优先**：`waves` 的补偿已覆盖全部开局路径；`loadout` 保持现状。

---

## 改动文件清单（G4 全部）

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/game/Rules.java` | `waves=false`、`waveSpacing=60*60*2`、`dropZoneRadius=300f`；`loadout` 加偏离声明 |
| `core/src/com/phoenix/game/io/SaveMeta.java` | `RulesMeta.waves=false`、`waveSpacing=60*60*2`、`dropZoneRadius=300f` |
| `core/src/com/phoenix/game/game/GlobalData.java` | `addItem` 补 `itemsDelivered` 累计 + 钳制 + null 守卫；加 `import Vars` |
| `core/src/com/phoenix/game/game/SpawnGroup.java` | `createUnit` 补 `if(items != null) unit.addItem(...)` |
| `core/src/com/phoenix/game/core/GameState.java` | `isPaused()` 补 gameOver 分支（`netActive` 等价物）；加 `import Vars` |
| `core/src/com/phoenix/game/core/Logic.java` | `update()` 补 `!isPaused()` 仿真守卫；`countEnemies` 移出守卫 |
| `core/src/com/phoenix/game/game/Team.java` | 占位阵营改随机 HSV；加 import `Mathf`/`MathUtils` |
| `core/src/com/phoenix/game/core/Control.java` | `play()` / `playZone()` 各补 `rules.waves = true` 补偿 |

## 全局遗留风险 / 未验证项

- **未编译、未运行**（按协议）。以上均为静态核对；建议主会话编译 `:core` 后按 `PHOENIX_AUTOPLAY=1` 跑一次，确认战役仍开波（`Logic.runWave` 的 `"第 N 波开始"` 日志）、结算框弹出后单位停动（G4-5）。
- **G4-5**：`isPaused()` 语义变宽后，凡依赖它判断"手动暂停"的 UI/输入在 gameOver 期间也会命中；已逐个核对无副作用（结算框全屏覆盖）。
- **G4-7**：`Zone.getRules()` 的 `waves` 未同步（无消费方，见上）。
- **G4-3**：`itemsDelivered` 只在 `GlobalData.addItem`（全局入库）时累加，与"区域送达核心"同口径（原版一致）。

## 发现的额外问题（供后续批次参考，本批未改）

1. **`Rules.loadout` 默认值无法照抄原版**（G4-7 详述）——根因是 `Items` 走 `load()` 赋值而非字段内联，凡"在 `Rules` 字段默认值里直接引用 `Items.xxx`"的写法都有同样的 null 陷阱。原版 `Rules` 还有 `defaultTeam = Team.sharded` 等引用内容对象，本项目已用 `Team.sharded`（Team 是纯静态内联，不受影响）。
2. **`MapEditorDialog` 未套 `Gamemode.editor`**（`MapEditorDialog.java:302` 直接 `new Rules()`，注释标了 TODO）——改 `waves` 默认后编辑器恰好得到 `waves=false`（更贴近原版），但其余 editor 专属字段（`enemyCoreBuildRadius=0`、`respawnTime=0` 等）仍缺失，建议后续补 `Gamemode.editor.apply(...)`。
3. **`DefaultWaves` 仅 10 组、未用 `items`/`revenant`/`lich`**（审计第二节第 8 条，不属 G4 范围）——G4-4 补上 `items` 消费后，补齐 `DefaultWaves` 的自爆/燃烧弹编成才有可见效果。
