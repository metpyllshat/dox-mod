#!/usr/bin/env python3
"""Пиксель-арт текстуры Black Channel: предметы 16x16 и блоки 16x16."""
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "blackchannel", "textures")

W = H = 16


def c(rgb, a=255):
    if isinstance(rgb, tuple):
        return (rgb[0], rgb[1], rgb[2], a)
    return ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a)


T = (0, 0, 0, 0)

INK = c(0x0B0F12)
INK2 = c(0x151B20)
STEEL = c(0x555F66)
STEEL_HI = c(0x77848C)
STEEL_LO = c(0x39434A)
DARK = c(0x1E262B)
GLASS = c(0x0F2B24)
GREEN = c(0x35E08A)
GREEN_HI = c(0x8CFFC6)
GREEN_LO = c(0x159055)
AMBER = c(0xFFB43D)
AMBER_HI = c(0xFFD98A)
RED = c(0xE8503F)
RED_HI = c(0xFF9A86)
RED_LO = c(0x8E2A22)
BLUE = c(0x3DA9E0)
BLUE_HI = c(0x9BD9FF)
BLUE_LO = c(0x1C5E80)
PURPLE = c(0x9A55E0)
PURPLE_HI = c(0xD0A8FF)
CYAN = c(0x39E0D0)
CYAN_HI = c(0xA8FFF4)
GOLD = c(0xE0A93D)
GOLD_HI = c(0xFFE08A)
GOLD_LO = c(0x9C6C18)
PAPER = c(0xE6E2D4)
PAPER_LO = c(0xB8B3A2)
WHITE = c(0xEFF4F6)
WHITE_LO = c(0xA8B4BB)


class Art:
    def __init__(self):
        self.g = [[T for _ in range(W)] for _ in range(H)]

    def px(self, x, y, col):
        if 0 <= x < W and 0 <= y < H:
            self.g[y][x] = col

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, col)

    def frame(self, x0, y0, x1, y1, col):
        for x in range(x0, x1 + 1):
            self.px(x, y0, col)
            self.px(x, y1, col)
        for y in range(y0, y1 + 1):
            self.px(x0, y, col)
            self.px(x1, y, col)

    def hline(self, x0, x1, y, col):
        for x in range(x0, x1 + 1):
            self.px(x, y, col)

    def vline(self, x, y0, y1, col):
        for y in range(y0, y1 + 1):
            self.px(x, y, col)

    def rows(self, y0, lines, pal):
        for dy, line in enumerate(lines):
            for dx, ch in enumerate(line):
                if ch in pal:
                    self.px(dx, y0 + dy, pal[ch])

    def stamp(self, x0, y0, lines, pal):
        for dy, line in enumerate(lines):
            for dx, ch in enumerate(line):
                if ch in pal:
                    self.px(x0 + dx, y0 + dy, pal[ch])

    def outline(self, col=INK):
        """Тёмный контур по краю непрозрачных пикселей."""
        src = [row[:] for row in self.g]
        for y in range(H):
            for x in range(W):
                if src[y][x][3] != 0:
                    continue
                near = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and src[ny][nx][3] != 0:
                        near = True
                        break
                if near:
                    self.g[y][x] = col

    def pixels(self):
        return self.g


def png_bytes(grid):
    raw = bytearray()
    for row in grid:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))
    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- предметы

def burner_phone():
    a = Art()
    a.rect(3, 1, 12, 14, INK)
    a.rect(4, 2, 11, 13, DARK)
    a.rect(4, 2, 11, 2, STEEL_LO)
    a.rect(5, 4, 10, 9, GLASS)
    a.rect(6, 5, 9, 8, GREEN_LO)
    a.rect(6, 5, 7, 6, GREEN)
    a.px(6, 5, GREEN_HI)
    a.rect(5, 11, 10, 12, STEEL_LO)
    a.hline(5, 10, 11, STEEL)
    a.px(7, 12, STEEL_HI)
    a.px(8, 12, STEEL_HI)
    a.vline(4, 3, 12, STEEL)
    a.px(11, 3, STEEL_LO)
    a.px(2, 0, STEEL_HI)
    a.px(2, 1, STEEL)
    a.px(1, 0, STEEL_LO)
    return a


def field_laptop():
    a = Art()
    a.rect(1, 2, 14, 10, INK)
    a.rect(2, 3, 13, 9, STEEL_LO)
    a.rect(3, 4, 12, 8, GLASS)
    a.rect(4, 5, 11, 7, GREEN_LO)
    a.hline(4, 9, 5, GREEN)
    a.hline(4, 7, 6, GREEN_HI)
    a.rect(0, 11, 15, 13, INK)
    a.rect(1, 11, 14, 12, STEEL)
    a.hline(1, 14, 11, STEEL_HI)
    a.hline(1, 14, 13, STEEL_LO)
    for x in range(3, 13, 2):
        a.px(x, 12, DARK)
    a.rect(6, 14, 9, 14, STEEL_LO)
    return a


