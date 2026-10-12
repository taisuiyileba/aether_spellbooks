"""Export 1.20.1 books at Iron's Spells' native pixel density. Requires Pillow.

Each character below is one deliberately placed texel. Run with --check to
verify existing exports. The original 16x16 inventory icons are never modified.
"""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'
SIZE = 32
BOOKS = {
    'slider_codex': {
        'body': ['262e40', '343c4e', '525e76', '74829c', 'b4c0d4'],
        'metal': ['5a2c10', 'a4581c', 'dc8a34', 'ffd08a'],
        'paper': ['7e7c70', 'aaa898', 'ccc8b6', 'e6e2d0'],
        'accent': ['1c86ac', '58e6ff', 'effeff'],
        # Bevel above the eye, one stepped stone groove below, copper fittings.
        'front': ['mghhlgm', 'Glhbbmg', 'bhlhlbs', 'hsxaxbs', 'bxawaxb',
                  'hsxaxsb', 'bhsssbb', 'bbsbbss', 'mhbbsGm', 'cmsssmc'],
        'back': ['mghhlgm', 'Glhhbmg', 'hbbhhbs', 'hbssbbs', 'hbsbbbs',
                 'bbsbbbs', 'bbssbbs', 'sbbhbss', 'mhbbsGm', 'cmsssmc'],
    },
    'valkyrie_grimoire': {
        'body': ['0c1430', '1a2a58', '264484', '365eae', '84acea'],
        'metal': ['7a4a10', 'c88a22', 'f0c048', 'fff2a8'],
        'paper': ['9a7630', 'd0ae58', 'ecd694', 'fcf0c8'],
        'accent': ['8ea4cc', 'd4e2f4', 'ffffff', 'c4f2ff', '3aa0ff'],
        # Compact silver wings with a blue tip, surrounded by folded blue leather.
        'front': ['gmhhsmg', 'Ghhhhgm', 'hlbhhbs', 'hwbhbws', 'bWwbwWs',
                  'shWCWbs', 'shbBbbs', 'sbshbbs', 'gbsssmg', 'mgsssmc'],
        'back': ['gmhhsmg', 'Ghhbhgm', 'hlbhhbs', 'hbbbhbs', 'hbssbbs',
                 'sbsbhbs', 'sbbhbbs', 'sbshbbs', 'gbsssmg', 'mgsssmc'],
    },
    'solar_codex': {
        'body': ['1c0404', '3c0808', '681010', '961c14', 'e66a3a'],
        'metal': ['7a4a10', 'c88a22', 'f0c048', 'fff2a8'],
        'paper': ['96602a', 'cc9a52', 'eac886', 'faeabc'],
        'accent': ['e06410', 'f7a020', 'ffd23c', 'fffbe0'],
        # Orange/yellow transition into a small ivory core, not a flat disc.
        'front': ['gmhhsmg', 'Ghhhhgm', 'hshbhbs', 'hbsasbs', 'hbxAxbs',
                  'baAwAab', 'bsxAxbs', 'bbsasbs', 'gbsssmg', 'mgsssmc'],
        'back': ['gmhhsmg', 'Ghhlhgm', 'hbbhhbs', 'hhbbhbs', 'hbsbbbs',
                 'hbsbhbs', 'bbshbbs', 'bbsbbss', 'gbsssmg', 'mgsssmc'],
    },
}

