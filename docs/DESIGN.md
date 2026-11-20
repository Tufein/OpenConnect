# The OpenConnect look

From v1.0.0 a fresh install opens in a **modern** style: readable Plex Sans type, generous rounded cards that sit softly
on the background, pill-shaped controls and a floating bottom bar. The original **Handheld** console look (v0.31.0) is
still here as the *Retro* style, and all colour themes work in every style, so nobody loses a look they chose in an older
version. Everything below lives in `android/app/src/main/kotlin/nl/tufein/openconnect/ui/`.

## The rules

| | Modern (default, `clean`) | Retro (`retro`) |
|---|---|---|
| **Type** | *IBM Plex Sans* for headlines, numbers and sentences, with slightly tighter tracking on big titles; *IBM Plex Mono* for labels, units and data. | *Jersey 10* (a pixel face made for sports shirts) for headlines and big numbers, Plex Sans for sentences, Plex Mono for labels. |
| **Corners** | Rounded corners of 12, 16, 20, 28 and 36 dp (`ModernShapes`), so a card reads as one soft layer. | Corners cut in pixel steps (`PixelCornerShape`): two steps of half the corner size, like a rounded corner on graph paper. |
| **Panels** | `Panel` in `Pixel.kt`: surface with a soft 4 dp shadow, no border, 18 dp padding when the caller uses the default. | Flat surface, 2 dp ink outline and a hard offset shadow (3 dp right and down, no blur). |
| **Colour** | Warm paper (`#ECE5D0`) in light mode, warm black (`#131210`) in dark mode, orange as the one signal colour. Text and outlines are ink; on a dark screen the outline is softened. Each body signal keeps its own colour in every theme. | The same palettes. |
| **Icons** | Our own small grid icons (`PixelIcons.kt`), written as text and drawn by `PixelIcon`; no icon pack. | The same icons. |
| **Logo** | A pixel heart with a pulse through it (`PixelIcons.Logo`). The launcher icon (`res/drawable/ic_launcher_*.xml`) is generated from the same grid. | The same logo. |
| **Controls** | `PrimaryButton`, `SecondaryButton` and `QuietButton` are pills with a soft lift; `PixelChip` is a pill whose chosen entry is filled with the main colour. `PixelSwitch` has a round knob, `PixelProgress` still uses 24 blocks for progress. | A pressed button sinks onto its hard shadow and the chosen chip is filled with ink, like a highlighted menu entry. |
| **Charts** | `SegmentBar` draws one rounded bar and `RingGauge` one smooth arc with rounded ends. Date axes and chart content descriptions are unchanged. | Block bars, a square-capped line and a dial of 40 ticks. |
| **Tab bar** | One softly lifted strip with a rounded pill behind the open tab (`ModernBar` in `Navigation.kt`); every tab is at least 56 dp high. | A flat strip under an ink line, the open tab a block filled with ink. |

No gradients and no blur are used in either style; emphasis comes from colour, a tint or a shadow. A `tint` washes a
little of a signal colour into a panel.

## Colour themes, letters and the launcher icon

**Colour themes** (*Settings, Appearance*) come in four groups, 24 in all, each with a dark and a light version:

- **Retro screens**: Handheld (the default), Game Boy, Virtual Boy, Famicom, Commodore 64, Atari, Amber terminal, Terminal
  and Synthwave. Game Boy, Virtual Boy and Terminal keep to the shades of their one colour, also for the body signals.
- **Easy on the eyes**: High contrast (pure black and white), Grey, Slate, Sand, Nord, Solarized, Gruvbox and Dracula.
- **Colourful**: Ocean, Forest, Sunset, Candy, Violet and Cherry.
- **Phone**: Wallpaper, the phone's own colours on Android 12 and newer.

Shapes and layout are the same in all of them. A theme is a pair of `Keys` in `PaletteKeys` (`Theme.kt`): primary,
secondary, tertiary, background, panel and text colour, and from them `scheme()` builds the whole Material colour scheme.
`ThemesLettersAndIconTest.everyThemeIsReadableInDarkAndInLight` holds every theme to the same limits: text on the
background and on a panel at least 7:1, small grey text at least 4.5:1, text on each accent colour at least 4.5:1 and
the main colour against a panel at least 3:1 (WCAG contrast ratios). A new theme is one more entry in `Palette`, one in
`PaletteKeys` and a name in both string files; the test tells you if it is too faint.

A theme saved by an older version (Arcade, Clean, Midnight) no longer exists and opens as Handheld. Ocean and Sunset
exist again, with new colours.

**Letters** (*Settings, Appearance, Letters*) only apply to Retro: *Pixel letters* is the console look above, *Clear
letters* swaps Jersey and Plex Mono for Plex Sans. Modern and Classic always use the clear type.

**Launcher icon** (*Settings, App icon*): eight icons, each an `activity-alias` of the main screen in the manifest
(`.IconHandheld`, `.IconNight`, and so on) with its own adaptive icon in `res/mipmap-anydpi-v26`. Exactly one alias is
switched on at a time; `AppIcons.select` turns the new one on before the old ones off, and `AppIcons.current` reads which
is on from the system, so it also survives clearing the app's data. The picture of each icon is made from the same pixel
grids as the app by `android/tools/launcher_icons.py` (the colours in that script and in `AppIcon.kt` must match; the
first icon is hand-made). The launcher may take a moment to show the new icon, and on some phones the app closes once.

Since v0.43.0 the whole look is one section of Settings (*Appearance*) that is folded away until its header is tapped
(`FoldSection` in `Components.kt`): dark or light, style, letters (Retro only), colour themes and the launcher icon. The
header shows what is chosen now (for example "Handheld · Sleek").

The watch faces (Clean, Dashboard, Retro, see [WATCH_FACES.md](WATCH_FACES.md)) are not part of the app since v0.43.0: they are Connect IQ code
for the watch and keep their own three designs.

## Adding a screen

1. Put content in `SurfaceCard` or `Panel`, titles in `SectionTitle`, numbers in `displayMedium` or `titleLarge` and
   sentences in `bodyMedium`. Take text styles from the theme: they change with the style and the phone's font scale.
2. Use the controls above, never `Button`, `FilterChip`, `Switch` or an icon from the Material icon set.
3. A new icon is a new grid in `PixelIcons.kt`: `#` is filled, anything else is empty, and every row has the same length.
4. Never add a gradient or a blur. If something needs emphasis, use a tint, a colour or a shadow. Check the screen in
   both the modern default and Retro, at 200% font size and in dark and light mode.

## Fonts

Jersey 10 (Copyright 2023 The Soft Type Project Authors) and IBM Plex Sans and Mono (Copyright 2017 IBM Corp.) are
licensed under the SIL Open Font License 1.1; the texts are in `licenses/` and the credit is in `NOTICE`.

## Styles

`Style` in `Theme.kt` is saved as `theme_style` and read through `LocalStyle`:

- **Sleek** (`clean`): the modern default above, with `ModernShapes` (12 to 36 dp), soft shadows, pill controls, clear
  letters and smooth charts.
- **Retro** (`retro`): the original Handheld console look, with pixel-cut corners, ink outlines and a hard shadow.
- **Classic** (`classic`): small corners (2 to 8 dp), hairlines, no shadow, clear letters with serif titles.

Shapes come from `MaterialTheme.shapes`, so everything that uses them follows the style. Outlines use `lineWidth()` and
`inkColor()`, the hard shadow uses `shadowColor()` (transparent outside Retro) and `softShadow()` lifts modern cards; use
those rather than fixed values. Only Retro has a choice of letters. What does not change with the style: the pixel icons
and the launcher icon. Every colour theme works in every style.
