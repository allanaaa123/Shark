"""Create ~24fps transparent swim loops from the approved eight-pose WebPs.
Usage: python tools/prepare_species_assets.py /directory/containing/concept_webps
Requires Pillow and FFmpeg (RGBA-capable minterpolate). Keeps source motion timing
within 1.5%, interpolates across the loop seam, and packages Android 8 atlases.
"""
from pathlib import Path
import json
import subprocess
import sys
import tempfile
from PIL import Image

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/shark'
for name, frame_count, fps in [('whale_shark', 29, '145/6'), ('orca', 24, '24')]:
    original = Path(sys.argv[1]) / (name + '_concept.webp')
    dest = root / name
    dest.mkdir(parents=True, exist_ok=True)
    animation = Image.open(original)
    assert animation.n_frames == 8, 'Expected the approved eight-pose source'
    with tempfile.TemporaryDirectory(prefix='shark-interpolation-') as temporary:
        temporary = Path(temporary)
        input_dir, output_dir = temporary / 'input', temporary / 'output'
        input_dir.mkdir()
        output_dir.mkdir()
        durations = []
        # Three cycles give motion estimation real context on both sides of the seam.
        for i in range(24):
            animation.seek(i % 8)
            frame = animation.convert('RGBA')
            if i < 8:
                durations.append(animation.info['duration'])
            frame.save(input_dir / f'{i:03d}.png')
        assert len(set(durations)) == 1
        subprocess.run([
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-framerate', f'1000/{durations[0]}', '-i', str(input_dir / '%03d.png'),
            '-vf', f'minterpolate=fps={fps}:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:mb_size=8:search_param=16:vsbmc=1:scd=none',
            '-start_number', '0', str(output_dir / '%03d.png')
        ], check=True)
        frames = []
        animation.seek(0)
        first_box = animation.convert('RGBA').getchannel('A').getbbox()
        scale = 170 / (first_box[2] - first_box[0])
        atlas = Image.new('RGBA', (192 * 8, 192 * ((frame_count + 7) // 8)))
        for i in range(frame_count):
            frame = Image.open(output_dir / f'{frame_count + i:03d}.png').convert('RGBA')
            alpha_min, alpha_max = frame.getchannel('A').getextrema()
            assert alpha_min == 0 and alpha_max >= 250, 'FFmpeg discarded alpha'
            frames.append(frame)
            # One fixed scale/pivot per clip: do not make the body breathe in size.
            sprite = frame.resize((round(frame.width * scale), round(frame.height * scale)), Image.Resampling.LANCZOS)
            tile = Image.new('RGBA', (192, 192))
            tile.alpha_composite(sprite, ((192 - sprite.width) // 2, (192 - sprite.height) // 2))
            atlas.alpha_composite(tile, ((i % 8) * 192, (i // 8) * 192))
        assert len({f.tobytes() for f in frames}) == frame_count, 'Repeated output frames'
        # Same 42ms timing as the existing hammerhead: 23.81fps (~24fps).
        frames[0].save(dest / 'swim.webp', save_all=True, append_images=frames[1:], duration=42, loop=0, lossless=True, method=6)
        atlas.save(dest / 'swim_atlas.webp', lossless=True, method=6)
        metadata = {'swim': dict(frames=frame_count, durationMs=frame_count * 42,
                                frameDurationsMs=[42] * frame_count, frameSize=192, columns=8,
                                sourcePoses=8, interpolation='ffmpeg_motion_compensated')}
        (dest / 'animations.json').write_text(json.dumps(metadata, indent=2) + '\n')
        print(name, frame_count, 'unique frames', frame_count * 42, 'ms (~24fps)', flush=True)
