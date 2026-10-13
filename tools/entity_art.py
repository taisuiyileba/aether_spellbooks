"""Spellcasting mob skins, entity/projectile sprites and particles of Aether Spellbooks (1.21.1).

Writes, under src/main/resources/assets/aether_spellbooks/:
  textures/entity/valkyrie_sorceress.png (+.mcmeta)   64x64 player-layout skin, blink + circlet-gem glint
  textures/entity/solar_acolyte.png (+.mcmeta)        64x64 skin, pulsing sun sigil
  textures/entity/solar_acolyte_glowmask.png          emissive pixels of the acolyte (same frame layout)
  textures/entity/valkyrie_wings.png                  40x44, two 20x44 panels (inner | outer) of the right wing
  textures/entity/solar_halo.png                      gold sun halo behind the acolyte's head (alpha blended)
  textures/entity/solar_rays.png, thunder_glow.png    additive glows (premultiplied: RGB is the light added)
  textures/entity/solar_core.png (+.mcmeta)           boiling sun core, 4 frames (hard alpha: works additive or blended)
  textures/entity/solar_bolt.png, zephyr_orb.png      projectile sprites (alpha blended, spun by the renderer)
  textures/entity/thunder_crystal.png (+.mcmeta)      ambrosium crystal sprite, 4 facet frames (looks like it spins)
  textures/particle/{cloud_puff,feather,gravity_mote,sky_sparkle}_{0..3}.png

Conventions
  * Every texel is deliberate. Skins, sprites and particles are character-grid maps (one char = one texel, '.' is
    transparent) over per-material ramps that run dark -> light and shift hue (shadows cooler, highlights warmer).
    The wing is built from whole-texel feather columns (lit edge, quill, shadow edge, rounded tip) laid row over
    row; the radial sprites test texel centres against radii and ray wedges. No anti-aliasing, no noise, no
    dithering; everything is shaded with the same discrete ramps (at most two alpha steps on glows).
  * Sprites the renderers spin (halo, rays, core, bolt, zephyr orb) are shaded radially, not lit from one side,
    so no highlight turns with them.
  * Skins use the standard 64x64 player layout that Iron's Spells' abstract_casting_mob.geo.json maps; the overlay
    (hat / jacket / sleeves / pants) adds hair volume, hood rim, pauldrons and robe hems. No face is pre-shaded: the
    game lights the faces itself.
  * Light comes from the top-left. Outlines use the darkest tone of the local material, never pure black.
  * Glowmask: GeckoLib's AutoGlowingTexture takes the colour from the base skin at every non-transparent glowmask
    texel (the glowmask's own RGB is ignored, its alpha is kept), so the glowmask holds the emissive texels only.
    It follows the base skin's animation frames, so it is stacked with the same layout.
  * Additive textures (RenderType.eyes in SolarHaloLayer / SolarOrbRenderer / ThunderCrystalRenderer /
    SpriteProjectileRenderer glow) must have RGB = 0 wherever they are transparent; their RGB is the light added.
  * Animated textures: frames stacked vertically + .png.mcmeta. Skins are ticked by GeckoLib; the sprites drawn
    with vanilla RenderTypes are ticked by AnimatableTexture.setAndUpdate() in their renderer (SolarHaloLayer,
    SolarOrbRenderer, ThunderCrystalRenderer, which also draws thunder_crystal.png as an alpha-blended billboard).
  * Particle frames are sequences: sky_sparkle grows, peaks and fades over its life (setSpriteFromAge); feather
    turns edge-on and over (ASParticleTypes plays 0 1 2 3 2 1 as a flutter loop); gravity_mote loops rest, swell,
    burst, ripple; cloud_puff goes from a dense puff to broken wisps (also tinted dark for the storm puff).

Run:  python tools/entity_art.py            write the assets and the previews (build/art_preview/entities/)
      python tools/entity_art.py --check    verify the exported files match (exit 1 if not); writes nothing
      python tools/entity_art.py --no-preview
Requires Pillow and numpy. Deterministic.
"""
import argparse
import io
import json
import math
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
PREVIEW = ROOT / 'build/art_preview/entities'
BEFORE = PREVIEW / '_before'          # optional copy of the previous art, shown in the previews when present
REF = Path(os.environ.get('ASB_REF', 'C:/Users/suiyi/AppData/Local/Temp/asb_ref'))   # optional, previews only


# ============================================================================ basics
def rgba(h, a=255):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def palette(spec):
    return {k: (v if isinstance(v, tuple) else rgba(v)) for k, v in spec.items()}


def check_rows(rows, w=None, h=None, what=''):
    assert rows, what
    ww = len(rows[0])
    assert all(len(r) == ww for r in rows), (what, [len(r) for r in rows])
    assert w is None or ww == w, (what, 'width', ww, w)
    assert h is None or len(rows) == h, (what, 'height', len(rows), h)


def grid(rows, pal):
    check_rows(rows)
    img = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = pal[ch]
    return img


def layer(base, detail):
    """Detail rows drawn over base rows; '.' keeps the base, ' ' cuts a hole."""
    check_rows(base)
    check_rows(detail, len(base[0]), len(base))
    return [''.join('.' if d == ' ' else (b if d == '.' else d) for b, d in zip(br, dr)) for br, dr in zip(base, detail)]


def bands(col_levels, row_offsets, ramp):
    """Cloth / hair block: each column keeps its tone (a strand or fold) and rows shift it lighter or darker."""
    top = len(ramp) - 1
    return [''.join(ramp[max(0, min(top, c + r))] for c in col_levels) for r in row_offsets]


def hstack(*blocks):
    return [''.join(parts) for parts in zip(*blocks)]


def stack_frames(frames):
    w, h = frames[0].size
    out = Image.new('RGBA', (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * h))
    return out


def mcmeta(frametime, frames, width=None, height=None):
    anim = {'frametime': frametime, 'interpolate': False, 'frames': frames}
    if width:
        anim['width'] = width
        anim['height'] = height
    return json.dumps({'animation': anim}, indent=2) + '\n'


def centers(n, cx=None, cy=None):
    """Texel-centre coordinates relative to (cx, cy) (default: the middle of an n x n texture)."""
    cx = n / 2 if cx is None else cx
    cy = n / 2 if cy is None else cy
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float64) + 0.5
    return xs - cx, ys - cy


def neighbours_outside(mask):
    """For each texel: is the texel to the (left, right, up, down) outside the mask?"""
    p = np.pad(mask, 1, constant_values=False)
    return ~p[1:-1, :-2], ~p[1:-1, 2:], ~p[:-2, 1:-1], ~p[2:, 1:-1]


def from_levels(levels, ramp_rgba, alpha=None):
    """levels: int array, -1 = transparent; ramp_rgba: list of RGBA tuples."""
    h, w = levels.shape
    arr = np.zeros((h, w, 4), np.uint8)
    for i, c in enumerate(ramp_rgba):
        arr[levels == i] = c
    if alpha is not None:
        arr[..., 3] = np.where(levels >= 0, alpha, 0)
    return Image.fromarray(arr, 'RGBA')


def premultiply(img):
    """Additive textures: RGB scaled by alpha, alpha kept; fully transparent texels become (0, 0, 0, 0)."""
    a = np.asarray(img.convert('RGBA')).astype(np.int32)
    out = a.copy()
    out[..., :3] = (a[..., :3] * a[..., 3:4] + 127) // 255
    out[a[..., 3] == 0] = 0
    return Image.fromarray(out.astype(np.uint8), 'RGBA')


# ============================================================================ skin layout
# part -> ((u, v), (width, height, depth)) in the 64x64 player skin
PARTS = {
    'head': ((0, 0), (8, 8, 8)), 'hat': ((32, 0), (8, 8, 8)),
    'body': ((16, 16), (8, 12, 4)), 'jacket': ((16, 32), (8, 12, 4)),
    'rarm': ((40, 16), (4, 12, 4)), 'rsleeve': ((40, 32), (4, 12, 4)),
    'larm': ((32, 48), (4, 12, 4)), 'lsleeve': ((48, 48), (4, 12, 4)),
    'rleg': ((0, 16), (4, 12, 4)), 'rpants': ((0, 32), (4, 12, 4)),
    'lleg': ((16, 48), (4, 12, 4)), 'lpants': ((0, 48), (4, 12, 4)),
}


def face_rect(part, face):
    (u, v), (w, h, d) = PARTS[part]
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}[face]


class Skin:
    """Paints character maps onto a player-layout skin. `glow` lists the palette keys that are emissive."""

    def __init__(self, pal, glow=''):
        self.pal = pal
        self.glow_keys = set(glow)
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def copy(self):
        s = Skin(self.pal, ''.join(self.glow_keys))
        s.img = self.img.copy()
        s.glow = self.glow.copy()
        return s

    def face(self, part, face, rows):
        x0, y0, w, h = face_rect(part, face)
        check_rows(rows, w, h, (part, face))
        px, gx = self.img.load(), self.glow.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == '.':
                    continue
                if ch == ' ':
                    px[x0 + x, y0 + y] = (0, 0, 0, 0)
                    gx[x0 + x, y0 + y] = (0, 0, 0, 0)
                    continue
                px[x0 + x, y0 + y] = self.pal[ch]
                gx[x0 + x, y0 + y] = self.pal[ch] if ch in self.glow_keys else (0, 0, 0, 0)

    def wrap(self, part, rows):
        """rows span right | front | left | back side by side (the order the faces unfold in)."""
        (_, _), (w, h, d) = PARTS[part]
        check_rows(rows, 2 * (w + d), h, part)
        x = 0
        for face, fw in (('right', d), ('front', w), ('left', d), ('back', w)):
            self.face(part, face, [r[x:x + fw] for r in rows])
            x += fw

    def fill(self, part, face, ch):
        _, _, w, h = face_rect(part, face)
        self.face(part, face, [ch * w] * h)


