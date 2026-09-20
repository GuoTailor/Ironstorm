"""启动 phoenix 自动播放、恢复窗口、抓图并裁剪 HUD 区域。

用法: python tools/hud_shot.py [输出文件] [裁剪规格]
裁剪规格形如 "x,y,w,h"（窗口内坐标），省略时抓整窗。
"""
import ctypes
import ctypes.wintypes as wt
import os
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "tools"))
from win_shot import capture, find_window  # noqa: E402

WINDOW_TITLE_EXACT = "phoenix"


def restore_window():
    """窗口最小化时恢复并置于前台，返回句柄。

    用精确标题匹配，避免抓到标题含 "phoenix" 的 IDE 窗口；
    仅有多个游戏窗口时取最后枚举到的一个（最新启动的通常在最上层）。
    """
    user32 = ctypes.windll.user32
    hwnd, title = find_window(WINDOW_TITLE_EXACT, exact=True)
    if hwnd is None:
        return None

    if user32.IsIconic(hwnd):
        user32.ShowWindow(hwnd, 9)  # SW_RESTORE
    #SetForegroundWindow 会被前台锁定策略拒绝（IDE 抢占），用 SwitchToThisWindow 强制切换，
    #与 win_click.py 一致。窗口没真正到前台时 PrintWindow 可能返回过期/空白的 GL 后备缓冲
    #（症状：整窗纯白，或只画出一部分）。
    user32.ShowWindow(hwnd, 9)
    ctypes.windll.user32.SwitchToThisWindow(hwnd, True)
    time.sleep(2.0)
    return hwnd


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "shot-game.png"
    crop = sys.argv[2] if len(sys.argv) > 2 else None

    hwnd = restore_window()
    if hwnd is None:
        print("未找到游戏窗口，请先启动: PHOENIX_AUTOPLAY=1 ./gradlew :desktop:run")
        return 2

    image, ok = capture(hwnd)
    print(f"抓图成功 尺寸={image.size} PrintWindow={ok}")

    if crop:
        x, y, w, h = (int(v) for v in crop.split(","))
        image = image.crop((x, y, x + w, y + h))
        print(f"裁剪为 {image.size}")

    image.save(out)
    print(f"已保存 {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