# Pixel coordinates: one texel per model unit, like Iron's gold/ice/blaze books.
UV = {
    'front': [0, 0, 7, 10], 'back': [8, 0, 15, 10],
    'lining': [16, 0, 23, 10], 'spine': [24, 0, 26, 10],
    'cover_edge': [27, 0, 28, 10], 'cover_end': [29, 0, 30, 7],
    'binding_end': [24, 11, 26, 12],
    'left_page': [0, 12, 5, 20], 'right_page': [6, 12, 11, 20],
    'page_edge': [12, 12, 14, 20], 'page_end': [16, 12, 18, 17],
    'left_edge': [12, 12, 13, 20], 'right_edge': [13, 12, 14, 20],
    'left_end': [16, 12, 17, 17], 'right_end': [17, 12, 18, 17],
}
DISPLAY = {
    'thirdperson_righthand': {'rotation': [90, 180, 0], 'translation': [0, -2, 2], 'scale': [.75, .75, .75]},
    'thirdperson_lefthand': {'rotation': [90, 180, 0], 'translation': [0, -2, 2], 'scale': [.75, .75, .75]},
    'firstperson_righthand': {'rotation': [-137, 10, 90], 'translation': [-4.25, 3, -3.75], 'scale': [.8, .8, .8]},
    'firstperson_lefthand': {'rotation': [-137, 10, -90], 'translation': [4.25, 3, -3.25], 'scale': [.8, .8, .8]},
    'ground': {'rotation': [-89, 0, 0], 'translation': [0, 0, -4], 'scale': [.75, .75, .75]},
    'head': {'rotation': [0, 0, -90], 'translation': [4, 8.5, 0]},
    'fixed': {'rotation': [-90, 0, 90], 'translation': [-4.5, 0, 0]},
}


def palette(spec):
    colors = dict(zip('osbhl', spec['body']))
    colors.update(zip('cmgG', spec['metal']))
    colors.update(zip('pqrt', spec['paper']))
    a = spec['accent']
    colors.update(x=a[0], a=a[1], A=a[2], w=a[2] if len(a) != 4 else a[3],
                  W=a[1], C=a[min(3, len(a)-1)], B=a[-1])
    return {key: tuple(bytes.fromhex(value)) + (255,) for key, value in colors.items()}


def paint(im, island, rows, colors):
    x0, y0, x1, y1 = UV[island]
    assert len(rows) == y1-y0 and all(len(row) == x1-x0 for row in rows), island
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            im.putpixel((x0+x, y0+y), colors[key])


def atlas(name, spec):
    colors = palette(spec)
    icon = Image.open(ASSETS / f'textures/item/{name}.png').convert('RGBA')
    assert set(colors.values()) <= {icon.getpixel((x, y)) for y in range(icon.height) for x in range(icon.width)}, name
    im = Image.new('RGBA', (SIZE, SIZE))
    paint(im, 'front', spec['front'], colors)
    paint(im, 'back', spec['back'], colors)
    paint(im, 'lining', ['sbbhbbs', 'bhhhhbs', 'bhbbbbs', 'bhbbbbs', 'bbbbbbs',
                         'sbbbbbs', 'sbbbbbs', 'sbbbsbs', 'sbbbbss', 'sssssss'], colors)
    paint(im, 'spine', ['mg', 'Gs', 'hb', 'sb', 'ha', 'sb', 'hb', 'sb', 'gm', 'mc'], colors)
    paint(im, 'cover_edge', ['g', 'm', 'h', 'b', 'b', 'b', 's', 'b', 'g', 'm'], colors)
    paint(im, 'cover_end', ['g', 'm', 'h', 'b', 'b', 'g', 'm'], colors)
    paint(im, 'binding_end', ['mg'], colors)
    # Small ink clusters and pale gutters at the same scale as the covers.
    paint(im, 'left_page', ['rtttr', 'rtqqt', 'rtttt', 'rtqpt', 'rttpt', 'rtqtt', 'rtqqt', 'rrttr'], colors)
    paint(im, 'right_page', ['rtttr', 'tqqtr', 'ttttr', 'tpqtr', 'tpttr', 'ttqtr', 'tqqtr', 'rttqr'], colors)
    # Layer colour follows thickness, not stripes stretched across the book width.
    paint(im, 'page_edge', ['rt', 'rt', 'qt', 'rt', 'rt', 'rq', 'rt', 'rt'], colors)
    paint(im, 'page_end', ['rt', 'rt', 'qt', 'rt', 'rq'], colors)
    return im


def face(island, rotation=0):
    # +Z is up in the real hand transforms. WEST is the player's visible cover.
    if island in {'front', 'back', 'lining', 'left_page', 'right_page'}:
        rotation = (rotation + 180) % 360
    result = {'uv': [v * 16/SIZE for v in UV[island]], 'texture': '#book'}
    if rotation:
        result['rotation'] = rotation
    return result


