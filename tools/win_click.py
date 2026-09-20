"""模拟鼠标点击游戏窗口坐标（用于人工验证联机菜单）。
用法: python tools/win_click.py <x> <y> [双击间隔毫秒]
坐标是窗口客户区坐标（与 win_shot 截图坐标一致）。
"""
import ctypes
import ctypes.wintypes as wt
import sys
import time

sys.path.insert(0, "tools")
from win_shot import find_window

def main():
    x, y = int(sys.argv[1]), int(sys.argv[2])
    user32 = ctypes.windll.user32
    hwnd, _ = find_window("phoenix", exact=True)
    if hwnd is None:
        print("未找到 phoenix 窗口")
        return 1

    #客户区坐标转屏幕坐标
    rect = wt.RECT()
    user32.GetClientRect(hwnd, ctypes.byref(rect))
    pt = wt.POINT(x, y)
    user32.ClientToScreen(hwnd, ctypes.byref(pt))

    #SetForegroundWindow 会被前台锁定策略拒绝（IDE 抢占），用 SwitchToThisWindow 强制切换
    user32.ShowWindow(hwnd, 9)  # SW_RESTORE
    ctypes.windll.user32.SwitchToThisWindow(hwnd, True)
    time.sleep(0.5)
    user32.SetCursorPos(pt.x, pt.y)
    time.sleep(0.2)
    user32.mouse_event(0x0002, 0, 0, 0, 0)  # LEFTDOWN
    time.sleep(0.08)
    user32.mouse_event(0x0004, 0, 0, 0, 0)  # LEFTUP
    print(f"clicked client ({x},{y}) -> screen ({pt.x},{pt.y})")
    return 0

if __name__ == "__main__":
    sys.exit(main())
