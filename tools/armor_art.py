"""Valkyrie Mage and Phoenix Mage armour: GeckoLib geo, idle animation, animated atlas, capes and item icons.

Everything here is hand-placed pixel art: every character in the maps below is one deliberate texel (no noise, no
dithering, no smooth gradients). Requires Pillow and numpy. Writes straight into the assets:

    geo/<set>_armor.geo.json                     bones + cubes (box UV, 128x128 atlas)
    animations/<set>_armor.animation.json        "idle" loop (helm wings / flame crest / plumes / ankle wings sway)
    textures/models/armor/<set>.png (+ .mcmeta)   atlas, N frames stacked vertically (GeckoLib AnimatableTexture)
    textures/models/armor/<set>_cape.png (+ .mcmeta)  vanilla 64x32 cape layout, frames stacked (MageCapeLayer)
    textures/item/<set>_{helmet,chestplate,leggings,boots}.png (+ .mcmeta where animated)

with <set> = valkyrie_mage | phoenix_mage, and previews in build/art_preview/armor/ (software render of both sets on
a mannequin from the front, side and back, the atlas frames, a GIF of the texture animation, the icons next to the
Aether / Iron's Spells / vanilla references).

Run:   python tools/armor_art.py            (export + previews)
       python tools/armor_art.py --check    (re-render in memory, verify the exported files are identical)
       python tools/armor_art.py --dry      (previews only, assets untouched)

Texture animation (frame 0 is the resting look)
- valkyrie: 5 frames; frame 0 holds 70 ticks, then a 2-tick-per-frame glint sweeps the sapphires (brow, plastron,
  buckle, cape emblem) and the vambrace / greave runes glow blue.
- phoenix: 8 frames at 3 ticks; frames 0-3 are the flame flicker (crest, boot flames, cape hem; silhouettes may move,
  they are flames), frames 4-7 repeat it with the opals flared and the vambrace embers lit (5 flicker loops, 2 flared).
- Armour atlases animate through GeoArmorRenderer (AnimatableTexture.setAndUpdate). The capes are 64x32 frames
  ("width"/"height" in the mcmeta) and need MageCapeLayer to call AnimatableTexture.setAndUpdate before getBuffer.
- Icons keep their alpha outline on every frame (gem glint, crest / flame tones only).

Conventions
- Bedrock geometry: units are texels, +Y up, the wearer faces -Z, the wearer's right is -X. Box UV nets are packed
  into the atlas by the script. A cube net is [right | front | left | back] under [top | bottom]; on the right face
  column 0 is the back edge, on the left face column 0 is the front edge, on the front face column 0 is the wearer's
  right, on the back face column 0 is the wearer's left, on the top face row 0 is the back edge.
- Bone names follow GeckoLib's armour renderer and Iron's Spells' GenericCustomArmorRenderer: armorHead, armorBody,
  armorRightArm/LeftArm, armorRightLeg/LeftLeg, armorRightBoot/LeftBoot, armorLeggingTorsoLayer (leggings, follows the
  body) and armorTorsoExtensionRight/LeftLeg (chestplate, follows the legs).
- Kept from earlier in-game iterations: the chest body (inflate 1.01) never encloses the leggings' sash (a 10x3x6
  cube, inflate 0.1, over the chest hem) so the leggings stay visible; no skirt over the thighs (1px hip tassets and a
  back coat-tail only); boots are 5 rows tall; no cube face is coplanar with another one (no z-fighting).
- Wings, flame crest, plumes and ankle feathers are zero-thickness planes (as in Iron's Spells' hats). Both faces of a
  plane are painted so every texel shows the same colour from either side.
- Each plate face gets a 1px bevel after mirroring (top/left edges one step lighter, bottom/right one step darker),
  applied only to the base tone of the listed materials, so hand-placed trims and gems are never touched.
- Palettes: o s m l h w = main plate (dark..specular), d g G Y = gold, k b B c = cloth, p P q W = gem (rim, body, facet,
  glint), 1-5 = feathers / flame (dark..bright), r R = glowing rune, ' ' = transparent, '.' = keep the layer below.
"""
import argparse
import io
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
PREVIEW = ROOT / 'build/art_preview/armor'
REFS = Path(r'C:/Users/suiyi/AppData/Local/Temp/asb_ref')       # only used for the preview comparison sheet
ATLAS = 128
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')
SIDES = ('right', 'front', 'left', 'back')


def palette(spec):
    return {k: tuple(bytes.fromhex(v)) + (255,) for k, v in spec.items()}


# ============================================================================ palettes (hue-shifted ramps)
VALK = palette({
    # white/silver plate: cool blue-violet shadows, warm white highlights
    'o': '2c3358', 's': '5b6690', 'm': '919cc0', 'l': 'c4cce2', 'h': 'e6eaf4', 'w': 'fffdf6',
    # gold trim
    'd': '6a3a12', 'g': 'b0701e', 'G': 'e6ad3a', 'Y': 'fde58c',
    # sky-blue cloth
    'k': '142452', 'b': '1f3c86', 'B': '2e5ab0', 'c': '4a84d4', 'C': '78acec',
    # sapphire
    'p': '0e246a', 'P': '2468d4', 'q': '6cc4ff', 'W': 'ffffff',
    # white feathers
    '1': '515e8c', '2': '8b98c0', '3': 'bfcae4', '4': 'e3e9f6', '5': 'ffffff',
    # rune glow
    'r': '5cb4f4', 'R': 'c8f0ff',
})
PHX = palette({
    # crimson plate: wine shadows, orange highlights
    'o': '220810', 's': '4a0f18', 'm': '721a1e', 'l': '9c2a22', 'h': 'c8442a', 'w': 'f27a3c',
    # warm gold
    'd': '5c2808', 'g': 'a65614', 'G': 'e09226', 'Y': 'ffd660',
    # smouldering cloth (dark wine)
    'k': '1a0810', 'b': '33101a', 'B': '4e1a20', 'c': '6c2626', 'C': '9c4028',
    # fire opal
    'p': '6c1404', 'P': 'd84c0c', 'q': 'ffa832', 'W': 'fff6d2',
    # flame: dark red -> white-hot
    '1': '7e1406', '2': 'c8320c', '3': 'f27014', '4': 'ffb830', '5': 'fff2a6',
    # ember glow
    'r': 'ff7a1c', 'R': 'ffe080',
})


# ============================================================================ grid helpers
def solid(fw, fh, ch):
    return [ch * fw for _ in range(fh)]


def hflip(rows):
    return [r[::-1] for r in rows]


def overlay(base, top, ox=0, oy=0):
    """Paint `top` over `base` at (ox, oy); '.' in `top` keeps the base."""
    out = [list(r) for r in base]
    for y, row in enumerate(top):
        for x, ch in enumerate(row):
            X, Y = x + ox, y + oy
            if ch != '.' and 0 <= Y < len(out) and 0 <= X < len(out[Y]):
                out[Y][X] = ch
    return [''.join(r) for r in out]


def resolve(spec, fw, fh, frame):
    """A face paint: a char, a list of rows, a callable (fw, fh, frame) -> rows, or a tuple of layers."""
    if spec is None:
        return solid(fw, fh, ' ')
    if isinstance(spec, str):
        return solid(fw, fh, spec)
    if callable(spec):
        return resolve(spec(fw, fh, frame), fw, fh, frame)
    if isinstance(spec, tuple):
        rows = solid(fw, fh, ' ')
        for layer in spec:
            rows = overlay(rows, resolve(layer, fw, fh, frame))
        return rows
    rows = list(spec)
    if all(len(r) == 1 for r in rows):          # one char per row: a horizontal band pattern of any width
        rows = [r * fw for r in rows]
    assert len(rows) == fh and all(len(r) == fw for r in rows), f'map {fw}x{fh} expected, got {rows}'
    return rows


def at(rows, ox=0, oy=0):
    """A small map stamped at an offset (negative = from the right / bottom)."""
    def paint(fw, fh, f):
        r = rows(fw, fh, f) if callable(rows) else rows
        x = ox if ox >= 0 else fw - len(r[0]) + ox + 1
        y = oy if oy >= 0 else fh - len(r) + oy + 1
        return overlay(solid(fw, fh, '.'), r, x, y)
    return paint


def anim_ch(seq):
    """A single animated texel char per frame (e.g. a rune that glows on frames 2-4)."""
    return lambda f: seq[f % len(seq)]


# ============================================================================ model description
class Cube:
    def __init__(self, origin, size, inflate=0.0, faces=None, bevel=(), mirror=False, note=''):
        self.origin, self.size, self.inflate = list(origin), list(size), inflate
        self.faces = faces or {}
        self.bevel, self.mirror, self.note = list(bevel), mirror, note
        self.uv = None

    def mirrored(self):
        (x, y, z), (w, h, d) = self.origin, self.size
        return Cube((-(x + w), y, z), (w, h, d), self.inflate, self.faces, self.bevel, not self.mirror, self.note)

    def dims(self, face):
        w, h, d = self.size
        return {'top': (w, d), 'bottom': (w, d), 'right': (d, h), 'left': (d, h), 'front': (w, h), 'back': (w, h)}[face]

    def net_size(self):
        w, h, d = self.size
        return 2 * (w + d), d + h

    def face_spec(self, face):
        for key in (face, 'sides' if face in SIDES else 'caps', 'all'):
            if key in self.faces:
                return self.faces[key]
        return None

    def grids(self, frame):
        out = {}
        for f in FACES:
            fw, fh = self.dims(f)
            if fw == 0 or fh == 0:
                continue
            out[f] = resolve(self.face_spec(f), fw, fh, frame)
        if self.mirror:
            swap = {'right': 'left', 'left': 'right'}
            out = {swap.get(f, f): hflip(g) for f, g in out.items()}
        for f, g in out.items():
            out[f] = bevel(g, self.bevel, f)
        return out


