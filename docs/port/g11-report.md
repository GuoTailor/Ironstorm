# G11 —— `Lines.square` 4 参形式的 √2 语义差（修复报告）

## 一、语义对照

| 形式 | arc 原版实现 | 语义 | 本项目旧实现 | 旧实现偏差 |
| --- | --- | --- | --- | --- |
| 3 参 `square(x,y,rad)` | `rect(x-rad, y-rad, rad*2, rad*2)`（Arc `Lines.java:353-355`） | rad = **半边长**，轴对齐，边长 `2*rad` | 委托 4 参 `rotation=0` → 顶点角度 `{45,135,225,315}`、距中心 `rad*1.4142135` → 角点 `(±rad, ±rad)` | **无偏差**（恰好等价） |
| 4 参 `square(x,y,rad,rot)` | `poly(x, y, 4, rad, rot-45)`；`poly` 顶点角度 `360/4*i + (rot-45) + 90 = i*90 + rot + 45`、距中心 `rad`（Arc `Lines.java:357-359` + `poly` 实现） | rad = **外接圆半径** | 顶点角度 `i*90 + 45 + rot`、距中心 `rad*1.4142135` | **大 √2 倍** |

要点：两个重载在 arc 里语义**不同**（3 参半边长、4 参外接圆半径）。本项目旧实现把两者统一成"半边长"，
于是 3 参凑巧正确、4 参大 √2。**修复不能靠"3 参委托 4 参"**——因为 arc 4 参在 `rot=0` 时半边长是 `rad/√2`，
与 arc 3 参的半边长 `rad` 不等。故本次将两个重载拆成独立实现。

角度恒等式：`i*90 + 45 + rot` ≡ `i*90 + (rot-45) + 90`，与 arc `poly` 的角度公式逐字一致。

## 二、最终实现（`core/src/com/phoenix/game/core/Lines.java`）

```java
/**
 * 空心正方形（**轴对齐**），radius 为**半边长**（对应 arc 的 3 参重载
 * {@code square(x,y,rad)} → {@code rect(x-rad, y-rad, rad*2, rad*2)}）。
 * <p>⚠ <b>不能委托给 4 参重载</b>：arc 的 4 参把 rad 当**外接圆半径**，两者语义不同
 * （4 参在 rotation=0 时半边长 = rad/√2，而本重载半边长 = rad）。
 */
public static void square(float x, float y, float radius){
    rect(x - radius, y - radius, radius * 2f, radius * 2f);
}

/**
 * 空心正方形，可绕中心旋转；radius 为**外接圆半径**（对应 arc 的 4 参重载
 * {@code square(x,y,rad,rot)} → {@code poly(x,y,4,rad,rot-45)}：
 * 顶点在角度 {@code rot-45 + i*90} 处、距中心 {@code rad}）。
 */
public static void square(float x, float y, float radius, float rotation){
    float[] cx = new float[4], cy = new float[4];
    for(int i = 0; i < 4; i++){
        float a = i * 90f + 45f + rotation;
        cx[i] = x + Angles.trnsx(a, radius);
        cy[i] = y + Angles.trnsy(a, radius);
    }
    for(int i = 0; i < 4; i++){
        int j = (i + 1) % 4;
        line(cx[i], cy[i], cx[j], cy[j]);
    }
}
```

- 3 参**独立实现**：直接走 `rect(...)`，与 arc 的 3 参逐字等价，不再经过 4 参。
- 4 参：仅去掉 `* 1.4142135f`，顶点角度公式与 arc `poly` 完全一致。

## 三、全量调用点审计表

用 python 脚本对 `core/src` 全量扫描 `Lines.square` / `Drawf.square`，按**顶层逗号计数**判定参数个数
（脚本同时跳过括号内逗号，可正确处理跨行调用）：

