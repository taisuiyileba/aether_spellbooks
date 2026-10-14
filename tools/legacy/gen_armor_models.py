"""
3D armour models of the Valkyrie and Phoenix mage sets: GeckoLib geo + idle animation + texture, all generated.

Each set is described below as bones and cubes (Bedrock coordinates: units are pixels, +Y up, the model faces -Z,
its right side is -X). The script packs the cubes' box-UV nets into a 128x128 atlas, paints every face from the
recipes given with the cube, and writes
    assets/aether_spellbooks/geo/<set>_armor.geo.json
    assets/aether_spellbooks/animations/<set>_armor.animation.json
    assets/aether_spellbooks/textures/models/armor/<set>.png  (+ <set>_cape.png)
and item icons, plus build/armor_preview.png (software render on a mannequin) for checking the look.

Bone names follow GeckoLib's armour renderer (armorHead, armorBody, armorRightArm, ... armorRightBoot) and
Iron's Spells' GenericCustomArmorRenderer extras (armorLeggingTorsoLayer follows the body for the leggings,
armorTorsoExtensionRight/LeftLeg follow the legs for the chestplate's hip tassets). The chestplate stops at the
waist and the leggings' sash sits over its hem, so the leggings stay visible between the sash and the boots. All geometry and art is original.
"""
import json
import math
import os
import re

import numpy as np
from PIL import Image, ImageDraw

from gen_textures import BAYER4, PROJECT, ROOT, hexa
from gen_mob_textures import MODEL, Raster, box_faces, face_rect, rot_x, rot_y, rot_z

ASSETS = os.path.dirname(ROOT)
ARMOR_PREVIEW = os.path.join(PROJECT, "build", "armor_preview.png")
ATLAS = 128

ALL = ("top", "bottom", "right", "front", "left", "back")
SIDES = ("right", "front", "left", "back")
FACE_SHIFT = {"top": 0.45, "front": 0.0, "right": -0.3, "left": -0.3, "back": -0.5, "bottom": -0.9}


def ramp(*hexes):
    return [hexa(h) for h in hexes]


def pick(rmp, level, x, y, dither=True):
    """Colour from a shade ramp for a continuous level; ordered dithering between neighbours, or plain rounding."""
    level = max(0.0, min(len(rmp) - 1.0, level))
    if not dither:
        return rmp[int(level + 0.5)]
    base = int(level)
    if base + 1 < len(rmp) and level - base > (BAYER4[y % 4][x % 4] + 0.5) / 16:
        base += 1
    return rmp[base]


def noise(x, y, seed):
    return ((x * 73856093 ^ y * 19349663 ^ seed * 83492791) & 1023) / 1023.0 - 0.5


# ============================================================================ nets and painters
class Net:
    """The box-UV net of one cube as a small image of its own."""

    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.img = Image.new("RGBA", (2 * (w + d), d + h), (0, 0, 0, 0))
        self.px = self.img.load()

    def rect(self, face):
        w, h, d = self.w, self.h, self.d
        return {"top": (d, 0, w, d), "bottom": (d + w, 0, w, d), "right": (0, d, d, h),
                "front": (d, d, w, h), "left": (d + w, d, d, h), "back": (2 * d + w, d, w, h)}[face]

    def each(self, faces):
        for f in faces:
            x0, y0, fw, fh = self.rect(f)
            for y in range(fh):
                for x in range(fw):
                    yield f, x, y, fw, fh, x0 + x, y0 + y

    def mirrored(self):
        """Net of the mirrored cube: the two sides swap and every face flips horizontally."""
        out = Net(self.w, self.h, self.d)
        swap = {"right": "left", "left": "right"}
        for f in ALL:
            sx0, sy0, fw, fh = self.rect(swap.get(f, f))
            dx0, dy0, _, _ = out.rect(f)
            for y in range(fh):
                for x in range(fw):
                    out.px[dx0 + x, dy0 + y] = self.px[sx0 + fw - 1 - x, sy0 + y]
        return out


def shade(rmp, level=2.6, grad=0.8, jitter=0.15, faces=ALL, seed=0):
    """Base fill: lighter toward the top of each side face, lit from the front-top, a little texture noise."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            g = grad * (0.5 - (y + 0.5) / fh) if f in SIDES else 0
            # plates and cloth get clean pixel-art shade bands; dithering is kept for flame gradients
            net.px[X, Y] = pick(rmp, level + g + jitter * noise(X, Y, seed) + FACE_SHIFT[f], X, Y, dither=False)
    return paint


def band(rmp, ys, level=2.8, faces=SIDES, highlight=True):
    """Horizontal trim rows (negative = from the bottom); the top row of the band catches the light."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            rows = [r % fh for r in ys]
            if y in rows:
                lift = 0.8 if highlight and (y - 1) % fh not in rows and y == min(rows) else 0
                net.px[X, Y] = pick(rmp, level + lift + FACE_SHIFT[f], X, Y)
    return paint


def columns(rmp, xs, level=2.8, faces=SIDES, rows=None):
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            if x in [c % fw for c in xs] and (rows is None or y in rows):
                net.px[X, Y] = pick(rmp, level + FACE_SHIFT[f], X, Y)
    return paint