def bevel(rows, specs, face):
    """1px plate bevel on the base tone of each (base, light, dark[, faces]) material: top/left lit, bottom/right dark."""
    fh, fw = len(rows), len(rows[0])
    if fw < 3 or fh < 3 or not specs:
        return rows
    out = [list(r) for r in rows]
    for spec in specs:
        base, hi, lo = spec[:3]
        if len(spec) > 3 and face not in spec[3]:
            continue
        for y in range(fh):
            for x in range(fw):
                if rows[y][x] != base:
                    continue
                if (y == 0 and x < fw - 1) or (x == 0 and y < fh - 1):
                    out[y][x] = hi
                elif y == fh - 1 or x == fw - 1:
                    if not (y == fh - 1 and x == 0) and not (x == fw - 1 and y == 0):
                        out[y][x] = lo
    return [''.join(r) for r in out]


def xplane(rows):
    """Faces of a zero-width (YZ) plane: `rows` as seen from the wearer's right; the other face is its mirror."""
    return {'right': rows, 'left': lambda fw, fh, f: hflip(resolve(rows, fw, fh, f))}


def zplane(rows):
    """Faces of a zero-depth (XY) plane: `rows` as seen from the front; the back face is its mirror."""
    return {'front': rows, 'back': lambda fw, fh, f: hflip(resolve(rows, fw, fh, f))}


class Bone:
    def __init__(self, name, parent, pivot, rotation=(0, 0, 0), cubes=(), anim=None):
        self.name, self.parent, self.pivot, self.rotation = name, parent, list(pivot), list(rotation)
        self.cubes, self.anim = list(cubes), anim

    def mirrored(self):
        rx, ry, rz = self.rotation
        anim = None
        if self.anim:
            anim = {'length': self.anim['length'], 'keys': [(t, (ax, -ay, -az)) for t, (ax, ay, az) in self.anim['keys']]}
        return Bone(self.name.replace('Right', 'Left'), self.parent.replace('Right', 'Left') if self.parent else None,
                    (-self.pivot[0], self.pivot[1], self.pivot[2]), (rx, -ry, -rz), [c.mirrored() for c in self.cubes], anim)


def sway(length, *channels):
    """Idle keyframes summed from sines: channels = (axis 0/1/2, amplitude in degrees, cycles per loop, phase)."""
    steps = 24
    keys = []
    for i in range(steps + 1):
        v = [0.0, 0.0, 0.0]
        for axis, amp, cycles, phase in channels:
            v[axis] += amp * math.sin(2 * math.pi * cycles * i / steps + phase)
        keys.append((round(length * i / steps, 4), tuple(round(a, 3) + 0.0 for a in v)))
    return {'length': length, 'keys': keys}


def skeleton():
    return [
        Bone('bipedHead', None, (0, 24, 0)), Bone('armorHead', 'bipedHead', (0, 24, 0)),
        Bone('bipedBody', None, (0, 24, 0)), Bone('armorBody', 'bipedBody', (0, 24, 0)),
        Bone('armorLeggingTorsoLayer', 'bipedBody', (0, 24, 0)),
        Bone('bipedRightArm', None, (-5, 22, 0)), Bone('armorRightArm', 'bipedRightArm', (-5, 22, 0)),
        Bone('bipedLeftArm', None, (5, 22, 0)), Bone('armorLeftArm', 'bipedLeftArm', (5, 22, 0)),
        Bone('bipedRightLeg', None, (-2, 12, 0)), Bone('armorRightLeg', 'bipedRightLeg', (-2, 12, 0)),
        Bone('armorRightBoot', 'bipedRightLeg', (-2, 12, 0)),
        Bone('bipedLeftLeg', None, (2, 12, 0)), Bone('armorLeftLeg', 'bipedLeftLeg', (2, 12, 0)),
        Bone('armorLeftBoot', 'bipedLeftLeg', (2, 12, 0)),
        Bone('armorTorsoExtensionRightLeg', None, (-2, 12, 0)), Bone('armorTorsoExtensionLeftLeg', None, (2, 12, 0)),
    ]


SLOT_OF = {'armorHead': 'head', 'armorBody': 'chest', 'armorRightArm': 'chest', 'armorLeftArm': 'chest',
           'armorTorsoExtensionRightLeg': 'chest', 'armorTorsoExtensionLeftLeg': 'chest',
           'armorLeggingTorsoLayer': 'legs', 'armorRightLeg': 'legs', 'armorLeftLeg': 'legs',
           'armorRightBoot': 'feet', 'armorLeftBoot': 'feet'}


class ArmorModel:
    def __init__(self, name, pal, nframes, mcmeta_frames, frametime):
        self.name, self.pal, self.nframes = name, pal, nframes
        self.mcmeta_frames, self.frametime = mcmeta_frames, frametime
        self.bones = skeleton()

    def bone(self, name):
        return next(b for b in self.bones if b.name == name)

    def add(self, bone_name, *cubes, mirror=False):
        self.bone(bone_name).cubes.extend(cubes)
        if mirror:
            self.bone(bone_name.replace('Right', 'Left')).cubes.extend(c.mirrored() for c in cubes)

    def add_bone(self, bone, mirror=False):
        self.bones.append(bone)
        if mirror:
            self.bones.append(bone.mirrored())

    def slot(self, bone):
        while bone.name not in SLOT_OF:
            bone = self.bone(bone.parent)
        return SLOT_OF[bone.name]

    # ---------------------------------------------------------------- texture
    def pack(self):
        """Shelf-pack the cube nets (tallest first); deterministic, so every frame shares the same layout."""
        cubes = [c for b in self.bones for c in b.cubes]
        order = sorted(range(len(cubes)), key=lambda i: (-cubes[i].net_size()[1], -cubes[i].net_size()[0], i))
        x = y = row_h = 0
        for i in order:
            w, h = cubes[i].net_size()
            if x + w > ATLAS:
                x, y, row_h = 0, y + row_h, 0
            cubes[i].uv = (x, y)
            x += w
            row_h = max(row_h, h)
        assert y + row_h <= ATLAS, f'{self.name}: atlas overflow ({y + row_h}px)'
        self.used_height = y + row_h

    def atlas(self, frame):
        img = Image.new('RGBA', (ATLAS, ATLAS), (0, 0, 0, 0))
        px = img.load()
        for b in self.bones:
            for c in b.cubes:
                paint_net(px, c, c.grids(frame), self.pal)
        return img

    def strip(self):
        frames_ = [self.atlas(f) for f in range(self.nframes)]
        out = Image.new('RGBA', (ATLAS, ATLAS * self.nframes), (0, 0, 0, 0))
        for i, fr in enumerate(frames_):
            out.paste(fr, (0, ATLAS * i))
        return out, frames_

    # ---------------------------------------------------------------- json
    def geo(self):
        bones = []
        for b in self.bones:
            entry = {'name': b.name}
            if b.parent:
                entry['parent'] = b.parent
            entry['pivot'] = [num(v) for v in b.pivot]
            if any(b.rotation):
                entry['rotation'] = [num(v) for v in b.rotation]
            if b.cubes:
                entry['cubes'] = []
                for c in b.cubes:
                    cube = {'origin': [num(v) for v in c.origin], 'size': [num(v) for v in c.size], 'uv': list(c.uv)}
                    if c.inflate:
                        cube['inflate'] = num(c.inflate)
                    entry['cubes'].append(cube)
            bones.append(entry)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{self.name}_armor', 'texture_width': ATLAS, 'texture_height': ATLAS,
                            'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 1.75, 0]},
            'bones': bones}]}

    def animation(self):
        bones, length = {}, None
        for b in self.bones:
            if b.anim:
                assert length in (None, b.anim['length']), f'{self.name}: every idle channel must share one loop length'
                length = b.anim['length']
                bones[b.name] = {'rotation': {f'{t:g}': [num(a) for a in v] for t, v in b.anim['keys']}}
        return {'format_version': '1.8.0', 'animations': {'idle': {'loop': True, 'animation_length': length, 'bones': bones}},
                'geckolib_format_version': 2}

    def mcmeta(self):
        return mcmeta(self.mcmeta_frames, self.frametime)


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def net_rects(w, h, d):
    return {'top': (d, 0, w, d), 'bottom': (d + w, 0, w, d), 'right': (0, d, d, h),
            'front': (d, d, w, h), 'left': (d + w, d, d, h), 'back': (2 * d + w, d, w, h)}


def paint_net(px, cube, grids, pal):
    u, v = cube.uv
    rects = net_rects(*cube.size)
    for f, rows in grids.items():
        x0, y0, fw, fh = rects[f]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch not in (' ', '.'):
                    px[u + x0 + x, v + y0 + y] = pal[ch]


def mcmeta(frame_list, frametime, width=None, height=None):
    anim = {'frametime': frametime, 'interpolate': False}
    if width:
        anim['width'], anim['height'] = width, height
    anim['frames'] = frame_list
    return {'animation': anim}


# ============================================================================ shared pieces
def gem2(rest='qP|Pp', seq=None):
    """A 2x2 gem (rim, body, facet) whose glint sweeps across on the animated frames."""
    base = rest.split('|')
    seq = seq or {}
    return lambda fw, fh, f: seq.get(f, base)


VALK_GEM = {1: ['WP', 'Pp'], 2: ['Wq', 'qp'], 3: ['qW', 'Pq'], 4: ['qq', 'Pp']}
VALK_RUNE = 'mmrRr'             # engraved groove -> glow on frames 2-4
VALK_N, VALK_FRAMES = 5, [{'index': 0, 'time': 70}, 1, 2, 3, 4]   # rest ~3.5 s, then a 0.4 s glint
PHX_N = 8                       # frames 0-3 flame flicker, 4-7 the same flicker with the embers / gems flared
PHX_FRAMES = [0, 1, 2, 3] * 5 + [4, 5, 6, 7] * 2
PLATE_V = [('l', 'h', 'm'), ('G', 'Y', 'g'), ('B', 'c', 'b')]
PLATE_P = [('l', 'h', 'm'), ('G', 'Y', 'g'), ('B', 'c', 'b')]


