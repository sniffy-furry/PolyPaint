# PolyPaint

A from-scratch Android app for painting PBR textures onto `.obj` 3D models:
a live 3D view and a flat "UV sheet" view of the same texture, side by
side, both paintable, always in sync, across six PBR channels (albedo,
normal, roughness, metallic, occlusion, emissive). Exports back out as
`.obj` + `.mtl` + PNGs, zipped into one file.

This is a genuinely large kind of app (think a stripped-down mobile
Substance Painter), so this is a real, working **foundation** - not a
polished v1.0. It should open in Android Studio, build, install, and let
you import a UV-unwrapped `.obj` and paint on it today. The "Not built
yet" section below is an honest list of what a production version would
still need.

## Building it

**I could not compile or test this project myself** - the environment I
wrote it in has no network access, so I couldn't run Gradle or the
Android toolchain. I wrote every file carefully and checked the APIs I
used against current documentation, but a project this size, handwritten
without a compiler to check it, very plausibly has at least a small error
somewhere. If the build fails, paste me the error and I'll fix it.

### Option A - GitHub Actions (does the actual compiling)
Push this repo to GitHub. `.github/workflows/android-build.yml` runs on
every push: it installs JDK 17, the Android SDK, and Gradle 8.13,
generates the Gradle wrapper (see below), builds a debug APK, and
uploads it as a workflow artifact you can download from the Actions tab.

### Option B - Android Studio
Open the project folder directly. One thing to know up front:

**The Gradle wrapper jar (`gradle/wrapper/gradle-wrapper.jar`) is not
included.** It's a compiled binary, and I can only write text files, so
including a hand-made one seemed riskier than just leaving it out.
`gradle/wrapper/gradle-wrapper.properties` (which pins Gradle 8.13) *is*
included. When you open the project, Android Studio will notice the
wrapper jar is missing and offer to generate it - accept that prompt. If
it doesn't ask, run once from a terminal with Gradle available:
```
gradle wrapper --gradle-version 8.13
```

## What's implemented

- **OBJ/MTL import** - a hand-written parser (`model/ObjLoader.kt`):
  triangles/quads/n-gons, vertex dedup, generated tangents for normal
  mapping, flat-normal fallback if the file has none.
- **PBR 3D preview** - OpenGL ES 3.0, a metallic/roughness Cook-Torrance
  shader (`render/PbrShaders.kt`) with one key light plus a cheap
  hemisphere-ambient term.
- **Two synced paint surfaces** - the 3D view and the flat UV sheet both
  read/write the *same* bitmap per channel, so there's no explicit "sync"
  step: painting on either one shows up on both immediately.
- **3D-surface painting** - touch on the model raycasts (Möller-Trumbore)
  into the mesh, finds the hit triangle, interpolates its UV, and paints
  there.
- **UV sheet painting** - direct 2D painting on the texture, with a
  toggleable UV wireframe overlay, plus independent pan/zoom.
- **Six PBR channels** - switchable in the toolbar, with a color picker
  for albedo/emissive and a value slider for the scalar channels.
- **Export** - writes `model.obj` + `model.mtl` (using the `map_Pr` /
  `map_Pm` PBR extension lines) + one PNG per painted channel, zipped.

## Why a `.zip`, not textures "inside" the `.obj`

`.obj` is a plain-text format - it can only *reference* image files by
name via a `.mtl`, never embed image bytes. There's no way around that
without leaving the OBJ format entirely (glTF's binary `.glb` form *can*
embed everything in one file, if that'd be more useful to you later - it
isn't implemented here, see below). The zip this app produces is the
closest single-file equivalent: unzip it and you get a completely
standard `.obj` + `.mtl` + textures folder any 3D tool can open.

## Project layout

```
app/src/main/java/com/polypaint/app/
  model/    ObjModel, ObjLoader, MtlIO, PaintChannel   - parsing + data
  render/   GLModelRenderer, PbrShaders, Raycast, ...  - OpenGL ES + PBR
  paint/    Brush, TextureLayerManager                 - the paint engine
  io/       ImportUtils, ModelExporter, ZipUtils        - files in/out
  ui/       PaintScreen, Gl3DView, UvPaintCanvas, theme - Compose UI
```

## Android / custom ROM notes

- `minSdk 34` (Android 14), `compileSdk`/`targetSdk 36` (Android 16).
  Android 16 QPR2 is still API level 36 (minor version 36.1) - it doesn't
  need a different `compileSdk` number to build against.
- Renders with **OpenGL ES 3.0**, not Vulkan. ES is uniformly available
  across custom ROMs and older/budget GPUs; Vulkan driver quality varies
  a lot more between vendors and forks, which matters more for "works
  everywhere" than the extra performance would.
- No Google Play Services dependency anywhere, and no `INTERNET`
  permission - the app is fully local and doesn't need it.
- File access goes through the standard Storage Access Framework
  (document picker), which every AOSP-based ROM supports the same way.

## Known limitations / not built yet

Scoped out deliberately, to keep the first version something that could
actually work rather than a sprawl of half-finished features:

- **One unified texture set per model.** If your `.obj` has multiple
  materials, only the first one's maps are loaded as a starting point,
  and everything paints into one shared set of six channels. True
  multi-material support (separate paintable sets per material, switching
  between them) is a natural next step but a substantial one.
- **No automatic UV unwrapping.** The model must already be UV-unwrapped
  (in Blender, Maya, etc.) - this app paints onto existing UVs, it
  doesn't generate them.
- **Import needs a single `.obj` or a `.zip`.** A picked `.obj` is staged
  into the app's private storage so its `.mtl` and textures can be found
  as normal files; that means a lone `.obj` picked from a folder full of
  textures won't find them automatically. Zip the `.obj` + `.mtl` +
  textures together first if you're bringing in existing materials to
  keep painting on. Folder-level import (via `ACTION_OPEN_DOCUMENT_TREE`)
  would be a reasonable enhancement.
- **Brute-force raycasting.** 3D-surface painting tests every triangle on
  every stroke sample - fine for typical tens-of-thousands-of-triangles
  mobile assets, but a spatial index (BVH/grid) would be needed for much
  denser meshes.
- **No undo/redo**, no layers or masks within a channel, no brush
  smoothing/symmetry.
- **Normal-map painting is just RGB painting.** There's no sculpt-to-
  normal tool - you're painting tangent-space colors directly (flat is
  roughly R128,G128,B255).
- **No GLTF/GLB export.** Mentioned above as the "true single embedded
  file" alternative to the OBJ+MTL+PNG zip - not implemented, but the
  export code is isolated in `io/ModelExporter.kt` if you want to add it.
- **Rotation is handled by not recreating the Activity** (`configChanges`
  in the manifest), which keeps in-progress painting safe across a
  rotate but is a simpler approach than a real `ViewModel` + saved-state
  architecture.
- Release build has `isMinifyEnabled = false` - shrinking/obfuscation
  isn't tuned in.

## If the build breaks

Almost certainly somewhere in the Gradle/AGP/Kotlin/Compose version
pinning (`build.gradle.kts` files) or a Compose API that's shifted
slightly - handwritten without a compiler to check against, that's the
most likely failure point, not the core logic. Send me the error from
the Actions log (or Android Studio's Build output) and I'll fix it.
