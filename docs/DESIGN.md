# The OpenConnect look ("Handheld")

Since v0.31.0 the app has one look of its own. It replaces the three designs (Clean Pro, Dashboard Dark, Retro Pro) and
the three styles (Sport, Round, Pixel) of v0.18.0 to v0.30.0. The idea: the screen of a handheld console, not a stock
Material app. Everything below lives in `android/app/src/main/kotlin/nl/tufein/openconnect/ui/`.

## The rules

| | |
|---|---|
| **Type** | *Jersey 10* (a pixel face made for sports shirts) for every headline and every big number, *IBM Plex Sans* for sentences, *IBM Plex Mono* for labels, units and data. All three are bundled in `res/font`, so every phone draws the same letters. Jersey has one weight and looks smaller than its size, so its sizes are larger than the usual Material ones. |
| **Corners** | Cut in pixel steps (`PixelCornerShape` in `Theme.kt`): two steps of half the corner size, like a rounded corner on graph paper. It is a Material `CornerBasedShape`, so every Material component that takes its shape from the theme (dialogs, text fields, the snackbar) gets the same corners. |
| **Panels** | `Panel` (`Pixel.kt`): flat surface, 2 dp ink outline, hard offset shadow (3 dp right and down, no blur). No gradients and no glow anywhere. A *tint* washes a little of a signal colour into the surface. |
| **Colour** | Warm paper (`#ECE5D0`) in light mode, warm black (`#131210`) in dark mode, orange as the one signal colour. Text and outlines are the same ink colour. On a dark screen the outline is softened and the shadow becomes a faint ghost. Each body signal keeps its own colour in every theme. |
| **Icons** | Pixel art on a 12 by 12 grid, written as text (`PixelIcons.kt`) and drawn by `PixelIcon`. No icon pack. |
| **Logo** | A pixel heart with a pulse through it (`PixelIcons.Logo`). The launcher icon (`res/drawable/ic_launcher_*.xml`) is generated from the same grid. |
| **Controls** | `PrimaryButton`, `SecondaryButton` and `QuietButton` (a pressed button sinks onto its shadow), `PixelChip` (the chosen one is filled with ink, like the highlighted entry of a menu), `PixelSwitch`, `PixelSpinner` and `PixelProgress` (blocks). |
| **Charts** | Bar charts are stacks of blocks like the level meter of a stereo; line charts have a scan-line fill under a square-capped line; the dial (`RingGauge`) is 40 blocks around a centre; the readiness score on Home is a 25-block health bar. |
| **Tab bar** | A flat strip under an ink line; the open tab is a block filled with ink, like the highlighted entry in a game menu. |

## Colour themes

Under *Settings, Appearance* the colours can still be changed: **Handheld** (the default), **Game Boy** (four greens),
**Amber terminal**, **Synthwave** and **Wallpaper** (the phone's own colours on Android 12 and newer). Shapes, fonts and
layout are the same in all of them. A theme saved by an older version (Arcade, Clean, Midnight, Ocean, Sunset) no longer
exists and opens as Handheld.

The watch faces (Clean, Dashboard, Retro, see [WATCH_FACES.md](WATCH_FACES.md)) are separate: they are Connect IQ code
for the watch and keep their own three designs.

## Adding a screen

1. Put content in `SurfaceCard` or `Panel`, titles in `SectionTitle`, numbers in the Jersey styles
   (`displayMedium`, `titleLarge`) and sentences in `bodyMedium`.
2. Use the controls above, never `Button`, `FilterChip`, `Switch` or an icon from the Material icon set.
3. A new icon is a new grid in `PixelIcons.kt`: `#` is filled, anything else is empty, and every row has the same length.
4. Never add a gradient, a blur or a rounded corner. If something needs emphasis, use ink, a tint or the shadow.

## Fonts

Jersey 10 (Copyright 2023 The Soft Type Project Authors) and IBM Plex Sans and Mono (Copyright 2017 IBM Corp.) are
licensed under the SIL Open Font License 1.1; the texts are in `licenses/` and the credit is in `NOTICE`.
