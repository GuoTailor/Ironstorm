# A2 审计：world / content / type / ctype

> 审计范围：本项目 `com/phoenix/game/{world,content,type,ctype}` ↔ 原版 `mindustry/{world,content,type,ctype}`。
> 判据：行为级对照；**无注释声明的差异才算问题**。豁免：arc→libgdx 结构替换、无 Mech 系统（MechPad 降级为 UnitType）、PowerVoid/MechPad 等有注释的刻意不同、@Remote 注释态、自研网络/存档、Player 非 Unit。
> 跨包去重：entities/ai/game 由 A1 负责；本报告对 A1 已登记项只做 cross-ref，不重复计数。
> **文件级结论：四个包全部 1:1 对应（world 155 对、content 12 对、type 15 对、ctype 5 对），无缺文件。**
> 内容名覆盖：Blocks 198 个内容名 / Bullets 52 个 / Fx 121 个 / UnitTypes 17 个 / Mechs 10 个 / Zones 16 个 —— **原版有的名字本项目全有**（差异仅为项目侧多出的别名与 Mech→UnitType 的 5 个飞船）。子弹构造器数值（damage/speed）逐条比对一致。

## 摘要：缺失 4 / 未声明偏离 4 / 断裂 3（另有 12 项"偏离已声明"，见文末清单）

最严重的三条：
1. **StatusEffects.burning 伤害是原版的 8.3 倍**：项目 `damage = 0.5f`（`type/StatusEffect.update` 再 `/60` → 0.5/秒），原版 `damage = 0.06f`（`damagePeriodic(0.06)` = `0.06*delta` → 0.06/秒）；且类注释声称"数值一律照抄原版"。melting(0.3)/corroded(0.1) 数值恰好等价，唯 burning 不等价。
2. **自家核心可被拆除**：原版 `CoreBlock.canBreak()` 返回 false，由 `Build.validBreak` 的 `tile.block().canBreak(tile)` 拦截；本项目 `Build.canDeconstruct`（world/Build.java:192）只判 `tile.breakable() && getTeam()==team`，而 CoreBlock `destructible=true` 且无 canBreak 覆写 → 玩家/建造无人机可拆掉自己的核心（原版禁止）。
3. **世界暗化层（darkness）整链缺失**：原版 `Tile.isDarkened()`（5 处消费：core/World.java:308/337/344、graphics/FloorRenderer.java:185）用于黑暗瓦片/黑幕渲染；本项目 world/Tile.java 与 core/World.java 全无该概念，含黑暗层的战役/自定义地图渲染不正确。

## 明细表（按优先级排序）

### 高优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| content/StatusEffects.java:20 | burning.damage = 0.06f（每帧，经 damagePeriodic 乘 delta） | 偏离(未声明) | content/StatusEffects.java:26（0.5f） | 本项目 0.5/60≈0.0083/帧 vs 原版 0.001/帧，燃烧伤害约 8.3 倍；注释还称"数值一律照抄原版" | 高 |
| world/blocks/storage/CoreBlock.java:147 | `canBreak()` 返回 false（核心不可拆） | 偏离(未声明) | world/Build.java:192 `canDeconstruct` | 项目 canDeconstruct 未调 `block().canBreak()`，CoreBlock 又 destructible=true 且无覆写 → 可拆自家核心 | 高 |
| world/blocks/distribution/Conveyor.java:141 | `unitOn(Tile, Unit)`：地面单位站上传送带被沿带推动 | 缺失 | world/blocks/distribution/Conveyor.java（无任何单位逻辑） | 项目 `Block` 无 `unitOn/unitRemoved` 钩子（grep=0），Conveyor 只处理物品；Unit.java:226 的调用点被注释掉 → 单位不被带子推动（与 A1"Unit.updateVelocityStatus 未落地"同因，cross-ref A1） | 高 |

