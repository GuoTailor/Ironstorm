# Mindustry 全量移植收尾（Full-Port Completion）实施方案

> **For Claude（执行协调者）：** 本文件是**唯一协调载体**（取代已丢失的 plans/phoenix-remaining-port.md）。
> 执行模式 = 主会话调度 + 子代理逐批推进（superpowers:subagent-driven-development）。
> 每完成一批：主会话编译/验证/提交 → 勾选本文件 → 按子代理反馈**修订后续批次**再派发下一批。

**目标：** 把 phoenix（Mindustry Build 126.2 → libgdx 手工移植）从"文件级/内容名零缺口"推进到
**行为级零缺口**：缺失功能补齐、TODO 全部消化、数值与原版一致、残留清零。

**当前基线（2026-09-29）：**
- 文件级移植已完成（512 文件，零 arc 依赖）；S1~S10 批次（缺失子系统）全部完成
- 本会话已修：联机出生单位 dagger→dart-ship（NetServer.respawnPlayer）、玩家移动加速度模型（InputHandler/BaseUnit/FlyingUnit/UnitTypes）
- core TODO 剩 **254** 条（峰值 1169）
- 验证手段齐备：`PHOENIX_AUTOPLAY/HOST/JOIN/SHOT/BLKSIZE/BLKDATA/UNITDATA`（见 CODEBUDDY.md）

---

## 铁律速查（每个子代理都必须遵守；详见 CODEBUDDY.md 与 转换.md）

1. **参照工程只读**：`D:\IdeaProjects\Mindustry-126.2`；不引入 arc，一律 libgdx。
2. **子代理禁止运行 gradlew / gradle**（编译与运行验证由主会话统一做）。
3. **移植判据**：能照抄就照抄（保留原类名/字段名/逻辑结构）；**没有注释声明偏离的，一律按原版改**（Mender 判据）；数值/耗电一律照抄原版，不要自行折算。
4. **已知移植坑速查**（动手前先读 转换.md 对应条目）：
   - `Time.delta()` 是方法不是字段（= getDeltaTime×60，随帧率缩放，60fps 时=1.0）
   - `Bullet.remove()` 池化清 type → 回调先取局部变量；热路径用下标循环（libgdx Array 迭代器不能嵌套）
   - `@Remote` 注解必须保持注释状态；`Scl.scl()` 不能在类初始化阶段调用
   - `EntityGroup.add/remove` 是延迟提交；实体的 `removed()` 同步执行（池化实体回收后判空）
   - 本项目 `worldx()` 是角点（原版是中心）→ 方块中心用 `Block.centerX/centerY`；`rotation` 0=上（原版 0=右，差 90°）；d4 旋向相反（带符号索引差公式会左右翻转）
   - 本项目 `acceptItem` 默认"有空间就收"（与原版相反）→ 纯产出方块要覆写
   - i18n bundle 是裁剪过的：缺键返回 `???key???`，不能用 `get(key, fallback)`
   - `getDataDirectory()` = assets 根，**绝不能整体删**
5. **验证纪律**：日志只用 ASCII（中文控制台乱码）；调试钩子用完必删；截图 `PHOENIX_SHOT>=140`（开局降落动画 ~120 帧）；验证计时在单命令内取样；平台/渲染改动必须实跑。
6. **新增贴图必须同时拷进 `sprites_out/`** 并重打图集（texturePacker），重打后用区域名集合 diff 校验。
7. **存档格式**：Rules 走 SaveMeta.RulesMeta 手工白名单（加字段同步三处）；改格式改 `SaveIO`。

---

## 执行协议（每一批都一样）

**主会话（调度者，保持上下文干净）：**
1. 派发**一个**子代理（general-purpose），brief = 本文件对应批次段落 + 让它自己读铁律与参照文件
2. 收报告（子代理把详细产出写到 `docs/port/` 下的清单/报告文件，**只回 ≤200 字摘要**）
3. `git diff --stat` 核对改动范围 → `gradlew :core:compileJava :desktop:classes` 编译
4. 按批次类型做运行验证（dump 对照 / autoplay 实跑 / 截图 / 联机冒烟）
5. 编译错误只看第一条修复或退回子代理；验证通过 → `git commit`（一批一提交，格式 `port(<批号>): <摘要>`）
6. 勾选本文件对应批次；**把子代理报告中的新发现回写到"批次登记表"**（必要时插入新批次）
7. 派发下一批