def fringe(top, rows, tip, width=3):
    """Feathered band for any face width: a `top` trim row, feather rows (one (hi, mid, lo) triple per row,
    `width`-px feathers) and a row of pointed tips (only each feather's middle texel)."""
    def paint(fw, fh, f):
        off = (fw % width) // 2 + 1
        out = [top * fw]
        for tri in rows:
            out.append(''.join(tri[(x + off) % width] for x in range(fw)))
        out.append(''.join(tip if (x + off) % width == 1 else ' ' for x in range(fw)))
        assert len(out) == fh, (fw, fh, len(out))
        return out
    return paint


def flick(*maps):
    """Flame paint cycling through 4 maps (frame % 4); used on the phoenix set's flames."""
    return lambda fw, fh, f: maps[f % 4]


# ============================================================================ Valkyrie Mage
VALK_WING = ['5.........',
             '45........',
             '445..5....',
             '3445.45...',
             '23445445..',
             '.2344344.5',
             '..2334345.',
             '...233445.',
             '....2dgGYg',
             '.....ddgg.']
VALK_HEEL = ['..4.5.',
             '.43453',
             '434353',
             '.2GGYg']


def valkyrie_mage():
    m = ArmorModel('valkyrie_mage', VALK, VALK_N, VALK_FRAMES, 2)
    rune = anim_ch(VALK_RUNE)

    def runed(rows, mark='*'):
        return lambda fw, fh, f: [r.replace(mark, rune(f)) for r in rows]

    gem = gem2(seq=VALK_GEM)
    # ------------------------------------------------------------ helm: winged sky-helm, open face, sapphire brow
    side = ['llllllll',
            'llllllll',
            'dgGGGGGG',
            'llllllll',
            'lllllwll',
            'lllllmll',
            'ssssslll',
            'dggggggG']
    m.add('armorHead',
          Cube((-4, 24, -4), (8, 8, 8), 1.0, bevel=PLATE_V, note='helm shell', faces={
              'top': ['lllGGlll',
                      'lllGYlll',
                      'lllGYlll',
                      'lhhGYlll',
                      'lhwGYlll',
                      'lhhGYlll',
                      'lllGYlll',
                      'lllgGlll'],
              'front': ['lllGYlll',
                        'lhlgGlhl',
                        'dGGGYGGd',
                        'l      s',
                        'l      s',
                        'lG    gs',
                        'lg    ds',
                        'gG    Gd'],
              'right': side, 'left': hflip(side),
              'back': ['lllGGlll',
                       'lllGYlll',
                       'dGGGYGGd',
                       'lllgGlll',
                       'lllgGlll',
                       'lllgGlll',
                       'ssssssss',
                       'dgggGggd'],
              'bottom': 's'}),
          Cube((-0.5, 33, -4.5), (1, 1, 9), note='crest ridge', faces={
              'top': ['G'] + ['Y'] * 7 + ['G'], 'right': ['gGGGGGGGG'], 'left': ['GGGGGGGGg'], 'all': 'G', 'bottom': ' '}),
          Cube((-1, 29, -5.5), (2, 2, 1), note='brow sapphire', faces={'front': gem, 'top': 'G', 'all': 'g'}))
    m.add('armorHead', Cube((-5.5, 28.5, -1.5), (1, 2, 3), note='wing root', faces={
        'right': ['GYG', 'dgg'], 'front': ['G', 'g'], 'back': ['g', 'd'], 'top': 'Y', 'bottom': 'd', 'left': 'g'}), mirror=True)
    m.add_bone(Bone('helmWingRight', 'armorHead', (-5.4, 29, 0), (15, -45, -8), [
        Cube((-5.4, 28, -1.5), (0, 10, 10), note='helm wing', faces=xplane(VALK_WING))],
        anim=sway(3.0, (1, 6, 1, 0), (0, 2.5, 2, 0.6))), mirror=True)

    # ------------------------------------------------------------ chest: plate over a blue tunic, raised plastron
    m.add('armorBody',
          Cube((-4, 12, -2), (8, 12, 4), 1.01, bevel=PLATE_V, note='breastplate', faces={
              'front': ['lGkbbkGl',
                        'lgGkkGgl',
                        'llgGGgll',
                        'lsllllsl',
                        'llllllll',
                        'llllllll',
                        'slllllls',
                        'dGGYYGGd',
                        'bBBcBBBb',
                        'bBcBBcBb',
                        'kbBBBBbk',
                        'dggGGggd'],
              'back': ['lllGGlll',
                       'lllGYlll',
                       'lllgGlll',
                       'ssslGsss',
                       'lllgGlll',
                       'lllgGlll',
                       'slllllls',
                       'dGGYYGGd',
                       'bBBcBBBb',
                       'bBcBBcBb',
                       'kbBBBBbk',
                       'dggGGggd'],
              'sides': ['llll', 'llll', 'lwll', 'lmll', 'llll', 'llll', 'slls', 'dGGG', 'bBBB', 'bBcB', 'kbBb', 'dggg'],
              'top': ['lGGGGGGl', 'Gk    kG', 'Gk    kG', 'lGGGGGGl'],
              'bottom': 'k'}),
          Cube((-3, 17, -3.65), (6, 5, 1), note='plastron', faces={
              'front': (['gGYYGg',
                         'GlhhlG',
                         'Glqqlg',
                         'Glqqlg',
                         'dggggd'], at(gem, 2, 2)),
              'top': 'Y', 'sides': 'g', 'bottom': 'd'}))
    # ------------------------------------------------------------ arms: blue sleeve, gold elbow, runed vambrace, cuff
    arm = ['llll', 'llll', 'llll', 'bBBb', 'BcBB', 'BcBb', 'dGGg', 'llll', 'l**l', 'llll', 'GGGG', 'slls']
    m.add('armorRightArm',
          Cube((-8, 12, -2), (4, 12, 4), 1.0, bevel=PLATE_V, note='sleeve + vambrace', faces={
              'sides': runed(arm), 'top': 'l', 'bottom': 's'}),
          Cube((-9, 12, -3), (6, 2, 6), 0.25, note='cuff', faces={'sides': ['G', 'g'], 'top': 'G', 'bottom': 'd'}),
          mirror=True)
    m.add_bone(Bone('pauldronRight', 'armorRightArm', (-5, 23, 0), (0, 0, -10), [
        Cube((-9.5, 22.5, -3.5), (6, 3, 7), bevel=PLATE_V, note='pauldron dome', faces={
            'top': ['Gllllm', 'Glhhlm', 'Glhwlm', 'Glhhlm', 'Glhllm', 'Gllllm', 'GGGGGG'],
            'sides': ['l', 'l', 'G'], 'right': ['lllllll', 'lYlllYl', 'GGGGGGG'], 'bottom': 's'}),
        Cube((-10, 19.5, -3.5), (5, 3, 7), note='feathered lame', faces={
            'sides': fringe('G', ['543'], '4'), 'top': 'G', 'bottom': ' '})]), mirror=True)
    # ------------------------------------------------------------ leggings: blue sash over the hem, breeches, greaves
    m.add('armorLeggingTorsoLayer',
          Cube((-5, 11, -3), (10, 3, 6), 0.1, note='sash', faces={
              'sides': ['G', 'B', 'g'], 'front': ['GGGGYYGGGG', 'BcBBBBBBcB', 'gggggggggg'], 'top': 'G', 'bottom': 'g'}),
          Cube((-1.5, 11.3, -3.75), (3, 2, 1), note='buckle', faces={
              'front': lambda fw, fh, f: ['GYG', 'g' + {1: 'q', 2: 'W', 3: 'q'}.get(f, 'P') + 'g'],
              'top': 'Y', 'sides': 'g', 'bottom': 'd'}))
    leg = ['BBBB', 'BcBB', 'BcBb', 'bcBb', 'BcBB', 'bBBb', 'dGGg', 'llll', 'l*ll', 'llll', 'llll', 'slls']
    m.add('armorRightLeg',
          Cube((-4, 0, -2), (4, 12, 4), 0.5, bevel=PLATE_V, note='breeches + greave', faces={
              'sides': runed(leg), 'right': runed(overlay(leg, ['.G..'] * 6)), 'top': 'B', 'bottom': 's'}),
          Cube((-4.2, 6.4, -2.95), (4, 2, 1), note='knee cop', faces={
              'front': ['lhhl', 'gGGg'], 'top': 'h', 'sides': 'l', 'bottom': 'g'}),
          mirror=True)
    m.add('armorTorsoExtensionRightLeg',
          Cube((-5.55, 8.5, -2.5), (1, 3, 5), bevel=PLATE_V, note='hip tasset', faces={
              'right': ['lllll', 'lllll', 'GGGGG'], 'sides': ['l', 'l', 'G'], 'top': 'G', 'left': 'b', 'bottom': 'g'}),
          mirror=True)
    # ------------------------------------------------------------ boots: silver, gold-rimmed cuff, gold toe cap, heel wings
    m.add('armorRightBoot',
          Cube((-4, 0, -2), (4, 5, 4), 0.75, bevel=PLATE_V, note='boot', faces={
              'sides': ['llll', 'llll', 'llll', 'llll', 'oooo'], 'top': 'l', 'bottom': 'o'}),
          Cube((-5, 4, -3), (6, 2, 6), note='boot cuff', faces={'sides': ['G', 'l'], 'top': 'G', 'bottom': 'm'}),
          Cube((-4, 0, -3), (4, 2, 1), 0.25, note='toe cap', faces={
              'front': ['GYYG', 'dggd'], 'top': 'Y', 'sides': 'g', 'bottom': 'd'}),
          mirror=True)
    m.add_bone(Bone('bootWingRight', 'armorRightBoot', (-5.2, 2.5, 0.5), (0, -45, -12), [
        Cube((-5.2, 1.5, -0.5), (0, 4, 6), note='heel wing', faces=xplane(VALK_HEEL))],
        anim=sway(3.0, (1, 5, 2, 1.0))), mirror=True)
    return m


