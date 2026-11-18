"""Paint two high-contrast 16px spell glyphs, with the same dark vignette as the spellbook."""
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw
from legacy.gen_textures import finish_icon, icon_bg


OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/aether_spellbooks/textures/gui/spell_icons'


def thorn_icon():
    icon = finish_icon(icon_bg('#686443', '#182b2a', cx=57, cy=80, seed=37), seed=37)
    d = ImageDraw.Draw(icon)
    # Back hook, shadow first, then wine-red growth and a warm horn tip.
    d.polygon([(1, 12), (2, 7), (4, 4), (7, 3), (6, 5), (4, 7), (4, 11)], fill='#332f36')
    d.line([(2, 10), (3, 7), (4, 5), (6, 4)], fill='#bb7267', width=2)
    d.line([(4, 5), (6, 4), (7, 4)], fill='#f3dca6')
    d.polygon([(4, 14), (4, 11), (6, 8), (7, 5), (9, 3), (12, 1), (15, 1),
               (13, 4), (11, 5), (10, 8), (9, 12), (7, 14)], fill='#3b2836')
    d.polygon([(5, 13), (5, 11), (7, 8), (8, 5), (10, 3), (13, 2),
               (11, 5), (9, 7), (8, 11)], fill='#a45458')
    d.line([(6, 11), (7, 9), (8, 6), (10, 4)], fill='#dc8870')
    d.line([(9, 5), (11, 3), (13, 2), (15, 1)], fill='#ffe6ac', width=2)
    d.polygon([(8, 13), (10, 10), (12, 8), (14, 8), (12, 10), (10, 13)], fill='#b77867')
    d.line([(12, 9), (14, 8)], fill='#f2d49e')
    d.line([(0, 14), (3, 12), (6, 13), (10, 13), (14, 11)], fill='#3d6240', width=2)
    d.line([(1, 14), (3, 13), (6, 14), (9, 13)], fill='#b9c57e')
    d.point([(1, 9), (12, 14), (14, 10)], fill='#e1bd85')
    return icon


def flower_icon():
    icon = finish_icon(icon_bg('#5d7871', '#172338', cx=58, cy=67, seed=38), seed=38)
    d = ImageDraw.Draw(icon)
    d.line([(8, 8), (9, 11), (8, 13), (6, 15)], fill='#3c4054', width=3)
    d.line([(9, 10), (8, 13), (6, 15)], fill='#ad829b')
    d.polygon([(8, 13), (10, 10), (14, 10), (12, 12)], fill='#75a16c')
    d.line([(9, 12), (12, 11)], fill='#d2d786')
    petals = [
        ([(6, 7), (4, 4), (5, 1), (7, 3), (8, 6)], [(5, 2), (6, 3), (7, 5)]),
        ([(8, 6), (9, 2), (12, 1), (12, 4), (10, 7)], [(10, 4), (11, 2)]),
        ([(7, 7), (5, 8), (2, 7), (0, 4), (4, 4)], [(1, 5), (4, 5), (6, 7)]),
        ([(8, 7), (11, 5), (15, 4), (13, 8), (10, 9)], [(10, 7), (13, 6), (14, 5)]),
        ([(5, 8), (8, 7), (9, 9), (6, 12), (3, 12), (4, 9)], [(4, 11), (5, 9), (7, 8)]),
        ([(8, 7), (11, 7), (13, 10), (13, 12), (10, 11), (8, 9)], [(9, 8), (11, 9), (12, 11)]),
    ]
    for outline, vein in petals:
        d.polygon(outline, fill='#31858e', outline='#254959')
        d.line(vein, fill='#8de9d0')
    d.polygon([(5, 6), (7, 4), (9, 5), (10, 7), (8, 9), (6, 8)], fill='#654765')
    d.line([(6, 6), (7, 5), (9, 6)], fill='#ffd88a')
    d.point((7, 7), fill='#fff1b4')
    d.point([(2, 2), (13, 2)], fill='#becd90')
    return icon


def corolla():
    # Aether's two tiers use separate 8 x 1 x 10 petal UVs. Cut the tips to a point.
    atlas = Image.new('RGBA', (64, 32))
    shape = [
        '...hh...', '..hlvh..', '.hlvpvh.', 'hlvppvvh', 'hvvLpvvh',
        'hvlLpvvh', '.vlLlvv.', '.blllvb.', '..bllb..', '...bb...',
    ]
    for u, v, colors in [
        (28, 2, ['#304b63', '#4f8193', '#85d8c0', '#ce90c5', '#ffe4ac', '#557572']),
        (0, 0, ['#413959', '#716992', '#bba7d5', '#e6b3d4', '#fff1bb', '#4b646d']),
    ]:
        palette = dict(zip('hvlpLb', colors))
        for y, row in enumerate(shape):
            for x, c in enumerate(row):
                if c != '.':
                    color = ImageColor.getrgb(palette[c]) + (255,)
                    atlas.putpixel((u + 10 + x, v + y), color)
                    atlas.putpixel((u + 18 + x, v + 9 - y), color)
        pen = ImageDraw.Draw(atlas)
        for left, length in [(0, 10), (10, 8), (18, 10), (28, 8)]:
            pen.line([(u + left + length // 3, v + 10), (u + left + length * 2 // 3, v + 10)], fill=colors[1])
    # The throat rotates with the petals; its underside and rim remain visible from below.
    pen = ImageDraw.Draw(atlas)
    pen.rectangle((0, 12, 23, 19), fill='#305658')
    disk = ['.gggg.', 'gppspg', 'gpylpg', 'gsyypg', 'gppspg', '.gggg.']
    colors = {'.': '#356c6b', 'g': '#79ba9a', 'p': '#805477', 's': '#c68899', 'y': '#f1bb70', 'l': '#fff0a4'}
    for offset in (6, 12):
        for y, row in enumerate(disk):
            for x, c in enumerate(row):
                pen.point((offset + x, 12 + y), fill=colors[c])
    for x in (1, 7, 13, 19):
        pen.line((x, 18, x + 2, 18), fill='#ace1bc')
    # Short faceted neck joins the tilting bloom to the level leaf rosette.
    pen.rectangle((36, 20, 47, 28), fill='#354d51')
    for x in (36, 39, 42, 45):
        pen.line((x + 1, 23, x + 1, 27), fill='#77a483')
        pen.point((x + 1, 24), fill='#b1c68d')
    return atlas


if __name__ == '__main__':
    thorn_icon().save(OUT / 'thorn_spikes.png')
    flower_icon().save(OUT / 'aechor_flower.png')
    corolla().save(OUT.parents[1] / 'entity/aechor_corolla.png')
