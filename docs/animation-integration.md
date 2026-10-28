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

## Species selection and persistence

The Add button opens a three-option species picker: hammerhead, whale shark,
and orca. The limit is ten pets total across all species. Each settings card
shows its species name, size and speed. Preferences now include the stable
`species` ID (`hammerhead`, `whale_shark`, `orca`). Missing or unknown IDs map to
hammerhead; size/speed are clamped through the same `SharkConfig.fromSaved`
factory in both Activity and Service. Deletion uses list position rather than
data equality so identical-looking configurations remain independently removable.

The two user-approved concept animations are packaged in
`assets/shark/whale_shark/` and `assets/shark/orca/`, each as a transparent source
WebP plus a motion-interpolated atlas and manifest (29 whale shark frames,
24 orca frames). They keep playing their swim cycle
through navigation turns; only hammerheads currently have dedicated turn clips.
A monotonic Double animation clock applies modulo only per clip, so different
loop lengths cannot interrupt each other. All five atlases together require
31.5 MiB of shared ARGB storage, excluding GPU copies.

Regenerate and check with:

```sh
python tools/prepare_species_assets.py /directory/with/approved/concept_webps
python tools/check_species_assets.py
```

The new clips derive from the approved eight-pose concept art. FFmpeg performs
motion-compensated interpolation with real preceding/following cycle context,
including alpha. Whale shark has 29 frames / 1218ms; orca has 24 frames / 1008ms.
Both use 42ms frames (23.81fps), exactly matching the hammerhead timing. Swim
cadence still follows each pet's actual speed. This adds intermediate motion,
not new anatomical source poses. The conversion uses one fixed scale and pivot
per clip to prevent rescaling flutter. All output frames must be unique, remain
transparent and fit within the frame; the runtime memory check allows 34 MiB.
The original 8-pose concept files are the generator inputs; the published WebPs
and atlases are now the interpolated output. Regeneration additionally requires
FFmpeg with RGBA-capable minterpolate. Device testing should cover
mixed species, adding/canceling at the ten-pet limit, deleting identical configs,
relaunching the Activity and restarting the Service.


## Hammerhead articulation and agility

Hammerheads add a continuous 24 x 12 BitmapMesh deformation on top of the
original animation. Aft-weighted lateral motion increases caudal sweep while
keeping the hammer-shaped head steady. Signed actual angular velocity drives
a smoothly changing body curvature and local dorsal-fin shear, so the fin tip
leans relative to its base. The extra tail wave runs during turns as well as
cruise; turn-clip progress no longer makes the whole animal look paused.

Only hammerheads remove the old 0.85 turn-speed factor. Course-change radii use
max(0.65 * body radius, 0.26 * speed), with 90–240 degrees/second steering;
offscreen return steering is 220 degrees/second instead of 100. Whale shark and
orca keep their original navigation values. Speed target changes still use the
existing acceleration limit; no instantaneous velocity boost is introduced.

BitmapMesh consumes one complete frame bitmap. The loader slices hammerhead
atlases once on its background executor, shares all frame bitmaps across pets,
and releases the atlas after slicing. Persistent pixel storage stays at about
31.5 MiB for all species, with transient extra decode/slicing memory and GPU
copies excluded. Each renderer reuses one 650-float vertex buffer; no frame
bitmaps are allocated while swimming. Unit tests check tail sweep, head stability,
loop continuity, dorsal-tip response, mesh orientation and species-specific
turning/velocity behavior. The final visual tuning and hardware rendering still
need real-device validation.

## Touch-through on Android 12 and later

`FLAG_NOT_TOUCHABLE` alone does not guarantee delivery to another app. Android
12+ treats `TYPE_APPLICATION_OVERLAY` as untrusted and uses the window's
`LayoutParams.alpha` for obscuring opacity, including transparent image padding.
The previous per-pet windows had alpha=1, and opacity from same-UID windows can
combine as `1 - product(1 - alpha_i)` when they overlap.

All pets now render into one non-focusable, non-touchable, translucent overlay
window. On API 31+ its alpha is below both 0.8 and the value returned by
`InputManager.getMaximumObscuringOpacityForTouch()`, with a 0.01 safety margin.
On older Android it keeps alpha=1. Drawing more pets does not add more windows or
increase their combined window opacity. Fish on modern Android look slightly
more transparent as a consequence; sprite alpha alone would not fix input.

Positions, heading and sizes are Canvas transforms rather than WindowManager
moves. The surface is explicitly cleared before each frame. Navigation uses the
laid-out overlay dimensions, with display metrics only as startup fallback.
Starting/updating replaces the single window, stopping removes it and cancels
the loop, and an empty pet list stops the service without leaving a transparent
fullscreen overlay attached. No additional permissions or system setting changes
are used.

The platform's low-opacity exception does not override an app's own
`filterTouchesWhenObscured` or sensitive-screen overlay restrictions. For those
screens the existing notification Stop action removes the overlay. Real-device
verification is required: ordinary-app taps/scrolls under a fish and its padding,
after it moves away, overlapping 10 pets, restarting and stopping, portrait /
landscape, plus a sensitive app with its own obscured-touch policy.

Reference: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
