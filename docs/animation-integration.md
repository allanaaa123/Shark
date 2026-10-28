# Hammerhead animation integration

The original three user-supplied Animated WebPs are preserved in
`app/src/main/assets/shark/hammerhead/`. They contain 72/48/48 frames at 42 ms
per frame. Turns rotate the whole shark about 90 degrees and are not seamless
loops, despite the files' infinite-loop metadata.

## Runtime design

`SharkNavigator` remains the source of position and heading. Read-only `motion`
and `turnProgress` expose its existing cruise/turn state. Positive screen angles
are clockwise (right); negative angles are left. No turn direction is inferred
from wrapped angle subtraction.

`tools/prepare_shark_assets.py` normalizes source padding and body length and
removes the turn clips' baked-in heading using an alpha-weighted principal axis.
Generated static lossless WebP atlases contain 192 x 192 tiles, eight columns.
The source alpha and fin movement remain; Canvas supplies the world heading.
Inspect a contact sheet after replacing the source art: principal-axis estimation
is specific to this mostly horizontal, top-down shark and is not a general 3D
orientation detector.

`SharkArt` decodes all three atlases once on a background executor. The immutable
bitmaps are shared across preview and all overlay sharks. Total ARGB storage is
23.625 MiB, excluding transient decoder/GPU overhead. No animated WebP decoder
or third-party runtime dependency is needed on Android 8 (API 26). The same
indexed renderer runs on newer Android versions. Native AnimatedImageDrawable
(API 28+) was not chosen because it has no public seek or playback-rate API;
precise navigation progress and baked-heading compensation require frame access.

`SharkAnimationState` gives each shark independent swim phase and speed-dependent
cadence (0.35 to 2.4 times, incorporating actual speed variation). Turn clips play once according to navigation progress,
not their embedded infinite loop. This stretches a 2.016-second turn to fit both
small course corrections and large boundary turns. Heading and position continue
to come exclusively from navigation. A 120 ms premultiplied crossfade on an
isolated layer softens state changes without reducing the combined opacity.

Original size/speed preferences and up to ten independent overlays are retained.
The service still advances at approximately 25 Hz. Screen-off pauses navigation
and rendering; screen-on resumes with a reset timestamp. Preview updates stop
when its window is hidden. Observers are removed on View detach. The shared
bitmap cache is dropped after 30 seconds with no attached consumers. Atlas
loading failure logs an error and falls back to the existing Canvas shark.

## Regenerate and check assets

With Pillow and NumPy available in the development environment:

```sh
python tools/prepare_shark_assets.py app/src/main/assets/shark/hammerhead
python tools/check_shark_assets.py
```

The generator can also take a separate source directory. Original filenames are
retained. A size/aspect change requires reviewing scale, centering and turn-axis
normalization. The generator rejects clipping instead of silently losing fins.

## Validation and remaining limits

- Asset checks cover all 168 frames: count, timing, transparency, bounds, heading
  normalization, atlas dimensions and a 26 MiB shared-memory budget.
- JVM tests cover turn-frame clamping, independent cadence, navigation turn sign,
  progress, return to cruise, and finite positions in tiny viewports.
- Actions runs JVM tests, Android lint and `assembleDebug` on pull requests,
  main pushes and manual dispatch. The artifact remains `SharkOverlay-APK`.
- APK signing remains the debug key; no manifest or permission changes are made.
- Real-device checks are still required: Android 8 and a recent Android version,
  1/10 sharks, size/speed extremes, transparent edges, turns, screen off/on,
  stopping/restarting and touch-through under Android overlay security rules.
- 192-pixel tiles favor bounded memory for ten sharks. Large sharks on high-DPI
  screens can look softer; original full-resolution WebPs remain available.
- These supplied assets contain substantial whole-image rotation. Normalization
  prevents double rotation, but cannot create anatomical deformation absent from
  the source. Stronger body bending requires improved turn art.

## Offscreen excursions and variable speed

The fish center may pass the visible screen edge. Return steering starts only
outside an additional margin (1.2 times navigation radius or 8% of the short
screen dimension, capped at 35%). There is no hard position clamp. While outside,
a stable target in the central 35–65% region and continuously recomputed bearing
bring it back with a maximum 100 degrees/second turn. Random course changes do
not interrupt return steering. Returning ends after reaching the visible inset.

Each fish selects its own 0.65–1.45 speed factor every 2–6 seconds, approaches it
at no more than 0.22 factor/second, and gently slows during turns. The saved speed
setting remains the baseline. Both swim cadence and procedural fallback phase
follow actual navigation speed. Tiny preview viewports cap travel speed relative
to viewport size to avoid overshooting the entire view each tick.

Whale shark and orca concept animations are review artifacts only. They are not
included in Android assets, configuration choices or this implementation.