def mirror_wrap(rows, w=4, d=4):
    """Wrap rows for the opposite limb: swap the outer and inner sides and mirror every face."""
    out = []
    for r in rows:
        right, front, left, back = r[:d], r[d:d + w], r[d + w:2 * d + w], r[2 * d + w:]
        out.append(left[::-1] + front[::-1] + right[::-1] + back[::-1])
    return out


def mirror(rows):
    return [r[::-1] for r in rows]


# ============================================================================ Valkyrie Sorceress
VALK = palette({
    # golden hair: darkest .. highlight
    'j': '#6A3F14', 'h': '#A66E1E', 'H': '#D29C36', 'J': '#EEC85E', 'K': '#FFEAA2',
    # fair skin: outline, shade, mid, light, blush
    'x': '#A9685A', 's': '#DDA286', 'S': '#F2C4A6', 'T': '#FFDEC6', 'k': '#F4B09E',
    # eyes and mouth
    'l': '#7A4A24', 'e': '#3C8CE4', 'E': '#1D449A', 'm': '#E0968A',
    # silver plate 0..4
    '0': '#343C5C', '1': '#626C90', '2': '#98A4C4', '3': '#CCD5E8', '4': '#F4F8FF',
    # gold trim 5..9
    '5': '#5C340C', '6': '#9A6016', '7': '#D4982C', '8': '#F3CD60', '9': '#FFF1BA',
    # valkyrie-blue robe a..D
    'a': '#141C4E', 'b': '#1F347C', 'c': '#2C50A8', 'd': '#4380D2', 'D': '#78B6F0',
    # white underdress / sleeves u..V
    'u': '#7C85A6', 'v': '#AFBAD4', 'w': '#D7DFEE', 'V': '#F6F9FF',
    # sky gem: dark, mid, light, glint
    'g': '#14508C', 'G': '#2E94E6', 'I': '#9CE2FF', '+': '#FFFFFF',
})
HAIR = 'jhHJK'
ROBE = 'abcdD'
CLOTH = 'uvwV'


def valkyrie_skin():
    s = Skin(VALK)
    # ---------------------------------------------------------------- head: golden hair, blue eyes
    side = bands([1, 2, 2, 1, 2, 3, 3, 2], [1, 1, 0, 0, 0, 0, -1, -1], HAIR)
    side = layer(side, ['........', '........', '........', '........',
                        '........', '........', '.......s', '......hS'])        # jaw line at the front-bottom
    back = bands([1, 2, 3, 2, 1, 2, 3, 1], [1, 2, 1, 0, 0, 0, -1, -1], HAIR)
    front = [
        'hHJKKJHh',
        'HJHJJHJh',
        'hHTTTSHh',
        'hllTSllh',
        'hVeTSeVh',
        'hTSTSSsh',
        'hSSmmSsh',
        'jhsSSshj',
    ]
    s.wrap('head', hstack(side, front, mirror(side), back))
    s.face('head', 'top', [
        'hHJHHJHh',
        'HJKHhJJH',
        'HJKHhHJH',
        'hJJHhHJh',
        'HJJHhHHH',
        'HJHHhHJH',
        'hHJHhJHh',
        'hHJHHJHh',
    ])
    s.fill('head', 'bottom', 's')
    # hat: hair volume, a silver circlet with a sky gem, and a small white wing at each temple
    hat_side = [
        '..wVVV8.',
        '2wVVVV73',
        '..w.Vw6.',
        'hH......',
        'hHh.....',
        'jhH.....',
        '.jh.....',
        '........',
    ]
    s.wrap('hat', hstack(hat_side, [
        'hJK87JHh',
        '347IG632',
        'hH.65..h',
        'H......h',
        'h......h',
        'H......j',
        'h.......',
        '........',
    ], mirror(hat_side), [
        'hJJKJJHh',
        '12233221',
        'HJHhHJHh',
        'hHHhHHhH',
        'jhHhhHhh',
        '.jhhjhj.',
        '........',
        '........',
    ]))
    s.face('hat', 'top', [
        '.hHJJHh.',
        'hJKHHJHh',
        'HKJHhJJH',
        'HJJHhHJH',
        'HJHHhHHH',
        'hJHHhHJh',
        'hHJHhJHh',
        'hHJHHJHh',
    ])
    # ---------------------------------------------------------------- body: breastplate, gold belt, robe front
    s.wrap('body', [
        # right   front       left    back
        'bccd' + 'bcwVVwcb' + 'dccb' + 'bccddccb',
        'bcdc' + 'c344432b' + 'cdcb' + 'b123321b',
        'bcdc' + 'c347832b' + 'cdcb' + 'b234421b',
        'bccc' + 'c37IG62b' + 'cccb' + 'b123321b',
        'bccc' + 'c326521b' + 'cccb' + 'b122211b',
        'abcc' + 'b232110b' + 'ccba' + 'b678876b',
        'abcc' + 'b121100b' + 'ccba' + 'abccccba',
        '5667' + '67832765' + '7665' + '56777765',
        'abcd' + 'bd7Vw6cb' + 'dcba' + 'abcddcba',
        'abcd' + 'bd7wv6cb' + 'dcba' + 'abcdccba',
        'abcc' + 'bc7wv6bb' + 'ccba' + 'abccccba',
        'abbc' + 'bc7wv6ba' + 'cbba' + 'aabccbaa',
    ])
    s.face('body', 'top', ['bccwwccb', 'bcwVVwcb', 'bcwwwwcb', 'bccccccb'])
    s.fill('body', 'bottom', 'a')
    # jacket: her hair falls over the back plate
    s.wrap('jacket', hstack(['....'] * 12, ['........'] * 12, ['....'] * 12, [
        '.hHJJHh.',
        '.hJHHJh.',
        '.HhHJHh.',
        '..hHJh..',
        '..HhHh..',
        '..hHhj..',
        '...hj...',
    ] + ['........'] * 5))
    # ---------------------------------------------------------------- arms: robe sleeves, silver bracers
    arm = [
        # outer   front    inner    back
        'cddc' + 'cddc' + 'bccb' + 'bccb',
        'cdDc' + 'cdcb' + 'bccb' + 'bcca',
        'cdDc' + 'cdcb' + 'bccb' + 'bcba',
        'cdDc' + 'cdcb' + 'bccb' + 'bcba',
        'cdDc' + 'cdcb' + 'abcb' + 'abba',
        'cddc' + 'ccdb' + 'abcb' + 'abba',
        'bccb' + 'bccb' + 'abba' + 'aaba',
        '3443' + '3443' + '2332' + '2332',
        '2IG2' + '2332' + '1221' + '1221',
        '6gG6' + '6776' + '5665' + '5665',
        'STSs' + 'TSSs' + 'sSSs' + 'sSsx',
        'sSsx' + 'sSsx' + 'ssxx' + 'sxxx',
    ]
    s.wrap('rarm', arm)
    s.wrap('larm', mirror_wrap(arm))
    for part in ('rarm', 'larm'):
        s.fill(part, 'top', 'c')
        s.fill(part, 'bottom', 's')
    pauldron = [
        '3443' + '3443' + '2332' + '2332',
        '2342' + '2342' + '1221' + '1221',
        '1231' + '1221' + '0110' + '0110',
        '6786' + '6786' + '5665' + '5665',
    ] + ['....' * 4] * 8
    s.wrap('rsleeve', pauldron)
    s.wrap('lsleeve', mirror_wrap(pauldron))
    s.face('rsleeve', 'top', ['3443', '3443', '2342', '2332'])
    s.face('lsleeve', 'top', mirror(['3443', '3443', '2342', '2332']))
    # ---------------------------------------------------------------- legs: robe, gold hem, silver boots
    # the robe's front opening (gold edges, white underdress) continues the body's centre panel
    leg = [
        'abcc' + 'cd7V' + 'wwvv' + 'abcb',
        'abcc' + 'cd7w' + 'wvvu' + 'abcb',
        'abcc' + 'cd7w' + 'wvvu' + 'abbb',
        'abcc' + 'cd7w' + 'vvuu' + 'abcb',
        'abcc' + 'cc7w' + 'vvuu' + 'abcb',
        'abbc' + 'cd7v' + 'vuuu' + 'abbb',
        'abbc' + 'cc7v' + 'vuuu' + 'abba',
        'aabc' + 'bc6v' + 'uuuu' + 'aaba',
        '5676' + '6787' + '5665' + '5665',
        '1221' + '1221' + '0110' + '0110',
        '2342' + '2343' + '1221' + '1221',
        '1221' + '1231' + '0110' + '0110',
    ]
    s.wrap('rleg', leg)
    s.wrap('lleg', mirror_wrap(leg))
    for part in ('rleg', 'lleg'):
        s.fill(part, 'top', 'a')
        s.fill(part, 'bottom', '0')
    # pants: the lower robe flares out over the boots
    skirt = ['....' * 4] * 5 + [
        'bccd' + 'cd7v' + '....' + 'abcb',
        'abcc' + 'cd7v' + '....' + 'abcb',
        'abcc' + 'cc6v' + '....' + 'abba',
        '5676' + '6787' + '....' + '5665',
    ] + ['....' * 4] * 3
    s.wrap('rpants', skirt)
    s.wrap('lpants', mirror_wrap(skirt))
    return s