def edges(rmp, level=2.6, faces=ALL):
    """Outline every edge of the listed faces (plate rims)."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            if x in (0, fw - 1) or y in (0, fh - 1):
                net.px[X, Y] = pick(rmp, level + FACE_SHIFT[f], X, Y)
    return paint


def stamp(face, rows, pal, ox=0, oy=0):
    """Pixel map on one face. pal: char -> (ramp, level); ' ' cuts a hole; '.' keeps what is there."""
    def paint(net):
        x0, y0, fw, fh = net.rect(face)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                X, Y = x + ox, y + oy
                if ch == "." or not (0 <= X < fw and 0 <= Y < fh):
                    continue
                if ch == " ":
                    net.px[x0 + X, y0 + Y] = (0, 0, 0, 0)
                else:
                    rmp, level = pal[ch]
                    net.px[x0 + X, y0 + Y] = pick(rmp, level + FACE_SHIFT[face], x0 + X, y0 + Y)
    return paint


def cut(faces, test):
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            if test(x, y, fw, fh):
                net.px[X, Y] = (0, 0, 0, 0)
    return paint


def zigzag(faces=SIDES, depth=1):
    """Feathered lower edge: every other pixel of the bottom row(s) is cut away."""
    return cut(faces, lambda x, y, fw, fh: y >= fh - depth and (x + y) % 2 == 1)


def feather_stripes(rmp, level=2.6, faces=SIDES, width=2, tip=None):
    """Vertical feathers: a darker line between feathers, the rows near the tip take the tip ramp."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            lv = level + 0.7 * (0.5 - y / max(1, fh - 1)) + FACE_SHIFT[f]
            if x % width == width - 1:
                lv -= 0.9
            r = rmp
            if tip and y >= fh - tip[1]:
                r, lv = tip[0], tip[2] + FACE_SHIFT[f] - (0.8 if x % width == width - 1 else 0)
            net.px[X, Y] = pick(r, lv, X, Y)
    return paint


def gradient(stops, faces=SIDES, along="y", jitter=0.25, seed=0):
    """Vertical (or along-z for side faces) colour gradient across several ramps: stops = [(ramp, level), ...]."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            if along == "y":
                t = y / max(1, fh - 1)
            else:   # along the cube's depth: side faces run back->front (right) or front->back (left)
                t = x / max(1, fw - 1) if f != "right" else 1 - x / max(1, fw - 1)
            t = min(0.999, max(0.0, t + jitter * noise(X, Y, seed) * 0.3))
            k = t * (len(stops) - 1)
            i = int(k)
            rmp, lv = stops[i]
            net.px[X, Y] = pick(rmp, lv + (k - i) * 0.6 + FACE_SHIFT[f], X, Y)
    return paint


def scales(rmp, level=2.4, faces=SIDES):
    """Overlapping scallops like feathers on a bird's back (4x3 tile, staggered every other band)."""
    tile = ["1221", "2332", "2222"]

    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            band_i = y // 3
            c = tile[y % 3][(x + 2 * (band_i % 2)) % 4]
            net.px[X, Y] = pick(rmp, level + (int(c) - 2) * 0.8 + FACE_SHIFT[f], X, Y)
    return paint


