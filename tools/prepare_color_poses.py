"""Pack the three approved, alpha-cutout color sheets into shared mesh textures.

Usage: python tools/prepare_color_poses.py plate-a.png plate-b.png plate-c.png
Requires Pillow, NumPy and SciPy. Input sheets must contain three isolated rows.
"""
from pathlib import Path
import argparse
import hashlib
import json
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage


GROUPS = [
    [('hammerhead', 1008), ('great_white', 1400), ('tiger', 1500)],
    [('whale_shark', 1600), ('shortfin_mako', 1100), ('thresher', 1900)],
    [('zebra', 2100), ('oceanic_whitetip', 1800), ('orca', 1400)],
]


def pack(paths, output, preview):
    contact = Image.new('RGB', (768, 630), (231, 241, 247))
    records = []
    for path, group in zip(paths, GROUPS):
        source = Image.open(path).convert('RGBA')
        pixels = np.array(source)
        assert pixels[:, :, 3].min() == 0, 'input must have genuine transparency'
        labels, _ = ndimage.label(pixels[:, :, 3] > 96)
        sizes = np.bincount(labels.ravel())
        components = [i for i in range(1, len(sizes)) if sizes[i] > 10000]
        objects = ndimage.find_objects(labels)
        components.sort(key=lambda i: objects[i-1][0].start)
        assert len(components) == 3, (path, 'expected exactly three animal silhouettes')
        for component, (name, duration) in zip(components, group):
            region = labels == component
            ys, xs = np.nonzero(region)
            bounds = (max(0, int(xs.min())-4), max(0, int(ys.min())-4),
                      min(source.width, int(xs.max())+5), min(source.height, int(ys.max())+5))
            # Preserve antialiasing at the silhouette; discard stray alpha outside it.
            rgba = pixels.copy()
            rgba[:, :, 3] = np.where(ndimage.binary_dilation(region, iterations=3), pixels[:, :, 3], 0)
            pose = Image.fromarray(rgba).crop(bounds)
            ratio = 448 / pose.width
            head_rows = ys[xs > xs.max()-(xs.max()-xs.min())*.025]
            pivot_y = float(np.median(head_rows))
            pose = pose.resize((448, round(pose.height*ratio)), Image.Resampling.LANCZOS)
            tile = Image.new('RGBA', (512, 512))
            tile.alpha_composite(pose, (32, round(256-(pivot_y-bounds[1])*ratio)))
            box = tile.getchannel('A').getbbox()
            assert box and 0 < box[0] < box[2] < 512 and 0 < box[1] < box[3] < 512
            folder = output / name
            folder.mkdir(parents=True, exist_ok=True)
            tile.save(folder / 'swim_atlas.webp', lossless=True, method=6)
            manifest = {'swim': dict(frames=1, durationMs=duration, frameDurationsMs=[duration],
                        frameSize=512, columns=1, animation='continuous_mesh',
                        appearance='realistic_color_v1')}
            (folder / 'animations.json').write_text(json.dumps(manifest, indent=2)+'\n')
            records.append(dict(species=name, source=path.name,
                                sourceSha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                                bounds=box))
            index = len(records)-1
            thumb = tile.resize((256, 256), Image.Resampling.LANCZOS)
            x, y = index % 3 * 256, index // 3 * 210 - 28
            contact.paste(thumb, (x, y), thumb)
            ImageDraw.Draw(contact).text((x+14, y+214), name, fill=(15, 42, 64))
            print(name, box, (folder / 'swim_atlas.webp').stat().st_size, 'bytes')
    preview.parent.mkdir(parents=True, exist_ok=True)
    contact.save(preview)
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plates', nargs=3, type=Path)
    parser.add_argument('--output', type=Path,
                        default=Path(__file__).resolve().parents[1] / 'app/src/main/assets/shark')
    parser.add_argument('--preview', type=Path, default=Path('/tmp/color-poses.png'))
    args = parser.parse_args()
    pack(args.plates, args.output, args.preview)
