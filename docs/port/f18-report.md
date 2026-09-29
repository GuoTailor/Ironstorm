# F18 报告：小功能合集（②组 D/H/I，6 条 TODO，4 个主题）

日期：2026-09-30。执行：fix-f18（子代理，禁编译，静态核对）。结论：**4/4 主题全部落地，其中 bloom 管线本体按任务授权 no-op 降级（接口与挂接链路全通）**。

---

## 一、组 D：bloom 后处理管线（2 条 TODO，**降级实现**）

原版考据：
- `arc-core/src/arc/graphics/g2d/Bloom.java`（359 行）：3 个 Shader（threshold/blur/bloom 合成，资源在 `core/assets/bloomshaders/` 共 9 个 GLSL 文件）+ 3 个 FrameBuffer（original + 2 张 ping-pong）+ 全屏 quad Mesh。
- 原版消费点 `mindustry/core/Renderer.java`：
  - `init()`（:97-99）与 `resize()`（:163-168）：`settings.getBool("bloom")` 时 `setupBloom()`；
  - `setupBloom()`（:174-190）：`new Bloom(true)` + `setClearColor(0,0,0,0)`，异常时回写设置 false + `showErrorMessage("$error.bloom")`；
  - `toggleBloom(boolean)`（:191-202）：开=建实例、关=dispose；
  - 帧内 `:273-275`（单位层后）`Draw.flush(); bloom.capture();`、`:280-283`（子弹/特效层后）`Draw.flush(); bloom.render();` —— bloom 只覆盖"子弹+特效"层；
  - `dispose()`（:152-158）释放。
- libgdx 本体**无现成等价物**（gdx-vfx 是第三方依赖，按约定不引入）→ 按任务授权走**最小实现**：接口就位、本体 no-op、报告声明。

### 改动

| 文件 | 改动 |
| --- | --- |
| `graphics/Bloom.java`（新建） | no-op 空实现。接口对齐 arc Bloom 的消费子集：`(boolean useBlending)` 构造 / `setClearColor` / `capture` / `render` / `resume` / `dispose` + `blurPasses` 字段。类注释写明降级原因与**真实现步骤清单**（shader→ShaderProgram、FBO 全程 FboUtil 保存/恢复句柄+视口、resize 重建、挂接点已就位只需换本体） |
| `core/Renderer.java` | 字段 `bloom` + `bloomChecked`；`draw()` 首帧懒初始化（对应原版 init，本项目 Renderer 无 init 钩子）；帧内两个挂接点（`drawUnits()` 后 capture、`effectGroup.draw()` 后 render，各配 `Draw.flush()`，位置逐字对应原版 ：273-275/:280-283）；新增 `setupBloom()`/`toggleBloom(boolean)`/`disposeBloom()`（结构照原版 setupBloom/toggleBloom/dispose） |
| `ui/dialogs/SettingsMenuDialog.java` | bloom 复选框回调（:288）改调 `Vars.renderer.toggleBloom(val)`（判空）；类注释 TODO 条目改为"已接线+no-op 说明" |
| `ClientLauncher.java` | `dispose()` 里调 `renderer.disposeBloom()`（生命周期对齐，no-op 无资源） |

**降级声明**：设置里 bloom 复选框现在真实建/弃 `Bloom` 实例（开关链路通），但画面不变。真实现时只需替换 `graphics/Bloom` 内部，调用方零改动。风险提示（已写进类注释）：bloom FBO 与 Pixelator/LightRenderer/护盾 shieldBuffer 嵌套时必须走 `FboUtil`（libgdx `FrameBuffer.end()` 固定回绑 0，S10 教训）。

---

## 二、组 H：地图滤镜应用链（1 条 TODO，**完整实现**）

