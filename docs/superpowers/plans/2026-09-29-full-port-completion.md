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
| A2 | 审计 | 世界/内容包功能对照（world/content） | ☑ 缺失4/偏离4/断裂3 → docs/port/audit-A2-world-content.md |
| A3 | 审计 | UI/输入/渲染/核心包功能对照（ui/input/graphics/core/meta/net） | ☑ 缺失8/偏离2/断裂3 → docs/port/audit-A3-ui-input-graphics.md |
| N1 | 数值 | 运行时 dump 三方对照（BLKSIZE/BLKDATA/UNITDATA ↔ 原版源码 ↔ 图集）修复差异；已知待修：burning.damage、wet.speedMultiplier | ☑ 检查161/不一致23/全修/复验归零 → docs/port/n1-report.md（提交 4849e59）；根因=8 个方块类构造器自设默认 health |
| T0 | 清理 | 254 条 TODO 全量分类（过时/缺失功能/刻意偏离/待议）→ docs/port/todo-classified.md | ☑ ①过时11 ②缺失26 ③刻意偏离217 ④待议0 |
| T1 | 清理 | **T0-① 过时 11 条**：Mechs 弹药 javadoc×7、MusicControl 接 UI 可见性、Bresenham2 注释、**Block.useContent 接解锁链**（GlobalData.isUnlocked/unlockContent 已在）、SaveVersion javadoc 按现状改写 | ☑（提交 2a700d3）；11 条清理 + useContent 真接线（!headless && 玩家队 && isZone → data.unlockContent，instanceof 过滤液体）；autoplay 冒烟无异常 |
| T1~Tn | 清理 | 按分类逐批消化 TODO（过时注释直接修；缺失功能转 F 批） | ☐ |
| F1 | 功能 | **单位物理五项**（A1 高）：卡墙 kill / 深水溺亡 drownTime / 出界 kill+warp 推回 / spawner 失效 kill / 液体地板状态+伤害+walkEffect（原版 Unit.java:99,183,196-216,236-260,288）；**含 Block.unitOn 钩子与 Conveyor 单位推动**（A2：Conveyor.java:141，原版 Unit.java:226 调用点） | ☑ 六项全落（提交 410166c）；FlyingUnit.updateVelocity 冗余覆写删除改走基类飞行分支；**实测：深水 dagger ~f230 溺亡、飞行玩家悬停满血** |
| F2 | 功能 | **开波整链**（A1 高）：出生点清场（spawnShockwave+全额伤害）、延迟分批出生（Time.run+spawn 特效）、attack 模式核心增援（WaveSpawner.java:72-150） | ☑（提交 02e230c）；**实测：wave+0/+10 敌人 0 → +100 出齐 4 个（延迟分批），清场未误伤玩家**；附带修 groundSpawns 空 early-return（attack 图跳过增援） |
| F3 | 功能 | **Lightning 完整化**（A1 高）：沿途 damageLightning 子弹 + 30 格链式跳跃（maxChain 8）+ insulated 绝缘墙拦截（Lightning.java:71-115） | ☑（提交 3adba0d）；**实测：3 连纵队每只均匀 3 击（130→100）**；insulated 纠正=塑钢墙/PowerDiode（非 surge），SurgeWall/ShockMine 补偿代码已回退 |
| F4 | 功能 | **破坏表现链**（A1 中）：Block.onDestroyed 库存爆炸/液体泼洒/电力缓冲（Block.java:604-647，配套 ItemModule.sum/has/addAll——A2）、breakSound、单位 deathSound、死亡携带物品殉爆（Unit.java:99-101） | ☑（提交 2fde8a7）；ItemModule 零修改（126.2 链不用 sum/hasAll）；实测：塞 100 blastCompound kill→瓦片变 air 无异常、dagger 死亡殉爆 health=-18.8 无异常；**dynamicExplosion 已随 F4 补全**（含震屏上限 9f），F6 只剩 tileDamage + Damage.calculateDamage 衰减 |
| F5 | 功能 | **子弹对空语义**（A1 高，小改）：collides() 改 `!(flying && !collidesAir)`、collideGroups 方向（Bullet.java:168-170）——实际执行时 collideGroups 实参顺序**保持本项目对调**（同步回收子弹的既声明偏离，仅补注释）；BaseUnit.collides 照原版 Unit.collides 重写 | ☑ |
| F6 | 功能 | **Damage 家族**（A1 中）：tileDamage 菱形蔓延、calculateDamage 距离衰减+击退、dynamicExplosion 分层时序+震屏上限（Damage.java:33-274） | ☑（提交 f284a92）；tileDamage 照原版 214-255 逐行 + dynamicExplosion 多波伤建筑接线 + 静态入口对齐（删 damageAll）+ 核爆半径漏乘 tilesize 真 bug；**实测墙 320→168 距离衰减生效**；顺带 fix IntQueue.resize 满环越界（实测抓到）|
| F7 | 功能 | **断裂接线四件**（A1 中，小改）：launchWaveMultiplier 消费、Zone.updateWave/bestWave、护甲减免 calculateDamage（**补 freezing.armorMultiplier=0.8**——A2）、Fire.extinguish 发 fireExtinguish + Fire 接已移植的 Puddle | ☑（freezing 字段早已就位、仅缺 damage 接线；collideGroups 保持对调为已声明偏离 → docs/port/f5f7-report.md） |
| F8 | 功能 | **索敌语义**（A1 中）：derelict 豁免、findEnemyTile tilePred、targetClosest 用 max(weapon.range, type.range)（Units.java:84-150、BaseUnit.java:181-186） | ☑（提交 3f21acb）；derelict 三入口早退 + findEnemyTile/findAllyTile + tilePred/closestTarget 6 参 + closestRange=max(weapon.bullet.range, type.range)（FlyingUnit 自创 700f 口径一并替换）；LOGTEST 冒烟无异常 |
| F9 | 功能 | **状态存档与染色**（A1 中/低）：Statuses writeSave/readSave、getStatusColor | ☑（提交 328cd35）；SaveIO v5→v6（接线 writeEntities/readEntities，type==null 空读防流错位）；getStatusColor 简单平均（原版即如此，未接 draw 链因原版 126.2 也零调用）；**实测：burning→存→读→敌方单位状态恢复 1 项，v5 旧档正确拒绝**；Color.white→WHITE 编译修正 1 处 |
| F13 | 功能 | **输入/UX 补齐**（A3）：解锁提示 showUnlock（Control 监听 UnlockEvent + HudFragment，断裂）、E 键暂停建造队列（Player.isBuilding/buildWasAutoPaused + buildautopause 消费，断裂）、P 键地图截图（takeMapScreenshot）、中键吸取方块（Binding.pick）、R 键旋转已放置方块（rotateplaced/rotateBlock，注意项目现把 R 用作放置朝向——按原版 DesktopInput.java:225-227 恢复语义并共存）、drawConfigure 配置叠加绘制（Block+ItemBridge/MassDriver/PowerNode）、buildUI 建造提示条、投递物品光标预览（isDroppingItem 消费）、Ctrl 对角放置、canShoot 输入层守卫（hasMouse/onConfigurable/isDroppingItem） | ☑（提交 3c2f9c5）；arc setTranslation/translateBy→libgdx fadeIn/fadeOut 等价 + 新增 ui/MenuFragment.Act（每帧跑 updater）；autoplay 冒烟无异常 |
| F14 | 功能 | **地图缩略图链**（T0-②组A，8 条）：Maps.queueNewPreview 生成队列（参照原版 Maps#queueNewPreview/MapPreviewLoader，SavePreview 的 FBO 思路可复用）→ map.texture 就绪后 MapsDialog/CustomGameDialog/MapPlayDialog/MapLoadDialog 四处自动出图 | ☑（提交 b04fa57）；SaveIO v6→v7（实体长度前缀子块，v6 双读兼容）+ MapIO.generatePreview 离线流式解析 + Maps 三入档点 + 四对话框；**实测：存档→createMap→generatePreview→PNG 120x120 配色正确**；交付后修 7 处：revision 1B 漏读致 EOF（真 bug）+ postRunnable/drawPixel×5/size() 映射 |
| F15 | 功能 | **键位系统 + 教程指引**（T0-②组B/C，4 条，玩家可感）：KeyBinds setDefaults/save/load + ControlsDialog 键位重绑定界面；Tutorial.outline/toggled（原版 TutorialStage.outline：scene.findVisible + Lines.rect + 呼吸箭头） | ☑（提交 51af32f）；KeyBinds(Binding 枚举默认表=settings 持久化 arc 同名键) + ControlsDialog 重绑定（键盘/鼠标/滚轮三路捕获）+ DesktopInput 21 处消费点 + Tutorial outline；**实测 seed select→G(34) 生效**；交付后修 trns 编译错 1 处；已知降级：鼠标↔键盘互换重绑暂不生效（管道分离，已声明） |
| F16 | 功能 | **集结与命令 AI**（T0-②组G，3 条）：Pathfinder.rallyPoints 流场（扫 BlockFlag.rally，集结链当前断裂）、CommandAI.hit() 受击反击、afterRead() 读档恢复命令；blockingUnit 需先补 Units.nearbyCheck | ☑（提交 9864bbf）；rallyPoints 照原版接 getAllied(rally)（入口 CommandCenter 已刻意退役，链路待用）+ hit 反击（126.2 无 CommandAI，v146 对齐）挂 collision + nearbyCheck 两重载；考据：afterRead 命令存档任何版本均无→不做；**实测反击链闭环**（attackTarget 指向攻击者→进入射程按 stopAtTarget 释放=原版语义） |
| F17 | 功能 | **贴图资源缺口**（T0-②组F，3 条）：message / illuminator(-top) / armored-conveyor 贴图从原版 assets 拷进 `core/assets-raw/sprites_out/`（**同时拷 sprites_out/ 顶层**），主会话跑 texturePacker + 区域名集合 diff 校验 | ☑（提交 a08a2f6）；主会话执行：25 张（message/illuminator+top/armored 20帧+2icon）拷入 sprites_out 对应子目录（icon 变体在 editor//generated/ 目录）→ texturePacker → **区域名 diff 校验 +25 零丢失**；冒烟无异常 |
| F18 | 功能 | **小功能合集**（T0-②组D/H/I，6 条）：bloom 后处理管线（设置项已存在）、MapIO.FilterContext 滤镜应用链、DatabaseDialog Fonts.getUnicode 复制彩蛋、coreselect 写蓝图判据 | ☐ |
| F10 | 功能 | **A2 产出**：世界暗化层 isDarkened 整链（Tile/World/FloorRenderer，原版 core/World.java:308-344）、Door 手动开合+门音效+开关刷新寻路（Door.java:20-46）、**canDeconstruct 未调 canBreak → 自家核心可拆**（Build.java:192 + CoreBlock.canBreak） | ☑（提交 9ff7dea）；暗化=addDarkness 现算（rotation 存 0-5 暗度）+渲染/小地图/入档重算；**截图验证岩体暗度梯度**；含 rgba→toIntBits 编译修正 |
| F11 | 功能 | A3 审计产出（待 A3 完成后登记） | ☐ |
| F12 | 功能 | T0 分类产出的"缺失功能"项（待 T0 完成后登记） | ☐ |
| F-LOW | 功能 | 低优先级合集：EventType 缺失事件类、Schematics.overwrite、SpawnGroup 序列化、SoundLoop 工作音效、GlobalData 资源池、TileEntity 休眠优化；**内容详情面板**（ContentInfoDialog.show + displayInfo 补 Item/UnitType/Liquid/StatusEffect 覆写 + ContentDisplay.displayItem/displayLiquid/displayUnit——A3+T0 组E）、Administration 子网封禁/strict/Config（A3 中低） | ☐ |
| R1 | 残留 | 死代码/占位实现/`// removed` 残留清扫（含 S3d-5 主菜单背景）；**含 StatusEffect.reactive 死字段、Edges.getPixelPolygon 死方法**（A2） | ☐ |
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
- 2026-09-29 A2 完成（缺失4/偏离4/断裂3，报告 docs/port/audit-A2-world-content.md；内容名全覆盖、子弹数值一致）；F1/F4/F7/N1/R1 吸收 A2 项 + F10 落地；派发 A3
- 2026-09-29 A3 完成（缺失8/偏离2/断裂3，报告 docs/port/audit-A3-ui-input-graphics.md）；登记 F13 + F-LOW 扩充。**三片审计齐**：合计缺失 27/偏离 12/断裂 10。进入修复流水线：派 T0（后台）+ 主会话跑 N1 dump
- 2026-09-29 N1 dump 三份就绪（docs/port/dump-*.txt）；T0 完成（①11 ②26 ③217 ④0，docs/port/todo-classified.md）；登记 F14~F18 + T1；派发 N1 修复批
- 2026-09-29 N1 完成：检查161/不一致23/全修（根因=8 个方块类构造器自设默认 health；dagger-factory 材料+耗电；burning/wet/freezing），编译绿 + BLKDATA 复跑 diff 36 行全对（提交 4849e59）；派发 F5+F7
- 2026-09-29 F5+F7 完成：6 项全落（Bullet.collides 照原版、collideGroups 保持对调+补注释、BaseUnit.collides/calculateDamage 照原版、WaveEvent 静态块、launchWaveMultiplier、Fire 水洼+事件），编译绿 + autoplay 冒烟无异常（提交 d4423fe）；派发 F1
- 2026-09-29 F1 完成：6 项全落（卡墙/溺亡/液体交互/出界三件/spawner 失效/unitOn 钩子），FlyingUnit 冗余覆写删除；**深水溺亡专项实测通过**（dagger f230 死、飞行玩家满血）（提交 410166c）；派发 F2
- 2026-09-29 F2 完成：清场/延迟分批出生/attack 增援三链 + Damage 6 参重载 + BaseUnit.add()；**波次专项实测通过**（runWave 后 +0/+10 敌 0、+100 出齐 4、玩家满血）（提交 02e230c）；派发 F3
- 2026-09-29 F3 完成：沿途/链式/绝缘三段照原版重写 + Geometry.findFurthest 补齐 + insulated 字段（塑钢墙/二极管）；**闪电链实测 3 连纵队均匀 3 击**（提交 3adba0d，含 1 处 arc Color.white 编译修正）；派发 F10
- 2026-09-29 F10 完成：darkness 整链 + Door 手动开合 + 核心不可拆（canBreak）；**截图验证岩体暗度梯度**（提交 9ff7dea，含 rgba→toIntBits 编译修正）；派发 F13
- 2026-09-30 F13 完成：输入/UX 九项全落（解锁 toast/E 暂停建造/P 整图截图/中键吸取/R 旋转已放置/drawConfigure×4/buildUI 提示条/投递预览/Ctrl 对角+canShoot）；arc setTranslation→fadeIn/fadeOut 等价 + 新增 ui/MenuFragment.Act；编译绿 + autoplay 冒烟无异常（提交 3c2f9c5）；**A1/A2/A3 高优先级全部清零**；派发 F4
- 2026-09-30 F4 完成：onDestroyed 整链（物品/液体/电力/残骸）+ kill 接线 breakSound + dynamicExplosion 照原版补全（闪电/火球/多波/震屏上限）+ 单位殉爆/deathSound；**实测双链通过**（建筑 kill→air、dagger 死亡 -18.8，均无异常）；派发 F6
- 2026-09-30 F6 完成：tileDamage 菱形蔓延（GridBits/IntQueue 复用）+ 多波伤害伤建筑接线 + 入口对齐（删 damageAll、核爆半径漏乘 tilesize 修正）；**实测抓到 IntQueue.resize 满环回绕越界并修复**（墙 320→168 衰减生效）（提交 f284a92）；派发 F8
- 2026-09-30 F8 完成：索敌语义三项全落（derelict 豁免/tilePred 透传/range 口径）；LOGTEST 冒烟无异常（提交 3f21acb）；派发 F9
- 2026-09-30 F9 完成：状态存档 v6 整链 + getStatusColor；**存读实测闭环**（burning 恢复、旧档拒绝）（提交 328cd35）；A1 审计项全部清零；派发 T1
- 2026-09-30 T1 完成：11 条过时 TODO 清理 + useContent 解锁链接线；冒烟无异常（提交 2a700d3）；派发 F14
- 2026-09-30 F14 完成：缩略图整链 v7 + 离线预览；实测 PNG 出图正确；修 revision 漏读 EOF + 6 处映射（提交 b04fa57）；派发 F15
- 2026-09-30 F15 完成：键位系统全链 + 教程指引框；实测 settings seed 重绑生效（select→G=34，M=41/SPACE=62 核对）；修 trns 编译错（提交 51af32f）；派发 F16
- 2026-09-30 F16 完成：rally 流场/hit 反击/nearbyCheck；实测反击链闭环（含 Interval 开局冷却=arc 同款行为考据、stopAtTarget 释放=原版语义）（提交 9864bbf）；派发 F18，F17 主会话并行做
- 2026-09-30 F17 完成：3 组贴图入集 +25 区域零丢失；**发现并修复提交裹挟**（F18 并行改动的 World/Maps 被拆出，F18 交付时一起提交）（提交 a08a2f6）
