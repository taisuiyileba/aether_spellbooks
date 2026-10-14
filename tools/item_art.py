"""Hand-placed 16x16 item icons for Aether Spellbooks (1.21.1): the four spellblades and the smithing icons.

Writes into src/main/resources/assets/aether_spellbooks/textures/item/:
  sunfire_spellblade, stormcaller_spellblade, hallowed_spellblade, sanguine_spellblade   (+ .png.mcmeta)
  aether_arcane_upgrade_smithing_template                                                (+ .png.mcmeta)
  empty_slot_rune                                                                        (static ghost icon)
The ring, pendant, gloves and mantle icons are kept as their original hand-made PNGs (the owner preferred them) and
are not written here; FROZEN lists them so the preview sheet still shows them next to their references.

Every character in the maps below is one deliberately placed texel ('.' = transparent). Each map has its own
palette; shared letters keep the same role everywhere:
  o / O   lit / shadowed outline of a blade        A / B   outline of metal (gold, steel)
  1..6    blade or cloth ramp, dark -> light        a..e    metal ramp, dark -> light
  R Q r   rune fuller (glow / mid / groove)         x y z w gem ramp, dark -> light (w = glint)
  K k l m grip wrap (outline, dark -> light)

Conventions followed (only silhouettes and composition are shared with the host games; all pixels are original):
  * spellblades: vanilla/Aether sword composition - hilt bottom-left, tip top-right at 45 degrees, using the full
    diagonal. Blade lanes run parallel to the axis: lit edge, rune fuller, shadowed edge. A 2x2 gem sits where the
    blade meets the guard (like Iron's Spells' Truthseeker), the guard arms are mirror images across the blade
    axis, grip = two-lane wrap, pommel = 2x2 gem/knob in the corner.
  * template: vanilla smithing template layout - upright stone tablet with a motif.
  * empty_slot_rune: vanilla empty-slot ghost style - one 1px #555555 line drawing, everything else transparent.
Light comes from the top-left; outlines are the darkest tone of their material, never pure black.

Animations (vertical frame strips + .png.mcmeta, frame 0 = the resting icon shown most of the time). Only interior
texels change between frames, so the alpha silhouette is identical in every frame (no jitter in the inventory):
  spellblades - a glint runs along the rune fuller every ~3.5 s (sanguine: blood light runs down into the gem),
  template - rune glow pulse.

Run:   python tools/item_art.py            (writes the textures and the preview sheets)
       python tools/item_art.py --check    (re-renders in memory, verifies the exported files are identical)
Previews go to build/art_preview/items/ (zoomed sheets next to the reference icons, frame strips, GIFs).
"""
import argparse
import tempfile
import os
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
TEX = ASSETS / 'textures/item'
PREVIEW = ROOT / 'build/art_preview/items'
REF = Path(os.environ.get('ASB_REF', str(Path(tempfile.gettempdir()) / 'asb_ref')))  # optional, previews only


def rgba(h):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def render(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), rgba(pal[ch]))
    return im


def apply(rows, changes):
    """Copy of a map with {(x, y): char} replacements (used for animation frames)."""
    grid = [list(r) for r in rows]
    for (x, y), ch in changes.items():
        assert grid[y][x] != '.', ('animation must not touch transparent texels', x, y)
        grid[y][x] = ch
    return [''.join(r) for r in grid]


# =====================================================================================================================
# SPELLBLADES
# =====================================================================================================================
# All four share the hilt frame: gem (4..5, 10..11), grip (3,11) (2..3,12) (1..2,13), pommel (0..1, 14..15).