**子代理（执行者）：**
- 只做本批 brief 范围内的事；改码 + 把详细清单/对照表写到 `docs/port/<批号>-*.md`
- 报告格式（回给主会话，≤200 字）：
  `改动文件 N 个（列表见 docs/port/X-report.md）；照抄/偏离要点；编译自查未跑（按禁令）；新发现的问题 M 条（已写入报告）；遗留。`
- 严禁：跑 gradle、改 brief 范围外的文件、留下调试钩子、删 TODO 标记（除非该批目标就是消化 TODO）

---

## 批次登记表（动态：新发现的问题追加到这里，主会话统一排期）

| 编号 | 类型 | 内容 | 状态 |
| --- | --- | --- | --- |
| A1 | 审计 | 实体/AI/游戏逻辑包功能对照（entities/ai/game） | ☑ 缺失15/偏离6/断裂4 → docs/port/audit-A1-entities-ai-game.md |
| A2 | 审计 | 世界/内容包功能对照（world/content） | ☐ |
| A3 | 审计 | UI/输入/渲染/核心包功能对照（ui/input/graphics/core/meta/net） | ☐ |
| N1 | 数值 | 运行时 dump 三方对照（BLKSIZE/BLKDATA/UNITDATA ↔ 原版源码 ↔ 图集）修复差异 | ☐ |
| T0 | 清理 | 254 条 TODO 全量分类（过时/缺失功能/刻意偏离/待议）→ docs/port/todo-classified.md | ☐ |
| T1~Tn | 清理 | 按分类逐批消化 TODO（过时注释直接修；缺失功能转 F 批） | ☐ |
| F1 | 功能 | **单位物理五项**（A1 高）：卡墙 kill / 深水溺亡 drownTime / 出界 kill+warp 推回 / spawner 失效 kill / 液体地板状态+伤害+walkEffect（原版 Unit.java:99,183,196-216,236-260,288） | ☐ |
| F2 | 功能 | **开波整链**（A1 高）：出生点清场（spawnShockwave+全额伤害）、延迟分批出生（Time.run+spawn 特效）、attack 模式核心增援（WaveSpawner.java:72-150） | ☐ |
| F3 | 功能 | **Lightning 完整化**（A1 高）：沿途 damageLightning 子弹 + 30 格链式跳跃（maxChain 8）+ insulated 绝缘墙拦截（Lightning.java:71-115） | ☐ |
| F4 | 功能 | **破坏表现链**（A1 中）：Block.onDestroyed 库存爆炸/液体泼洒/电力缓冲（Block.java:604-647）、breakSound、单位 deathSound、死亡携带物品殉爆（Unit.java:99-101） | ☐ |
| F5 | 功能 | **子弹对空语义**（A1 高，小改）：collides() 改 `!(flying && !collidesAir)`、collideGroups 方向 (bullet,unit)（Bullet.java:168-170） | ☐ |
| F6 | 功能 | **Damage 家族**（A1 中）：tileDamage 菱形蔓延、calculateDamage 距离衰减+击退、dynamicExplosion 分层时序+震屏上限（Damage.java:33-274） | ☐ |
| F7 | 功能 | **断裂接线四件**（A1 中，小改）：launchWaveMultiplier 消费、Zone.updateWave/bestWave、护甲减免 calculateDamage、Fire.extinguish 发 fireExtinguish + Fire 接已移植的 Puddle | ☐ |
| F8 | 功能 | **索敌语义**（A1 中）：derelict 豁免、findEnemyTile tilePred、targetClosest 用 max(weapon.range, type.range)（Units.java:84-150、BaseUnit.java:181-186） | ☐ |
| F9 | 功能 | **状态存档与染色**（A1 中/低）：Statuses writeSave/readSave、getStatusColor | ☐ |
| F10 | 功能 | A2 审计产出（待 A2 完成后登记） | ☐ |
| F11 | 功能 | A3 审计产出（待 A3 完成后登记） | ☐ |
| F12 | 功能 | T0 分类产出的"缺失功能"项（待 T0 完成后登记） | ☐ |
| F-LOW | 功能 | 低优先级合集（A1 表尾）：EventType 缺失事件类、Schematics.overwrite、SpawnGroup 序列化、SoundLoop 工作音效、GlobalData 资源池、TileEntity 休眠优化 | ☐ |
| R1 | 残留 | 死代码/占位实现/`// removed` 残留清扫（含 S3d-5 主菜单背景） | ☐ |
| V1 | 终验 | 全量验证：编译绿 + autoplay 实跑 + 截图核对 + 联机冒烟 + 数值 dump 复检 | ☐ |

---

## P0 基线（主会话，开工前）