def chip(accent, accent_hi, glyph):
    """Плата с ножками и мини-глифом 5x5."""
    a = Art()
    a.rect(2, 2, 13, 13, INK)
    a.rect(3, 3, 12, 12, c(0x1F4034))
    a.rect(3, 3, 12, 3, c(0x2C5B49))
    a.frame(3, 3, 12, 12, c(0x153026))
    a.rect(5, 5, 10, 10, accent)
    a.rect(5, 5, 10, 5, accent_hi)
    a.frame(5, 5, 10, 10, c(0x0E1A16))
    a.stamp(6, 6, glyph, {"#": INK, "o": WHITE})
    for i in range(4, 12, 2):
        a.px(i, 14, GOLD_LO)
        a.px(i, 1, GOLD_LO)
        a.px(1, i, GOLD_LO)
        a.px(14, i, GOLD_LO)
    a.px(3, 3, WHITE_LO)
    return a


GLYPH_V = ["#..#", "#..#", "#..#", ".##."]
GLYPH_VV = ["#..#", "#.##", "#.##", ".##."]
GLYPH_BOLT = [".##.", "##..", "###.", "..#."]
GLYPH_BANG = ["..#.", "..#.", "....", "..#."]
GLYPH_ZERO = [".##.", "#..#", "#..#", ".##."]
GLYPH_SHIELD = ["####", "#..#", ".##.", "...."]
GLYPH_SAT = ["..#.", ".##.", "###.", ".#.."]
GLYPH_WAVE = ["#..#", ".##.", ".##.", "#..#"]


def vpn_chip():
    return chip(GREEN_LO, GREEN, GLYPH_V)


def deep_vpn():
    return chip(PURPLE, PURPLE_HI, GLYPH_VV)


def ids_module():
    return chip(BLUE_LO, BLUE, GLYPH_SHIELD)


def emp_hardening():
    return chip(c(0xB06A1E), AMBER, GLYPH_BOLT)


def exploit_firmware():
    return chip(RED_LO, RED, GLYPH_BANG)


def zero_day():
    return chip(c(0x1E4A46), CYAN, GLYPH_ZERO)


def satellite_firmware():
    return chip(c(0x7A5A16), GOLD, GLYPH_SAT)


def signal_sample():
    a = Art()
    a.rect(1, 3, 14, 12, INK)
    a.rect(2, 4, 13, 11, DARK)
    a.rect(2, 4, 13, 4, STEEL_LO)
    a.stamp(6, 6, ["#...#", ".#.#.", "..#..", ".#.#."], {"#": GREEN})
    a.px(7, 7, GREEN_HI)
    for x in (3, 6, 9, 12):
        a.px(x, 10, GREEN_LO if x % 2 else CYAN)
    a.px(3, 5, STEEL_HI)
    return a


def battery_cell():
    a = Art()
    a.rect(4, 2, 11, 14, INK)
    a.rect(5, 3, 10, 13, c(0x2E7A4A))
    a.rect(5, 3, 10, 3, c(0x49B06C))
    a.rect(4, 4, 4, 13, c(0x235C39))
    a.rect(6, 0, 9, 1, GOLD_LO)
    a.rect(7, 5, 8, 8, GOLD_HI)
    a.rect(6, 9, 9, 9, GOLD_HI)
    a.rect(7, 11, 8, 12, GOLD)
    a.px(9, 13, c(0x1B4A2D))
    return a


def burner_sim():
    a = Art()
    a.rect(2, 2, 13, 13, INK)
    a.rect(3, 3, 12, 12, WHITE)
    a.rect(3, 3, 12, 3, WHITE_LO)
    a.rect(2, 2, 6, 6, INK)
    a.rect(3, 3, 5, 5, WHITE)
    a.rect(6, 7, 11, 11, GOLD_LO)
    a.rect(7, 7, 10, 7, GOLD)
    a.hline(7, 10, 9, GOLD)
    a.vline(8, 7, 10, GOLD_HI)
    a.hline(7, 10, 11, GOLD)
    a.px(3, 12, WHITE_LO)
    a.px(12, 3, WHITE)
    return a


def hashcoin():
    a = Art()
    for y in range(H):
        for x in range(W):
            dx, dy = x - 7.5, y - 7.5
            if dx * dx + dy * dy <= 34:
                a.px(x, y, GOLD)
    for y in range(H):
        for x in range(W):
            dx, dy = x - 7.5, y - 7.5
            d = dx * dx + dy * dy
            if 20 < d <= 34:
                a.px(x, y, GOLD_LO)
            elif d <= 8:
                a.px(x, y, GOLD_HI)
    a.stamp(6, 5, ["#...#", "#.#.#", "###.#", "#.#.#", "#...#"], {"#": c(0x7A4E10)})
    a.px(5, 4, GOLD_HI)
    return a


