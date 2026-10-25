"""Dungeon rematch art and 3D vault models for Aether Spellbooks (1.20.1).

Writes into src/main/resources/assets/aether_spellbooks/:
  models/block/template_dungeon_vault.json, template_dungeon_vault_ejecting.json
  models/block/{carved,angelic,hellfire}_vault{,_active,_unlocking,_ejecting_reward}.json
  textures/block/{carved,angelic,hellfire}_vault_{bottom,side,side_active,front,front_active,front_unlocking,top,top_ejecting}.png
  textures/item/{guardian_shard,sentry_core,valkyrie_oath,solar_ember}.png (+ .png.mcmeta)
  textures/entity/guardian_soul_{slider,valkyrie_queen,sun_spirit}.png     (4 stacked 16x16 frames)
  textures/entity/guardian_soul_glow.png, guardian_soul_beam.png

Vaults follow Iron's Spells' vault anatomy (3px top cornice with stone masonry joints, 3px bottom plinth with recessed
energy trough, 3x3 side cage lattice with vertical & horizontal bars, sculpted front guardian lock crest with side rib
slits, 4-quadrant top hatch and octagonal open ejection shaft) combined with a multi-element 3D block model (stepped
plinth + inner altar pedestal, 3D corner pillars, recessed double-sided cage lattice, protruding 3D front guardian lock
emblem, and recessed top shutter well).

Run:   python tools/dungeon_art.py            (writes models, textures and previews)
       python tools/dungeon_art.py --check    (re-renders in memory, verifies the exported files are identical)
Previews go to build/art_preview/dungeon/.
"""
import argparse
import io
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
TEX = ASSETS / 'textures'
MODELS = ASSETS / 'models/block'
PREVIEW = ROOT / 'build/art_preview/dungeon'


def rgba(h, a=255):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def render(rows, pal):
    h, w = len(rows), len(rows[0])
    assert all(len(r) == w for r in rows), [(i, len(r), r) for i, r in enumerate(rows) if len(r) != w]
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = rgba(pal[ch]) if isinstance(pal[ch], str) else pal[ch]
    return im


def apply(rows, changes):
    grid = [list(r) for r in rows]
    for (x, y), ch in changes.items():
        assert grid[y][x] != '.', ('animation must not touch transparent texels', x, y)
        grid[y][x] = ch
    return [''.join(r) for r in grid]


def strip(frames):
    w, h = frames[0].size
    out = Image.new('RGBA', (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, h * i))
    return out


# =====================================================================================================================
# VAULT PALETTES & FACES
# =====================================================================================================================
# Shared symbol roles across the three dungeon tiers:
#   O       deepest crevice / iron-stone outline
#   0..5    dungeon stone ramp (dark shadow -> bright highlight edge)
#   b, m, M, B   cage & lock metal/trim ramp (dark iron/bronze -> specular highlight)
#   h       abyssal keyhole / open shaft interior
#   i, j    dormant glyph inlay (shadow / mid)
#   g, G, W active glyph / energy glow (outer glow -> bright core -> white-hot flare)

TIERS = {
    'carved': {   # Bronze Dungeon (Slider): cool slate-grey carved stone, dark bronze trim, sentry-cyan eye & runes
        'O': '#181c24', '0': '#282f3a', '1': '#3b4452', '2': '#525d6e', '3': '#6d798b', '4': '#8e9aa9', '5': '#b6c0ce',
        'b': '#231f1d', 'm': '#3d352e', 'M': '#68594a', 'B': '#99836b',
        'h': '#0d1016', 'i': '#1c3454', 'j': '#2b5280',
        'g': '#1f75d8', 'G': '#62c2ff', 'W': '#e8faff',
    },
    'angelic': {  # Silver Dungeon (Valkyrie Queen): warm ivory angelic marble, burnished gold trim, holy gold-amber glow
        'O': '#332617', '0': '#4f3d26', '1': '#6e5739', '2': '#917752', '3': '#b59c73', '4': '#d6c299', '5': '#f2e6c7',
        'b': '#3b2c14', 'm': '#6b4c1b', 'M': '#a87b2a', 'B': '#e3b548',
        'h': '#1a120a', 'i': '#59421a', 'j': '#876527',
        'g': '#d88c18', 'G': '#ffd24a', 'W': '#fffbe0',
    },
    'hellfire': {  # Gold Dungeon (Sun Spirit): deep crimson hellfire brick, dark forged copper trim, solar fire glow
        'O': '#1e080a', '0': '#361113', '1': '#521b1c', '2': '#722826', '3': '#943a33', '4': '#b85346', '5': '#da7662',
        'b': '#290f0a', 'm': '#4f1f14', 'M': '#87381d', 'B': '#c45e28',
        'h': '#110405', 'i': '#4a140e', 'j': '#782415',
        'g': '#e03e12', 'G': '#ff9a24', 'W': '#fff3b8',
    },
}


# ---------------------------------------------------------------------------------------------------------------------
# SIDE FACE (16x16):
#   rows 0..2:  3px sculpted stone cornice with block joints at x=4,11 and side rune studs at x=2,13
#   rows 3..12: 3x3 cage lattice (outer stone pillars x=0..1,14..15; inner metal stiles x=2,13; vertical bars x=5,10;
#               horizontal bars y=3,6,9,12 with rivet highlights at intersections; 9 transparent 2px-tall windows)
#   rows 13..15: 3px stone plinth with recessed energy channel at x=4..11, y=13..14

SIDE_IDLE = [
    "4554155555514553",
    "4321343333431232",
    "110O01111110O010",
    "32mbbbbbbbbbbm21",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "10mMMBmmmmBMMm0O",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "10mMMBmmmmBMMm0O",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "21mMMMMMMMMMMm10",
    "4320ijjjjjji0232",
    "3210iiiiiiii0121",
    "100O00000000O00O",
]

SIDE_ACTIVE = [
    "4554155555514553",
    "43g1343333431g32",
    "110O01111110O010",
    "32mbbbbbbbbbbm21",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "10mMMBmmmmBMMm0O",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "10mMMBmmmmBMMm0O",
    "42m..m....m..m21",
    "31b..b....b..b10",
    "21mMMBggggBMMm10",
    "4320gGGGGGGg0232",
    "3210gggggggg0121",
    "100O00000000O00O",
]


