# G3 批次报告：事件派发 / 实例化断裂（6 项）

> 范围：`docs/port/audit-A7-net-io-meta.md`（A7-B1/B2/B3/B4/B5）+ `docs/port/audit-A4-world-content.md`（A4-B1）。
> 参照 `D:\IdeaProjects\Mindustry-126.2`（只读）。**未运行任何 gradle / git 命令**（按协议），以下为静态核对。

## 完成情况：6/6 全部完成

| # | 项 | 审计号 | 状态 | 改动文件 |
| --- | --- | --- | --- | --- |
| G3-1 | ClientLoadEvent 派发 | A7-B1 | ✅ | `ClientLauncher.java` |
| G3-2 | Trigger.update 每帧派发 | A7-B3 | ✅ | `core/Logic.java` |
| G3-3 | StateChangeEvent 派发 | A7-B4 | ✅（含修 import 类型错误） | `core/GameState.java`、`game/EventType.java` |
| G3-4 | PlayEvent / DisposeEvent 派发 | A7-B5 | ✅ | `core/Control.java`、`ClientLauncher.java` |
| G3-5 | CoreItemDeliverEvent 派发 | A4-B1 | ✅ | `world/blocks/storage/CoreBlock.java` |
| G3-6 | MusicControl / LoopControl 实例化 + 每帧驱动 | A7-B2 | ✅（music 位置刻意偏离） | `Vars.java`、`core/Control.java`、`ClientLauncher.java` |

---

## G3-1 ClientLoadEvent 派发（原版 ClientLauncher.java:133）

**原版**：`update()` 里 `if(assets.update(...))` 异步加载完成 → `for(listener : modules) listener.init();` → `mods.eachClass(Mod::init);` → `finished = true;` → **`Events.fire(new ClientLoadEvent());`** → `super.resize(...)`。

**本项目落点**：`ClientLauncher.java:205-211`，位于 `create()` 的 `if(!finished){ ... }` 块**末尾**（`Vars.ui` / `listfrag` 构建之后）。

**为什么是这个点（关键差异）**：本项目 `create()` 是**同步一次性**完成的，没有原版 `assets.update()` 的异步 loading 循环 —— `finished = true` 在 `create()` 开头（:116）就置位，其后的整段（`Core.assets.finishLoading()` → 内容/图集/字体/UI 全部就绪）就是原版"module.init() + 内容加载 + 资源就绪"的等价物。因此把派发点放在该段末尾，与"原版在所有 module.init() 之后 fire"对齐。

**不会重复 fire**：`create()` 只跑一次（`finished` 守卫生效），且不在 `render()` 主循环内。

**消费方核对**：
- `game/MusicControl.java:37` `Events.on(ClientLoadEvent.class, e -> reload())` → `reload()` 读 `Musics.game1/2/3/4/5/6/7`。这些是**生成类 `io.anuke.mindustry.gen.Musics` 的字段初始化器**（`Annotations` 的 `AssetsAnnotationProcessor` 生成 `new com.phoenix.game.core.MockMusic()`）→ **非 null**，不 NPE。
- `game/Tutorial.java:91-95` `for(TutorialStage stage : TutorialStage.values()) stage.load();` → `load()` 只读 `I18n.get(...)` 与 `Core.bundle.get(...)`；`Core.bundle` 在 `create()` :82 已建，`Vars.mobile` 在 `Vars.init()`（:165 调用）已算好。缺键走 `???key???` 字面量（`setExceptionOnMissingKey(false)`），不抛异常。

---

## G3-2 Trigger.update 每帧派发（原版 Logic.java:211）

**改动**：`core/Logic.java:35-41`，在 `public static void update()` 的**第一条语句**加
`Events.fire(EventType.Trigger.update);`（照原版位置：`Time.update()` 之前）。

`EventType.Trigger.update` 枚举成员已存在（`game/EventType.java:40`）；本项目 `Events.fire(Enum)` 走"按事件对象注册"的 key（`core/Events.java:27`），可正常派发。

**⚠ 刻意说明（已写入代码注释）**：原版 `Logic` 是 `ApplicationListener` 模块、**每帧无条件** `update()`；本项目 `Logic` 是纯静态类，由 `ClientLauncher.render()` 在 `state.isPlaying()` 时调用（:590）→ 本事件**只在游玩中派发**，菜单/暂停态不派发。当前无监听者，故无行为变化，仅补形状。

