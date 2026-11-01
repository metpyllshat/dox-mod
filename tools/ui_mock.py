"""Черновик интерфейса без Minecraft: рисует телефон и меню по тем же числам.

Нужен, чтобы проверять раскладку, когда игры под рукой нет: если на картинке
надписи не наезжают друг на друга и всё влезает в рамку, значит и в игре будет
так же. Координаты ниже повторяют PhoneScreen, AdminScreen и MainMenuScreen.

Запуск: python3 tools/ui_mock.py   →  docs/ui-phone.png, docs/ui-menu.png, docs/ui-admin.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
SCALE = 3

BG = (18, 22, 26)
DEEP = (11, 15, 18)
EDGE = (224, 167, 46)
EDGE_DIM = (70, 56, 24)
TEXT = (217, 210, 196)
MUTED = (122, 131, 139)
AMBER = (224, 167, 46)
RED = (214, 69, 69)
GREEN = (127, 224, 138)
BLUE = (126, 200, 255)
ON_FILL = (42, 34, 19)
OFF_FILL = (16, 21, 24)


class Canvas:
    """Мини-Гуи с теми же примитивами, что у GuiGraphics: fill и текст по 6 px на знак."""

    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.img = Image.new("RGB", (w * SCALE, h * SCALE), BG)
        self.draw = ImageDraw.Draw(self.img)
        self.font = ImageFont.truetype(FONT_PATH, 10 * SCALE)
        self.char = 6.02 * SCALE

    def fill(self, x1, y1, x2, y2, color):
        if x2 <= x1 or y2 <= y1:
            return
        self.draw.rectangle([x1 * SCALE, y1 * SCALE, x2 * SCALE - 1, y2 * SCALE - 1], fill=color)

    def text(self, x, y, s, color):
        self.draw.text((x * SCALE, y * SCALE - 2), s, font=self.font, fill=color)

    def width(self, s):
        return int(len(s) * 6.02)

    def box(self, x, y, w, h, fill, edge):
        self.fill(x, y, x + w, y + h, fill)
        self.fill(x, y, x + w, y + 1, edge)
        self.fill(x, y + h - 1, x + w, y + h, (5, 8, 10))

    def button(self, x, y, w, h, label, on=False, over=False):
        self.box(x, y, w, h, ON_FILL if on else OFF_FILL, EDGE if on else EDGE_DIM)
        if on or over:
            self.fill(x, y + 1, x + 2, y + h - 1, EDGE if on else (122, 95, 26))
        color = AMBER if on else (TEXT if over else MUTED)
        self.text(x + 4, y + (h - 8) // 2, label[: max(1, (w - 8) // 6)], color)

    def tab(self, x, y, w, h, label, on=False):
        self.fill(x, y, x + w, y + h, ON_FILL if on else OFF_FILL)
        self.fill(x, y, x + w, y + (2 if on else 1), EDGE if on else EDGE_DIM)
        self.text(x + max(2, (w - self.width(label)) // 2), y + (h - 8) // 2, label, AMBER if on else MUTED)

    def panel(self, x, y, w, h):
        self.fill(x, y, x + w, y + h, BG)
        self.fill(x, y, x + w, y + 1, EDGE)
        self.fill(x, y + h - 1, x + w, y + h, EDGE_DIM)
        self.fill(x, y, x + 1, y + h, EDGE_DIM)
        self.fill(x + w - 1, y, x + w, y + h, EDGE_DIM)
        self.fill(x + 1, y + 1, x + 6, y + 2, EDGE)
        self.fill(x + 1, y + 1, x + 2, y + 6, EDGE)

    def well(self, x, y, w, h):
        self.fill(x, y, x + w, y + h, DEEP)
        self.fill(x, y, x + w, y + 1, (27, 42, 36))

    def divider(self, x, y, w):
        self.fill(x, y, x + w, y + 1, EDGE_DIM)

    def save(self, name):
        path = os.path.join(ROOT, "docs", name)
        self.img.save(path)
        print("сохранено:", path)


W, H = 248, 228
TOP, BOTTOM = 68, 184
TABS_Y, TABS_H, INPUT_Y = 188, 16, 208


def status(c, x, y, bars=3, battery=100, clock="14:52"):
    for i in range(4):
        bh = 4 + i * 3
        c.fill(x + 8 + i * 5, y + 16 - bh, x + 11 + i * 5, y + 16, GREEN if i < bars else (42, 50, 56))
    c.text(x + W // 2 - c.width(clock) // 2, y + 5, clock, TEXT)
    pct = f"{battery}%"
    c.text(x + W - 46 - c.width(pct), y + 5, pct, MUTED)
    bx = x + W - 42
    c.fill(bx, y + 5, bx + 30, y + 16, (0, 0, 0))
    c.fill(bx + 1, y + 6, bx + 29, y + 15, (18, 24, 28))
    c.fill(bx + 1, y + 6, bx + 1 + int(28 * battery / 100), y + 15, GREEN)
    c.fill(bx + 30, y + 8, bx + 32, y + 13, (42, 50, 56))
    c.divider(x + 6, y + 19, W - 12)


def head(c, x, y, title, coins=10, handle="quartz-moth-34", crew="Красные", state="СТОЙКА", state_color=GREEN):
    c.text(x + 8, y + 24, title, TEXT)
    c.text(x + W - 8 - c.width(f"#{coins}"), y + 24, f"#{coins}", AMBER)
    who = f"{handle} · {crew}"
    c.text(x + 8, y + 35, who[: (W - 24 - c.width(state)) // 6], MUTED)
    c.text(x + W - 8 - c.width(state), y + 35, state, state_color)


def noise(c, x, y, level=42, hint="СЛЫШНО", hint_color=AMBER):
    c.text(x + 8, y + 46, "ШУМ", MUTED)
    lit = -(-level * 14 // 100)
    for i in range(14):
        bh = 3 + i
        color = (RED if i > 10 else AMBER if i > 6 else GREEN) if i < lit else (35, 40, 44)
        bx = x + 34 + i * 7
        c.fill(bx, y + 60 - bh, bx + 5, y + 60, color)
    c.text(x + W - 8 - c.width(hint), y + 46, hint, hint_color)
    c.divider(x + 6, y + 63, W - 12)


def feed(c, x, y):
    ly = y + TOP
    c.text(x + 8, ly, "Пеленг готов: точка в 340 м", AMBER)
    ly += 10
    chat = [
        ("quartz-moth-34", "слышу вас на пятёрке", GREEN),
        ("oxide-yarrow-94", "стойка у моста, жду", MUTED),
        ("murk-marble-70", "кто брал схрон у школы?", MUTED),
        ("quartz-moth-34", "не я. смотрите эфир", GREEN),
        ("oxide-yarrow-94", "у меня шум, молчу", MUTED),
        ("murk-marble-70", "принял", MUTED),
    ]
    for who, text, color in chat:
        if ly + 10 > y + 130:
            break
        c.text(x + 8, ly, f"{who}: {text}"[: (W - 20) // 6], color)
        ly += 10
    c.divider(x + 6, y + 132, W - 12)
    by = y + 136
    c.text(x + 8, by, "ЛУЧИ", MUTED)
    by += 10
    for line in ["quartz-moth-34 34° ±10 x2", "oxide-yarrow-94 -125° ±12"]:
        c.text(x + 8, by, line, AMBER)
        by += 10
    ty = max(by + 2, y + 158)
    c.text(x + 8, ty, "ТОЧКА oxide-yarrow-94 ±14 · 2м1с", GREEN)


def radar(c, x, y, r=48):
    cx, cy = x + 56, y + 124
    c.well(cx - r, cy - r, r * 2, r * 2)
    for i in range(-r + 24, r, 24):
        c.fill(cx - r, cy + i, cx + r, cy + i + 1, (29, 42, 36))
        c.fill(cx + i, cy - r, cx + i + 1, cy + r, (29, 42, 36))
    c.fill(cx, cy - r, cx + 1, cy + r, (43, 58, 50))
    c.fill(cx - r, cy, cx + r, cy + 1, (43, 58, 50))
    # луч
    for step in range(0, 44):
        px = cx + int(step * 0.9)
        py = cy - int(step * 0.6)
        if (px - cx) ** 2 + (py - cy) ** 2 > (r - 1) ** 2:
            break
        c.fill(px, py, px + 1, py + 1, AMBER)
    c.fill(cx - 1, cy - 1, cx + 2, cy + 2, AMBER)
    # блипы
    for dx, dy, color in [(18, -12, GREEN), (-22, 20, BLUE), (30, 26, AMBER)]:
        c.fill(cx + dx - 1, cy + dy - 1, cx + dx + 2, cy + dy + 2, color)
    c.text(cx - 2, cy - r + 2, "С", MUTED)
    c.text(cx - c.width("r 52") // 2, cy + r + 3, "r 52", (74, 84, 90))
    rx, ly = x + 112, y + TOP
    c.text(rx, ly, "ОТМЕТКИ", MUTED)
    ly += 10
    for line, color in [("Стойка 34м", AMBER), ("Маяк 120м", GREEN), ("Клетка 210м", MUTED), ("Ложный 260м", BLUE)]:
        c.text(rx, ly, line, color)
        ly += 10
    ly += 4
    c.text(rx, ly, "oxide-yarrow-94 ±14", GREEN)


def net(c, x, y):
    ly = y + TOP
    c.button(x + 8, ly, 84, 13, "АВИАРЕЖИМ")
    c.button(x + 96, ly, 56, 13, "DND")
    c.button(x + 156, ly, 84, 13, "РАДАР HUD", on=True)
    ly += 17
    c.button(x + 8, ly, 110, 13, "ГАЙД")
    c.button(x + 122, ly, 118, 13, "СЖЕЧЬ SIM")
    ly += 21
    c.well(x + 8, ly, W - 16, 54)
    rows = [("аплинк", "СТОЙКА", GREEN), ("VPN", "VPN", GREEN), ("IDS", "да", GREEN),
            ("закалка", "нет", MUTED), ("спутник", "0", MUTED)]
    ry = ly + 4
    for label, value, color in rows:
        c.text(x + 12, ry, label, MUTED)
        c.text(x + W - 24 - c.width(value), ry, value, color)
        ry += 10
    ly += 60
    c.text(x + 8, ly, "Связь зависит от стойки рядом", (74, 84, 90))


def market(c, x, y):
    ty = y + TOP
    w = (W - 16 - 8) // 3
    c.tab(x + 8, ty, w, 14, "ВИТРИНА", on=True)
    c.tab(x + 8 + w + 4, ty, w, 14, "ДОСКА")
    c.tab(x + 8 + (w + 4) * 2, ty, w, 14, "АУКЦИОН")
    names = ["SIM", "ЭЛЕМ", "ПРОШ", "КОНТ", "VPN", "IDS", "ЗАК", "ЛОЖ", "КЛЕТ", "ГЛУШ", "СПУТ", "НУЛЬ"]
    for i in range(12):
        sx = x + 11 + (i % 6) * 38
        sy = ty + 18 + (i // 6) * 40
        c.box(sx, sy, 36, 38, (16, 22, 20) if i else ON_FILL, EDGE if i == 0 else EDGE_DIM)
        c.text(sx + 6, sy + 8, names[i], TEXT)
        price = "#" + str(6 + i * 4)
        c.text(sx + max(2, (36 - c.width(price)) // 2), sy + 27, price, AMBER)
    fy = ty + 99
    c.divider(x + 6, fy - 3, W - 12)
    c.box(x + 9, fy + 2, 16, 16, (16, 22, 20), EDGE_DIM)
    c.text(x + 28, fy + 1, "Элемент питания", TEXT)
    c.text(x + 28, fy + 11, "продавец  #6", AMBER)
    c.button(x + 160, fy + 3, 78, 14, "КУПИТЬ", on=True)


def phone_tabs(c, x, y, active):
    names = ["ЭФИР", "КАРТА", "СЕТЬ", "РЫНОК"]
    for i, name in enumerate(names):
        c.tab(x + 6 + i * 59, y + TABS_Y, 57, TABS_H, name, on=i == active)


def phone(tab, title, path, c_status="СТОЙКА"):
    c = Canvas(W, H)
    x, y = 0, 0
    c.panel(x, y, W, H)
    status(c, x, y, bars=3, battery=100)
    head(c, x, y, title, 10, "quartz-moth-34", "Красные", c_status, GREEN if c_status == "СТОЙКА" else MUTED)
    noise(c, x, y, 42, "СЛЫШНО", AMBER)
    if tab == 0:
        feed(c, x, y)
        c.well(x + 8, y + INPUT_Y, W - 66, 14)
        c.fill(x + 8, y + INPUT_Y + 13, x + W - 58, y + INPUT_Y + 14, EDGE_DIM)
        c.text(x + 12, y + INPUT_Y + 3, "@ник — личка, Enter", (74, 84, 90))
        c.button(x + W - 56, y + INPUT_Y, 48, 14, "ОТПР")
    elif tab == 1:
        radar(c, x, y)
    elif tab == 2:
        net(c, x, y)
    else:
        market(c, x, y)
    phone_tabs(c, x, y, tab)
    c.save(path)


def menu():
    width, height = 480, 270
    c = Canvas(width, height)
    c.fill(0, 0, width, height, (7, 12, 9))
    # имитация фона: силуэты труб и тарелки
    for bx, bw, bh in [(40, 12, 90), (60, 9, 70), (86, 14, 60), (300, 10, 80)]:
        c.fill(bx, height - 60 - bh, bx + bw, height - 40, (10, 16, 12))
    c.fill(150, height - 120, 190, height - 60, (12, 18, 14))
    c.text(width - 12 - c.width("> YOU ARE NOT SAFE"), 14, "> YOU ARE NOT SAFE", (143, 168, 154))
    for i, line in enumerate(["IP: 192.168.0.1", "LOCATION: UNKNOWN", "STATUS: EXPOSED", "TRUST NO ONE"]):
        color = RED if "EXPOSED" in line else (143, 168, 154)
        c.text(width - 12 - c.width("> " + line), 25 + i * 11, "> " + line, color)
    c.text(width - 12 - c.width("ТВОЯ ЛИЧНОСТЬ УЖЕ СЛИТА"), 25 + 4 * 11 + 6, "ТВОЯ ЛИЧНОСТЬ УЖЕ СЛИТА", AMBER)
    title = "ЧЁРНЫЙ КАНАЛ"
    scale = 3
    big = ImageFont.truetype(FONT_PATH, 10 * SCALE * scale // 2)
    c.draw.text((width // 2 - c.width(title) * scale // 2, 40), title, font=big, fill=(201, 210, 196))
    c.text(width // 2 - c.width("JAVA EDITION") // 2, 78, "JAVA EDITION", (143, 216, 160))
    c.text(width // 2 - c.width("— VERSION 1.0.0 —") // 2, 92, "— VERSION 1.0.0 —", (168, 176, 166))
    bx, bw = width // 2 - 170, 340
    for i, label in enumerate(["Одиночная игра", "Сетевая игра", "Minecraft Realms"]):
        y = 108 + i * 34
        c.fill(bx, y, bx + bw, y + 26, (18, 21, 24))
        c.fill(bx, y, bx + bw, y + 1, (58, 47, 22))
        c.text(bx + (bw - c.width(label)) // 2, y + 9, label, TEXT)
    half = (bw - 8) // 2
    for i, label in enumerate(["Настройки...", "Выйти из игры"]):
        y, x = 216, bx + i * (half + 8)
        c.fill(x, y, x + half, y + 22, (18, 21, 24))
        c.fill(x, y, x + half, y + 1, (58, 47, 22))
        c.text(x + (half - c.width(label)) // 2, y + 7, label, TEXT)
    for i, line in enumerate(["> scan_user", "> search_history", "> leak_data", "> deanonymize", "> complete"]):
        c.text(12, height - 70 + i * 11, line, (102, 160, 108))
    c.text(12, height - 12, "Minecraft 1.20.1 · Forge", (102, 176, 166))
    c.save("ui-menu.png")


def admin():
    W2, H2 = 320, 232
    c = Canvas(W2, H2)
    c.panel(0, 0, W2, H2)
    c.text(8, 7, "АДМИН-ПАНЕЛЬ", AMBER)
    c.text(W2 - 8 - c.width("OP · МАТЧ ИДЁТ"), 7, "OP · МАТЧ ИДЁТ", GREEN)
    c.text(8, 18, "до конца 12:40 · живых 4 · шум 42 · крипта 510", MUTED)
    tabs = ["МАТЧ", "ИГРОК", "ОТРЯД", "ЧЕК-ЛИСТ"]
    tw = (W2 - 16 - 9) // 4
    for i, name in enumerate(tabs):
        c.tab(8 + i * (tw + 3), 30, tw, 15, name, on=i == 0)
    c.well(6, 48, W2 - 12, H2 - 78)
    w = (W2 - 16 - 8) // 3
    for i, label in enumerate(["ЦЕНТР ЗДЕСЬ", "СТАРТ", "СТОП"]):
        c.button(8 + i * (w + 4), 54, w, 14, label, on=i == 1)
    c.text(8, 74, "размер", MUTED)
    c.well(52, 72, 28, 10)
    c.text(56, 73, "1000", AMBER)
    for i, size in enumerate(["1000", "1500", "2000"]):
        c.button(84 + i * 44, 72, 40, 12, size)
    c.button(216, 72, 22, 12, "✓")
    c.button(8, 90, 150, 14, "ФИНАЛ ЧЕРЕЗ 60С")
    c.button(162, 90, 150, 14, "СВОДКА В ЧАТ")
    c.divider(8, 112, W2 - 16)
    c.text(8, 118, "ОТРЯДЫ И ОЧКИ", MUTED)
    for i, (name, score, alive) in enumerate([("Красные", 14, "2/2"), ("Голубые", 9, "1/2"), ("Зелёные", 3, "0/1")]):
        y = 129 + i * 12
        c.text(11, y + 1, name, TEXT)
        c.text(W2 - 16 - c.width(alive), y + 1, alive, MUTED)
        c.text(11 + c.width(name) + 8, y + 1, str(score), TEXT)
    c.text(8, H2 - 22, "/bc admin · центр и размер как в /minigame", (62, 74, 70))
    c.save("ui-admin.png")


def laptop():
    W3, H3 = 260, 222
    c = Canvas(W3, H3)
    c.panel(0, 0, W3, H3)
    c.text(8, 6, "oxide-yarrow-94", EDGE)
    c.text(W3 - 8 - c.width("#127"), 6, "#127", AMBER)
    c.text(8, 16, "RACK  T0 D0 S0 B0", MUTED)
    c.divider(6, 26, W3 - 12)
    c.tab(8, 30, 80, 14, "СЛЕД", on=True)
    c.tab(90, 30, 80, 14, "СУМКА")
    c.tab(172, 30, 80, 14, "ТОРГ")
    c.well(6, 46, W3 - 12, H3 - 52)
    c.button(8, 48, 118, 13, "TRACE")
    c.button(134, 48, 118, 13, "WIPE")
    c.button(8, 64, 118, 13, "BOMB")
    c.button(134, 64, 118, 13, "UAV")
    c.text(8, 82, "Перезарядка 20 с.", AMBER)
    c.text(8, 100, "позывной для TRACE", (62, 74, 70))
    c.text(8, 122, "ЛУЧИ", MUTED)
    c.text(8, 133, "murk-marble-70 -125° ±12", AMBER)
    c.text(8, 143, "oxide-yarrow-94 34° ±10 x2", AMBER)
    c.divider(8, 154, W3 - 16)
    c.text(8, 157, "INTEL", MUTED)
    for i, (label, stale) in enumerate([("#1 murk-marble-70 ±14", False), ("#2 quartz-moth-34 ±18", True)]):
        y = 168 + i * 12
        if i == 0:
            c.fill(8, y, W3 - 8, y + 11, ON_FILL)
            c.fill(8, y, 10, y + 11, EDGE)
        c.text(11, y + 2, label, GREEN if not stale else MUTED)
    c.save("ui-laptop.png")


if __name__ == "__main__":
    phone(0, "ЭФИР", "ui-phone-feed.png")
    phone(1, "КАРТА", "ui-phone-map.png")
    phone(2, "СЕТЬ", "ui-phone-net.png")
    phone(3, "РЫНОК", "ui-phone-market.png")
    menu()
    admin()
    laptop()