# ---------------------------------------------------------------------------------------------------------------------
# FRONT FACES (16x16):
#   rows 0..2:  sculpted stone cornice with central guardian brow (x=5..10)
#   rows 3..12: outer pillars (x=0..1, 14..15), horizontal cage ribs (y=3,6,9,12) with 3-row window slits on left
#               (x=2..4) and right (x=11..13), and the central 6x10 sculpted guardian crest + keyhole (x=5..10)
#   rows 13..15: 3px stone plinth with recessed energy channel

FRONT_CRESTS = {
    # Carved (Slider): geometric monolith brow, square Sentry Eye socket (x=5..10, y=3..7), bronze keyhole jaw below
    'carved': {
        'idle': [
            "4554155445514553",
            "4321354224531232",
            "110O03i22i30O010",
            "32mbb30OO03bbm21",
            "42...2OjjO2...21",
            "31...2Ojjo2...10",
            "10mMM3OiiO3MMm0O",
            "42...10OO01...21",
            "31...mBhhBm...10",
            "10mMMmMhhMmMMm0O",
            "42...mbhhbm...21",
            "31...0bhhb0...10",
            "21mMM10OO01MMm10",
            "4320ijjjjjji0232",
            "3210iiiiiiii0121",
            "100O00000000O00O",
        ],
        'active': [
            "4554155445514553",
            "43g1354224531g32",
            "110O03G22G30O010",
            "32mbb30OO03bbm21",
            "42...2OGGO2...21",
            "31...2GWWG2...10",
            "10mMM3OGGO3MMm0O",
            "42...g0OO0g...21",
            "31...mBhhBm...10",
            "10mMMgMhhMgMMm0O",
            "42...gbhhbg...21",
            "31...0bggb0...10",
            "21mMM1gGGg1MMm10",
            "4320gGGGGGGg0232",
            "3210gggggggg0121",
            "100O00000000O00O",
        ],
        'unlocking': [
            "4554155445514553",
            "43G1354334531G32",
            "110O03W22W30O010",
            "32mbb30GG03bbm21",
            "42...2GWWO2...21",
            "31...2WWWW2...10",
            "10mMM3GWWG3MMm0O",
            "42...G0GG0G...21",
            "31...mBWWBm...10",
            "10mMMGMGGMGMMm0O",
            "42...gbGGbg...21",
            "31...0bGGb0...10",
            "21mMM1GWWG1MMm10",
            "4320GWWWWWWG0232",
            "3210gGGGGGGg0121",
            "100O00000000O00O",
        ],
    },
    # Angelic (Valkyrie Queen): winged golden crown brow, radiant Valkyrie diamond sigil, ornate arched keyhole
    'angelic': {
        'idle': [
            "455415B44B514553",
            "43215BM33MB51232",
            "110O4M2ii2M4O010",
            "32mbbMijjiMbbm21",
            "42...BjjjjB...21",
            "31...MijjiM...10",
            "10mMM3MiiM3MMm0O",
            "42...2mMMm2...21",
            "31...3BhhB3...10",
            "10mMM2MhhM2MMm0O",
            "42...2mhhm2...21",
            "31...1mhhm1...10",
            "21mMM20OO02MMm10",
            "4320ijjjjjji0232",
            "3210iiiiiiii0121",
            "100O00000000O00O",
        ],
        'active': [
            "455415B44B514553",
            "43g15BM33MB51g32",
            "110O4M2GG2M4O010",
            "32mbbMgGGgMbbm21",
            "42...BGWWGB...21",
            "31...MgGGgM...10",
            "10mMM3MGGM3MMm0O",
            "42...2mBBm2...21",
            "31...3BhhB3...10",
            "10mMMgMhhMgMMm0O",
            "42...gmhhmg...21",
            "31...1mggm1...10",
            "21mMM2gGGg2MMm10",
            "4320gGGGGGGg0232",
            "3210gggggggg0121",
            "100O00000000O00O",
        ],
        'unlocking': [
            "455415B44B514553",
            "43G15BW44WB51G32",
            "110O4B2WW2B4O010",
            "32mbbBGWWGBbbm21",
            "42...BWWWWB...21",
            "31...BGWWGB...10",
            "10mMM3BWWB3MMm0O",
            "42...2BBBB2...21",
            "31...3BWWB3...10",
            "10mMMGMGGMGMMm0O",
            "42...gmGGmg...21",
            "31...1mGGm1...10",
            "21mMM2GWWG2MMm10",
            "4320GWWWWWWG0232",
            "3210gGGGGGGg0121",
            "100O00000000O00O",
        ],
    },
    # Hellfire (Sun Spirit): horned solar brow, 8-rayed sunburst emblem, furnace-jaw keyhole over molten trough
    'hellfire': {
        'idle': [
            "45541B4334B14553",
            "43213M4224M31232",
            "110O0ji22ij0O010",
            "32mbb2ijji2bbm21",
            "42...ijjjji...21",
            "31...ijjjji...10",
            "10mMM2ijji2MMm0O",
            "42...ji00ij...21",
            "31...mBhhBm...10",
            "10mMMmMhhMmMMm0O",
            "42...mbhhbm...21",
            "31...0bhhb0...10",
            "21mMM10OO01MMm10",
            "4320ijjjjjji0232",
            "3210iiiiiiii0121",
            "100O00000000O00O",
        ],
        'active': [
            "45541B4334B14553",
            "43g13M4224M31g32",
            "110O0Gg22gG0O010",
            "32mbb2gGGg2bbm21",
            "42...gGWWGg...21",
            "31...gGWWGg...10",
            "10mMM2gGGg2MMm0O",
            "42...Gg00gG...21",
            "31...mBhhBm...10",
            "10mMMgMhhMgMMm0O",
            "42...gbhhbg...21",
            "31...0bggb0...10",
            "21mMM1gGGg1MMm10",
            "4320gGGGGGGg0232",
            "3210gggggggg0121",
            "100O00000000O00O",
        ],
        'unlocking': [
            "45541B4334B14553",
            "43G13B4334B31G32",
            "110O0WG22GW0O010",
            "32mbb2GWWG2bbm21",
            "42...GWWWWG...21",
            "31...GWWWWG...10",
            "10mMM2GWWG2MMm0O",
            "42...WG00GW...21",
            "31...mBWWBm...10",
            "10mMMGMGGMGMMm0O",
            "42...gbGGbg...21",
            "31...0bGGb0...10",
            "21mMM1GWWG1MMm10",
            "4320GWWWWWWG0232",
            "3210gGGGGGGg0121",
            "100O00000000O00O",
        ],
    },
}