def valkyrie_frames():
    """0 rest, 1 blink, 2 gem brightens, 3 gem glint with a four-point sparkle on the circlet."""
    base = valkyrie_skin()
    blink = base.copy()
    blink.face('head', 'front', [
        'hHJKKJHh',
        'HJHJJHJh',
        'hHTTTSHh',
        'hTTTSSSh',
        'hllTSllh',
        'hTSTSSsh',
        'hSSmmSsh',
        'jhsSSshj',
    ])
    bright = base.copy()
    bright.face('hat', 'front', [
        'hJK89JHh',
        '348+I732',
        'hH.76..h',
        'H......h',
        'h......h',
        'H......j',
        'h.......',
        '........',
    ])
    bright.face('body', 'front', ['bcwVVwcb', 'c344432b', 'c348932b', 'c38+I72b', 'c327621b',
                                  'b232110b', 'b121100b', '67832765', 'bd7Vw6cb', 'bd7wv6cb', 'bc7wv6bb', 'bc7wv6ba'])
    glint = bright.copy()
    glint.face('hat', 'front', [
        'hJK+9JHh',
        '34+++I32',
        'hH.+6..h',
        'H......h',
        'h......h',
        'H......j',
        'h.......',
        '........',
    ])
    return [base.img, blink.img, bright.img, glint.img]


VALKYRIE_ANIM = [0] * 30 + [1] + [0] * 14 + [2, 3, 3, 2] + [0] * 22 + [1, 0, 0, 1]


# ============================================================================ Solar Acolyte
ACO = palette({
    # crimson robe q..P (maroon shadows, orange-red lights)
    'q': '#2A0812', 'r': '#4C0E1E', 'R': '#7C1824', 'Q': '#AC2A26', 'P': '#D24E30',
    # gold 5..9
    '5': '#5A2C0A', '6': '#985614', '7': '#D48E28', '8': '#F5C24C', '9': '#FFEDA4',
    # ivory tabard i..l
    'i': '#7C6246', 'I': '#B2966E', 'L': '#DAC394', 'l': '#F5E7C4',
    # face lost in the hood's shadow
    'D': '#14050A', 'd': '#2A1010', 's': '#4C281E', 'S': '#6C3E2E',
    # emissive: eyes and sun sigil
    'e': '#FFA82A', 'E': '#FFF3B4', 'o': '#FF861C', 'O': '#FFBE36', 'Y': '#FFE47C', 'W': '#FFFCE6',
    # sandals
    'k': '#22100A', 'K': '#462818',
})
CRIMSON = 'qrRQP'
ACO_GLOW = 'eEoOYW'


def sigil(frame):
    """The chest sun (8 x 6 detail rows over the robe), brighter in the pulse frames."""
    return {
        0: ['..o..o..', '...OO...', '.oOYYOo.', '.oOYYOo.', '...OO...', '..o..o..'],
        1: ['..o..o..', '...OO...', '.oOWYOo.', '.oOYYOo.', '...OO...', '..o..o..'],
        2: ['..O..O..', '...YY...', 'oOYWWYOo', 'oOYWWYOo', '...YY...', '..O..O..'],
    }[frame]


def back_sigil(frame):
    return {
        0: ['...oo...', '..oYYo..', '..oYYo..', '...oo...'],
        1: ['...oo...', '..oWYo..', '..oYYo..', '...oo...'],
        2: ['...OO...', '..OWWO..', '..OWWO..', '...OO...'],
    }[frame]


def acolyte_skin(frame=0):
    s = Skin(ACO, glow=ACO_GLOW)
    # ---------------------------------------------------------------- head: a dark face, burning eyes, a veil
    dark = ['DDDDDDDD'] * 8
    eyes = 'DEesseED' if frame < 2 else 'DEEssEED'
    s.wrap('head', hstack(dark, [
        'DDDDDDDD',
        'DDDDDDDD',
        'DddddddD',
        'DdsSSsdD',
        eyes,
        'DdsSSsdD',
        'D788887D',
        'DQPQQRQD',
    ], dark, dark))
    s.fill('head', 'top', 'D')
    s.fill('head', 'bottom', 'D')
    # hat = the hood: crimson folds, a gold rim around the face
    hood_side = layer(bands([1, 2, 3, 2, 1, 2, 3, 3], [1, 1, 0, 0, 0, 0, -1, -1], CRIMSON),
                      ['.......7'] + ['.......7'] * 6 + ['.......6'])
    hood_back = bands([1, 2, 3, 2, 2, 3, 2, 1], [1, 0, 0, 0, 0, 0, -1, -1], CRIMSON)
    s.wrap('hat', hstack(hood_side, [
        'QPPQQRRr',
        '78898876',
        '7......6',
        '7......6',
        '7......6',
        '7......6',
        '7......6',
        '6......5',
    ], mirror(hood_side), hood_back))
    s.face('hat', 'top', bands([2, 3, 3, 2, 2, 3, 2, 1], [0, 1, 1, 0, 0, 0, 0, 0], CRIMSON))
    # ---------------------------------------------------------------- body: robe, sun sigil, gold sash, tabard
    robe_front = bands([1, 2, 3, 2, 2, 3, 2, 1], [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1], CRIMSON)
    front = layer(robe_front, ['..7887..'] + sigil(min(frame, 2)) + [
        '67788776',
        '..7lL6..',
        '..7LI6..',
        '..7LI6..',
        '..7II6..',
    ])
    robe_side = bands([1, 2, 3, 2], [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1], CRIMSON)
    robe_side = layer(robe_side, ['....'] * 7 + ['5667'] + ['....'] * 4)
    robe_back = bands([1, 2, 2, 3, 2, 2, 1, 1], [1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1], CRIMSON)
    robe_back = layer(robe_back, ['........', '........'] + back_sigil(min(frame, 2)) + ['........', '56777765'] + ['........'] * 4)
    s.wrap('body', hstack(robe_side, front, mirror(robe_side), robe_back))
    s.face('body', 'top', ['rRQQQQRr', 'RQ7887QR', 'RQQ77QQR', 'rRRRRRRr'])
    s.fill('body', 'bottom', 'q')
    # jacket: the hood's cowl drapes over the shoulders at the back
    s.wrap('jacket', hstack(['....'] * 12, ['........'] * 12, ['....'] * 12,
                            ['.rRQQRr.', '..rRRr..', '...rr...'] + ['........'] * 9))
    # ---------------------------------------------------------------- arms: wide sleeves with gold cuffs
    sleeve_cloth = lambda cols: bands(cols, [1, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0, 0], CRIMSON)
    arm = hstack(
        layer(sleeve_cloth([2, 3, 3, 2]), ['....'] * 8 + ['6787', '5665', 'SSsd', 'sSdd']),
        layer(sleeve_cloth([2, 3, 2, 2]), ['....'] * 8 + ['6787', '5665', 'SSsd', 'sSdd']),
        layer(sleeve_cloth([1, 2, 2, 1]), ['....'] * 8 + ['5665', '5555', 'sssd', 'sddd']),
        layer(sleeve_cloth([1, 2, 1, 1]), ['....'] * 8 + ['5665', '5555', 'sdsd', 'dddd']))
    s.wrap('rarm', arm)
    s.wrap('larm', mirror_wrap(arm))
    for part in ('rarm', 'larm'):
        s.fill(part, 'top', 'R')
        s.fill(part, 'bottom', 's')
    flare = [
        'RQPQ' + 'QPQR' + 'rRRr' + 'rRRr',
        'RQQR' + 'QQRR' + 'rRRr' + 'rRrr',
        '6787' + '7876' + '5665' + '5665',
        '....' * 4,
        '....' * 4,
        '....' * 4,
        'rRRr' + 'rRRr' + 'qrrq' + 'qrrq',
        'RQQR' + 'RQQR' + 'rRRr' + 'rRRr',
        'QPQR' + 'QPQR' + 'RQRr' + 'RQRr',
        '6787' + '6787' + '5665' + '5665',
        '....' * 4,
        '....' * 4,
    ]
    s.wrap('rsleeve', flare)
    s.wrap('lsleeve', mirror_wrap(flare))
    s.face('rsleeve', 'top', ['RQQR', 'QPQR', 'QPQR', 'RQQR'])
    s.face('lsleeve', 'top', mirror(['RQQR', 'QPQR', 'QPQR', 'RQQR']))
    # ---------------------------------------------------------------- legs: robe to the ankles, tabard, sandals
    leg_cloth = lambda cols: bands(cols, [0, 0, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0], CRIMSON)
    leg = hstack(
        layer(leg_cloth([1, 2, 3, 2]), ['....'] * 9 + ['6787', 'KKKK', 'kKKk']),
        layer(leg_cloth([2, 3, 2, 2]), ['..7l', '..7L', '..7L', '..7L', '..7L', '..7I', '..7I', '..6I', '..6I',
                                        '6787', 'KK8K', 'kKKk']),
        layer(leg_cloth([1, 1, 2, 1]), ['....'] * 9 + ['5665', 'kKKk', 'kkkk']),
        layer(leg_cloth([1, 2, 2, 1]), ['....'] * 9 + ['5665', 'kKKk', 'kkkk']))
    s.wrap('rleg', leg)
    s.wrap('lleg', mirror_wrap(leg))
    for part in ('rleg', 'lleg'):
        s.fill(part, 'top', 'q')
        s.fill(part, 'bottom', 'k')
    skirt = ['....' * 4] * 6 + hstack(
        layer(bands([1, 2, 3, 2], [0, -1, -1, 0], CRIMSON), ['....', '....', '....', '6787']),
        layer(bands([2, 3, 2, 2], [0, -1, -1, 0], CRIMSON), ['..7I', '..6I', '..6I', '6787']),
        ['....'] * 4,
        layer(bands([1, 2, 2, 1], [0, -1, -1, 0], CRIMSON), ['....', '....', '....', '5665'])) + ['....' * 4] * 2
    s.wrap('rpants', skirt)
    s.wrap('lpants', mirror_wrap(skirt))
    return s


