# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 项目定位

phoenix 是把 **Mindustry Build 126.2**（参考工程 `D:\IdeaProjects\Mindustry-126.2`，只读参考）手工移植到 libgdx 的工程：保留原版类名/字段名/玩法逻辑，**不用原版的 arc 库**，一律替换成 libgdx；实在替代不了的按最小实现复制（原码整段注释 + TODO，不要删）。包名 `mindustry.*` → `com.phoenix.game.*`。

尚未移植：建筑实体逻辑、寻路（现为直线移动）、网络同步、地图存档、HUD/建 造栏。改动前先读根目录 `转换.md`（arc→libgdx 替换规则与踩坑清单）。

## 常用命令

| 用途 | 命令 |
| --- | --- |
| 编译 core（触发注解处理器生成 `io.anuke.mindustry.gen.*`） | `gradlew :core:compileJava` |
| 编译桌面端 | `gradlew :desktop:classes` |
| 运行游戏（工作目录固定为 `<repo>/assets`） | `gradlew :desktop:run` |
| 跳过菜单直接进战役模式 | 先设环境变量 `PHOENIX_AUTOPLAY=1` 再 run |
| 打包桌面 jar / Android APK | `gradlew :desktop:dist` / `gradlew :android:assembleDebug` |
| 重打图集 | `gradlew texturePacker` |

没有测试源集，验证方式 = 编译 + 运行看日志（异常输出在 stderr）。日常只用 `:core` 与 `:desktop`。

## 架构

- **资源与代码生成**：运行期资源根是 `<repo>/assets`（`sprites/sprites.atlas`、`fonts/`、`i18n/`、`locales`）；构建期资源在 `core/assets`（音频）与 `core/assets-raw`（切图源）。`annotations` 模块在编译 core 时扫描 `core/assets/{sounds,music}`、`core/assets-raw/sprites/ui` 生成 `Sounds/Musics/Icon/Tex/Call`，**新增音频或 UI 图标后必须重编 `:core`**。
- **arc 替代层**：`core/`（`Core`≈arc.Core，以及 `Time/Draw/Tmp/Scl/Pal/Interval/Events`）与 `math/`（`Mathf/Angles/Position/geom.Geometry`）。

## 硬约束（易踩）

- `@Remote` 注解必须保持注释状态，否则注解处理器报 `No @WriteClass method` 直接中断编译。
- 热路径（`Units/Damage/Effects/Renderer/Logic`）统一用下标循环：libgdx `Array` 的 for-each 迭代器不能嵌套。
- `Bullet.remove()` 会把子弹回收进对象池并清空 `type`：回调里先用局部变量取 `type`；碰撞分组时子弹放内层。
- 单位死亡要先 `setDead(true)` 再触发爆炸，否则递归触发 `onDeath` 到栈溢出。
- `Scl.scl()` 不能在类初始化阶段调用（`Core.app` 为 null），相机缩放要懒初始化。
- `resize()` 里必须 `viewport.update(w, h, false)`，传 `true` 会把相机重置到屏幕中心（地图偏移 + 黑边）。
- `Draw.scl` 必须在 atlas 就绪后由 `Control.loadAsync()` 设为 `1 / scale_marker 宽度`（=0.25），否则单位等按贴图原始尺寸绘制的对象大 4 倍；世界缩放绘制统一用 `尺寸 * Draw.scl`。
- arc 的 `mouseY()` 原点在屏幕下方（libgdx 在上方）；`Angles.angle` 内部必须用 `MathUtils.atan2(y2-y, x2-x)`（写反会让瞄准/朝向偏 90°）。
- ⚠**双端逻辑一致**：客户端与服务器行为必须同步两端。