---

## G3-3 StateChangeEvent 派发（原版 GameState.java:32-35）

**改动 1（必要前置修复）**：`game/EventType.java:4` 的 import 由 `com.badlogic.gdx.ai.fsm.State` 改为 `com.phoenix.game.core.GameState.State`。

> ⚠ **这是原移植的一个类型错误**：原版 `EventType.java:4` 是 `import mindustry.core.GameState.State;`（游戏状态枚举），本项目误写成 libgdx 的 `ai.fsm.State`（接口）。`StateChangeEvent` 的两个 `State` 形参因此与真正要传的 `GameState.State` **类型不兼容** —— 不修这个 import，G3-3 无法编译。全工程 `State` 标识符在 `EventType` 里只被 `StateChangeEvent` 使用，改 import 无其它影响。

**改动 2**：`core/GameState.java` 的 `set(...)` 照抄原版顺序（**先 fire 再赋值**）：

```java
public void set(State astate){
    Events.fire(new StateChangeEvent(this.state, astate));
    this.state = astate;
}
```

参数名由 `state` 改回原版的 `astate`（消除与字段同名遮蔽）。原版语义：监听器读 `getState()` 拿到的是**旧**状态（本项目 `Events.fire` 同步，行为一致）。

**消费方**：本项目暂无 `StateChangeEvent` 监听者。原版监听者在 `Control` 构造器（切状态时停所有音效 + `platform.updateRPC`）——**未移植**（音频未启用），超出 G3 范围，见"遗留"。

---

## G3-4 PlayEvent / DisposeEvent 派发（原版 Logic.java:105 / Renderer.java:159）

### PlayEvent

**落点**：`core/Control.java:699-707`，`enterWorld(boolean clearUnits)` 里 `Vars.state.set(GameState.State.playing)`（:697）**之后**。

**⚠ 刻意偏离 + 原因（已写入代码注释）**：原版 `PlayEvent` **只在 `logic.play()`**（`playMap` / `playZone` / `playTutorial`）里 fire；**读档路径不 fire**（`LoadDialog.runLoadSave` 是直接 `state.set(State.playing)`，不经 `logic.play()`）。本项目 `play()` / `playMap()` / `playZone()` 与 `load()` **共用** `enterWorld()`（这是本项目既有的等价替代，见其 javadoc），所以用现成的 `clearUnits` 开关区分：

- `clearUnits == true` → `play()` / `playMap()` / `playZone()`（含 `playTutorial`）→ **fire**；
- `clearUnits == false` → `load()` 读档 → **不 fire**。

这样与本项目对原版"仅新局派发 PlayEvent"的语义**精确一致**。（`clearUnits` 本是"是否清空 Units"的开关，此处被当作"是否新局"的代理 —— 当前两者取值完全重合，代码注释已说明；若将来新增 `enterWorld(true)` 的读档路径需同步复查。）

**消费方**：本项目暂无 `PlayEvent` 监听者（原版 `Control` 构造器里那个做 `assignTeam/setDead/add` 的监听器未移植，见"遗留"）。

### DisposeEvent

**落点**：`ClientLauncher.java` 的 `dispose()`，在 **renderer 资源释放之后、`batch`/`atlas` 释放之前**（`renderer.disposeBloom()` 之后）。

**落点选择理由（已写入代码注释）**：
- 原版在 `Renderer.dispose()` **末尾** fire（`Renderer.java:159`），而 `Renderer.dispose()` 由 `ApplicationCore` 的模块 dispose 循环调用 → 触发时 **`batch`/`atlas` 尚未释放**。
- 本项目 `Renderer` **没有 `dispose()`**（只有 `disposeBloom()`，已确认全类无 `void dispose`）；其资源释放由 `ClientLauncher.dispose()` 开头几行承担 → 等价落点即"renderer 资源释放之后"。
- 放在 `batch`/`atlas` 释放**之前**，保持原版"监听器仍可访问 `Core.batch`/`Core.atlas`"的不变式。

**消费方**：本项目暂无 `DisposeEvent` 监听者。原版的监听者（`MenuFragment` → `MenuRenderer.dispose()`、`Schematics` → 预览 FBO 释放）在本项目是**直接调用**（`ClientLauncher.dispose()` 里的 `menufrag.dispose()` / `Vars.schematics.disposePreviews()`）→ 此处 fire 属"提供 API / 保真原版形状"。