# ============================================================================ Phoenix Mage
PHX_OPAL = {4: ['Wq', 'qp'], 5: ['Wq', 'qp'], 6: ['qW', 'Pq'], 7: ['qW', 'Pq']}
PHX_CREST = (['...2...', '..242..', '2.343.2', '3234323', '3345433', '4455544', 'gGGYGGg'],
             ['..2....', '..32...', '2.343..', '323432.', '3345433', '4455544', 'gGGYGGg'],
             ['....2..', '...23..', '.2343.2', '.234323', '3345433', '4455544', 'gGGYGGg'],
             ['...1...', '..222..', '2.242.2', '3234323', '3345433', '4455544', 'gGGYGGg'])
PHX_CREST_SIDE = (['.2....', '.32.2.', '2342.3', '334323', '344543', '445554', 'gGGYGg'],
                  ['2.....', '32..2.', '342.32', '334323', '344543', '445554', 'gGGYGg'],
                  ['.2..2.', '.32.3.', '23423.', '334323', '344543', '445554', 'gGGYGg'],
                  ['......', '.2..2.', '2342.3', '334323', '344543', '445554', 'gGGYGg'])
PHX_PLUME = ['...2334455G',
             '.12334455YG',
             '12334445555',
             '.123344554g',
             '...12334...']
PHX_ANKLE = ['..3.4.',
             '.34453',
             '343453',
             '.2gGYg']


def phoenix_mage():
    m = ArmorModel('phoenix_mage', PHX, PHX_N, PHX_FRAMES, 3)
    opal = gem2(seq=PHX_OPAL)

    def embered(rows, mark='*'):
        return lambda fw, fh, f: [r.replace(mark, 'r' if f >= 4 else '2') for r in rows]

    side = ['llllllll',
            'llllllll',
            'llllllll',
            'mlllllll',
            'lGGGYGll',
            'llGGGgll',
            'llllllll',
            'dggggggd']
    m.add('armorHead',
          Cube((-4, 24, -4), (8, 8, 8), 1.0, bevel=PLATE_P, note='helm shell', faces={
              'top': ['llllllll', 'llllllll', 'llhhllll', 'lhhwllll', 'llhhllll', 'llllllll', 'llllllll', 'llllllll'],
              'front': ['llllllll',
                        'llllllll',
                        'llllllll',
                        'mllllllm',
                        'l      s',
                        'lG    gs',
                        'lG    gs',
                        'dG    Gd'],
              'right': side, 'left': hflip(side),
              'back': ['llllllll', 'llllllll', 'llllllll', 'mllllllm', 'llllllll', 'llllllll', 'ssssssss', 'dggggggd'],
              'bottom': 's'}),
          Cube((-5, 29, -5), (10, 1, 10), 0.2, note='circlet', faces={
              'sides': ['GGGGGGGGGG'], 'front': ['GGGYGGYGGG'], 'top': 'G', 'bottom': 'd'}),
          Cube((-1, 28.5, -5.6), (2, 2, 1), note='circlet opal', faces={'front': opal, 'top': 'G', 'all': 'g'}))
    m.add_bone(Bone('crestCenter', 'armorHead', (0, 33, -1.5), (-12, 0, 0), [
        Cube((-3.5, 32.5, -1.5), (7, 7, 0), note='flame crest', faces=zplane(flick(*PHX_CREST))),
        Cube((0, 32.5, -2.5), (0, 7, 6), note='flame crest (side)', faces=xplane(flick(*PHX_CREST_SIDE)))],  # noqa
        anim=sway(3.6, (0, 4, 2, 0), (2, 2, 3, 0.4))))
    m.add_bone(Bone('plumeRight', 'armorHead', (-2.5, 32, 4), (10, -45, 6), [
        Cube((-2.5, 30, 3.5), (0, 5, 11), note='tail plume', faces=xplane(PHX_PLUME))],
        anim=sway(3.6, (0, 6, 1, 0.6), (1, 3, 2, 0))), mirror=True)
    # ------------------------------------------------------------ chest: crimson plate with a gold phoenix, opal heart
    m.add('armorBody',
          Cube((-4, 12, -2), (8, 12, 4), 1.01, bevel=PLATE_P, note='breastplate', faces={
              'front': ['lGGYYGGl',
                        'llgGGgll',
                        'llllllll',
                        'llllllll',
                        'YllllllY',
                        'GYllllYG',
                        'lGYGGYGl',
                        'llGYYGll',
                        'dGGYYGGd',
                        'BBcBBcBB',
                        'BcBBcBBB',
                        'dggGGggd'],
              'back': ['lllGGlll', 'lllGYlll', 'lllgGlll', 'ssslGsss', 'lllgGlll', 'lllgGlll', 'slllllls',
                       'dGGYYGGd', 'BBcBBcBB', 'BcBBcBBB', 'bBBBBBBb', 'dggGGggd'],
              'sides': ['llll', 'llll', 'lwll', 'lmll', 'llll', 'llll', 'slls', 'dGGG', 'BBcB', 'BcBB', 'bBBb', 'dggg'],
              'top': ['lGGGGGGl', 'Gk    kG', 'Gk    kG', 'lGGGGGGl'],
              'bottom': 'k'}),
          Cube((-1, 16.5, -3.6), (2, 2, 1), note='chest opal', faces={'front': opal, 'top': 'G', 'all': 'g'}),
          Cube((-4.5, 20, -3.5), (9, 4, 7), note='feather mantle', faces={
              'front': fringe('G', ['hlm', 'lms'], '2'), 'back': fringe('G', ['hlm', 'lms'], '2'), 'bottom': ' '}))
    # ------------------------------------------------------------ arms: wine sleeve, gold band, crimson vambrace, flame cuff
    sleeve = ['llll', 'llll', 'llll', 'BBBB', 'BcBB', 'BcBB', 'dGGg', 'llll', 'l*ll', 'llll', 'GGGG', 'mllm']
    m.add('armorRightArm',
          Cube((-8, 12, -2), (4, 12, 4), 1.0, bevel=PLATE_P, note='sleeve', faces={'sides': embered(sleeve), 'top': 'l', 'bottom': 's'}),
          Cube((-9, 12, -3), (6, 2, 6), 0.25, note='cuff', faces={'sides': ['G', 'g'], 'top': 'G', 'bottom': 'd'}),
          mirror=True)
    m.add_bone(Bone('pauldronRight', 'armorRightArm', (-5, 23, 0), (0, 0, -15), [
        Cube((-9.5, 22.5, -3.5), (6, 3, 7), bevel=PLATE_P, note='pauldron', faces={
            'top': ['Gllllm', 'Glhhlm', 'Glhwlm', 'Glhhlm', 'Glhllm', 'Gllllm', 'GGGGGG'],
            'sides': ['l', 'l', 'G'], 'right': ['lllllll', 'llYGYll', 'GGGGGGG'], 'bottom': 's'}),
        Cube((-10, 19.5, -3.5), (5, 3, 7), note='flame feathers', faces={
            'sides': fringe('G', ['hlm'], '2'), 'top': 'G', 'bottom': ' '})]), mirror=True)
    # ------------------------------------------------------------ leggings: gold sash, crimson plate, flames from the boots
    m.add('armorLeggingTorsoLayer',
          Cube((-5, 11, -3), (10, 3, 6), 0.1, note='sash', faces={
              'sides': ['G', 'm', 'g'], 'front': ['GGGGYYGGGG', 'mmsmmmmsmm', 'gggggggggg'], 'top': 'G', 'bottom': 'g'}),
          Cube((-1.5, 11.3, -3.75), (3, 2, 1), note='buckle', faces={
              'front': lambda fw, fh, f: ['GYG', 'gPg'] if f < 4 else ['GYG', 'gqg'], 'top': 'Y', 'sides': 'g', 'bottom': 'd'}))
    leg = ['BBBB', 'BcBB', 'llll', 'llll', 'llll', 'llll', 'dGGg', 'llll', 'llll', 'l2ll', '3l23', '3443']
    m.add('armorRightLeg',
          Cube((-4, 0, -2), (4, 12, 4), 0.5, bevel=PLATE_P, note='leg', faces={
              'sides': leg, 'right': overlay(leg, ['....', '....', '.Y..', '.G..', '.G..', '.g..']), 'top': 'B', 'bottom': 's'}),
          Cube((-4.2, 6.4, -2.95), (4, 2, 1), note='knee cop', faces={
              'front': ['GYYG', 'dggd'], 'top': 'Y', 'sides': 'g', 'bottom': 'd'}),
          mirror=True)
    m.add('armorTorsoExtensionRightLeg',
          Cube((-5.55, 8, -2.5), (1, 4, 5), note='flame tasset', faces={
              'right': fringe('G', ['hlm', 'lms'], '2'), 'sides': ['G', 'l', 'm', ' '], 'top': 'G', 'left': 'm', 'bottom': ' '}),
          Cube((-4.5, 7, 2.55), (4, 4, 1), note='coat-tail', faces={
              'back': ['GGGG', 'llll', 'mllm', 'g33g'], 'sides': ['G', 'l', 'm', 'g'], 'front': 'm', 'top': 'G', 'bottom': 'g'}),
          mirror=True)
    # ------------------------------------------------------------ boots: crimson with flickering flames, gold cuff and sole
    m.add('armorRightBoot',
          Cube((-4, 0, -2), (4, 5, 4), 0.75, bevel=PLATE_P, note='boot', faces={
              'sides': flick(['llll', 'llll', 'l3ll', '3443', 'dggd'], ['llll', 'llll', 'll2l', '3443', 'dggd'],
                             ['llll', 'llll', 'l2ll', '3443', 'dggd'], ['llll', 'llll', 'll3l', '3443', 'dggd']),
              'top': 'l', 'bottom': 'd'}),
          Cube((-5, 4, -3), (6, 2, 6), note='boot cuff', faces={'sides': ['G', 'g'], 'top': 'G', 'bottom': 'd'}),
          Cube((-4, 0, -3), (4, 2, 1), 0.25, note='toe cap', faces={
              'front': ['GYYG', 'dggd'], 'top': 'Y', 'sides': 'g', 'bottom': 'd'}),
          mirror=True)
    m.add_bone(Bone('bootFeatherRight', 'armorRightBoot', (-5.2, 2.5, 0.5), (0, -45, -12), [
        Cube((-5.2, 1.5, -0.5), (0, 4, 6), note='ankle feather', faces=xplane(PHX_ANKLE))],
        anim=sway(3.6, (1, 5, 2, 0))), mirror=True)
    return m


