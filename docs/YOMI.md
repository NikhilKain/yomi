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
keeps Yomi's floating dock with a clear sliding selection. Tablets still get
the adaptive navigation rail.

The installed original Yomi has a different database and local-book layout.
Use the side-by-side build until a migration has been designed and tested:
`-PyomiValidation=true` selects `com.manga.yomi.validation` for release or
`com.manga.yomi.validation.dev` for debug. The normal release ID is
`com.manga.yomi`. Do not install a normal release over the existing app as a
migration shortcut.

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

On November 14, 2026, the app's Kotlin and Java compiled and the SQLDelight
debug and release migration checks completed. The Windows build stalled after
debug dex generation while writing the Gradle build cache. A retry with a
separate Gradle home and `--no-build-cache` passed dependency and resource
preparation, then stalled while Gradle was clearing stale build outputs.
No new APK was packaged, and neither the app unit suite nor the phone smoke
checks can be reported as passing. The original Yomi remains installed.