### 中优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| world/Tile.java 的 `isDarkened()` + core/World.java:308/337/344 + graphics/FloorRenderer.java:185 | 世界暗化（dark 层）判定与渲染 | 缺失 | world/Tile.java / core/World.java / graphics/FloorRenderer.java | 全项目 grep `isDarkened`/`darkened` = 0；仅 MenuRenderer 有整体压暗，与世界黑暗层无关 | 中 |
| world/blocks/defense/Door.java:20-46 | `consumesTap=true` + `tapped()` + `onDoorToggle()`(Remote) + openfx/closefx + Sounds.door + `pathfinder.updateTile(tile)` | 缺失 | world/blocks/defense/Door.java | 项目 Door 只有实体 update 的"邻近单位自动开合"，无手动点击开合、无门音效、开关时不刷新寻路（单位可能仍绕路/卡门） | 中 |
| content/StatusEffects.java:27 | freezing.armorMultiplier = 0.8f | 偏离(未声明) | content/StatusEffects.java:41-46（无该字段） | 项目 freezing 只设 speedMultiplier=0.6f；**但** armorMultiplier 全项目无消费者（Statuses.getArmorMultiplier 唯一消费点 Unit.java:135 已注释）→ 实际无行为差异 | 中低 |
| content/StatusEffects.java:33 | wet.speedMultiplier = 0.9f | 偏离(未声明) | content/StatusEffects.java:49（0.85f） | 湿身减速被调强（0.9→0.85），玩家可感 | 中低 |

### 低优先级

| 原版位置(file:line) | 项 | 状态 | 本项目位置 | 证据（一句话） | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| type/StatusEffect.java（原版无 reactive 字段） | 项目新增 `public boolean reactive`（burning/melting 设 true） | 偏离(未声明) | type/StatusEffect.java:23 | 全项目 grep `reactive` 仅此声明与两处赋值，**无任何消费者**（原版 126 已用 transitions 取代该旧字段）→ 死字段 | 低 |
| world/Block.java 的 `shouldIdleSound(Tile)` + `idleSound` | 方块空闲音效（钻头/工厂等持续音） | 缺失 | world/Block.java / entities/type/TileEntity.java | 项目有 `shouldActiveSound`(5 处) 但无 `shouldIdleSound`；音效系统属已声明 B 类，此项随之缺失 | 低 |
| world/modules/ItemModule.java:25,39,43,108 | `sum` / `has(Item)` / `has(ItemStack[])` / `addAll` | 缺失 | world/modules/ItemModule.java | `sum` 原被 `Block.onDestroyed` 用于物品可燃/爆炸性累加（A1 已登记 onDestroyed 偏离）；其余在原版无消费点（死方法），无行为影响 | 低 |
| world/blocks/storage/StorageBlock.java | `removeItem` / `hasItem` 覆写 | 缺失 | world/blocks/storage/StorageBlock.java | 项目保留 acceptItem + 内联 getMaximumAccepted，原版 removeItem/hasItem 的消费点（装卸器/单位取物）在项目走 ItemModule 路径，等价 | 低 |
| world/blocks/logic/MessageBlock.java | `updateTableAlign()` | 缺失 | world/blocks/logic/MessageBlock.java | 纯 UI 对齐，无逻辑影响 | 低 |
| ctype/Content.java:20,22 | `@Nullable LoadedMod mod` / `Fi sourceFile` 字段 | 缺失 | ctype/Content.java（改用 ModContentInfo） | 模组来源信息；项目模组系统自研，属结构差异 | 低 |
| ctype/UnlockableContent.java:22 | `createIcons(MultiPacker)`（构建期打包图标） | 缺失 | ctype/UnlockableContent.java | 项目改为运行期 `Core.atlas.findRegion` 回退查找（已注释），行为等价 | 低 |
| world/Edges.java:63 | `getPixelPolygon(float)` | 缺失 | world/Edges.java | 原版该方法自身**无调用点**（死代码）→ 无影响 | 低 |

### 断裂汇总（方法/字段存在但调用链没接上）

| 项 | 本项目位置 | 证据 |
| --- | --- | --- |
| `Zone.updateWave(int)` 无调用点 | type/Zone.java:140 | 全项目 grep `.updateWave(` = 0（原版 core/Logic.java:41 调用）；`bestWave()` 有消费者（Objectives.java:68）但永不更新 → 战役波次进度/发射条件失真（A1 已在 core/Logic 登记，cross-ref） |
| `StatusEffect.reactive` 无消费者 | type/StatusEffect.java:23 | 死字段（原版已移除该概念） |
| `Block.unitOn/unitRemoved` 钩子无定义 | world/Block.java（不存在） | 原版三处覆写中 Conveyor 无替代实现（见高优先级表）；ShockMine 已声明改"每帧检测附近敌方单位"、UnitFactory 已声明改 `getSpawner()` 计数 |