# Frame 3 is a copy of the resting frame 0, and the rest alternates between them. GeckoLib only re-uploads an animated
# skin when the frame index changes, and while the glow layer first loads, AutoGlowingTexture puts the whole stacked
# image into the base texture (and leaves the glow texture empty) until that next upload: a long run of one index would
# show a squashed, unlit acolyte for seconds. Alternating keeps that to a tick or two.
ACOLYTE_FRAMES = [0, 1, 2, 0]
ACOLYTE_ANIM = [0, 3] * 14 + [1, 2, 2, 2, 1]


# ============================================================================ Valkyrie wings
WING_W, WING_H = 40, 44
WHITE = [rgba(c) for c in ('#4A5476', '#8E9ABA', '#C3CCE1', '#E6EBF5', '#FFFFFF')]
GOLD = [rgba(c) for c in ('#6A3C0E', '#A8681A', '#DCA232', '#F6D166', '#FFF0BA')]


def wing_top(x):
    """Leading edge of the wing (texel row): rises from the shoulder to the wrist, then falls to the tip."""
    pts = [(0, 6.0), (10, 3.0), (20, 1.0), (28, 0.6), (34, 1.6), (40, 4.0)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        if xa <= x <= xb:
            return ya + (yb - ya) * (x - xa) / (xb - xa)
    return pts[-1][1]


def valkyrie_wings():
    """Right wing seen from behind: root at the left edge, leading edge at the top, tip at the right.

    Every feather is a column of whole texels: a lit left edge, a quill line, a body and a rounded tip. Rows are
    drawn from the trailing edge up (flight feathers, greater coverts, gold-tipped median coverts, the gold leading
    edge), so each row's scalloped tips lie over the row below and cast a one-texel shadow on it.
    """
    H, W = WING_H, WING_W
    ids = np.full((H, W), -1)
    lv = np.zeros((H, W), int)
    gold = np.zeros((H, W), bool)
    order = [0]

    def feather(x0, y0, y1, width, slope=0.0, quill=True, gold_tip=0):
        fid = order[0]
        order[0] += 1
        for y in range(int(round(y0)), y1 + 1):
            if y < 0 or y >= H:
                continue
            xl = x0 + int(math.floor((y - y0) * slope + 0.5))
            lo, hi = xl, xl + width
            if y == y1:                       # rounded tip
                lo, hi = lo + 1, hi - 1
            for x in range(lo, hi):
                if not 0 <= x < W:
                    continue
                c = x - xl
                level = 4 if c < 2 else 3
                if c == width - 1:
                    level = 2
                elif quill and c == 2 and y0 + 3 < y < y1 - 3:
                    level = 4                     # the quill catches the light
                if quill and y >= y1 - 1:
                    level = min(level, 2) if c else 3     # flight feather tips turn a little grey
                ids[y, x] = fid
                lv[y, x] = level
                gold[y, x] = bool(gold_tip) and y > y1 - gold_tip

    def root_y(x, off):
        return wing_top(x) + off

    # primaries: outermost first, fanning slightly outward; then the secondaries hanging from the arm
    for x0, tip, width, slope in [(33, 37, 4, 0.10), (30, 41, 5, 0.09), (27, 43, 5, 0.07), (24, 42, 5, 0.05), (20, 40, 5, 0.03)]:
        feather(x0, root_y(x0 + 2, 7), tip, width, slope)
    for x0, tip in [(16, 37), (12, 35), (8, 33), (4, 31), (0, 29)]:
        feather(x0, root_y(x0 + 2, 7), tip, 5, 0.0)
    # greater coverts: staggered half a feather against the flight feathers
    for x0 in range(34, -4, -4):
        y0 = root_y(x0 + 2, 2.5)
        feather(x0, y0, int(round(y0 + 8 + (1 if x0 >= 20 else 0))), 5, 0.0, quill=False)
    # median coverts with gold tips
    for x0 in range(36, -4, -4):
        y0 = root_y(x0 + 2, 1.0)
        feather(x0, y0, int(round(y0 + 5)), 4, 0.0, quill=False, gold_tip=2)
    # the gold leading edge along the wing's arm
    fid = order[0]
    for x in range(W):
        t = int(math.floor(wing_top(x + 0.5)))
        for y in (t, t + 1, t + 2):
            ids[y, x] = fid
            lv[y, x] = 4 if y == t + 1 else 3
            gold[y, x] = True

    solid = ids >= 0
    out = lv.copy()
    for y in range(H):
        for x in range(W):
            if not solid[y, x]:
                continue
            me = ids[y, x]
            dark = light = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if dx == -1 and nx < 0:
                    continue                  # the root edge meets the body: no outline there
                other = ids[ny, nx] if (0 <= nx < W and 0 <= ny < H) else -1
                if other < 0:
                    if dx == 1 or dy == 1:
                        dark = True
                    else:
                        light = True
                elif other > me and dy == -1:
                    out[y, x] = min(out[y, x], 1)      # shadow under the tip of the feather lying over this one
            if dark:
                out[y, x] = 0
            elif light:
                out[y, x] = min(out[y, x], 1 if not gold[y, x] else 2)
    # ambient occlusion where the wing meets the back
    col0 = out[:, 0]
    out[:, 0] = np.where(col0 > 2, col0 - 1, col0)
    arr = np.zeros((H, W, 4), np.uint8)
    for i in range(5):
        arr[solid & ~gold & (out == i)] = WHITE[i]
        arr[solid & gold & (out == i)] = GOLD[i]
    return Image.fromarray(arr, 'RGBA')


# ============================================================================ sun halo, rays, core, glow
HALO_GOLD = [rgba(c) for c in ('#743408', '#C06A12', '#EBA62C', '#FFD45A', '#FFF2B0')]


def solar_halo():
    """Gold ring with eight tapered rays (long on the axes, shorter on the diagonals); alpha blended, crisp.
    The layer spins it, so the shading is radial (a pale inner band on the ring, a light spine on each ray)
    rather than lit from one side."""
    n = 32
    x, y = centers(n)
    d = np.hypot(x, y)
    ring = (d >= 6.6) & (d < 10.4)
    lv = np.full((n, n), -1)
    lv[ring] = 0
    lv[ring & (d >= 7.5) & (d < 8.5)] = 4
    lv[ring & (d >= 8.5) & (d < 9.5)] = 2
    rays = np.zeros((n, n), bool)
    half_w = np.zeros((n, n))
    perp_d = np.full((n, n), 9.0)
    for k in range(8):
        a = k * math.pi / 4
        tip = 15.8 if k % 2 == 0 else 14.0
        along = x * math.cos(a) + y * math.sin(a)
        perp = np.abs(-x * math.sin(a) + y * math.cos(a))
        t_ = np.clip((along - 9.5) / (tip - 9.5), 0, 1)
        half = 3.0 - 2.45 * t_
        m = (along > 8.5) & (along <= tip) & (perp <= half) & (d >= 9.5)
        rays |= m
        half_w = np.where(m, half, half_w)
        perp_d = np.where(m, perp, perp_d)
        tips = m & (along > tip - 1.2)
        half_w = np.where(tips, -1.0, half_w)
    shape = ring | rays
    l, r, u, dn = neighbours_outside(shape)
    boundary = shape & (l | r | u | dn)
    lv[rays] = np.where(perp_d[rays] < 0.8, 3, 2)
    edge = rays & boundary
    lv[edge] = np.where(half_w[edge] > 1.3, 0, np.where(half_w[edge] < 0, 1, 2))
    return from_levels(lv, HALO_GOLD)


def solar_rays():
    """Additive corona (premultiplied): eight long and eight short one-texel rays that step dimmer outward and
    leave the middle clear for the core. The texture's centre is a texel corner, so each axis ray hugs one side of
    it (a pinwheel, symmetric under a half turn) to stay one texel wide; the renderers spin it anyway."""
    n = 32
    x, y = centers(n)
    d = np.hypot(x, y)
    lv = np.full((n, n), -1)
    dirs = []
    for k in range(4):        # axes and diagonals (long), 2:1 slopes in between (short)
        a = k * math.pi / 2
        dirs += [(a, 14.6, 'axis'), (a + math.pi / 4, 13.0, 'diag'), (a + math.atan(0.5), 10.0, 'slope'),
                 (a + math.pi / 2 - math.atan(0.5), 10.0, 'slope')]
    for a, tip, kind in dirs:
        along = x * math.cos(a) + y * math.sin(a)
        perp = -x * math.sin(a) + y * math.cos(a)
        if kind == 'axis':
            on = (perp > 0) & (perp < 1)
        elif kind == 'diag':
            on = np.abs(perp) < 0.3
        else:
            on = np.abs(perp) < 0.5
        if kind == 'slope':
            step = np.where(d < 8.0, 1, 0)
        else:
            step = np.where(d < 7.5, 2, np.where(d < 11.0, 1, 0))
        m = (along > 4.2) & (along <= tip) & on
        lv = np.where(m, np.maximum(lv, step), lv)
    cols = [(120, 60, 18, 255), (214, 140, 56, 255), (255, 232, 160, 255)]
    return premultiply(from_levels(lv, cols))


def solar_core(frame):
    """Hard-alpha sun disc (16x16): dark rim, orange band, gold, white-hot heart; flame tongues boil around it."""
    n = 16
    x, y = centers(n)
    d = np.hypot(x, y)
    ang = np.arctan2(y, x)
    heart = [1.3, 1.7, 2.1, 1.7][frame]
    lv = np.full((n, n), -1)
    disc = d < 5.6
    lv[disc] = 1
    lv[disc & (d < 4.6)] = 2
    lv[disc & (d < 3.4)] = 3
    lv[disc & (d < heart + 1.0)] = 4
    lv[disc & (d < heart)] = 5
    # tongues of flame: eight, turning a little each frame
    tongue = np.zeros((n, n), bool)
    for k in range(8):
        a = k * math.pi / 4 + frame * math.pi / 16 + (math.pi / 8 if k % 2 else 0) * 0.0
        diff = np.abs((ang - a + math.pi) % (2 * math.pi) - math.pi)
        reach = 7.4 if k % 2 == 0 else 6.6
        tongue |= (d >= 5.6) & (d < reach) & (diff < 0.2 + 0.25 * (reach - d) / 2)
    lv[tongue] = 1
    shape = lv >= 0
    l, r, u, dn = neighbours_outside(shape)
    lv[shape & (l | r | u | dn)] = 0
    lv[(np.abs(x + 1.5) < 0.6) & (np.abs(y + 1.5) < 0.6)] = 6      # glint, upper left of the heart
    cols = [rgba(c) for c in ('#9A2C0A', '#E4601A', '#FF9C2C', '#FFCC4E', '#FFEB9A', '#FFF8DC', '#FFFFFF')]
    return from_levels(lv, cols)


def thunder_glow():
    """Additive halo: stepped discs and a faint four-point cross, neutral warm white (tinted by the renderer)."""
    n = 32
    x, y = centers(n)
    d = np.hypot(x, y)
    lv = np.full((n, n), -1)
    lv[d < 15.2] = 0
    lv[d < 11.4] = 1
    lv[d < 7.4] = 2
    lv[d < 3.6] = 3
    cross = ((np.abs(x) < 0.6) | (np.abs(y) < 0.6)) & (d < 15.6)
    lv[cross] = np.minimum(lv[cross] + 1, 3)
    lv[cross & (lv < 0)] = 0
    cols = [(22, 20, 16, 255), (50, 46, 38, 255), (98, 92, 76, 255), (214, 208, 190, 255)]
    return premultiply(from_levels(lv, cols))


def solar_bolt():
    """Small spinning eight-point sun star (16x16), alpha blended: a white-hot heart in a dark rim, orange arms
    with a gold spine and dark tips (radial shading, since the renderer spins it)."""
    n = 16
    x, y = centers(n)
    d = np.hypot(x, y)
    core = d < 3.4
    star = core.copy()
    arm_perp = np.full((n, n), 9.0)
    arm_tip = np.zeros((n, n), bool)
    for k in range(8):
        a = k * math.pi / 4
        tip = 7.8 if k % 2 == 0 else 5.8
        along = x * math.cos(a) + y * math.sin(a)
        perp = np.abs(-x * math.sin(a) + y * math.cos(a))
        t_ = np.clip((along - 2.5) / (tip - 2.5), 0, 1)
        m = (along > 0) & (along <= tip) & (perp <= 1.7 - 1.25 * t_)
        star |= m
        arm_perp = np.where(m & ~core, np.minimum(arm_perp, perp), arm_perp)
        arm_tip |= m & (along > tip - 1.3)
    arms = star & ~core
    lv = np.full((n, n), -1)
    lv[arms] = np.where(arm_perp[arms] < 0.8, 2, 1)
    lv[arms & arm_tip] = 0
    lv[core] = 2
    lv[core & (d < 2.6)] = 3
    lv[core & (d < 1.3)] = 4
    l, r, u, dn = neighbours_outside(star)
    lv[core & (l | r | u | dn)] = 0
    cols = [rgba(c) for c in ('#8A2808', '#EC6A1A', '#FFB63E', '#FFEDB4', '#FFFFFF')]
    return from_levels(lv, cols)


def zephyr_orb():
    """A wind sphere (16x16): deep sky-blue rim, glassy body (one alpha step) and two white streaks winding into
    a bright eye along an Archimedean spiral. Radially shaded: the renderer spins it, so the streaks whirl."""
    n = 16
    x, y = centers(n)
    d = np.hypot(x, y)
    ang = np.arctan2(y, x)
    disc = d < 7.2
    lv = np.full((n, n), -1)
    lv[disc] = 1
    lv[disc & (d < 5.8)] = 2
    k = 4.2 / math.pi                     # radius gained per radian of turn: each streak curls half a turn
    dist = np.full((n, n), 9.0)
    for arm in (0.0, math.pi):
        th = (ang - arm) % (2 * math.pi)
        dist = np.minimum(dist, np.abs(d - (2.0 + k * th)))
    width = 0.25 + 0.11 * d               # comma-shaped: thin where it curls in, broad at the rim
    streak = disc & (d > 2.0) & (d < 6.3) & (dist < width)
    lv[streak] = 3
    lv[streak & (dist < width - 0.45) & (d > 2.8)] = 4
    lv[d < 1.0] = 4                       # the eye
    lv[(d >= 1.0) & (d < 1.6)] = 3
    l, r, u, dn = neighbours_outside(disc)
    lv[disc & (l | r | u | dn)] = 0
    cols = [rgba('#2D6496'), rgba('#7EC2EC', 230), rgba('#B6E2F8', 165), rgba('#D2F0FF'), rgba('#FFFFFF')]
    return from_levels(lv, cols)


def thunder_crystal(frame):
    """Ambrosium-gold crystal (16x16), four frames. The ridge between the two facets facing the viewer slides
    across, so the flat sprite seems to turn like the spinning shell drawn around it; a glint rides the ridge."""
    n = 16
    x, y = centers(n)
    shape = (np.abs(x) / 4.7 + np.abs(y) / 7.7) <= 1.0
    edge_x = [-2.6, -0.9, 0.9, 2.6][frame]
    ex = edge_x * (1 - np.abs(y) / 7.7)     # the ridge converges on the top and bottom points
    left = x < ex
    upper = y < 0
    lv = np.full((n, n), -1)
    lv[shape & left & upper] = 3
    lv[shape & ~left & upper] = 2
    lv[shape & left & ~upper] = 2
    lv[shape & ~left & ~upper] = 1
    ridge = shape & (np.abs(x - ex) < 0.5)
    lv[ridge & upper] = 4
    lv[ridge & ~upper] = 3
    l, r, u, dn = neighbours_outside(shape)
    outline = shape & (l | r | u | dn)
    # a bright rim just inside the upper-left outline, a dark one inside the lower-right
    pl, pr, pu, pd = neighbours_outside(shape & ~outline)
    inner = shape & ~outline
    lv[inner & (pl | pu) & upper & left] = 4
    lv[inner & (pr | pd) & ~upper & ~left] = 0
    lv[outline] = 0
    if frame in (1, 2):
        gx = int(round(8 + edge_x * 0.55 - 0.5))
        lv[4, gx] = 5
        lv[5, gx] = 4
    cols = [rgba(c) for c in ('#6E3C04', '#B86E0E', '#ECB02A', '#FFDE6A', '#FFF6C4', '#FFFFFF')]
    return from_levels(lv, cols)


# ============================================================================ particles
PART = palette({
    # sparkle: white, pale gold, gold, dim gold (alpha step)
    'W': '#FFFFFF', 'Y': '#FFF0A8', 'a': '#F0BE3E', 'b': rgba('#E8B040', 150),
    # feather: outline, shade, white, quill, underside shade
    'o': '#7A86A8', 'w': '#C6CEE2', 'V': '#FFFFFF', 'q': '#C89A48', 'u': '#9AA6C4',
    # gravitite mote: outline, mid, light, highlight, ring (alpha step)
    'M': '#6E1A68', 'm': '#C83AB2', 'L': '#F27ADA', 'H': '#FFD2F6', 'r': rgba('#E25CCB', 150),
})

SPARKLE = [
    ['........', '........', '...a....', '..aWa...', '...a....', '........', '........', '........'],
    ['........', '...a....', '...Y....', '.aYWYa..', '...Y....', '...a....', '........', '........'],
    ['...a....', '...Y....', '.b.Y.b..', 'aYYWYYa.', '.b.Y.b..', '...Y....', '...a....', '........'],
    ['........', '........', '...b....', '..bYb...', '...b....', '........', '........', '........'],
]

FEATHER = [
    ['.....oo.', '....oVVo', '...oVVwo', '..oVVwo.', '.oVVwo..', '.oVwo...', '.qoo....', 'q.......'],
    ['......o.', '.....oVo', '....oVwo', '...oVwo.', '..oVwo..', '..oVo...', '.qo.....', 'q.......'],
    ['.......o', '......ow', '.....ow.', '....ow..', '...ow...', '..ow....', '.q......', 'q.......'],
    ['.....oo.', '....owwo', '...owwuo', '..owwuo.', '.owwuo..', '.owuo...', '.qoo....', 'q.......'],
]

MOTE = [
    ['........', '........', '...m....', '..mLm...', '...m....', '........', '........', '........'],
    ['........', '...M....', '..mLm...', '.MLHLM..', '..mLm...', '...M....', '........', '........'],
    ['...r....', '.r.M.r..', '..mLm...', 'rMLHLMr.', '..mLm...', '.r.M.r..', '...r....', '........'],
    ['..rrr...', '.r...r..', 'r..m..r.', 'r.mLm.r.', 'r..m..r.', '.r...r..', '..rrr...', '........'],
]

CLOUD = [rgba('#9EAAC2'), rgba('#C9D3E4'), rgba('#EDF2FA'), rgba('#FFFFFF')]


def cloud_frame(blobs, alpha):
    """A puff built from discs: lit rim at the top-left, shade band and darker edge at the bottom-right."""
    n = 16
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float64) + 0.5
    m = np.zeros((n, n), bool)
    for cx, cy, r in blobs:
        m |= np.hypot(xs - cx, ys - cy) < r
    lv = np.full((n, n), -1)
    lv[m] = 2

    def shifted(dx, dy):
        p = np.pad(m, 2, constant_values=False)
        return p[2 + dy:2 + dy + n, 2 + dx:2 + dx + n]

    lv[m & ~shifted(-1, -1)] = 3          # top-left rim catches the light
    lv[m & ~shifted(2, 2)] = 1            # shade band toward the bottom-right
    lv[m & ~shifted(1, 1)] = 0
    lv[m & ~shifted(0, 1) & ~shifted(1, 0)] = 0
    return from_levels(lv, CLOUD, alpha=np.where(lv >= 0, alpha, 0).astype(np.uint8))


