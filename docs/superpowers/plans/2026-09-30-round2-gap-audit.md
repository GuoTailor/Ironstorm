# 第二轮缺口普查（Round 2 Gap Audit）——计划与执行协议

> 上一轮（`2026-09-29-full-port-completion.md`）已完成：A1~A3 审计 → F1~F18 修复 → T1/R1 清理 → V1 终验通过
> （编译/实跑/截图/联机 dart-ship/数值 dump 零差异/dist 打包 44.5MB）。
> 本轮目标：**对当前 HEAD 做一轮全新的静默缺口普查**，找上一轮审计（有限轮次）未覆盖或新引入的缺口，再按批次修复。

## 审计口径（严格执行，避免重复劳动）

**报告条件（三者同时满足才报）**：
1. 原版（`D:\IdeaProjects\Mindustry-126.2`）**有**该功能/字段/方法/行为；
2. 本项目（`D:\IdeaProjects\phoenix`）**没有**（或行为不同）；
3. **且代码里没有注释声明这是刻意偏离**。

**必读前置（不读就报 = 无效报告）**：
- `docs/superpowers/plans/2026-09-29-full-port-completion.md` 的批次登记表（F1~F18/T1/R1/V1 已完成项清单）
- `docs/port/` 下既有报告：`audit-A1/A2/A3-*.md`、`todo-classified.md`、`silent-*`（S 系列）、`f1~f18-report.md`、`t1-report.md`、`r1-report.md`、`f-low-report.md`

**已知刻意偏离（不要报，除非发现"声称偏离但实际可低成本补齐"）**：
- `acceptItem` 默认语义（本项目"有空间就收"）、坐标口径（worldx 角点 vs 原版中心）、rotation 相差 90°、d4 旋向、
  数值折算一律照抄原版、`PowerVoid/MechPad` 等与原版不同是刻意的、液体已不再是排除项。
- 已声明降级：bloom（no-op，真实现步骤清单在 `graphics/Bloom.java` 注释）、SoundLoop（音频未启用）、
  TileEntity 休眠优化（无性能证据）、命令状态不入档（原版即如此）。

**分类（沿用 A1~A3 口径）**：
- **缺失**：原版有、本项目完全没有（类/方法/字段/子系统）。
- **偏离**：两边都有但行为/数值/语义不同（且无声明）。
- **断裂**：本项目已有实现但**没有接线**（无消费点/调用点缺失）。

**严重度**：高（玩家可感/影响玩法正确性）、中（功能可用但表现不对）、低（边角/不可见）。

## 分片（4 个并行 Explore 审计，互不重叠）

| 片 | 范围 | 报告 |
| --- | --- | --- |
| A4 | `world/`（blocks 全子包、consumers、meta）、`content/`、`type/` | `docs/port/audit-A4-world-content.md` |
| A5 | `entities/`（type/units/bullet/effect/traits）、`ai/`、`ctype/` | `docs/port/audit-A5-entities-ai.md` |
| A6 | `ui/`（fragments/dialogs）、`input/`、`graphics/`、`core/` 图形部分（Renderer/Draw/Drawf/Fill/Lines/Fonts） | `docs/port/audit-A6-ui-input-graphics.md` |
| A7 | `net/`、`io/`、`game/`、`maps/`、`editor/`、`core/` 逻辑与元系统（Control/Logic/ContentLoader/Vars/Settings） | `docs/port/audit-A7-net-io-meta.md` |

## 执行协议（与上一轮一致）

1. **主会话**：派发（brief 含全部背景，子代理看不到会话）→ 读摘要 → 抽查 diff → **编译** → **专项实测**（环境变量钩子，用完即删）→ 提交 → 勾选登记表 → 派下一批。
2. **子代理**：只改代码 + 写报告，**禁止运行 gradlew/任何编译命令**，**禁止 git 操作**；详细产物写 `docs/port/<批次>-report.md`；回摘要 ≤200 字。
3. 每批提交信息格式：`port(G<n>): <一句话>`；修 bug 用 `fix(<area>): <一句话>`。
4. 主会话保持上下文干净：子代理的探索过程不进主会话，只有摘要 + 报告落盘。

## 批次登记表（审计回来后填）

| 批次 | 类型 | 内容 | 状态 |
| --- | --- | --- | --- |
| A4~A7 | 审计 | 四片并行普查（口径见上） | 进行中 |

## 进度日志（每批一行，主会话维护）

- 2026-09-30 第二轮方案建立（上一轮 V1 已通过）；派发 A4~A7 四片并行审计
