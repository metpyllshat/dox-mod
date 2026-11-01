"""Рисует макеты интерфейса для концепции (docs/vision).

Запуск: python3 docs/vision/make_mocks.py
Требует Pillow и шрифты DejaVu (есть в большинстве дистрибутивов).
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))

GRAPHITE = (18, 22, 26)
INK = (11, 14, 17)
BONE = (217, 210, 196)
AMBER = (224, 167, 46)
GREEN = (127, 224, 138)
RED = (214, 69, 69)
GREY = (120, 128, 136)
LINE = (42, 49, 56)

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
REG = os.path.join(FONT_DIR, "DejaVuSans.ttf")
BOLD = os.path.join(FONT_DIR, "DejaVuSans-Bold.ttf")
MONO = os.path.join(FONT_DIR, "DejaVuSansMono.ttf")


def font(path, size):
    return ImageFont.truetype(path, size)


def rounded(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def phone_mock(path):
    W, H = 720, 1400
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # корпус
    rounded(d, (16, 16, W - 16, H - 16), 46, fill=INK, outline=LINE, width=3)
    # экран
    rounded(d, (32, 32, W - 32, H - 32), 32, fill=GRAPHITE)

    # статус-бар
    for i, h in enumerate((10, 16, 24, 30)):
        d.rectangle((56 + i * 12, 96 - h, 56 + i * 12 + 7, 96), fill=BONE)
    d.text((W / 2, 82), "21:47", font=font(MONO, 26), fill=BONE, anchor="mm")
    # батарея
    rounded(d, (W - 108, 74, W - 56, 98), 5, outline=BONE, width=2)
    d.rectangle((W - 104, 78, W - 84, 94), fill=AMBER)
    d.rectangle((W - 54, 82, W - 50, 90), fill=BONE)

    # заголовок
    d.text((56, 130), "ЭФИР", font=font(BOLD, 44), fill=BONE)
    d.text((56, 188), "канал открыт · позывной СОСНА-2", font=font(REG, 20), fill=GREY)

    # индикатор шума
    d.text((56, 240), "ШУМ", font=font(REG, 18), fill=GREY)
    levels = [0.15, 0.3, 0.55, 0.9, 0.6, 0.35, 0.2, 0.1, 0.1, 0.1, 0.1, 0.1]
    for i, v in enumerate(levels):
        x = 56 + i * 22
        h = int(6 + v * 46)
        color = RED if v > 0.7 else AMBER if v > 0.4 else GREEN
        d.rectangle((x, 290 - h, x + 14, 290), fill=color)
    d.text((W - 56, 236), "пик 90", font=font(BOLD, 20), fill=RED, anchor="ra")
    d.text((W - 56, 262), "20:58 · TRACE", font=font(REG, 17), fill=GREY, anchor="ra")

    d.line((56, 320, W - 56, 320), fill=LINE, width=2)

    feed = [
        ("20:58", RED, "ТЕБЯ СЛЫШНО", ["TRACE ушёл. 250 блоков вокруг", "знают, что здесь кто-то есть."]),
        ("21:04", AMBER, "ПЕЛЕНГ 1", ["Луч 42° · погрешность 6°", "источник: ноутбук + тарелка"]),
        ("21:06", AMBER, "ПЕЛЕНГ 2", ["Луч 118° · пятно 240×240", "источник: вышка «Элеватор»"]),
        ("21:11", GREEN, "КОНТРАКТ", ["Взлом стойки выполнен", "+50 крипты · +1 заряд"]),
        ("21:19", BONE, "КАНАЛ", ["«Кто-то вышел в эфир.", "Уже интересно.»"]),
        ("21:23", GREY, "РЫНОК", ["Заказ: 2 аптечки — 30", "выполнит любой"]),
    ]
    y = 350
    for time, color, tag, lines in feed:
        d.text((56, y + 4), time, font=font(MONO, 18), fill=GREY)
        d.ellipse((124, y + 8, 138, y + 22), fill=color)
        d.text((152, y), tag, font=font(BOLD, 22), fill=color)
        yy = y + 32
        for ln in lines:
            d.text((152, yy), ln, font=font(REG, 20), fill=BONE)
            yy += 28
        y = yy + 18

    # карточка состояния
    top = H - 330
    rounded(d, (56, top, W - 56, top + 150), 18, fill=(24, 29, 34), outline=LINE, width=2)
    rows = [
        ("СВЯЗЬ", "стойка ✓ · вышка ✓ · тарелка ✓", GREEN),
        ("КРИПТА", "84 · заряды 3 · батарея 78%", AMBER),
        ("ДАЛЬШЕ", "аирдроп 04:12 · сводка 0:00", BONE),
    ]
    yy = top + 22
    for label, value, color in rows:
        d.text((84, yy), label, font=font(BOLD, 17), fill=GREY)
        d.text((196, yy - 2), value, font=font(REG, 20), fill=color)
        yy += 44

    # нижние вкладки
    rounded(d, (56, H - 150, W - 56, H - 60), 18, fill=(24, 29, 34))
    tabs = ["ЭФИР", "КАРТА", "СЕТЬ", "РЫНОК"]
    step = (W - 112) / 4
    for i, t in enumerate(tabs):
        cx = 56 + step * (i + 0.5)
        active = i == 0
        d.text((cx, H - 118), t, font=font(BOLD if active else REG, 20),
               fill=AMBER if active else GREY, anchor="mm")
        if active:
            d.rectangle((cx - 26, H - 92, cx + 26, H - 88), fill=AMBER)

    img.save(path)
    return path


def wedge(d, origin, target, spread_deg, color, alpha=30, length=None):
    """Залитый сектор от origin в сторону target."""
    ox, oy = origin
    tx, ty = target
    L = length or math.hypot(tx - ox, ty - oy)
    base = math.atan2(ty - oy, tx - ox)
    spread = math.radians(spread_deg)
    pts = [(ox, oy)]
    steps = 24
    for i in range(steps + 1):
        a = base - spread + 2 * spread * i / steps
        pts.append((ox + math.cos(a) * L, oy + math.sin(a) * L))
    d.polygon(pts, fill=color + (alpha,))


def bearing_mock(path):
    W, H = 1400, 900
    img = Image.new("RGBA", (W, H), GRAPHITE + (255,))
    d = ImageDraw.Draw(img, "RGBA")

    for x in range(0, W, 100):
        d.line((x, 0, x, H), fill=(255, 255, 255, 12))
    for y in range(0, H, 100):
        d.line((0, y, W, y), fill=(255, 255, 255, 12))

    A = (320, 640)    # вышка
    B = (1120, 700)   # ноутбук
    T = (720, 270)    # цель

    dA = math.hypot(T[0] - A[0], T[1] - A[1])
    dB = math.hypot(T[0] - B[0], T[1] - B[1])
    wedge(d, A, T, 4, AMBER, 34, dA)
    wedge(d, B, T, 6, GREEN, 28, dB)

    d.ellipse((T[0] - 78, T[1] - 52, T[0] + 78, T[1] + 52), outline=RED, width=3)
    d.ellipse((T[0] - 8, T[1] - 8, T[0] + 8, T[1] + 8), fill=RED)

    def node(p, label, sub, color):
        d.ellipse((p[0] - 13, p[1] - 13, p[0] + 13, p[1] + 13), fill=color)
        d.ellipse((p[0] - 21, p[1] - 21, p[0] + 21, p[1] + 21), outline=color, width=2)
        d.text((p[0], p[1] + 34), label, font=font(BOLD, 21), fill=BONE, anchor="ma")
        d.text((p[0], p[1] + 60), sub, font=font(REG, 18), fill=GREY, anchor="ma")

    node(A, "ВЫШКА «ЭЛЕВАТОР»", "угол 4°", AMBER)
    node(B, "НОУТБУК + ТАРЕЛКА", "угол 6°", GREEN)

    # подписи лучей — на тёмной подложке, чтобы не сливались с заливкой
    def ray_label(pos, text, color):
        d.rectangle((pos[0] - 8, pos[1] - 6, pos[0] + 8 + d.textlength(text, font=font(MONO, 19)), pos[1] + 26),
                    fill=GRAPHITE + (235,))
        d.text(pos, text, font=font(MONO, 19), fill=color)

    ray_label((470, 470), "луч 1", AMBER)
    ray_label((880, 520), "луч 2", GREEN)

    tx0, ty0 = T[0] + 100, T[1] - 34
    rounded_box = (tx0 - 10, ty0 - 6, tx0 + 186, ty0 + 62)
    d.rounded_rectangle(rounded_box, radius=8, fill=GRAPHITE + (240,), outline=LINE, width=1)
    d.text((tx0 + 4, ty0), "пятно 90×90", font=font(BOLD, 22), fill=RED)
    d.text((tx0 + 4, ty0 + 30), "это уже штурм", font=font(REG, 18), fill=GREY)

    d.text((60, 50), "ПЕЛЕНГ: ДВА ЛУЧА — ПЯТНО, ТРИ — ТОЧКА", font=font(BOLD, 32), fill=BONE)
    d.text((60, 98), "каждый луч — направление с погрешностью; пересечение даёт площадь поиска",
           font=font(REG, 21), fill=GREY)

    legend = [
        (AMBER, "вышка — точнее, но её надо держать"),
        (GREEN, "ноутбук — ближе, но грубее"),
        (RED, "пересечение — цель для удара"),
    ]
    lf = font(REG, 18)
    y = H - 74
    x = 60
    for color, text in legend:
        d.rectangle((x, y + 7, x + 20, y + 15), fill=color)
        d.text((x + 30, y), text, font=lf, fill=BONE)
        x += 30 + d.textlength(text, font=lf) + 42

    img.save(path)
    return path


if __name__ == "__main__":
    print(phone_mock(os.path.join(HERE, "phone-mock.png")))
    print(bearing_mock(os.path.join(HERE, "bearing.png")))