# ============================================================================ capes (vanilla cape: 10x16x1 box at uv 0,0 of 64x32)
def cape_image(front, back, edges, pal, nframes, bottom=None):
    """The cape net painted per frame; `front` is the outer face seen from behind the player, `back` the lining
    (given as seen from the same side, so hem cuts line up; it is mirrored onto the face here)."""
    imgs = []
    faces = {'front': front, 'back': lambda fw, fh, f: hflip(resolve(back, fw, fh, f)), 'all': edges}
    if bottom:
        faces['bottom'] = bottom
    cube = Cube((0, 0, 0), (10, 16, 1), faces=faces)
    cube.uv = (0, 0)
    for f in range(nframes):
        img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        paint_net(img.load(), cube, cube.grids(f), pal)
        imgs.append(img)
    out = Image.new('RGBA', (64, 32 * nframes), (0, 0, 0, 0))
    for i, im in enumerate(imgs):
        out.paste(im, (0, 32 * i))
    return out, imgs


VALK_CAPE = ['gGGGGGGGGg',
             'GhllBBllmG',
             'GlYG..GYlG',
             'GYGg..gGYG',
             'GlGY..YGmG',
             'GllgBBgllG',
             'GhllBBlhmG',
             'GhlmBBlhmG',
             'GhlmBclhmG',
             'GhlmBBlhmG',
             'GlhmBBmhlG',
             'GlhmcBmhlG',
             'GmhmBBmhmG',
             'gGGGGGGGGg',
             'BBcBBBBcBB',
             'kBBb  bBBk']
VALK_CAPE_LINING = ['bBBBBBBBBb',
                    'bBcBbBBcBb',
                    'bBcBbBBcBb',
                    'bBcBbBBcbb',
                    'bBcbbBcBbb',
                    'bBcbbBcBbb',
                    'bBcbBBcBbb',
                    'bcBbBBcBbb',
                    'bcBbBcBBbb',
                    'bcBbBcBbbb',
                    'bcBbBcBbBb',
                    'bcBbBcBbBb',
                    'bBBbBcBbBb',
                    'gGGGGGGGGg',
                    'kbbbbbbbbk',
                    'kbbb  bbbk']


def valkyrie_cape():
    """White cloth with soft folds, gold piping, a blue banner with a gold-winged sapphire, swallowtail hem."""
    return cape_image((VALK_CAPE, at(gem2(seq=VALK_GEM), 4, 2)), VALK_CAPE_LINING, 'G', VALK, VALK_N,
                      bottom=['bbbb  bbbb'])


PHX_CAPE = ['gGGGGGGGGg',
            'GllllllllG',
            'GYllllllYG',
            'GGYllllYGG',
            'GlGY..YGlG',
            'GllG..GllG',
            'GhllGGllmG',
            'GhlGYYGlmG',
            'GhGlGGlGmG',
            'GhlllllmlG',
            'GlmlllmllG']
PHX_CAPE_HEM = (['GllllllllG', 'Gl2lll2llG', 'G232l2322G', '3433343434', '45 4544 54'],
                ['GllllllllG', 'G2lll2ll2G', 'G32l2321lG', '3433343443', '4 54454 45'],
                ['Gll2lllllG', 'G12ll1l2lG', 'G323l232lG', '4333434343', ' 454 5445 '],
                ['GllllllllG', 'Gl2l2ll2lG', 'G2321l2332', '3343343433', '54 4454 45'])


def phoenix_cape():
    """Deep crimson with a gold phoenix around an opal, burning into a flickering flame hem."""
    outer = lambda fw, fh, f: PHX_CAPE + list(PHX_CAPE_HEM[f % 4])
    lining = lambda fw, fh, f: (['kbbbbbbbbk'] * 11 +
                                [''.join('b' if ch != ' ' else ' ' for ch in row) for row in PHX_CAPE_HEM[f % 4]])
    return cape_image((outer, at(gem2(seq=PHX_OPAL), 4, 4)), lining, 'g', PHX, PHX_N,
                      bottom=lambda fw, fh, f: [''.join('2' if ch != ' ' else ' ' for ch in PHX_CAPE_HEM[f % 4][-1])])


# ============================================================================ item icons (16x16, vanilla armour conventions)
# Front-on helmet, chestplate and leggings, boots side-on with each toe pointing outward (left boot left, right
# boot right), outline in the darkest tone of the local material and lighter ('s') on the lit top-left edges.
ICONS = {
    'valkyrie_mage': {
        'helmet': ['................',
                   '.5............5.',
                   '.45..........54.',
                   '.345.ssssss.543.',
                   '..34shhGYllo43..',
                   '..2slhhGYllmo2..',
                   '..gslhlGYlmmog..',
                   '..ddGGGqPGGgdd..',
                   '...olhlPplmso...',
                   '...olhkkkkmso...',
                   '...olhmoomsso...',
                   '...olhmoomsso...',
                   '...oGGg..Ggdo...',
                   '....ooo..ooo....',
                   '................',
                   '................'],
        'chestplate': ['................',
                       '................',
                       '.sssss....sssso.',
                       '.sGYGGs..sGGGgo.',
                       '.shlllGkkGlllmo.',
                       '.slhllhGGllllmo.',
                       '.s5s4lGYYGl4s5o.',
                       '...sslGqPglsoo..',
                       '....slGPpglo....',
                       '....slhdglmo....',
                       '....sdGYYGdo....',
                       '....sBcBBcbo....',
                       '....sbBBBBbo....',
                       '.....oooooo.....',
                       '................',
                       '................'],
        'leggings': ['................',
                     '................',
                     '...sssssssssso..',
                     '...sGGGYYGGGgo..',
                     '...sBBGqPGBbbo..',
                     '...sBcBssBcBbo..',
                     '...sBco..sBco...',
                     '...sBco..sBco...',
                     '...sdgo..sdgo...',
                     '...shlo..shlo...',
                     '...shmo..shmo...',
                     '...shmo..shmo...',
                     '...smso..smso...',
                     '...oooo..oooo...',
                     '................',
                     '................'],
        'boots': ['................',
                  '................',
                  '................',
                  '...ssss..ssss...',
                  '...sGYo..sYGo...',
                  '...shlo..shlo...',
                  '...shlo..shlo...',
                  '...shmo..shmo...',
                  '...shmo..shmo...',
                  '..sllmo..shllo..',
                  '.sGYlmo..shlYGo.',
                  '.sdggdo..odggdo.',
                  '.ooooo....ooooo.',
                  '................',
                  '................',
                  '................'],
    },
    'phoenix_mage': {
        'helmet': ['.......22.......',
                   '....2..33..2....',
                   '....32.43.23....',
                   '....33444433....',
                   '.....455554.....',
                   '....sgGGYGGo....',
                   '...slhllllmmo...',
                   '...sGGGqPGGgo...',
                   '...sldGPpGdmo...',
                   '...slkkkkkkmo...',
                   '...sGlkkkkmgo...',
                   '...sGlkkkkmgo...',
                   '...odG.kk.Gdo...',
                   '....oo....oo....',
                   '................',
                   '................'],
        'chestplate': ['................',
                       '................',
                       '.sssss....sssso.',
                       '.sGYGGs..sGGGgo.',
                       '.shlllGkkGlllmo.',
                       '.slhllhGGllllmo.',
                       '.s3s3hlmmlh3s3o.',
                       '...sYlh3hlmYo...',
                       '....sGYqPYGo....',
                       '....slGPpGmo....',
                       '....sdGYYGdo....',
                       '....sBcBBcbo....',
                       '....sbBBBBbo....',
                       '.....oooooo.....',
                       '................',
                       '................'],
        'leggings': ['................',
                     '................',
                     '...sssssssssso..',
                     '...sGGGYYGGGgo..',
                     '...smmGqPGmmso..',
                     '...shlhssllmso..',
                     '...shYo..shYo...',
                     '...slGo..slGo...',
                     '...sdgo..sdgo...',
                     '...shlo..shlo...',
                     '...s3lo..s3lo...',
                     '...s43o..s43o...',
                     '...s54o..s54o...',
                     '...oooo..oooo...',
                     '................',
                     '................'],
        'boots': ['................',
                  '................',
                  '................',
                  '...ssss..ssss...',
                  '...sGYo..sYGo...',
                  '...shlo..shlo...',
                  '...shlo..shlo...',
                  '...shmo..shmo...',
                  '...s3mo..s3mo...',
                  '..s343o..s434o..',
                  '.sGY45o..s54YGo.',
                  '.sdggdo..odggdo.',
                  '.ooooo....ooooo.',
                  '................',
                  '................',
                  '................'],
    },
}
# Per-frame texel changes (row, col, chars); the alpha outline never changes. Valkyrie: the sapphire glint sweeps
# across on frames 1-4 (same timing as the worn armour). Phoenix: the crest / flames shimmer on every frame
# (frame % 4) and the opal flares on frames 4-7.
VALK_ICON_GEM = {1: ('WP', 'Pp'), 2: ('Wq', 'qp'), 3: ('qW', 'Pq'), 4: ('qq', 'Pp')}
PHX_ICON_OPAL = {4: ('Wq', 'qp'), 5: ('Wq', 'qp'), 6: ('qW', 'Pq'), 7: ('qW', 'Pq')}
PHX_FLICKER = {1: [(2, 7, '5'), (3, 6, '4554')], 2: [(1, 7, '34'), (3, 6, '5445'), (4, 6, '5455')],
               3: [(0, 7, '21'), (2, 8, '4'), (3, 6, '4544')]}
