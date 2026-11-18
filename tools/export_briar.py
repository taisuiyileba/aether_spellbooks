"""Export the editable Blockbench meshes into two independently animated Forge OBJ models."""
import base64
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/aether_spellbooks'


def export_meshes(file, meshes):
    lines = ['mtllib briar.mtl', 'usemtl bark', 's off']
    vertex_index = 1
    uv_index = 1
    for mesh in meshes:
        lines.append('g ' + mesh['name'])
        indices = {}
        for key, point in mesh['vertices'].items():
            lines.append('v ' + ' '.join(f'{v / 16:.6f}' for v in point))
            indices[key] = vertex_index
            vertex_index += 1
        for face in mesh['faces'].values():
            corners = []
            for key in face['vertices']:
                u, v = face['uv'][key]
                lines.append(f'vt {u / 32:.6f} {1 - v / 32:.6f}')
                corners.append(f'{indices[key]}/{uv_index}')
                uv_index += 1
            lines.append('f ' + ' '.join(corners))
    file.write_text('\n'.join(lines) + '\n', encoding='utf-8')


if __name__ == '__main__':
    project = json.loads((ROOT / 'art/nature/briar.bbmodel').read_text(encoding='utf-8'))
    parts = project['elements']
    output = ASSETS / 'models/effect'
    output.mkdir(parents=True, exist_ok=True)
    for name, runner in [('briar_hook', False), ('briar_runner', True)]:
        export_meshes(output / f'{name}.obj', [m for m in parts if m['name'].startswith('runner') == runner])
    encoded = project['textures'][0]['source'].split(',', 1)[1]
    (ASSETS / 'textures/block/briar.png').write_bytes(base64.b64decode(encoded))