def dog_tag():
    a = Art()
    a.rect(5, 4, 11, 14, INK)
    a.rect(6, 5, 10, 13, STEEL)
    a.rect(6, 5, 10, 5, STEEL_HI)
    a.rect(6, 13, 10, 13, STEEL_LO)
    a.px(8, 4, INK)
    a.rect(7, 7, 9, 7, STEEL_LO)
    a.rect(7, 9, 9, 9, STEEL_LO)
    a.px(3, 2, STEEL_HI)
    a.px(4, 3, STEEL)
    a.px(5, 4, STEEL_LO)
    a.px(6, 1, STEEL_HI)
    a.px(7, 2, STEEL_LO)
    return a


def false_contract():
    a = Art()
    a.rect(2, 1, 13, 14, INK)
    a.rect(3, 2, 12, 13, PAPER)
    a.rect(3, 2, 12, 2, PAPER_LO)
    for y in (4, 6, 8, 10):
        a.hline(4, 10, y, PAPER_LO)
    a.hline(4, 7, 12, PAPER_LO)
    for y in range(9, 13):
        for x in range(9, 13):
            dx, dy = x - 10.5, y - 10.5
            if dx * dx + dy * dy <= 3:
                a.px(x, y, RED)
    a.px(10, 10, RED_HI)
    a.px(11, 3, PAPER_LO)
    return a


def team_beacon_block(accent, accent_hi, accent_lo):
    a = Art()
    a.rect(1, 1, 14, 14, INK)
    a.rect(2, 2, 13, 13, DARK)
    a.rect(2, 2, 13, 2, STEEL_LO)
    a.rect(2, 13, 13, 13, c(0x0C1114))
    a.rect(5, 5, 10, 10, c(0x0E1A16))
    a.rect(6, 6, 9, 9, accent)
    a.rect(6, 6, 9, 6, accent_hi)
    a.px(6, 9, accent_lo)
    a.px(9, 9, accent_lo)
    a.px(3, 3, STEEL_HI)
    a.px(12, 3, STEEL)
    a.px(3, 12, STEEL)
    a.px(12, 12, STEEL_LO)
    a.vline(1, 4, 11, STEEL_LO)
    a.vline(14, 4, 11, STEEL_LO)
    for y in (4, 11):
        a.hline(3, 12, y, STEEL_LO)
    return a


def block_rack():
    a = Art()
    a.rect(0, 0, 15, 15, DARK)
    a.rect(0, 0, 15, 15, c(0x232C31))
    a.frame(0, 0, 15, 15, c(0x11171A))
    for y in (2, 3, 7, 8, 12, 13):
        a.hline(2, 13, y, c(0x2F3A40))
    a.rect(2, 2, 13, 3, c(0x1A2226))
    a.rect(2, 7, 13, 8, c(0x1A2226))
    a.rect(2, 12, 13, 13, c(0x1A2226))
    for y in (2, 7, 12):
        a.px(3, y, GREEN)
        a.px(4, y, GREEN_LO)
        a.px(11, y, AMBER)
    a.hline(6, 9, 5, c(0x161D20))
    a.hline(6, 9, 10, c(0x161D20))
    a.px(0, 0, STEEL_LO)
    a.px(15, 15, c(0x090C0E))
    return a


def block_dish():
    a = Art()
    a.rect(0, 0, 15, 15, c(0x8E979D))
    a.frame(0, 0, 15, 15, c(0x6A7378))
    for y in range(1, 15, 3):
        a.hline(1, 14, y, c(0x99A2A7))
    # тарелка: кольцо + чаша
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.0
            d = dx * dx + dy * dy
            if d <= 8.5:
                a.px(x, y, c(0x3D464B))
            elif d <= 12.5:
                a.px(x, y, c(0x505A60))
            elif d <= 25.0:
                a.px(x, y, c(0xE2E9EC))
            elif d <= 31.0:
                a.px(x, y, c(0xBCC7CC))
    a.px(7, 3, c(0xF4F8FA))
    a.px(6, 4, c(0xF4F8FA))
    a.px(5, 5, c(0xD6DEE2))
    # облучатель
    a.px(7, 7, CYAN)
    a.px(8, 7, CYAN_HI)
    a.px(7, 8, c(0x6E787E))
    # стойка
    a.rect(7, 12, 8, 13, c(0x59636A))
    a.rect(6, 14, 9, 14, c(0x6E787E))
    return a