原版考据：
- `mindustry/core/World.java:201`：`loadMap(Map, Rules)` → `SaveIO.load(map.file, new FilterContext(map))`；
- `World.java:442-486`：`FilterContext extends Context`，`end()` 里取 `map.filters()`，对每个 `GenerateFilter`：`input.begin(filter, width(), height(), (x,y)->tiles[x][y])` → 双层循环 `input.apply(x,y,floor,block,overlay)` → `filter.apply(input)` → 写回 `setFloor((Floor)input.floor)` / `setOverlay(input.ore)` / block 仅在双方块都非 synthetic 时写；
- 编辑器 `MapEditor.beginEdit` 走的是**无 context** 的 `SaveIO.load(map.file)` → 编辑器路径不应用滤镜（保持此语义）。

本项目现状（移植前）：`maps/filters/` 体系完整（`GenerateFilter.GenerateInput` 已就位）、`Map.filters()`/`Maps.readFilters()` 已补、`MapIO` 已实现 TileProvider、编辑器 `MapGenerateDialog` 已有交互式 apply 链 —— 唯独缺"载入地图时应用滤镜"这一段（`GenerateInput.begin` 在世界加载路径上无调用点）。

### 改动

| 文件 | 改动 |
| --- | --- |
| `core/World.java` | 新增 `public void applyFilters(Map map)`：循环体照原版 `FilterContext.end()` 逐字（provider 用方法引用 `this::rawTile`；`tile.block()` 对应本项目 `blockRaw()`——原版 block() 是原始值、本项目 block() 会解析多格链接）。热路径下标循环 |
| `maps/Maps.java` | `loadMap(Map)`（对应原版 `World.loadMap` 的加载段）在 `SaveIO.load` 成功后调 `Vars.world.applyFilters(map)` |
| `io/MapIO.java` | 类注释 TODO 段更新为"已闭环"（`MapIO.java:44` 那条） |

注：本项目游玩加载读的是自有 PHNX 格式（原版 .msav 不可读），`maps.loadMap` 就是"游玩加载一张地图"的唯一入口，与原版消费点语义等价。

---

## 三、组 I：DatabaseDialog 复制彩蛋（2 条 TODO，**完整实现**）

原版考据：
- `mindustry/ui/dialogs/DatabaseDialog.java:72-75`：`if(keyDown(SHIFT_LEFT) && Fonts.getUnicode(unlock.name) != 0){ setClipboardText((char)getUnicode + ""); showInfoFade("$copied"); } else { content.show(unlock); }`；
- `mindustry/ui/Fonts.java:36-38`：`getUnicode(String)` = `unicodeIcons.get(content, 0)`；
- 数据源 `Fonts.loadContentIcons()`（Fonts.java:63，调用点 `core/UI.java:98`）：读 `icons/icons.properties`（每行 `码点=内容名|贴图名`，220 行），填 `unicodeIcons` 并给 icon 字体塞 Glyph。

本项目现状：无 icon.ttf 字体、无 icons.properties、`core/Fonts` 只有三个 BitmapFont 字段；DatabaseDialog 的 SHIFT 分支已存在但降级为"复制 localizedName"。

### 改动

| 文件 | 改动 |
| --- | --- |
| `assets/icons/icons.properties`（新资源，拷自原版 `core/assets/icons/`） | 220 行 码点=内容名|贴图名 映射，getUnicode 的数据源 |
| `core/Fonts.java` | 补 `unicodeIcons`（libgdx `ObjectIntMap<String>`）+ `getUnicode(String)`（原版签名逐字）+ `loadContentIcons()`。**刻意降级**：只移植"名字→码点"映射半边，不移植"给 icon 字体塞 Glyph"半边（本项目无 fonts/icon.ttf、图标体系是图集制 gen.Icon，原样抄会 NPE）；坏行跳过不抛（原版直接抛） |
| `ClientLauncher.java` | `Styles.load()` 之后调 `Fonts.loadContentIcons()`（对应原版 `UI` 初始化里 :98 的调用时机） |
| `ui/dialogs/DatabaseDialog.java` | 点击分支照原版判据接线：`isKeyPressed(SHIFT_LEFT) && Fonts.getUnicode(unlock.name) != 0` → 复制 `(char)码点` + `$copied`；替换原先"复制 localizedName"的顶替实现；类注释同步更新 |

