"""
Staff models and spellblade sprites.

Staffs are 3D item models (vanilla JSON elements, like the ones Blockbench exports) built from the element lists
below; every face is packed into a 64x64 texture and painted by the face's recipe. Zero-thickness "planes" carry
ornaments (wings, sun rays) and use the same texture on both sides (mirrored on the back so the outline matches).
Spellblades are 16x16 handheld sprites drawn along the diagonal with sub-pixel coverage.
Writes assets/aether_spellbooks/models/item/<staff>.json, textures/item/<name>.png and build/weapon_preview.png.
All art is original.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from gen_textures import BAYER4, PROJECT, ROOT, hexa
from gen_mob_textures import Raster, rot_x, rot_y, rot_z

ASSETS = os.path.dirname(ROOT)
WEAPON_PREVIEW = os.path.join(PROJECT, "build", "weapon_preview.png")
TEX = 64


def ramp(*hexes):
    return [hexa(h) for h in hexes]


def pick(rmp, level, x=0, y=0, dither=False):
    level = max(0.0, min(len(rmp) - 1.0, level))
    if not dither:
        return rmp[int(level + 0.5)]
    base = int(level)
    if base + 1 < len(rmp) and level - base > (BAYER4[y % 4][x % 4] + 0.5) / 16:
        base += 1
    return rmp[base]


# palettes
WOOD = ramp("#4A3418", "#7A5A2E", "#A88450", "#CFB27A", "#EEDCAA")        # skyroot
ZAN = ramp("#2A0F4A", "#4B2476", "#7A3EB4", "#A874E0", "#E4C8FF")          # zanite
GOLD = ramp("#5E3C0C", "#9C6A1C", "#D8A63A", "#F4D273", "#FFF1C2")
SILVER = ramp("#4E566C", "#7E88A0", "#AEB7CA", "#D8DEEA", "#F6F8FD")
CLOUD = ramp("#7C8CA8", "#AEBCD4", "#D8E2F0", "#F2F6FC", "#FFFFFF")
SKY = ramp("#1A3464", "#2A5696", "#4480C8", "#6CAAE8", "#A4D0F8")
GRAV = ramp("#3A1840", "#6F2F73", "#B056B6", "#E28CE6", "#FFD8FF")
IRON = ramp("#1E1C26", "#34323E", "#4E4C5A", "#6E6C7C", "#9492A2")
FLAME = ramp("#7A1E06", "#B83A0C", "#EA6A16", "#FF9E34", "#FFD064", "#FFF2B0")
CRIMSON = ramp("#2E0806", "#5A120C", "#8A2014", "#B8341A", "#DC5424")
GEM_BLUE = ramp("#1C4E9A", "#3A86D8", "#8AD4FF", "#E4F8FF")
AMBER = ramp("#8A4A08", "#E08A18", "#FFC040", "#FFF0A0")
FEATHER = ramp("#6E7A98", "#B8C3D8", "#E4EAF4", "#FFFFFF")

FACE_SHIFT = {"up": 0.4, "north": 0.0, "south": -0.2, "east": -0.3, "west": -0.3, "down": -0.8}


# ============================================================================ staff models
class Element:
    """A box (or a plane when one size is 0) with a painter per face: painter(img, x0, y0, w, h, face)."""

    def __init__(self, frm, to, paint, rotation=None, faces=None):
        self.frm, self.to, self.paint, self.rotation = list(frm), list(to), paint, rotation
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        if faces is None:
            if dz == 0:
                faces = ("north", "south")
            elif dx == 0:
                faces = ("east", "west")
            else:
                faces = ("north", "south", "east", "west", "up", "down")
        self.faces = faces
        self.rects = {}

    def face_size(self, face):
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]

    def is_plane(self):
        return len(self.faces) == 2


def gradient_paint(rmp, top=3.4, bottom=2.0, stripes=None, jitter=0.0):
    """Vertical shading; stripes = {row_from_top: (ramp, level)} bands (e.g. rings, wraps)."""
    def paint(img, x0, y0, w, h, face):
        for y in range(h):
            for x in range(w):
                lv = top + (bottom - top) * (y / max(1, h - 1)) + FACE_SHIFT[face]
                if jitter:
                    lv += jitter * (((x * 7 + y * 13 + x0) % 5) / 4 - 0.5)
                c = pick(rmp, lv)
                if stripes and y in stripes:
                    r2, l2 = stripes[y]
                    c = pick(r2, l2 + FACE_SHIFT[face])
                img.putpixel((x0 + x, y0 + y), c)
    return paint


def flat(rmp, level):
    return gradient_paint(rmp, level, level)


def grain(rmp, level=2.6):
    """Skyroot wood: vertical grain lines and knots."""
    def paint(img, x0, y0, w, h, face):
        for y in range(h):
            for x in range(w):
                lv = level + FACE_SHIFT[face] + (0.6 if (y * 3 + x) % 11 == 0 else 0) - (0.8 if (y * 5 + x * 3) % 13 == 0 else 0)
                img.putpixel((x0 + x, y0 + y), pick(rmp, lv))
    return paint


def gem_paint(rmp):
    """Faceted crystal: bright top-left, dark bottom-right, a highlight pixel."""
    def paint(img, x0, y0, w, h, face):
        for y in range(h):
            for x in range(w):
                t = (x / max(1, w - 1) + y / max(1, h - 1)) / 2
                lv = len(rmp) - 1.1 - t * 2.2 + FACE_SHIFT[face]
                img.putpixel((x0 + x, y0 + y), pick(rmp, lv))
        img.putpixel((x0, y0), rmp[-1])
    return paint


def mask_paint(rows, pal):
    """Pixel map for a plane (size must match); ' '/'.' are transparent."""
    def paint(img, x0, y0, w, h, face):
        for y in range(h):
            row = rows[y] if y < len(rows) else ""
            for x in range(w):
                ch = row[x] if x < len(row) else " "
                if ch in pal:
                    rmp, lv = pal[ch]
                    img.putpixel((x0 + x, y0 + y), pick(rmp, lv, x, y, dither=True))
    return paint


def cloud_paint(img, x0, y0, w, h, face):
    for y in range(h):
        for x in range(w):
            lv = 3.6 - 1.4 * (y / max(1, h - 1)) + FACE_SHIFT[face] - (0.7 if (x + 2 * y) % 5 == 0 else 0)
            img.putpixel((x0 + x, y0 + y), pick(CLOUD, lv))


def rot(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": origin}


def zanite_staff():
    prong = grain(WOOD, 2.2)
    return [
        Element((7.5, -15, 7.5), (8.5, 17, 8.5), grain(WOOD, 2.6)),
        Element((7, -16, 7), (9, -14, 9), gem_paint(ZAN)),
        Element((7, -5, 7), (9, 1, 9), gradient_paint(SKY, 3.0, 2.0, {0: (GOLD, 3.0), 5: (GOLD, 2.6)})),
        Element((7, 15, 7), (9, 17, 9), flat(GOLD, 2.8)),
        Element((6.2, 16.5, 7.5), (7.2, 20.5, 8.5), prong, rot("z", 22.5, [7, 16.5, 8])),
        Element((8.8, 16.5, 7.5), (9.8, 20.5, 8.5), prong, rot("z", -22.5, [9, 16.5, 8])),
        Element((7.5, 16.5, 6.2), (8.5, 20.5, 7.2), prong, rot("x", -22.5, [8, 16.5, 7])),
        Element((7.5, 16.5, 8.8), (8.5, 20.5, 9.8), prong, rot("x", 22.5, [8, 16.5, 9])),
        Element((6.8, 18, 6.8), (9.2, 23, 9.2), gem_paint(ZAN), rot("y", 45, [8, 20, 8])),
        Element((7.4, 23, 7.4), (8.6, 25, 8.6), gem_paint(ZAN), rot("y", 45, [8, 24, 8])),
    ]


def aercloud_staff():
    return [
        Element((7.5, -14, 7.5), (8.5, 17, 8.5), gradient_paint(CLOUD, 3.4, 2.4, {r: (SKY, 2.6) for r in range(0, 31, 4)})),
        Element((7, -15, 7), (9, -13, 9), flat(GOLD, 2.8)),
        Element((7, 0, 7), (9, 1, 9), flat(GOLD, 2.8)),
        Element((7, 14, 7), (9, 16, 9), flat(GOLD, 3.0)),
        Element((5.5, 16.5, 5.5), (10.5, 20.5, 10.5), cloud_paint),
        Element((3.5, 17.5, 6.5), (6, 20, 9.5), cloud_paint),
        Element((10, 18, 6.5), (12.5, 21, 9.5), cloud_paint),
        Element((6.5, 20, 6.5), (10, 23, 9.5), cloud_paint),
        Element((4.5, 19.5, 7), (7, 21.5, 9), cloud_paint),
        Element((7.3, 23, 7.3), (8.7, 24.5, 8.7), gem_paint(GOLD), rot("y", 45, [8, 23.75, 8])),
    ]


def gravitite_staff():
    frame = gradient_paint(IRON, 3.2, 2.0)
    return [
        Element((7.5, -15, 7.5), (8.5, 16, 8.5), gradient_paint(IRON, 3.0, 1.8, {r: (GRAV, 2.4) for r in (6, 7, 20, 21)})),
        Element((7, -16, 7), (9, -14, 9), gem_paint(GRAV)),
        Element((7, -4, 7), (9, 1, 9), gradient_paint(IRON, 2.2, 1.4, {0: (GRAV, 3.0), 4: (GRAV, 3.0)})),
        Element((6.5, 15.5, 7.5), (9.5, 16.5, 8.5), frame),
        Element((5.5, 16, 7.5), (6.5, 23, 8.5), frame),
        Element((9.5, 16, 7.5), (10.5, 23, 8.5), frame),
        Element((5.5, 23, 7.5), (10.5, 24, 8.5), frame),
        Element((7.5, 24, 7.5), (8.5, 26, 8.5), gem_paint(GRAV)),
        Element((6.8, 18, 6.8), (9.2, 21.5, 9.2), gem_paint(GRAV), rot("y", 45, [8, 19.75, 8])),
    ]


WING_ROWS = [
    "  GG    ",
    " GYYG   ",
    "GYWWWG  ",
    "GWWWWWG ",
    "WWWWWWWG",
    "fWWfWWWW",
    " fWWfWWW",
    "  fWWfWW",
    "   fWWfW",
    "    ffff",
]


def valkyrie_scepter():
    pal = {"G": (GOLD, 2.8), "Y": (GOLD, 3.8), "W": (FEATHER, 2.7), "f": (FEATHER, 1.4)}
    right_wing = mask_paint(WING_ROWS, pal)
    left_wing = mask_paint([r[::-1] for r in WING_ROWS], pal)
    return [
        Element((7.5, -13, 7.5), (8.5, 17, 8.5), gradient_paint(SILVER, 3.4, 2.2, {0: (GOLD, 3.0), 8: (GOLD, 2.8), 18: (GOLD, 2.8), 29: (GOLD, 2.6)})),
        Element((7, -14, 7), (9, -12, 9), flat(GOLD, 2.8)),
        Element((7, 15, 7), (9, 17, 9), flat(GOLD, 3.0)),
        Element((6.8, 17, 6.8), (9.2, 20.5, 9.2), gem_paint(GEM_BLUE), rot("y", 45, [8, 18.75, 8])),
        Element((7.5, 20.5, 7.5), (8.5, 24, 8.5), gradient_paint(GOLD, 3.8, 2.6)),
        Element((0, 14, 8), (8, 24, 8), left_wing, rot("y", -22.5, [8, 19, 8])),
        Element((8, 14, 8), (16, 24, 8), right_wing, rot("y", 22.5, [8, 19, 8])),
    ]


def sun_rows(n=10):
    """A sun disc with twelve rays, as a pixel map."""
    rows = []
    c = (n - 1) / 2
    # rays: every 30 degrees, alternating long and short
    for y in range(n):
        row = ""
        for x in range(n):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 360) % 30
            k = int(((math.degrees(math.atan2(dy, dx)) + 360) % 360) // 30)
            ray = abs(a - 15) > 9 and r < (n / 2 if k % 2 == 0 else n * 0.4)
            row += "O" if r < n * 0.17 else "S" if r < n * 0.3 else "R" if ray else " "
        rows.append(row)
    return rows


def solar_scepter():
    pal = {"O": (AMBER, 3.0), "S": (FLAME, 4.2), "R": (FLAME, 3.0)}
    sun = mask_paint(sun_rows(14), pal)
    return [
        Element((7.5, -15, 7.5), (8.5, 16, 8.5), gradient_paint(GOLD, 3.4, 2.0, {r: (CRIMSON, 2.6) for r in range(12, 18)})),
        Element((7, -16, 7), (9, -14, 9), gem_paint(ramp("#5A0A06", "#A81810", "#F04A24", "#FFA070"))),
        Element((7, 14, 7), (9, 16, 9), flat(GOLD, 3.0)),
        Element((1, 13, 8), (15, 27, 8), sun),
        Element((8, 13, 1), (8, 27, 15), sun),
        Element((6.8, 18, 6.8), (9.2, 22, 9.2), gem_paint(AMBER), rot("y", 45, [8, 20, 8])),
    ]


STAFFS = {
    "zanite_staff": zanite_staff,
    "aercloud_staff": aercloud_staff,
    "gravitite_staff": gravitite_staff,
    "valkyrie_scepter": valkyrie_scepter,
    "solar_scepter": solar_scepter,
}

DISPLAY = {
    "thirdperson_righthand": {"translation": [0, 3, 1.25]},
    "thirdperson_lefthand": {"translation": [0, 3, 1.25]},
    "firstperson_righthand": {"translation": [1.5, -1.5, -2.75], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"translation": [1.5, -1.5, -2.75], "scale": [0.8, 0.8, 0.8]},
    "ground": {"translation": [0, 9.5, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [-15, 125, 60], "translation": [0, -0.4, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"translation": [0, 1.75, -0.75], "scale": [0.55, 0.55, 0.55]},
    "head": {"translation": [0, 14, -8]},
}


def build_staff(name, elements):
    """Packs every face, paints the texture and returns (model json, texture)."""
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    x = y = row_h = 0
    rects = []
    for el in elements:
        for face in (el.faces[:1] if el.is_plane() else el.faces):
            w, h = el.face_size(face)
            rects.append((el, face, max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6)), w, h))
    for el, face, pw, ph, w, h in sorted(rects, key=lambda r: -r[3]):
        if x + pw > TEX:
            x, y, row_h = 0, y + row_h, 0
        el.rects[face] = (x, y, w, h)
        el.paint(img, x, y, pw, ph, face)
        x += pw
        row_h = max(row_h, ph)
    assert y + row_h <= TEX, f"{name}: texture overflow"

    s = 16 / TEX
    out = []
    for el in elements:
        faces = {}
        for face in el.faces:
            src = el.faces[0] if el.is_plane() else face
            u, v, w, h = el.rects[src]
            uv = [u * s, v * s, (u + w) * s, (v + h) * s]
            if el.is_plane() and face != src:
                uv = [uv[2], uv[1], uv[0], uv[3]]     # back of a plane: mirrored so the outline matches
            faces[face] = {"uv": [round(c, 4) for c in uv], "texture": "#0"}
        entry = {"from": el.frm, "to": el.to, "faces": faces}
        if el.rotation:
            entry["rotation"] = el.rotation
        out.append(entry)
    tex = f"aether_spellbooks:item/{name}"
    model = {"credit": "Generated by tools/gen_weapon_models.py", "texture_size": [TEX, TEX], "gui_light": "front",
             "textures": {"0": tex, "particle": tex}, "elements": out, "display": DISPLAY}
    return model, img


# ============================================================================ spellblade sprites
def blade_sprite(spec):
    """
    Diagonal 16x16 sword from the pommel (bottom-left) to the tip (top-right).
    spec: blade/guard/grip/pommel (ramp, level) entries, blade length and width profile, extras.
    """
    size = 16
    p0 = np.array([1.6, 14.4])
    d = np.array([1, -1]) / math.sqrt(2)          # along the sword
    n = np.array([1, 1]) / math.sqrt(2)           # across it (toward the bottom-right edge)
    length = spec.get("length", 17.2)
    grid = {}
    sub = [(i + 0.5) / 4 for i in range(4)]
    for py in range(size):
        for px in range(size):
            hits = {}
            for sy in sub:
                for sx in sub:
                    q = np.array([px + sx, py + sy]) - p0
                    a, b = float(q @ d), float(q @ n)
                    part = classify(spec, a, b, length)
                    if part:
                        hits.setdefault(part[0], []).append(part)
            if not hits:
                continue
            # the part covering most of the pixel wins; fill when at least 7 of 16 samples hit
            best = max(hits.values(), key=len)
            if sum(len(v) for v in hits.values()) >= 7:
                grid[(px, py)] = best[len(best) // 2]
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for (px, py), (part, a, b, t) in grid.items():
        img.putpixel((px, py), colour(spec, part, a, b, t, px, py))
    # outline: a one-pixel ring around the silhouette in the darkest shade of the part it borders
    out = img.copy()
    for py in range(size):
        for px in range(size):
            if (px, py) in grid:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nb = grid.get((px + dx, py + dy))
                if nb:
                    out.putpixel((px, py), spec[nb[0]][0][0])
                    break
    return out


def classify(spec, a, b, length):
    grip0, grip1 = 1.4, spec.get("grip_len", 4.4)
    guard1 = grip1 + spec.get("guard_depth", 1.1)
    if a < -0.2:
        return None
    if a < grip0:
        return ("pommel", a, b, 0) if abs(b) < spec.get("pommel_w", 1.25) else None
    if a < grip1:
        return ("grip", a, b, 0) if abs(b) < 0.72 else None
    if a < guard1:
        return ("guard", a, b, 0) if abs(b) < spec.get("guard_w", 3.4) else None
    if a > length:
        return None
    t = (a - guard1) / (length - guard1)
    w = spec["width"](t)
    b_c = b - spec.get("curve", 0) * t * t
    if abs(b_c) < w:
        return ("blade", a, b_c, t)
    return None


def colour(spec, part, a, b, t, px, py):
    rmp, lv = spec[part]
    if part == "blade":
        rmp_t = spec.get("blade_tip")
        if rmp_t and t > spec.get("tip_from", 0.6):
            rmp = rmp_t
        edge = spec.get("fuller")
        if edge and abs(b) < 0.45 and 0.08 < t < 0.85:
            rmp, lv = edge
        return pick(rmp, lv + (0.9 if b < 0 else -0.5), px, py)
    if part == "guard":
        if spec.get("guard_gem") and abs(b) < 0.8:
            g, gl = spec["guard_gem"]
            return pick(g, gl)
        return pick(rmp, lv + (0.6 if b < 0 else -0.4))
    if part == "grip":
        return pick(rmp, lv + (0.5 if (int(a * 2) % 2) else -0.4))
    return pick(rmp, lv + (0.6 if b < 0 else -0.3))


RUBY = ramp("#5A0A06", "#A81810", "#F04A24", "#FFA070")
BLOOD = ramp("#2A0406", "#5E0A12", "#9A1420", "#D0283A", "#FF6A7A")
STORM = ramp("#2C4A7A", "#6A94C8", "#A8CCF0", "#DCEEFF", "#FFFFFF")
BOLT = ramp("#8A6A10", "#E8C030", "#FFF080", "#FFFFE0")

SPELLBLADES = {
    "sunfire_spellblade": {
        "blade": (FLAME, 3.4), "blade_tip": FLAME, "tip_from": 0.7, "fuller": (GOLD, 3.2),
        "guard": (GOLD, 2.8), "guard_gem": (RUBY, 2.4), "grip": (CRIMSON, 2.2), "pommel": (RUBY, 2.4),
        "guard_w": 3.8, "width": lambda t: (1.75 + 0.45 * math.sin(t * 15)) * (1 - t) ** 0.55 + 0.3},
    "stormcaller_spellblade": {
        "blade": (STORM, 2.8), "fuller": (BOLT, 2.4), "guard": (GOLD, 2.6), "guard_gem": (GEM_BLUE, 2.4), "guard_w": 3.0,
        "grip": (SKY, 1.4), "pommel": (GOLD, 2.8),
        "width": lambda t: (1.6 + (0.55 if int(t * 6) % 2 else -0.1)) * (1 - t) ** 0.5 + 0.25},
    "hallowed_spellblade": {
        "blade": (SILVER, 3.2), "fuller": (GOLD, 3.4), "guard": (GOLD, 3.0), "guard_gem": (GEM_BLUE, 2.8), "guard_w": 4.4,
        "guard_depth": 1.4, "grip": (CLOUD, 2.4), "pommel": (GEM_BLUE, 2.4), "length": 17.6,
        "width": lambda t: 2.15 * (1 - t) ** 0.4 + 0.25},
    "sanguine_spellblade": {
        "blade": (BLOOD, 3.0), "blade_tip": BLOOD, "fuller": (IRON, 1.6), "guard": (IRON, 2.6), "guard_gem": (RUBY, 2.6),
        "grip": (IRON, 1.4), "pommel": (RUBY, 2.4), "curve": 1.3, "guard_w": 3.6,
        "width": lambda t: 1.7 * (1 - t) ** 0.55 + 0.3},
}


# ============================================================================ preview
def element_quads(el):
    """(p0, ex, ey, face) for each face, in model units (y up), before the element rotation."""
    (x1, y1, z1), (x2, y2, z2) = el.frm, el.to
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    table = {
        "north": ((x2, y2, z1), (-dx, 0, 0), (0, -dy, 0)),
        "south": ((x1, y2, z2), (dx, 0, 0), (0, -dy, 0)),
        "east": ((x2, y2, z2), (0, 0, -dz), (0, -dy, 0)),
        "west": ((x1, y2, z1), (0, 0, dz), (0, -dy, 0)),
        "up": ((x1, y2, z1), (dx, 0, 0), (0, 0, dz)),
        "down": ((x1, y1, z2), (dx, 0, 0), (0, 0, -dz)),
    }
    return [(face,) + table[face] for face in el.faces]


def render_staff(r, elements, tex_img):
    tex = np.asarray(tex_img, np.float32) / 255.0
    for el in elements:
        m = np.eye(3)
        o = np.zeros(3)
        if el.rotation:
            ang = math.radians(el.rotation["angle"])
            m = {"x": rot_x, "y": rot_y, "z": rot_z}[el.rotation["axis"]](ang)
            o = np.array(el.rotation["origin"], np.float64)
        for face, p0, ex, ey in element_quads(el):
            src = el.faces[0] if el.is_plane() else face
            u, v, w, h = el.rects[src]
            p = m @ (np.array(p0, np.float64) - o) + o
            vx, vy = m @ np.array(ex, np.float64), m @ np.array(ey, np.float64)
            rect = (int(u), int(v), max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6)))
            if el.is_plane() and face != src:
                # mirrored back face: start from the other edge
                p, vx = p + vx, -vx
            r.quad(tex, rect, p, vx, vy, {"up": 1.0, "north": 0.95, "south": 0.85, "east": 0.8, "west": 0.8, "down": 0.6}[face])


def preview(staffs, blades):
    cell = 200
    sheet = Image.new("RGB", (cell * len(staffs), cell * 2 + 110), (40, 42, 48))
    d = ImageDraw.Draw(sheet)
    for i, (name, (elements, tex)) in enumerate(staffs.items()):
        for row, (yaw, pitch) in enumerate(((math.radians(-35), math.radians(-12)), (math.radians(60), math.radians(-25)))):
            r = Raster(cell, 4.4, yaw, pitch, (8, 5, 8))
            render_staff(r, elements, tex)
            sheet.paste(r.image(), (i * cell, row * cell))
        d.text((i * cell + 4, 4), name, fill=(230, 230, 230))
    for i, (name, img) in enumerate(blades.items()):
        big = img.resize((96, 96), Image.NEAREST)
        sheet.paste(big, (i * 110 + 10, cell * 2 + 8), big)
    os.makedirs(os.path.dirname(WEAPON_PREVIEW), exist_ok=True)
    sheet.save(WEAPON_PREVIEW)


def generate(save):
    staffs = {}
    for name, fn in STAFFS.items():
        elements = fn()
        model, tex = build_staff(name, elements)
        save(tex, "item", name + ".png")
        path = os.path.join(ASSETS, "models", "item", name + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(model, f, indent=1)
            f.write("\n")
        staffs[name] = (elements, tex)
    blades = {}
    for name, spec in SPELLBLADES.items():
        img = blade_sprite(spec)
        save(img, "item", name + ".png")
        blades[name] = img
    preview(staffs, blades)
