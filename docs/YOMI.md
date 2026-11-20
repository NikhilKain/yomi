# Yomi on Mihon

Yomi uses the complete [Mihon v0.20.4](https://github.com/mihonapp/mihon/releases/tag/v0.20.4)
source tree. The base commit is `df6507256acce8e7f3660783a3db6dbd1a31b6b5`.
The owner's reader and sync changes were recovered by comparing the local source
snapshot against that release, so local settings, generated files and signing
materials were not copied. The local `codex/mihon-0213-reference` branch
holds upstream v0.21.3 for follow-up work. v0.21 changed the database layout
and removed the revision fields used by this sync implementation; upgrading
requires a separate data and restore migration.

Yomi retains the original Mihon library categories, tracking, download queue,
backups, extension installer, source access, settings and local reader. The
owner snapshot adds Drive sync, PDF chapters, animated page turns, book spreads,
and reader settings. New presentation work uses Hanami's sakura colors,
variable typography, artwork spotlight and staggered motion. On phones, it
uses a floating dock based on Stride's Verdant layout. The glass lens slides
over the dock surface and magnifies it without adding blur. The selection uses
the active Yomi theme's accent. Tablets still get
the adaptive navigation rail.

Yomi has four destinations instead of Mihon's five:

- **Home** (`HomeHubTab`) shows a reading deck, shortcuts, new chapters,
  recently read titles and titles not started yet. Mihon's Updates and History
  screens open from Home as full screens with a back button. Shortcuts and
  notifications that target Updates or History open those screens over Home.
- **Library** keeps every Mihon category, sort, filter and display mode.
- **Discover** is Mihon's Browse screen with sources, extensions and migration.
  It adds a field at the top that searches every source.
- **You** replaces More. Downloaded-only and incognito are large toggle tiles,
  and the download queue, categories, stats, data, settings, about, support
  and help links are all still there.

## Release identity

The owner selected the Mihon-based implementation as the continuing Yomi project
on November 16, 2026. Its first release is **0.1.0**, application ID
`com.manga.yomi`, version code **1**. The original 0.2.0 implementation is retired.
The version-code reset deliberately prevents an in-place upgrade of its
incompatible database. The owner removed that original phone installation.

The previous Mihon-based preview uses `com.manga.yomi.validation.dev`.
Changing the application ID does not transfer Android app data. Export a full
`.tachibk` backup from the preview, select the same external storage directory in
the release, then restore the backup and verify the library and reading progress.
Keep the preview until verification finishes. Account secrets are excluded by the
default backup settings and may require signing in again.

`-PyomiValidation=true` remains available for isolated testing; it uses version
code 41 and package `com.manga.yomi.validation` (release) or
`com.manga.yomi.validation.dev` (debug). Do not run connected test runners against
an owner's normal library installation.

Use a JDK 21 Gradle runtime, Android SDK 37, and NDK 29.0.14206865. Run
`./gradlew check` for repository checks. For the side-by-side build, run
`./gradlew :app:assembleDebug -PyomiValidation=true`. The ARM64 debug APK is
under `app/build/outputs/apk/debug/`. For a signed build the signing key stays
outside the repository; pass `YOMI_STORE_FILE`, `YOMI_STORE_PASSWORD`,
`YOMI_KEY_ALIAS` and `YOMI_KEY_PASSWORD` through the environment.

The pinned JitPack FlexibleAdapter commit no longer publishes an AAR. This
checkout uses the library's published 5.1.0 Maven Central release. The Yomi
build keeps telemetry opt-in and does not use Mihon's app updater.

[Drive sync setup](../SYNC_SETUP.md) requires your own OAuth client and a
browser consent flow. A local merge test cannot validate that account flow.
The normal app and each test package use separate filenames in Drive.

Copyright notices for upstream Mihon and Tachiyomi are in [LICENSE](../LICENSE)
and the app's open-source licences screen. Google Sans Flex is included with
its [SIL Open Font License](../app/src/main/assets/licenses/google_sans_flex_OFL.txt).
Hanami is the owner's local design reference; Yomi retains its own branding.

## Validation status

The November 16 expressive preview passed 80 host tests and module lint checks;
see [redesign validation](EXPRESSIVE-REDESIGN.md). The 0.1.0 package migration and
signed release verification are recorded separately after installation.