**行为边界（已写进 DatabaseDialog 类注释）**：复制出的是码点字符本身，与原版数据/行为一致；但本项目没有 icon 字体，粘贴到聊天框不会渲染成图标——字体渲染那半边不在本项目（与彩蛋的"复制"动作无关）。

---

## 四、组 I：coreselect 写蓝图判据（1 条 TODO，**完整实现**）

原版考据：`mindustry/core/Schematics.java:347`（`create` 方法内）：收集判据
`tile.entity.block.isVisible() || (tile.entity.block instanceof CoreBlock && Core.settings.getBool("coreselect"))`
—— 核心 buildVisibility 默认 hidden → 默认不进蓝图，勾选"允许蓝图包含核心"才写入。

本项目现状：`Schematics.create`（:161）没有该判据（核心无条件进蓝图）；`SettingsMenuDialog:182` 只存值。两边 `Block.isVisible()`/`buildVisibility` 默认值语义已对齐（CoreBlock 均未覆写）。

### 改动

| 文件 | 改动 |
| --- | --- |
| `game/Schematics.java` | `create()` 在 BuildBlock skip 之后补判据（照原版 ：347 逐字，含 `Core.settings.getBool("coreselect")`）；新增 `import com.phoenix.game.core.Core` |
| `ui/dialogs/SettingsMenuDialog.java` | coreselect 的"消费方不存在"注释更新为已接线 |

附带行为对齐说明：原版 create 的 `isVisible` 判据同时挡掉"不可见但有实体"的方块（如核心）——本项目此前没有该判据，补上后与原版收集范围一致（树木/地板等无实体方块本来就被 entity==null 跳过，不受影响）。

---

## 五、TODO 关闭清单（todo-classified.md ②组）

| 条目 | 状态 |
| --- | --- |
| SettingsMenuDialog.java:44（toggleBloom 无对应物） | ✅ 已接线（管线本体 no-op 降级） |
| SettingsMenuDialog.java:289（bloom 回调只存值） | ✅ 已接线（同上） |
| io/MapIO.java:44（FilterContext 未实现） | ✅ 完整实现（World.applyFilters + Maps.loadMap） |
| DatabaseDialog.java:39（Fonts.getUnicode 缺） | ✅ 完整实现（+icons.properties 资源） |
| DatabaseDialog.java:142（SHIFT 复制分支 TODO） | ✅ 已照原版接线 |
| SettingsMenuDialog.java:180（coreselect 无消费者） | ✅ 完整实现（Schematics.create 判据） |

**统计：完成 6/6 条（4/4 主题）；降级 1 项（bloom 管线本体，接口/挂接全通）；跳过 0 条。**

## 六、不确定点

1. **未编译验证**（协议禁 gradlew）。改动均为增量式：新方法/新字段/注释，未动既有方法签名。静态核对过的引用：`struct.Array` 继承 libgdx（get/size 可用）、`MappableContent.name` 存在、`Core.settings.getBool/put/save` 存在、`World.rawTile(int,int)` 与 `GenerateInput.TileProvider` 签名匹配、`Block.synthetic()/isVisible()` 存在、`Draw.flush()` 存在、Renderer 与 Core 同包无须 import。建议主会话跑一次 `:core:compileJava` 兜底。
2. **bloom no-op 的 capture/render 挂接点**：按原版位置挂在 drawUnits 后/effectGroup 后，no-op 下 `Draw.flush()` 每帧多两次 batch flush——零渲染差异（flush 只提交批次，不改变绘制内容），真实现后这两次 flush 正是原版语义。
3. **icons.properties 的码点在无 icon 字体的环境下无渲染意义**——复制彩蛋的"复制"动作真实生效，但字符显示依赖未来是否移植字体图标渲染（已在两处类注释声明）。
4. `World.applyFilters` 假定 `Map.filters()` 每次调用返回新滤镜实例（原版语义，`Maps.readFilters` 就是每次新建）——滤镜带随机种子（randomize），重复加载同一地图会重掷种子，与原版行为一致。