- [x] 写本方案
- [ ] `git add -A && git commit`（基线提交，含本方案；此后一批一提交可回滚）
- [ ] 建立 `docs/port/` 目录

## A1 审计批：实体 / AI / 游戏逻辑（Explore 子代理，只读）

**范围：** `core/src/com/phoenix/game/{entities,input 之外的核心逻辑,ai,game}` ↔ 原版
`core/src/mindustry/{entities,ai,game,core(Logic部分)}`。
（entities 含 traits/type/bullet/effect/units 子包）

**做法：**
1. 逐文件对照：原版 public 方法/字段/内部类 → 本项目是否存在、语义是否一致
2. 重点嗅探：原版有而本项目**无注释声明**就缺失/偏离的行为（调用链断裂、空实现、占位 TODO）
3. 产出 `docs/port/audit-A1-entities-ai-game.md`：表格（原版位置 | 本项目状态[完整/偏离/缺失] | 证据 | 建议）
4. 回报 ≤200 字：缺失 N 条 / 偏离 M 条 / 最严重的 3 条

## A2 审计批：世界 / 内容

**范围：** `world/{blocks,power,modules,meta}` + `content/*` ↔ 原版 `world/**` + `content/**`。
做法同 A1（注意：内容类的"刻意偏离"清单先读本文件铁律 4 与各类注释；MechPad/PowerVoid 等差异是刻意的）。
产出 `docs/port/audit-A2-world-content.md`。

## A3 审计批：UI / 输入 / 渲染 / 核心

**范围：** `ui/**`、`input/**`、`graphics/**`、`core/**`、`net/**` ↔ 原版 `ui/**`、`input/**`、`graphics/**`、`core/**`、`net/**`、`io/**`。
UI 类以"功能等价"判（arc→libgdx 已知缺口见 转换.md，不算缺失；但**交互行为**缺失要记）。
产出 `docs/port/audit-A3-ui-input-graphics.md`。

## N1 数值保真批

1. 主会话跑 dump：`PHOENIX_BLKSIZE=1` / `PHOENIX_BLKDATA=1` / `PHOENIX_UNITDATA=1` 各一次，输出存 `docs/port/dump-*.txt`
2. 子代理：dump ↔ 原版 `content/*.java`（含写在辅助方法/方块类构造器里的 size/health）↔ `core/assets-raw` 贴图尺寸三方交叉，产出差异清单并修复
3. 主会话复跑 dump 核对归零

## T0 TODO 分类批

子代理：把 core 全部 254 条 TODO 按「①过时注释（描述的事已在别处/已完成）②缺失功能（真没写）③刻意偏离声明 ④待议」分类，
输出 `docs/port/todo-classified.md`（每条带 文件:行 + 一句话）。①类转 T 批直接修，②类转批次登记表排 F 批。

## T1~Tn TODO 消化批（每批 30~60 条同域）

按 T0 分类，按包分批：过时注释改为"事实陈述"或删除；每批报告里列出②类转登记表。主会话编译 + 抽查。

## F* 缺失功能批（动态）

来源：A1~A3 审计清单 + T0 的②类。**每批做完，主会话根据报告与登记表重新排下一批**（这就是反馈改进环）。
每批 brief 临时拟定，包含：原版参照文件、本项目落点、验证方式（能实跑验证的必须实跑）。

## R1 残留批

死代码 / 占位实现 / `// removed` 注释 / 无主 TODO 扫尾；**S3d-5 主菜单背景**（原版 Menus.menuBackground 之类的动态背景，用图集/UI 资源按原版重做，能实跑截图验证）。

## V1 终验批（主会话主导，子代理协助修问题）

1. `gradlew :desktop:dist` 打包成功
2. autoplay 实跑 300 帧 + `PHOENIX_SHOT=150` 截图逐项核对（世界/HUD/单位/特效）
3. 联机冒烟：`PHOENIX_HOST=1` + `PHOENIX_JOIN`，客户端日志 `[客户端] 获得玩家单位 type=dart-ship`
4. 数值 dump 复检归零；TODO 归零（或全部有"刻意偏离/待议"声明）
5. Android 端 `:android:assembleDebug` 装机冒烟（JDK 17，MuMu 模拟器）

---

## 进度日志（每批一行，主会话维护）

- 2026-09-29 方案建立；基线提交 9bdbefc；派发 A1
- 2026-09-29 A1 完成（缺失15/偏离6/断裂4，报告 docs/port/audit-A1-entities-ai-game.md）；登记 F1~F9/F-LOW；派发 A2