def flame_tongues(rmp, faces=SIDES, max_height=3, seed=0):
    """Flames licking up from the bottom edge: bright at the base, cut into tongues at the top."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            hgt = 1 + int((math.sin(x * 1.7 + seed) * 0.5 + 0.5) * (max_height - 1) + 0.5)
            from_bottom = fh - 1 - y
            if from_bottom < hgt:
                net.px[X, Y] = pick(rmp, len(rmp) - 1.2 - from_bottom * 1.1 + FACE_SHIFT[f], X, Y)
    return paint


def flames_up(rmp, base, max_height=3, faces=SIDES, seed=0.0, below=None):
    """Flames rising from row `base` (tongue heights vary per column); rows under the base take `below` (ramp, level)."""
    def paint(net):
        for f, x, y, fw, fh, X, Y in net.each(faces):
            hgt = 1 + int((math.sin(x * 2.1 + seed) * 0.5 + 0.5) * (max_height - 1) + 0.5)
            if y > base and below:
                net.px[X, Y] = pick(below[0], below[1] + FACE_SHIFT[f], X, Y)
            elif base - hgt < y <= base:
                net.px[X, Y] = pick(rmp, len(rmp) - 1.4 - (base - y) * 1.2 + FACE_SHIFT[f], X, Y)
    return paint


# ============================================================================ model description
class Cube:
    def __init__(self, origin, size, inflate=0.0, paint=(), mirror=False):
        self.origin, self.size, self.inflate, self.paint, self.mirror = list(origin), list(size), inflate, list(paint), mirror
        self.uv = None
        self.net = None

    def mirrored(self):
        (x, y, z), (w, h, d) = self.origin, self.size
        return Cube((-(x + w), y, z), (w, h, d), self.inflate, self.paint, not self.mirror)

    def build_net(self):
        net = Net(*self.size)
        for p in self.paint:
            p(net)
        self.net = net.mirrored() if self.mirror else net


class Bone:
    def __init__(self, name, parent, pivot, rotation=(0, 0, 0), cubes=(), anim=None):
        self.name, self.parent, self.pivot, self.rotation = name, parent, list(pivot), list(rotation)
        self.cubes, self.anim = list(cubes), anim

    def mirrored(self):
        name = self.name.replace("Right", "Left")
        parent = self.parent.replace("Right", "Left") if self.parent else None
        rx, ry, rz = self.rotation
        anim = None
        if self.anim:
            anim = {"length": self.anim["length"], "keys": [(t, (ax, -ay, -az)) for t, (ax, ay, az) in self.anim["keys"]]}
        return Bone(name, parent, (-self.pivot[0], self.pivot[1], self.pivot[2]), (rx, -ry, -rz), [c.mirrored() for c in self.cubes], anim)


def sway(axis, amplitude, length=3.0, phase=0.0, steps=8):
    """Idle keyframes: a sine oscillation of one rotation axis (0=x, 1=y, 2=z) in degrees."""
    keys = []
    for i in range(steps + 1):
        t = length * i / steps
        v = [0.0, 0.0, 0.0]
        v[axis] = round(amplitude * math.sin(2 * math.pi * i / steps + phase), 3)
        keys.append((round(t, 3), tuple(v)))
    return {"length": length, "keys": keys}


def skeleton():
    """The armour bones every set has (GeckoLib and Iron's Spells positions and names)."""
    return [
        Bone("bipedHead", None, (0, 24, 0)), Bone("armorHead", "bipedHead", (0, 24, 0)),
        Bone("bipedBody", None, (0, 24, 0)), Bone("armorBody", "bipedBody", (0, 24, 0)),
        Bone("armorLeggingTorsoLayer", "bipedBody", (0, 24, 0)),
        Bone("bipedRightArm", None, (-5, 22, 0)), Bone("armorRightArm", "bipedRightArm", (-5, 22, 0)),
        Bone("bipedLeftArm", None, (5, 22, 0)), Bone("armorLeftArm", "bipedLeftArm", (5, 22, 0)),
        Bone("bipedRightLeg", None, (-2, 12, 0)), Bone("armorRightLeg", "bipedRightLeg", (-2, 12, 0)),
        Bone("armorRightBoot", "bipedRightLeg", (-2, 12, 0)),
        Bone("bipedLeftLeg", None, (2, 12, 0)), Bone("armorLeftLeg", "bipedLeftLeg", (2, 12, 0)),
        Bone("armorLeftBoot", "bipedLeftLeg", (2, 12, 0)),
        Bone("armorTorsoExtensionRightLeg", None, (-2, 12, 0)), Bone("armorTorsoExtensionLeftLeg", None, (2, 12, 0)),
    ]


class ArmorModel:
    def __init__(self, name):
        self.name = name
        self.bones = skeleton()

    def bone(self, name):
        return next(b for b in self.bones if b.name == name)

    def add(self, bone_name, *cubes, mirror=False):
        """Cubes for a bone; with mirror=True the same cubes (mirrored) also go on the Left twin bone."""
        self.bone(bone_name).cubes.extend(cubes)
        if mirror:
            self.bone(bone_name.replace("Right", "Left")).cubes.extend(c.mirrored() for c in cubes)

    def add_bone(self, bone, mirror=False):
        self.bones.append(bone)
        if mirror:
            self.bones.append(bone.mirrored())

    def cubes(self):
        return [c for b in self.bones for c in b.cubes]

    # ---------------------------------------------------------------- output
    def pack(self):
        cubes = sorted(self.cubes(), key=lambda c: -(c.size[2] + c.size[1]))
        x = y = row_h = 0
        for c in cubes:
            c.build_net()
            w, h = c.net.img.size
            if x + w > ATLAS:
                x, y, row_h = 0, y + row_h, 0
            c.uv = (x, y)
            x += w
            row_h = max(row_h, h)
        assert y + row_h <= ATLAS, f"{self.name}: atlas overflow ({y + row_h}px)"
        atlas = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
        for c in cubes:
            atlas.alpha_composite(c.net.img, c.uv)
        return atlas

    def geo(self):
        bones = []
        for b in self.bones:
            entry = {"name": b.name, "pivot": b.pivot}
            if b.parent:
                entry["parent"] = b.parent
            if any(b.rotation):
                entry["rotation"] = b.rotation
            if b.cubes:
                entry["cubes"] = []
                for c in b.cubes:
                    cube = {"origin": [round(v, 3) for v in c.origin], "size": c.size, "uv": list(c.uv)}
                    if c.inflate:
                        cube["inflate"] = c.inflate
                    entry["cubes"].append(cube)
            bones.append(entry)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}_armor", "texture_width": ATLAS, "texture_height": ATLAS,
                            "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 1.75, 0]},
            "bones": bones}]}

    def animation(self):
        bones = {}
        length = 1.0
        for b in self.bones:
            if b.anim:
                length = max(length, b.anim["length"])
                bones[b.name] = {"rotation": {f"{t}": list(v) for t, v in b.anim["keys"]}}
        return {"format_version": "1.8.0", "animations": {"idle": {"loop": True, "animation_length": length, "bones": bones}},
                "geckolib_format_version": 2}


# ============================================================================ Valkyrie Mage
V = dict(
    S=ramp("#4E566C", "#7E88A0", "#AEB7CA", "#D8DEEA", "#F6F8FD"),     # silver
    G=ramp("#5E3C0C", "#9C6A1C", "#D8A63A", "#F4D273", "#FFF1C2"),     # gold
    B=ramp("#1A3464", "#2A5696", "#4480C8", "#6CAAE8", "#A4D0F8"),     # sky-blue cloth
    W=ramp("#7E879C", "#B6BFD2", "#DEE4EE", "#F6F8FD"),                # white cloth
    F=ramp("#6E7A98", "#B8C3D8", "#E4EAF4", "#FFFFFF"),                # feathers
    P=ramp("#1C4E9A", "#3A86D8", "#8AD4FF", "#E4F8FF"),                # gem
)
VP = {"A": (V["S"], 3.6), "a": (V["S"], 2.8), "1": (V["S"], 2.0), "0": (V["S"], 1.2),
      "Y": (V["G"], 3.6), "G": (V["G"], 2.7), "g": (V["G"], 1.7),
      "B": (V["B"], 2.9), "b": (V["B"], 2.0), "W": (V["W"], 2.8), "w": (V["W"], 1.8), "P": (V["P"], 2.4), "p": (V["P"], 1.4)}


