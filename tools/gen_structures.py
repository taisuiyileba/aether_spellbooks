"""
Builds the structure templates of the two Aether spellcaster shrines (original designs, Aether + vanilla blocks):

  * Valkyrie Sanctum - an open octagonal temple of angelic stone on a holystone plinth: eight Aether pillars carry a
    stepped dome with an oculus over an angelic altar and its chest. Two Valkyrie Sorceresses keep it.
  * Solar Altar      - a three-tier hellfire-stone ziggurat crowned by a Sun Altar, a glowing sun ring, campfire
    braziers and a chest. Two Solar Acolytes tend it.

The casters are saved in the templates (PersistenceRequired) so they appear when the structure generates, like Iron's
Spells' own casters. Everything inside the shrine volume is written explicitly (air included) so it clears any grass
or trees on the site; blocks outside the footprint are left untouched.

Writes data/aether_spellbooks/structures/{valkyrie_sanctum,solar_altar}.nbt and build/structure_preview.png.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw

import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, ".."))
OUT = os.path.join(PROJECT, "src", "main", "resources", "data", "aether_spellbooks", "structures")
PREVIEW = os.path.join(PROJECT, "build", "structure_preview.png")
DATA_VERSION = 3465  # 1.20.1
NS = "aether_spellbooks"


class Build:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}      # (x, y, z) -> (name, props, block entity nbt)
        self.entities = []

    def set(self, x, y, z, name, props=None, be=None):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), be)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def clear(self, test, y0, y1):
        """Explicit air in the given layers wherever test(x, z) holds and nothing else has been placed."""
        for y in range(y0, y1 + 1):
            for x in range(self.size[0]):
                for z in range(self.size[2]):
                    if test(x, z) and (x, y, z) not in self.blocks:
                        self.set(x, y, z, "minecraft:air")

    def entity(self, x, y, z, entity_id, extra=None):
        data = {"id": entity_id, "PersistenceRequired": nbt.Byte(1)}
        data.update(extra or {})
        self.entities.append(((x + 0.5, float(y), z + 0.5), (x, y, z), data))

    def center_jigsaw(self, x, y, z, final_state):
        """The start jigsaw: IslandShrineStructure places the template so this block lands on the checked spot (any rotation)."""
        self.set(x, y, z, "minecraft:jigsaw", {"orientation": "up_north"},
                 {"id": "minecraft:jigsaw", "name": f"{NS}:center", "target": "minecraft:empty", "pool": "minecraft:empty",
                  "final_state": final_state, "joint": "rollable"})

    def chest(self, x, y, z, facing, loot):
        self.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot})

    def write(self, path):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, be = self.blocks[pos]
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(props)
                palette.append(entry)
            block = {"pos": nbt.List(nbt.INT, list(pos)), "state": index[key]}
            if be:
                block["nbt"] = be
            blocks.append(block)
        root = {
            "DataVersion": DATA_VERSION,
            "size": nbt.List(nbt.INT, list(self.size)),
            "palette": nbt.List(nbt.COMPOUND, palette),
            "blocks": nbt.List(nbt.COMPOUND, blocks),
            "entities": nbt.List(nbt.COMPOUND, [
                {"pos": nbt.List(nbt.DOUBLE, list(p)), "blockPos": nbt.List(nbt.INT, list(bp)), "nbt": data} for p, bp, data in self.entities]),
        }
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.write(path, root)


def octagon(r):
    """Test for a regular-ish octagon of 'radius' r around the origin (x, z offsets)."""
    return lambda dx, dz: abs(dx) <= r and abs(dz) <= r and abs(dx) + abs(dz) <= r * 1.42


def stairs(block, facing, half="bottom"):
    return block, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


# ============================================================================ Valkyrie Sanctum
def valkyrie_sanctum():
    S = 19
    c = S // 2
    b = Build(S, 11, S)
    rnd = random.Random(7)
    inside = lambda r: (lambda x, z: octagon(r)(x - c, z - c))

    for x in range(S):
        for z in range(S):
            dx, dz = x - c, z - c
            # y0: holystone brick foundation flush with the ground; y1: the angelic stone floor on a stepped plinth
            if octagon(9)(dx, dz):
                b.set(x, 0, z, "aether:holystone_bricks")
            if octagon(8)(dx, dz):
                ring = max(abs(dx), abs(dz), (abs(dx) + abs(dz)) / 1.42)
                if ring <= 2.5 or 5.5 <= ring <= 6.5:
                    floor = "aether:light_angelic_stone"
                else:
                    floor = "aether:angelic_stone"
                b.set(x, 1, z, floor)
            elif octagon(9)(dx, dz):
                b.set(x, 1, z, "aether:holystone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    # steps into the four doorways
    for side, facing in (((0, -1), "south"), ((0, 1), "north"), ((-1, 0), "east"), ((1, 0), "west")):
        for w in (-1, 0, 1):
            sx = c + side[0] * 9 + (w if side[0] == 0 else 0)
            sz = c + side[1] * 9 + (w if side[1] == 0 else 0)
            b.set(sx, 1, sz, *stairs("aether:holystone_brick_stairs", facing))

    # eight pillars on the octagon's corners: base, shaft, capital, a glowing block above each
    pillars = []
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        px, pz = round(c + math.cos(a) * 6.6), round(c + math.sin(a) * 6.6)
        pillars.append((px, pz))
        b.set(px, 2, pz, "aether:pillar_top", {"facing": "down"})
        for y in (3, 4, 5):
            b.set(px, y, pz, "aether:pillar", {"axis": "y"})
        b.set(px, 6, pz, "aether:pillar_top", {"facing": "up"})
        b.set(px, 7, pz, "aether:light_angelic_stone")
    # entablature ring, then a stepped dome around an open oculus: pale holystone brick over the warm angelic floor,
    # with a glowing band of light angelic stone round the oculus and over the pillars
    for x in range(S):
        for z in range(S):
            dx, dz = x - c, z - c
            if octagon(7.6)(dx, dz) and not octagon(5.4)(dx, dz) and b.get(x, 7, z) is None:
                b.set(x, 7, z, "aether:holystone_bricks" if (x + z) % 4 else "aether:light_angelic_stone")
            if octagon(8.3)(dx, dz) and not octagon(7.6)(dx, dz):
                b.set(x, 7, z, "aether:holystone_brick_slab", {"type": "top", "waterlogged": "false"})
            if octagon(6.4)(dx, dz) and not octagon(3.6)(dx, dz):
                b.set(x, 8, z, "aether:light_angelic_stone" if octagon(4.4)(dx, dz) else "aether:holystone_bricks")
            if octagon(7.4)(dx, dz) and not octagon(6.4)(dx, dz):
                b.set(x, 8, z, "aether:holystone_brick_slab", {"type": "bottom", "waterlogged": "false"})
            if octagon(4.4)(dx, dz) and not octagon(2.4)(dx, dz):
                b.set(x, 9, z, "aether:angelic_slab", {"type": "bottom", "waterlogged": "false"})
    # the altar: a dais with the chest, ambrosium lights at its corners, bookshelves behind
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.set(c + dx, 2, c + dz, "aether:light_angelic_stone")
    b.chest(c, 3, c, "south", f"{NS}:chests/valkyrie_sanctum")
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(c + dx, 3, c + dz, "aether:ambrosium_torch")
    for dx in (-2, -1, 1, 2):
        for y in (2, 3):
            b.set(c + dx, y, c - 5, "aether:skyroot_bookshelf")
    b.set(c, 2, c - 5, "aether:skyroot_bookshelf")
    b.set(c, 3, c - 5, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    # banners of cold aercloud drifting around the plinth
    for k in range(14):
        a = rnd.uniform(0, math.tau)
        rr = rnd.uniform(8.6, 9.4)
        x, z = round(c + math.cos(a) * rr), round(c + math.sin(a) * rr)
        if b.get(x, 1, z) is None and not octagon(8)(x - c, z - c):
            b.set(x, 1, z, "aether:cold_aercloud")
            if rnd.random() < 0.5:
                b.set(x, 2, z, "aether:cold_aercloud")

    b.center_jigsaw(c, 0, c, "aether:holystone_bricks")
    b.clear(inside(8), 2, 10)
    for x, z in ((c - 3, c + 3), (c + 3, c - 2)):
        b.entity(x, 2, z, f"{NS}:valkyrie_sorceress")
    return b


# ============================================================================ Solar Altar
def solar_altar():
    S = 17
    c = S // 2
    b = Build(S, 13, S)
    square = lambda r: (lambda x, z: abs(x - c) <= r and abs(z - c) <= r)
    tiers = [(0, 8, "aether:hellfire_stone"), (1, 7, "aether:hellfire_stone"), (2, 5, "aether:hellfire_stone"), (3, 3, "aether:hellfire_stone")]
    for y, r, block in tiers:
        for x in range(S):
            for z in range(S):
                if square(r)(x, z):
                    edge = max(abs(x - c), abs(z - c)) == r
                    name = block
                    if y > 0 and edge:
                        name = "aether:light_hellfire_stone"
                    b.set(x, y, z, name)
    # stairs up the middle of every face of every tier
    for y, r, _ in tiers[1:]:
        for side, facing in (((0, -1), "south"), ((0, 1), "north"), ((-1, 0), "east"), ((1, 0), "west")):
            for w in (-1, 0, 1):
                sx = c + side[0] * r + (w if side[0] == 0 else 0)
                sz = c + side[1] * r + (w if side[1] == 0 else 0)
                b.set(sx, y, sz, *stairs("aether:hellfire_stairs", facing))
    # braziers on the corners of the first and second tiers
    for y, r in ((2, 6), (3, 4)):
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            x, z = c + dx * r, c + dz * r
            b.set(x, y, z, "aether:light_hellfire_stone")
            b.set(x, y + 1, z, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    # the crown: a Sun Altar, a chest behind it, a glowing sun ring rising over them
    b.set(c, 4, c, "aether:sun_altar")
    b.chest(c, 4, c - 2, "south", f"{NS}:chests/solar_altar")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        b.set(c + dx, 4, c + dz, "aether:ambrosium_torch")
    ring_z = c - 3
    cy = 8.5
    for x in range(S):
        for y in range(5, 13):
            d = math.hypot(x - c, y - cy)
            if 2.6 <= d <= 3.5:
                b.set(x, y, ring_z, "minecraft:glowstone")
            elif d < 2.6:
                b.set(x, y, ring_z, "minecraft:orange_stained_glass" if d > 1.2 else "minecraft:yellow_stained_glass")
    # rays of the sun
    for a in range(0, 360, 45):
        rad = math.radians(a)
        x, y = round(c + math.cos(rad) * 4.4), round(cy + math.sin(rad) * 4.4)
        if 4 < y < 13:
            b.set(x, y, ring_z, "aether:light_hellfire_stone")
    # the ring's feet stand on the top tier
    for x in (c - 2, c + 2):
        b.set(x, 4, ring_z, "aether:hellfire_wall", {"east": "none", "north": "none", "south": "none", "west": "none", "up": "true", "waterlogged": "false"})

    b.center_jigsaw(c, 0, c, "aether:hellfire_stone")
    b.clear(square(8), 1, 12)
    for x, z in ((c - 1, c + 2), (c + 1, c + 2)):
        b.entity(x, 4, z, f"{NS}:solar_acolyte")
    return b


# ============================================================================ preview
COLORS = {
    "aether:holystone_bricks": (196, 198, 202), "aether:holystone_brick_slab": (196, 198, 202), "aether:holystone_brick_stairs": (190, 192, 196),
    "aether:angelic_stone": (216, 214, 204), "aether:light_angelic_stone": (240, 236, 214), "aether:angelic_slab": (216, 214, 204),
    "aether:pillar": (232, 230, 222), "aether:pillar_top": (244, 242, 236), "minecraft:gold_block": (246, 208, 60), "minecraft:glowstone": (250, 214, 120),
    "minecraft:chest": (160, 110, 50), "aether:ambrosium_torch": (255, 240, 120), "aether:skyroot_bookshelf": (150, 120, 80),
    "minecraft:lectern": (170, 130, 80), "aether:cold_aercloud": (236, 244, 255),
    "aether:hellfire_stone": (150, 50, 40), "aether:light_hellfire_stone": (198, 96, 70), "aether:hellfire_stairs": (150, 50, 40),
    "minecraft:campfire": (255, 150, 40), "aether:sun_altar": (250, 210, 90), "minecraft:orange_stained_glass": (240, 140, 50),
    "minecraft:yellow_stained_glass": (255, 225, 80), "aether:hellfire_wall": (150, 50, 40),
}


def render(b, scale=12):
    sx, sy, sz = b.size
    w = (sx + sz) * scale + 20
    h = (sx + sz) * scale // 2 + sy * scale + 40
    img = Image.new("RGB", (w, h), (46, 50, 60))
    d = ImageDraw.Draw(img)
    ox, oy = sz * scale + 10, sy * scale + 20
    order = sorted((p for p, v in b.blocks.items() if v[0] != "minecraft:air"), key=lambda p: (p[1], p[0] + p[2]))
    for x, y, z in order:
        col = COLORS.get(b.blocks[(x, y, z)][0], (255, 0, 255))
        px = ox + (x - z) * scale
        py = oy + (x + z) * scale // 2 - y * scale
        top = [(px, py), (px + scale, py + scale // 2), (px, py + scale), (px - scale, py + scale // 2)]
        left = [(px - scale, py + scale // 2), (px, py + scale), (px, py + 2 * scale), (px - scale, py + 1.5 * scale)]
        right = [(px, py + scale), (px + scale, py + scale // 2), (px + scale, py + 1.5 * scale), (px, py + 2 * scale)]
        shade = lambda k: tuple(int(v * k) for v in col)
        d.polygon(left, fill=shade(0.72))
        d.polygon(right, fill=shade(0.86))
        d.polygon(top, fill=col, outline=shade(0.8))
    for (x, y, z), _, data in [(bp, p, dd) for p, bp, dd in b.entities]:
        px = ox + (x - z) * scale
        py = oy + (x + z) * scale // 2 - (y + 2) * scale
        d.rectangle([px - 3, py, px + 3, py + scale * 2], fill=(80, 170, 255) if "valkyrie" in data["id"] else (255, 90, 40))
    return img


def main():
    shrines = {"valkyrie_sanctum": valkyrie_sanctum(), "solar_altar": solar_altar()}
    previews = []
    for name, build in shrines.items():
        path = os.path.join(OUT, name + ".nbt")
        build.write(path)
        print(f"{name}: {build.size}, {len(build.blocks)} blocks, {len(build.entities)} casters -> {os.path.normpath(path)}")
        previews.append(render(build))
    sheet = Image.new("RGB", (sum(p.width for p in previews), max(p.height for p in previews)), (46, 50, 60))
    x = 0
    for p in previews:
        sheet.paste(p, (x, 0))
        x += p.width
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)


if __name__ == "__main__":
    main()