PHX_LEG_FLICKER = {1: [(11, 4, '34'), (11, 10, '34')], 2: [(12, 4, '45'), (12, 10, '45')], 3: [(10, 4, '2'), (10, 10, '2')]}
PHX_BOOT_FLICKER = {1: [(9, 4, '4'), (9, 11, '4')], 2: [(10, 4, '54'), (10, 10, '45')], 3: [(8, 4, '2'), (8, 10, '2')]}


def gem_stamps(seq, f, row, col, rows=2):
    g = seq.get(f)
    return [(row + i, col, g[i]) for i in range(rows)] if g else []


def icon_changes(set_, piece, f):
    if set_ == 'valkyrie_mage':
        if piece in ('helmet', 'chestplate'):
            return gem_stamps(VALK_ICON_GEM, f, 7, 7)
        if piece == 'leggings':
            return gem_stamps(VALK_ICON_GEM, f, 4, 7, rows=1)
        return []
    ch = []
    if piece == 'helmet':
        ch = PHX_FLICKER.get(f % 4, []) + gem_stamps(PHX_ICON_OPAL, f, 7, 7)
    elif piece == 'chestplate':
        ch = gem_stamps(PHX_ICON_OPAL, f, 8, 7)
    elif piece == 'leggings':
        ch = PHX_LEG_FLICKER.get(f % 4, []) + gem_stamps(PHX_ICON_OPAL, f, 4, 7, rows=1)
    elif piece == 'boots':
        ch = PHX_BOOT_FLICKER.get(f % 4, [])
    return ch


def icon(rows, pal):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in (' ', '.'):
                img.putpixel((x, y), pal[ch])
    return img


def icons():
    """{name: (frames, mcmeta or None)}; pieces without any change stay single-frame and static."""
    out = {}
    for set_, pieces in ICONS.items():
        pal, n, seq, ft = (VALK, VALK_N, VALK_FRAMES, 2) if set_ == 'valkyrie_mage' else (PHX, PHX_N, PHX_FRAMES, 3)
        for piece, rows in pieces.items():
            assert len(rows) == 16 and all(len(r) == 16 for r in rows), (set_, piece)
            frames_ = []
            for f in range(n):
                g = rows
                for r, c, chars in icon_changes(set_, piece, f):
                    assert all(g[r][c + i] not in ' .' for i in range(len(chars))), (set_, piece, r, c)
                    g = overlay(g, [chars], c, r)
                frames_.append(icon(g, pal))
            if all(fr.tobytes() == frames_[0].tobytes() for fr in frames_):
                out[f'{set_}_{piece}'] = ([frames_[0]], None)
            else:
                out[f'{set_}_{piece}'] = (frames_, mcmeta(seq, ft))
    return out


# ============================================================================ software preview renderer
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], np.float64)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float64)


def box_faces(origin, size, inflate=0.0):
    """(face, corner at texel (0,0), vector along texture x, along texture y, outward normal) in GeckoLib's baked
    space (Bedrock x mirrored): the model faces -Z and its right side is +X."""
    x0, y0, z0 = (o - inflate for o in origin)
    w, h, d = (s + 2 * inflate for s in size)
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    return [('front', (x1, y1, z0), (-w, 0, 0), (0, -h, 0), (0, 0, -1)),
            ('back', (x0, y1, z1), (w, 0, 0), (0, -h, 0), (0, 0, 1)),
            ('right', (x1, y1, z1), (0, 0, -d), (0, -h, 0), (1, 0, 0)),
            ('left', (x0, y1, z0), (0, 0, d), (0, -h, 0), (-1, 0, 0)),
            ('top', (x1, y1, z1), (-w, 0, 0), (0, 0, -d), (0, 1, 0)),
            ('bottom', (x1, y0, z0), (-w, 0, 0), (0, 0, d), (0, -1, 0))]


LIGHTS = [np.array(v) / np.linalg.norm(v) for v in ((0.2, 1.0, -0.7), (-0.2, 1.0, 0.7))]


def mc_light(n, size=None):
    """Minecraft's two-directional entity lighting (ambient 0.4 + 0.6 diffuse) for a model-space normal of an
    entity facing south (model -Z = world +Z). For flat cubes GeckoLib's RenderUtil.fixInvertedFlatCube flips
    negative normal components, which this repeats."""
    n = np.array([-n[0], n[1], -n[2]], np.float64)
    if size is not None and 0 in size:
        w, h, d = size
        if n[0] < 0 and (h == 0 or d == 0):
            n[0] = -n[0]
        if n[1] < 0 and (w == 0 or d == 0):
            n[1] = -n[1]
        if n[2] < 0 and (w == 0 or h == 0):
            n[2] = -n[2]
    return min(1.0, 0.4 + 0.6 * sum(max(0.0, float(np.dot(n, l))) for l in LIGHTS))


class Raster:
    """Orthographic textured-quad rasteriser with a z-buffer (nearest texel, alpha test)."""

    def __init__(self, w, h, scale, yaw, pitch, center, bg=(0.47, 0.62, 0.86)):
        self.w, self.h, self.scale = w, h, scale
        self.view = rot_x(pitch) @ rot_y(yaw)
        self.center = np.array(center, np.float64)
        self.rgb = np.zeros((h, w, 3), np.float32)
        self.rgb[:] = bg
        self.z = np.full((h, w), np.inf)

    def project(self, p):
        v = self.view @ (np.asarray(p, np.float64) - self.center)
        return np.array([self.w / 2 - v[0] * self.scale, self.h / 2 - v[1] * self.scale]), v[2]

    def quad(self, tex, rect, p0, ex, ey, light):
        a, za = self.project(p0)
        b, zb = self.project(np.add(p0, ex))
        c, zc = self.project(np.add(p0, ey))
        e1, e2 = b - a, c - a
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            return
        corners = np.array([a, b, c, b + e2])
        lo = np.floor(corners.min(0)).astype(int)
        hi = np.ceil(corners.max(0)).astype(int)
        lo = np.maximum(lo, 0)
        hi = np.minimum(hi, (self.w, self.h))
        if lo[0] >= hi[0] or lo[1] >= hi[1]:
            return
        gy, gx = np.mgrid[lo[1]:hi[1], lo[0]:hi[0]].astype(np.float64) + 0.5
        dx, dy = gx - a[0], gy - a[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        x, y, w, h = rect
        tx = np.clip((x + s * w).astype(int), x, x + max(w, 1) - 1)
        ty = np.clip((y + t * h).astype(int), y, y + max(h, 1) - 1)
        texel = tex[ty, tx]
        z = za + s * (zb - za) + t * (zc - za)
        zbuf = self.z[lo[1]:hi[1], lo[0]:hi[0]]
        msk = inside & (texel[..., 3] > 0.1) & (z < zbuf - 1e-4)
        zbuf[msk] = z[msk]
        self.rgb[lo[1]:hi[1], lo[0]:hi[0]][msk] = texel[msk, :3] * light

    def image(self):
        return Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8), 'RGB')


def bone_matrices(bones, pose=None):
    """4x4 transforms per bone in GeckoLib's baked space: pivot negated in x, R = Rz(z) Ry(-y) Rx(-x)."""
    by_name = {b.name: b for b in bones}
    cache = {}
    pose = pose or {}

    def mat(b):
        if b.name in cache:
            return cache[b.name]
        parent = mat(by_name[b.parent]) if b.parent else np.eye(4)
        px, py, pz = -b.pivot[0], b.pivot[1], b.pivot[2]
        rx, ry, rz = np.add(b.rotation, pose.get(b.name, (0, 0, 0)))
        r = rot_z(math.radians(rz)) @ rot_y(math.radians(-ry)) @ rot_x(math.radians(-rx))
        local = np.eye(4)
        local[:3, :3] = r
        local[:3, 3] = np.array([px, py, pz]) - r @ np.array([px, py, pz])
        cache[b.name] = parent @ local
        return cache[b.name]

    return {b.name: mat(b) for b in bones}


def draw_cubes(r, bones, tex, slots=None, model=None, pose=None):
    mats = bone_matrices(bones, pose)
    for b in bones:
        if not b.cubes or (slots is not None and model.slot(b) not in slots):
            continue
        m4 = mats[b.name]
        for c in b.cubes:
            (x, y, z), (w, h, d) = c.origin, c.size
            u, v = c.uv
            rects = net_rects(w, h, d)
            for face, p0, ex, ey, n in box_faces((-(x + w), y, z), (w, h, d), c.inflate):
                fx, fy, fw, fh = rects[face]
                if fw == 0 or fh == 0:
                    continue
                P0 = m4[:3, :3] @ np.array(p0, np.float64) + m4[:3, 3]
                EX = m4[:3, :3] @ np.array(ex, np.float64)
                EY = m4[:3, :3] @ np.array(ey, np.float64)
                N = m4[:3, :3] @ np.array(n, np.float64)
                r.quad(tex, (u + fx, v + fy, fw, fh), P0, EX, EY, mc_light(N, c.size))