---

## G3-5 CoreItemDeliverEvent 派发（原版 CoreBlock.java:194-202）

**改动**：`world/blocks/storage/CoreBlock.java` 新增 `handleItem(Item, Tile, Tile)` 覆写（插在 `canBreak` 与内部类 `CoreEntity` 之间），并补 import（`core.Events`、`game.EventType`、`type.Item`）。

```java
@Override
public void handleItem(Item item, Tile tile, Tile source){
    if(Vars.isServer() || !Vars.isClient()){
        super.handleItem(item, tile, source);
        if(Vars.state != null && Vars.state.rules != null && Vars.state.rules.tutorial){
            Events.fire(new EventType.CoreItemDeliverEvent());
        }
    }
}
```

**⚠ 网络 API 差异**：原版判据 `net.server() || !net.active()`。本项目**没有 `net.active()`**（`Vars` 只有 `isServer()`/`isClient()`，见 `Vars.java:235/241`）→ 用等价写法 `Vars.isServer() || !Vars.isClient()`：
- 本机开服 → `isServer()` true → 入库；
- 单机 → 两者皆 false → 入库；
- 纯客机（已连远程且非主机）→ false → 不入库（由服务端同步）。

三态与原版 `server() || !active()` 完全等价（`active() = server() || client()`）。**已写入代码注释。**

**额外守卫**：`Vars.state != null && Vars.state.rules != null` —— 本项目 `Events.fire` 是同步的，且 `handleItem` 在极端早期可能被调用；`rules.tutorial` 字段本身存在（`Rules.tutorial`）。

**消费方**：`game/Tutorial.java:86` `Events.on(CoreItemDeliverEvent.class, e -> events.add("coreitem"))` → 教程 `conveyor` 步骤 `placed(conveyor,2) && event("lineconfirm") && event("coreitem")`（此前 `coreitem` 永不置位 → 该步骤永不完成）。

---

## G3-6 MusicControl / LoopControl 实例化 + 每帧驱动

| 子项 | 原版 | 本项目 |
| --- | --- | --- |
| 字段声明 | `loops` 在 `Vars.java:162`；`music` 在 `Control.java:43` | **两者都放 `Vars`**：`Vars.java:190` `music`、:196 `loops` |
| 实例化 | `Control.java:54 music = new MusicControl();`；`Vars.java:226 loops = new LoopControl();` | `Control` 构造器 `Vars.music = new MusicControl();`（`Control.java:65`）；`Vars.init()` 里 `loops = new LoopControl();`（`Vars.java:294`） |
| 每帧驱动 | `Control.update()` `Control.java:418-419` | `ClientLauncher.render()`，在 `if(!Vars.state.isMenu())` **判定之前**（`ClientLauncher.java:551-557`） |

**⚠ 刻意偏离（music 的位置，已写入代码注释）**：原版 `music` 是 `Control` 的**实例字段**，本项目按任务要求放 `Vars.music`（与 `Vars.hud`/`Vars.ui` 同风格，便于启动器统一驱动）。**实例化时机照抄原版**（仍在 `Control` 构造器里、紧随 `tutorial = new Tutorial();` 之后）。

**驱动位置理由**：原版 `music.update(); loops.update();` 在 `Control.update()` 里位于 `if(!state.is(State.menu))` 分支**之前**（菜单态也跑，`MusicControl.update()` 内部靠 `state.isMenu()` 选播菜单曲）。因此本项目也放在世界/菜单分支**之前**（紧随 `chatfrag.update()` 之后）。若按任务初稿放进世界分支，`MusicControl` 的菜单音乐分支会变成死代码。

