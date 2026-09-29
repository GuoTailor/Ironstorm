# F15 报告：键位系统 + 教程指引（T0-②组B/C）

日期：2026-09-30。对应 `docs/port/todo-classified.md` ②组B/C 的 4 条 TODO：

| TODO | 处理 |
| --- | --- |
| [input/KeyBinds.java:10] setDefaults/save/load 未移植 | **完成**（含消费侧接线，重绑后即刻生效） |
| [ui/dialogs/ControlsDialog.java:10] KeybindDialog 无替代层 | **完成**（完整最小版：列表 + 当前键 + Rebind/Reset + 恢复默认） |
| [game/Tutorial.java:353] `outline(String)` 空实现 | **完成**（含 UI 元素命名接线） |
| [game/Tutorial.java:347] `toggled(String)` 恒 false | **完成** |

## 一、KeyBinds 存取（input/KeyBinds.java 重写）

原版 `arc.KeyBinds` 是实例（`Core.keybinds`），支持多 Section（多人分坐）+ 手柄 DeviceType。
本项目只有单玩家键盘 → 改成**静态工具类**，Section/DeviceType 固定 `default/keyboard`，
**settings 键名格式与 arc 逐字一致**（`keybind-default-keyboard-<bind>-single/-key/-min/-max`），日后扩展多人/手柄不用改存档格式。

- 默认表：`Binding` 枚举自身（`defaultValue`），等价 arc 的 `defaultCache`；不提供 `setDefaults(...)`（枚举即全量表），`KeyBinds.init()` 承担原版 `setDefaults+load` 的调用序列。
- 覆盖表：`ObjectMap<Binding, Axis>` 只存"用户改过的"绑定，查询 miss 回默认值。
- API：`get(Binding)→Axis`、`key(Binding)→int`、`bind(binding,key)` / `bind(binding,min,max)`、`resetToDefault(s)`、`save()/load()`。
- "没存过"判定用哨兵 `UNSET = -1000000`（arc 用 `KeyCode.UNSET.ordinal()`；本项目 int 键码没有 UNSET，合法键码键盘 ≥0、鼠标 -1..-6 不会撞上）。
- `keyCodeName(int)`：键名显示。鼠标/滚轮（负数区间）给自定义名（"Mouse Left"/"Scroll"…），键盘键走 `Input.Keys.toString`。

### 消费侧接线（默认键位行为 0 变化，重绑后生效）

| 消费点 | 改动 |
| --- | --- |
| `InputHandler`（基类） | 新增 `keyDown/keyTap(Binding)`、`isButtonPressed(Binding)`、`mouseCode(int)`（libgdx Buttons 0..4 → 本项目负数鼠标键码）；`keyDown(int)/keyTap(int)` 加负数键码守卫（重绑后轴键可能落鼠标域，键盘轮询管道收不到，恒 false 防越界）。`axisX/axisY` 改走 `KeyBinds.get(move_x/move_y)` |
| `DesktopInput` | 15 处键盘键硬编码（M/T/R/Ctrl/Shift×2/Esc/F11/Space/Tab/E/C/P/F7）→ `keyTap/keyDown(Binding.xxx)`；touchDown/touchUp 的鼠标键（select/deselect/pick + 命令模式框选）→ `mouseCode(button)` 与 `KeyBinds.key(...)` 比对；`isButtonPressed(Buttons.BACK)`（相机平移）→ `isButtonPressed(Binding.mouse_move)`；`bindingKey()`（HUD 提示条键名）走 `KeyBinds.keyCodeName` |
| `ScriptConsoleFragment.keyTap(Binding)` | 原先读 `Binding.defaultValue` 的私有 helper → `KeyBinds.key(binding)` |
| `PlacementFragment.defaultKey(Binding)` | 同上（数字键快选 block_select_01..10） |
| `ClientLauncher` | `KeyBinds.init()`（fpscap 应用之后、`Vars.ui` 构造之前——ControlsDialog 构造器就会查表） |

