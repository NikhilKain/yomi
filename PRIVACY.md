# Yomi privacy policy

*Updated 21 November 2026. Applies to Yomi 0.2.1 and later, as published by Vythera at
[github.com/NikhilKain/yomi](https://github.com/NikhilKain/yomi).*

Yomi has no ads, no accounts and no third-party analytics or tracking SDKs. Your library,
reading history, sources, searches and anything you type stay on your device unless you choose
to sync them with your own Google Drive.

## Anonymous usage check-in

To know how many devices use Yomi, the app checks in with Vythera's admin service, which runs on
Cloudflare Workers and D1 and is shared by several Vythera apps. Each check-in contains:

- A SHA-256 hash of your device's Android ID and Yomi's package name. The Android ID itself
  never leaves the device.
- A random installation ID and a random ID for each session, with how long Yomi has been open.
- Your device's manufacturer and model, the Android version and Yomi's version.

Cloudflare adds the approximate city, region and country of the connection. The service doesn't
store your IP address.

Yomi checks in when it opens, about every five minutes while it is open, and when it closes.
The check-in only counts devices: Yomi never locks or limits the app based on the answer.
Only official release builds check in. The check-in is part of the app and cannot be turned off
in the settings.

It never includes your library, titles, chapters, sources or extensions, searches, reading
history, tracker accounts, Google account, backups or anything you type.

Records stay until Vythera deletes them. To ask for your record to be deleted, write in the
[Telegram channel](https://t.me/vythera_apps) or use the feedback form.

## Update checks

Yomi asks GitHub (`api.github.com`) for the latest release on the schedule you pick under
*About → Check automatically* (every day, week, month, or a custom number of days), and when you
tap *Check for updates*. This is an ordinary request to GitHub; Yomi sends nothing about you with it.

## Things that happen only when you choose them

- **Sources and extensions** you install connect to their own websites. Their privacy practices
  are their own; only install extensions you trust.
- **Google Drive sync** stores a single sync file in your own Drive. Yomi can see only the files
  it creates there. There is no Yomi server in between.
- **Trackers** (AniList, MyAnimeList and others) receive what you choose to track.
- **Feedback** opens Vythera's feedback website in your browser, with the app name, Yomi's
  version, your device model and Android version filled in. Nothing is sent until you submit it.
- **Crash reports** are saved on your device, in the `logs` folder of your storage location. They
  are never uploaded by themselves.

## Contact

Telegram: [t.me/vythera_apps](https://t.me/vythera_apps) ·
Feedback: [vythera-feedback.narzo9990.workers.dev](https://vythera-feedback.narzo9990.workers.dev/?app=yomi)