SUNFIRE = {  # flaming sword awakened: flamberge (wavy) blade of sun-steel, molten rune fuller, gold flame-curl guard
    'rows': [
        "..............o.",
        "............oo5O",
        "...........o5RO.",
        ".........oo4R2O.",
        "........o5RR2O..",
        ".A.....o4R2OO...",
        "AdA...oo4R2O....",
        "AcbA.o5RR2O.....",
        ".AcbA4R2OO......",
        "..Acb4R2O.......",
        "...AwzAO........",
        "..KmyxaA........",
        ".KlkAAbaA.......",
        "KmkK..AbaA......",
        "czA....AbcA.....",
        "baA.....AA......",
    ],
    'pal': {
        'o': '#c24a1c', 'O': '#4a0e0a', '5': '#ffd27a', '4': '#ffa040', '2': '#c4401a',
        'R': '#ffe48a', 'V': '#fff4c4', 'W': '#ffffff',
        'A': '#5a2c08', 'a': '#a06414', 'b': '#d89a28', 'c': '#f6c850', 'd': '#fff0a4',
        'x': '#7a0c06', 'y': '#d42414', 'z': '#ff7a3c', 'w': '#fff0c0',
        'K': '#2e0e0a', 'k': '#62201a', 'l': '#943222', 'm': '#c45a34',
    },
    # hilt -> tip along the wavy fuller, then the point
    'path': [(6, 9), (6, 8), (7, 7), (8, 7), (9, 6), (9, 5), (10, 4), (11, 4), (12, 3), (13, 2), (14, 1)],
    'lanes': '542R',
    'rest': 62,  # ticks of rest; differs per blade so they do not glint in sync
}

STORMCALLER = {  # lightning sword awakened: storm-steel blade with a zig-zag bolt fuller, steel guard with spark tips
    'rows': [
        "..............o.",
        "............oo6O",
        "..........oo64O.",
        ".........o5R32O.",
        "........o5R32O..",
        ".......o54R2O...",
        ".A....o54R2O....",
        "AzbA.o5RR2O.....",
        ".AcbA5R32O......",
        "..Acb5R2O.......",
        "...AwzAO........",
        "..KmyxaA........",
        ".KlkAAbaA.......",
        "KmkK..AbaA......",
        "zyK....AzA......",
        "yxK.....A.......",
    ],
    'pal': {
        'o': '#3e72c4', 'O': '#0e1a46', '6': '#f4fcff', '5': '#c4ecff', '4': '#86c4f8', '3': '#5a96e0', '2': '#2e5cb4',
        'R': '#e8fbff', 'V': '#9ff4ff', 'W': '#ffffff',
        'A': '#141a34', 'a': '#3a4468', 'b': '#5e6a90', 'c': '#909cbc',
        'x': '#0e5a96', 'y': '#2cb8ec', 'z': '#9ef2ff', 'w': '#ffffff',
        'K': '#0c1026', 'k': '#1c2856', 'l': '#2e4288', 'm': '#4864b2',
    },
    'path': [(6, 9), (6, 8), (7, 7), (8, 7), (9, 6), (10, 5), (10, 4), (11, 3), (12, 2), (13, 2), (14, 1)],
    'lanes': '65432R',
    'rest': 70,  # ticks of rest; differs per blade so they do not glint in sync
}

HALLOWED = {  # holy sword awakened: broad radiant gold blade, white rune fuller, small white-gold wing guard
    'rows': [
        "..............o.",
        "............oo6O",
        "...........o64O.",
        ".........oo5R2O.",
        "........o54R2O..",
        ".AA....o54R2O...",
        "AdcA..o54R32O...",
        "AgccAo54R32O....",
        "Efggc54R32O.....",
        "EEfgc5R2OO......",
        ".EeAwzAO........",
        "..KmyxcbA.......",
        ".KlkAAggcA......",
        "KmkKEefgbcA.....",
        "czA..EEfgdA.....",
        "baA...EEAA......",
    ],
    'pal': {
        'o': '#a87a20', 'O': '#4a2c08', '6': '#ffffff', '5': '#fff6c8', '4': '#ffe27a', '3': '#f2c044', '2': '#cc8e26',
        'R': '#fffbe6', 'V': '#fff2b0', 'W': '#ffffff',
        'A': '#5a3a0a', 'a': '#a06c14', 'b': '#dca42c', 'c': '#f8d460', 'd': '#fff4b4',
        'x': '#1a6ab8', 'y': '#46a8f0', 'z': '#a8e4ff', 'w': '#ffffff',
        'E': '#5e6c8e', 'e': '#a4b2ce', 'f': '#d4def0', 'g': '#ffffff',
        'K': '#141c3c', 'k': '#22346c', 'l': '#3456a4', 'm': '#5a82d0',
    },
    'path': [(6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 2), (14, 1)],
    'lanes': '65432R',
    'rest': 56,  # ticks of rest; differs per blade so they do not glint in sync
}