**NPE 风险核对（任务要求）**：
- `Musics.game1` / `Musics.menu` 等：生成类 `Musics` 的每个字段都有初始化器 `new MockMusic()`（见 `AssetsAnnotationProcessor.processSounds` 的 `FieldSpec...initializer("new ...MockMusic()")`）→ **非 null**，`MusicControl.reload()` 安全。
- `MockMusic`（`core/MockMusic.java`）所有方法为 no-op → `play/setLooping/setVolume/setOnCompletionListener/stop` 均安全。
- `LoopControl.update()` 对**空** `ObjectMap` 遍历 + 读 `Core.settings` → 无副作用；`LoopControl` 无构造依赖（不依赖 `Sounds`）。
- `Vars.music` 由 `Control` 构造器 new、`Vars.loops` 由 `Vars.init()` new，**均早于 `render()`**。驱动处仍加了 `!= null` 守卫（低成本的防御，与原版"无守卫"略有差别，已在注释说明）。
- ⚠ 类初始化顺序已核对：`Vars.state`（:182）声明早于 `Vars.control`（:184）；`music`/`loops` 声明在 `control` **之后**且**无初始化器** → `Control` 构造器里对 `Vars.music` 的赋值不会被 `Vars.<clinit>` 覆盖。

**既有 TODO（未动，非 G3 范围）**：`MusicControl.update()` 里原版 `ui.deploy.isShown()/ui.editor.isShown()` 分播 `Musics.launch/editor` 的分支仍未接线（类内注释已声明）。

---

## 文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/ClientLauncher.java` | `create()` 末尾 fire `ClientLoadEvent`；`render()` 菜单判定前驱动 `Vars.music/loops`；`dispose()` 在 renderer 资源释放后 fire `DisposeEvent` |
| `core/src/com/phoenix/game/Vars.java` | +`music`/`loops` 字段；`init()` 里 `loops = new LoopControl()` |
| `core/src/com/phoenix/game/core/Control.java` | 构造器 `Vars.music = new MusicControl()`（+import）；`enterWorld()` 在 `set(playing)` 后按 `clearUnits` fire `PlayEvent` |
| `core/src/com/phoenix/game/core/GameState.java` | `set(...)` 先 fire `StateChangeEvent` 再赋值（+import `EventType.StateChangeEvent`） |
| `core/src/com/phoenix/game/core/Logic.java` | `update()` 开头 fire `Trigger.update` |
| `core/src/com/phoenix/game/game/EventType.java` | **修 import**：`ai.fsm.State` → `core.GameState.State` |
| `core/src/com/phoenix/game/world/blocks/storage/CoreBlock.java` | +`handleItem` 覆写（网络判据 + 教程下 fire `CoreItemDeliverEvent`）；+imports |

---

## 遗留风险 / 额外发现

1. **`EventType.StateChangeEvent` 的类型错误已修**（见 G3-3）——这是本批次最实质的"隐藏 bug"：定义看似齐全，实则形参类型与调用方不兼容，不修无法接线。**建议后续批次全局扫一遍其它事件类的形参类型是否也有同类误用。**
2. **原版 `Control` 构造器的两个监听器未移植**（均超出 G3 范围，未动）：
   - `StateChangeEvent` → 切状态时停所有音效 + `platform.updateRPC`（音频未启用）；
   - `PlayEvent` → `player.setTeam(assignTeam) / setDead(true) / add()`（本项目玩家创建走 `enterWorld()` 内联）。
   → 现在事件**有派发、无监听**，属"提供 API / 保真形状"，不影响现有行为。
3. **`Trigger.update` 的派发时机与原版不同**（仅游玩中，见 G3-2）。若后续有依赖"菜单态也收 update"的监听者（如脚本/成就），需把 fire 提到 `ClientLauncher.render()` 的菜单判定之前。
4. **音频系统仍未启用**：`Musics.load()/Sounds.load()` 在 `ClientLauncher.create()` 里是注释状态；`Musics/Sounds` 全部是 `MockMusic/MockSound`。因此 G3-6 的 `music/loops` 实例化当前**只有状态机推进**，听不到声音 —— 属预期，不是本批次缺陷。
5. **`LoopControl` 的消费方未接线**：原版 `TileEntity.update`（`idleSound`）与 `Player.update`（推进器/建造音）会 `loops.play(...)`，本项目这两处尚未接线（同样是音频未启用的连带）。`Vars.loops` 目前恒为空 map。
6. **`Renderer` 无 `dispose()`**（已确认）→ `DisposeEvent` 落点选在 `ClientLauncher.dispose()`，理由见 G3-4。
7. **未运行验证**：按协议禁止跑 gradlew，故以上均为静态核对（签名/import/覆写/类初始化顺序）。`CoreItemDeliverEvent` 的实际接通（教程 `coreitem` 步骤可完成）需要主会话编译 + 实跑教程确认。