def valkyrie_mage():
    S, G, B, W, F, P = V["S"], V["G"], V["B"], V["W"], V["F"], V["P"]
    m = ArmorModel("valkyrie_mage")
    # --- helm: open-faced, gold brow band with a blue gem, a gold crest and feathered wings at the temples
    m.add("armorHead",
          Cube((-4, 24, -4), (8, 8, 8), 1.0, [
              shade(S, 2.9, 0.9),
              band(G, [-1], 2.4),
              stamp("front", ["aAAAAAAa", "aAAAAAAa", "GGGYYGGG", "a      a", "a      a", "1      1", "1      1", "G      G"], VP),
              stamp("top", ["...GG...", "...YG..."] * 4, VP),
              stamp("right", ["........", "........", "........", "....GY..", "....gG..", "........", "........", "........"], VP),
              stamp("left", ["........", "........", "........", "..YG....", "..Gg....", "........", "........", "........"], VP),
              stamp("back", ["........", "...GG...", "...GG...", "...Gg...", "........", "........", "........", "........"], VP)]),
          Cube((-0.5, 33, -4.5), (1, 1, 9), 0, [shade(G, 3.0, 0.5)]),
          Cube((-4.5, 29.25, -5.15), (9, 1, 1), 0, [shade(G, 2.9, 0.3), columns(G, [4], 3.8, faces=("front",))]),
          Cube((-0.5, 29.25, -5.4), (1, 1, 1), 0, [shade(P, 2.6, 0)]))
    wing_paint = [feather_stripes(F, 2.4, faces=("right", "left"), width=2), band(G, [0], 2.8, faces=("right", "left")), shade(G, 2.6, 0, faces=("top", "front")),
                  shade(F, 2.2, 0, faces=("back", "bottom"))]
    # three rows of feathers fanning back from the temple, the whole wing tilted so it sweeps up and back
    m.add_bone(Bone("helmWingRight", "armorHead", (-5.5, 29, -1), (28, -18, 0), [
        Cube((-6, 27.5, -1), (1, 2, 6), 0, wing_paint),
        Cube((-6, 29.5, -1), (1, 2, 8), 0, wing_paint),
        Cube((-6, 31.5, 0), (1, 2, 6), 0, wing_paint)], anim=sway(1, 7, 3.0)), mirror=True)
    # --- breastplate over a sky-blue tunic, a raised plastron with a gem, a gorget
    m.add("armorBody",
          Cube((-4, 12, -2), (8, 12, 4), 1.01, [
              shade(S, 2.8, 0.8),
              band(G, [7], 2.6), band(B, [8, 9, 10, 11], 2.4, highlight=False), band(G, [-1], 2.2),
              stamp("front", ["GYYYYYYG", "aAAAAAAa", "1aAAAAa1", "1aAAAAa1", "1aAGGAa1", "1aAAAAa1", "01aAAa10", "G01aa10G",
                              "BGGggGGB", "BbBGGBbB", "bBbBBbBb", "gGGGGGGg"], VP),
              stamp("back", [".GGGGGG.", "...GG...", "...Gg...", "...GG...", "...Gg...", "...GG...", "...Gg..."], VP)]),
          Cube((-3, 17, -3.65), (6, 5, 1), 0, [shade(G, 2.6, 0.4), stamp("front", ["GGYYGG", "GaAAaG", "YAPPAY", "GaAAaG", "gGGGGg"], VP)]),
          Cube((-4.5, 21.5, -3.5), (9, 2, 7), 0, [shade(S, 3.0, 0.6), band(G, [0], 2.8, faces=ALL)]))
    # --- arms: white sleeves, silver vambraces with gold edges, domed pauldrons, gold cuffs with a gem
    m.add("armorRightArm",
          Cube((-8, 12, -2), (4, 12, 4), 1.0, [
              shade(W, 2.4, 0.8), band(G, [6, 10], 2.6), band(S, [7, 8, 9], 2.8, highlight=False), band(S, [11], 2.0, highlight=False),
              shade(W, 2.6, 0, faces=("top",)), shade(S, 1.8, 0, faces=("bottom",))]),
          Cube((-8.5, 13.5, -2.5), (5, 2, 5), 0, [shade(G, 2.6, 0.6), stamp("front", ["..P..", "..p.."], VP)]),
          mirror=True)
    m.add_bone(Bone("pauldronRight", "armorRightArm", (-5, 23, 0), (0, 0, -10), [
        Cube((-9.5, 21.5, -3), (6, 3, 6), 0, [shade(S, 3.0, 1.0), band(G, [-1], 2.6), shade(S, 3.6, 0, faces=("top",)), edges(G, 2.6, faces=("top",))]),
        Cube((-10, 19.8, -3.5), (5, 2, 7), 0, [shade(S, 2.6, 0.6), band(G, [-1], 2.4)])]), mirror=True)
    # --- leggings: a gold-edged sky-blue sash worn over the chestplate's hem, blue breeches, silver greaves and knee cops.
    # The chestplate stops at the waist, so everything between the sash and the boots belongs to the leggings.
    m.add("armorLeggingTorsoLayer",
          Cube((-5, 11, -3), (10, 3, 6), 0.1, [shade(B, 2.4, 0.3), band(G, [0], 2.9), band(G, [-1], 2.0, highlight=False),
                                                columns(B, [2, 5, 8], 1.6, faces=("front", "back"), rows=[1]), shade(G, 2.8, 0, faces=("top", "bottom"))]),
          Cube((-1.5, 11.3, -3.75), (3, 2, 1), 0, [shade(G, 2.8, 0.4), stamp("front", ["GYG", "gPg"], VP)]))
    m.add("armorRightLeg",
          Cube((-4, 0, -2), (4, 12, 4), 0.5, [
              shade(S, 2.9, 1.2), band(B, [0, 1, 2, 3], 2.5, highlight=False), band(B, [0], 2.0, highlight=False),
              columns(B, [1], 1.7, faces=("front", "back"), rows=range(1, 4)), band(G, [4], 2.7),
              stamp("right", ["....", ".Y..", ".G..", ".g.."], VP), stamp("left", ["....", "..Y.", "..G.", "..g."], VP)]),
          Cube((-4.2, 5.6, -2.95), (4, 2, 1), 0, [shade(S, 3.1, 0.6), band(G, [0], 2.8, faces=SIDES), stamp("front", [".PP.", "...."], VP)]),
          mirror=True)
    # --- chestplate tassets: short silver plates at the outer hips; the front of the legs stays uncovered
    m.add("armorTorsoExtensionRightLeg",
          Cube((-5.45, 8.5, -2.5), (1, 3, 5), 0, [shade(S, 3.0, 0.8), edges(G, 2.6, faces=("right",)), band(G, [-1], 2.4, faces=("front", "back")),
                                                   shade(B, 1.8, 0, faces=("left", "top", "bottom"))]),
          mirror=True)
    # --- boots: silver with gold toe caps and rims, small feathered wings at the heels
    m.add("armorRightBoot",
          Cube((-4, 0, -2), (4, 5, 4), 0.75, [
              shade(S, 2.8, 0.8), band(G, [0], 2.8), band(S, [-1], 1.2, highlight=False),
              stamp("front", ["....", "....", "GYYG", "GGGG", "...."], VP), shade(S, 0.6, 0, faces=("bottom",))]),
          Cube((-4.5, 4.4, -2.5), (5, 1, 5), 0.25, [shade(G, 2.8, 0)]),
          mirror=True)
    heel = [feather_stripes(F, 2.4, faces=("right", "left"), width=2), band(G, [0], 2.8, faces=("right", "left")), shade(F, 2.4, 0, faces=("top", "front", "back", "bottom"))]
    m.add_bone(Bone("bootWingRight", "armorRightBoot", (-4.8, 3, 1.5), (0, -20, 0), [
        Cube((-5.8, 2, 1), (1, 2, 3), 0, heel), Cube((-5.8, 3.5, 2.5), (1, 2, 2), 0, heel)], anim=sway(1, 5, 2.0, 1.0)), mirror=True)
    return m