SANGUINE = {  # vampire blade awakened: curved blood blade, dark blood channel with glowing runes, fanged guard
    'rows': [
        "...........o5O..",
        "..........o55O..",
        ".........o4r2O..",
        ".........o4Q2O..",
        "........o4r2O...",
        "........o4r2O...",
        "..A....o4Q2O....",
        ".AbA..o4r2O.....",
        "AbcbAo4r2O......",
        "AdAcb4Q2O.......",
        ".A.AwzAO........",
        "..KmyxbA........",
        ".KlkAAcbA.......",
        "KmkK..AcbA......",
        "zyK..AdbA.......",
        "yxK...AA........",
    ],
    'pal': {
        'o': '#a8202a', 'O': '#34060c', '5': '#ff9c94', '4': '#e8384a', '2': '#8a1020',
        'r': '#4a040c', 'Q': '#ff4a5a', 'V': '#ff8a94', 'W': '#ffd8dc',
        'A': '#1c1622', 'a': '#4a4058', 'b': '#6e6480', 'c': '#9c94ae', 'd': '#f0e6d6',
        'x': '#4a0410', 'y': '#b81424', 'z': '#ff4252', 'w': '#ffd8dc',
        'K': '#16080c', 'k': '#2e1016', 'l': '#4c1a22', 'm': '#6e2a32',
    },
    # tip -> hilt: the blood light drains down the channel into the gem
    'path': [(12, 0), (11, 1), (11, 2), (11, 3), (10, 4), (10, 5), (9, 6), (8, 7), (7, 8), (6, 9)],
    'lanes': '542rQ',
    'rest': 78,  # ticks of rest; differs per blade so they do not glint in sync
    'reverse_glow': True,
}


def blade_frames(spec):
    """Resting frame + a glint travelling along spec['path'] (hot texel, a warm tail behind it and the blade texels
    beside it in the same row lit up, so the shine crosses the whole blade), then a flash of the point or gem."""
    rows, path, lanes = spec['rows'], spec['path'], spec['lanes']
    frames = [rows]
    for head in range(len(path) + 1):
        changes = {}
        if head < len(path):
            hx, hy = path[head]
            for dx in (-1, 1):
                if 0 <= hx + dx < 16 and rows[hy][hx + dx] in lanes:
                    changes[(hx + dx, hy)] = 'V'
        for i, p in enumerate(path):
            d = head - i
            if d == 0:
                changes[p] = 'W'
            elif d == 1:
                changes[p] = 'V'
        frames.append(apply(rows, changes))
    # last frame: the end of the path (gem side for sanguine, point otherwise) glows
    end = path[-1]
    if spec.get('reverse_glow'):
        frames.append(apply(rows, {(4, 10): 'W', (5, 10): 'w', (4, 11): 'z', (5, 11): 'y'}))
    else:
        frames.append(apply(rows, {end: 'W'}))
    timing = [{'index': 0, 'time': spec['rest']}] + [{'index': i, 'time': 1} for i in range(1, len(frames) - 1)] \
        + [{'index': len(frames) - 1, 'time': 3}]
    return frames, timing


# =====================================================================================================================
# SMITHING
# =====================================================================================================================
SMITHING_TEMPLATE = {  # holystone tablet, gold rune frame with a sky-blue Tiwaz (upgrade) glyph, golden wings
    'rows': [
        "................",
        "....OOOOOOOO....",
        "...O65555554O...",
        "...O544ccc332O..",
        "...Od4cgTga3cO..",
        "...OcdcSTSacbO..",
        "...ObccgSgabaO..",
        "...O4bcgSgaa2O..",
        "...O44bgsga22O..",
        "...O443baa221O..",
        "...O433332221O..",
        "...O413322211O..",
        "...OO41222121O..",
        "....O2221111O...",
        ".....OOOOOOO....",
        "................",
    ],
    'pal': {
        'O': '#2a2e38', '1': '#4e5464', '2': '#6c7486', '3': '#8c94a6', '4': '#aab2c2', '5': '#cdd4e0', '6': '#eef2f8',
        'g': '#14284e', 'a': '#946016', 'b': '#c88c24', 'c': '#eebe46', 'd': '#fff0a0',
        's': '#2a86d8', 'S': '#70ccff', 'T': '#e4faff', 'W': '#ffffff',
    },
}

