# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 项目定位

phoenix 是把 **Mindustry Build 126.2**（参考工程 `D:\IdeaProjects\Mindustry-126.2`，只读参考）手工移植到 libgdx 的工程：保留原版类名/字段名/玩法逻辑，**不用原版的 arc 库**，一律替换成 libgdx；实在替代不了的按最小实现复制（原码整段注释 + TODO，不要删）。包名 `mindustry.*` → `com.phoenix.game.*`。

尚未移植：建筑实体逻辑、网络同步、地图存档、HUD/建造栏（单位寻路已由 `ai/Pathfinder` 流场实现）。改动前先读根目录 `转换.md`（arc→libgdx 替换规则与踩坑清单）。

## 常用命令

| 用途 | 命令 |
| --- | --- |
| 编译 core（触发注解处理器生成 `io.anuke.mindustry.gen.*`） | `gradlew :core:compileJava` |
| 编译桌面端 | `gradlew :desktop:classes` |
| 运行游戏（工作目录固定为 `<repo>/assets`） | `gradlew :desktop:run` |
| 跳过菜单直接进战役模式 | 先设环境变量 `PHOENIX_AUTOPLAY=1` 再 run |
| 跳过菜单直接加入服务器（自动化联机验证） | 先设环境变量 `PHOENIX_JOIN=ip[:port]` 再 run（端口默认 6567） |
| 打包桌面 jar / Android APK | `gradlew :desktop:dist` / `gradlew :android:assembleDebug` |
| 重打图集 | `gradlew texturePacker` |

没有测试源集，验证方式 = 编译 + 运行看日志（异常输出在 stderr）。日常只用 `:core` 与 `:desktop`。

## 调试与验证（运行期截图 / 日志钩子）

视觉与交互类改动只能「跑起来 + 看画面 + 看日志」验证。窗口通常在后台或被遮挡，**不要用系统截屏**——
改为**在游戏进程内读 GL 帧缓冲**：与窗口是否可见、是否被遮挡、是否拥有焦点全都无关。

### 约定

| 项 | 约定 |
| --- | --- |
| 开关 | 环境变量 `PHOENIX_*`（`PHOENIX_SHOT`/`PHOENIX_CHAINDBG`/`PHOENIX_LINEDBG`/`PHOENIX_PLAYERDBG`…），不设就不生效 |
| 日志前缀 | `DBG `，便于 `Select-String 'DBG '` 过滤 |
| 结束 | 抓完/验完调 `Gdx.app.exit()`，脚本侧再 `Stop-Process` 兜底 |
| 清理 | 验证完成后删掉钩子代码与临时 png/txt（除非刻意做成长期调试设施） |
| 编码 | 中文在 Windows 控制台会乱码：日志只用 ASCII 标记（`U/R/D/L`）或纯数字 |

### 1) 代码：在 `render()` 里埋抓帧钩子（**必须放在 HUD 绘制之后**）

位置：`core/src/com/phoenix/game/ClientLauncher.java` 的 `render()`，字段 `private int shotFrames = 1;`。

```java
//DEBUG-SHOT-START
String shot = System.getenv("PHOENIX_SHOT");                  // 值 = 第几帧抓
if(shot != null && shotFrames++ == Integer.parseInt(shot)){   // shotFrames 是字段，初值 1
    Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
    PixmapIO.writePNG(Gdx.files.absolute("d:/IdeaProjects/phoenix/shot.png"), pm);
    pm.dispose();
    Gdx.app.exit();
}
//DEBUG-SHOT-END
```

`Pixmap.createFromFrameBuffer` 就是 `glReadPixels`，读本进程的后台缓冲：**窗口在后台也能拿到完整画面**。
放在 `stage.draw()`（HUD）之前抓，图里就没有 HUD；想连 HUD 一起看就放在之后。

### 2) PowerShell：起进程 → 等待 → 收尾 → 看日志

```powershell
$env:PHOENIX_AUTOPLAY='1'; $env:PHOENIX_SHOT='150'
$p = Start-Process -FilePath 'd:\IdeaProjects\phoenix\gradlew.bat' `
     -ArgumentList '-p','d:\IdeaProjects\phoenix',':desktop:run','--console=plain' `
     -RedirectStandardOutput 'd:\IdeaProjects\phoenix\run-out.txt' `
     -RedirectStandardError  'd:\IdeaProjects\phoenix\run-err.txt' -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 50
