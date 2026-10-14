"""
Skins and extra textures of the spellcasting mobs (Valkyrie Sorceress, Solar Acolyte).

The skins use the standard 64x64 player layout, which is what Iron's Spells' shared humanoid casting model
(`irons_spellbooks:geo/abstract_casting_mob.geo.json`) is mapped to. All art is original: every face is
authored below as a pixel map. Called from gen_textures.main(); also writes build/mob_preview.png, a small
software render of the skins on the model (and the wings / halo) for checking the art without launching the game.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from gen_textures import Canvas, Shapes, blur, hexa, PROJECT, ROOT

MOB_PREVIEW = os.path.join(PROJECT, "build", "mob_preview.png")

# ============================================================================ skin layout
# part -> ((u, v), (width, height, depth)) in the 64x64 player skin
PARTS = {
    "head": ((0, 0), (8, 8, 8)), "hat": ((32, 0), (8, 8, 8)),
    "body": ((16, 16), (8, 12, 4)), "jacket": ((16, 32), (8, 12, 4)),
    "rarm": ((40, 16), (4, 12, 4)), "rsleeve": ((40, 32), (4, 12, 4)),
    "larm": ((32, 48), (4, 12, 4)), "lsleeve": ((48, 48), (4, 12, 4)),
    "rleg": ((0, 16), (4, 12, 4)), "rpants": ((0, 32), (4, 12, 4)),
    "lleg": ((16, 48), (4, 12, 4)), "lpants": ((0, 48), (4, 12, 4)),
}
# Light from the front-top; the maps give the material, this adds the form shading per face.
FACE_LIGHT = {"top": 1.06, "front": 1.0, "right": 0.9, "left": 0.9, "back": 0.82, "bottom": 0.72}


def face_rect(part, face):
    (u, v), (w, h, d) = PARTS[part]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]


class Skin:
    """Paints pixel maps onto a player-layout skin and records which pixels glow."""

    def __init__(self, palette, glow=""):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.pal = {k: hexa(v) for k, v in palette.items()}
        self.glow_keys = set(glow)

    def _put(self, x, y, ch, light):
        if ch not in self.pal:
            return
        r, g, b, a = self.pal[ch]
        if ch in self.glow_keys:
            self.glow.putpixel((x, y), (r, g, b, 255))
            light = 1.0
        self.img.putpixel((x, y), (min(255, int(r * light)), min(255, int(g * light)), min(255, int(b * light)), a))

    def face(self, part, face, rows):
        x0, y0, w, h = face_rect(part, face)
        assert len(rows) == h and all(len(r) == w for r in rows), (part, face, [len(r) for r in rows])
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                self._put(x0 + x, y0 + y, ch, FACE_LIGHT[face])

    def wrap(self, part, rows):
        """rows span right | front | left | back side by side (the order the faces are unfolded in)."""
        (_, _), (w, h, d) = PARTS[part]
        spans = (("right", d), ("front", w), ("left", d), ("back", w))
        assert len(rows) == h and all(len(r) == 2 * (w + d) for r in rows), (part, [len(r) for r in rows])
        x = 0
        for face, fw in spans:
            self.face(part, face, [r[x:x + fw] for r in rows])
            x += fw

    def fill(self, part, face, ch):
        x0, y0, w, h = face_rect(part, face)
        self.face(part, face, [ch * w] * h)


def mirror_wrap(rows, w, d):
    """Wrap rows for the opposite limb: swap the outer and inner sides and mirror every face."""
    out = []
    for r in rows:
        right, front, left, back = r[:d], r[d:d + w], r[d + w:2 * d + w], r[2 * d + w:]
        out.append(left[::-1] + front[::-1] + right[::-1] + back[::-1])
    return out


# ============================================================================ Valkyrie Sorceress
VALKYRIE_PAL = {
    # hair
    "H": "#E2B64C", "L": "#F8DE86", "h": "#B8882A", "d": "#7E5818",
    # skin / face
    "S": "#F6D0AE", "s": "#E0AC8C", "k": "#F0A898", "W": "#FAFCFF", "B": "#3E8EDA", "b": "#1E4C8C", "l": "#6A4420", "m": "#C47468",
    # silver armour
    "A": "#F4F6FC", "a": "#D4DAE8", "1": "#A8B1C6", "0": "#727C94",
    # gold trim
    "G": "#EAB93A", "g": "#B4841E", "Y": "#FFE7A0",
    # sky-blue cloth, white cloth
    "C": "#70B8F0", "c": "#4488CC", "2": "#2C5E9E", "T": "#FAFBFF", "t": "#D2D9E8",
    # gem, soles
    "P": "#8AD8FF", "N": "#4A4E62",
}


def valkyrie_skin():
    s = Skin(VALKYRIE_PAL)
    # --- head: long golden hair, blue eyes
    s.wrap("head", [
        # right (back->front)  front                 left (front->back)   back
        "hHLHHLHH" + "HLLHHLLH" + "HHLHHLHh" + "HLHHLHHL",
        "HLHHLHHH" + "HHLhhLHH" + "HHHLHHLH" + "HHLHHLHH",
        "HHhHHHhH" + "HhSSSShH" + "HhHHHhHH" + "hHHhHHhH",
        "HhHHHHSS" + "HllSSllH" + "SSHHHHhH" + "HHhHHhHH",
        "HHhHHSSS" + "hWBSSBWh" + "SSSHHhHH" + "hHHHhHHh",
        "hHHhHHsS" + "HSSSsSSH" + "SsHHhHHh" + "HhHHHHhH",
        "HhHHhHHs" + "HSSkkSSH" + "sHHhHHhH" + "HHhHhHHH",
        "hHhHHhHh" + "hHsSSsHh" + "hHhHHhHh" + "hHHhHHhH",
    ])
    s.face("head", "top", ["HLHHHLHH", "HHLHHHLH", "hHHHLHHh", "HHhLHHHH", "HLHHhHLH", "HHLHHHHH", "hHHLHHLh", "HHHHLHHH"])
    s.fill("head", "bottom", "h")
    # hat layer: silver circlet with a blue gem and small feathered wings at the temples
    s.wrap("hat", [
        "........" + "........" + "........" + "........",
        "TTt....." + "........" + ".....tTT" + "........",
        "aAAAAAAa" + "aAAGPGAa" + "aAAAAAAa" + "aAaAAaAa",
        ".tTt...." + "........" + "....tTt." + "........",
        "........" + "........" + "........" + "........",
        "........" + "........" + "........" + "........",
        "........" + "........" + "........" + "........",
        "........" + "........" + "........" + "........",
    ])
    # --- body: silver breastplate with a gold sunburst, gold belt, sky-blue skirt
    s.wrap("body", [
        # right   front       left    back
        "1aAA" + "aAGSSGAa" + "AAa1" + "1aAAAAa1",
        "1aAA" + "aAAGGAAa" + "AAa1" + "1aAAAAa1",
        "1aAa" + "1aAAAAa1" + "aAa1" + "1aAaaAa1",
        "01aa" + "1aAGGAa1" + "aa10" + "01aAAa10",
        "01a1" + "1aGYPGa1" + "1a10" + "01aaaa10",
        "0111" + "01aGGa10" + "1110" + "00111100",
        "gGGG" + "GGGYYGGG" + "GGGg" + "gGGGGGGg",
        "2cCC" + "cCCGGCCc" + "CCc2" + "2cCCCCc2",
        "2cCC" + "cCCCCCCc" + "CCc2" + "2cCcCcC2",
        "2ccC" + "2cCcCCc2" + "Ccc2" + "2cCcCcc2",
        "22cC" + "2cCccCc2" + "Cc22" + "22cCcC22",
        "ggGG" + "gGGGGGGg" + "GGgg" + "gggGGggg",
    ])
    s.face("body", "top", ["aAAAAAAa", "aAAAAAAa", "1aAAAAa1", "1aaAAaa1"])
    s.fill("body", "bottom", "2")
    # jacket layer: her hair falls over the back plate
    s.wrap("jacket", [
        "...." + "........" + "...." + ".HLHHLH.",
        "...." + "........" + "...." + ".hHHLHh.",
        "...." + "........" + "...." + "..HhHH..",
        "...." + "........" + "...." + "..hHHh..",
        "...." + "........" + "...." + "...hh...",
    ] + ["...." + "........" + "...." + "........"] * 7)
    # --- arms: white sleeves, gold bracers with a gem, silver pauldrons on the overlay
    arm = [
        # right(outer) front  left(inner) back
        "TTTT" + "TTTT" + "tTTt" + "tTTt",
        "TTTt" + "TTTT" + "tTTt" + "tTTt",
        "TTtt" + "tTTt" + "ttTt" + "tTtt",
        "TTTt" + "TTTT" + "tTTt" + "tTTt",
        "TTTT" + "TTTT" + "tTTt" + "tTTt",
        "tTTt" + "tTTt" + "ttTt" + "tTtt",
        "ttTt" + "tTTt" + "tttt" + "ttTt",
        "GGGG" + "GYGG" + "GGGg" + "gGGG",
        "gGGg" + "gPGg" + "gGGg" + "gGGg",
        "gGGg" + "gGGg" + "ggGg" + "gGgg",
        "SSSS" + "SSSS" + "sSSs" + "sSSs",
        "sSSs" + "sSSs" + "ssSs" + "sSss",
    ]
    s.wrap("rarm", arm)
    s.wrap("larm", mirror_wrap(arm, 4, 4))
    for part in ("rarm", "larm"):
        s.fill(part, "top", "T")
        s.fill(part, "bottom", "s")
    pauldron = [
        "AAAA" + "AAAA" + "aAAa" + "aAAa",
        "aAAa" + "aAAa" + "1aa1" + "1aa1",
        "1aa1" + "1aa1" + "0110" + "0110",
        "GGGG" + "GYYG" + "gGGg" + "gGGg",
    ] + ["." * 16] * 8
    s.wrap("rsleeve", pauldron)
    s.wrap("lsleeve", mirror_wrap(pauldron, 4, 4))
    for part in ("rsleeve", "lsleeve"):
        s.face(part, "top", ["AAAA", "AAAA", "aAAa", "aAAa"])
    # --- legs: skirt (flared on the overlay), white leggings, silver-gold boots
    leg = [
        "CCCC" + "CCCC" + "cCCc" + "cCCc",
        "cCCc" + "cCCc" + "ccCc" + "cCcc",
        "ccCc" + "cCcC" + "c2cc" + "ccc2",
        "GGGG" + "GYGG" + "gGGg" + "gGGg",
        "TTTT" + "TTTT" + "tTTt" + "tTTt",
        "tTTt" + "TTTT" + "ttTt" + "tTtt",
        "tTTt" + "tTTt" + "tTtt" + "ttTt",
        "aAAa" + "aGGa" + "1aa1" + "1aa1",
        "GGGG" + "GYYG" + "gGGg" + "gGGg",
        "aAAa" + "AAAA" + "1aA1" + "1aa1",
        "1aA1" + "aAAa" + "01a0" + "0110",
        "0110" + "1aa1" + "0000" + "0000",
    ]
    s.wrap("rleg", leg)
    s.wrap("lleg", mirror_wrap(leg, 4, 4))
    for part in ("rleg", "lleg"):
        s.fill(part, "bottom", "N")
        s.fill(part, "top", "c")
    skirt = [
        "CCCC" + "CCCC" + "cCCc" + "cCCc",
        "cCCc" + "cCCc" + "ccCc" + "cCcc",
        "GGGG" + "GYGG" + "gGGg" + "gGGg",
    ] + ["." * 16] * 9
    s.wrap("rpants", skirt)
    s.wrap("lpants", mirror_wrap(skirt, 4, 4))
    return s


# ============================================================================ Solar Acolyte
ACOLYTE_PAL = {
    # crimson robe
    "Q": "#C8482A", "R": "#9C2A1A", "r": "#6E1A10", "q": "#46100A",
    # gold trim
    "Y": "#FFD878", "G": "#F0A830", "g": "#B06A18",
    # face in the hood's shadow, glowing eyes, golden veil
    "D": "#220806", "S": "#7E503C", "s": "#553426", "E": "#FFB43C", "e": "#FFF2A8",
    "M": "#EAB444", "m": "#A8741C", "n": "#6E4610",
    # cream stole, sun sigil (glowing), sandals
    "W": "#F2E4C2", "w": "#C8B48C", "O": "#FFC24A", "o": "#FFF0A0", "K": "#4A2A18", "k": "#2A160C",
}


def fabric(w, h, phase=0, darken_from=None):
    """Robe cloth: vertical folds (a crease every third column, lit on its left near the top), darker toward the hem."""
    rows = []
    for y in range(h):
        row = ""
        for x in range(w):
            k = (x + phase) % 3
            level = 2 if k == 0 else (0 if k == 1 and y < h // 2 else 1)
            if darken_from is not None and y >= darken_from:
                level = min(3, level + 1)
            row += "QRrq"[level]
        rows.append(row)
    return rows


def layer(base, detail):
    """Detail rows drawn over base rows; '.' keeps the base, ' ' cuts a transparent hole."""
    return ["".join(d if d != "." else b for b, d in zip(br, dr)) for br, dr in zip(base, detail)]


def wrap_rows(right, front, left, back):
    return [a + b + c + d for a, b, c, d in zip(right, front, left, back)]


def acolyte_skin():
    s = Skin(ACOLYTE_PAL, glow="EeOo")
    # --- head: a face lost in shadow, burning eyes, a golden veil over nose and mouth
    dark = ["DDDDDDDD"] * 8
    s.wrap("head", wrap_rows(dark, [
        "DDDDDDDD",
        "DDDDDDDD",
        "DDssssDD",
        "DssSSssD",
        "DseSSesD",
        "DnmMMmnD",
        "DmMnnMmD",
        "DDmMMmDD",
    ], dark, dark))
    s.fill("head", "top", "D")
    s.fill("head", "bottom", "D")
    # hat layer: the hood, a thin gold trim around the face, a glowing sun sigil on the back
    hood_side = fabric(8, 8, 1, darken_from=6)
    s.wrap("hat", wrap_rows(
        [r[:7] + "g" for r in hood_side],
        layer(fabric(8, 8, 0), [
            "........",
            "gGGYYGGg",
            "g      g",
            "g      g",
            "g      g",
            "g      g",
            "g      g",
            "q      q",
        ]),
        ["g" + r[1:] for r in fabric(8, 8, 2, darken_from=6)],
        layer(fabric(8, 8, 1, darken_from=6), [
            "........",
            "...OO...",
            "..OooO..",
            "..OooO..",
            "...OO...",
            "........",
            "........",
            "........",
        ])))
    s.face("hat", "top", fabric(8, 8, 1))
    # --- body: robe with a glowing sun sigil, gold belt and a cream stole down the front
    s.wrap("body", wrap_rows(
        layer(fabric(4, 12, 2, darken_from=9), ["...."] * 7 + ["gGGG"] + ["...."] * 4),
        layer(fabric(8, 12, 0, darken_from=9), [
            "W.GssG.W",
            "W..OO..W",
            "W.OYYO.W",
            "wOYooYOw",
            "wOYooYOw",
            "W.OYYO.W",
            "W..OO..W",
            "wGGYYGGw",
            "W..GG..W",
            "W..GG..W",
            "w..GG..w",
            "G..GG..G",
        ]),
        layer(fabric(4, 12, 1, darken_from=9), ["...."] * 7 + ["GGGg"] + ["...."] * 4),
        layer(fabric(8, 12, 1, darken_from=9), ["........"] * 7 + ["gGGGGGGg"] + ["........"] * 4)))
    s.face("body", "top", ["WRRRRRRW", "WRrRRrRW", "wRRRRRRw", "wrRRRRrw"])
    s.fill("body", "bottom", "q")
    # the hood's cowl drapes over the shoulders at the back
    s.wrap("jacket", wrap_rows(["...."] * 12, ["........"] * 12, ["...."] * 12,
                               [".rRQRRr.", "..rRRr..", "...rr..."] + ["........"] * 9))
    # --- arms: wide sleeves (flared on the overlay), gold cuffs, dark hands with gold bangles
    cuff = ["...."] * 7 + ["GGGG", "gGGg", "sSSs", "GYGG", "sSSs"]
    cuff_front = ["...."] * 7 + ["GOGG", "gGOg", "sSSs", "GYGG", "sSSs"]
    arm = wrap_rows(layer(fabric(4, 12, 1, 5), cuff), layer(fabric(4, 12, 0, 5), cuff_front),
                    layer(fabric(4, 12, 2, 4), cuff), layer(fabric(4, 12, 1, 4), cuff))
    s.wrap("rarm", arm)
    s.wrap("larm", mirror_wrap(arm, 4, 4))
    for part in ("rarm", "larm"):
        s.fill(part, "top", "R")
        s.fill(part, "bottom", "s")
    flare = ["...."] * 4 + ["RRRR", "RrRr", "rRrq", "gGGg"] + ["...."] * 4
    sleeve = wrap_rows(layer(["...."] * 12, flare), layer(["...."] * 12, flare[:7] + ["GYGG"] + flare[8:]),
                       layer(["...."] * 12, flare), layer(["...."] * 12, flare))
    s.wrap("rsleeve", sleeve)
    s.wrap("lsleeve", mirror_wrap(sleeve, 4, 4))
    # --- legs: the robe falls to the ankles, gold hem stitched with glowing runes, sandals
    hem = ["...."] * 9 + ["GOGO", "KKKK", "kKKk"]
    leg = wrap_rows(layer(fabric(4, 12, 1, 6), hem), layer(fabric(4, 12, 0, 7), ["...."] * 9 + ["OGOG", "KKKK", "KkkK"]),
                    layer(fabric(4, 12, 2, 5), ["...."] * 9 + ["gGgG", "kKKk", "kkkk"]),
                    layer(fabric(4, 12, 1, 5), ["...."] * 9 + ["GgGg", "kKKk", "kkkk"]))
    s.wrap("rleg", leg)
    s.wrap("lleg", mirror_wrap(leg, 4, 4))
    for part in ("rleg", "lleg"):
        s.fill(part, "bottom", "k")
        s.fill(part, "top", "q")
    # the robe's skirt flares out a little around the hem
    skirt = ["." * 16] * 6 + wrap_rows(*[fabric(4, 3, p, 1) for p in (1, 0, 2, 1)]) + ["GOGO" + "OGOG" + "gGgG" + "GgGg"] + ["." * 16] * 2
    s.wrap("rpants", skirt)
    s.wrap("lpants", mirror_wrap(skirt, 4, 4))
    return s


# ============================================================================ Valkyrie wings
WING_W, WING_H = 40, 44     # two 20x44 panels side by side: inner (root) and outer (tip)
WING_SS = 4                 # painted at 4x and reduced


def valkyrie_wings():
    """Right wing, root at the left edge, leading edge at the top. White feathers, gold coverts."""
    n = WING_H * WING_SS    # square working canvas, cropped to the texture width afterwards
    k = WING_SS
    sh = Shapes(n, 2)
    cv = Canvas(n)
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32) + 0.5

    def feather(root, tip, w, body, edge, shaft=True):
        """A rounded-tip feather from root to tip (texel coords) of half-width w."""
        (x0, y0), (x1, y1) = root, tip
        ang = math.atan2(y1 - y0, x1 - x0)
        nx, ny = -math.sin(ang), math.cos(ang)
        pts = [(x0 + nx * w * 0.6, y0 + ny * w * 0.6), (x1 + nx * w, y1 + ny * w),
               (x1 + math.cos(ang) * w, y1 + math.sin(ang) * w),
               (x1 - nx * w, y1 - ny * w), (x0 - nx * w * 0.6, y0 - ny * w * 0.6)]
        m = sh.poly([(x * k, y * k) for x, y in pts])
        cv.over(edge, m)
        ins = 1.0   # outline width in texels, so single feathers stay readable after reduction
        inner = [(x0 + nx * (w - ins) * 0.6, y0 + ny * (w - ins) * 0.6), (x1 + nx * (w - ins), y1 + ny * (w - ins)),
                 (x1 + math.cos(ang) * (w - ins), y1 + math.sin(ang) * (w - ins)),
                 (x1 - nx * (w - ins), y1 - ny * (w - ins)), (x0 - nx * (w - ins) * 0.6, y0 - ny * (w - ins) * 0.6)]
        mi = sh.poly([(x * k, y * k) for x, y in inner])
        px_, py_ = xs / k - x0, ys / k - y0
        length = max(1, math.hypot(x1 - x0, y1 - y0))
        # along: 0 at the root, 1 at the tip; across: -1..1 over the vane (the side away from the light is shaded)
        along = np.clip((px_ * math.cos(ang) + py_ * math.sin(ang)) / length, 0, 1)
        across = np.clip((px_ * nx + py_ * ny) / w, -1, 1)
        cv.over(body[0], mi)
        cv.over(body[1], mi * np.clip(along * 1.2 - 0.1, 0, 1), 0.85)
        cv.over(edge, mi * np.clip(across - 0.35, 0, 1), 0.55)
        if shaft:
            cv.over(edge, sh.line([(x0 * k, y0 * k), ((x0 + (x1 - x0) * 0.8) * k, (y0 + (y1 - y0) * 0.8) * k)], 0.5 * k), 0.45)

    white = ("#FFFFFF", "#DCE4F0")
    shade = ("#EEF2FA", "#C2CCDE")
    edge = "#7C88A6"
    # primaries fan out from the outer panel toward the tip, secondaries hang from the inner panel;
    # drawn outermost first so each feather overlaps the next one out, as seen from behind
    primaries = [((21, 11), (23, 41)), ((24.5, 10), (27.5, 40)), ((28, 9), (32, 38)),
                 ((31.5, 8), (36, 34)), ((35, 7), (38.3, 28)), ((37.5, 6), (38.8, 20))]
    for i, (root, tip) in reversed(list(enumerate(primaries))):
        feather(root, tip, 2.6, white if i % 2 else shade, edge)
    for i in reversed(range(5)):
        x = 2.5 + i * 4.2
        feather((x, 12), (x - 1.0, 35 + i * 0.9), 2.6, shade if i % 2 else white, edge)
    # the leading edge: two rows of coverts, the outer row tipped in gold
    for i in range(9):
        x = 3 + i * 4.2
        feather((x, 4 + i * 0.35), (x - 0.5, 16 + i * 0.2), 2.3, white, edge, shaft=False)
    for i in range(10):
        x = 2 + i * 3.8
        feather((x, 2 + i * 0.3), (x - 0.4, 8.5 + i * 0.2), 2.1, ("#FFE9A6", "#E8B840"), "#96661A", shaft=False)
    # a strip of gold along the wing's arm
    cv.over("#F4CC5C", sh.line([(1 * k, 2.5 * k), (39 * k, 5.5 * k)], 1.4 * k))
    cv.over("#FFF3C8", sh.line([(1 * k, 2.1 * k), (39 * k, 5.1 * k)], 0.5 * k))
    # soft ambient occlusion where the wing meets the back
    root = np.clip(1 - xs / (5 * k), 0, 1) * cv.a
    cv.shade(1 - 0.22 * root)

    small = cv.down(WING_H, Image.BOX)
    img = small.image().crop((0, 0, WING_W, WING_H))
    # cutout: hard alpha, darkest shade on the silhouette
    arr = np.asarray(img).copy()
    solid = arr[..., 3] >= 110
    arr[..., 3] = np.where(solid, 255, 0)
    out = Image.fromarray(arr, "RGBA").copy()
    px = out.load()
    edge_rgba = hexa("#66708C")
    for y in range(WING_H):
        for x in range(WING_W):
            if not solid[y, x]:
                continue
            if any(not (0 <= x + dx < WING_W and 0 <= y + dy < WING_H) or not solid[y + dy, x + dx]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1)) if not (dx == -1 and x == 0)):
                r, g, b, _ = px[x, y]
                px[x, y] = (int(r * 0.55 + edge_rgba[0] * 0.45), int(g * 0.55 + edge_rgba[1] * 0.45), int(b * 0.55 + edge_rgba[2] * 0.45), 255)
    return out


# ============================================================================ Solar Acolyte halo
def solar_halo():
    """A golden sun halo (ring and twelve rays), drawn opaque-ish so it reads against the Aether's bright sky."""
    n, size = 256, 64
    sh = Shapes(n, 2)
    cv = Canvas(n)
    c = n / 2
    rays = []
    for i in range(12):
        a = math.radians(i * 30 + 15)
        r0, r1, w = 56, (122 if i % 2 == 0 else 100), math.radians(7 if i % 2 == 0 else 6)
        rays.append([(c + math.cos(a - w) * r0, c + math.sin(a - w) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1),
                     (c + math.cos(a + w) * r0, c + math.sin(a + w) * r0)])
    ray_mask = sh.polys(rays)
    ring = np.clip(sh.ellipse(c, c, 66) - sh.ellipse(c, c, 50), 0, 1)
    d = np.hypot(*(np.mgrid[0:n, 0:n] - c + 0.5)) / (n / 2)
    cv.over("#D98A1C", ray_mask)
    cv.over("#FFD35A", ray_mask * np.clip(1.25 - d, 0, 1), 0.9)
    cv.over("#9C5A10", ring)
    cv.over("#F4B53A", np.clip(sh.ellipse(c, c, 63) - sh.ellipse(c, c, 53), 0, 1))
    cv.over("#FFF0B0", np.clip(sh.ellipse(c, c, 60) - sh.ellipse(c, c, 57), 0, 1), 0.8)
    small = cv.down(size, Image.BOX)
    img = small.image()
    arr = np.asarray(img).copy()
    # crisp pixel edges, slightly translucent body
    arr[..., 3] = np.where(arr[..., 3] >= 100, 235, 0).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


