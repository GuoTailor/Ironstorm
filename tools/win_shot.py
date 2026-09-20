"""按窗口标题抓取游戏窗口截图，不依赖前台焦点。

用法: python tools/win_shot.py <标题关键字> <输出文件> [裁剪规格]
裁剪规格形如 "x,y,w,h"（窗口内坐标），省略则抓整窗。
"""
import ctypes
import ctypes.wintypes as wt
import sys

from PIL import Image

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

PW_RENDERFULLCONTENT = 0x00000002


def find_window(keyword, exact=False):
    """查找标题匹配的可见顶层窗口，返回 (句柄, 标题)。

    exact=True 时要求标题完全相等（避免 "phoenix" 匹配到 IDE 的
    "phoenix – TASK.md"）；否则为不区分大小写的子串匹配。
    """
    found = []
    want = keyword.strip().lower()

    @ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)
    def enum_proc(hwnd, _):
        if not user32.IsWindowVisible(hwnd):
            return True
        length = user32.GetWindowTextLengthW(hwnd)
        if length == 0:
            return True
        buf = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buf, length + 1)
        title = buf.value.strip().lower()
        hit = title == want if exact else want in title
        if hit:
            found.append((hwnd, buf.value))
            return False
        return True

    user32.EnumWindows(enum_proc, 0)
    return found[0] if found else (None, None)


def capture(hwnd):
    """用 PrintWindow 抓取窗口客户区（含 DirectX 内容）。"""
    rect = wt.RECT()
    user32.GetClientRect(hwnd, ctypes.byref(rect))
    width, height = rect.right - rect.left, rect.bottom - rect.top
    if width <= 0 or height <= 0:
        raise RuntimeError(f"窗口尺寸非法: {width}x{height}")

    hdc = user32.GetDC(hwnd)
    mem_dc = gdi32.CreateCompatibleDC(hdc)
    bitmap = gdi32.CreateCompatibleBitmap(hdc, width, height)
    gdi32.SelectObject(mem_dc, bitmap)

    # PW_RENDERFULLCONTENT 让 GL/DX 渲染内容也能被抓到
    ok = user32.PrintWindow(hwnd, mem_dc, PW_RENDERFULLCONTENT)

    class BITMAPINFOHEADER(ctypes.Structure):
        _fields_ = [
            ("biSize", wt.DWORD), ("biWidth", wt.LONG), ("biHeight", wt.LONG),
            ("biPlanes", wt.WORD), ("biBitCount", wt.WORD), ("biCompression", wt.DWORD),
            ("biSizeImage", wt.DWORD), ("biXPelsPerMeter", wt.LONG), ("biYPelsPerMeter", wt.LONG),
            ("biClrUsed", wt.DWORD), ("biClrImportant", wt.DWORD),
        ]

    header = BITMAPINFOHEADER()
    header.biSize = ctypes.sizeof(BITMAPINFOHEADER)
    header.biWidth = width
    header.biHeight = -height  # 负数表示自上而下
    header.biPlanes = 1
    header.biBitCount = 32
    header.biCompression = 0

    buf = ctypes.create_string_buffer(width * height * 4)
    gdi32.GetDIBits(mem_dc, bitmap, 0, height, buf, ctypes.byref(header), 0)

    gdi32.DeleteObject(bitmap)
    gdi32.DeleteDC(mem_dc)
    user32.ReleaseDC(hwnd, hdc)

    image = Image.frombuffer("RGBA", (width, height), buf, "raw", "BGRA", 0, 1)
    return image.convert("RGB"), ok


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1

    keyword, out = sys.argv[1], sys.argv[2]
    hwnd, title = find_window(keyword, exact=True)
    if hwnd is None:
        print(f"未找到标题为 {keyword!r} 的窗口（精确匹配）")
        return 2

    image, ok = capture(hwnd)
    print(f"窗口 {title!r} 尺寸 {image.size} PrintWindow={ok}")

    if len(sys.argv) > 3:
        x, y, w, h = (int(v) for v in sys.argv[3].split(","))
        image = image.crop((x, y, x + w, y + h))

    image.save(out)
    print(f"已保存 {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
