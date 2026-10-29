"""Validate packaged atlases. Optional species names restrict the check to selected folders."""
from pathlib import Path
import json
import sys
from PIL import Image

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/shark'
expected = {'hammerhead', 'whale_shark', 'orca', 'great_white', 'tiger',
            'shortfin_mako', 'thresher', 'zebra', 'oceanic_whitetip', 'blue_spotted_ray', 'golden_ray', 'eagle_ray', 'silver_ray', 'manta_ray', 'dunkleosteus', 'giant_grouper', 'giant_squid'}
selected = set(sys.argv[1:]) if len(sys.argv) > 1 else expected
assert selected <= expected
total = 0
for species in sorted(selected):
    manifest = json.loads((root / species / 'animations.json').read_text())
    assert 'swim' in manifest
    for name, spec in manifest.items():
        columns, size, frames = spec['columns'], spec['frameSize'], spec['frames']
        atlas = Image.open(root / species / (name + '_atlas.webp')).convert('RGBA')
        assert atlas.size == (columns * size, ((frames + columns - 1) // columns) * size)
        assert len(spec['frameDurationsMs']) == frames
        assert sum(spec['frameDurationsMs']) == spec['durationMs']
        total += atlas.width * atlas.height * 4
        hashes = set()
        for i in range(frames):
            x, y = i % columns * size, i // columns * size
            tile = atlas.crop((x, y, x + size, y + size))
            hashes.add(tile.tobytes())
            alpha = tile.getchannel('A')
            box = alpha.getbbox()
            assert alpha.getextrema()[0] == 0 and alpha.getextrema()[1] >= 250
            assert box and 0 < box[0] < box[2] < size and 0 < box[1] < box[3] < size
        assert len(hashes) == frames, (species, name, 'duplicate frames')
    if not species.endswith('_ray'):
        assert set(manifest) == {'swim'}
        assert manifest['swim']['appearance'] == 'realistic_color_v1'
        assert manifest['swim']['frames'] == 1 and manifest['swim']['frameSize'] == 512
        assert {p.name for p in (root / species).glob('*.webp')} == {'swim_atlas.webp'}
    print(species, 'transparent aligned assets OK')
assert total < 40 * 1024 * 1024
print('Checked decoded source pixels:', round(total / 1024**2, 2), 'MiB')

