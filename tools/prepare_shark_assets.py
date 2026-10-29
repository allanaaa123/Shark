"""Convert the three supplied WebPs to shared, heading-normalized WebP atlases.
Usage: python tools/prepare_shark_assets.py /path/to/source_directory
Requires Pillow and NumPy. Runtime needs neither dependency.
"""
import argparse
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image

FRAME = 192
COLUMNS = 8
BODY_LENGTH = 170
NAMES = {
    'swim': 'hammerhead_seamless_swim_transparent.webp',
    'turn_left': 'hammerhead_left_turn_24fps.webp',
    'turn_right': 'hammerhead_right_turn_24fps.webp',
}


def prepare(source, destination):
    destination.mkdir(parents=True, exist_ok=True)
    metadata = {}
    for name, filename in NAMES.items():
        raw = source / filename
        (destination / filename).write_bytes(raw.read_bytes())
        animation = Image.open(raw)
        sheet = Image.new('RGBA', (FRAME * COLUMNS, FRAME * math.ceil(animation.n_frames / COLUMNS)))
        first_angle = None
        previous_angle = 0.0
        durations = []
        first_length = None
        for index in range(animation.n_frames):
            animation.seek(index)
            frame = animation.convert('RGBA')
            durations.append(animation.info['duration'])
            # Weighted principal axis follows the body's baked-in heading.
            alpha = np.asarray(frame.getchannel('A'), dtype=float) / 255
            yy, xx = np.nonzero(alpha > 0.2)
            points = np.column_stack((xx, yy))
            weights = alpha[yy, xx]
            covariance = np.cov(points.T, aweights=weights)
            _, axes = np.linalg.eigh(covariance)
            axis = axes[:, -1]
            angle = math.degrees(math.atan2(axis[1], axis[0]))
            while angle - previous_angle > 90:
                angle -= 180
            while angle - previous_angle < -90:
                angle += 180
            previous_angle = angle
            if first_angle is None:
                first_angle = angle
                box = frame.getchannel('A').getbbox()
                first_length = box[2] - box[0]
            heading = 0 if name == 'swim' else angle - first_angle
            # Normalize source padding and scale once per clip, never per frame.
            scale = BODY_LENGTH / first_length
            size = (round(frame.width * scale), round(frame.height * scale))
            scaled = frame.resize(size, Image.Resampling.LANCZOS)
            tile = Image.new('RGBA', (FRAME, FRAME))
            tile.alpha_composite(scaled, ((FRAME - size[0]) // 2, (FRAME - size[1]) // 2))
            # PIL positive rotation is counterclockwise, opposite screen heading.
            tile = tile.rotate(heading, Image.Resampling.BICUBIC)
            box = tile.getchannel('A').getbbox()
            if box and (box[0] == 0 or box[1] == 0 or box[2] == FRAME or box[3] == FRAME):
                raise ValueError(f'{name} frame {index}: clipped frame')
            sheet.alpha_composite(tile, ((index % COLUMNS) * FRAME, (index // COLUMNS) * FRAME))
        sheet.save(destination / f'{name}_atlas.webp', lossless=True, method=6)
        metadata[name] = dict(frames=animation.n_frames, durationMs=sum(durations),
                              frameDurationsMs=durations, frameSize=FRAME, columns=COLUMNS)
        print(name, metadata[name]['frames'], 'frames', sum(durations), 'ms')
    (destination / 'animations.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    prepare(args.source, Path(__file__).resolve().parents[1] / 'app/src/main/assets/shark/hammerhead')