# ============================================================================ Phoenix Mage
X = dict(
    C=ramp("#2E0806", "#5A120C", "#8A2014", "#B8341A", "#DC5424"),              # crimson
    F=ramp("#7A1E06", "#B83A0C", "#EA6A16", "#FF9E34", "#FFD064", "#FFF2B0"),   # flame
    G=ramp("#4E2A06", "#8E5410", "#D08A24", "#F2BE52", "#FFE6A6"),              # gold
    R=ramp("#5A0A06", "#A81810", "#F04A24", "#FFA070"),                         # ruby
    A=ramp("#8A4A08", "#E08A18", "#FFC040", "#FFF0A0"),                         # amber
    D=ramp("#1E0C08", "#3A1A10", "#5A2C1A", "#7A4024"),                         # dark leather
)
XP = {"Q": (X["C"], 3.6), "R": (X["C"], 2.8), "r": (X["C"], 1.9), "q": (X["C"], 1.0),
      "Y": (X["G"], 3.6), "G": (X["G"], 2.7), "g": (X["G"], 1.7),
      "O": (X["F"], 3.2), "o": (X["F"], 4.4), "f": (X["F"], 2.2), "E": (X["R"], 2.4), "A": (X["A"], 2.6), "a": (X["A"], 1.6)}


def phoenix_mage():
    C, F, G, R, A, D = X["C"], X["F"], X["G"], X["R"], X["A"], X["D"]
    m = ArmorModel("phoenix_mage")
    feather_grad = gradient([(G, 2.8), (F, 3.4), (F, 2.4), (R, 1.8)], faces=SIDES)
    # --- hood of crimson plumage, gold-trimmed face, a gold circlet with a ruby, a crest and trailing plumes
    m.add("armorHead",
          Cube((-4, 24, -4), (8, 8, 8), 1.0, [
              scales(C, 2.4, faces=ALL),
              stamp("front", ["rRRQQRRr", "GGGYYGGG", "G      G", "G      G", "g      g", "g      g", "G      G", "g      g"], XP),
              stamp("back", ["........", "...OO...", "..OooO..", "..OooO..", "...OO...", "........", "........", "........"], XP),
              band(G, [-1], 2.2, faces=("right", "left", "back"))]),
          Cube((-5, 29.2, -5), (10, 1, 10), 0.2, [shade(G, 2.9, 0), stamp("front", ["....EE...."], {"E": (R, 2.6)})]))
    crest = [feather_grad, gradient([(G, 2.8), (F, 3.4), (F, 2.4), (R, 1.8)], faces=("front", "back")),
             shade(R, 1.8, 0, faces=("top",)), shade(G, 2.4, 0, faces=("bottom",))]
    m.add_bone(Bone("crestCenter", "armorHead", (0, 33, 1), (-20, 0, 0), [Cube((-0.5, 33, 0), (1, 7, 2), 0, crest)], anim=sway(0, 4, 2.4)))
    m.add_bone(Bone("crestRight", "armorHead", (-1.8, 32.5, 1), (-30, 0, -14), [Cube((-2.3, 32.5, 0), (1, 5, 2), 0, crest)],
                    anim=sway(2, 5, 2.4, 1.2)), mirror=True)
    plume = [gradient([(G, 2.8), (F, 3.4), (F, 2.6), (R, 2.0), (C, 1.6)], faces=("right", "left", "top", "bottom"), along="z"),
             shade(G, 2.6, 0, faces=("front",)), shade(R, 1.6, 0, faces=("back",))]
    m.add_bone(Bone("plumeRight", "armorHead", (-2.5, 30.5, 4.5), (-20, -14, 0), [Cube((-3, 30, 4.5), (1, 2, 7), 0, plume)],
                    anim=sway(0, 6, 3.2, 0.6)), mirror=True)
    # --- robe with a gold phoenix on the chest, an amber gem, a mantle of flame-tipped feathers
    m.add("armorBody",
          Cube((-4, 12, -2), (8, 12, 4), 1.01, [
              scales(C, 2.4),
              stamp("front", ["gGGYYGGg", "RrGGGGrR", "RRrGGrRR", "Y......Y", "OY....YO", "rOY..YOr", "RrOYYOrR", "RRrYYrRR",
                              "RRrGGrRR", "RRRGGRRR", "rRRGGRRr", "gGGGGGGg"], XP),
              stamp("back", [".GGGGGG.", "...GG...", "...Gg...", "...GG...", "...Gg...", "...GG...", "...Gg...", "...GG..."], XP),
              band(G, [-1], 2.2, faces=("right", "left", "back"))]),
          Cube((-1, 17.5, -3.6), (2, 2, 1), 0, [shade(A, 2.8, 0.6)]),
          Cube((-5, 20.2, -3.5), (10, 3, 7), 0, [feather_stripes(F, 3.0, width=2, tip=(R, 1, 2.2)), zigzag(),
                                                  shade(F, 3.2, 0, faces=("top",)), shade(C, 1.2, 0, faces=("bottom",))]))
    # --- arms: crimson sleeves with gold bands, feather pauldrons, flaring flame cuffs
    # crimson above the elbow band, flames licking down toward the hand
    sleeve = [scales(C, 2.3), band(G, [3], 2.6), band(G, [8], 2.4),
              stamp("front", ["...."] * 9 + ["fOOf", "OooO", "fOOf"], XP), stamp("back", ["...."] * 9 + ["fffO", "OfOf", "ffff"], XP),
              stamp("right", ["...."] * 9 + ["ffOf", "OfOO", "ffff"], XP), stamp("left", ["...."] * 9 + ["fOff", "OOfO", "ffff"], XP),
              scales(C, 2.3, faces=("top",)), shade(D, 2.0, 0, faces=("bottom",))]
    m.add("armorRightArm",
          Cube((-8, 12, -2), (4, 12, 4), 1.0, sleeve),
          Cube((-8.5, 12, -2.5), (5, 3, 5), 0, [shade(G, 2.8, 0.2), band(F, [1], 3.2, highlight=False), band(F, [2], 4.0, highlight=False), zigzag()]),
          mirror=True)
    m.add_bone(Bone("pauldronRight", "armorRightArm", (-5, 23, 0), (0, 0, -15), [
        Cube((-9.5, 21, -3), (6, 3, 6), 0, [feather_stripes(F, 3.2, width=2, tip=(F, 1, 2.0)), zigzag(), shade(G, 3.0, 0, faces=("top",)), edges(G, 2.6, faces=("top",))]),
        Cube((-10, 18.8, -3), (5, 3, 6), 0, [feather_stripes(F, 2.4, width=2, tip=(R, 1, 2.0)), zigzag(), shade(C, 2.2, 0, faces=("top", "bottom"))])]),
        mirror=True)
    # --- leggings: a gold sash with a ruby buckle worn over the robe's hem, crimson plumage legs with a gold flame
    # stripe and flames licking up from the boots
    m.add("armorLeggingTorsoLayer",
          Cube((-5, 11, -3), (10, 3, 6), 0.1, [shade(G, 2.6, 0.3), band(G, [0], 3.2), band(C, [1], 2.2, highlight=False), band(G, [-1], 1.9, highlight=False),
                                                shade(G, 2.8, 0, faces=("top", "bottom"))]),
          Cube((-1.5, 11.3, -3.75), (3, 2, 1), 0, [shade(G, 2.8, 0.2), stamp("front", ["GEG", "gEg"], {"G": (G, 2.8), "g": (G, 1.8), "E": (R, 2.4)})]))
    m.add("armorRightLeg",
          Cube((-4, 0, -2), (4, 12, 4), 0.5, [
              scales(C, 2.2), flames_up(F, 7, 3, seed=0.7, below=(F, 1.2)),
              stamp("right", ["....", ".G..", ".GY.", "..G.", ".G..", "....", "....", "....", "....", "....", "....", "...."], XP),
              stamp("left", ["....", "..G.", ".YG.", ".G..", "..G.", "....", "....", "....", "....", "....", "....", "...."], XP),
              band(G, [0], 2.0, faces=SIDES, highlight=False)]),
          mirror=True)
    # --- chestplate robe: short flame-feather tassets at the outer hips and a split coat-tail behind; the front of
    # the legs stays open so the leggings show
    m.add("armorTorsoExtensionRightLeg",
          Cube((-5.45, 8, -2.5), (1, 4, 5), 0, [feather_stripes(F, 3.0, width=2, tip=(R, 1, 2.2)), zigzag(),
                                                 shade(G, 2.8, 0, faces=("top",)), shade(C, 1.4, 0, faces=("bottom", "left"))]),
          Cube((-4.5, 7, 2.55), (4, 4, 1), 0, [
              gradient([(C, 2.6), (C, 2.2), (F, 2.4), (F, 3.4)], faces=("back", "right", "left"), seed=3),
              band(G, [0], 2.6, faces=("back",), highlight=False),
              cut(("back", "right", "left"), lambda x, y, fw, fh: y >= fh - int((math.sin(x * 1.9) * 0.5 + 0.5) * 1.6)),
              cut(("front", "top"), lambda x, y, fw, fh: True), shade(F, 2.4, 0, faces=("bottom",))]),
          mirror=True)
    # --- boots: burnished gold with crimson flames, feathers at the ankles
    m.add("armorRightBoot",
          Cube((-4, 0, -2), (4, 5, 4), 0.75, [shade(G, 2.0, 0.8), band(G, [0], 3.0), flame_tongues(R, max_height=3, seed=2), band(D, [-1], 1.2, highlight=False),
                                              shade(D, 0.8, 0, faces=("bottom",))]),
          mirror=True)
    m.add_bone(Bone("bootFeatherRight", "armorRightBoot", (-4.8, 3.5, 1), (-25, -20, 0), [
        Cube((-5.8, 2.5, 0.5), (1, 3, 3), 0, [gradient([(G, 2.8), (F, 3.2), (R, 2.0)], faces=("right", "left"), along="z"), shade(F, 3.0, 0, faces=("top", "front", "back", "bottom"))])],
        anim=sway(1, 5, 2.2)), mirror=True)
    return m


