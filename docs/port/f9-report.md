# F9 状态存档与染色移植报告（A1 审计产出）

原版参照：`Mindustry-126.2/core/src/mindustry/entities/units/Statuses.java`。
⚠ 任务书写的 `mindustry/entities/comp/StatusComp.java` 在 Build 126.2 里**不存在**（126.2 尚未拆 comp），
状态逻辑全部集中在 `entities/units/Statuses.java`（getStatusColor :61-73、writeSave :123-130、readSave :132-148）。

## 1. Statuses writeSave / readSave — 已完成

原版字节格式：`byte 数量` + 每项 `byte effect.id + float time`（effect.id 为 `ContentType.status` 注册序）。

| 原版 | 本项目落点 |
| --- | --- |
| `Statuses.writeSave(DataOutput)` :123-130 | `entities/units/Statuses.java` writeSave :147（类补 `implements Saveable`，接口签名与原版一致：java.io 的流） |
| `Statuses.readSave(DataInput, byte)` :132-148 | 同文件 readSave :163 |
| `Unit.writeSave` 末尾 `status.writeSave(stream)` | 接线到 `io/SaveIO.java` writeEntities 单位记录末尾（v6） |
| `Unit.readSave` 的 `this.status.readSave(stream, version)` | 接线到 `io/SaveIO.java` readEntities 单位记录读取（v6） |

### 存档链路说明（动手前核实）

- 本项目**实际存档链路是 `io/SaveIO`**（自有格式，version=5→本批升 6）；`io/SaveVersion` 及
  `io/versions/Save1..3` 是"刻意不接线"的 MSAV 兼容层（死代码，注释明说不接）；`entities/type/Unit.java:76-119`
  有一段**注释掉的**原版 Unit.writeSave/readSave（历史残留，未动）。
- 因此接线点是 `SaveIO.writeEntities`（:423 后追加 `unit.status().writeSave(out)`）与
  `SaveIO.readEntities`（单位循环末尾 `unit.status().readSave(in, (byte)version)`）。
- ⚠ readEntities 里 `type == null`（存档含本版本不认识的单位类型）会 `continue` 跳过该单位，
  而状态块是**变长**的 → 在 continue 之前按相同格式空读跳过该块，避免流错位
  （与既有 v4 背包"continue 之前读掉"同模式）。
- **版本 5 → 6**：单位记录追加字段，按既有模式（v3→v4 背包、v4→v5 brokenBlocks）升版本号并
  在 `SaveIO.version` javadoc 补一段"不兼容 v5 旧档"说明；旧档照旧在版本检查处报
  "不支持的存档版本"。未动任何无关格式。

### 刻意偏离（均有代码注释声明）

| 偏离 | 原因 |
| --- | --- |
| readSave 不走 `Pools.free/obtain`，直接 `clear()` + `new StatusEntry()` | 本项目 StatusEntry 无池化（既有实现就没接池） |
| `content.getByID(ContentType.status, id)` 返回 null 时不守卫（坏 id 会塞进列表、之后 update NPE） | 与原版行为一致（原版同样无守卫）；SaveIO 自有格式里 status id 不走内容名表重映射，同构建内注册序稳定，风险与原版相同 |

### 范围说明

- 玩家单位在本项目存档里被刻意跳过（`writeEntities` 不写玩家单位，由 `Control.enterWorld` 重建），
  故玩家单位**没有**状态存档 —— 与"玩家单位不入档"的既有决策一致。
- 联机同步（`net/TypeIO` 等）不在本批次范围，未动。

## 2. getStatusColor 状态色混合 — 已完成（仅加方法，未接 draw 链）

- 原版 :61-73：把所有激活状态的 `effect.color` 的 r/g/b **取简单平均**（alpha 恒 1），无状态返回白色。
  ⚠ 任务书描述"按剩余时间加权"，但原版实现**没有**加权 —— 按项目"照抄原版"规则实现为简单平均。
- 落点：`entities/units/Statuses.java` getStatusColor :86；临时色用本项目 `core/Tmp.c1`（对应 arc Tmp.c1）。
  循环用下标遍历（单位绘制路径属热路径，gdx Array for-each 迭代器不能嵌套）。
- **未接线原因**：全库 grep 原版 126.2，`getStatusColor` **只有定义、没有任何调用点**
  （原版 `BaseUnit.draw()` :311-313 是空的，染色消费是后续版本才有）；本项目 BaseUnit 绘制同样
  没有状态染色钩子（grep getStatusColor/color/tint 均无），两边口径一致 → 只补方法，
  绘制链不加（接了反而偏离 126.2）。

## 跳过 / 未动项

| 项 | 原因 |
| --- | --- |
| `Unit.java:76-119` 注释掉的原版 writeSave/readSave 残留 | 项目惯例保留注释原码作参照，未启用 |
| 玩家单位状态存档 | 玩家单位被刻意跳过不入档（既有决策，见 SaveIO.writeEntities 注释） |
| `Statuses.update` 的 `applied` Bits / 预乘乘数字段（原版 :24-28 :79-105 的实现方式） | 本项目已有等价的扫描式实现（hasEffect/逐次乘），非本批次范围 |
| MSAV 旧档里的状态读取 | SaveIO 对 MSAV 本来就"只识别、不读取"，属 SaveVersion 接线的大工程，不在本批 |

## 自验方式（未执行 —— 本批次禁编译）

禁跑 gradlew，仅静态核对：Saveable 接口签名匹配（java.io 流）、`Vars.content.getByID(ContentType.status, id)`
存在（ContentLoader.java:167，越界返回 null）、`Tmp.c1` 存在（core/Tmp.java:28）、`BaseUnit.status()` 访问器
存在（BaseUnit.java:164）、write/read 字节序对称（byte count → [byte id, float time]*）。
