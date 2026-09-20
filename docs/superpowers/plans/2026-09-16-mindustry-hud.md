# Mindustry HUD 完整移植实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按照 `Mindustry-126.2` 的 HUD/PlacementFragment 行为，将 phoenix 当前最小 HUD 改造成截图式完整 HUD，并全程使用 libgdx。

**Architecture:** `HudFragment` 负责全局 HUD 可见性、左上波次面板、右上小地图和菜单快捷键；新增 `PlacementFragment` 独立负责右侧建造分类、方块网格、选中方块详情与建筑悬停信息。`DesktopInput` 只处理 libgdx 输入并通过 `Vars`/HUD 接口切换菜单，不依赖 arc。

**Tech Stack:** Java、libgdx Scene2D、`Stage`、独立 `ScreenViewport`、现有 `Scl/Styles/Vars/InputHandler`。

**Spec:** 本会话已确认的 HUD 设计：参考 Mindustry-126.2 职责边界；未选中对象不显示生命/电力/制造信息；Tab 关闭/打开建造菜单；右键/Esc 取消建造；R 旋转；不引入 arc。

## Global Constraints

- phoenix 是 libgdx 移植工程；所有 arc 类型必须替换为 libgdx 或现有 phoenix 最小实现。
- 修改前先更新根目录 `TASK.md`，并立即维护任务状态。
- 不主动创建测试源集；验证使用 `gradlew :core:compileJava`、`gradlew :desktop:classes` 和桌面运行日志/截图。
- `Stage` 必须使用独立 HUD 相机与 `ScreenViewport`，不能复用世界相机 viewport。
- 热路径使用 libgdx `Array` 下标循环，避免嵌套迭代器。
- 逻辑行为保持客户端/服务端一致；HUD 只读取状态，不改变战斗逻辑。
- 遵守 Java 项目规范：公共/受保护方法补 Javadoc，避免魔法值，异常不空吞。

---

### Task 1: 记录 HUD 移植任务并确认现有接口

**Files:**
- Modify: `TASK.md`（新增 2026-09-16 HUD 完整移植条目）
- Read: `core/src/com/phoenix/game/ui/fragments/HudFragment.java`
- Read: `core/src/com/phoenix/game/input/DesktopInput.java`
- Read: `core/src/com/phoenix/game/Vars.java`

**Interfaces:**
- Consumes: 当前 `HudFragment` 的 `build/show/hide`、`InputHandler.buildBlock/breaking`、`Vars.control.input`。
- Produces: 可追踪的 HUD 子任务清单，后续每完成一项立即更新状态。

- [ ] **Step 1: 在 TASK.md 添加进行中条目**

追加：

```markdown
## 2026-09-16：Mindustry HUD 完整移植

参照 `Mindustry-126.2/core/src/mindustry/ui/fragments/HudFragment.java`、
`PlacementFragment.java`、`input/DesktopInput.java`，使用 libgdx 重做右侧 HUD。

- [>] 105. 拆分 HudFragment / PlacementFragment，保留现有存档、波次、建造逻辑
- [ ] 106. 右侧建造栏：分类、4 列方块网格、材料状态、拆除按钮
- [ ] 107. 选中/悬停状态详情：未选中不显示建筑运行信息
- [ ] 108. Tab 菜单开关、Esc/右键取消建造，接入输入路由
- [ ] 109. 小地图与截图布局对齐
- [ ] 110. 编译、运行和截图验证
```

- [ ] **Step 2: 检查 TASK.md 状态和现有工作区变更**

运行：

```bash
git status --short
```

若当前目录不是 git 仓库，只记录文件状态，不执行 git 操作；不得覆盖用户已有改动。

- [ ] **Step 3: 保存任务记录后再进入代码修改**

确认 `TASK.md` 已写入后，才开始 Task 2。

---

### Task 2: 抽取 PlacementFragment，建立右侧 HUD 骨架

**Files:**
- Create: `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java`
- Modify: `core/src/com/phoenix/game/ui/fragments/HudFragment.java`
- Modify: `core/src/com/phoenix/game/ClientLauncher.java`（仅在现有构建入口需要接线时修改）

**Interfaces:**
- Consumes: `Vars.control.input`, `Blocks` 中现有方块、`Build.costText/canAfford`、`Styles`、`Scl`。
- Produces: `PlacementFragment.build(Group)`、`show()`、`hide()`、`setShown(boolean)`、`isShown()`；`HudFragment` 持有并构建它。

- [ ] **Step 1: 新建 PlacementFragment 基础类和状态字段**

实现以下字段：

```java
private static final int ROW_WIDTH = 4;
private final Array<BlockButton> blockButtons = new Array<>();
private final Array<Block> visibleBlocks = new Array<>();
private Table root;
private Table detailTable;
private Table blockTable;
private Table categoryTable;
private Category currentCategory = Category.distribution;
private boolean shown = true;
```

全部使用 `com.badlogic.gdx.utils.Array`、Scene2D `Table/Button/Label`。

- [ ] **Step 2: 构建右下角面板**

使用 `root.setFillParent(true)`，面板通过 `bottom().right()` 放到右下角；内部顺序固定为：

