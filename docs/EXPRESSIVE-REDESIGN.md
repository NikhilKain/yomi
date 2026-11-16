# Expressive UI redesign

The visual reference is the owner's local Hanami project (`D:\Projects\hanami`).
This pass changes presentation, not the Mihon data model, sync protocol,
sources or reading engine. No feature or preference was removed, and no extra
preference toggles are added.

## Shared kit

`presentation-core/.../components/yomi/` holds the pieces every screen uses:
shaped icon badges, grouped tonal rows (`GroupRow`, `LocalGroupPosition`),
connected single-choice buttons, pill toggles (`ChoiceChip`), tags, stat tiles,
empty states, press bounce, staggered reveal and shimmer placeholders. Colours
always come from `MaterialTheme`, so every theme, light/dark, AMOLED and
wallpaper colour keeps working.

## Presentation coverage

- Identity: the moonlit crescent-and-pages artwork in the launcher, Android
  splash, notifications and the More/About header. The Mihon icon and splash
  resources are gone; upstream legal credit remains.
- Shared navigation: top-level screens use a large display title with tonal
  round action buttons; deeper screens use a round back button over a large
  title that folds away on scroll. Search, selection mode and overflow actions
  remain. Yomi's glass dock is kept (the owner preferred it to a notch dock).
- Library: title with collection count, always-visible pill search, connected
  category toggles, a cover-tinted spotlight card with progress and a read
  button, rounded covers with a progress pill. All display modes, categories
  and selection remain.
- Details: cover backdrop that melts into the page, poster beside a large title,
  one connected action group (library, update rhythm, tracking, WebView), a
  progress card with large numerals and Start/Resume, grouped chapter rows,
  tag pills. The page takes its colours from the cover. Metadata search,
  tracking, downloads, bookmarks, notes and swipe actions remain.
- Browse, Extensions, Migrate, Updates, History, More, Settings, About,
  categories and download queue: grouped tonal rows with shaped icons and
  spacing instead of divider lines.
- Search: round back button, pill search field, wavy progress while sources
  answer, connected source filter, shimmer placeholders, tonal error rows.
- Reader: floating top bar and pill bottom bar with round buttons; options use
  switch rows and pill toggles; reading-mode and rotation pickers are tiles;
  between-chapter notices are tonal panels. The reader engine, directions,
  modes, PDF, spread and page-flip implementations are unchanged.
- Stats: number tiles with shaped icons. Tracking sheet: separate rounded tiles.
- Onboarding: large heading, step progress line, large rounded buttons. Storage
  selection and permission decisions remain explicit.
- Loading and empty states: the expressive morphing loading indicator and a
  flower-shaped empty-state badge.

## Validation

On November 16, 2026, `testDebugUnitTest` passed (80 tests, no failures or
skips) and `lintDebug` passed in every module. Lint first reported a dark-only
`splash` colour inherited from upstream; it was removed because the splash is
the same moonlit navy in both themes.

The side-by-side build (`com.manga.yomi.validation.dev`) was installed on the
owner's phone and checked in dark and light: Library, details, reader controls
and options, reading-mode picker, between-chapter notice, search, History,
Browse, Extensions, Upcoming, More, Settings and Appearance. The original
`com.manga.yomi` was not touched.

Snappiness: a debug build launched cold in 6.6 s with 14% janky frames. The
same source built with `-PyomiOptimized=true` (R8, not debuggable) launched in
2.3-2.8 s with 5% janky frames, and the slowest 1% of frames fell from 400 ms
to 105 ms. Screen and tab transitions were also shortened. Previews meant to be
judged for feel must use the optimized build.

Open: reading history on the test copy disappeared twice between installs on
November 14-15. The cause was not found, and history recorded normally
afterwards.
