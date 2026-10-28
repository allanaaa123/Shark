"""Validate packaged animation geometry and decoded memory without Android."""
import json
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/shark/hammerhead'
manifest = json.loads((root / 'animations.json').read_text())
total = 0
for name, count in [('swim', 72), ('turn_left', 48), ('turn_right', 48)]:
    spec = manifest[name]
    assert spec['frames'] == count
    assert sum(spec['frameDurationsMs']) == spec['durationMs']
    assert set(spec['frameDurationsMs']) == {42}
    atlas = Image.open(root / f'{name}_atlas.webp').convert('RGBA')
    assert atlas.size == (1536, 192 * ((count + 7) // 8))
    total += atlas.width * atlas.height * 4
    for index in range(count):
        x, y = index % 8 * 192, index // 8 * 192
        tile = atlas.crop((x, y, x + 192, y + 192))
        alpha = tile.getchannel('A')
        box = alpha.getbbox()
        assert alpha.getextrema() == (0, 255)
        assert 0 < box[0] < box[2] < 192 and 0 < box[1] < box[3] < 192
        # Baked-in 90 degree rotation must not remain in the runtime atlas.
        yy, xx = np.nonzero(np.asarray(alpha) > 64)
        assert np.var(xx) > 3 * np.var(yy), (name, index)
    print(f'{name}: {count} transparent, unclipped, heading-normalized frames OK')
assert total < 26 * 1024 * 1024
print(f'Shared decoded atlas memory: {total / 1024**2:.2f} MiB')