# ============================================================================ preview renderer
# Boxes of Iron's Spells' casting model in texels: (part, overlay part, origin, size, inflate for overlay)
MODEL = [
    ("head", "hat", (-4, 24, -4), (8, 8, 8)),
    ("body", "jacket", (-4, 12, -2), (8, 12, 4)),
    ("rarm", "rsleeve", (4, 12, -2), (4, 12, 4)),
    ("larm", "lsleeve", (-8, 12, -2), (4, 12, 4)),
    ("rleg", "rpants", (0, 0, -2), (4, 12, 4)),
    ("lleg", "lpants", (-4, 0, -2), (4, 12, 4)),
]


def box_faces(origin, size, inflate=0.0):
    """Faces of a box as (face, corner at texture (0,0), vector along texture x, vector along texture y) in texels.
    The model faces -Z; its right side is +X (the viewer's left when facing it)."""
    x0, y0, z0 = (o - inflate for o in origin)
    w, h, d = (s + 2 * inflate for s in size)
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    return [
        ("front", (x1, y1, z0), (-w, 0, 0), (0, -h, 0)),
        ("back", (x0, y1, z1), (w, 0, 0), (0, -h, 0)),
        ("right", (x1, y1, z1), (0, 0, -d), (0, -h, 0)),
        ("left", (x0, y1, z0), (0, 0, d), (0, -h, 0)),
        ("top", (x1, y1, z1), (-w, 0, 0), (0, 0, -d)),
        ("bottom", (x1, y0, z0), (-w, 0, 0), (0, 0, d)),
    ]


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float64)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], np.float64)