**刻意保持硬编码（不在 126.2 的 Binding 枚举里，不参与重绑）**：Q（切命令模式，phoenix 刻意改键）、G（全选屏幕单位）、Ctrl+数字编组、命令模式的右键/中键队列（原版 v8 的 commandQueue）。均有注释。

**已知降级**：把鼠标域绑定重绑成键盘键（或反之）时，另一条输入管道收不到该键（arc 的键鼠在同一 keyDown 管道可互换，libgdx 的 `keyDown`/`touchDown` 分离）。表现为该绑定暂时失效，不崩溃；重绑界面里再绑回鼠标键即可恢复。报告此点，后续如需完整互换要在 InputProcessor 层做事件翻译。

## 二、ControlsDialog（ui/dialogs/ControlsDialog.java 重写）

FloatingDialog 里直接实现 arc `KeybindDialog` 的等价物（设置 → 控件的入口 `UI.controls` 原本已接好）：

- 列表：`ScrollPane(Styles.defaultPane)` + 按 `Binding.category()` 分组（灰字标题 + accent 分隔线，原版同款）；每行 = `keybind.<name>.name`（缺键回退首字母大写的枚举名，用 `I18n.get`）+ 当前键 + Rebind + Reset。
- 当前键显示：双键轴（move_x/move_y）显示 `min / max`（arc 的 `[red]/[]` markup 由项目 Label 解析）；单键/单键轴显示键名。
- 重绑：弹出捕获对话框（原版 rebindDialog 同型），三条输入路径都能录——键盘 `keyDown`、鼠标 `touchDown`（换算负数键码）、滚轮 `scrolled`（录成 `SCROLL`）；ESC/BACK 取消；**双键轴走原版两段式**（先负方向后正方向，第二段对话框标题带 (2/2)，用 `Gdx.app.postRunnable` 延一帧重开，对应原版 `Time.runTask(1f, ...)`）。
- Reset：单键 `resetToDefault`；表尾 `Reset to Defaults`（`resetToDefaults`）。
- 不做原版的 Section/手柄设备切换栏（本项目无手柄）。

## 三、Tutorial outline/toggled（game/Tutorial.java）

- `toggled(name)`：`findVisible(name) instanceof Button && isChecked()`（libgdx Button 同样有 isChecked）。
- `outline(name)`：把 TODO 里的原版代码原样落地——`localToStageCoordinates` 取元素左上角 → `Mathf.sin` 外扩量 + `Lines.stroke/rect` 呼吸高亮框 → `Mathf.absin` 呼吸尺寸的双层箭头（`Drawf.tri`，Pal.gray 底 + Pal.place 面），`Draw.color()` 复位（原版 `Draw.reset()`）。
- `findVisible(name)`：自写 DFS，照 arc `Group.findVisible` 的语义（先直接子节点、再递归**可见**子组；libgdx 自带的 `findActor` 不判可见性）。
- `draw()`：补了原版 `if(!Core.scene.hasDialog())` 的等价判定（复用 `DesktopInput.hasDialog()`，改 public）；浮层绘制自开 batch 会话——`Drawf.begin` 取"批次当前投影"，`stage.draw()` 结束后批次投影仍是 stage 矩阵（1:1 屏幕坐标），`Core.batch.begin()` 后直接复用；`finally` 里 `Drawf.end()`（几何落盘 + shader 抢回）+ `batch.end()`。
- 调用点：`ClientLauncher` 游戏分支 `stage.draw()` 之后（对应原版 `UI.update` 里 `scene.draw()` 之后的 `control.tutorial.draw(); Draw.flush()`）。

### UI 元素命名接线（outline 的查找目标）

