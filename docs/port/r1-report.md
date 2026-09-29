# R1 残留清扫报告

批次负责人：fix-r1（2026-09-30）
范围：S3d-5 主菜单背景核对、A2 两项死成员处理、全仓死代码/占位/removed 残留清扫。

## 结论速览

| 项 | 结论 | 动作 |
| --- | --- | --- |
| S3d-5 主菜单背景 | **已完整移植**（此前批次完成，登记过时） | 仅补 dispose 生命周期接线 |
| A2：StatusEffect.reactive | 审计有误：原版 126.2 无此字段；本项目全工程无消费者 | 删字段 + 2 处赋值 |
| A2：Edges.getPixelPolygon | 方法在本项目不存在（原版也没有），全工程 grep 零匹配 | 无需处理，条目作废 |
| 残留清扫 | `// removed`/`TODO 删除` 全仓零匹配；仅 MenuFragment 3 处调试 println 是残留 | 删 3 处 println，其余保留（理由见下） |

## 1. S3d-5 主菜单背景：核对确认已完成

静默缺失扫描登记"只剩 S3d-5"已过时。实际状态：

- `core/src/com/phoenix/game/graphics/MenuRenderer.java`（535 行）：完整移植原版 `MenuRenderer`（随机地形生成 `generate()`、三缓冲烘焙 `cache()`（原版 CacheBatch→FrameBuffer 替代，类头注释有完整偏差说明）、`render()`、飞行单位 `drawFlyers()`、`dispose()`）。
- `MenuFragment.java:33/47/53`：懒创建挂点 `renderer()` + `dispose()`（构造早于内容加载，MenuRenderer 需要 Blocks/UnitTypes → 必须懒创建，注释已说明）。
- `ClientLauncher.java:619-625`：菜单分支、`stage.draw()` 之前调用 `menufrag.renderer().render()`，带 content 就绪守卫。

### 本次唯一补丁：dispose 接线

`MenuFragment.dispose()` 此前**全工程无消费者**——MenuRenderer 的三张 FrameBuffer（floorBuffer/wallBuffer/shadows）在整个进程生命周期不释放。原版由 Core 的 dispose 链调 `MenuFragment.dispose()`；本项目 `ClientLauncher.dispose()`（ApplicationListener 生命周期钩子，已有 minimap/pixelator/blocks/bloom/schematics 一串接线）漏了这一条。

修复（`ClientLauncher.java` dispose() 内，schematics 之后）：

```java
//主菜单背景的三张 FBO（MenuRenderer.floor/wall/shadows；对应原版 MenuFragment.dispose）
if(menufrag != null) menufrag.dispose();
```

## 2. A2：StatusEffect.reactive 已删除

**审计证伪**：`grep -rn reactive D:\IdeaProjects\Mindustry-126.2\core\src` → **零匹配**。原版 126.2 的 `StatusEffect` 根本没有 `reactive` 字段（那是更新版本的概念；A2 报告"原版有 reactive（反转状态语义）"不成立）。

本项目侧 `grep -rn reactive core/src` → 仅 2 文件 3 处，全部无消费者：

- `type/StatusEffect.java:23` 字段声明
- `content/StatusEffects.java:27`（burning）赋值
- `content/StatusEffects.java:80`（melting）赋值

处理：三处全删。无反射/按名调用风险（字段名纯 Java 访问，已全仓 grep）。这同时回归了"按原版"原则——删除的是本项目自创的死字段。

## 3. A2：Edges.getPixelPolygon 不存在

`core/src` grep `getPixelPolygon`/`pixelpolygon`（忽略大小写）→ 零匹配。本项目 `world/Edges.java` 只有 `getEdges/getInsideEdges/getFacingEdge`（与原版一致）；原版 `Edges` 同样没有 `getPixelPolygon`。审计条目所指方法从未被移植、也无需删除，条目作废。

## 4. 残留清扫

### 已删（3 处，均在 `ui/fragments/MenuFragment.java`）

| 位置 | 内容 | 理由 |
| --- | --- | --- |
| fadeInMenu() 尾 | `System.out.println("fadeInMenu")` | 调试残留，无信息量 |
| fadeOutMenu() 尾 | `System.out.println("fadeOutMenu")` | 同上 |
| buttons() 点击回调首 | `System.out.println(event + " " + x + " " + y)` | 每次点菜单按钮都打印事件坐标，纯调试 |

### 保留（grep 过、声明理由）

- **`BaseUnit.write/read`（BaseUnit.java:1243/1247，空实现）**：注释自述"死代码，只在子类 super 链互相调用"，但它们对应原版 `Unit.write/read`（SyncTrait 序列化骨架），BuilderDrone/MinerDrone/RepairDrone 的覆写经 super 链调用，属**结构性占位**——单位同步移植时的挂点，按"原版框架方法不删"保留。
- **`FileChooser.print()`（FileChooser.java:395，无人调用）**：原版自带调试方法（原版 FileChooser.java:295 一模一样），忠实移植，不删。
- **`Vars.itemSize`（"TODO remove"注释）**：原版同字段同注释（原版自带的 TODO），保留。
- **`MapIO` 实现 `GenerateInput.TileProvider`**：注释里提到原版同名嵌套接口是死代码，但本项目 D1c/F18 已把该接口激活（编辑器滤镜链消费），非死代码。
- **ClientLauncher 的 `DEBUG-SHOT`/`DEBUG-LOGTEST`/watchdog 钩子**：CODEBUDDY.md 约定的长期调试设施，不删。
- **全仓 `// removed`、`TODO 删除`、`TODO.*删/移除`**：零有效匹配（MapEditorDialog:321 的"该字段删除"指"不引入原版字段"，非残留；TimeTrait:12 是"等 Entity 补齐后可删"的过渡声明，现在动不了）。
- **`System.out.println` 其余 ~50 处**：联机/开局/波次等运维日志（`[服务器]`/`[客户端]`/胜负判定等），是长期日志设施，不是残留。

## 改动文件清单

| 文件 | 改动 |
| --- | --- |
| `core/src/com/phoenix/game/type/StatusEffect.java` | 删 `reactive` 字段 |
| `core/src/com/phoenix/game/content/StatusEffects.java` | 删 burning/melting 两处 `reactive = true;` |
| `core/src/com/phoenix/game/ClientLauncher.java` | dispose() 接线 `menufrag.dispose()` |
| `core/src/com/phoenix/game/ui/fragments/MenuFragment.java` | 删 3 处调试 println |

## 未验证项 / 风险

- 本批禁 gradlew → 未编译。风险极低：reactive 是纯字段删除（消费者 grep 为零）；dispose 接线调用的方法签名稳定；println 删除无行为影响。
- MenuRenderer 本体未做运行期验证（超本批权限，且禁编译）；其移植完整性以代码考据为准（结构、原版对应注释、挂接链齐全）。