class Raster:
    """Orthographic textured-quad rasteriser with a z-buffer (nearest texel sampling, alpha test)."""

    def __init__(self, size, scale, yaw, pitch, center):
        self.size, self.scale = size, scale
        self.view = rot_x(pitch) @ rot_y(yaw)
        self.center = np.array(center, np.float64)
        self.rgb = np.zeros((size, size, 3), np.float32)
        self.rgb[:] = (0.20, 0.22, 0.27)
        self.z = np.full((size, size), np.inf)

    def project(self, p):
        v = self.view @ (np.asarray(p, np.float64) - self.center)
        # viewer looks along +Z, the model's right (+X) shows on the image's left
        return np.array([self.size / 2 - v[0] * self.scale, self.size / 2 - v[1] * self.scale]), v[2]

    def quad(self, tex, rect, p0, ex, ey, light=1.0, additive=False):
        """Draw texture sub-rect `rect` (x, y, w, h) on the parallelogram p0 + s*ex + t*ey (s, t in 0..1)."""
        a, za = self.project(p0)
        b, zb = self.project(np.add(p0, ex))
        c, zc = self.project(np.add(p0, ey))
        e1, e2 = b - a, c - a
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            return
        corners = np.array([a, b, c, b + e2])
        lo = np.clip(np.floor(corners.min(0)).astype(int), 0, self.size)
        hi = np.clip(np.ceil(corners.max(0)).astype(int), 0, self.size)
        if lo[0] >= hi[0] or lo[1] >= hi[1]:
            return
        gy, gx = np.mgrid[lo[1]:hi[1], lo[0]:hi[0]].astype(np.float64) + 0.5
        dx, dy = gx - a[0], gy - a[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        x, y, w, h = rect
        tx = np.clip((x + s * w).astype(int), x, x + w - 1)
        ty = np.clip((y + t * h).astype(int), y, y + h - 1)
        texel = tex[ty, tx]
        z = za + s * (zb - za) + t * (zc - za)
        zbuf = self.z[lo[1]:hi[1], lo[0]:hi[0]]
        if additive:
            m = inside & (z < zbuf)
            region = self.rgb[lo[1]:hi[1], lo[0]:hi[0]]
            region[m] = np.clip(region[m] + texel[m, :3] * light, 0, 1)
            return
        m = inside & (texel[..., 3] > 0.5) & (z < zbuf)
        zbuf[m] = z[m]
        self.rgb[lo[1]:hi[1], lo[0]:hi[0]][m] = texel[m, :3] * light

    def image(self):
        return Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8), "RGB")