| 元素 | 位置 | 对应原版 |
| --- | --- | --- |
| `category-<cat>` 分类按钮 | `PlacementFragment.rebuildCategories` | PlacementFragment.java:402 |
| `block-<block.name>` 方块按钮 | `PlacementFragment.rebuildGrid` | PlacementFragment.java:219（`block.name` 经 `MappableContent`/`transformName` 恒等，`"block-mechanical-drill"` 等名字与 outline 字符串精确匹配） |
| `blockinfo` 信息按钮 | PlacementFragment.java:758（**已有**，无需改） | PlacementFragment.java:297 |
| `waves` 波次面板 | `HudFragment` waveTable | HudFragment.java:624 |
| `pause` / `breakmode` / `confirmplace` | **未命名**（phoenix MobileInput 没有这三个按钮） | MobileInput.java:185/247、HudFragment.java:79 |

`pause/breakmode/confirmplace` 三个名字只被 `if(mobile)` 分支的 outline 消费（原版桌面端同样不画）；phoenix 移动端没有对应元素 → `findVisible` 返回 null → outline 静默跳过，行为等价于"移动端教程指引无高亮框"，不报错。桌面端（drill/blockinfo/conveyor/turret/waves/launch 各阶段）全部可画。

- 新增 `Lines.rect(x,y,w,h)`（空心矩形，arc 的 `Lines.rect` 缺失项；4 条 `line` 复用同一几何会话）。

## 四、文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/input/KeyBinds.java` | 重写：存取层（get/key/bind/reset/save/load/keyCodeName） |
| `core/src/com/phoenix/game/input/InputHandler.java` | +Binding 判定 helper、负数键码守卫、axisX/axisY 走绑定 |
| `core/src/com/phoenix/game/input/DesktopInput.java` | 15 处键盘键 + 6 处鼠标键 → Binding 查询；bindingKey 走 KeyBinds；hasDialog 改 public |
| `core/src/com/phoenix/game/ui/dialogs/ControlsDialog.java` | 重写：键位表 + 重绑捕获 + 恢复默认 |
| `core/src/com/phoenix/game/game/Tutorial.java` | draw/toggled/outline/findVisible 落地；javadoc 更新 |
| `core/src/com/phoenix/game/core/Lines.java` | +rect(x,y,w,h) |
| `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java` | 分类/方块按钮 setName；defaultKey 走 KeyBinds；清理 import |
| `core/src/com/phoenix/game/ui/fragments/ScriptConsoleFragment.java` | keyTap(Binding) 走 KeyBinds；清理 import |
| `core/src/com/phoenix/game/ui/fragments/HudFragment.java` | waveTable.setName("waves") |
| `core/src/com/phoenix/game/ClientLauncher.java` | KeyBinds.init() 插桩 + tutorial.draw() 插桩 + import |

## 五、验证情况与不确定点

- **本批未编译**（按批次协议禁跑 gradlew）。已逐 API 静态核对：`Core.settings.get/put/remove/save`、`MappableContent.name`、`transformName` 恒等、`Angles.angle(4参)`、`Mathf.sin(x,y)/absin(scl,mag)`（S5b 已对齐 arc）、`Tmp.v1/v2`、`Drawf.tri`、`Tex.whiteui.tint`、`Styles.defaultPane`、`Dialog(prompt, WindowStyle)`、`InputDevice.DeviceType.keyboard` 名为 `"keyboard"`。请主会话跑 `:core:compileJava` 核对。
- `Mathf.sin(11f, Scl.scl(4f))` 沿用 phoenix 现有 `sin(x/y)` 语义逐字照抄原版调用；该值在原版里同样是常量（呼吸效果来自 `absin` 的 Time.time 版），与原版行为一致。
- 键位重绑后的**实际生效链路**（改键 → 存档 → 下次启动载入 → DesktopInput 轮询新键）建议主会话按常规 run + 截图姿势抽验一项（如把 pause 从空格改到别的键再按空格验证失效）。
- bundle 缺键（`keybind.*`/`category.*`/`settings.rebind` 等）按 `I18n.get(key, fallback)` 回退英文，不出现 `???key???`。
- 鼠标↔键盘互换重绑的管道限制（见第一节末）。