def mannequin_skin():
    """A neutral original 64x64 player-layout skin for the previews (face, hair, shirt, trousers)."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    skin, hair, shirt, pants, shoe = (196, 150, 118, 255), (74, 52, 36, 255), (70, 92, 84, 255), (52, 60, 92, 255), (50, 46, 44, 255)
    d.rectangle((0, 0, 31, 15), fill=skin)
    d.rectangle((8, 0, 15, 7), fill=hair)          # head top
    d.rectangle((24, 8, 31, 15), fill=hair)        # head back
    d.rectangle((8, 8, 15, 9), fill=hair)
    d.rectangle((0, 8, 7, 10), fill=hair)
    d.rectangle((16, 8, 23, 10), fill=hair)
    for x in (9, 13):
        img.putpixel((x, 12), (255, 255, 255, 255))
        img.putpixel((x + 1, 12), (60, 70, 140, 255))
    d.rectangle((11, 14, 12, 14), fill=(150, 90, 80, 255))
    d.rectangle((16, 16, 39, 31), fill=shirt)
    d.rectangle((40, 16, 55, 31), fill=shirt)
    d.rectangle((32, 48, 47, 63), fill=shirt)
    d.rectangle((44, 28, 55, 31), fill=skin)
    d.rectangle((36, 60, 47, 63), fill=skin)
    d.rectangle((0, 16, 15, 31), fill=pants)
    d.rectangle((16, 48, 31, 63), fill=pants)
    d.rectangle((0, 28, 15, 31), fill=shoe)
    d.rectangle((16, 60, 31, 63), fill=shoe)
    return img


MANNEQUIN = [  # (skin uv, origin in Bedrock coords, size, bone)
    ((0, 0), (-4, 24, -4), (8, 8, 8), 'bipedHead'), ((16, 16), (-4, 12, -2), (8, 12, 4), 'bipedBody'),
    ((40, 16), (-8, 12, -2), (4, 12, 4), 'bipedRightArm'), ((32, 48), (4, 12, -2), (4, 12, 4), 'bipedLeftArm'),
    ((0, 16), (-4, 0, -2), (4, 12, 4), 'bipedRightLeg'), ((16, 48), (0, 0, -2), (4, 12, 4), 'bipedLeftLeg')]


def draw_mannequin(r, pose_bones):
    tex = np.asarray(mannequin_skin(), np.float32) / 255.0
    mats = bone_matrices(pose_bones)
    for (u, v), (x, y, z), (w, h, d), bone in MANNEQUIN:
        m4 = mats[bone]
        rects = net_rects(w, h, d)
        for face, p0, ex, ey, n in box_faces((-(x + w), y, z), (w, h, d)):
            fx, fy, fw, fh = rects[face]
            P0 = m4[:3, :3] @ np.array(p0, np.float64) + m4[:3, 3]
            r.quad(tex, (u + fx, v + fy, fw, fh), P0, m4[:3, :3] @ np.array(ex, float), m4[:3, :3] @ np.array(ey, float),
                   mc_light(m4[:3, :3] @ np.array(n, float)))


VIEWS = [('front', 0, -6), ('3/4', -32, -10), ('side', -90, -5), ('back', 180, -8)]


def draw_cape(r, cape):
    """The cape as MageCapeLayer hangs it at rest: 0.2 blocks behind the body centre, tilted 6 degrees back."""
    tex = np.asarray(cape, np.float32) / 255.0
    z_in, tilt = 3.2, math.radians(-6)
    rx = rot_x(tilt)
    pivot = np.array([0.0, 24.0, z_in])

    def tp(p):
        return rx @ (np.asarray(p, np.float64) - pivot) + pivot

    def tv(v):
        return rx @ np.asarray(v, np.float64)

    quads = [((1, 1, 10, 16), (-5, 24, z_in + 1), (10, 0, 0), (0, -16, 0), (0, 0, 1)),     # outer, seen from behind
             ((12, 1, 10, 16), (5, 24, z_in), (-10, 0, 0), (0, -16, 0), (0, 0, -1)),       # lining
             ((0, 1, 1, 16), (5, 24, z_in + 1), (0, 0, -1), (0, -16, 0), (1, 0, 0)),
             ((11, 1, 1, 16), (-5, 24, z_in), (0, 0, 1), (0, -16, 0), (-1, 0, 0))]
    for rect, p0, ex, ey, n in quads:
        r.quad(tex, rect, tp(p0), tv(ex), tv(ey), mc_light(tv(n)))


def render_view(model, atlas, yaw, pitch, scale=7, size=(300, 330), pose=None, slots=None, cape=None):
    r = Raster(size[0], size[1], scale, math.radians(yaw), math.radians(pitch), (0, 18, 0))
    draw_mannequin(r, model.bones)
    tex = np.asarray(atlas, np.float32) / 255.0
    draw_cubes(r, model.bones, tex, slots, model, pose)
    if cape is not None:
        draw_cape(r, cape)
    return r.image()


def label(img, text, xy=(4, 4)):
    ImageDraw.Draw(img).text(xy, text, fill=(255, 255, 255))


class ShotRaster(Raster):
    """Perspective camera like the game's: world units are blocks, MC yaw/pitch (yaw 0 = looking south, +Z),
    vertical FOV 70 degrees. Quads are split per texel so the affine rasteriser stays accurate."""

    def __init__(self, w, h, cam, yaw, pitch, fov=70.0, bg=(0.55, 0.7, 0.95)):
        super().__init__(w, h, 1.0, 0.0, 0.0, (0, 0, 0), bg)
        y, p = math.radians(yaw), math.radians(pitch)
        self.cam = np.array(cam, np.float64)
        self.f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
        r = np.array([-self.f[2], 0.0, self.f[0]])
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)
        self.k = (h / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        d = np.asarray(p, np.float64) - self.cam
        depth = max(1e-3, float(d @ self.f))
        return np.array([self.w / 2 + (d @ self.r) / depth * self.k, self.h / 2 - (d @ self.u) / depth * self.k]), depth

    def quad(self, tex, rect, p0, ex, ey, light):
        x, y, w, h = rect
        p0, ex, ey = (np.asarray(v, np.float64) for v in (p0, ex, ey))
        for j in range(max(h, 1)):
            for i in range(max(w, 1)):
                super().quad(tex, (x + i, y + j, 1, 1), p0 + ex * (i / w) + ey * (j / h), ex / w, ey / h, light)


def to_world(stand, m4):
    """GeckoLib model space (texels, facing -Z) -> world blocks for an armour stand at `stand` facing south."""
    rot = np.diag([-1.0, 1.0, -1.0]) / 16.0
    out = np.eye(4)
    out[:3, :3] = rot @ m4[:3, :3]
    out[:3, 3] = rot @ m4[:3, 3] + np.asarray(stand, np.float64)
    return out


def draw_stand(r, model, atlas, stand, pose=None):
    tex = np.asarray(atlas, np.float32) / 255.0
    mats = bone_matrices(model.bones, pose)
    for b in model.bones:
        if not b.cubes:
            continue
        m4 = to_world(stand, mats[b.name])
        for c in b.cubes:
            (x, y, z), (w, h, d) = c.origin, c.size
            u, v = c.uv
            rects = net_rects(w, h, d)
            for face, p0, ex, ey, n in box_faces((-(x + w), y, z), (w, h, d), c.inflate):
                fx, fy, fw, fh = rects[face]
                if fw == 0 or fh == 0:
                    continue
                N = mats[b.name][:3, :3] @ np.array(n, np.float64)
                r.quad(tex, (u + fx, v + fy, fw, fh), m4[:3, :3] @ np.array(p0, float) + m4[:3, 3],
                       m4[:3, :3] @ np.array(ex, float), m4[:3, :3] @ np.array(ey, float), mc_light(N, c.size))


# ClientRenderCheck "armor" scene: stands at origin.x -/+ 1.2, camera = origin + offset (+1.62 eye height)
CHECK_SHOTS = [('asb_armor_front', (0, 0.3, 3.4), 180, 6), ('asb_armor_back', (0, 0.5, -3.4), 0, 8),
               ('asb_armor_side', (3.6, 0.6, 0), 90, 8)]
# every flat piece (wings, plumes, crest, ankle feathers) must face the camera well enough in all three shots
PLANE_MIN = 0.55


def plane_report(models, verbose=True):
    """Worst |cos| between each zero-thickness piece and the camera it is seen from in the render-check shots.
    Near 0 means that piece is edge-on (a sliver) from that camera. Returns the smallest value found."""
    rot = np.diag([-1.0, 1.0, -1.0])
    worst = 1.0
    for model, _ in models:
        mats = bone_matrices(model.bones)
        for bone in model.bones:
            axes = [np.array([1, 0, 0] if c.size[0] == 0 else [0, 0, 1], float) for c in bone.cubes if 0 in c.size]
            if not axes or not any(bone.rotation):
                continue
            for name, off, yaw, pitch in CHECK_SHOTS:
                stand = 1.2 if 'Right' in bone.name else -1.2      # phoenix stand is at +x, valkyrie at -x
                view = np.array([off[0] - stand, off[1] + 1.62, off[2]], np.float64)
                view /= np.linalg.norm(view)
                for ax in axes:
                    n = rot @ (mats[bone.name][:3, :3] @ ax)
                    n /= np.linalg.norm(n)
                    c = abs(float(n @ view))
                    worst = min(worst, c)
                    if verbose and c < PLANE_MIN:
                        print(f'  ! {model.name}.{bone.name} is {math.degrees(math.acos(min(1, c))):.0f} deg off '
                              f'face-on in {name} ({c:.2f})')
    if verbose:
        print(f'  plane visibility: worst |cos| = {worst:.2f} (want >= {PLANE_MIN:.2f})')
    return worst


def ingame_shots(models, scale=0.5):
    w, h = int(854 * scale), int(480 * scale)
    sheet = Image.new('RGB', (w * len(CHECK_SHOTS), h + 16), (30, 32, 38))
    for i, (name, off, yaw, pitch) in enumerate(CHECK_SHOTS):
        r = ShotRaster(w, h, (off[0], off[1] + 1.62, off[2]), yaw, pitch)
        for (model, frames_), x in zip(models, (-1.2, 1.2)):
            draw_stand(r, model, frames_[0], (x, 0.0, 0.0))
        sheet.paste(r.image(), (i * w, 16))
        ImageDraw.Draw(sheet).text((i * w + 4, 2), name + ' (predicted)', fill=(255, 255, 255))
    return sheet


ICON_REFS = {'valkyrie_mage': [('aether valkyrie', 'aether/assets/aether/textures/item/armor/valkyrie_{}.png'),
                               ('iss priest', 'iss/assets/irons_spellbooks/textures/item/priest_{}.png')],
             'phoenix_mage': [('aether phoenix', 'aether/assets/aether/textures/item/armor/phoenix_{}.png'),
                              ('iss pyromancer', 'iss/assets/irons_spellbooks/textures/item/pyromancer_{}.png')]}
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')


def ref_icon(path):
    try:
        return Image.open(REFS / path).convert('RGBA').crop((0, 0, 16, 16))
    except OSError:
        return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def icon_sheet(icon_imgs):
    """Ours next to the Aether and Iron's Spells references at 8x, plus a 1x / 2x inventory strip."""
    s, cell = 8, 16 * 8 + 8
    rows = []
    for set_ in ICON_REFS:
        rows.append(('ours ' + set_, [icon_imgs[f'{set_}_{p}'][0][0] for p in PIECES]))
        for lab, pattern in ICON_REFS[set_]:
            rows.append((lab, [ref_icon(pattern.format(p)) for p in PIECES]))
    sheet = Image.new('RGBA', (150 + cell * 4, cell * len(rows) + 120), (139, 139, 139, 255))
    d = ImageDraw.Draw(sheet)
    for i, (lab, imgs) in enumerate(rows):
        d.text((6, i * cell + 60), lab, fill=(0, 0, 0))
        for j, im in enumerate(imgs):
            sheet.alpha_composite(im.resize((16 * s, 16 * s), Image.NEAREST), (150 + j * cell, i * cell + 4))
    # inventory strip: slots like the vanilla GUI, 1x and 2x
    y0 = cell * len(rows) + 8
    for k, scale in enumerate((1, 2)):
        x = 150
        for set_ in ICON_REFS:
            for p in PIECES:
                slot = Image.new('RGBA', (18 * scale, 18 * scale), (55, 55, 55, 255))
                slot.paste((139, 139, 139, 255), (scale, scale, 18 * scale, 18 * scale))
                slot.alpha_composite(icon_imgs[f'{set_}_{p}'][0][0].resize((16 * scale, 16 * scale), Image.NEAREST), (scale, scale))
                sheet.alpha_composite(slot, (x, y0 + k * 30))
                x += 18 * scale + 2
    d.text((6, y0 + 4), 'inventory 1x / 2x', fill=(0, 0, 0))
    return sheet