def cloud_puffs():
    return [
        cloud_frame([(8, 8.6, 4.2), (5.4, 9.4, 2.8), (10.8, 9.2, 3.0), (7.0, 6.2, 2.6), (9.8, 6.6, 2.4)], 255),
        cloud_frame([(8, 8.4, 4.6), (4.4, 9.4, 3.2), (11.8, 9.0, 3.2), (6.2, 5.4, 3.0), (10.4, 5.8, 2.8)], 235),
        cloud_frame([(4.6, 9.2, 3.1), (11.4, 8.4, 3.2), (7.2, 4.6, 2.6), (10.6, 12.6, 1.8)], 200),
        cloud_frame([(4.0, 10.4, 2.1), (12.2, 7.6, 2.3), (7.6, 3.8, 1.6)], 160),
    ]


# ============================================================================ outputs
def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG', optimize=False, compress_level=9)
    return buf.getvalue()


def build():
    """All exported files: path relative to the assets root -> bytes, plus the images for the previews."""
    imgs = {}
    files = {}

    def tex(rel, img, meta=None):
        imgs[rel] = img
        files['textures/' + rel + '.png'] = png_bytes(img)
        if meta:
            files['textures/' + rel + '.png.mcmeta'] = meta.encode()

    tex('entity/valkyrie_sorceress', stack_frames(valkyrie_frames()), mcmeta(2, VALKYRIE_ANIM))
    acolytes = [acolyte_skin(f) for f in ACOLYTE_FRAMES]
    tex('entity/solar_acolyte', stack_frames([a.img for a in acolytes]), mcmeta(2, ACOLYTE_ANIM))
    tex('entity/solar_acolyte_glowmask', stack_frames([a.glow for a in acolytes]))
    tex('entity/valkyrie_wings', valkyrie_wings())
    tex('entity/solar_halo', solar_halo())
    tex('entity/solar_rays', solar_rays())
    tex('entity/solar_core', stack_frames([solar_core(f) for f in range(4)]), mcmeta(2, [0, 1, 2, 3]))
    tex('entity/thunder_glow', thunder_glow())
    tex('entity/solar_bolt', solar_bolt())
    tex('entity/zephyr_orb', zephyr_orb())
    tex('entity/thunder_crystal', stack_frames([thunder_crystal(f) for f in range(4)]), mcmeta(2, [0, 1, 2, 3]))
    for i, rows in enumerate(SPARKLE):
        tex(f'particle/sky_sparkle_{i}', grid(rows, PART))
    for i, rows in enumerate(FEATHER):
        tex(f'particle/feather_{i}', grid(rows, PART))
    for i, rows in enumerate(MOTE):
        tex(f'particle/gravity_mote_{i}', grid(rows, PART))
    for i, img in enumerate(cloud_puffs()):
        tex(f'particle/cloud_puff_{i}', img)
    # drawn additively (RenderType.eyes): anything with RGB where it is transparent would add a visible square
    for rel in ('entity/solar_rays', 'entity/thunder_glow', 'entity/solar_core'):
        a = np.asarray(imgs[rel])
        assert not a[a[..., 3] == 0][:, :3].any(), rel
    # the glowmask must only mark texels the skin actually has
    sk, gm = np.asarray(imgs['entity/solar_acolyte']), np.asarray(imgs['entity/solar_acolyte_glowmask'])
    assert sk.shape == gm.shape and not ((gm[..., 3] > 0) & (sk[..., 3] == 0)).any()
    return files, imgs