## 偏离(已声明)清单 —— 不计为问题

- `type/Zone.java:151-175`：`updateObjectives` 只保留 `closure.run()`，缺 `ZoneRequireCompleteEvent/ZoneConfigureCompleteEvent` 派发（注释明确"仍然阻塞：事件未移植"）。
- `type/Mech.java` / `content/Mechs.java`：Mech 的 `draw/drawStats/drawCell/drawItems/drawLight`、`canShoot/updateAlt` 等无调用方（注释声明"机甲行为已搬到 content/UnitTypes"）。
- `content/Mechs.java`（23 处注释）：5 个飞船 Mech 降级为 UnitType；`MechPad` 直换。
- `world/blocks/units/{MechPad,CommandCenter,RepairPoint,UnitFactory}.java`：工厂/指挥中心退役或改写（各有注释声明）。
- `world/consumers/Consumers.java`：无 `powerCond(...)` → PowerTurret 注释声明"刻意降级（B 类），退化为无条件耗电"；RepairPoint 自实现 `hasPower()`。
- `world/consumers/Consumers.java`：无 `liquid(Liquid, float)` 便捷方法 → Blocks 里改用 `consumes.add(new ConsumeLiquid(...))`（等价）。
- `world/Block.java:958`：省略 `tile.entity.noSleep()`（无休眠机制，声明）。
- `world/Block.java:1299-1340`：`onDestroyed` 的物品/液体爆炸结算、`liquidDrop.flammability` 读取有声明性注释（部分已由 A1 登记）。
- `world/blocks/power/PowerGraph.java`：整体重写（reflow/useBatteries/chargeBatteries/getSatisfaction 等自研 API，替代原版 distributePower/getPowerBalance/…），行为等价。
- `world/blocks/production/{Pump,SolidPump}.java`：用自研 `linkedTiles(...)` 替代原版 `tile.getLinkedTiles(...)`（注释声明）。
- `world/consumers/ConsumeItems.java:24-31`：`applyItemFilter` 登记后"当前没有消费点"（注释声明，见 `转换.md`）。
- `world/Build.java:66` 与 `input/Placement.java:91`：`Block.canReplace` 未建方法，已在 Placement 内联（注释声明）。
- `entities/type/TileEntity.java`：`Block.update/placed/removed/handleDamage` 等钩子改由实体 `update()/removed()/handleDamage()` 承担（结构差异，行为等价）；`ItemModule/LiquidModule/PowerModule/ConsumeModule` 的 `read/write` 改由 TileEntity 内联序列化（ConsumeModule 派生数据不落盘，注释声明）。
- `ui/fragments/BlockConfigFragment.java`：原版 `shouldShowConfigure/shouldHideConfigure/getCursor/onConfigureTileTapped` 钩子未建，配置 UI 改由输入层 `InputHandler.configTile` + `Block.buildConfiguration` 驱动（注释声明"与原版的差异…常驻面板"）。
- `world/blocks/defense/ShockMine.java`：`unitOn` 改"每帧检测附近敌方单位"（注释声明）。
- `world/blocks/units/UnitFactory.java`：`unitRemoved` 改 `BaseUnit.getSpawner()` 计数（注释声明）。

## 备注 / 未完全核实的项

- content 数值：本次逐条核对了 Items/Liquids/StatusEffects/Bullets 构造器；Blocks/UnitTypes/Mechs 的 size/health/requirements 数值未逐条静态比对（静态解析易假阳性），建议用 `PHOENIX_BLKDATA=1` / `PHOENIX_UNITDATA=1` 运行时 dump 复核（与既有数值审计报告口径一致）。
- `world/Block.java` 的 `drawRequest*/createIcons/getDisplayIcon/variantRegions/drawLayer2/drawConfigure/bounds/getProgressIncrease/getReplacement/setBars/isStatic/shouldActiveSound` 等方法名在方法级 diff 中"缺失"，但 grep 显示均存在于项目其它类（已改名/挪位），**非缺口**，故不单列。
- `Tile.getLinkedTiles/getLinkedTilesAs`、`Block.canReplace`、`Tile.getHitbox`、`Consumers.getPower` 在方法级 diff 中"缺失"，均为项目用等价自研实现替代（`linkedTiles`/`Placement.canReplace`/距离判据/`hasPower`），非缺口。