# ============================================================================ capes (vanilla cape layout: a 10x16x1 box at 0,0 of a 64x32 texture)
def cape(outer, inner, edge):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    net = Net(10, 16, 1)
    for p in outer:
        p(net)
    for p in inner:
        p(net)
    for p in edge:
        p(net)
    img.alpha_composite(net.img, (0, 0))
    return img


def valkyrie_cape():
    S, G, B, W = V["S"], V["G"], V["B"], V["W"]
    return cape(
        [shade(W, 2.8, 0.9, faces=("front",)), columns(G, [0, -1], 2.6, faces=("front",)), band(G, [-1], 2.6, faces=("front",)),
         stamp("front", ["..........", "...BBBB...", "...BGGB...", "..BGYYGB..", "..BGYYGB..", "...BGGB...", "...BBBB...", "....BB....",
                         "....bb....", "....BB....", "....bb....", "....BB....", "....bb....", "....BB....", "..........", ".........."], VP),
         cut(("front",), lambda x, y, fw, fh: y == fh - 1 and x % 2 == 1)],
        [shade(B, 2.2, 0.6, faces=("back",)), cut(("back",), lambda x, y, fw, fh: y == fh - 1 and x % 2 == 0)],
        [shade(G, 2.6, 0, faces=("top", "bottom", "right", "left"))])