STALE = ['textures/entity/valkyrie_wings.png.mcmeta', 'textures/entity/solar_halo.png.mcmeta',
         'textures/entity/solar_rays.png.mcmeta', 'textures/entity/thunder_glow.png.mcmeta',
         'textures/entity/solar_bolt.png.mcmeta', 'textures/entity/zephyr_orb.png.mcmeta',
         'textures/entity/solar_acolyte_glowmask.png.mcmeta']


# ============================================================================ preview: software renderer
def frame0(img, w=None):
    w = w or img.width
    return img.crop((0, 0, w, w)) if img.height > w and img.height % w == 0 else img


class Raster:
    """Orthographic textured-quad rasteriser with a z-buffer. Model space: x to the viewer's right when the mob
    faces the camera (the mob's left), y up, z out of the mob's front; units are texels."""

    def __init__(self, w, h, scale, yaw, pitch, center, bg):
        self.w, self.h, self.scale = w, h, scale
        cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
        ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
        self.view = rx @ ry
        self.center = np.asarray(center, float)
        self.rgb = np.zeros((h, w, 3), np.float32)
        self.rgb[:] = np.asarray(bg[:3], np.float32) / 255
        self.z = np.full((h, w), -np.inf)

    def project(self, p):
        v = self.view @ (np.asarray(p, float) - self.center)
        return np.array([self.w / 2 + v[0] * self.scale, self.h / 2 - v[1] * self.scale]), v[2]

    def quad(self, tex, rect, p0, ex, ey, light=1.0, mode='cutout', tint=(1, 1, 1, 1)):
        a, za = self.project(p0)
        b, zb = self.project(np.add(p0, ex))
        c, zc = self.project(np.add(p0, ey))
        e1, e2 = b - a, c - a
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            return
        corners = np.array([a, b, c, b + e2])
        lo = np.clip(np.floor(corners.min(0)).astype(int), 0, [self.w, self.h])
        hi = np.clip(np.ceil(corners.max(0)).astype(int), 0, [self.w, self.h])
        if lo[0] >= hi[0] or lo[1] >= hi[1]:
            return
        gy, gx = np.mgrid[lo[1]:hi[1], lo[0]:hi[0]].astype(float) + 0.5
        dx, dy = gx - a[0], gy - a[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        x, y, w, h = rect
        tx = np.clip((x + s * w).astype(int), x, x + w - 1)
        ty = np.clip((y + t * h).astype(int), y, y + h - 1)
        texel = tex[ty, tx] * np.asarray(tint, np.float32)
        z = za + s * (zb - za) + t * (zc - za)
        zbuf = self.z[lo[1]:hi[1], lo[0]:hi[0]]
        region = self.rgb[lo[1]:hi[1], lo[0]:hi[0]]
        if mode == 'add':
            m = inside & (z >= zbuf - 1e-6)
            region[m] = np.clip(region[m] + texel[m, :3], 0, 1)
        elif mode == 'blend':
            m = inside & (z >= zbuf - 1e-6) & (texel[..., 3] > 0.1)
            al = texel[m, 3:4]
            region[m] = region[m] * (1 - al) + texel[m, :3] * light * al
        else:
            m = inside & (texel[..., 3] > 0.1) & (z > zbuf)
            zbuf[m] = z[m]
            region[m] = texel[m, :3] * light

    def image(self):
        return Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8), 'RGB')


MODEL = [  # base part, overlay part, box origin (texels), size
    ('head', 'hat', (-4, 24, -4), (8, 8, 8)),
    ('body', 'jacket', (-4, 12, -2), (8, 12, 4)),
    ('rarm', 'rsleeve', (-8, 12, -2), (4, 12, 4)),
    ('larm', 'lsleeve', (4, 12, -2), (4, 12, 4)),
    ('rleg', 'rpants', (-4, 0, -2), (4, 12, 4)),
    ('lleg', 'lpants', (0, 0, -2), (4, 12, 4)),
]
FACE_SHADE = {'top': 1.0, 'front': 0.92, 'back': 0.86, 'right': 0.76, 'left': 0.76, 'bottom': 0.55}