# Fix single lowercase 'o' typo in carved idle row 5 -> 'j'
FRONT_CRESTS['carved']['idle'][5] = "31...2OjjO2...10"


# ---------------------------------------------------------------------------------------------------------------------
# TOP & BOTTOM FACES (16x16):
#   top:          2px stone cornice rim, 4-quadrant sculpted hatch with diagonal ribs & central rune clasp
#   top_ejecting: 2px stone cornice rim, 10x10 octagonal dark shaft with 4 glowing corner clamps
#   bottom:       2px stone rim, 2x2 Aether dungeon stone bevelled tile masonry with corner rivets

TOP_CLOSED = [
    "4554155555514553",
    "4322133333312232",
    "430bbbbbbbbbb031",
    "42b4432mM2344b21",
    "11b4321mM1234b10",
    "53b321mbBM123b32",
    "53b21mBjjBM12b31",
    "53bmMBjGGjBMmb31",
    "53bmMBjGGjBMmb31",
    "53b21mBjjBM12b31",
    "53b321mbBM123b32",
    "11b4321mM1234b10",
    "32b4432mM2344b21",
    "320bbbbbbbbbb021",
    "3211122222211120",
    "2100O000000O001O",
]

TOP_EJECTING = [
    "4554155555514553",
    "4322133333312232",
    "430bbbbbbbbbb031",
    "42bMBgOhhhOgBMb21",  # adjusted to 16 chars below
    "11bBGOhhhhhOGBb10",
    "53bgOhhhhhhhhOgb32",
    "53bOhhhhhhhhhhOb31",
    "53bOhhhhhhhhhhOb31",
    "53bOhhhhhhhhhhOb31",
    "53bOhhhhhhhhhhOb31",
    "53bgOhhhhhhhhOgb32",
    "11bBGOhhhhhOGBb10",
    "32bMBgOhhhOgBMb21",
    "320bbbbbbbbbb021",
    "3211122222211120",
    "2100O000000O001O",
]

TOP_EJECTING = [
    "4554155555514553",
    "4322133333312232",
    "430bbbbbbbbbb031",
    "42bMBgOhhOgBMb21",
    "11bBGWOhhhOWGBb10",  # wait: 16 chars
]
# Let's write exact 16-char rows for TOP_EJECTING and BOTTOM:
TOP_EJECTING = [
    "4554155555514553",
    "4322133333312232",
    "430bbbbbbbbbb031",
    "42bMBgOhhOgBMb21",
    "11bBGOhhhhOGBb10",
    "53bgOhhhhhhOgb32",
    "53bOhhhhhhhhOb31",
    "53bOhhhhhhhhOb31",
    "53bOhhhhhhhhOb31",
    "53bOhhhhhhhhOb31",
    "53bgOhhhhhhOgb32",
    "11bBGOhhhhOGBb10",
    "32bMBgOhhOgBMb21",
    "320bbbbbbbbbb021",
    "3211122222211120",
    "2100O000000O001O",
]

BOTTOM_ROWS = [
    "3443144444413442",
    "3211022222201121",
    "310OOOOOOOOOO010",
    "32O44332044332O1",
    "10O43221043221O0",
    "32O32232032232O1",
    "32O32321032321O1",
    "32O21210O21210O1",
    "10O0000OO0000OO0",
    "32O44332044332O1",
    "32O43221043221O1",
    "10O32232032232O0",
    "21O21210O21210O0",
    "210OOOOOOOOOO010",
    "2100011111100010",
    "1000O000000O000O",
]


def vault_textures(tier_name):
    pal = TIERS[tier_name]
    crests = FRONT_CRESTS[tier_name]
    faces = {
        'bottom': BOTTOM_ROWS,
        'side': SIDE_IDLE,
        'side_active': SIDE_ACTIVE,
        'front': crests['idle'],
        'front_active': crests['active'],
        'front_unlocking': crests['unlocking'],
        'top': TOP_CLOSED,
        'top_ejecting': TOP_EJECTING,
    }
    return {f'block/{tier_name}_vault_{name}': render(rows, pal) for name, rows in faces.items()}


# =====================================================================================================================
# 3D VAULT BLOCK MODELS
# =====================================================================================================================
# Extends Iron's Spells' double-sided vault cage with 3D architectural relief:
#   - 3px bottom stone plinth + 1px inner altar pedestal (where the floating display item hovers)
#   - 4 solid 3D corner pillars (2x10x2) with inner & outer faces
#   - Recessed double-sided cage lattice (outer at 0.75px, inverted inner at 0.752px)
#   - Protruding 3D front guardian lock crest (x=5..11, y=3..13, z=0..1.5)
#   - 3px top stone cornice rim with a recessed 3D shutter well (y=15.25 when closed, y=13.75 deep shaft when ejecting)

