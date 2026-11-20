# The OpenConnect look

A fresh install opens in a **sporty modern** style: compact Plex Sans type, flat panels with a hairline border and
restrained corners, rectangular controls and a plain bottom bar. The default colours are **Graphite**, cool neutrals with
one blue accent; **Track** is the same idea with an orange accent. The original **Handheld** console look (v0.31.0) is
still here as the *Retro* style, and all colour themes work in every style, so nobody loses a look they chose in an
older version. Everything below lives in `android/app/src/main/kotlin/nl/tufein/openconnect/ui/`.

## The rules

| | Modern (default, `clean`) | Retro (`retro`) |
|---|---|---|
| **Type** | *IBM Plex Sans* for headlines, numbers and sentences, compact and semi-bold, with tighter tracking on big titles; *IBM Plex Mono* for labels, units and data. | *Jersey 10* (a pixel face made for sports shirts) for headlines and big numbers, Plex Sans for sentences, Plex Mono for labels. |
| **Corners** | Restrained corners of 8, 10, 12, 16 and 20 dp (`ModernShapes`): enough to read as a panel, not a bubble. | Corners cut in pixel steps (`PixelCornerShape`): two steps of half the corner size, like a rounded corner on graph paper. |
| **Panels** | `Panel` in `Pixel.kt`: a flat surface with a 1 dp hairline border, no shadow and 14 dp padding when the caller uses the default. A tint is only a 10% wash. | Flat surface, 2 dp ink outline and a hard offset shadow (3 dp right and down, no blur). |
| **Colour** | Graphite: cool neutral surfaces (`#0E1116` dark, `#F4F6F8` light) with one blue accent (`#4C9AFF` / `#1D4ED8`). Track swaps in orange. Each body signal keeps its own colour in every theme. | The same palettes. |
| **Icons** | Our own small grid icons (`PixelIcons.kt`), written as text and drawn by `PixelIcon`; no icon pack. | The same icons. |
| **Logo** | A pixel heart with a pulse through it (`PixelIcons.Logo`). The launcher icon (`res/drawable/ic_launcher_*.xml`) is generated from the same grid. | The same logo. |
| **Controls** | `PrimaryButton`, `SecondaryButton` and `QuietButton` are compact rectangles with a 10 dp radius and a hairline outline; a chosen `PixelChip` is filled with the accent colour. `PixelSwitch` has a round knob, `PixelProgress` still uses 24 blocks for progress. | A pressed button sinks onto its hard shadow and the chosen chip is filled with ink, like a highlighted menu entry. |
| **Charts** | `SegmentBar` draws one rounded bar and `RingGauge` one smooth arc with rounded ends. Date axes and chart content descriptions are unchanged. | Block bars, a square-capped line and a dial of 40 ticks. |
| **Tab bar** | A flat strip with a hairline above it and a short accent line under the open tab (`ModernBar` in `Navigation.kt`); every tab is at least 56 dp high. | A flat strip under an ink line, the open tab a block filled with ink. |
| **Hierarchy** | Score tiles show numbers only; the 7-day lines sit in a folded *Trends* section, and the month chart and recent activities fold away with their headline value in the header (`FoldSection`). Health keeps the scores on top and draws **one chart at a time**, chosen by tabs (`health_chart`). Insights keeps six everyday tabs and puts the other six behind *More*. Folds and tabs are for secondary information only. | The same structure, with the console look. |

No gradients and no blur are used in either style; emphasis comes from colour, a tint or a shadow. A `tint` washes a
little of a signal colour into a panel.

## Colour themes, letters and the launcher icon

**Colour themes** (*Settings, Appearance*) come in five groups, 26 in all, each with a dark and a light version:

- **Sport**: Graphite (the default, cool neutrals with one blue accent) and Track (near-black with an orange accent).
- **Retro screens**: Handheld, Game Boy, Virtual Boy, Famicom, Commodore 64, Atari, Amber terminal, Terminal
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

The home-screen widget and the short start-up background follow the chosen palette too. `HomeWidgetViews` paints its
`RemoteViews` from `colorSchemeFor` (it cannot read the Compose theme), and `HomeWidget.refresh` is called when a palette,
style or dark mode is picked. The window background in `values/colors.xml` and `values-night/colors.xml` uses the two
Graphite background colours so a start-up in another theme does not flash cream.

Since v0.43.0 the whole look is one section of Settings (*Appearance*) that is folded away until its header is tapped
(`FoldSection` in `Components.kt`): dark or light, style, letters (Retro only), colour themes and the launcher icon. The
header shows what is chosen now (for example "Graphite · Sleek"). At the top sits a live **preview** (`LookPreview`):
a small dashboard drawn in the chosen theme, so a colour or style can be judged before it is kept instead of from two
little squares. The first start shows the same preview with a choice of style and theme (`OnboardingScreen`).

The watch faces (Clean, Dashboard, Retro, see [WATCH_FACES.md](WATCH_FACES.md)) are not part of the app since v0.43.0: they are Connect IQ code
for the watch and keep their own three designs.

## Adding a screen

1. Put content in `SurfaceCard` or `Panel`, titles in `SectionTitle`, numbers in `displayMedium` or `titleLarge` and
   sentences in `bodyMedium`. Take text styles from the theme: they change with the style and the phone's font scale.
2. Use the controls above, never `Button`, `FilterChip`, `Switch` or an icon from the Material icon set.
3. A new icon is a new grid in `PixelIcons.kt`: `#` is filled, anything else is empty, and every row has the same length.
4. Never add a gradient or a blur. If something needs emphasis, use a tint, a colour or a weight. Check the screen in
   both the modern default and Retro, at 200% font size and in dark and light mode.
5. Fold only secondary information, with its headline value in the fold header (`FoldSection`). The day's key numbers and
   anything a test or the daily glance needs stay visible.

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
