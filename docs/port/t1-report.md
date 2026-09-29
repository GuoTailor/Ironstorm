# T1 过时 TODO 清理报告

> 来源：`docs/port/todo-classified.md` ①组"过时"（11 条）。
> 日期：2026-09-30。执行：fix-t1 子代理。
> 协议：只动注释/TODO；唯一运行逻辑改动是第 4 条 `Block.useContent` 补接线（任务授权）。未跑 gradlew，未做 git 操作。

## 处理总览

| # | 位置（原行号 → 现行号） | 原 TODO/过时说法 | 处理方式 |
| --- | --- | --- | --- |
| 1 | Mechs.java:27-29 → :27-28 | 类 javadoc"其实早已在 Bullets 注册，只是本类的 TODO 未同步更新" | **改写**：去掉历史叙事，直接陈述现状（6 种弹药均在 Bullets 注册并赋给 weapon.bullet） |
| 2 | Mechs.java:89 → :88 | `//F12b：原版弹药（此前 TODO 说"未移植"是过时信息，Bullets.standardMechSmall 早已存在）` | **简化**为 `//原版弹药（F12b）`（下一行即 `bullet = Bullets.standardMechSmall`，自明） |
| 3 | Mechs.java:126 → :125 | 同款（Bullets.lightning） | 同上 |
| 4 | Mechs.java:178 → :177 | 同款（Bullets.healBullet） | 同上 |
| 5 | Mechs.java:236 → :235 | 同款（Bullets.missileSwarm） | 同上 |
| 6 | Mechs.java:337 → :336 | 同款（Bullets.missileJavelin） | 同上 |
| 7 | Mechs.java:458 → :457 | 同款（Bullets.standardGlaive） | 同上 |
| 8 | MusicControl.java:56 → :56-58 | "原版依赖 Vars.ui.deploy / Vars.ui.editor 的 isShown()（UI 系统尚未移植）" | **改写**：删掉"UI 尚未移植"的过时事实；保留"该分支尚未接线、菜单态只播 Musics.menu"的真实现状声明 |
| 9 | Bresenham2.java:9 → :9 | "Placement 里也有同名 TODO（lineNoDiagonal）" | **改写**：lineNoDiagonal 已在 `input/Placement` 内联实现（Placement.java:70），引用改为"已在 input/Placement 内联实现，不经过本类" |
| 10 | Block.java:1299(实际:1343-1355) → :1343-1363 | useContent 留空的理由"GlobalData 无 isUnlocked/unlockContent（grep 已证）" | **补接线**（详见下节） |
| 11 | SaveVersion.java:54 → :54 | "version/build/mods/stats/rules 全部 TODO" | **改写**：按现状陈述——build/stats/rules 已在 writeMeta/readMeta 直接读写（build=Version.build、stats/rules 走 JsonIO，:160/:172-173/:190-191），mods 恒写 `"[]"`（:175，mod 降级）；这几项不走 SaveMeta |

"已消失"条目：0 条（11 条全部存在且与分类描述相符；仅 Block.java useContent 的实际行号是 1343 而非清单写的 1299，行号漂移）。

## 第 4 条：Block.useContent 补接线

**现状确认**（grep）：
- `GlobalData.isUnlocked`（GlobalData.java:84）/ `unlockContent`（:93）已在 S4a 落地，全工程 20+ 调用点；留空理由（"缺依赖"）已不成立。
- `Block.useContent` 调用点 5 处：SolidPump:223、Separator:133、Pump:199、LiquidConverter:76、GenericCrafter:110（均在方块 update 内高频触发）。

**原版参照**：
- `mindustry/world/Block.java:374-379`：`if(!headless && tile.getTeam() == player.getTeam() && world.isZone()) logic.handleContent(content);`
- `mindustry/core/Logic.java:96-100`：`handleContent` = `if(!headless) data.unlockContent(content);`
- 即原版完整语义 = 三条件成立时 `data.unlockContent(content)`。

**本项目实现**（Block.java:1356-1363）：照原版三条件 + 直接调 `Vars.data.unlockContent(...)`：
- 不走 `ContentLoader.handleContent`（它是"注册内容"，与解锁同名但无关）。
- 参数保持刻意偏离的 `MappableContent`（Liquid 继承 MappableContent 而非 UnlockableContent），用 `instanceof UnlockableContent` 过滤 → 液体产出调用安全跳过，物品（Item extends UnlockableContent）正常解锁。
- 高频调用安全：`GlobalData.unlockContent` 内部按 `set.add` 防重复，首次解锁才 `onUnlock()` + `Events.fire(UnlockEvent)`（与原版 :149-153 等价）。
- 与文件既有风格一致：`Vars.*` 用全限定（Block.java 无 `import Vars`，仅 static import tilesize）。

**效果**：战役（zone）模式下玩家方生产物品即解锁该内容（原版"生产内容即解锁"链路补全；分类清单 ② 中提到该缺口，接线即消）。

## 修改文件清单

- `core/src/com/phoenix/game/content/Mechs.java`（7 处，纯注释）
- `core/src/com/phoenix/game/game/MusicControl.java`（1 处，纯注释）
- `core/src/com/phoenix/game/math/geom/Bresenham2.java`（1 处，纯注释）
- `core/src/com/phoenix/game/world/Block.java`（1 处，javadoc 改写 + useContent 接线）
- `core/src/com/phoenix/game/io/SaveVersion.java`（1 处，纯注释）

## 验证

- 静态自检：接线用到的 `Vars.headless/player/data/world`（Vars.java:103/186/202/180）、`Tile.getTeam()`（Block.java:1287 同款用法）、`World.isZone()`（ItemSelection.java:61 同款用法）、`GlobalData.unlockContent` 均已 grep 证实存在；instanceof/强转与 UnlockableContent 继承链（extends MappableContent，:23）核对无误。
- 按批次协议未运行编译；建议主会话合并后统一 `gradlew :core:compileJava` 复核。