def previews(models, capes, icon_imgs):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    cw, ch = 300, 330
    # --- front / 3/4 / side / back of both sets, cape hung as MageCapeLayer does (and without, back view)
    views = VIEWS + [('back, no cape', 180, -8)]
    sheet = Image.new('RGB', (cw * len(views), ch * len(models)), (30, 32, 38))
    for row, (model, frames_) in enumerate(models):
        cape = capes[model.name][1][0]
        for col, (name, yaw, pitch) in enumerate(views):
            img = render_view(model, frames_[0], yaw, pitch, cape=None if 'no cape' in name else cape)
            label(img, f'{model.name} {name}')
            sheet.paste(img, (col * cw, row * ch))
    sheet.save(PREVIEW / 'armor_views.png')
    # --- about the in-game size of an armour stand a few blocks away (3 px per texel)
    small = Image.new('RGB', (150 * 8, 160), (30, 32, 38))
    i = 0
    for model, frames_ in models:
        for name, yaw, pitch in VIEWS:
            small.paste(render_view(model, frames_[0], yaw, pitch, scale=3, size=(150, 160), cape=capes[model.name][1][0]),
                        (i * 150, 0))
            i += 1
    small.save(PREVIEW / 'armor_small.png')
    # --- atlas frame strips and a GIF of the texture animation (front 3/4 view, mcmeta timing)
    for model, frames_ in models:
        strip = Image.new('RGBA', ((ATLAS + 4) * len(frames_), ATLAS), (60, 62, 70, 255))
        for i, fr in enumerate(frames_):
            strip.alpha_composite(fr, ((ATLAS + 4) * i, 0))
        strip.resize((strip.width * 3, strip.height * 3), Image.NEAREST).save(PREVIEW / f'{model.name}_atlas_frames.png')
        cape_frames = capes[model.name][1]
        gif = [render_view(model, frames_[f], -32, -10, scale=9, size=(380, 420)) for f in range(model.nframes)]
        gif_back = [render_view(model, frames_[f], 160, -10, scale=9, size=(380, 420), cape=cape_frames[f])
                    for f in range(model.nframes)]
        both = []
        for a, b in zip(gif, gif_back):
            im = Image.new('RGB', (760, 420))
            im.paste(a, (0, 0))
            im.paste(b, (380, 0))
            both.append(im)
        seq = []
        for e in model.mcmeta_frames:
            idx, t = (e['index'], e['time']) if isinstance(e, dict) else (e, model.frametime)
            seq.append((both[idx], t * 50))
        seq[0][0].save(PREVIEW / f'{model.name}_anim.gif', save_all=True, append_images=[q[0] for q in seq[1:]],
                       duration=[q[1] for q in seq], loop=0)
    # --- capes (all frames, 4x) and icons
    capesheet = Image.new('RGBA', (2 * (64 * 4 + 20), 32 * 4 * max(len(c[1]) for c in capes.values())), (60, 62, 70, 255))
    for i, (name, (strip, imgs)) in enumerate(capes.items()):
        capesheet.alpha_composite(strip.resize((64 * 4, strip.height * 4), Image.NEAREST), (i * (64 * 4 + 20), 0))
    capesheet.save(PREVIEW / 'capes.png')
    icon_sheet(icon_imgs).save(PREVIEW / 'icons.png')
    ingame_shots(models, 1.0).save(PREVIEW / 'ingame_shots_predicted.png')
    anim = [(n, fr) for n, (fr, meta) in icon_imgs.items() if meta]
    strip = Image.new('RGBA', (16 * 8 * 8 + 160, len(anim) * 136), (139, 139, 139, 255))
    d = ImageDraw.Draw(strip)
    for i, (n, fr) in enumerate(anim):
        d.text((4, i * 136 + 60), n, fill=(0, 0, 0))
        for j, im in enumerate(fr):
            strip.alpha_composite(im.resize((128, 128), Image.NEAREST), (160 + j * 130, i * 136 + 4))
    strip.save(PREVIEW / 'icons_frames.png')


# ============================================================================ export / check
def outputs():
    """Every exported file as {relative path: bytes-or-image}; images are compared by pixels in --check."""
    out = {}
    built = []
    for model in (valkyrie_mage(), phoenix_mage()):
        model.pack()
        strip, frames_ = model.strip()
        out[f'geo/{model.name}_armor.geo.json'] = dumps(model.geo())
        out[f'animations/{model.name}_armor.animation.json'] = dumps(model.animation())
        out[f'textures/models/armor/{model.name}.png'] = strip
        out[f'textures/models/armor/{model.name}.png.mcmeta'] = dumps(model.mcmeta())
        built.append((model, frames_))
    capes = {}
    for name, fn, seq, ft in (('valkyrie_mage', valkyrie_cape, VALK_FRAMES, 2), ('phoenix_mage', phoenix_cape, PHX_FRAMES, 3)):
        strip, imgs = fn()
        capes[name] = (strip, imgs)
        out[f'textures/models/armor/{name}_cape.png'] = strip
        out[f'textures/models/armor/{name}_cape.png.mcmeta'] = dumps(mcmeta(seq, ft, 64, 32))
    icon_imgs = icons()
    for name, (imgs, meta) in icon_imgs.items():
        strip = Image.new('RGBA', (16, 16 * len(imgs)), (0, 0, 0, 0))
        for i, im in enumerate(imgs):
            strip.paste(im, (0, 16 * i))
        out[f'textures/item/{name}.png'] = strip
        if meta:
            out[f'textures/item/{name}.png.mcmeta'] = dumps(meta)
    return out, built, capes, icon_imgs


def dumps(obj, indent=0):
    """JSON with two-space indent and number lists kept on one line (readable in Blockbench and diffs)."""
    pad = '  ' * indent
    if isinstance(obj, dict):
        items = [f'{pad}  {json.dumps(k)}: {dumps(v, indent + 1)}' for k, v in obj.items()]
        text = '{\n' + ',\n'.join(items) + '\n' + pad + '}' if items else '{}'
    elif isinstance(obj, list) and all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in obj):
        text = '[' + ', '.join(json.dumps(v) for v in obj) + ']'
    elif isinstance(obj, list):
        text = '[\n' + ',\n'.join(f'{pad}  {dumps(v, indent + 1)}' for v in obj) + '\n' + pad + ']'
    else:
        text = json.dumps(obj)
    return text + '\n' if indent == 0 else text


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=True)
    return buf.getvalue()


STALE = ['textures/item/{}_{}.png.mcmeta'.format(s, p) for s in ('valkyrie_mage', 'phoenix_mage')
         for p in ('helmet', 'chestplate', 'leggings', 'boots')]


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='verify the exported files instead of writing them')
    ap.add_argument('--no-preview', action='store_true')
    ap.add_argument('--dry', action='store_true', help='previews only, do not write the assets')
    args = ap.parse_args()
    out, built, capes, icon_imgs = outputs()
    if args.check:
        bad = []
        for rel, data in out.items():
            path = ASSETS / rel
            if not path.exists():
                bad.append(f'missing {rel}')
            elif isinstance(data, Image.Image):
                have = Image.open(path).convert('RGBA')
                if have.size != data.size or have.tobytes() != data.tobytes():
                    bad.append(f'pixels differ {rel}')
            elif path.read_text(encoding='utf-8') != data:
                bad.append(f'text differs {rel}')
        for rel in STALE:
            if rel not in out and (ASSETS / rel).exists():
                bad.append(f'stale {rel}')
        print('\n'.join(bad) if bad else f'OK: {len(out)} files match')
        raise SystemExit(1 if bad else 0)
    for rel, data in ([] if args.dry else out.items()):
        path = ASSETS / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, Image.Image):
            path.write_bytes(png_bytes(data))
        else:
            path.write_text(data, encoding='utf-8', newline='\n')
    for rel in ([] if args.dry else STALE):
        if rel not in out and (ASSETS / rel).exists():
            (ASSETS / rel).unlink()
    for model, _ in built:
        print(f'{model.name}: atlas rows used {model.used_height}/{ATLAS}, {model.nframes} frames')
    plane_report([(m, f) for m, f in built])
    if not args.no_preview:
        previews(built, capes, icon_imgs)
    print(f'wrote {len(out)} files' if not args.dry else 'dry run: previews only')


if __name__ == '__main__':
    main()