```text
detailTable
分隔线
blockTable（4 列）
categoryTable
actionTable
```

面板宽度使用命名常量，不使用裸数字散落在方法中；默认窗口 `900x700` 下不得覆盖整个世界区域。

- [ ] **Step 3: 将原 HudFragment 底部建造按钮迁移到 PlacementFragment**

保留已有方块入口：墙体、核心、炮塔、工厂、铜钻、铅钻、硅炉、传送带、仓库、拆除；改为方块分类网格。点击方块执行：

```java
input.buildBlock = input.buildBlock == block ? null : block;
input.breaking = false;
```

拆除按钮执行：

```java
input.buildBlock = null;
input.breaking = true;
```

- [ ] **Step 4: 从 HudFragment 删除重复底部建造栏**

保留波次、存档、读档、自动存档、玩家/核心状态等已存在功能；不再由 HudFragment 直接创建 buildButtons。

- [ ] **Step 5: 编译 core，修复仅由拆分导致的错误**

运行：

```bash
gradlew :core:compileJava
```

预期：BUILD SUCCESSFUL。

---

### Task 3: 实现 Mindustry 风格分类、方块选择和材料反馈

**Files:**
- Modify: `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java`
- Read: `core/src/com/phoenix/game/world/Block.java`
- Read: `core/src/com/phoenix/game/world/Build.java`
- Read: `core/src/com/phoenix/game/type/Category.java`

**Interfaces:**
- Consumes: `Block.category`、`Block.requirements`、`Build.costText`、`Build.canAfford`、`Items.all`。
- Produces: 每帧刷新选中状态、材料颜色、分类按钮状态；切换分类后保持当前分类选择。

- [ ] **Step 1: 实现 `getByCategory(Category)`**

遍历内容方块，过滤可用于当前 HUD 的方块；使用下标循环，按当前项目可用顺序返回。空分类按钮隐藏或禁用。

- [ ] **Step 2: 实现 4 列网格重建**

切换分类时清空 `blockTable`，为每个可见方块创建 `ImageButton` 或 libgdx `Button`；每 4 个按钮换行。按钮选中状态绑定 `input.buildBlock == block`。

- [ ] **Step 3: 实现材料颜色反馈**

每帧根据 `Build.canAfford(Team.sharded, block)` 设置白色/红色；禁止把库存、电力或敌方状态写入 HUD 的建造按钮状态。

- [ ] **Step 4: 实现分类快捷切换基础接口**

提供：

```java
public void nextCategory(InputHandler input)
public void previousCategory(InputHandler input)
public void selectCategory(Category category, InputHandler input)
```

保持当前分类的最后选择方块，行为对应参考工程 `selectedBlocks`。

- [ ] **Step 5: 编译验证**

运行：

```bash
gradlew :core:compileJava
```

---

### Task 4: 实现选中/悬停详情显示规则

**Files:**
- Modify: `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java`
- Modify: `core/src/com/phoenix/game/ui/fragments/HudFragment.java`（移除与右侧建筑状态冲突的显示）
- Read: `core/src/com/phoenix/game/world/Tile.java`
- Read: `core/src/com/phoenix/game/world/blocks/Block.java`
- Read: `core/src/com/phoenix/game/world/TileEntity.java`

**Interfaces:**
- Consumes: 鼠标屏幕坐标、`InputHandler.mouseWorldX/Y`、`World.toTile`、`Tile.block/entity/team`。
- Produces: `updateDetail()`；详情面板严格按对象状态显示。

- [ ] **Step 1: 实现世界悬停瓦片检测**

当鼠标不在右侧 HUD 控件内时，将鼠标坐标转换到世界坐标，取得 `Tile`；鼠标位于 HUD 上时清空悬停对象，避免 UI 覆盖世界详情。

- [ ] **Step 2: 实现详情状态分类**

按以下规则生成详情：

```text
input.buildBlock != null -> 显示待建造方块图标、名称、材料需求
hoverTile == null 且无 buildBlock -> 隐藏 detailTable
hoverTile.block 有己方实体 -> 显示名称、生命值；有电力/生产能力时显示对应状态
其他地形/敌方对象 -> 只显示基础名称和允许公开的信息
```

绝不能在“无选中、无悬停”时显示生命、电力、制造进度或单位数量。

- [ ] **Step 3: 实现状态条组件**

用 libgdx `Table` + 背景/填充控件模拟截图中的生命、电力、制造进度、单位数量条；状态条只在对应值存在时加入详情表。

- [ ] **Step 4: 编译验证**

运行：

```bash
gradlew :core:compileJava
```

---

### Task 5: 添加 HUD 菜单开关和输入行为

**Files:**
- Modify: `core/src/com/phoenix/game/ui/fragments/HudFragment.java`
- Modify: `core/src/com/phoenix/game/ui/fragments/PlacementFragment.java`
- Modify: `core/src/com/phoenix/game/input/DesktopInput.java`
- Modify: `core/src/com/phoenix/game/Vars.java`（若现有全局 UI 引用不足）