Get-Process | Where-Object { $_.MainWindowTitle -eq 'phoenix' } | Stop-Process -Force   # 兜底
if(!$p.HasExited){ $p.Kill() }
Get-Content run-out.txt | Select-String -Pattern 'DBG |error:' | Select-Object -First 20 | Out-String -Width 200
Get-Content run-err.txt | Select-String -Pattern 'Exception|at com\.phoenix' | Select-Object -First 8
```

`-WindowStyle Hidden` 只隐藏控制台，游戏窗口仍存在并持续出帧（**别把窗口最小化**，部分驱动最小化后会暂停渲染，帧缓冲就不可信了）。

### 3) 后处理：翻正 + 裁剪放大（帧缓冲是上下翻转的）

```powershell
Add-Type -AssemblyName System.Drawing
$src=[System.Drawing.Image]::FromFile('shot.png')
$src.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipY)   # 必须！否则会误判"贴图有问题"
# 裁一块并用 NearestNeighbor 放大 3~6 倍看细节（棋盘格/像素边缘不失真）
$crop=New-Object System.Drawing.Bitmap -ArgumentList $cw,$ch
$g=[System.Drawing.Graphics]::FromImage($crop)
$g.DrawImage($src,(New-Object System.Drawing.Rectangle -ArgumentList 0,0,$cw,$ch),
             (New-Object System.Drawing.Rectangle -ArgumentList $ox,$oy,$cw,$ch),[System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$big=New-Object System.Drawing.Bitmap -ArgumentList ($cw*3),($ch*3)
$g2=[System.Drawing.Graphics]::FromImage($big)
$g2.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g2.DrawImage($crop,0,0,($cw*3),($ch*3)); $g2.Dispose(); $big.Save('shot-zoom.png')
```

看细节前先用 `Vars.renderer.setScale(6f)` 把世界放大（相机缩放上限 6），否则 1 格只有十几像素、分不出贴图朝向。

### 键鼠状态注入不了 → 直接调内部方法跑同一批代码路径

外部无法可靠模拟键鼠（`Gdx.input.setCursorPosition` 受焦点影响、投影坐标有偏移），所以验证交互逻辑时**直接调用内部入口**：

| 想验的东西 | 直接调用 |
| --- | --- |
| 放置/材料扣减 | `Build.placeBlock(tile, block, team, rotation)` |
| 拖拽连放 | `InputHandler.beginLine(...)` → `updateLine(startX,startY,endX,endY)` → `flushLine()` |
| 玩家控制/相机 | 设 `Vars.player.unit().velocity()`、`Vars.control.input.targetX/Y` 后调 `updateShooting()` |
| 把镜头/玩家挪到某处 | `Vars.player.unit().set(worldX, worldY)`（相机 lerp 会跟过去） |

这样测的是**真实代码路径**（不是 mock），同时避开输入层不确定性。

### 备选方案（一般不适用）

- `Graphics.CopyFromScreen`：要求窗口在前台且完全可见，后台场景直接失效。
- Win32 `PrintWindow(hwnd, hdc, PW_RENDERFULLCONTENT)`：能抓后台窗口，但对 OpenGL 窗口常抓到黑屏。
- 结论：GL 程序优先「进程内读帧缓冲」，最稳且零依赖。

## 架构

- **资源与代码生成**：运行期资源根是 `<repo>/assets`（`sprites/sprites.atlas`、`fonts/`、`i18n/`、`locales`）；构建期资源在 `core/assets`（音频）与 `core/assets-raw`（切图源）。`annotations` 模块在编译 core 时扫描 `core/assets/{sounds,music}`、`core/assets-raw/sprites/ui` 生成 `Sounds/Musics/Icon/Tex/Call`，**新增音频或 UI 图标后必须重编 `:core`**。
- **arc 替代层**：`core/`（`Core`≈arc.Core，以及 `Time/Draw/Tmp/Scl/Interval/Events`）与 `math/`（`Mathf/Angles/Position/geom.Geometry`）；libgdx 没有的 arc.struct 类型放在 `struct/`。
- **包结构对齐 Mindustry**（便于逐文件对照移植）：根包放入口 `ClientLauncher` 与 `Vars`；其余为 `ai/ content/ core/ ctype/ entities/{type,type/base,traits,bullet,effect,units} game/ graphics/ input/ math/ struct/ type/ ui/fragments/ world/{meta,modules,blocks/power}`。

## 硬约束（易踩）

- `@Remote` 注解必须保持注释状态，否则注解处理器报 `No @WriteClass method` 直接中断编译。
- 热路径（`Units/Damage/Effects/Renderer/Logic`）统一用下标循环：libgdx `Array` 的 for-each 迭代器不能嵌套。
- `Bullet.remove()` 会把子弹回收进对象池并清空 `type`：回调里先用局部变量取 `type`；碰撞分组时子弹放内层。
- 单位死亡要先 `setDead(true)` 再触发爆炸，否则递归触发 `onDeath` 到栈溢出。
- `Scl.scl()` 不能在类初始化阶段调用（`Core.app` 为 null），相机缩放要懒初始化。
- `Time.delta` 是**方法不是字段**（arc 的 `Time` 没有 delta 字段，它只是 `update()` 里的局部变量）：一律写 `Time.delta()`。写成字段会拿到恒为 `1f` 的常量，使速度随帧率变化（`Time.delta` 字段已删除）。
- `resize()` 里必须 `viewport.update(w, h, false)`，传 `true` 会把相机重置到屏幕中心（地图偏移 + 黑边）。
- `Draw.scl` 必须在 atlas 就绪后由 `Control.loadAsync()` 设为 `1 / scale_marker 宽度`（=0.25），否则单位等按贴图原始尺寸绘制的对象大 4 倍；世界缩放绘制统一用 `尺寸 * Draw.scl`。
- arc 的 `mouseY()` 原点在屏幕下方（libgdx 在上方）；`Angles.angle` 内部必须用 `MathUtils.atan2(y2-y, x2-x)`（写反会让瞄准/朝向偏 90°）。
- ⚠**双端逻辑一致**：客户端与服务器行为必须同步两端。