EMPTY_SLOT_RUNE = {  # ghost icon for the smithing addition slot: a rune stone with an algiz glyph
    'rows': [
        "................",
        ".....#######....",
        "....#.......#...",
        "...#.........#..",
        "...#.#..#..#.#..",
        "...#..#.#.#..#..",
        "...#...###...#..",
        "...#....#....#..",
        "...#....#....#..",
        "...#....#....#..",
        "...#....#....#..",
        "...#....#....#..",
        "...#.........#..",
        "....#.......#...",
        ".....#######....",
        "................",
    ],
    'pal': {'#': '#555555'},
}


def glint_frames(rows, steps, rest):
    """steps: list of {(x, y): char} for the frames after the resting one; rest = resting time in ticks."""
    frames = [rows] + [apply(rows, s) for s in steps]
    timing = [{'index': 0, 'time': rest}] + [{'index': i, 'time': 2} for i in range(1, len(frames))]
    return frames, timing


def template_frames():
    glyph = [(8, 4), (7, 5), (8, 5), (9, 5), (8, 6), (8, 7)]
    low = [(8, 8)]
    up1 = {p: 'T' for p in glyph} | {p: 'S' for p in low}
    up2 = {p: 'W' for p in glyph} | {p: 'T' for p in low}
    return glint_frames(SMITHING_TEMPLATE['rows'], [up1, up2, up2, up1], 50)


# name -> (palette, frames builder or None)
ICONS = {
    'sunfire_spellblade': (SUNFIRE['pal'], lambda: blade_frames(SUNFIRE)),
    'stormcaller_spellblade': (STORMCALLER['pal'], lambda: blade_frames(STORMCALLER)),
    'hallowed_spellblade': (HALLOWED['pal'], lambda: blade_frames(HALLOWED)),
    'sanguine_spellblade': (SANGUINE['pal'], lambda: blade_frames(SANGUINE)),
    'aether_arcane_upgrade_smithing_template': (SMITHING_TEMPLATE['pal'], template_frames),
    'empty_slot_rune': (EMPTY_SLOT_RUNE['pal'], lambda: ([EMPTY_SLOT_RUNE['rows']], None)),
}

# Icons the mod ships but this script no longer draws: the owner asked for the original Aether-style art back for the
# ring, pendant, gloves and mantle, so those PNGs are hand-kept files now. They are still loaded from disk for the
# preview sheet so the comparison stays complete, and "frozen" keeps them out of the write/--check loop.
FROZEN = {
    'ambrosium_ring': ['aether/assets/aether/textures/item/accessories/golden_ring.png',
                       'iss/assets/irons_spellbooks/textures/item/mana_ring.png'],
    'zanite_focus_pendant': ['aether/assets/aether/textures/item/accessories/zanite_pendant.png',
                             'aether/assets/aether/textures/item/accessories/golden_pendant.png'],
    'gravitite_casting_gloves': ['aether/assets/aether/textures/item/accessories/gravitite_gloves.png',
                                 'aether/assets/aether/textures/item/accessories/valkyrie_gloves.png'],
    'valkyrie_mantle': ['aether/assets/aether/textures/item/accessories/valkyrie_cape.png',
                        'aether/assets/aether/textures/item/accessories/white_cape.png'],
}


def frozen_icon(name):
    """The shipped PNG of an icon this script no longer draws, as one 16x16 first frame."""
    im = Image.open(TEX / f'{name}.png').convert('RGBA')
    return im.crop((0, 0, 16, 16))