def phoenix_cape():
    C, F, G, R = X["C"], X["F"], X["G"], X["R"]
    flame_cut = lambda x, y, fw, fh: y >= fh - int((math.sin(x * 1.6 + 0.5) * 0.5 + 0.5) * 3.4)
    return cape(
        [gradient([(C, 2.2), (C, 2.8), (F, 2.4), (F, 3.4), (F, 4.4)], faces=("front",), seed=5),
         stamp("front", ["..........", "..........", "...Y..Y...", "..YO..OY..", ".YOf..fOY.", ".OfYYYYfO.", "..f.YY.f..", "....YY....",
                         "....GG....", "...G..G...", "..........", "..........", "..........", "..........", "..........", ".........."], XP),
         columns(G, [0, -1], 2.4, faces=("front",), rows=range(0, 10)), cut(("front",), flame_cut)],
        [shade(C, 1.6, 0.6, faces=("back",)), cut(("back",), flame_cut)],
        [shade(G, 2.2, 0, faces=("top", "bottom", "right", "left"))])


# ============================================================================ item icons (16x16 pixel art)
def icon(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                rmp, level = pal[ch]
                img.putpixel((x, y), pick(rmp, level, x, y))
    return img


ICONS = {
    "helmet": [
        "................",
        "w..............w",
        "Ww....oooo....wW",
        "WWw..oAAAAo..wWW",
        ".WWwoAAYYAAowWW.",
        "..wWoAAGGAAoWw..",
        "...oAAAGGAAAo...",
        "...oGGGYYGGGo...",
        "...oAkkPPkkAo...",
        "...oAk....kAo...",
        "...o1k....k1o...",
        "...o1k....k1o...",
        "...oGo....oGo...",
        "....o......o....",
        "................",
        "................"],
    "chestplate": [
        "................",
        "..oooo....oooo..",
        ".oAAAAooooAAAAo.",
        ".oAAaGYYYYGaAAo.",
        ".o1aAAAAAAAAa1o.",
        "..oo1aAPPAa1oo..",
        "....o1aAAa1o....",
        "....oGGYYGGo....",
        "....oBbBBbBo....",
        "....oBBbbBBo....",
        "....obBBBBbo....",
        "....oGGGGGGo....",
        ".....oooooo.....",
        "................",
        "................",
        "................"],
    "leggings": [
        "................",
        "...oooooooooo...",
        "...oGGGYYGGGo...",
        "...oBBbPPbBBo...",
        "...oWWWooWWWo...",
        "...oWWwooWWwo...",
        "...oWWwooWWwo...",
        "...oGGGooGGGo...",
        "...o1aAoo1aAo...",
        "...o1aAoo1aAo...",
        "...o1aAoo1aAo...",
        "...o01aoo01ao...",
        "...oooooooooo...",
        "................",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "..oooo....oooo..",
        "..oGGo....oGGo..",
        "w.oAAo....oAAo.w",
        "ww1aAo....oAa1ww",
        ".w1aAo....oAa1w.",
        "..1aAoo..ooAa1..",
        "..oaAGYo.oYGAao.",
        "..o1aGGo.oGGa1o.",
        "..oooooo.oooooo.",
        "................",
        "................",
        "................"],
}


def armor_icons(prefix, pal):
    return {f"{prefix}_{piece}": icon(rows, pal) for piece, rows in ICONS.items()}


def valkyrie_icons():
    pal = dict(VP)
    pal.update({"o": (V["S"], 0.4), "k": (V["B"], 0.4), "w": (V["F"], 1.6), "W": (V["F"], 3.0)})
    return armor_icons("valkyrie_mage", pal)


def phoenix_icons():
    # same silhouettes, recoloured: crimson plumage for silver, flames for the blue cloth, gold stays gold
    pal = {"A": (X["C"], 3.4), "a": (X["C"], 2.6), "1": (X["C"], 1.8), "0": (X["C"], 1.0), "o": (X["C"], 0.2),
           "Y": (X["G"], 3.6), "G": (X["G"], 2.7), "g": (X["G"], 1.7), "B": (X["F"], 3.2), "b": (X["F"], 2.2),
           "W": (X["C"], 2.8), "w": (X["F"], 3.0), "P": (X["A"], 2.6), "p": (X["A"], 1.6), "k": (X["D"], 0.6)}
    pal["w"] = (X["F"], 3.4)
    return armor_icons("phoenix_mage", pal)


def empty_rune_slot():
    """Ghost icon for the smithing table's addition slot: a rune stone outline."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (255, 255, 255, 110)
    d.polygon([(8, 1), (13, 4), (13, 11), (8, 14), (3, 11), (3, 4)], outline=c)
    d.line([(8, 4), (8, 11)], fill=c)
    d.line([(5, 6), (8, 8), (11, 6)], fill=c)
    return img


# ============================================================================ preview
def world_matrices(model):
    """4x4 transforms of each bone in GeckoLib's baked space (x mirrored), matching the preview renderer."""
    by_name = {b.name: b for b in model.bones}
    cache = {}

    def mat(bone):
        if bone.name in cache:
            return cache[bone.name]
        parent = mat(by_name[bone.parent]) if bone.parent else np.eye(4)
        px, py, pz = -bone.pivot[0], bone.pivot[1], bone.pivot[2]
        rx, ry, rz = bone.rotation
        r = rot_z(math.radians(rz)) @ rot_y(math.radians(-ry)) @ rot_x(math.radians(-rx))
        local = np.eye(4)
        local[:3, :3] = r
        local[:3, 3] = np.array([px, py, pz]) - r @ np.array([px, py, pz])
        cache[bone.name] = parent @ local
        return cache[bone.name]

    return {b.name: mat(b) for b in model.bones}


def render_armor(r, model, atlas):
    tex = np.asarray(atlas, np.float32) / 255.0
    mats = world_matrices(model)
    for b in model.bones:
        m = mats[b.name]
        for c in b.cubes:
            (x, y, z), (w, h, d) = c.origin, c.size
            u, v = c.uv
            for face, p0, ex, ey in box_faces((-(x + w), y, z), (w, h, d), c.inflate):
                p0 = m[:3, :3] @ np.array(p0, np.float64) + m[:3, 3]
                ex = m[:3, :3] @ np.array(ex, np.float64)
                ey = m[:3, :3] @ np.array(ey, np.float64)
                r.quad(tex, face_rect_uv(u, v, w, h, d, face), p0, ex, ey, {"top": 1.0, "front": 0.95, "right": 0.8, "left": 0.8, "back": 0.7, "bottom": 0.55}[face])


def face_rect_uv(u, v, w, h, d, face):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]


