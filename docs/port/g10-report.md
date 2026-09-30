# G10 批次报告（全量移植收尾·第二轮）

范围：`Fill.square` 半径/边长语义系统性错配（G5 期间新发现）。

约定：原版 = `D:\IdeaProjects\Mindustry-126.2`；arc = `D:\IdeaProjects\Arc`；本项目 = `d:\IdeaProjects\phoenix`。

## 结论

**采用「改 Fill 语义 + 换算自写调用点」方案**（而非逐点 ×2）：把 `Fill.square` 对齐 arc 的**半径**语义，
使全部从 arc 原样搬来的调用点自动归位；再单独换算本项目的自写调用点。

## 语义对照

arc `arc/graphics/g2d/Fill.java:109-115`：

```java
public static void square(float x, float y, float radius){
    square(x, y, radius, 0f);
}
public static void square(float x, float y, float radius, float rotation){
    rect(x, y, radius * 2f, radius * 2f, rotation);
}
```

→ 第三参是**半径**，内部按 `radius*2` 作为边长。

本项目旧实现（错误）：`square(x,y,size,rot)` → `rect(x,y,size,size,rot)`，把第三参当**边长**。

## 本项目改动

1. `core/src/com/phoenix/game/core/Fill.java`
   - `square(x,y,radius)` / `square(x,y,radius,rotation)` 改为 arc 半径语义：
     `rect(x, y, radius * 2f, radius * 2f, rotation)`。
   - 补注释说明历史错配与调用点口径。

2. `core/src/com/phoenix/game/world/blocks/power/Battery.java`
   - 旧补偿式 `Fill.square(centerX, centerY, tilesize * size - 2f)`（为抵消边长语义而 ×2）
     改回**原版原式** `Fill.square(centerX(tile), centerY(tile), tilesize * size / 2f - 1f)`。
   - 渲染结果不变：旧式边长 = `8*1-2 = 6`；新式半径 `8*1/2-1 = 3` → 边长 `3*2 = 6`。

3. `core/src/com/phoenix/game/content/Fx.java`（`unitDebris`，**唯一自写点**）
   - `size = 4f * (1f - e.fin()) + 1f` → `size = (4f * (1f - e.fin()) + 1f) / 2f`
     （自写时按边长传参，半径语义下减半保持原观感）。

## 调用点审计

| 类别 | 处数 | 处理 |
| --- | --- | --- |
| 照抄 arc（`content/Fx.java` 18 处 + `entities/effect/Lightning.java` 1 处） | 19 | 不动，语义自动归位（此前实际只画一半） |
| 本项目自写 | 1（`Fx.unitDebris`） | 尺寸 /2 |
| 已补偿的本项目调用点 | 1（`Battery`） | 改回原版原式（渲染不变） |

`tools` 目录另有独立 `Fill`，不在本项目 `core` 引用链上，不受影响。

## 验证（PHOENIX_G10TEST）

在 `Battery.draw` 内临时叠加一个**半径 4 的洋红参照方块**（放在电池中心上方 3 格避免遮挡），
放置 1×1 电池（`tilesize=8`）、`setScale(6f)`、`PHOENIX_SHOT=200` 截图。

- 像素坐标定位：洋红方块外接框 `47px`（阈值采样，真实≈48）。
- 尺度换算：本项目 `camerascale` 即「1 世界单位 = 多少像素」，硬上限 6
  （`core/src/com/phoenix/game/core/Renderer.java:319` 注释 + `[1.5,6]` 夹取）。
  → 1 格 = 8 单位 × 6 = **48px**。
- 判定：半径 4 → 宽 8 单位 = **48px** ✓（实测 47，阈值边缘侵蚀）。
  若仍是旧的**边长**语义，宽只有 4 单位 = 24px（需 ppu = 11.75 > 6，**不可能**）→ 旧语义被证伪。

结论：`Fill.square` 半径语义正确。

## 顺带发现（另立 G11）

`Lines.square` 的 **4 参**形式存在同类问题：本项目按**半边长**，而 arc 的 4 参版本是
`poly(x,y,4,rad,rot-45)`（**外接圆半径**）→ 同一 rad 大 √2 倍。
但 arc 的 **3 参**版本 `square(x,y,rad)=rect(x-rad,y-rad,rad*2,rad*2)` 恰是**半边长**，
故所有 3 参调用点（`Renderer`/`Block`/`ItemBridge`/`Fx` 多处）**不受影响、必须保持现状**。
仅 4 参受影响：`Drawf.square`（2 处，已有文档化偏离说明）+ `Fx.attackCommand`（1 处，自写）。
已登记为 G11。

## 清理

临时钩子（`Battery.draw` 的 `PHOENIX_G10TEST` 块、`ClientLauncher.debugG10Test()` 及其调用点）已全部删除；
`grep -rn "G10TEST\|g10Frame\|debugG10Test" core/src/` 为空；`:core:compileJava` 绿。