def build(name):
    """-> (PIL image strip, mcmeta dict or None, list of frame images)."""
    pal, builder = ICONS[name]
    frames, timing = builder()
    ims = [render(f, pal) for f in frames]
    base_alpha = ims[0].getchannel('A').tobytes()
    for im in ims:
        assert im.getchannel('A').tobytes() == base_alpha, (name, 'silhouette changes between frames')
    strip = Image.new('RGBA', (16, 16 * len(ims)), (0, 0, 0, 0))
    for i, im in enumerate(ims):
        strip.paste(im, (0, 16 * i))
    meta = None
    if timing is not None:
        meta = {'animation': {'interpolate': False, 'frametime': 2, 'frames': timing}}
    return strip, meta, ims


def png_bytes(im):
    import io
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=True)
    return buf.getvalue()


def meta_text(meta):
    return json.dumps(meta, indent=2) + '\n'


# =====================================================================================================================
# previews
# =====================================================================================================================
SLOT = (139, 139, 139, 255)


def ref_icon(rel):
    p = REF / rel
    if not p.exists():
        return None
    im = Image.open(p).convert('RGBA')
    return im.crop((0, 0, 16, 16))


REFS = {
    'sunfire_spellblade': ['aether/assets/aether/textures/item/weapons/flaming_sword.png',
                           'iss/assets/irons_spellbooks/textures/item/keeper_flamberge_gui.png'],
    'stormcaller_spellblade': ['aether/assets/aether/textures/item/weapons/lightning_sword.png',
                               'iss/assets/irons_spellbooks/textures/item/truthseeker_gui.png'],
    'hallowed_spellblade': ['aether/assets/aether/textures/item/weapons/holy_sword.png',
                            'iss/assets/irons_spellbooks/textures/item/spellbreaker_gui.png'],
    'sanguine_spellblade': ['aether/assets/aether/textures/item/weapons/vampire_blade.png',
                            'iss/assets/irons_spellbooks/textures/item/amethyst_rapier_gui.png'],
    'aether_arcane_upgrade_smithing_template': ['vanilla/assets/minecraft/textures/item/netherite_upgrade_smithing_template.png',
                                                'vanilla/assets/minecraft/textures/item/ward_armor_trim_smithing_template.png'],
    'empty_slot_rune': ['vanilla/assets/minecraft/textures/item/empty_slot_smithing_template_netherite_upgrade.png',
                        'vanilla/assets/minecraft/textures/item/empty_slot_ingot.png'],
}


def slot_cell(im, scale):
    """An inventory slot (18x18 with the vanilla bevel) at GUI scale, the icon drawn nearest-neighbour."""
    s = Image.new('RGBA', (18, 18), SLOT)
    d = ImageDraw.Draw(s)
    d.line([(0, 0), (17, 0)], fill=(55, 55, 55, 255))
    d.line([(0, 0), (0, 17)], fill=(55, 55, 55, 255))
    d.line([(1, 17), (17, 17)], fill=(255, 255, 255, 255))
    d.line([(17, 1), (17, 17)], fill=(255, 255, 255, 255))
    s.alpha_composite(im, (1, 1))
    return s.resize((18 * scale, 18 * scale), Image.NEAREST)