**Interfaces:**
- Consumes: libgdx `Input.Keys.TAB/ESCAPE/R`、`InputHandler.clearBuild()`。
- Produces: `HudFragment.toggleMenus()`、`HudFragment.shown()`、Tab/ESC 行为。

- [ ] **Step 1: 在 HudFragment 实现菜单可见状态**

增加：

```java
private boolean menusShown = true;
public void toggleMenus(){
    menusShown = !menusShown;
    placementFragment.setShown(menusShown);
}
public boolean menusShown(){
    return menusShown;
}
```

左上菜单按钮状态与 `menusShown` 同步。

- [ ] **Step 2: 暴露 HUD 引用**

优先沿用现有 `Vars` 架构；若无 UI 容器，则在 `Vars` 增加：

```java
public static HudFragment hud;
```

`ClientLauncher` 创建 HUD 后赋值。不得让 `DesktopInput` 持有 `Stage` 或直接操作 Scene2D 控件。

- [ ] **Step 3: 在 DesktopInput 添加快捷键**

在 `update()` 菜单/对话框拦截之后加入：

```java
if(keyTap(Input.Keys.TAB) && Vars.ui != null){
    Vars.ui.toggleHudMenus();
}
if(keyTap(Input.Keys.ESCAPE) && isPlacing()){
    clearBuild();
}
```

如果项目没有 `Vars.ui`，调用 `Vars.hud.toggleMenus()`，但保持输入层只依赖公开 HUD 行为。

- [ ] **Step 4: 保持右键取消和 R 旋转**

确认现有 `touchDown` 右键调用 `clearBuild()`；确认 R 只在建造状态有效，避免与暂停/其他状态冲突。

- [ ] **Step 5: 编译验证**

运行：

```bash
gradlew :core:compileJava
```

---

### Task 6: 实现右上小地图和截图式 HUD 布局

**Files:**
- Create: `core/src/com/phoenix/game/ui/fragments/MinimapFragment.java`
- Modify: `core/src/com/phoenix/game/ui/fragments/HudFragment.java`
- Read: `core/src/com/phoenix/game/core/Renderer.java`
- Read: `core/src/com/phoenix/game/world/World.java`

**Interfaces:**
- Consumes: 世界尺寸、玩家位置、地图/瓦片颜色信息。
- Produces: `MinimapFragment.build(Group)`、`show()`、`hide()`；右上固定小地图。

- [ ] **Step 1: 新建独立 MinimapFragment**

使用 libgdx `Table`/自定义 `Actor` 绘制缩略地图；不使用 arc `Minimap`。小地图绘制不改变世界相机，不处理建造输入。

- [ ] **Step 2: 添加玩家位置标记和基本地图颜色**

按世界瓦片采样绘制底色；玩家位置使用高亮标记。地图为空或未加载时隐藏而不是抛异常。

- [ ] **Step 3: 接入 HudFragment**

将小地图放在右上角；`menusShown` 只控制建造/操作菜单时，按参考实现保持小地图可独立显示，除非用户关闭完整 HUD 的总开关。

- [ ] **Step 4: 编译验证**

运行：

```bash
gradlew :core:compileJava
```

---

### Task 7: 全量编译、运行和截图验证

**Files:**
- Modify: `TASK.md`（逐项标记 105-110）
- Modify: 受验证发现影响的 HUD/输入文件

**Interfaces:**
- Consumes: Tasks 1-6 全部实现。
- Produces: 编译成功、运行日志无 phoenix 异常、HUD 行为符合设计。

- [ ] **Step 1: 编译 core**

运行：

```bash
gradlew :core:compileJava
```

预期：BUILD SUCCESSFUL，无 `No @WriteClass method`，无 arc 依赖错误。

- [ ] **Step 2: 编译 desktop**

运行：

```bash
gradlew :desktop:classes
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 自动播放运行验证**

运行：

```bash
PHOENIX_AUTOPLAY=1 gradlew :desktop:run
```

检查：无 `at com.phoenix.game` 异常；地图、单位、波次、建造栏正常加载。

- [ ] **Step 4: 手工验证 HUD 状态**

依次验证：

```text
1. 开局无选中/无悬停：右侧不显示生命、电力、制造进度、单位数量。
2. 鼠标悬停己方建筑：显示名称和对应生命/电力/生产信息。
3. 点击建造方块：显示待建造方块信息和材料需求。
4. Tab：右侧建造/操作菜单隐藏，再按 Tab 恢复。
5. Esc/右键：取消建造或拆除模式。
6. R：可旋转方块方向。
7. 鼠标滚轮缩放世界：HUD 不随世界缩放。
8. 窗口 resize：HUD 保持边缘布局，世界相机不重置出生点。
```

- [ ] **Step 5: 更新 TASK.md 完成状态**

全部验证通过后，将 105-110 改为 `[x]`；若存在明确限制，写入“已知限制”，不得伪报成功。

- [ ] **Step 6: 最终检查 arc 依赖**

运行：

```bash
grep -R "import arc\\.\|arc\\." core/src/com/phoenix/game/ui core/src/com/phoenix/game/input
```

预期：没有新增 arc 引用；已有生成包 `io.anuke.mindustry.gen` 只在项目既有兼容层中保留，不作为 HUD 实现依赖。