def box(name, start, end, material, **overrides):
    faces = {side: face(material) for side in ('north', 'south', 'east', 'west', 'up', 'down')}
    faces.update(overrides)
    return {'name': name, 'from': start, 'to': end, 'faces': faces}


def cover(name, x0, x1, y0, y1, outer, west):
    return box(name, [x0, y0, 3], [x1, y1, 13], 'cover_edge',
               north=face('cover_end'), south=face('cover_end'),
               west=face(outer if west else 'lining', 270),
               east=face('lining' if west else outer, 90))


def models():
    front = cover('front_cover', 6, 7, 0, 7, 'front', True)
    back = cover('back_cover', 9, 10, 0, 7, 'back', False)
    spine = box('bound_spine', [7, 6, 3], [9, 7, 13], 'binding_end',
                up=face('spine'), down=face('spine'),
                east=face('cover_edge'), west=face('cover_edge'))
    pages = box('page_block', [7, 1, 4], [9, 6, 12], 'page_end',
                east=face('right_page', 90), west=face('left_page', 270),
                down=face('page_edge'), up=face('page_edge'))
    closed = {'credit': 'Aether Spellbooks; original pixel art at one texel per model unit',
              'texture_size': [SIZE, SIZE], 'elements': [front, back, spine, pages], 'display': DISPLAY}
    open_front = cover('open_front_cover', 6, 7, -5, 2, 'front', True)
    open_back = cover('open_back_cover', 6, 7, 2, 9, 'back', True)
    left = box('left_pages', [7, -3, 4], [8, 2, 12], 'left_end',
               east=face('left_page', 90), west=face('left_page', 270),
               down=face('left_edge'), up=face('left_edge'))
    right = box('right_pages', [7, 2, 4], [8, 7, 12], 'right_end',
                east=face('right_page', 90), west=face('right_page', 270),
                down=face('right_edge'), up=face('right_edge'))
    leaves = []
    for name, y0, y1, angle, island in [('left_leaf', -3, 2, 22.5, 'left_page'), ('right_leaf', 2, 7, -22.5, 'right_page')]:
        leaves.append({'name': name, 'from': [8, y0, 4], 'to': [8, y1, 12],
                       'rotation': {'origin': [8, 2, 8], 'axis': 'z', 'angle': angle},
                       'faces': {'east': face(island, 90), 'west': face(island, 270)}})
    opened = {'credit': closed['credit'], 'texture_size': [SIZE, SIZE],
              'elements': [open_front, open_back, left, right, *leaves], 'display': DISPLAY}
    return closed, opened


def validate(im, model):
    for element in model['elements']:
        assert all(-16 <= value <= 32 for value in element['from'] + element['to'])
        for f in element['faces'].values():
            x0, y0, x1, y1 = [int(v*SIZE/16) for v in f['uv']]
            assert 0 <= x0 < x1 <= SIZE and 0 <= y0 < y1 <= SIZE
            assert im.crop((x0, y0, x1, y1)).getchannel('A').getextrema() == (255, 255)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='check exports without changing files')
    check = parser.parse_args().check
    templates = models()
    for name, spec in BOOKS.items():
        im = atlas(name, spec)
        for model in templates:
            validate(im, model)
        path = ASSETS / f'textures/item/spell_book_models/{name}.png'
        if check:
            existing = Image.open(path).convert('RGBA')
            assert existing.size == im.size and existing.tobytes() == im.tobytes(), path
        else:
            im.save(path)
        wrapper = json.loads((ASSETS / f'models/item/{name}.json').read_text(encoding='utf-8'))
        assert wrapper['base']['parent'] == 'aether_spellbooks:item/template_spell_book'
        assert wrapper['perspectives']['head']['parent'] == 'aether_spellbooks:item/template_spell_book_open'
        assert wrapper['perspectives']['gui']['textures']['layer0'] == f'aether_spellbooks:item/{name}'
    for suffix, model in zip(('', '_open'), templates):
        path = ASSETS / f'models/item/template_spell_book{suffix}.json'
        if check:
            assert json.loads(path.read_text(encoding='utf-8')) == model, path
        else:
            path.write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    print(('Checked' if check else 'Exported') + ' three native-density atlases and both models; all UVs in bounds and opaque.')


if __name__ == '__main__':
    main()