def write_previews(built):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    # the icons this script draws first, then the frozen (hand-kept, original-art) ones so the sheet stays complete
    names = list(ICONS) + list(FROZEN)

    def frames_of(n):
        if n in built:
            return built[n][2]
        return [frozen_icon(n)]

    def refs_of(n):
        return REFS.get(n) or FROZEN[n]

    z = 10
    cw = 16 * z + 12
    # 1) comparison sheet: ours (zoomed) | refs (zoomed), plus 1x / 2x / 3x inventory slots
    rowh = 16 * z + 70
    sheet = Image.new("RGBA", (cw * 3 + 12 + 22 + 40 + 54 + 12, rowh * len(names) + 12), (198, 198, 198, 255))
    d = ImageDraw.Draw(sheet)
    for r, n in enumerate(names):
        y = 12 + r * rowh
        ours = frames_of(n)[0]
        cells = [('ours: ' + n, ours)] + [(Path(p).stem, ref_icon(p)) for p in refs_of(n)]
        for c, (lab, im) in enumerate(cells):
            x = 12 + c * cw
            bg = Image.new('RGBA', (16 * z, 16 * z), SLOT)
            if im is not None:
                bg.alpha_composite(im.resize((16 * z, 16 * z), Image.NEAREST))
            sheet.alpha_composite(bg, (x, y))
            d.text((x, y + 16 * z + 3), lab[:34], fill=(0, 0, 0, 255))
        # inventory slots at GUI scale 1, 2 and 3: one column per scale, one row per icon (ours, refs)
        xs = [12 + 3 * cw, 12 + 3 * cw + 22, 12 + 3 * cw + 22 + 40]
        for k, sc in enumerate((1, 2, 3)):
            for j, (lab, im) in enumerate(cells):
                if im is not None:
                    sheet.alpha_composite(slot_cell(im, sc), (xs[k], y + j * 58))
    sheet.save(PREVIEW / 'items_vs_refs.png')

    # 2) inventory mock-up: all our icons in a row of slots at GUI scale 2 and 3, next to the references
    for sc in (2, 3):
        row_ims = [frames_of(n)[0] for n in names]
        refs = [ref_icon(refs_of(n)[0]) for n in names]
        w = len(names) * 18 * sc
        inv = Image.new('RGBA', (w + 16, 2 * 18 * sc + 24), (198, 198, 198, 255))
        for i, im in enumerate(row_ims):
            inv.alpha_composite(slot_cell(im, sc), (8 + i * 18 * sc, 8))
            if refs[i] is not None:
                inv.alpha_composite(slot_cell(refs[i], sc), (8 + i * 18 * sc, 16 + 18 * sc))
        inv.save(PREVIEW / f'inventory_x{sc}.png')

    # 3) animation strips (zoomed, every frame side by side) and GIFs
    for n in names:
        frames = frames_of(n)
        if n not in built or len(frames) < 2:
            continue
        zz = 8
        strip = Image.new('RGBA', (len(frames) * (16 * zz + 4) + 4, 16 * zz + 8), SLOT)
        for i, f in enumerate(frames):
            strip.alpha_composite(f.resize((16 * zz, 16 * zz), Image.NEAREST), (4 + i * (16 * zz + 4), 4))
        strip.save(PREVIEW / f'anim_{n}.png')
        meta = built[n][1]['animation']
        gif = []
        durs = []
        for fr in meta['frames']:
            idx, t = (fr['index'], fr['time']) if isinstance(fr, dict) else (fr, meta['frametime'])
            bg = Image.new('RGBA', (16 * 6, 16 * 6), SLOT)
            bg.alpha_composite(frames[idx].resize((16 * 6, 16 * 6), Image.NEAREST))
            gif.append(bg.convert('P', palette=Image.ADAPTIVE))
            durs.append(t * 50)
        gif[0].save(PREVIEW / f'anim_{n}.gif', save_all=True, append_images=gif[1:], duration=durs, loop=0, disposal=1)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='verify the exported files instead of writing them')
    ap.add_argument('--no-preview', action='store_true')
    args = ap.parse_args()
    built = {n: build(n) for n in ICONS}
    bad = []
    for n, (strip, meta, _) in built.items():
        png = TEX / f'{n}.png'
        mc = TEX / f'{n}.png.mcmeta'
        if args.check:
            if not png.exists() or Image.open(png).convert('RGBA').tobytes() != strip.tobytes() \
                    or Image.open(png).size != strip.size:
                bad.append(str(png))
            if meta is None:
                if mc.exists():
                    bad.append(str(mc) + ' (should not exist)')
            elif not mc.exists() or mc.read_text(encoding='utf-8') != meta_text(meta):
                bad.append(str(mc))
            continue
        png.write_bytes(png_bytes(strip))
        if meta is None:
            if mc.exists():
                mc.unlink()
        else:
            mc.write_text(meta_text(meta), encoding='utf-8', newline='\n')
    if args.check:
        if bad:
            print('MISMATCH:\n  ' + '\n  '.join(bad))
            sys.exit(1)
        print(f'OK: {len(built)} item textures match')
        return
    if not args.no_preview:
        write_previews(built)
    print(f'wrote {len(built)} item textures to {TEX} ({len(FROZEN)} more kept as hand-drawn files)')


if __name__ == '__main__':
    main()