| 文件:行 | 调用 | 参数个数 | 分类 | 是否受本次改动影响 |
| --- | --- | --- | --- | --- |
| `content/Fx.java:83` | `Lines.square(e.x, e.y, 4f + e.finpow()*40f, e.fin()*90f)` | 4 | **4 参** | **是**（框小 √2 → 正确归位） |
| `core/Drawf.java:181` | `Lines.square(x, y, radius + 1f, 45f)` | 4 | **4 参** | **是**（与原版一致） |
| `core/Drawf.java:183` | `Lines.square(x, y, radius + 1f, 45f)` | 4 | **4 参** | **是**（与原版一致） |
| `content/Fx.java:90` | `Lines.square(e.x, e.y, tilesize/2f*e.rotation + e.fin()*3f)` | 3 | 3 参 | 否 |
| `content/Fx.java:104` | `Lines.square(e.x, e.y, tilesize/2f*e.rotation + e.fin()*3f)` | 3 | 3 参 | 否 |
| `content/Fx.java:1063` | `Lines.square(e.x, e.y, tilesize/2f + e.fin()*2f)` | 3 | 3 参 | 否 |
| `content/Fx.java:1069` | `Lines.square(e.x, e.y, tilesize/2f + e.fout()*2f)` | 3 | 3 参 | 否 |
| `content/Fx.java:1075` | `Lines.square(e.x, e.y, tilesize + e.fin()*2f)` | 3 | 3 参 | 否 |
| `content/Fx.java:1081` | `Lines.square(e.x, e.y, tilesize + e.fout()*2f)` | 3 | 3 参 | 否 |
| `content/Fx.java:1228` | `Lines.square(e.x, e.y, 1f + (e.fin()*e.rotation*tilesize/2f - 1f))` | 3 | 3 参 | 否 |
| `core/Renderer.java:588` | `Lines.square(cx, cy, tile.block().size*tilesize/2f + 3f + Mathf.absin(...))` | 3 | 3 参 | 否 |
| `core/Renderer.java:590` | `Lines.square(cx, cy, tile.block().size*tilesize/2f + 2f + Mathf.absin(...))` | 3 | 3 参 | 否 |
| `input/InputHandler.java:1254` | `Lines.square(ai.targetPos.x, ai.targetPos.y, 3.5f)` | 3 | 3 参 | 否 |
| `input/InputHandler.java:1261` | `Lines.square(next.x, next.y, 3.5f)` | 3 | 3 参 | 否 |
| `world/Block.java:738` | `Lines.square(centerX(tile), centerY(tile), size*tilesize/2f + 1f)` | 3 | 3 参 | 否 |
| `world/blocks/distribution/ItemBridge.java:323` | `Lines.square(centerX(tile), centerY(tile), size*tilesize/2f + 1f)` | 3 | 3 参 | 否 |
| `world/blocks/distribution/ItemBridge.java:332` | `Lines.square(other.block().centerX(other), ..., ... + (linked ? 0f : Mathf.absin(...)))` | 3 | 3 参 | 否 |

补充：`core/Drawf.java:174`、`world/blocks/power/PowerNode.java:70` 命中正则但位于**注释文本**内，非真实调用。

**`Drawf.square` 调用点**（本身不是 `Lines.square`，但 4 参重载内部会转发到 `Lines.square` 4 参）：

| 文件:行 | 调用 | 说明 |
| --- | --- | --- |
| `world/blocks/power/PowerNode.java:98` | `Drawf.square(cx, cy, size*tilesize/2f + 2f, Pal.place)` | 转发 `Lines.square(x,y,radius+1f,45f)`，本次随 4 参修正 → 与原版一致 |
| `world/blocks/power/PowerNode.java:140` | `Drawf.square(cx, cy, size*tilesize/2f + 1f, Pal.place)` | 同上 |

**结论：真实的 `Lines.square` 4 参调用点共 3 处**（`Drawf.java` 2 处 + `Fx.java:83` 1 处），与主会话分析一致，无遗漏；
其余 14 处全部为 3 参，不受影响。

## 四、`Drawf` 注释更新说明