def box_faces(origin, size, inf):
    x0, y0, z0 = (o - inf for o in origin)
    w, h, d = (q + 2 * inf for q in size)
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    return {'front': ((x0, y1, z1), (w, 0, 0), (0, -h, 0)), 'back': ((x1, y1, z0), (-w, 0, 0), (0, -h, 0)),
            'right': ((x0, y1, z0), (0, 0, d), (0, -h, 0)), 'left': ((x1, y1, z1), (0, 0, -d), (0, -h, 0)),
            'top': ((x0, y1, z0), (w, 0, 0), (0, 0, d)), 'bottom': ((x0, y0, z1), (w, 0, 0), (0, 0, -d))}


def render_skin(r, skin):
    tex = np.asarray(skin.convert('RGBA'), np.float32) / 255
    for base, over, origin, size in MODEL:
        for part, inf in ((base, 0.0), (over, 0.25)):
            for face, (p0, ex, ey) in box_faces(origin, size, inf).items():
                r.quad(tex, face_rect(part, face), p0, ex, ey, FACE_SHADE[face])


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def render_wings(r, wings, spread, phase):
    """Mirrors ValkyrieWingsLayer (its bone space: blocks, +Z out of the back; converted to texels, z flipped)."""
    tex = np.asarray(wings, np.float32) / 255
    lerp = lambda t, a, b: a + (b - a) * t
    beat = math.sin(phase)
    sweep = lerp(spread, 1.25, 0.25) + beat * lerp(spread, 0.04, 0.28)
    lift = lerp(spread, 0.10, 0.30) + beat * lerp(spread, 0.05, 0.40)
    fold = lerp(spread, 0.35, 0.08) + math.sin(phase - 0.9) * lerp(spread, 0.03, 0.22)
    pw, top, bottom = 0.62, 0.42, -0.98
    conv = np.diag([16.0, 16.0, -16.0])
    for side in (-1, 1):
        m1 = _ry(-side * sweep) @ _rz(side * lift)
        o1 = np.array([side * 0.06, 1.36, 0.17])
        m2 = m1 @ _ry(-side * fold) @ _rz(-side * fold * 0.35)
        o2 = o1 + m1 @ np.array([side * pw, 0, 0])
        for o, m, u0 in ((o1, m1, 0), (o2, m2, WING_W // 2)):
            p0 = conv @ (o + m @ np.array([0, top, 0]))
            ex = conv @ (m @ np.array([side * pw, 0, 0]))
            ey = conv @ (m @ np.array([0, bottom - top, 0]))
            r.quad(tex, (u0, 0, WING_W // 2, WING_H), p0, ex, ey, 0.95)


def render_halo(r, halo, rays, core, glow=200):
    """Mirrors SolarHaloLayer at rest (head bone; quads in the XY plane behind the head)."""
    c = np.array([0, 1.78 * 16, -0.31 * 16])
    for img, size, dz, mode, tint in ((halo, 1.3, 0, 'blend', (1, 1, 1, 235 / 255)),
                                      (rays, 1.5, -0.16, 'add', (glow / 255, glow * 0.8 / 255, glow * 0.45 / 255, 1)),
                                      (core, 0.55, -0.32, 'add', (glow / 255, glow * 0.85 / 255, glow * 0.6 / 255, 1))):
        tex = np.asarray(frame0(img), np.float32) / 255
        half = size * 8
        p0 = c + np.array([-half, half, dz])
        r.quad(tex, (0, 0, tex.shape[1], tex.shape[0]), p0, (2 * half, 0, 0), (0, -2 * half, 0), 1.0, mode, tint)


SKY = (146, 186, 236)
DUSK = (44, 48, 60)


def mob_views(skin, extra=None, views=None, bg=SKY, scale=7.0):
    views = views or [('front', 0, 8), ('back', 180, 10), ('side', 90, 6), ('3/4', 35, 14)]
    out = []
    for label, yaw, pitch in views:
        r = Raster(300, 300, scale, yaw, pitch, (0, 17, 0), bg)
        render_skin(r, skin)
        if extra:
            extra(r, label)
        out.append((label, r.image()))
    return out


# ============================================================================ preview: sheets
def up(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def on_bg(img, bg):
    base = Image.new('RGBA', img.size, bg + (255,) if len(bg) == 3 else bg)
    base.alpha_composite(img.convert('RGBA'))
    return base


def add_bg(img, bg):
    """An additive (premultiplied) texture over a background, as RenderType.eyes draws it."""
    a = np.asarray(img.convert('RGBA')).astype(np.int32)
    out = np.clip(a[..., :3] + np.asarray(bg[:3]), 0, 255).astype(np.uint8)
    return Image.fromarray(out, 'RGB').convert('RGBA')


def label_sheet(cells, cols, pad=10, bg=(60, 62, 70), title=None):
    """cells: list of (label, image). Lays them out in a grid with labels."""
    cw = max(c[1].width for c in cells) + pad
    ch = max(c[1].height for c in cells) + pad + 14
    rows = (len(cells) + cols - 1) // cols
    top = 22 if title else 0
    sheet = Image.new('RGB', (cols * cw + pad, rows * ch + pad + top), bg)
    d = ImageDraw.Draw(sheet)
    if title:
        d.text((pad, 4), title, fill=(255, 255, 255))
    for i, (lab, img) in enumerate(cells):
        x, y = pad + (i % cols) * cw, top + pad + (i // cols) * ch
        d.text((x, y), lab, fill=(235, 235, 235))
        sheet.paste(img.convert('RGB'), (x, y + 14))
    return sheet


def load_before(rel):
    p = BEFORE / rel
    return Image.open(p).convert('RGBA') if p.exists() else None


def load_ref(rel):
    p = REF / rel
    return Image.open(p).convert('RGBA') if p.exists() else None


def preview_mobs(imgs):
    valk = imgs['entity/valkyrie_sorceress']
    aco = imgs['entity/solar_acolyte']
    wings = imgs['entity/valkyrie_wings']
    halo, rays, core = imgs['entity/solar_halo'], imgs['entity/solar_rays'], imgs['entity/solar_core']
    rows = []
    v0 = frame0(valk, 64)
    a0 = frame0(aco, 64)
    rows.append(('valkyrie_sorceress (wings folded / spread)',
                 mob_views(v0, lambda r, lab: render_wings(r, wings, 0.0, 0.0))
                 + mob_views(v0, lambda r, lab: render_wings(r, wings, 1.0, 1.4), [('spread front', 0, 8), ('spread back', 180, 14)])))
    rows.append(('solar_acolyte (halo at rest)', mob_views(a0, lambda r, lab: render_halo(r, halo, rays, core))
                 + mob_views(frame0(aco.crop((0, 128, 64, 192))), lambda r, lab: render_halo(r, halo, rays, core, 230),
                             [('pulse front', 0, 8)])
                 + mob_views(a0, lambda r, lab: render_halo(r, halo, rays, core), [('dusk front', 0, 8)], bg=DUSK)))
    # references rendered by the same renderer (Iron's Spells casters) and the previous art
    refs = []
    for name in ('cryomancer.png', 'pyromancer.png', 'priest/priest.png', 'archevoker.png'):
        im = load_ref('iss/assets/irons_spellbooks/textures/entity/' + name)
        if im is not None:
            refs += [('ISS ' + name.split('/')[-1][:-4] + ' ' + lab, img) for lab, img in mob_views(im, views=[('front', 0, 8), ('3/4', 35, 14)])]
    for name in ('valkyrie_sorceress.png', 'solar_acolyte.png'):
        im = load_before('entity/' + name)
        if im is not None:
            refs += [('before ' + name[:-4] + ' ' + lab, img) for lab, img in mob_views(im, views=[('front', 0, 8)])]
    sheets = []
    for title, cells in rows:
        sheets.append(label_sheet([(lab, img) for lab, img in cells], 6, title=title))
    if refs:
        sheets.append(label_sheet(refs, 6, title='references (same renderer)'))
    w = max(s.width for s in sheets)
    out = Image.new('RGB', (w, sum(s.height for s in sheets)), (60, 62, 70))
    y = 0
    for s in sheets:
        out.paste(s, (0, y))
        y += s.height
    out.save(PREVIEW / 'mobs.png')
    # texture sheets at 6x: frames side by side, plus the glowmask
    cells = [('valkyrie f%d' % i, up(on_bg(valk.crop((0, 64 * i, 64, 64 * i + 64)), (90, 90, 100)), 6)) for i in range(4)]
    cells += [('acolyte f%d' % i, up(on_bg(aco.crop((0, 64 * i, 64, 64 * i + 64)), (90, 90, 100)), 6)) for i in range(3)]
    gm = imgs['entity/solar_acolyte_glowmask']
    cells += [('glowmask f%d' % i, up(on_bg(gm.crop((0, 64 * i, 64, 64 * i + 64)), (30, 30, 36)), 6)) for i in range(3)]
    cells += [('valkyrie_wings', up(on_bg(wings, SKY), 8))]
    b = load_before('entity/valkyrie_wings.png')
    if b is not None:
        cells += [('before wings', up(on_bg(b, SKY), 8))]
    label_sheet(cells, 4).save(PREVIEW / 'skins.png')


def composite(layers, size_px, ppb, bg):
    """Billboard layers like the renderers draw them. layers: (image, size in blocks, mode, tint rgba 0..1, angle)."""
    r = Raster(size_px, size_px, ppb / 16.0, 0, 0, (0, 0, 0), bg)
    for img, size, mode, tint, angle in layers:
        tex = np.asarray(img, np.float32) / 255
        half = size * 8
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        ex = np.array([2 * half * c, 2 * half * s, 0])
        ey = np.array([2 * half * s, -2 * half * c, 0])
        p0 = -ex / 2 - ey / 2
        r.quad(tex, (0, 0, tex.shape[1], tex.shape[0]), p0, ex, ey, 1.0, mode, tint)
    return r.image()


def preview_sprites(imgs):
    g = imgs['entity/thunder_glow']
    core = imgs['entity/solar_core']
    rays = imgs['entity/solar_rays']
    cells = []
    for name, k in (('solar_halo', 8), ('solar_rays', 8), ('thunder_glow', 8), ('solar_bolt', 12), ('zephyr_orb', 12)):
        im = imgs['entity/' + name]
        show = add_bg if name in ('solar_rays', 'thunder_glow') else on_bg
        cells.append((name + ' (sky)', up(show(im, SKY), k)))
        cells.append((name + ' (dark)', up(show(im, DUSK), k)))
        b = load_before('entity/%s.png' % name)
        if b is not None:
            kk = max(1, k * im.width // b.width)
            cells.append(('before ' + name, up(on_bg(b, SKY), kk)))
    for name in ('solar_core', 'thunder_crystal'):
        im = imgs['entity/' + name]
        strip = Image.new('RGBA', (im.width * 4 + 6, im.width), (0, 0, 0, 0))
        for i in range(im.height // im.width):
            strip.paste(im.crop((0, i * im.width, im.width, (i + 1) * im.width)), (i * (im.width + 2), 0))
        cells.append((name + ' frames (sky)', up(on_bg(strip, SKY), 8)))
        cells.append((name + ' frames (dark)', up(on_bg(strip, DUSK), 8)))
    # in-game style composites (as the renderers layer them), on the bright Aether sky
    c0 = frame0(core)
    crystal = frame0(imgs['entity/thunder_crystal'])
    comps = [
        ('solar orb', [(g, 4.2, 'add', (1, 170 / 255, 80 / 255, 1), 0), (rays, 3.4, 'add', (1, 200 / 255, 90 / 255, 1), 20),
                       (rays, 2.6, 'add', (1, 230 / 255, 140 / 255, 1), -30), (c0, 1.5, 'blend', (1, 1, 1, 1), 10)], 5),
        ('solar bolt', [(g, 1.4 * 0.6 / 0.6, 'add', (1, 1, 1, 1), 0), (imgs['entity/solar_bolt'], 0.6, 'blend', (1, 1, 1, 1), 15)], 1.6),
        ('zephyr orb', [(imgs['entity/zephyr_orb'], 1.0, 'blend', (1, 1, 1, 1), 25)], 1.4),
        ('thunder crystal', [(g, 1.3, 'add', (1, 240 / 255, 170 / 255, 1), 0), (crystal, 0.8, 'blend', (1, 1, 1, 1), 0)], 1.6),
    ]
    for name, layers, extent in comps:
        for bg, bgn in ((SKY, 'sky'), (DUSK, 'dark')):
            big = composite(layers, 192, 192 / extent, bg)
            small = composite(layers, 48, 48 / extent, bg)
            pair = Image.new('RGB', (192 + 8 + 96, 192), bg)
            pair.paste(big, (0, 0))
            pair.paste(up(small, 2), (200, 0))
            cells.append(('%s in game (%s) big / ~4 blocks' % (name, bgn), pair))
    label_sheet(cells, 4).save(PREVIEW / 'sprites.png')


def preview_particles(imgs):
    cells = []
    vanilla = REF / 'vanilla/assets/minecraft/textures/particle'
    for name, n, k in (('sky_sparkle', 8, 12), ('feather', 8, 12), ('gravity_mote', 8, 12), ('cloud_puff', 16, 8)):
        strip = Image.new('RGBA', (n * 4 + 6, n), (0, 0, 0, 0))
        bstrip = Image.new('RGBA', (16 * 4 + 6, 16), (0, 0, 0, 0))
        for i in range(4):
            strip.paste(imgs['particle/%s_%d' % (name, i)], (i * (n + 2), 0))
            b = load_before('particle/%s_%d.png' % (name, i))
            if b is not None:
                bstrip.paste(b, (i * (b.width + 2), 0))
        cells.append((name + ' 0..3 (sky)', up(on_bg(strip, SKY), k)))
        cells.append((name + ' 0..3 (dark)', up(on_bg(strip, DUSK), k)))
        cells.append(('before ' + name, up(on_bg(bstrip.crop((0, 0, (n if name == 'cloud_puff' else 8) * 4 + 6, n)), SKY), k)))
    refs = Image.new('RGBA', (8 * 10 + 18, 8), (0, 0, 0, 0))
    x = 0
    for nm in ('glow', 'spark_2', 'spark_4', 'spark_6', 'cherry_0', 'cherry_5', 'generic_3', 'glitter_4', 'effect_3', 'critical_hit'):
        p = vanilla / (nm + '.png')
        if p.exists():
            refs.paste(Image.open(p).convert('RGBA').crop((0, 0, 8, 8)), (x, 0))
        x += 10
    cells.append(('vanilla refs: glow spark cherry generic glitter effect crit', up(on_bg(refs, SKY), 12)))
    label_sheet(cells, 3).save(PREVIEW / 'particles.png')
    # 1x and 2x in-place check against vanilla scale
    gif_frames = []
    for t in range(12):
        canvas = Image.new('RGBA', (160, 48), SKY + (255,))
        for j, name in enumerate(('sky_sparkle', 'feather', 'gravity_mote', 'cloud_puff')):
            idx = {'sky_sparkle': min(3, t // 3), 'feather': [0, 1, 2, 3, 2, 1][(t // 2) % 6],
                   'gravity_mote': t % 4, 'cloud_puff': min(3, t // 3)}[name]
            im = imgs['particle/%s_%d' % (name, idx)]
            canvas.alpha_composite(up(im, 32 // im.width), (8 + j * 38, 8))
        gif_frames.append(canvas.convert('RGB'))
    gif_frames[0].save(PREVIEW / 'particles_anim.gif', save_all=True, append_images=gif_frames[1:], duration=100, loop=0)


def preview_anim(imgs):
    """GIFs of the animated textures, played with their mcmeta timing (1 tick = 50 ms)."""
    def play(img, size, frames, frametime, k, bg, name):
        out = []
        for f in frames:
            out.append(up(on_bg(img.crop((0, f * size, img.width, f * size + size)), bg), k).convert('RGB'))
        out[0].save(PREVIEW / name, save_all=True, append_images=out[1:], duration=frametime * 50, loop=0)

    play(imgs['entity/solar_core'], 16, [0, 1, 2, 3] * 4, 2, 12, DUSK, 'solar_core.gif')
    play(imgs['entity/thunder_crystal'], 16, [0, 1, 2, 3] * 4, 2, 12, SKY, 'thunder_crystal.gif')
    # skins: the face / chest region, enlarged
    valk = imgs['entity/valkyrie_sorceress']
    aco = imgs['entity/solar_acolyte']
    out = []
    for f in VALKYRIE_ANIM:
        head = valk.crop((8, 64 * f + 8, 16, 64 * f + 16))
        hat = valk.crop((40, 64 * f + 8, 48, 64 * f + 16))
        im = on_bg(head, (90, 90, 100))
        im.alpha_composite(hat)
        out.append(up(im, 16).convert('RGB'))
    out[0].save(PREVIEW / 'valkyrie_face.gif', save_all=True, append_images=out[1:], duration=100, loop=0)
    out = []
    for f in ACOLYTE_ANIM:
        chest = aco.crop((20, 64 * f + 20, 28, 64 * f + 32))
        out.append(up(on_bg(chest, (90, 90, 100)), 16).convert('RGB'))
    out[0].save(PREVIEW / 'acolyte_sigil.gif', save_all=True, append_images=out[1:], duration=100, loop=0)


# ============================================================================ main
def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='verify the exported files without writing anything')
    ap.add_argument('--no-preview', action='store_true')
    args = ap.parse_args()
    files, imgs = build()
    if args.check:
        bad = []
        for rel, data in sorted(files.items()):
            p = ASSETS / rel
            if not p.exists() or p.read_bytes() != data:
                bad.append(rel)
        bad += [rel for rel in STALE if (ASSETS / rel).exists()]
        for rel in bad:
            print('MISMATCH', rel)
        print('%d files checked, %d mismatched' % (len(files), len(bad)))
        sys.exit(1 if bad else 0)
    for rel, data in sorted(files.items()):
        p = ASSETS / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        if not p.exists() or p.read_bytes() != data:
            p.write_bytes(data)
            print('wrote', rel)
    for rel in STALE:
        if (ASSETS / rel).exists():
            (ASSETS / rel).unlink()
            print('removed', rel)
    if not args.no_preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        preview_mobs(imgs)
        preview_sprites(imgs)
        preview_particles(imgs)
        preview_anim(imgs)
        print('previews in', PREVIEW)


if __name__ == '__main__':
    main()