def block_faraday():
    a = Art()
    a.rect(0, 0, 15, 15, c(0x8E979C))
    for y in range(0, 16, 3):
        a.hline(0, 15, y, c(0x6B7479))
    for x in range(0, 16, 3):
        a.vline(x, 0, 15, c(0x6B7479))
    for y in range(16):
        for x in range(16):
            if (x + y) % 6 == 0:
                a.px(x, y, c(0xAAB3B8))
    a.frame(0, 0, 15, 15, c(0x4E565A))
    a.rect(6, 6, 9, 9, c(0x5B6469))
    a.px(7, 7, CYAN)
    return a


def block_jammer():
    a = Art()
    a.rect(0, 0, 15, 15, c(0x3A2224))
    a.frame(0, 0, 15, 15, c(0x1A0F10))
    for y in range(2, 14, 3):
        a.hline(1, 14, y, c(0x59302F))
    a.rect(3, 4, 12, 10, c(0x24191A))
    a.rect(4, 5, 11, 9, c(0x2E2021))
    a.rect(5, 6, 6, 8, RED)
    a.rect(8, 6, 9, 8, RED_LO)
    a.px(5, 6, RED_HI)
    a.px(3, 2, c(0x7A8A90))
    a.px(4, 2, c(0x5C696E))
    a.px(12, 12, c(0x6E3433))
    return a


def block_beacon(accent, accent_hi, accent_lo):
    a = Art()
    a.rect(0, 0, 15, 15, c(0x333B40))
    a.frame(0, 0, 15, 15, c(0x11171A))
    a.frame(1, 1, 14, 14, c(0x454F55))
    a.rect(3, 3, 12, 12, c(0x0D1417))
    a.rect(4, 4, 11, 11, accent_lo)
    a.rect(5, 5, 10, 10, accent)
    a.rect(6, 6, 9, 9, accent_hi)
    a.rect(7, 7, 8, 8, WHITE)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        a.px(x, y, c(0x6B767C))
    a.hline(3, 12, 2, c(0x2A3237))
    a.hline(3, 12, 13, c(0x1C2327))
    return a


def block_logger():
    a = Art()
    a.rect(0, 0, 15, 15, c(0x4A3A28))
    for x in range(0, 16, 4):
        a.vline(x, 0, 15, c(0x3A2D1F))
    for y in range(0, 16, 5):
        a.hline(0, 15, y, c(0x3A2D1F))
    a.frame(0, 0, 15, 15, c(0x241A10))
    a.rect(4, 4, 11, 11, c(0x1B2420))
    a.rect(5, 5, 10, 10, c(0x24322B))
    for y in (6, 8, 10):
        a.hline(6, 9, y, GREEN_LO)
    a.px(6, 6, GREEN)
    a.px(9, 10, AMBER)
    a.px(2, 2, c(0x5C4A33))
    return a


ITEMS = {
    "burner_phone": burner_phone,
    "field_laptop": field_laptop,
    "vpn_chip": vpn_chip,
    "deep_vpn": deep_vpn,
    "ids_module": ids_module,
    "emp_hardening": emp_hardening,
    "exploit_firmware": exploit_firmware,
    "zero_day": zero_day,
    "false_contract": false_contract,
    "battery_cell": battery_cell,
    "burner_sim": burner_sim,
    "satellite_firmware": satellite_firmware,
    "hashcoin": hashcoin,
    "signal_sample": signal_sample,
    "dog_tag": dog_tag,
    "team_beacon": lambda: team_beacon_block(GREEN, GREEN_HI, GREEN_LO),
    "decoy_beacon": lambda: team_beacon_block(AMBER, AMBER_HI, c(0xB06A1E)),
    "uplink_rack": lambda: block_rack(),
    "sigint_dish": lambda: block_dish(),
    "faraday_anchor": lambda: block_faraday(),
    "signal_jammer": lambda: block_jammer(),
    "trace_logger": lambda: block_logger(),
}

BLOCKS = {
    "uplink_rack": block_rack,
    "sigint_dish": block_dish,
    "faraday_anchor": block_faraday,
    "signal_jammer": block_jammer,
    "team_beacon": lambda: block_beacon(GREEN_LO, GREEN, GREEN_HI),
    "decoy_beacon": lambda: block_beacon(c(0xB06A1E), AMBER, AMBER_HI),
    "trace_logger": block_logger,
}


def write(path, art):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png_bytes(art.pixels()))


def main():
    for name, fn in ITEMS.items():
        art = fn()
        art.outline()
        write(os.path.join(RES, "item", name + ".png"), art)
    for name, fn in BLOCKS.items():
        write(os.path.join(RES, "block", name + ".png"), fn())
    print("textures written:", len(ITEMS), "items,", len(BLOCKS), "blocks")


if __name__ == "__main__":
    main()