`core/src/com/phoenix/game/core/Drawf.java` 的 `square(float x, float y, float radius, Color color)`：
- **删除**原"⚠ 尺寸差异…要完全复刻原版大小需要传 `(radius + 1f) / 1.4142135f`…"整段。
- **替换**为一句：本项目 `Lines.square` 4 参已对齐 arc 的外接圆半径语义，故原样传 `radius + 1f`
  即与原版 `Lines.square(x, y, radius + 1f, 45)`（`Mindustry-126.2/core/src/mindustry/graphics/Drawf.java:35`）一致。
- 两次 `Lines.square(x, y, radius + 1f, 45f)` **代码不动**。

## 五、"3 参调用点行为不变"的推理依据

旧 3 参：`square(x,y,rad)` → 4 参 `rotation=0` → 顶点 `Angles.trnsx/y(45°+i*90, rad*1.4142135f)`。
`cos(45°)*1.4142135 = 0.70710678*1.4142135 = 1.0000001`，故角点为 `(±1.0000001*rad, ±1.0000001*rad)`。

新 3 参：`rect(x-rad, y-rad, 2rad, 2rad)` → 四条 `line` 首尾相连，角点精确为 `(±rad, ±rad)`。

两者是**同一个轴对齐正方形轮廓**（4 条边、边长 `2*rad`、中心在 `(x,y)`），顶点位置仅有 `~1e-7` 相对量级的
浮点舍入差异（远小于 1 像素）。绘制顺序也一致（都是 4 条独立 `line` 线段，无额外填充）。
因此 3 参调用点**视觉与几何完全不变**。

同时新 3 参不再经过 4 参，从根本上杜绝了"4 参语义改成外接圆半径后把 3 参也带偏 √2"的风险——
这正是本批次最易出错处，已通过**独立实现**规避。

## 六、改动文件清单

1. `core/src/com/phoenix/game/core/Lines.java` —— 拆分 `square` 两个重载。
2. `core/src/com/phoenix/game/core/Drawf.java` —— 更新注释（代码不变）。
3. `core/src/com/phoenix/game/content/Fx.java` —— `attackCommand` 加注释（数值不变）。

未触碰用户未提交的并行改动文件（`Player.java` / `InputHandler.java` / `SaveIO.java` / `ClientLauncher.java` / `CLAUDE.md`）。
未运行 gradle、未执行任何 git 写操作。

---

## 七、主会话实测验证（PHOENIX_G11TEST）

在 `Renderer.draw()` 内临时画两个参照方框（同一 `radius=8`、白色、`stroke 1f`、`setScale(6f)`）：

- 左：`Lines.square(px-16, py+16, 8f)` —— 3 参
- 右：`Lines.square(px+16, py+16, 8f, 0f)` —— 4 参

`PHOENIX_AUTOPLAY=1 PHOENIX_G11TEST=1 PHOENIX_SHOT=200` 截图后逐像素测外接框：

| 方框 | 实测外接框 | 减去描边(≈6px) | 换算世界单位 (ppu=6) | 预期 |
| --- | --- | --- | --- | --- |
| 3 参 (半边长语义) | 102×102 px | 96×96 | **16×16** | 半边长 8 → 宽 16 ✓ |
| 4 参 (外接圆半径语义) | 74×74 px | 68×68 | **11.31×11.31** | 半边长 8/√2=5.657 → 宽 11.31 ✓ |

- 两方框均 **w=h**（轴对齐正方形）✓
- 真实边长比 96/68 = **1.412 ≈ √2** ✓（= 3 参半边长 rad ÷ 4 参半边长 rad/√2）
- 描边 6px = `stroke 1f × ppu 6`，与两框的外扩量一致，佐证测量口径

结论：3 参保持"半边长"、4 参归位"外接圆半径"，两者语义与 arc 一致。

临时钩子（`Renderer.draw()` 的 `PHOENIX_G11TEST` 调用 + `debugG11Squares()` 方法 + 尾部空行）已全部删除；
`grep -rn "G11TEST\|g11Scaled\|debugG11Squares" core/src/` 为空；`git diff` 对 `Renderer.java` 无残留；
`:core:compileJava` 绿。