FACE_SHADE = {"top": 1.0, "front": 0.95, "right": 0.8, "left": 0.8, "back": 0.7, "bottom": 0.55}


def render_model(r, skin):
    tex = np.asarray(skin, np.float32) / 255.0
    for base, over, origin, size in MODEL:
        for part, inflate in ((base, 0.0), (over, 0.25)):
            for face, p0, ex, ey in box_faces(origin, size, inflate):
                r.quad(tex, face_rect(part, face), p0, ex, ey, FACE_SHADE[face])


def render_wings(r, wings, spread, phase):
    """Mirrors ValkyrieWingsLayer (positions in blocks there, texels here)."""
    tex = np.asarray(wings, np.float32) / 255.0
    beat = math.sin(phase)
    lerp = lambda t, a, b: a + (b - a) * t
    sweep = lerp(spread, 1.25, 0.25) + beat * lerp(spread, 0.04, 0.28)
    lift = lerp(spread, 0.10, 0.30) + beat * lerp(spread, 0.05, 0.40)
    fold = lerp(spread, 0.35, 0.08) + math.sin(phase - 0.9) * lerp(spread, 0.03, 0.22)
    root = np.array([0.06, 1.36, 0.17]) * 16
    pw, top, bottom = 0.62 * 16, 0.42 * 16, -0.98 * 16
    for side in (-1, 1):
        m1 = rot_y(-side * sweep) @ rot_z(side * lift)
        o1 = root * np.array([side, 1, 1])
        m2 = m1 @ rot_y(-side * fold) @ rot_z(-side * fold * 0.35)
        o2 = o1 + m1 @ np.array([side * pw, 0, 0])
        for (o, m, u0) in ((o1, m1, 0), (o2, m2, WING_W // 2)):
            p0 = o + m @ np.array([0, top, 0])
            ex = m @ np.array([side * pw, 0, 0])
            ey = m @ np.array([0, bottom - top, 0])
            r.quad(tex, (u0, 0, WING_W // 2, WING_H), p0, ex, ey, 0.92)


def render_halo(r, halo, core):
    for img, size, z, additive in ((halo, 1.3, 0.31, False), (core, 0.5, 0.32, True)):
        tex = np.asarray(img, np.float32) / 255.0
        half = size * 16 / 2
        c = np.array([0, 1.78 * 16, z * 16])
        r.quad(tex, (0, 0, img.width, img.height), c + [half, half, 0], (-2 * half, 0, 0), (0, -2 * half, 0), 1.0, additive=additive)


def preview(skins):
    """skins: list of (name, skin image, extra draw fn or None)."""
    views = [("front", math.radians(-30), math.radians(-12)), ("side", math.radians(-90), math.radians(-8)),
             ("back", math.radians(150), math.radians(-15)), ("above", math.radians(200), math.radians(-40))]
    cell = 220
    sheet = Image.new("RGB", (cell * len(views) + 150, cell * len(skins)), (40, 42, 48))
    d = ImageDraw.Draw(sheet)
    for row, (name, skin, extra) in enumerate(skins):
        for col, (label, yaw, pitch) in enumerate(views):
            r = Raster(cell, 6.2, yaw, pitch, (0, 17, 0))
            render_model(r, skin)
            if extra:
                extra(r, col)
            sheet.paste(r.image(), (col * cell, row * cell))
            d.text((col * cell + 4, row * cell + 4), f"{name} / {label}", fill=(230, 230, 230))
        big = skin.resize((128, 128), Image.NEAREST)
        sheet.paste(big, (cell * len(views) + 11, row * cell + 40), big)
    os.makedirs(os.path.dirname(MOB_PREVIEW), exist_ok=True)
    sheet.save(MOB_PREVIEW)


def generate(save):
    valkyrie = valkyrie_skin()
    acolyte = acolyte_skin()
    wings = valkyrie_wings()
    save(valkyrie.img, "entity", "valkyrie_sorceress.png")
    save(acolyte.img, "entity", "solar_acolyte.png")
    save(acolyte.glow, "entity", "solar_acolyte_glowmask.png")
    save(wings, "entity", "valkyrie_wings.png")
    halo = solar_halo()
    save(halo, "entity", "solar_halo.png")

    core = Image.open(os.path.join(ROOT, "entity", "solar_core.png")).convert("RGBA")
    wing_states = [(0.0, 0.0), (0.75, 1.2), (1.0, 2.0), (0.75, 4.4)]
    preview([
        ("valkyrie_sorceress", valkyrie.img, lambda r, i: render_wings(r, wings, *wing_states[i])),
        ("solar_acolyte", acolyte.img, lambda r, i: render_halo(r, halo, core)),
    ])