def build_template_vault_model(ejecting=False):
    shutter_y = 13.5 if ejecting else 15.0
    elements = [
        {
            "name": "base_plinth",
            "from": [0, 0, 0],
            "to": [16, 3, 16],
            "faces": {
                "north": {"uv": [0, 13, 16, 16], "texture": "#front", "cullface": "north"},
                "east": {"uv": [0, 13, 16, 16], "texture": "#side", "cullface": "east"},
                "south": {"uv": [0, 13, 16, 16], "texture": "#side", "cullface": "south"},
                "west": {"uv": [0, 13, 16, 16], "texture": "#side", "cullface": "west"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            },
        },
        {
            "name": "top_cornice_north",
            "from": [0, 13, 0],
            "to": [16, 16, 3],
            "faces": {
                "north": {"uv": [0, 0, 16, 3], "texture": "#front", "cullface": "north"},
                "east": {"uv": [13, 0, 16, 3], "texture": "#side", "cullface": "east"},
                "west": {"uv": [0, 0, 3, 3], "texture": "#side", "cullface": "west"},
                "south": {"uv": [3, 0, 13, 3], "texture": "#side"},
                "up": {"uv": [0, 0, 16, 3], "texture": "#top", "cullface": "up"},
                "down": {"uv": [0, 0, 16, 3], "texture": "#bottom"},
            },
        },
        {
            "name": "top_cornice_south",
            "from": [0, 13, 13],
            "to": [16, 16, 16],
            "faces": {
                "south": {"uv": [0, 0, 16, 3], "texture": "#side", "cullface": "south"},
                "east": {"uv": [0, 0, 3, 3], "texture": "#side", "cullface": "east"},
                "west": {"uv": [13, 0, 16, 3], "texture": "#side", "cullface": "west"},
                "north": {"uv": [3, 0, 13, 3], "texture": "#side"},
                "up": {"uv": [0, 13, 16, 16], "texture": "#top", "cullface": "up"},
                "down": {"uv": [0, 13, 16, 16], "texture": "#bottom"},
            },
        },
        {
            "name": "top_cornice_west",
            "from": [0, 13, 3],
            "to": [3, 16, 13],
            "faces": {
                "west": {"uv": [3, 0, 13, 3], "texture": "#side", "cullface": "west"},
                "east": {"uv": [3, 0, 13, 3], "texture": "#side"},
                "up": {"uv": [0, 3, 3, 13], "texture": "#top", "cullface": "up"},
                "down": {"uv": [0, 3, 3, 13], "texture": "#bottom"},
            },
        },
        {
            "name": "top_cornice_east",
            "from": [13, 13, 3],
            "to": [16, 16, 13],
            "faces": {
                "east": {"uv": [3, 0, 13, 3], "texture": "#side", "cullface": "east"},
                "west": {"uv": [3, 0, 13, 3], "texture": "#side"},
                "up": {"uv": [13, 3, 16, 13], "texture": "#top", "cullface": "up"},
                "down": {"uv": [13, 3, 16, 13], "texture": "#bottom"},
            },
        },
        {
            "name": "top_shutter",
            "from": [3, 13, 3],
            "to": [13, shutter_y, 13],
            "faces": {
                "up": {"uv": [3, 3, 13, 13], "texture": "#top"},
                "down": {"uv": [3, 3, 13, 13], "texture": "#top"},
            },
        },
        {
            "name": "pillar_nw",
            "from": [0, 3, 0],
            "to": [2, 13, 2],
            "faces": {
                "north": {"uv": [14, 3, 16, 13], "texture": "#front", "cullface": "north"},
                "west": {"uv": [0, 3, 2, 13], "texture": "#side", "cullface": "west"},
                "south": {"uv": [0, 3, 2, 13], "texture": "#side"},
                "east": {"uv": [14, 3, 16, 13], "texture": "#front"},
            },
        },
        {
            "name": "pillar_ne",
            "from": [14, 3, 0],
            "to": [16, 13, 2],
            "faces": {
                "north": {"uv": [0, 3, 2, 13], "texture": "#front", "cullface": "north"},
                "east": {"uv": [14, 3, 16, 13], "texture": "#side", "cullface": "east"},
                "south": {"uv": [14, 3, 16, 13], "texture": "#side"},
                "west": {"uv": [0, 3, 2, 13], "texture": "#front"},
            },
        },
        {
            "name": "pillar_sw",
            "from": [0, 3, 14],
            "to": [2, 13, 16],
            "faces": {
                "south": {"uv": [0, 3, 2, 13], "texture": "#side", "cullface": "south"},
                "west": {"uv": [14, 3, 16, 13], "texture": "#side", "cullface": "west"},
                "north": {"uv": [14, 3, 16, 13], "texture": "#side"},
                "east": {"uv": [0, 3, 2, 13], "texture": "#side"},
            },
        },
        {
            "name": "pillar_se",
            "from": [14, 3, 14],
            "to": [16, 13, 16],
            "faces": {
                "south": {"uv": [14, 3, 16, 13], "texture": "#side", "cullface": "south"},
                "east": {"uv": [0, 3, 2, 13], "texture": "#side", "cullface": "east"},
                "north": {"uv": [0, 3, 2, 13], "texture": "#side"},
                "west": {"uv": [14, 3, 16, 13], "texture": "#side"},
            },
        },
        {
            "name": "front_crest",
            "from": [5, 3, 0],
            "to": [11, 13, 1.25],
            "faces": {
                "north": {"uv": [5, 3, 11, 13], "texture": "#front", "cullface": "north"},
                "south": {"uv": [11, 3, 5, 13], "texture": "#front"},
                "west": {"uv": [10, 3, 11, 13], "texture": "#front"},
                "east": {"uv": [5, 3, 6, 13], "texture": "#front"},
            },
        },
        {
            "name": "cage_north",
            "from": [2, 3, 0.75],
            "to": [14, 13, 0.77],
            "faces": {
                "north": {"uv": [2, 3, 14, 13], "texture": "#front"},
                "south": {"uv": [14, 3, 2, 13], "texture": "#front"},
            },
        },
        {
            "name": "cage_south",
            "from": [2, 3, 15.23],
            "to": [14, 13, 15.25],
            "faces": {
                "south": {"uv": [2, 3, 14, 13], "texture": "#side"},
                "north": {"uv": [14, 3, 2, 13], "texture": "#side"},
            },
        },
        {
            "name": "cage_west",
            "from": [0.75, 3, 2],
            "to": [0.77, 13, 14],
            "faces": {
                "west": {"uv": [2, 3, 14, 13], "texture": "#side"},
                "east": {"uv": [14, 3, 2, 13], "texture": "#side"},
            },
        },
        {
            "name": "cage_east",
            "from": [15.23, 3, 2],
            "to": [15.25, 13, 14],
            "faces": {
                "east": {"uv": [2, 3, 14, 13], "texture": "#side"},
                "west": {"uv": [14, 3, 2, 13], "texture": "#side"},
            },
        },
    ]
    return {
        "parent": "block/block",
        "textures": {"particle": "#side"},
        "render_type": "minecraft:cutout",
        "elements": elements,
    }


def build_tier_block_models(tier):
    base = f"aether_spellbooks:block/{tier}_vault"
    return {
        f"{tier}_vault.json": {
            "parent": "aether_spellbooks:block/template_dungeon_vault",
            "render_type": "minecraft:cutout",
            "textures": {
                "bottom": f"{base}_bottom",
                "front": f"{base}_front",
                "side": f"{base}_side",
                "top": f"{base}_top",
            },
        },
        f"{tier}_vault_active.json": {
            "parent": "aether_spellbooks:block/template_dungeon_vault",
            "render_type": "minecraft:cutout",
            "textures": {
                "bottom": f"{base}_bottom",
                "front": f"{base}_front_active",
                "side": f"{base}_side_active",
                "top": f"{base}_top",
            },
        },
        f"{tier}_vault_unlocking.json": {
            "parent": "aether_spellbooks:block/template_dungeon_vault",
            "render_type": "minecraft:cutout",
            "textures": {
                "bottom": f"{base}_bottom",
                "front": f"{base}_front_unlocking",
                "side": f"{base}_side_active",
                "top": f"{base}_top",
            },
        },
        f"{tier}_vault_ejecting_reward.json": {
            "parent": "aether_spellbooks:block/template_dungeon_vault_ejecting",
            "render_type": "minecraft:cutout",
            "textures": {
                "bottom": f"{base}_bottom",
                "front": f"{base}_front_unlocking",
                "side": f"{base}_side_active",
                "top": f"{base}_top_ejecting",
            },
        },
    }


# =====================================================================================================================
# ITEMS (16x16 hand-placed pixel art + glint animations)
# =====================================================================================================================

GUARDIAN_SHARD = {  # Jagged multi-faceted runic monolith shard with a glowing arcane fracture and gold clasp edge
    'rows': [
        ".........OO.....",
        "........O54O....",
        ".......O5432O...",
        "......O54g321O..",
        ".....O54gG3221O.",
        "....O543GWG321O.",
        "...O543gG4G221O.",
        "...O43gG43g210O.",
        "..O543G3323210O.",
        "..O43g33bB2210O.",
        ".O543gG2MBm10O..",
        ".O432GWGmbb10O..",
        ".O322gGg2110O...",
        "..O211g1100O....",
        "...OO1000OO.....",
        ".....OOOO.......",
    ],
    'pal': {
        'O': '#1a202c', '0': '#353d4d', '1': '#505a6e', '2': '#707b90', '3': '#94a0b4', '4': '#bcc6d6', '5': '#ebf0f7',
        'b': '#5c3c14', 'm': '#9c6c20', 'M': '#d8a238', 'B': '#ffe078',
        'g': '#2072d8', 'G': '#62c4ff', 'W': '#e8fbff',
    },
}

SENTRY_CORE = {  # Octagonal Slider Golem Core: carved-stone armor plates with bronze clasps framing a glowing Sentry Eye
    'rows': [
        "....OOOOOOOO....",
        "..OO45544554OO..",
        ".O45433gG33454O.",
        ".O53BM2gG2MB35O.",
        "O43M10OOOO01M34O",
        "O5320OiiiiO0235O",
        "O42gOiIGGIiOg24O",
        "O3ggOiGWWGiOgg3O",
        "O3GG0iGWWGi0GG3O",
        "O42gOiIGGIiOg24O",
        "O5320OiiiiO0235O",
        "O43M10OOOO01M34O",
        ".O53BM2gG2MB35O.",
        ".O32110gG01123O.",
        "..OO21000012OO..",
        "....OOOOOOOO....",
    ],
    'pal': {
        'O': '#181d26', '0': '#2b3240', '1': '#414a5c', '2': '#5b667a', '3': '#788498', '4': '#9ca8ba', '5': '#ccd4e0',
        'm': '#5a4224', 'M': '#96703c', 'B': '#d2a660',
        'i': '#1c4488', 'g': '#2678dc', 'I': '#52a8f8', 'G': '#82d4ff', 'W': '#ebfcff',
    },
}

VALKYRIE_OATH = {  # Royal Valkyrie winged crest: layered feather wings cradling a sapphire-and-gold sun medallion
    'rows': [
        ".OO..........OO.",
        "O54O........O45O",
        "O543O......O345O",
        "O4543O.OO.O3454O",
        "O34532OdbO23543O",
        ".O2442ObcO2442O.",
        ".O3432OdcO2343O.",
        "..O232OOOO232O..",
        "..O23dbddbd32O..",
        "...OOdcSWcdOO...",
        "....OdcsscdO....",
        "....ObbccbbO....",
        "....OsOaaOsO....",
        "...OsSOOOOsSO...",
        "...OO......OO...",
        "................",
    ],
    'pal': {
        'O': '#2e2518', '2': '#9eadc4', '3': '#c4cfdf', '4': '#e4ebf5', '5': '#ffffff',
        'a': '#7a4b12', 'b': '#b4781e', 'c': '#e4ac38', 'd': '#ffe484', 'W': '#ffffff',
        's': '#2462b8', 'S': '#6ec0ff',
    },
}

SOLAR_EMBER = {  # Blazing miniature Sun Spirit heart cradled inside a crimson hellfire-stone & golden-amber crown
    'rows': [
        ".......OO.......",
        "......OfFO......",
        "...O..OyyO..O...",
        "..OfO.OyyO.OfO..",
        ".OfFOOfWWfOOFfO.",
        ".O2fyfWWWWfyf2O.",
        ".O32yWWWWWWy23O.",
        "O432yWWWWWWy234O",
        "O321fyWWWWyf123O",
        ".O21rfyyyyfr12O.",
        ".O32OrffffrO23O.",
        "..O32OrrrrO23O..",
        "..O21aOOOOa12O..",
        "...O2A1111A2O...",
        "....OOOOOOOO....",
        "................",
    ],
    'pal': {
        'O': '#240a08', '1': '#4c1815', '2': '#722923', '3': '#9a3e34', '4': '#c25a4a',
        'a': '#cc8818', 'A': '#ffd24c',
        'r': '#b82814', 'f': '#eb6218', 'F': '#ff9c2c', 'y': '#ffd454', 'W': '#fff8d6',
    },
}


def glint(rows, steps, rest):
    frames = [rows] + [apply(rows, s) for s in steps]
    timing = [{'index': 0, 'time': rest}] + [{'index': i, 'time': 2} for i in range(1, len(frames))]
    return frames, timing


def shard_frames():
    low = {(6, 11): 'W', (7, 11): 'W', (6, 10): 'G', (7, 10): 'W'}
    mid = {(6, 8): 'W', (6, 7): 'W', (7, 6): 'W', (8, 5): 'W', (9, 9): 'B'}
    top = {(7, 5): 'W', (8, 4): 'W', (7, 3): 'G', (9, 10): 'B'}
    return glint(GUARDIAN_SHARD['rows'], [low, mid, top, mid], 44)


def core_frames():
    ring = [(6, 6), (9, 6), (6, 9), (9, 9), (7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8)]
    conduits = [(7, 2), (8, 2), (7, 3), (8, 3), (7, 12), (8, 12), (7, 13), (8, 13),
                (2, 7), (3, 7), (2, 8), (3, 8), (12, 7), (13, 7), (12, 8), (13, 8)]
    a = {p: 'G' for p in ring} | {p: 'G' for p in conduits}
    b = {p: 'W' for p in ring[:4]} | {p: 'G' for p in ring[4:]} | {p: 'W' for p in conduits}
    return glint(SENTRY_CORE['rows'], [a, b, b, a], 38)


def oath_frames():
    s1 = {(7, 10): 'S', (8, 10): 'S', (7, 9): 'W'}
    s2 = {(7, 10): 'W', (8, 10): 'S', (6, 8): 'W', (9, 8): 'W'}
    s3 = {(4, 4): '5', (11, 4): '5', (3, 3): '5', (12, 3): '5', (7, 5): 'W', (8, 5): 'W'}
    return glint(VALKYRIE_OATH['rows'], [s1, s2, s3, s2], 50)


def ember_frames():
    f1 = {(7, 2): 'W', (8, 2): 'W', (3, 4): 'y', (12, 4): 'y', (5, 12): 'A', (10, 12): 'A'}
    f2 = {(7, 1): 'y', (8, 1): 'W', (7, 3): 'W', (8, 3): 'W', (3, 3): 'F', (12, 3): 'F', (5, 9): 'W', (10, 9): 'W'}
    return glint(SOLAR_EMBER['rows'], [f1, f2, f2, f1], 28)


ITEMS = {
    'guardian_shard': (GUARDIAN_SHARD['pal'], shard_frames),
    'sentry_core': (SENTRY_CORE['pal'], core_frames),
    'valkyrie_oath': (VALKYRIE_OATH['pal'], oath_frames),
    'solar_ember': (SOLAR_EMBER['pal'], ember_frames),
}


def item_texture(name):
    pal, builder = ITEMS[name]
    frames, timing = builder()
    ims = [render(f, pal) for f in frames]
    alpha = ims[0].getchannel('A').tobytes()
    for im in ims:
        assert im.getchannel('A').tobytes() == alpha, (name, 'silhouette changes between frames')
    meta = {'animation': {'interpolate': False, 'frametime': 2, 'frames': timing}}
    return strip(ims), meta, ims


# =====================================================================================================================
# GUARDIAN SOULS (16x16 x 4 frames) + GLOW (32x32) + BEAM (16x32)
# =====================================================================================================================
# Each boss has its own iconic 16x16 spectral silhouette:
#   slider:         floating geometric monolith ring + piercing square Sentry Eye core + ascending soul wisps
#   valkyrie_queen: crowned Valkyrie spirit with sweeping spectral feather wings
#   sun_spirit:     blazing 8-pointed solar corona spirit with boiling sun heart

SOUL_PALETTES = {
    'slider': {'O': '#102c5e', 'd': '#1f64c2', 'm': '#52b2ff', 'l': '#ade4ff', 'W': '#ffffff'},
    'valkyrie_queen': {'O': '#5c360c', 'd': '#b87818', 'm': '#f6c23e', 'l': '#ffefaa', 'W': '#ffffff'},
    'sun_spirit': {'O': '#541208', 'd': '#c83412', 'm': '#ff821e', 'l': '#ffd468', 'W': '#ffffff'},
}

SLIDER_SOUL_BASE = [
    "......d..d......",
    ".....dml.md.....",
    "..OO.dmmmmmd.OO.",
    "..OmddddddddmO..",
    "...ddmllllmdd...",
    "..OdmllWWllmdO..",
    "..OdmllWWllmdO..",
    "..ddmlWWWWlmdd..",
    "..ddmlWWWWlmdd..",
    "..OdmllWWllmdO..",
    "..OdmllllllmdO..",
    "...ddmmllmmdd...",
    "..OmddddddddmO..",
    "..OO..dddd..OO..",
    ".......dd.......",
    "................",
]

QUEEN_SOUL_BASE = [
    "....d..ll..d....",
    "....lm.WW.ml....",
    ".d..dmlWWlmd..d.",
    "dlm..dmllmd..mld",
    "dllm.dmlWWlmd.mlld",  # 18 chars -> fixed to 16 below
]

QUEEN_SOUL_BASE = [
    "....d..ll..d....",
    "....lm.WW.ml....",
    ".d..dmlWWlmd..d.",
    "dlm..dmllmd..mld",
    "dllmdmlWWlmdmlld",
    "dmllmmlWWlmmllmd",
    ".dmlmlWWWWlmlmd.",
    ".ddmllWWWWllmd..",  # 16 chars
    "..ddmlWWWWlmdd..",
    "...dmlWWWWlmd...",
    "....dmllllmd....",
    "....ddmmmmdd....",
    ".....dm..md.....",
    ".....d....d.....",
    "................",
    "................",
]
QUEEN_SOUL_BASE[7] = ".ddmllWWWWllmdd."

SUN_SOUL_BASE = [
    ".......ll.......",
    "..d....mm....d..",
    "..lm..dffd..ml..",  # use palette keys O,d,m,l,W
]
SUN_SOUL_BASE = [
    ".......ll.......",
    "..d....mm....d..",
    "..lm..dmmd..ml..",
    "...mldmllmdlm...",
    "....dmllllmd....",
    "..ddmlWWWWlmdd..",
    ".dmmllWWWWllmmd.",
    "lmmlWWWWWWWWlmml",
    "lmmlWWWWWWWWlmml",
    ".dmmllWWWWllmmd.",
    "..ddmlWWWWlmdd..",
    "....dmllllmd....",
    "...mldmllmdlm...",
    "..lm..dmmd..ml..",
    "..d....mm....d..",
    ".......ll.......",
]


def soul_frames(kind):
    pal = SOUL_PALETTES[kind]
    base = {
        'slider': SLIDER_SOUL_BASE,
        'valkyrie_queen': QUEEN_SOUL_BASE,
        'sun_spirit': SUN_SOUL_BASE,
    }[kind]
    frames = []
    for f in range(4):
        grid = [list(r) for r in base]
        if kind == 'slider':
            # Wisp tips flicker and corner monoliths pulse
            if f in (1, 2):
                grid[1][8] = 'l'
                grid[3][3] = grid[3][12] = 'l'
                grid[12][3] = grid[12][12] = 'l'
            if f in (2, 3):
                grid[0][7] = 'm'
                grid[6][6] = grid[6][9] = 'W'
                grid[9][6] = grid[9][9] = 'W'
        elif kind == 'valkyrie_queen':
            # Wing tips & crown shimmer
            if f in (1, 2):
                grid[3][1] = grid[3][14] = 'W'
                grid[4][2] = grid[4][13] = 'W'
                grid[0][7] = grid[0][8] = 'W'
            if f in (2, 3):
                grid[5][2] = grid[5][13] = 'W'
                grid[10][7] = grid[10][8] = 'W'
        elif kind == 'sun_spirit':
            # Cardinal and diagonal solar flares alternate
            if f in (1, 2):
                grid[2][7] = grid[2][8] = 'W'
                grid[13][7] = grid[13][8] = 'W'
                grid[7][2] = grid[8][2] = 'W'
                grid[7][13] = grid[8][13] = 'W'
            if f in (2, 3):
                grid[3][4] = grid[3][11] = 'W'
                grid[12][4] = grid[12][11] = 'W'
        frames.append(render([''.join(r) for r in grid], pal))
    return strip(frames)


def glow_texture(size=32):
    """Stepped radial halo with an 8-pointed arcane star/rune ring so the renderer's rotation is visible."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2.0
    r_max = size / 2.0
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy) / r_max
            if d >= 1.0:
                continue
            angle = math.atan2(dy, dx)
            star = 0.08 * math.cos(angle * 8)
            ring = 0.06 if 0.52 <= d <= 0.64 else 0.0
            eff = max(0.0, min(0.999, 1.0 - d + star + ring))
            band = int(eff * 6)
            a = [0, 38, 76, 124, 176, 224][band]
            if a:
                im.putpixel((x, y), (255, 255, 255, a))
    return im


def beam_texture():
    """Vertical ritual pillar with bright core and diagonal rune-light strands that ascend as the beam spins."""
    w, h = 16, 32
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        end = min(y, h - 1 - y) / (h * 0.22)
        vfade = min(1.0, end)
        for x in range(w):
            d = abs(x - 7.5) / 8.0
            strand = 0.12 if ((x + y // 2) % 4 == 0 and d < 0.75) else 0.0
            idx = min(4, max(0, int((d - strand) * 5)))
            band = [255, 210, 145, 80, 28][idx]
            a = int(band * vfade)
            if a:
                im.putpixel((x, y), (255, 255, 255, a))
    return im


# =====================================================================================================================
# 3D ISOMETRIC SOFTWARE RENDERER FOR VAULT PREVIEWS
# =====================================================================================================================

def render_vault_3d(model_json, tex_map, scale=8):
    """Renders the 3D block model in standard Minecraft GUI isometric projection (rot X=30, Y=225)."""
    w, h = 16 * scale * 2, 16 * scale * 2
    zbuf = np.full((h, w), -1e9, dtype=np.float64)
    out = np.zeros((h, w, 4), dtype=np.uint8)

    s45 = math.sqrt(0.5)
    rx = math.radians(30.0)
    cx, sx = math.cos(rx), math.sin(rx)

    def proj(pt):
        x, y, z = pt[0] - 8.0, pt[1] - 8.0, pt[2] - 8.0
        # Rotate Y by 225 deg so West (-X) is front-left and North (-Z) is front-right
        x1 = (x - z) * s45
        z1 = (-x - z) * s45
        y2 = y * cx - z1 * sx
        z2 = y * sx + z1 * cx
        px = w * 0.5 + x1 * scale * 1.15
        py = h * 0.5 - y2 * scale * 1.15
        return px, py, z2

    # Backface culling for camera at (-X, +Y, -Z): only 'up', 'north', and 'west' faces point toward camera
    visible_faces = {'up': 1.0, 'north': 0.86, 'west': 0.72}

    for el in model_json['elements']:
        x0, y0, z0 = el['from']
        x1, y1, z1 = el['to']
        faces = el['faces']
        corners = {
            'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            'west':  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            'up':    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        }
        for fname, fspec in faces.items():
            if fname not in visible_faces:
                continue
            tex_key = fspec['texture'].lstrip('#')
            tex_im = tex_map[tex_key]
            u0, v0, u1, v1 = fspec['uv']
            pts3 = corners[fname]
            steps_u = max(8, int(abs(u1 - u0) * scale * 2))
            steps_v = max(8, int(abs(v1 - v0) * scale * 2))
            shade = visible_faces[fname]
            for iv in range(steps_v):
                tv = (iv + 0.5) / steps_v
                ty = int(min(15, max(0, v0 + (v1 - v0) * tv)))
                for iu in range(steps_u):
                    tu = (iu + 0.5) / steps_u
                    tx = int(min(15, max(0, u0 + (u1 - u0) * tu)))
                    r, g, b, a = tex_im.getpixel((tx, ty))
                    if a == 0:
                        continue
                    p = [
                        (1 - tu) * (1 - tv) * pts3[0][k]
                        + tu * (1 - tv) * pts3[1][k]
                        + tu * tv * pts3[2][k]
                        + (1 - tu) * tv * pts3[3][k]
                        for k in range(3)
                    ]
                    px, py, pz = proj(p)
                    ix, iy = int(round(px)), int(round(py))
                    if 0 <= ix < w and 0 <= iy < h and pz >= zbuf[iy, ix]:
                        zbuf[iy, ix] = pz
                        out[iy, ix] = (int(r * shade), int(g * shade), int(b * shade), 255)
    return Image.fromarray(out, 'RGBA')


# =====================================================================================================================
# BUILD & CHECK
# =====================================================================================================================

def build_all():
    tex_out = {}
    for tier in TIERS:
        for path, im in vault_textures(tier).items():
            tex_out[path] = (im, None)
    for name in ITEMS:
        im, meta, _ = item_texture(name)
        tex_out[f'item/{name}'] = (im, meta)
    for kind in SOUL_PALETTES:
        tex_out[f'entity/guardian_soul_{kind}'] = (soul_frames(kind), None)
    tex_out['entity/guardian_soul_glow'] = (glow_texture(), None)
    tex_out['entity/guardian_soul_beam'] = (beam_texture(), None)

    model_out = {
        'template_dungeon_vault.json': build_template_vault_model(False),
        'template_dungeon_vault_ejecting.json': build_template_vault_model(True),
    }
    for tier in TIERS:
        model_out.update(build_tier_block_models(tier))
    return tex_out, model_out


def png_bytes(im):
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=True)
    return buf.getvalue()


def json_text(obj):
    return json.dumps(obj, indent=2) + '\n'


def write_previews(tex_out, model_out):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    scale = 8
    # 1. Vault 2D face sheet
    order = ['bottom', 'side', 'side_active', 'front', 'front_active', 'front_unlocking', 'top', 'top_ejecting']
    sheet = Image.new('RGBA', (len(order) * (16 * scale + 6), len(TIERS) * (16 * scale + 6)), (34, 38, 46, 255))
    for row, tier in enumerate(TIERS):
        for col, face in enumerate(order):
            im = tex_out[f'block/{tier}_vault_{face}'][0].resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.alpha_composite(im, (col * (16 * scale + 6), row * (16 * scale + 6)))
    sheet.save(PREVIEW / 'vaults.png')

    # 2. Vault 3D isometric renders (idle, active, ejecting for each tier)
    states = [
        ('idle', model_out['template_dungeon_vault.json'], 'front', 'side', 'top'),
        ('active', model_out['template_dungeon_vault.json'], 'front_active', 'side_active', 'top'),
        ('ejecting', model_out['template_dungeon_vault_ejecting.json'], 'front_unlocking', 'side_active', 'top_ejecting'),
    ]
    cell_w = 16 * 6 * 2
    sheet3d = Image.new('RGBA', (len(states) * cell_w, len(TIERS) * cell_w), (28, 32, 40, 255))
    for row, tier in enumerate(TIERS):
        for col, (_, mdl, f_front, f_side, f_top) in enumerate(states):
            tmap = {
                'bottom': tex_out[f'block/{tier}_vault_bottom'][0],
                'front': tex_out[f'block/{tier}_vault_{f_front}'][0],
                'side': tex_out[f'block/{tier}_vault_{f_side}'][0],
                'top': tex_out[f'block/{tier}_vault_{f_top}'][0],
            }
            iso = render_vault_3d(mdl, tmap, scale=6)
            sheet3d.alpha_composite(iso, (col * cell_w, row * cell_w))
    sheet3d.save(PREVIEW / 'vaults_3d.png')

    # 3. Items: every frame on inventory slot grey
    names = list(ITEMS)
    frames = {n: item_texture(n)[2] for n in names}
    cols = max(len(f) for f in frames.values())
    sheet = Image.new('RGBA', (cols * (16 * scale + 6), len(names) * (16 * scale + 6)), (139, 139, 139, 255))
    for row, n in enumerate(names):
        for col, im in enumerate(frames[n]):
            sheet.alpha_composite(im.resize((16 * scale, 16 * scale), Image.NEAREST), (col * (16 * scale + 6), row * (16 * scale + 6)))
    sheet.save(PREVIEW / 'items.png')

    # 4. Souls: frames over the tinted glow
    sheet = Image.new('RGBA', (4 * (32 * 4 + 6), len(SOUL_PALETTES) * (32 * 4 + 6)), (24, 26, 32, 255))
    glow = tex_out['entity/guardian_soul_glow'][0]
    for row, kind in enumerate(SOUL_PALETTES):
        tint = rgba(SOUL_PALETTES[kind]['m'])
        tinted = Image.new('RGBA', glow.size)
        for y in range(glow.size[1]):
            for x in range(glow.size[0]):
                a = glow.getpixel((x, y))[3]
                tinted.putpixel((x, y), (tint[0], tint[1], tint[2], int(a * 0.75)))
        frames_im = tex_out[f'entity/guardian_soul_{kind}'][0]
        for col in range(4):
            cell = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
            cell.alpha_composite(tinted)
            cell.alpha_composite(frames_im.crop((0, col * 16, 16, col * 16 + 16)).resize((18, 18), Image.NEAREST), (7, 7))
            sheet.alpha_composite(cell.resize((32 * 4, 32 * 4), Image.NEAREST), (col * (32 * 4 + 6), row * (32 * 4 + 6)))
    sheet.save(PREVIEW / 'souls.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='verify the exported textures and models instead of writing them')
    args = parser.parse_args()
    tex_out, model_out = build_all()
    bad = []
    for path, (im, meta) in tex_out.items():
        png = TEX / f'{path}.png'
        mcmeta = TEX / f'{path}.png.mcmeta'
        if args.check:
            if not png.exists() or png.read_bytes() != png_bytes(im):
                bad.append(str(png))
            if meta is not None and (not mcmeta.exists() or mcmeta.read_text(encoding='utf8') != json_text(meta)):
                bad.append(str(mcmeta))
            continue
        png.parent.mkdir(parents=True, exist_ok=True)
        png.write_bytes(png_bytes(im))
        if meta is not None:
            mcmeta.write_text(json_text(meta), encoding='utf8', newline='\n')
    for name, mdl in model_out.items():
        mpath = MODELS / name
        if args.check:
            if not mpath.exists() or mpath.read_text(encoding='utf8') != json_text(mdl):
                bad.append(str(mpath))
            continue
        mpath.parent.mkdir(parents=True, exist_ok=True)
        mpath.write_text(json_text(mdl), encoding='utf8', newline='\n')
    if args.check:
        if bad:
            print('out of date:\n  ' + '\n  '.join(bad))
            sys.exit(1)
        print(f'{len(tex_out)} dungeon textures and {len(model_out)} vault models up to date')
        return
    write_previews(tex_out, model_out)
    print(f'wrote {len(tex_out)} dungeon textures and {len(model_out)} vault models, previews in {PREVIEW}')


if __name__ == '__main__':
    main()