def mannequin():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    grey = np.zeros((64, 64, 4), np.uint8)
    grey[...] = (120, 124, 134, 255)
    img = Image.fromarray(grey, "RGBA")
    return img


def render_mannequin(r):
    tex = np.asarray(mannequin(), np.float32) / 255.0
    for base, _, origin, size in MODEL:
        for face, p0, ex, ey in box_faces(origin, size):
            r.quad(tex, face_rect(base, face), p0, ex, ey, {"top": 1.0, "front": 0.95, "right": 0.8, "left": 0.8, "back": 0.7, "bottom": 0.55}[face])


def preview(models):
    views = [("front", math.radians(-30), math.radians(-12)), ("side", math.radians(-90), math.radians(-8)),
             ("back", math.radians(150), math.radians(-15)), ("above", math.radians(210), math.radians(-35))]
    cell = 240
    sheet = Image.new("RGB", (cell * len(views) + 150, cell * len(models)), (40, 42, 48))
    d = ImageDraw.Draw(sheet)
    for row, (model, atlas) in enumerate(models):
        for col, (label, yaw, pitch) in enumerate(views):
            r = Raster(cell, 6.2, yaw, pitch, (0, 17, 0))
            render_mannequin(r)
            render_armor(r, model, atlas)
            sheet.paste(r.image(), (col * cell, row * cell))
            d.text((col * cell + 4, row * cell + 4), f"{model.name} / {label}", fill=(230, 230, 230))
        sheet.paste(atlas, (cell * len(views) + 11, row * cell + 40), atlas)
    os.makedirs(os.path.dirname(ARMOR_PREVIEW), exist_ok=True)
    sheet.save(ARMOR_PREVIEW)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=1)
    # keep number lists on one line so the files stay readable in Blockbench / a diff
    text = re.sub(r"\[\s+([-0-9.,\s]+?)\s+\]", lambda mt: "[" + " ".join(mt.group(1).split()) + "]", text)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text + "\n")


def generate(save):
    built = []
    for model in (valkyrie_mage(), phoenix_mage()):
        atlas = model.pack()
        save(atlas, "models", "armor", model.name + ".png")
        write_json(os.path.join(ASSETS, "geo", model.name + "_armor.geo.json"), model.geo())
        write_json(os.path.join(ASSETS, "animations", model.name + "_armor.animation.json"), model.animation())
        built.append((model, atlas))
    save(valkyrie_cape(), "models", "armor", "valkyrie_mage_cape.png")
    import custom_item_art
    for name, img in {**custom_item_art.valkyrie_mage_icons(), **custom_item_art.phoenix_mage_icons()}.items():
        save(img, "item", name + ".png")
    save(custom_item_art.smithing_template(), "item", "aether_arcane_upgrade_smithing_template.png")
    save(empty_rune_slot(), "item", "empty_slot_rune.png")
    preview(built)
